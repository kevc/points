@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)

package com.points.core.data

import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointRepository
import com.points.core.domain.PointTile
import com.points.core.domain.PointType
import com.points.core.domain.PointTypeRepository
import com.points.core.domain.milestoneOf
import com.points.core.domain.ringStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Builds [ObserveTiles] — the home read model. It reacts to the active type list and, per type, to that type's
 * mode-aware value (and recency, for an easing type), recomputing the ring via the pure domain math so the
 * home tiles can't drift from the M5 trend.
 *
 * Observe-only (no `withContext`): the underlying queries already run on the data source's query context.
 *
 * Note: a daily type's "today" window is pinned to local midnight at subscription time — it does not roll live
 * at midnight, but is recomputed whenever the grid is re-observed (e.g. the app is reopened/foregrounded),
 * which is sufficient for a counting app.
 */
fun observeTiles(
    types: PointTypeRepository,
    points: PointRepository,
    clock: Clock,
    zone: TimeZone,
): ObserveTiles = ObserveTiles {
    types.observeTypes().flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { type -> tileFlow(type, points, clock, zone) }) { tiles -> tiles.toList() }
        }
    }
}

private fun tileFlow(type: PointType, points: PointRepository, clock: Clock, zone: TimeZone): Flow<PointTile> {
    val valueFlow = if (type.mode == PointMode.DAILY) {
        points.observeValueSince(type.id, startOfTodayMillis(clock, zone))
    } else {
        points.observeValue(type.id)
    }
    return if (type.goal == PointGoal.DOWN) {
        combine(valueFlow, points.observeLastActivity(type.id)) { value, lastActivity ->
            tile(type, value, lastActivity, clock)
        }
    } else {
        valueFlow.map { value -> tile(type, value, lastActivity = null, clock) }
    }
}

private fun tile(type: PointType, value: Long, lastActivity: Long?, clock: Clock): PointTile {
    // No positive event ever → maximally "calm" (the easing gauge reads full); irrelevant for other modes.
    val daysSinceLast = lastActivity
        ?.let { ((clock.now().toEpochMilliseconds() - it) / MILLIS_PER_DAY).coerceAtLeast(0L).toInt() }
        ?: Int.MAX_VALUE
    return PointTile(
        type = type,
        value = value,
        ring = ringStateOf(type, value, daysSinceLast),
        daysSinceLast = daysSinceLast,
        climbingNext = milestoneOf(value).climbingNext,
    )
}

private fun startOfTodayMillis(clock: Clock, zone: TimeZone): Long =
    clock.now().toLocalDateTime(zone).date.atStartOfDayIn(zone).toEpochMilliseconds()
