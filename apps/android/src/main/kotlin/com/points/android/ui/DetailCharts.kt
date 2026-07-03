@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.points.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.points.android.ui.theme.Hanken
import com.points.android.ui.theme.RingTrackDark
import com.points.android.ui.theme.RingTrackLight
import com.points.core.domain.Heatmap
import com.points.core.domain.PointMode
import com.points.core.domain.PointTrend
import com.points.core.domain.TrendRange
import com.points.core.presentation.detail.chartGoalLine
import com.points.core.presentation.detail.chartYDomain
import com.points.core.presentation.detail.dateLabelOf
import com.points.core.presentation.detail.scrubIndexOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Grouped integer formatting for chart labels and values ("4,030"). */
internal fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

internal fun formatCount(value: Double): String = formatCount(value.roundToLong())

/**
 * The trend "instrument" (design `charts.jsx` → `AreaTrend`): a smoothed accent line over a soft gradient
 * area, on an adaptive y-domain that hugs the data; gridlines at max/mid/min; a dashed goal line when the
 * next milestone/target is close enough to mean something; a today marker; and a touch scrubber that snaps
 * to the nearest day and reads its exact value and date. The line draws itself in over one second on mount.
 */
@Composable
fun AreaTrendChart(
    trend: PointTrend,
    line: List<Long>,
    range: TrendRange,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val domain = remember(trend, line) { trend.chartYDomain(line) }
    val goal = remember(trend) { trend.chartGoalLine() }
    val goalLabel = goal?.let { if (trend.type.mode == PointMode.DAILY) "target $it" else formatCount(it) }.orEmpty()

    val gridColor = MaterialTheme.colorScheme.surfaceContainerLow
    val faintColor = MaterialTheme.colorScheme.outline
    val tooltipBg = MaterialTheme.colorScheme.onSurface
    val tooltipFg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    var scrub by remember { mutableStateOf<Int?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun hideSoon() {
        hideJob?.cancel()
        hideJob = scope.launch { delay(900); scrub = null }
    }

    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(line) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(1f, tween(durationMillis = 1000))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp)
            // a press reads a day; a horizontal drag scrubs; vertical drags still scroll the screen
            .pointerInput(line) {
                detectTapGestures(onPress = { offset ->
                    hideJob?.cancel()
                    scrub = scrubIndexOf(offset.x.toDouble(), size.width.toDouble(), 8.dp.toPx().toDouble(), line.size)
                    tryAwaitRelease()
                    hideSoon()
                })
            }
            .pointerInput(line) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        hideJob?.cancel()
                        scrub = scrubIndexOf(offset.x.toDouble(), size.width.toDouble(), 8.dp.toPx().toDouble(), line.size)
                    },
                    onDragEnd = { hideSoon() },
                    onDragCancel = { hideSoon() },
                ) { change, _ ->
                    scrub = scrubIndexOf(change.position.x.toDouble(), size.width.toDouble(), 8.dp.toPx().toDouble(), line.size)
                    change.consume()
                }
            },
    ) {
        if (line.isEmpty()) return@Canvas
        val padX = 8.dp.toPx()
        val padT = 20.dp.toPx()
        val padB = 26.dp.toPx()
        val n = line.size
        val stepX = (size.width - padX * 2) / maxOf(1, n - 1)
        fun x(i: Int) = padX + i * stepX
        fun y(v: Double) = (padT + (1.0 - (v - domain.min) / domain.span) * (size.height - padT - padB)).toFloat()

        val pts = line.mapIndexed { i, v -> Offset(x(i), y(v.toDouble())) }
        val linePath = smoothPath(pts)
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(x(n - 1), size.height - padB)
            lineTo(x(0), size.height - padB)
            close()
        }

        // gridlines + y labels at max / mid / min
        val labelStyle = TextStyle(fontFamily = Hanken, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = faintColor)
        for (tick in listOf(domain.max, (domain.max + domain.min) / 2, domain.min)) {
            val ty = y(tick)
            drawLine(gridColor, Offset(padX, ty), Offset(size.width - padX, ty), strokeWidth = 1.dp.toPx())
            drawText(textMeasurer, formatCount(tick), Offset(padX, ty - 4.dp.toPx() - 10.sp.toPx()), labelStyle)
        }

        // dashed goal line (next milestone / daily target) when it falls inside the domain
        if (goal != null && goal <= domain.max) {
            val gy = y(goal.toDouble())
            drawLine(
                color = accent.copy(alpha = 0.6f),
                start = Offset(padX, gy),
                end = Offset(size.width - padX, gy),
                strokeWidth = 1.25.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )
            val goalStyle = TextStyle(fontFamily = Hanken, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
            val measured = textMeasurer.measure(AnnotatedString(goalLabel), goalStyle)
            drawText(measured, topLeft = Offset(size.width - padX - measured.size.width, gy - 5.dp.toPx() - measured.size.height))
        }

        // area fill + the animated line
        drawPath(
            areaPath,
            Brush.verticalGradient(0f to accent.copy(alpha = 0.22f), 1f to accent.copy(alpha = 0f)),
        )
        val visible = if (drawProgress.value >= 1f) {
            linePath
        } else {
            val measure = PathMeasure().apply { setPath(linePath, false) }
            Path().also { measure.getSegment(0f, measure.length * drawProgress.value, it, true) }
        }
        drawPath(visible, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val active = scrub
        if (active == null) {
            // today marker
            val last = pts.last()
            drawCircle(accent.copy(alpha = 0.16f), radius = 7.dp.toPx(), center = last)
            drawCircle(accent, radius = 3.5.dp.toPx(), center = last)
        } else {
            val p = pts[active]
            drawLine(accent.copy(alpha = 0.45f), Offset(p.x, padT), Offset(p.x, size.height - padB), strokeWidth = 1.25.dp.toPx())
            drawCircle(accent.copy(alpha = 0.16f), radius = 9.dp.toPx(), center = p)
            drawCircle(accent, radius = 4.dp.toPx(), center = p)
            drawCircle(surface, radius = 4.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))

            // tooltip: exact value + the day it belongs to
            val daysAgo = (if (range == TrendRange.YEAR) 7 else 1) * (n - 1 - active)
            val date = trend.today.minus(daysAgo, DateTimeUnit.DAY)
            val value = formatCount(line[active]) + if (trend.type.unit.isNotEmpty()) " ${trend.type.unit}" else ""
            drawTooltip(
                textMeasurer = textMeasurer,
                title = value,
                subtitle = dateLabelOf(date, trend.today),
                anchor = p,
                topBound = padT,
                background = tooltipBg,
                foreground = tooltipFg,
            )
        }
    }
}

private fun DrawScope.drawTooltip(
    textMeasurer: TextMeasurer,
    title: String,
    subtitle: String,
    anchor: Offset,
    topBound: Float,
    background: Color,
    foreground: Color,
) {
    val w = 96.dp.toPx()
    val h = 38.dp.toPx()
    val above = anchor.y - 52.dp.toPx() > topBound
    val tx = (anchor.x - w / 2).coerceIn(2.dp.toPx(), size.width - w - 2.dp.toPx())
    val ty = if (above) anchor.y - 52.dp.toPx() else anchor.y + 14.dp.toPx()
    drawRoundRect(background, topLeft = Offset(tx, ty), size = Size(w, h), cornerRadius = CornerRadius(9.dp.toPx()))
    val titleLayout = textMeasurer.measure(
        AnnotatedString(title),
        TextStyle(fontFamily = Hanken, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = foreground),
    )
    drawText(titleLayout, topLeft = Offset(tx + (w - titleLayout.size.width) / 2, ty + 5.dp.toPx()))
    val subLayout = textMeasurer.measure(
        AnnotatedString(subtitle),
        TextStyle(fontFamily = Hanken, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = foreground.copy(alpha = 0.65f)),
    )
    drawText(subLayout, topLeft = Offset(tx + (w - subLayout.size.width) / 2, ty + h - subLayout.size.height - 4.dp.toPx()))
}

/** Catmull-Rom-style smoothing (t = 0.18), the same curve the prototype draws. */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.size < 2) return path
    path.moveTo(pts[0].x, pts[0].y)
    val t = 0.18f
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { p2 }
        path.cubicTo(
            p1.x + (p2.x - p0.x) * t, p1.y + (p2.y - p0.y) * t,
            p2.x - (p3.x - p1.x) * t, p2.y - (p3.y - p1.y) * t,
            p2.x, p2.y,
        )
    }
    return path
}

/**
 * Per-bucket bars (design `charts.jsx` → `BarChart`): a faint full-height track behind each bar, rounded
 * caps, the last (current) bucket at full opacity, and thinned axis labels.
 */
@Composable
fun TrendBarChart(
    bars: List<Long>,
    labels: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (isSystemInDarkTheme()) RingTrackDark else RingTrackLight
    val faintColor = MaterialTheme.colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier.fillMaxWidth().height(168.dp)) {
        if (bars.isEmpty()) return@Canvas
        val padT = 12.dp.toPx()
        val padB = 22.dp.toPx()
        val n = bars.size
        val gap = (if (n > 20) 2.dp else if (n > 10) 4.dp else 7.dp).toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val plotH = size.height - padB - padT
        val max = maxOf(1L, bars.max()).toDouble()
        val radius = CornerRadius(minOf(4.dp.toPx(), bw / 2))

        bars.forEachIndexed { i, v ->
            val bx = i * (bw + gap)
            drawRoundRect(trackColor.copy(alpha = 0.5f), Offset(bx, padT), Size(bw, plotH), radius)
            val h = maxOf((v / max * plotH).toFloat(), if (v > 0) 3.dp.toPx() else 0f)
            if (h > 0f) {
                drawRoundRect(
                    accent.copy(alpha = if (i == n - 1) 1f else 0.78f),
                    Offset(bx, size.height - padB - h),
                    Size(bw, h),
                    radius,
                )
            }
        }

        // thin the labels to at most ~7 so month/year stay readable
        val stride = ceil(n / 7.0).toInt()
        val style = TextStyle(fontFamily = Hanken, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = faintColor)
        for (i in 0 until n step stride) {
            val text = labels.getOrNull(i)?.let { if (n == 7) it.take(1) else it } ?: continue
            val layout = textMeasurer.measure(AnnotatedString(text), style)
            val cx = (i * (bw + gap) + bw / 2 - layout.size.width / 2).coerceAtMost(size.width - layout.size.width)
            drawText(layout, topLeft = Offset(cx, size.height - layout.size.height))
        }
    }
}

/**
 * The calendar heatmap (design `charts.jsx` → `Heatmap`): 20 week columns × Mon–Sun rows, M/W/F row labels,
 * zero days as a faint track and active days as the accent with opacity ramping against the busiest day.
 */
@Composable
fun HeatmapChart(
    heatmap: Heatmap,
    bestDay: Long,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (isSystemInDarkTheme()) RingTrackDark else RingTrackLight
    val faintColor = MaterialTheme.colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    val cols = heatmap.columns.size
    if (cols == 0) return
    // prototype units: 13px cells, 4px gaps, 22px weekday gutter — keep its aspect and scale to width
    val unitW = 22f + cols * 17f - 4f
    val unitH = 7 * 17f - 4f
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(unitW / unitH)) {
        val s = size.width / unitW
        val cell = 13f * s
        val pitch = 17f * s
        val gutter = 22f * s
        val max = maxOf(1L, bestDay)
        val radius = CornerRadius(3f * s)

        val style = TextStyle(fontFamily = Hanken, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = faintColor)
        for ((row, label) in listOf(0 to "M", 2 to "W", 4 to "F")) {
            val layout = textMeasurer.measure(AnnotatedString(label), style)
            drawText(layout, topLeft = Offset(0f, row * pitch + cell - layout.size.height))
        }

        heatmap.columns.forEachIndexed { ci, column ->
            column.forEachIndexed { ri, day ->
                val level = if (day.value <= 0) 0f else (0.22f + (day.value.toFloat() / max) * 0.78f).coerceAtMost(1f)
                drawRoundRect(
                    color = if (day.value <= 0) trackColor.copy(alpha = 0.5f) else accent.copy(alpha = level),
                    topLeft = Offset(gutter + ci * pitch, ri * pitch),
                    size = Size(cell, cell),
                    cornerRadius = radius,
                )
            }
        }
    }
}
