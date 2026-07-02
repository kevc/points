@file:OptIn(ExperimentalUuidApi::class)

package com.points.core.data

import com.points.core.domain.ObservePointTrend
import com.points.core.domain.PointRepository
import com.points.core.domain.PointTypeRepository
import com.points.core.domain.pointTrendOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi

/**
 * Builds [ObservePointTrend] — the Detail screen's read model. It combines the point type (so a rename/recolor
 * re-emits), its full event ledger, and a [dayTicks] pulse, then buckets the ledger by local day via the pure
 * [pointTrendOf]. Because `now`/`zone` are read *inside* the transform on every emission — and [dayTicks] fires
 * at each local midnight — the day window rolls live rather than being pinned at subscription (issue #108).
 *
 * Observe-only (no `withContext`): the underlying queries already run on the data source's query context. A type
 * absent from the active catalog (unknown or tombstoned) emits nothing.
 */
fun observePointTrend(
    types: PointTypeRepository,
    points: PointRepository,
    clock: Clock,
    zone: ZoneProvider,
    dayTicks: Flow<Unit>,
): ObservePointTrend = ObservePointTrend { pointTypeId ->
    combine(
        types.observeTypes(),
        points.observeEvents(pointTypeId),
        dayTicks,
    ) { catalog, events, _ ->
        catalog.firstOrNull { it.id == pointTypeId }?.let { type ->
            pointTrendOf(type, events, clock.now(), zone())
        }
    }.filterNotNull()
}
