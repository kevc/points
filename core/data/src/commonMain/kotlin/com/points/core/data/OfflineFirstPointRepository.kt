package com.points.core.data

import com.points.core.database.LocalEventDataSource
import com.points.core.domain.PointEvent
import com.points.core.domain.PointRepository
import com.points.core.network.PointsApiService
import com.points.shared.contract.PointEventDto
import com.points.shared.contract.SyncRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Offline-first [PointRepository]. Every increment/decrement is written to the local ledger first (the
 * source of truth for display) and marked pending; the observed value comes from the local ledger so the
 * UI updates immediately, online or off. [append] does *not* sync — it only persists. Pushing the pending
 * event is the `SyncCoordinator`'s job: it observes the pending count and drives [sync], so the single sync
 * driver also owns the visible [com.points.core.domain.SyncStatus] (an append's push failure can no longer
 * be lost behind a stale "Synced", which is what happened when [append] swallowed its own best-effort sync).
 *
 * [sync] is the batch protocol: it uploads pending events and pulls the ones this device is missing
 * (`seq` greater than the stored cursor), unions them in, and advances the cursor. Because the value is
 * `SUM(delta)`, concurrent offline edits across devices merge additively (PN-counter style CRDT).
 */
@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointRepository(
    private val local: LocalEventDataSource,
    private val api: PointsApiService,
    private val clock: Clock = Clock.System,
) : PointRepository {

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

    override suspend fun sync() {
        val pending = local.pendingEvents()
        val response = api.sync(
            SyncRequestDto(
                ownerId = local.ownerId,
                sinceSeq = local.syncCursor(),
                events = pending.map { it.toDto(local.ownerId) },
            ),
        )
        response.events.forEach { local.applySynced(it.toPointEvent()) }
        local.clearPending(pending.map { it.id.toString() })
        local.setCursor(response.nextSeq)
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
