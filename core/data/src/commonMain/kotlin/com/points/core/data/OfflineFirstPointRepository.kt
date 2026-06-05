package com.points.core.data

import com.points.core.database.LocalEventDataSource
import com.points.core.domain.PointEvent
import com.points.core.domain.PointRepository
import com.points.core.network.PointsApiService
import com.points.shared.contract.PointEventDto
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Offline-first [PointRepository]. Every increment/decrement is written to the local ledger first
 * (the source of truth for display), then pushed to the backend best-effort: a missing or failing
 * network is not an error — unsynced events reconcile on the next sync pass (M3). The observed value
 * comes from the local ledger so the UI updates immediately, online or off.
 */
@OptIn(ExperimentalUuidApi::class)
class OfflineFirstPointRepository(
    private val local: LocalEventDataSource,
    private val api: PointsApiService,
    private val deviceId: String,
    private val clock: Clock = Clock.System,
) : PointRepository {

    override suspend fun append(pointTypeId: Uuid, delta: Long): PointEvent {
        val event = PointEvent(
            id = Uuid.random(),
            pointTypeId = pointTypeId,
            delta = delta,
            deviceId = deviceId,
            createdAt = clock.now(),
        )
        local.insert(event)
        runCatching { api.postEvent(event.toDto()) }
        return event
    }

    override fun observeValue(pointTypeId: Uuid): Flow<Long> = local.observeValue(pointTypeId)
}

@OptIn(ExperimentalUuidApi::class)
private fun PointEvent.toDto() = PointEventDto(
    id = id.toString(),
    pointTypeId = pointTypeId.toString(),
    delta = delta,
    deviceId = deviceId,
    createdAt = createdAt.toString(),
)
