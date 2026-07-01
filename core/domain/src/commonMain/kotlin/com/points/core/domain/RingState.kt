package com.points.core.domain

/**
 * The visual state of a tile's ring gauge — derived, never stored. Pure data (no UI copy); the presentation
 * layer formats any caption from the underlying [PointTile] fields.
 *
 * @property progress fill fraction 0..1.
 * @property ticks number of scale ticks to draw on the track (a small daily target shows one tick per unit so
 *   the count is legible off the face; everything else uses 4 quarter ticks).
 * @property over true when a daily count has exceeded its target (draw the over-target marker; never "red").
 *
 * The arc is always painted in the point's hue — color is the tile's identity, applied consistently across
 * every gauge (climbing, easing, and daily-target alike).
 */
data class RingState(
    val progress: Float,
    val ticks: Int,
    val over: Boolean,
)

/**
 * The canonical ring gauge for a [type] at [value], with [daysSinceLast] = days since its last positive event
 * (only consulted for an "easing" type). Pure; shared by both platforms via the home store.
 *
 * - **daily + target** → fills toward the target, one tick per unit when small, with an over-target marker.
 * - **easing** (`goal == DOWN`) → a recency gauge: fuller = longer since the last tally ("Nd calm"), capped
 *   at 30 days. Never a count, so "times angry" never reads as a scoreboard.
 * - **cumulative climbing** → progress to the next 1‑2‑5 milestone (see [milestoneOf]).
 */
fun ringStateOf(type: PointType, value: Long, daysSinceLast: Int): RingState {
    val target = type.target
    if (type.mode == PointMode.DAILY && target != null) {
        return RingState(
            progress = if (target <= 0L) 0f else (value.toFloat() / target.toFloat()).coerceIn(0f, 1f),
            ticks = if (target in 1L..12L) target.toInt() else 4,
            over = value > target,
        )
    }
    if (type.goal == PointGoal.DOWN) {
        return RingState(
            progress = (daysSinceLast.toFloat() / 30f).coerceIn(0f, 1f),
            ticks = 4,
            over = false,
        )
    }
    return RingState(progress = milestoneOf(value).progress, ticks = 4, over = false)
}
