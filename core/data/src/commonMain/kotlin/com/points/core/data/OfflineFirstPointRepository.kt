package com.points.core.data

import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
import com.points.core.domain.PointAggregate
import com.points.core.domain.PointEvent
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointRepository
import com.points.core.domain.PointType
import com.points.core.domain.PointTypeDraft
import com.points.core.domain.PointTypeRepository
import com.points.core.network.PointsApiService
import com.points.shared.contract.PointEventDto
import com.points.shared.contract.PointTypeDto
import com.points.shared.contract.SyncRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Offline-first repository for both the event ledger ([PointRepository]) and the point-type catalog
 * ([PointTypeRepository]). Every write hits the local store first (the source of truth for display) and is
 * marked pending; observed state comes from the local store, so the UI updates immediately, online or off.
 * Writes do *not* sync — pushing is the `SyncCoordinator`'s job, which observes the pending count and drives
 * [sync].
 *
 * [sync] reconciles both halves in **bounded** round trips: pending events and type changes are uploaded in
 * chunks of [batchSize], and the pull side loops while the server signals `hasMoreEvents`/`hasMoreTypes`, so
 * a device offline for weeks drains in several small requests instead of one unbounded one. Cursors advance
 * per round trip, so an interrupted drain resumes where it left off. Events merge additively (union by id,
 * PN-counter CRDT); types merge **last-write-wins by `updatedAt`** (a type is mutable, so the newer edit
 * wins — see [PointType]). Deleting a type is a tombstone, never a destructive remove.
 */
@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointRepository(
    private val local: LocalEventDataSource,
    private val types: LocalPointTypeDataSource,
    private val api: PointsApiService,
    private val clock: Clock = Clock.System,
    private val batchSize: Int = DEFAULT_SYNC_BATCH_SIZE,
) : PointRepository, PointTypeRepository {

    override suspend fun append(pointTypeId: Uuid, delta: Long): PointEvent {
        val event = PointEvent(
            id = Uuid.random(),
            pointTypeId = pointTypeId,
            delta = delta,
            deviceId = local.deviceId,
            createdAt = clock.now(),
        )
        local.insert(event) // persist as pending; the SyncCoordinator observes the bump and drives the push
        return event
    }

    override suspend fun currentValue(pointTypeId: Uuid): Long = local.value(pointTypeId)

    override fun observeValue(pointTypeId: Uuid): Flow<Long> = local.observeValue(pointTypeId)

    override fun observeEvents(pointTypeId: Uuid): Flow<List<PointEvent>> =
        local.observeEventsForType(pointTypeId)

    override fun observeAggregates(sinceMillis: Long): Flow<Map<Uuid, PointAggregate>> =
        local.observeAggregates(sinceMillis)

    override suspend fun create(draft: PointTypeDraft): PointType {
        val now = clock.now()
        val type = PointType(
            id = Uuid.random(),
            name = draft.name,
            hue = draft.hue,
            icon = draft.icon,
            mode = draft.mode,
            step = draft.step,
            goal = draft.goal,
            target = draft.target,
            unit = draft.unit,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        types.upsertLocal(type)
        return type
    }

    override suspend fun edit(id: Uuid, draft: PointTypeDraft): PointType? {
        val existing = types.typeById(id) ?: return null
        val updated = existing.copy(
            name = draft.name,
            hue = draft.hue,
            icon = draft.icon,
            mode = draft.mode,
            step = draft.step,
            goal = draft.goal,
            target = draft.target,
            unit = draft.unit,
            updatedAt = clock.now(), // re-stamp so this edit wins on merge; createdAt is preserved
        )
        types.upsertLocal(updated)
        return updated
    }

    override suspend fun delete(id: Uuid) {
        val existing = types.typeById(id) ?: return
        if (!existing.isActive) return // already tombstoned — idempotent
        val now = clock.now()
        types.upsertLocal(existing.copy(deletedAt = now, updatedAt = now))
    }

    override suspend fun restore(id: Uuid) {
        val existing = types.typeById(id) ?: return
        if (existing.isActive) return // already live — idempotent
        types.upsertLocal(existing.copy(deletedAt = null, updatedAt = clock.now()))
    }

    override fun observeTypes(): Flow<List<PointType>> = types.observeTypes()

    override suspend fun sync() {
        val eventChunks = ArrayDeque(local.pendingEvents().chunked(batchSize))
        val typeChunks = ArrayDeque(types.pendingTypes().chunked(batchSize))

        while (true) {
            val eventChunk = eventChunks.removeFirstOrNull().orEmpty()
            val typeChunk = typeChunks.removeFirstOrNull().orEmpty()
            val sinceSeq = local.syncCursor()
            val sinceTypeSeq = types.typeCursor()
            val response = api.sync(
                SyncRequestDto(
                    ownerId = local.ownerId,
                    sinceSeq = sinceSeq,
                    events = eventChunk.map { it.toDto(local.ownerId) },
                    sinceTypeSeq = sinceTypeSeq,
                    pointTypes = typeChunk.map { it.toDto(local.ownerId) },
                ),
            )

            // Event half: union by id, advance the event cursor. A record that fails to parse is skipped,
            // not fatal: it would fail identically on every future pull, so throwing here would wedge every
            // reconcile behind one poisoned record. The cursor still advances past it.
            response.events.forEach { dto ->
                val event = dto.toPointEventOrNull()
                if (event != null) local.applySynced(event) else println("Points sync: skipping malformed event ${dto.id}")
            }
            local.clearPending(eventChunk.map { it.id.toString() })
            local.setCursor(response.nextSeq)

            // Type half: last-write-wins by updatedAt, advance the type cursor. Same skip-don't-throw rule.
            response.pointTypes.forEach { dto ->
                val type = dto.toPointTypeOrNull()
                if (type != null) types.applySynced(type) else println("Points sync: skipping malformed type ${dto.id}")
            }
            types.clearPending(typeChunk.map { it.id.toString() })
            types.setTypeCursor(response.nextTypeSeq)

            val moreToPush = eventChunks.isNotEmpty() || typeChunks.isNotEmpty()
            val moreToPull = response.hasMoreEvents || response.hasMoreTypes
            if (!moreToPush && !moreToPull) break
            // A server claiming "more" without ever advancing a cursor would spin this loop forever —
            // treat no progress with nothing left to push as drained rather than trusting the flag.
            if (!moreToPush && response.nextSeq == sinceSeq && response.nextTypeSeq == sinceTypeSeq) break
        }
    }

    private companion object {
        /** Upload chunk size — mirrors the server's pull page size so both directions stay bounded. */
        const val DEFAULT_SYNC_BATCH_SIZE = 500
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun PointEvent.toDto(ownerId: String) = PointEventDto(
    id = id.toString(),
    ownerId = ownerId,
    pointTypeId = pointTypeId.toString(),
    delta = delta,
    deviceId = deviceId,
    createdAt = createdAt.toString(),
)

// Uuid.parse, Instant.parse, and enum valueOf all signal a malformed field as IllegalArgumentException;
// the *OrNull mappers fold that to null so the sync merge can skip the record instead of failing the batch.
@OptIn(ExperimentalUuidApi::class)
private fun PointEventDto.toPointEventOrNull(): PointEvent? = try {
    PointEvent(
        id = Uuid.parse(id),
        pointTypeId = Uuid.parse(pointTypeId),
        delta = delta,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
    )
} catch (_: IllegalArgumentException) {
    null
}

@OptIn(ExperimentalUuidApi::class)
private fun PointType.toDto(ownerId: String) = PointTypeDto(
    id = id.toString(),
    ownerId = ownerId,
    name = name,
    hue = hue,
    icon = icon,
    mode = mode.name,
    step = step,
    goal = goal.name,
    target = target,
    unit = unit,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

@OptIn(ExperimentalUuidApi::class)
private fun PointTypeDto.toPointTypeOrNull(): PointType? = try {
    PointType(
        id = Uuid.parse(id),
        name = name,
        hue = hue,
        icon = icon,
        mode = PointMode.valueOf(mode),
        step = step,
        goal = PointGoal.valueOf(goal),
        target = target,
        unit = unit,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        deletedAt = deletedAt?.let { Instant.parse(it) },
    )
} catch (_: IllegalArgumentException) {
    null
}
