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

    /** One-shot current value (`SUM(delta)`) for [pointTypeId] — used to compute a reset's compensating delta. */
    suspend fun currentValue(pointTypeId: Uuid): Long

    /** Emits the current value (`SUM(delta)`) for [pointTypeId], re-emitting whenever it changes. */
    fun observeValue(pointTypeId: Uuid): Flow<Long>

    /**
     * Emits [pointTypeId]'s full event ledger (ascending by time, then id), re-emitting on every change.
     * The M5 trend layer buckets these by local day; a whole-ledger read is fine for a counting app and keeps
     * day/timezone bucketing in pure Kotlin (see [com.points.core.domain.pointTrendOf]).
     */
    fun observeEvents(pointTypeId: Uuid): Flow<List<PointEvent>>

    /**
     * Emits every point type's aggregated values ([PointAggregate]) keyed by type id, computed in a single
     * grouped query and re-emitting once per ledger change — not once per observed type. Backs the home grid,
     * collapsing what would otherwise be a per-tile SUM/recency flow each. [sinceMillis] is the day cutoff for
     * the [PointAggregate.todayTotal] window (caller passes local midnight; `0` folds today into all-time). A
     * type with no events yet is absent from the map ([PointAggregate.Empty]).
     */
    fun observeAggregates(sinceMillis: Long): Flow<Map<Uuid, PointAggregate>>

    /** Uploads pending events and pulls missing ones, merging additively. Idempotent and best-effort. */
    suspend fun sync()
}
