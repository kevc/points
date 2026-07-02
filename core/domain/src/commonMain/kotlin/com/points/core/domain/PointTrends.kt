package com.points.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The M5 trend layer: everything the Detail screen shows about how a point's count moves over time, derived
 * from the ledger the same way the design prototype's `data.jsx` derives it — from a single `daily[]` array
 * (net delta per **local** day, last index = today). In the real app that array is the ledger bucketed by
 * `SUM(delta)` over the local date of each event's `createdAt`; every stat below is a pure function of it.
 *
 * Purity & determinism: [pointTrendOf] takes the observation instant and time zone explicitly, so bucketing
 * is testable and reproducible. The observing flow (in `core/data`) re-derives them per emission and on each
 * local-midnight tick, so the window rolls live rather than being pinned at subscription (see issue #108).
 */
@OptIn(ExperimentalUuidApi::class)
data class PointTrend(
    /** The point this trend is for; its mode/goal/target drive the value rule, the chart line, and the copy. */
    val type: PointType,
    /** Local date of `daily[0]` — the first day with a ledger event, or today when there is none yet. */
    val startDate: LocalDate,
    /** Net delta per local day, gap-filled with zeros; `daily.last()` is always today. Never empty. */
    val daily: List<Long>,
    /** The current value: a cumulative type's all-time running total, or a daily type's today bucket. */
    val value: Long,
    /** Running total across all history (`sum(daily)`) — the cumulative chart line's endpoint. */
    val cumulative: Long,
    /** Net change over the last 7 days — the "+N this week" hero pill. */
    val deltaThisWeek: Long,
    /** The single busiest day's net delta (0 if nothing was ever logged). */
    val bestDay: Long,
    /** Count of days with any net change — the "active days" stat. */
    val activeDays: Int,
    /** Days since the most recent positive day; [Int.MAX_VALUE] when there has never been one (fully calm). */
    val daysSinceLast: Int,
    /** The tone-aware caption shown under the trend chart. */
    val insight: Insight,
) {
    /** The local date `daily.last()` sits on — today at the observation instant. */
    val today: LocalDate get() = startDate.plus(daily.size - 1, DateTimeUnit.DAY)
}

/** Which span a chart shows. */
enum class TrendRange {
    /** 7 daily buckets ending today. */
    WEEK,

    /** 30 daily buckets ending today. */
    MONTH,

    /** 52 weekly (rolling 7-day) buckets, the last ending today. */
    YEAR,
}

/**
 * One chart's data for a [TrendRange]: [bars] is the per-bucket net delta; [line] is the cumulative running
 * total at each bucket's end for a cumulative point, or the raw per-bucket values for a daily point; [labels]
 * is one axis label per bucket (weekday, day-of-month, or month — the UI thins them as needed). All three
 * lists are the same length and share an index.
 */
data class TrendSeries(
    val bars: List<Long>,
    val line: List<Long>,
    val labels: List<String>,
)

/** One heatmap cell: the local [date] and its net [value]. */
data class HeatmapCell(val date: LocalDate, val value: Long)

/**
 * A GitHub-style calendar: [columns] weeks × 7 rows (Monday→Sunday), aligned so today is the most recent
 * real cell of the last column. Cells before the first event or after today carry a zero value (faint track).
 */
data class Heatmap(val columns: List<List<HeatmapCell>>)

/** One recent-activity entry: a local [date] and its net [delta]. */
data class LogEntry(val date: LocalDate, val delta: Long)

/** The reflective caption under the trend chart — plain language, tuned per point, never scolding. */
data class Insight(val text: String, val tone: InsightTone)

/** The emotional register of an [Insight], for the presentation layer to style. Never negative. */
enum class InsightTone {
    /** Cumulative-up progress toward the next milestone. */
    CLIMBING,

    /** A down-goal count that fell versus the prior month. */
    EASING,

    /** About the same as before. */
    STEADY,

    /** A gentle, pressure-free observation (a down-goal count that rose). */
    NEUTRAL,

    /** A daily average, with or without a target. */
    DAILY,
}

/**
 * Builds the [PointTrend] for [type] from its full [events] ledger, bucketing by local day at [now] in [zone].
 * Pure and deterministic; the caller supplies the clock/zone so tests are reproducible and the observing flow
 * can re-derive them per emission (issue #108). Events dated after today (clock skew across devices) fold into
 * today so `sum(daily)` stays equal to the ledger sum and `daily.last()` stays today.
 */
@OptIn(ExperimentalUuidApi::class)
fun pointTrendOf(
    type: PointType,
    events: List<PointEvent>,
    now: Instant,
    zone: TimeZone,
): PointTrend {
    val today = now.toLocalDateTime(zone).date
    val byDay = HashMap<LocalDate, Long>()
    for (event in events) {
        val day = event.createdAt.toLocalDateTime(zone).date.let { if (it > today) today else it }
        byDay[day] = (byDay[day] ?: 0L) + event.delta
    }
    val startDate = byDay.keys.minOrNull()?.let { if (it < today) it else today } ?: today
    val span = startDate.daysUntil(today) + 1
    val daily = (0 until span).map { offset -> byDay[startDate.plus(offset, DateTimeUnit.DAY)] ?: 0L }

    val cumulative = daily.sum()
    val value = if (type.mode == PointMode.DAILY) daily.last() else cumulative
    val bestDay = daily.maxOrNull() ?: 0L
    val activeDays = daily.count { it != 0L }
    val lastPositive = daily.indexOfLast { it > 0L }
    val daysSinceLast = if (lastPositive < 0) Int.MAX_VALUE else daily.lastIndex - lastPositive

    val trend = PointTrend(
        type = type,
        startDate = startDate,
        daily = daily,
        value = value,
        cumulative = cumulative,
        deltaThisWeek = sumOfLast(daily, 7),
        bestDay = bestDay,
        activeDays = activeDays,
        daysSinceLast = daysSinceLast,
        insight = Insight("", InsightTone.NEUTRAL), // replaced below once the derived stats exist
    )
    return trend.copy(insight = trend.computeInsight())
}

/** Net change over the last [days] days (the pill uses `days = 7`). */
fun PointTrend.deltaWindow(days: Int): Long = sumOfLast(daily, days)

/**
 * The chart data for [range]. `bars` are per-bucket net deltas; `line` is the cumulative running total at each
 * bucket's end (cumulative points) or the raw bucket values (daily points); `labels` is one label per bucket.
 */
fun PointTrend.series(range: TrendRange): TrendSeries {
    val prefix = prefixSums(daily)
    val dailyLine = type.mode == PointMode.DAILY
    return when (range) {
        TrendRange.WEEK -> dayBuckets(count = 7, prefix = prefix, dailyLine = dailyLine) { it.weekdayAbbrev() }
        TrendRange.MONTH -> dayBuckets(count = 30, prefix = prefix, dailyLine = dailyLine) { it.dayOfMonth.toString() }
        TrendRange.YEAR -> weekBuckets(count = 52, prefix = prefix, dailyLine = dailyLine)
    }
}

/**
 * The calendar heatmap: [weeksBack] week columns × 7 day rows (Monday→Sunday), the last column ending today.
 * A cell's value is that day's net delta; cells before the first event or after today are zero.
 */
fun PointTrend.heatmap(weeksBack: Int = 20): Heatmap {
    val currentWeekMonday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val firstMonday = currentWeekMonday.minus((weeksBack - 1) * 7, DateTimeUnit.DAY)
    val columns = (0 until weeksBack).map { col ->
        (0 until 7).map { row ->
            val date = firstMonday.plus(col * 7 + row, DateTimeUnit.DAY)
            HeatmapCell(date, if (date > today) 0L else deltaOn(date))
        }
    }
    return Heatmap(columns)
}

/** The most recent [limit] days with a non-zero net delta, newest first — the activity log. */
fun PointTrend.recentLog(limit: Int): List<LogEntry> {
    if (limit <= 0) return emptyList()
    val log = ArrayList<LogEntry>(limit)
    var idx = daily.lastIndex
    while (idx >= 0 && log.size < limit) {
        val delta = daily[idx]
        if (delta != 0L) log += LogEntry(startDate.plus(idx, DateTimeUnit.DAY), delta)
        idx--
    }
    return log
}

// --- internals -------------------------------------------------------------------------------------------

private fun PointTrend.deltaOn(date: LocalDate): Long {
    val idx = startDate.daysUntil(date)
    return if (idx in daily.indices) daily[idx] else 0L
}

/** Prefix sums so a range/point lookup is O(1): `prefix[i]` = sum of `daily[0 until i]`, length `size + 1`. */
private fun prefixSums(daily: List<Long>): LongArray {
    val prefix = LongArray(daily.size + 1)
    for (i in daily.indices) prefix[i + 1] = prefix[i] + daily[i]
    return prefix
}

/** Cumulative total of every daily delta up to and including [date] (0 before the start, capped at the end). */
private fun PointTrend.cumulativeUpTo(date: LocalDate, prefix: LongArray): Long {
    val idx = startDate.daysUntil(date)
    return when {
        idx < 0 -> 0L
        idx >= daily.size -> prefix[daily.size]
        else -> prefix[idx + 1]
    }
}

private fun PointTrend.dayBuckets(
    count: Int,
    prefix: LongArray,
    dailyLine: Boolean,
    label: (LocalDate) -> String,
): TrendSeries {
    val bars = ArrayList<Long>(count)
    val line = ArrayList<Long>(count)
    val labels = ArrayList<String>(count)
    for (i in 0 until count) {
        val date = today.minus(count - 1 - i, DateTimeUnit.DAY)
        val bar = deltaOn(date)
        bars += bar
        line += if (dailyLine) bar else cumulativeUpTo(date, prefix)
        labels += label(date)
    }
    return TrendSeries(bars = bars, line = line, labels = labels)
}

private fun PointTrend.weekBuckets(count: Int, prefix: LongArray, dailyLine: Boolean): TrendSeries {
    val bars = ArrayList<Long>(count)
    val line = ArrayList<Long>(count)
    val labels = ArrayList<String>(count)
    for (i in 0 until count) {
        val end = today.minus((count - 1 - i) * 7, DateTimeUnit.DAY)
        val start = end.minus(6, DateTimeUnit.DAY)
        val bar = rangeSum(start, end, prefix)
        bars += bar
        line += if (dailyLine) bar else cumulativeUpTo(end, prefix)
        labels += end.month.abbrev()
    }
    return TrendSeries(bars = bars, line = line, labels = labels)
}

/** Sum of daily deltas over the inclusive local-date range `[start, end]`, clamped to the known window. */
private fun PointTrend.rangeSum(start: LocalDate, end: LocalDate, prefix: LongArray): Long {
    val lo = startDate.daysUntil(start).coerceIn(0, daily.size)
    val hi = (startDate.daysUntil(end) + 1).coerceIn(0, daily.size)
    return if (hi <= lo) 0L else prefix[hi] - prefix[lo]
}

private fun sumOfLast(daily: List<Long>, days: Int): Long {
    if (days <= 0) return 0L
    val from = (daily.size - days).coerceAtLeast(0)
    var sum = 0L
    for (i in from until daily.size) sum += daily[i]
    return sum
}

private fun PointTrend.computeInsight(): Insight = when {
    type.mode == PointMode.DAILY && type.target != null -> dailyTargetInsight(type.target)
    type.mode == PointMode.DAILY -> dailyInsight()
    type.goal == PointGoal.DOWN -> easingInsight()
    else -> climbingInsight()
}

private fun PointTrend.climbingInsight(): Insight {
    val rate = deltaWindow(14).toDouble() / 14.0 // net per day over the last two weeks
    val next = milestoneOf(value).climbingNext
    val remaining = next - value
    if (rate <= 0.0 || remaining <= 0) return Insight("Climbing steadily", InsightTone.CLIMBING)
    val days = ceil(remaining / rate).roundToLong()
    return Insight("~$days ${dayWord(days)} to $next at this pace", InsightTone.CLIMBING)
}

private fun PointTrend.easingInsight(): Insight {
    val thisMonth = deltaWindow(30)
    val monthBefore = deltaWindow(60) - thisMonth
    return when {
        thisMonth < monthBefore ->
            Insight("${monthBefore - thisMonth} fewer this month than the month before — easing", InsightTone.EASING)
        thisMonth > monthBefore ->
            Insight("${thisMonth - monthBefore} more this month — just noticing, no pressure", InsightTone.NEUTRAL)
        else -> Insight("Steady — about the same as the month before", InsightTone.STEADY)
    }
}

private fun PointTrend.dailyTargetInsight(target: Long): Insight {
    val avg = recentDailyAverage()
    val pct = if (target > 0) (avg / target * 100).roundToInt() else 0
    return Insight("${oneDecimal(avg)} of $target a day on average · $pct% of target", InsightTone.DAILY)
}

private fun PointTrend.dailyInsight(): Insight =
    Insight("${oneDecimal(recentDailyAverage())} a day on average", InsightTone.DAILY)

/** Mean net delta over the last 30 days, or fewer if the point is younger. */
private fun PointTrend.recentDailyAverage(): Double {
    val window = minOf(30, daily.size)
    return if (window == 0) 0.0 else deltaWindow(window).toDouble() / window
}

private fun dayWord(days: Long): String = if (days == 1L) "day" else "days"

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${abs(tenths % 10)}"
}

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun LocalDate.weekdayAbbrev(): String = WEEKDAYS[dayOfWeek.isoDayNumber - 1]
private fun kotlinx.datetime.Month.abbrev(): String = MONTHS[number - 1]

/**
 * Streams the [PointTrend] for a point, re-emitting on every ledger change **and** at each local midnight so
 * the day window rolls live (issue #108). Backs the Detail screen; the range/heatmap derivations above are
 * pure and applied synchronously by the component off the emitted snapshot.
 */
@OptIn(ExperimentalUuidApi::class)
fun interface ObservePointTrend {
    operator fun invoke(pointTypeId: Uuid): Flow<PointTrend>
}
