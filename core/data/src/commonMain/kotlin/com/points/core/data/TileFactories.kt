@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)

package com.points.core.data

import com.points.core.domain.ObserveTiles
import com.points.core.domain.PointAggregate
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Builds [ObserveTiles] — the home read model. It combines the active type list with one grouped-aggregate
 * flow (all types' mode-aware value + recency in a single query), then recomputes each ring via the pure
 * domain math so the home tiles can't drift from the M5 trend. The single aggregate flow means a ledger write
 * re-emits once, not once per tile (the read-path N+1 this collapses).
 *
 * Observe-only (no `withContext`): the underlying query already runs on the data source's query context.
 *
 * A daily type's "today" window and the current time zone are re-derived on **every** emission, and the window
 * re-rolls on each local-midnight [dayTicks] pulse — not captured once at subscription — so the grid rolls live
 * at midnight and reflects a mid-run timezone change (issue #108). [zone] is read live per emission; a tick
 * re-runs the grouped query with the new day cutoff.
 */
fun observeTiles(
    types: PointTypeRepository,
    points: PointRepository,
    clock: Clock,
    zone: ZoneProvider,
    dayTicks: Flow<Unit>,
): ObserveTiles = ObserveTiles {
    // Re-subscribe the grouped aggregate with a fresh local-midnight cutoff whenever the day ticks over.
    val aggregatesToday = dayTicks.flatMapLatest {
        points.observeAggregates(startOfTodayMillis(clock, zone()))
    }
    combine(types.observeTypes(), aggregatesToday) { list, aggregates ->
        val now = clock.now()
        list.map { type ->
            val agg = aggregates[type.id] ?: PointAggregate.Empty
            val value = if (type.mode == PointMode.DAILY) agg.todayTotal else agg.total
            val lastActivity = if (type.goal == PointGoal.DOWN) agg.lastPositiveAt else null
            tile(type, value, lastActivity, now)
        }
    }
}

private fun tile(type: PointType, value: Long, lastActivity: Long?, now: Instant): PointTile {
    // No positive event ever → maximally "calm" (the easing gauge reads full); irrelevant for other modes.
    val daysSinceLast = lastActivity
        ?.let { ((now.toEpochMilliseconds() - it) / MILLIS_PER_DAY).coerceAtLeast(0L).toInt() }
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
