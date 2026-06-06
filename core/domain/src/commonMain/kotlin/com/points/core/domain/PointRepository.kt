package com.points.core.domain

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Port for reading and appending to the event ledger. Outer modules (`core/data` over `core/database`
 * + `core/network`) implement it; the domain never knows how persistence or sync works.
 *
 * Use cases — not components — depend on this. The repository owns event identity (generates [PointEvent.id],
 * stamps the device and time); callers supply only the point type and delta.
 */
@OptIn(ExperimentalUuidApi::class)
interface PointRepository {
    /** Appends one increment/decrement and returns the persisted event. */
    suspend fun append(pointTypeId: Uuid, delta: Long): PointEvent

    /** Emits the current value (`SUM(delta)`) for [pointTypeId], re-emitting whenever it changes. */
    fun observeValue(pointTypeId: Uuid): Flow<Long>

    /** Uploads pending events and pulls missing ones, merging additively. Idempotent and best-effort. */
    suspend fun sync()
}
