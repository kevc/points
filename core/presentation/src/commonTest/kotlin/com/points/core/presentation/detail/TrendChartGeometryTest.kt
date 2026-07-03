package com.points.core.presentation.detail

import com.points.core.domain.PointEvent
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTrend
import com.points.core.domain.PointType
import com.points.core.domain.pointTrendOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TrendChartGeometryTest {

    private val now = Instant.parse("2026-07-03T12:00:00Z")

    private fun trend(
        value: Long,
        mode: PointMode = PointMode.CUMULATIVE,
        goal: PointGoal = PointGoal.UP,
        target: Long? = null,
    ): PointTrend {
        val typeId = Uuid.random()
        val type = PointType(
            id = typeId, name = "X", hue = 152, icon = "spark", mode = mode, step = 1,
            goal = goal, target = target, unit = "",
            createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
        )
        val events = listOf(PointEvent(Uuid.random(), typeId, value, "device-test", now))
        return pointTrendOf(type, events, now, TimeZone.UTC)
    }

    // --- chartYDomain -------------------------------------------------------------------------------------

    @Test
    fun cumulativeDomainHugsTheDataWithTwentyTwoPercentPadding() {
        val domain = trend(value = 128).chartYDomain(line = listOf(100L, 110L, 120L, 128L))
        val pad = (128 - 100) * 0.22
        assertEquals(100 - pad, domain.min, absoluteTolerance = 1e-9, "not zero-based — hugs the data")
        assertTrue(domain.max >= 128 + pad)
    }

    @Test
    fun aFlatCumulativeLineStillGetsAVisibleWindow() {
        val domain = trend(value = 100).chartYDomain(line = listOf(100L, 100L, 100L))
        assertEquals(99.0, domain.min, absoluteTolerance = 1e-9, "flat data pads by the minimum of 1")
        assertTrue(domain.max > domain.min)
    }

    @Test
    fun aCloseMilestoneStretchesTheDomainToInclude() {
        // Value 128 → next 1-2-5 milestone 200; data range 28, so 200 <= 128 + 28*0.7 is false → no stretch;
        // value 96 → milestone 100, data range 16: 100 <= 96 + 11.2 → stretch to at least the milestone.
        val trend = trend(value = 96)
        val domain = trend.chartYDomain(line = listOf(80L, 88L, 96L))
        assertTrue(domain.max >= 100.0, "milestone 100 pulled into view")
    }

    @Test
    fun aFarMilestoneDoesNotDistortTheDomain() {
        val trend = trend(value = 128)
        val domain = trend.chartYDomain(line = listOf(100L, 110L, 120L, 128L))
        val pad = (128 - 100) * 0.22
        assertEquals(128 + pad, domain.max, absoluteTolerance = 1e-9, "milestone 200 is too far — domain hugs the data")
    }

    @Test
    fun dailyDomainGroundsAtZeroWithHeadroom() {
        val domain = trend(value = 5, mode = PointMode.DAILY).chartYDomain(line = listOf(2L, 4L, 5L))
        assertEquals(0.0, domain.min)
        assertEquals(5 * 1.18, domain.max, absoluteTolerance = 1e-9)
    }

    @Test
    fun dailyTargetAboveTheDataExtendsTheDomain() {
        val domain = trend(value = 3, mode = PointMode.DAILY, target = 8)
            .chartYDomain(line = listOf(1L, 2L, 3L))
        assertEquals(8 * 1.12, domain.max, absoluteTolerance = 1e-9, "leaves room for the target line")
    }

    // --- chartGoalLine ------------------------------------------------------------------------------------

    @Test
    fun climbingCumulativeChasesTheNextMilestone() {
        assertEquals(200L, trend(value = 128).chartGoalLine(), "128 climbs toward 200 on the 1-2-5 ladder")
    }

    @Test
    fun easingPointsCarryNoGoalLine() {
        assertNull(trend(value = 128, goal = PointGoal.DOWN).chartGoalLine(), "nothing to chase when letting go")
    }

    @Test
    fun dailyGoalLineIsTheTargetWhenSet() {
        assertEquals(8L, trend(value = 3, mode = PointMode.DAILY, target = 8).chartGoalLine())
        assertNull(trend(value = 3, mode = PointMode.DAILY).chartGoalLine())
    }

    // --- scrubIndexOf -------------------------------------------------------------------------------------

    @Test
    fun scrubSnapsToTheNearestIndexAndClamps() {
        // 7 points inset 8 units in a 320-unit chart → step ≈ 50.67.
        assertEquals(0, scrubIndexOf(x = 8.0, width = 320.0, padX = 8.0, count = 7))
        assertEquals(3, scrubIndexOf(x = 160.0, width = 320.0, padX = 8.0, count = 7))
        assertEquals(6, scrubIndexOf(x = 312.0, width = 320.0, padX = 8.0, count = 7))
        assertEquals(0, scrubIndexOf(x = -50.0, width = 320.0, padX = 8.0, count = 7), "clamps below")
        assertEquals(6, scrubIndexOf(x = 999.0, width = 320.0, padX = 8.0, count = 7), "clamps above")
        assertEquals(0, scrubIndexOf(x = 160.0, width = 320.0, padX = 8.0, count = 1), "single point")
    }

    // --- dateLabelOf --------------------------------------------------------------------------------------

    @Test
    fun dateLabelsReadNaturally() {
        val today = LocalDate(2026, 7, 3)
        assertEquals("Today", dateLabelOf(LocalDate(2026, 7, 3), today))
        assertEquals("Yesterday", dateLabelOf(LocalDate(2026, 7, 2), today))
        assertEquals("May 20", dateLabelOf(LocalDate(2026, 5, 20), today))
        assertEquals("Dec 31", dateLabelOf(LocalDate(2025, 12, 31), today))
    }
}
