package com.points.core.domain

import kotlinx.coroutines.flow.Flow

/**
 * The home-screen display model for one point type: its current [value] (mode-aware — a daily type's today
 * count, a cumulative type's running total) and the derived [ring]. Carries the raw inputs the presentation
 * layer needs to format the tile's caption ([daysSinceLast] for an easing gauge, [climbingNext] for the
 * "→ N" milestone label), keeping user-facing copy out of the domain.
 */
data class PointTile(
    val type: PointType,
    val value: Long,
    val ring: RingState,
    val daysSinceLast: Int,
    val climbingNext: Long,
)

/** Streams the home grid: one [PointTile] per active point type, re-emitting as types or values change. */
fun interface ObserveTiles {
    operator fun invoke(): Flow<List<PointTile>>
}
