package com.points.core.data

import com.points.core.database.LocalEventDataSource
import com.points.core.database.LocalPointTypeDataSource
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
 * [sync] is the one batch round trip for both halves: it uploads pending events **and** pending type changes
 * and pulls the ones this device is missing, then advances both cursors. Events merge additively (union by
 * id, PN-counter CRDT); types merge **last-write-wins by `updatedAt`** (a type is mutable, so the newer edit
 * wins — see [PointType]). Deleting a type is a tombstone, never a destructive remove.
 */
@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointRepository(
    private val local: LocalEventDataSource,
    private val types: LocalPointTypeDataSource,
    private val api: PointsApiService,
    private val clock: Clock = Clock.System,
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

    override fun observeValue(pointTypeId: Uuid): Flow<Long> = local.observeValue(pointTypeId)

    override fun observeValueSince(pointTypeId: Uuid, sinceMillis: Long): Flow<Long> =
        local.observeValueSince(pointTypeId, sinceMillis)

    override fun observeLastActivity(pointTypeId: Uuid): Flow<Long?> = local.observeLastActivity(pointTypeId)

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

    override fun observeTypes(): Flow<List<PointType>> = types.observeTypes()

    override suspend fun sync() {
        val pendingEvents = local.pendingEvents()
        val pendingTypes = types.pendingTypes()
        val response = api.sync(
            SyncRequestDto(
                ownerId = local.ownerId,
                sinceSeq = local.syncCursor(),
                events = pendingEvents.map { it.toDto(local.ownerId) },
                sinceTypeSeq = types.typeCursor(),
                pointTypes = pendingTypes.map { it.toDto(local.ownerId) },
            ),
        )

        // Event half: union by id, advance the event cursor.
        response.events.forEach { local.applySynced(it.toPointEvent()) }
        local.clearPending(pendingEvents.map { it.id.toString() })
        local.setCursor(response.nextSeq)

        // Type half: last-write-wins by updatedAt, advance the type cursor.
        response.pointTypes.forEach { types.applySynced(it.toPointType()) }
        types.clearPending(pendingTypes.map { it.id.toString() })
        types.setTypeCursor(response.nextTypeSeq)
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

@OptIn(ExperimentalUuidApi::class)
private fun PointEventDto.toPointEvent() = PointEvent(
    id = Uuid.parse(id),
    pointTypeId = Uuid.parse(pointTypeId),
    delta = delta,
    deviceId = deviceId,
    createdAt = Instant.parse(createdAt),
)

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
private fun PointTypeDto.toPointType() = PointType(
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
