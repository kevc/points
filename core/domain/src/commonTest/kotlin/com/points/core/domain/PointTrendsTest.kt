package com.points.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The M5 trend aggregations, tested as pure functions over fixture ledgers. "Today" is fixed at Monday
 * 2026-06-15 12:00 UTC so day/week bucketing and weekday labels are deterministic.
 */
@OptIn(ExperimentalUuidApi::class)
class PointTrendsTest {

    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-06-15T12:00:00Z") // a Monday
    private val typeId = Uuid.parse("00000000-0000-0000-0000-0000000000aa")

    private fun type(
        mode: PointMode = PointMode.CUMULATIVE,
        goal: PointGoal = PointGoal.UP,
        target: Long? = null,
    ) = PointType(
        id = typeId, name = "T", hue = 0, icon = "spark", mode = mode, step = 1,
        goal = goal, target = target, unit = "", createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun evt(instant: String, delta: Long) =
        PointEvent(Uuid.random(), typeId, delta, "dev", Instant.parse(instant))

    private fun day(date: String, delta: Long) = evt("${date}T12:00:00Z", delta)

    private fun trend(
        events: List<PointEvent>,
        type: PointType = type(),
        zone: TimeZone = utc,
        at: Instant = now,
    ) = pointTrendOf(type, events, at, zone)

    // --- bucketing & value -----------------------------------------------------------------------------

    @Test
    fun cumulativeValueIsTheAllTimeSumAndDailyGapFilled() {
        val t = trend(listOf(day("2026-06-13", 10), day("2026-06-15", 20)))
        assertEquals(30L, t.value)
        assertEquals(30L, t.cumulative)
        assertEquals(LocalDate(2026, 6, 13), t.startDate)
        assertEquals(listOf(10L, 0L, 20L), t.daily) // 13th, 14th (gap), 15th
        assertEquals(LocalDate(2026, 6, 15), t.today)
    }

    @Test
    fun dailyValueIsOnlyTodaysBucket() {
        val t = trend(listOf(day("2026-06-14", 3), day("2026-06-15", 5)), type = type(mode = PointMode.DAILY))
        assertEquals(5L, t.value)
        assertEquals(8L, t.cumulative)
    }

    @Test
    fun emptyLedgerYieldsASingleTodayBucketOfZero() {
        val t = trend(emptyList())
        assertEquals(0L, t.value)
        assertEquals(listOf(0L), t.daily)
        assertEquals(LocalDate(2026, 6, 15), t.startDate)
    }

    @Test
    fun bucketingHonorsTheTimeZone() {
        // 23:30Z on the 14th is already the 15th in Tokyo (+09:00, no DST).
        val e = listOf(evt("2026-06-14T23:30:00Z", 1))
        val daily = type(mode = PointMode.DAILY)
        assertEquals(0L, trend(e, type = daily, zone = utc).value, "the 14th's event is not in the UTC today")
        assertEquals(1L, trend(e, type = daily, zone = TimeZone.of("Asia/Tokyo")).value, "it is in Tokyo's today")
    }

    @Test
    fun futureDatedEventsFoldIntoTodaySoInvariantsHold() {
        val t = trend(listOf(evt("2026-06-16T00:00:00Z", 5)), type = type(mode = PointMode.DAILY))
        assertEquals(5L, t.value, "a future-dated event still counts in today's bucket")
        assertEquals(LocalDate(2026, 6, 15), t.today)
    }

    // --- series ----------------------------------------------------------------------------------------

    @Test
    fun weekSeriesHasSevenDailyBucketsWithCumulativeLineAndWeekdayLabels() {
        val t = trend(listOf(day("2026-06-08", 5), day("2026-06-10", 2), day("2026-06-15", 3)))
        val s = t.series(TrendRange.WEEK) // window is the 9th..15th; the 8th is baseline for the line
        assertEquals(listOf(0L, 2L, 0L, 0L, 0L, 0L, 3L), s.bars)
        assertEquals(listOf(5L, 7L, 7L, 7L, 7L, 7L, 10L), s.line)
        assertEquals(listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon"), s.labels)
    }

    @Test
    fun monthSeriesHasThirtyBuckets() {
        val s = trend(listOf(day("2026-06-15", 4))).series(TrendRange.MONTH)
        assertEquals(30, s.bars.size)
        assertEquals(30, s.line.size)
        assertEquals(30, s.labels.size)
        assertEquals(4L, s.bars.last())
        assertEquals("15", s.labels.last())
    }

    @Test
    fun dailyModeLineIsRawBucketsNotCumulative() {
        val t = trend(listOf(day("2026-06-14", 3), day("2026-06-15", 5)), type = type(mode = PointMode.DAILY))
        val s = t.series(TrendRange.WEEK)
        assertEquals(s.bars, s.line, "a daily point's line is its raw per-day values")
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 3L, 5L), s.bars)
    }

    @Test
    fun yearSeriesHasFiftyTwoWeeklyBucketsEndingToday() {
        val t = trend(listOf(day("2026-06-09", 2), day("2026-06-15", 3)))
        val s = t.series(TrendRange.YEAR)
        assertEquals(52, s.bars.size)
        assertEquals(5L, s.bars.last(), "both events fall in the final rolling 7-day bucket")
        assertTrue(s.bars.dropLast(1).all { it == 0L })
        assertEquals(5L, s.line.last())
        assertEquals("Jun", s.labels.last())
    }

    // --- windows & stats -------------------------------------------------------------------------------

    @Test
    fun deltaWindowSumsTheLastNDays() {
        val t = trend(listOf(day("2026-06-01", 10), day("2026-06-15", 5)))
        assertEquals(5L, t.deltaWindow(7), "only the 15th is inside the last 7 days")
        assertEquals(15L, t.deltaWindow(30))
        assertEquals(5L, t.deltaThisWeek)
    }

    @Test
    fun bestDayAndActiveDaysCountNetDays() {
        val t = trend(listOf(day("2026-06-10", 3), day("2026-06-10", 5), day("2026-06-12", -1), day("2026-06-15", 2)))
        assertEquals(8L, t.bestDay, "the 10th nets +8")
        assertEquals(3, t.activeDays, "the 10th, 12th, and 15th each moved")
    }

    @Test
    fun recentLogReturnsNewestNonZeroDaysUpToTheLimit() {
        val t = trend(listOf(day("2026-06-10", 8), day("2026-06-12", -1), day("2026-06-15", 2)))
        assertEquals(
            listOf(LogEntry(LocalDate(2026, 6, 15), 2), LogEntry(LocalDate(2026, 6, 12), -1)),
            t.recentLog(2),
        )
        assertEquals(3, t.recentLog(10).size)
    }

    @Test
    fun daysSinceLastCountsFromTheMostRecentPositiveDay() {
        assertEquals(5, trend(listOf(day("2026-06-10", 1))).daysSinceLast)
        assertEquals(Int.MAX_VALUE, trend(listOf(day("2026-06-10", -1))).daysSinceLast, "no positive day → fully calm")
    }

    // --- heatmap ---------------------------------------------------------------------------------------

    @Test
    fun heatmapIsWeekColumnsBySevenDayRowsAlignedToToday() {
        val t = trend(listOf(day("2026-06-09", 1), day("2026-06-15", 4)))
        val h = t.heatmap(weeksBack = 2)
        assertEquals(2, h.columns.size)
        assertTrue(h.columns.all { it.size == 7 })
        // Monday-based: today (the 15th) is the first row of the last column.
        assertEquals(HeatmapCell(LocalDate(2026, 6, 15), 4), h.columns[1][0])
        assertEquals(HeatmapCell(LocalDate(2026, 6, 9), 1), h.columns[0][1]) // the 9th is a Tuesday
        assertEquals(0L, h.columns[1][1].value, "the 16th is in the future → empty track")
        assertEquals(LocalDate(2026, 6, 16), h.columns[1][1].date)
    }

    @Test
    fun heatmapDefaultsToTwentyWeeks() {
        assertEquals(20, trend(listOf(day("2026-06-15", 1))).heatmap().columns.size)
    }

    // --- insight ---------------------------------------------------------------------------------------

    @Test
    fun climbingInsightProjectsDaysToNextMilestoneFromThe14DayRate() {
        val t = trend(listOf(day("2026-06-10", 8))) // value 8, rate 8/14/day, next milestone 10
        assertEquals(Insight("~4 days to 10 at this pace", InsightTone.CLIMBING), t.insight)
    }

    @Test
    fun climbingInsightFallsBackWhenNoRecentActivity() {
        val t = trend(listOf(day("2026-06-01", 5))) // outside the last 14 days → no pace
        assertEquals(Insight("Climbing steadily", InsightTone.CLIMBING), t.insight)
    }

    @Test
    fun easingInsightComparesThisMonthToTheMonthBefore() {
        val down = type(goal = PointGoal.DOWN)
        val fewer = trend(listOf(day("2026-06-10", 3), day("2026-05-01", 8)), type = down)
        assertEquals(Insight("5 fewer this month than the month before — easing", InsightTone.EASING), fewer.insight)

        val more = trend(listOf(day("2026-06-10", 8), day("2026-05-01", 3)), type = down)
        assertEquals(Insight("5 more this month — just noticing, no pressure", InsightTone.NEUTRAL), more.insight)

        val steady = trend(listOf(day("2026-06-10", 4), day("2026-05-01", 4)), type = down)
        assertEquals(Insight("Steady — about the same as the month before", InsightTone.STEADY), steady.insight)
    }

    @Test
    fun dailyWithTargetInsightReportsAverageAndPercentOfTarget() {
        val t = trend(
            listOf(day("2026-06-14", 5), day("2026-06-15", 8)),
            type = type(mode = PointMode.DAILY, target = 8),
        )
        assertEquals(Insight("6.5 of 8 a day on average · 81% of target", InsightTone.DAILY), t.insight)
    }

    @Test
    fun dailyWithoutTargetInsightReportsAverageOnly() {
        val t = trend(
            listOf(day("2026-06-14", 3), day("2026-06-15", 5)),
            type = type(mode = PointMode.DAILY),
        )
        assertEquals(Insight("4.0 a day on average", InsightTone.DAILY), t.insight)
    }
}
