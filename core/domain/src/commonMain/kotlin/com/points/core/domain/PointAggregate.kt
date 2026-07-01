package com.points.core.domain

/**
 * The aggregated ledger reads for one point type, produced for every type in a single grouped query so the
 * home grid renders N tiles from one observable instead of N per-type flows (the read-path N+1). Carries the
 * three raw aggregates a tile's ring needs; the mode-aware choice between them is the caller's (see the tile
 * read model):
 *
 * - [total] — `SUM(delta)` all-time; a cumulative tile's value.
 * - [todayTotal] — `SUM(delta)` at/after the day cutoff; a daily tile's "today" value.
 * - [lastPositiveAt] — epoch millis of the most recent positive event, or null if there is none; the recency
 *   input for an "easing" tile's gauge.
 */
data class PointAggregate(
    val total: Long,
    val todayTotal: Long,
    val lastPositiveAt: Long?,
) {
    companion object {
        /** A type with no ledger rows yet — absent from the grouped result, defaulted to zero by the caller. */
        val Empty = PointAggregate(total = 0, todayTotal = 0, lastPositiveAt = null)
    }
}
