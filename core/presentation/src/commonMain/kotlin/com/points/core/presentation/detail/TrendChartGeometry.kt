package com.points.core.presentation.detail

import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTrend
import com.points.core.domain.milestoneOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlin.math.roundToInt

/**
 * Shared geometry for the Detail trend chart — the design's "reflective instrument" rules, kept here so the
 * Compose and SwiftUI renderers can't drift. All of it is pure math over the [PointTrend] snapshot; the
 * renderers only map these numbers to pixels.
 */

/** The trend chart's y-axis window. Never zero-height: [max] is always strictly above [min]. */
data class ChartYDomain(val min: Double, val max: Double) {
    val span: Double get() = max - min
}

/**
 * The adaptive y-domain for a trend [line]: it hugs the data so the *change* is visible, not the absolute
 * scale. Cumulative points pad ±22% around the data's min/max (never zero-based — a long climb should slope,
 * not flatline at the top), stretching a little further when the next milestone is close enough to be worth
 * showing ([chartGoalLine] within 70% of the data range above the max). Daily points ground at zero and leave
 * 18% headroom (12% above the target when one is set).
 */
fun PointTrend.chartYDomain(line: List<Long>): ChartYDomain {
    val dataMin = (line.minOrNull() ?: 0L).toDouble()
    val dataMax = (line.maxOrNull() ?: 0L).toDouble()
    var min: Double
    var max: Double
    if (type.mode == PointMode.CUMULATIVE) {
        val pad = maxOf(1.0, (dataMax - dataMin) * 0.22)
        min = dataMin - pad
        max = dataMax + pad
        val milestone = chartGoalLine()
        if (milestone != null && milestone <= dataMax + (dataMax - dataMin) * 0.7) {
            max = maxOf(max, milestone + pad * 0.3)
        }
    } else {
        min = 0.0
        max = maxOf(1.0, dataMax) * 1.18
        type.target?.let { target -> max = maxOf(max, target * 1.12) }
    }
    if (max - min < 1e-6) max = min + 1
    return ChartYDomain(min, max)
}

/**
 * The value of the dashed goal line, or null when the chart shouldn't carry one: the next 1‑2‑5 milestone for
 * a climbing cumulative point, the daily target when set, and nothing for an easing (goal-down) point — a
 * point you're letting go of has no line to chase. Renderers draw it only when it falls inside the y-domain.
 */
fun PointTrend.chartGoalLine(): Long? = when {
    type.mode == PointMode.DAILY -> type.target
    type.goal == PointGoal.DOWN -> null
    else -> milestoneOf(value).climbingNext
}

/**
 * Maps a touch [x] (in chart-local units, same space as [width]) to the nearest data index of a [count]-point
 * series inset by [padX] on both sides — the scrubber's snapping rule.
 */
fun scrubIndexOf(x: Double, width: Double, padX: Double, count: Int): Int {
    if (count <= 1) return 0
    val step = (width - padX * 2) / (count - 1)
    return ((x - padX) / step).roundToInt().coerceIn(0, count - 1)
}

private val MONTH_ABBREVS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * A short human label for a local [date] relative to [today]: "Today", "Yesterday", or "May 20". Used by the
 * scrubber tooltip and the recent-activity log. (English-only for now, like the domain's axis labels — #126.)
 */
fun dateLabelOf(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
    else -> "${MONTH_ABBREVS[date.month.number - 1]} ${date.dayOfMonth}"
}
