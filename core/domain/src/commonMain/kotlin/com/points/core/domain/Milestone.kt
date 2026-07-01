package com.points.core.domain

/**
 * The "nice number" band a value sits in, used to drive the tile ring as a **gauge of progress to the next
 * round number** rather than a raw count — so a counter at 5 and one at 4,030 share one visual language.
 *
 * Milestones come from the 1‑2‑5 ladder (1, 2, 5, 10, 20, 50, 100, …). One full ring = one band, which fixes
 * the point→radian scale at `2π / (next − prev)`. This is the canonical math shared by the home tiles, the
 * M5 trend milestone line, and the insight caption, so it lives in the domain and can't drift.
 *
 * @property prev the round number the band starts at (0 below the first milestone).
 * @property next the round number reaching which **fills** the ring (`firstNiceGE`, so landing exactly on a
 *   milestone reads as complete).
 * @property progress fill fraction within `[prev, next]`, clamped to 0..1.
 * @property step `next − prev`, the band width (points per full ring).
 * @property climbingNext the **strictly greater** next round number (`firstNiceGT`) — the climbing target for
 *   the "→ N" label, so hitting a milestone fills the ring and then the goal advances.
 */
data class Milestone(
    val prev: Long,
    val next: Long,
    val progress: Float,
    val step: Long,
    val climbingNext: Long,
)

// The 1‑2‑5 ladder up to 5e9 — far beyond any realistic counter, and well within Long.
private val NICE_LADDER: List<Long> = buildList {
    var p = 1L
    repeat(10) {
        add(1 * p)
        add(2 * p)
        add(5 * p)
        p *= 10
    }
}

private fun firstNiceGE(v: Long): Long = NICE_LADDER.firstOrNull { it >= v } ?: NICE_LADDER.last()
private fun firstNiceGT(v: Long): Long = NICE_LADDER.firstOrNull { it > v } ?: NICE_LADDER.last()
private fun lastNiceLT(v: Long): Long {
    var result = 0L
    for (n in NICE_LADDER) {
        if (n < v) result = n else break
    }
    return result
}

/** The [Milestone] band [value] sits in (negative values clamp to 0). Pure and order-independent. */
fun milestoneOf(value: Long): Milestone {
    val v = maxOf(0L, value)
    val next = firstNiceGE(maxOf(1L, v))
    val prev = lastNiceLT(next)
    val step = next - prev
    val progress = if (step <= 0L) 1f else ((v - prev).toFloat() / step.toFloat()).coerceIn(0f, 1f)
    return Milestone(prev = prev, next = next, progress = progress, step = step, climbingNext = firstNiceGT(v))
}
