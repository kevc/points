package com.points.core.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RingMathTest {

    private fun type(
        mode: PointMode = PointMode.CUMULATIVE,
        goal: PointGoal = PointGoal.UP,
        target: Long? = null,
    ) = PointType(
        id = Uuid.random(),
        name = "X",
        hue = 152,
        icon = "spark",
        mode = mode,
        step = 1,
        goal = goal,
        target = target,
        unit = "",
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )

    // ── milestone ladder ──────────────────────────────────────────────────────

    @Test
    fun milestoneClimbsThe125Ladder() {
        // (value) -> expected (prev, next, climbingNext) from the design's worked examples
        assertEquals(Triple(20L, 50L, 50L), milestoneOf(42).let { Triple(it.prev, it.next, it.climbingNext) })
        assertEquals(Triple(100L, 200L, 200L), milestoneOf(182).let { Triple(it.prev, it.next, it.climbingNext) })
        assertEquals(Triple(2000L, 5000L, 5000L), milestoneOf(4030).let { Triple(it.prev, it.next, it.climbingNext) })
    }

    @Test
    fun progressMatchesTheDesignPercentages() {
        assertEquals(0.73f, milestoneOf(42).progress, 0.01f) // 73% to 50
        assertEquals(0.82f, milestoneOf(182).progress, 0.01f) // 82% to 200
        assertEquals(0.68f, milestoneOf(4030).progress, 0.01f) // 68% to 5,000
    }

    @Test
    fun smallCountsFillInsteadOfReadingEmpty() {
        // The old `step = mag/2` bug left counts < 10 permanently empty. Books at 5 is a completed band.
        val books = milestoneOf(5)
        assertEquals(1f, books.progress)
        assertEquals(2L, books.prev)
        assertEquals(5L, books.next)
        assertEquals(10L, books.climbingNext) // landing on a milestone fills, then the goal advances
    }

    @Test
    fun zeroIsAnEmptyRingClimbingToOne() {
        val m = milestoneOf(0)
        assertEquals(0f, m.progress)
        assertEquals(1L, m.climbingNext)
    }

    @Test
    fun negativeValuesClampToZero() {
        assertEquals(0f, milestoneOf(-5).progress)
    }

    // ── ring states ───────────────────────────────────────────────────────────

    @Test
    fun cumulativeRingIsMonochromeMilestoneProgress() {
        val ring = ringStateOf(type(mode = PointMode.CUMULATIVE), value = 42, daysSinceLast = 0)
        assertEquals(milestoneOf(42).progress, ring.progress)
        assertEquals(4, ring.ticks)
        assertFalse(ring.useAccent)
        assertFalse(ring.over)
    }

    @Test
    fun dailyTargetRingFillsWithOneTickPerUnitWhenSmall() {
        val water = type(mode = PointMode.DAILY, target = 8)
        val ring = ringStateOf(water, value = 5, daysSinceLast = 0)
        assertEquals(5f / 8f, ring.progress)
        assertEquals(8, ring.ticks, "a small daily target shows one tick per unit")
        assertTrue(ring.useAccent)
        assertFalse(ring.over)
    }

    @Test
    fun dailyTargetRingMarksOverTargetAndClampsToFull() {
        val water = type(mode = PointMode.DAILY, target = 8)
        val ring = ringStateOf(water, value = 11, daysSinceLast = 0)
        assertEquals(1f, ring.progress, "fill clamps at full")
        assertTrue(ring.over)
    }

    @Test
    fun largeDailyTargetFallsBackToQuarterTicks() {
        val ring = ringStateOf(type(mode = PointMode.DAILY, target = 50), value = 10, daysSinceLast = 0)
        assertEquals(4, ring.ticks)
    }

    @Test
    fun easingRingIsARecencyGaugeNotACount() {
        val anger = type(mode = PointMode.CUMULATIVE, goal = PointGoal.DOWN)
        // value is irrelevant for an easing gauge — only days-since matters.
        assertEquals(5f / 30f, ringStateOf(anger, value = 999, daysSinceLast = 5).progress)
        assertEquals(1f, ringStateOf(anger, value = 0, daysSinceLast = 40).progress, "caps at 30 days")
        assertTrue(ringStateOf(anger, value = 12, daysSinceLast = 5).useAccent)
    }

    @Test
    fun dailyWithoutTargetUsesTheMilestoneGauge() {
        // A daily tally with the target turned off is just a plain gauge, not a target ring.
        val ring = ringStateOf(type(mode = PointMode.DAILY, target = null), value = 5, daysSinceLast = 0)
        assertEquals(milestoneOf(5).progress, ring.progress)
        assertFalse(ring.useAccent)
    }
}
