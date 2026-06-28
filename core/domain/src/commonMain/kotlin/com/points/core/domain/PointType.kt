package com.points.core.domain

import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A thing being counted (e.g. "days since quitting", "times meditated"): the mutable metadata that gives a
 * ledger of [PointEvent]s its identity (name, color, how it's counted).
 *
 * Unlike the additive event ledger, a point type is **mutable** — it can be renamed and recolored — so it
 * syncs as a **last-write-wins register**, not a union: every edit bumps [updatedAt], and on conflict the
 * row with the newer [updatedAt] wins (commutative, idempotent, no merge prompts). Deletion is a
 * **tombstone**: [deletedAt] is set rather than the row erased, so the events that reference it stay
 * meaningful and sync stays conflict-free. [isActive] is the live/visible predicate.
 *
 * @property hue the point's accent, a color-wheel angle in degrees (0–359); the one place color appears.
 * @property icon a geometric glyph name from the design set (e.g. "leaf", "drop", "spark"); free-form so the
 *   set can grow without a schema change.
 * @property mode how the current value is read — a running [PointMode.CUMULATIVE] total or a [PointMode.DAILY]
 *   count that resets each day. [step] is how much one tap adds.
 * @property goal the emotional framing of a cumulative type ([PointGoal.UP] = climbing, [PointGoal.DOWN] =
 *   easing); never affects the math, only the copy/visualization. Always [PointGoal.UP] for daily types.
 * @property target an optional gentle daily goal (daily types only; null = a pressure-free tally).
 */
@OptIn(ExperimentalUuidApi::class)
data class PointType(
    val id: Uuid,
    val name: String,
    val hue: Int,
    val icon: String,
    val mode: PointMode,
    val step: Long,
    val goal: PointGoal,
    val target: Long?,
    val unit: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    /** True while the type is live (not tombstoned) — the predicate for what the home screen lists. */
    val isActive: Boolean get() = deletedAt == null
}

/** How a point type's current value is read from its ledger. */
enum class PointMode {
    /** A running total that climbs and never resets — value = `SUM(delta)` over all events. */
    CUMULATIVE,

    /** A count within the current day that gently resets each morning — value = today's `SUM(delta)`. */
    DAILY,
}

/** The non-judgmental framing of a cumulative type; affects copy/visualization only, never the count. */
enum class PointGoal {
    /** More feels like progress (e.g. days smoke-free, books read). */
    UP,

    /** Just noticing — no pressure, no red (e.g. times angry). */
    DOWN,
}
