@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.points.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.points.android.ui.theme.Hanken
import com.points.android.ui.theme.PointHue
import com.points.android.ui.theme.accent
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.domain.PointTrend
import com.points.core.domain.TrendRange
import com.points.core.domain.heatmap
import com.points.core.domain.recentLog
import com.points.core.domain.series
import com.points.core.presentation.detail.ChartStyle
import com.points.core.presentation.detail.DetailComponent
import com.points.core.presentation.detail.dateLabelOf

/**
 * The M5 per-point Detail screen (design `screens-detail.jsx` → `Detail`): app bar, hero value with the
 * week-delta pill, counter controls with custom step chips, the stats row, the chart card (Trend / Bars /
 * Cal with a Week / Month / Year range), the insight caption, and the recent-activity log. Reset and remove
 * live behind the more-sheet and stay reversible via the undo bar.
 */
@Composable
fun DetailScreen(component: DetailComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    val trend = state.trend
    var moreSheet by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    if (trend == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val accent = PointHue.forDegrees(state.hue).accent()

    Column(modifier = modifier.fillMaxSize()) {
        AppBar(
            title = trend.type.name,
            onBack = component::onBack,
            onEdit = component::onEdit,
            onMore = { moreSheet = true },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Hero(trend = trend, accent = accent)
            CounterControls(
                trend = trend,
                enabled = !state.deleted,
                onIncrement = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    component.onIncrement()
                },
                onDecrement = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    component.onDecrement()
                },
                onIncrementBy = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    component.onIncrementBy(it)
                },
            )
            StatsRow(trend = trend)
            ChartCard(
                trend = trend,
                chart = state.chart,
                range = state.range,
                accent = accent,
                onSetChart = component::onSetChart,
                onSetRange = component::onSetRange,
            )
            SectionLabel("Recent activity")
            ActivityLog(trend = trend, accent = accent)
            Spacer(Modifier.height(32.dp))
        }

        state.undoLabel?.let { label ->
            UndoBar(label = label, onUndo = component::onUndo, onDismiss = component::onDismissUndo)
        }
    }

    if (moreSheet) {
        MoreSheet(
            name = trend.type.name,
            onReset = { moreSheet = false; component.onReset() },
            onDelete = { moreSheet = false; component.onDelete() },
            onDismiss = { moreSheet = false },
        )
    }
}

@Composable
private fun AppBar(title: String, onBack: () -> Unit, onEdit: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "back" }) {
            StrokeIcon(IconPaths.BACK)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Hanken, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "edit" }) {
            StrokeIcon(IconPaths.EDIT, size = 20.dp)
        }
        IconButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = "more" }) {
            StrokeIcon(IconPaths.SLIDERS, size = 20.dp)
        }
    }
}

@Composable
private fun Hero(trend: PointTrend, accent: Color) {
    val down = trend.type.goal == PointGoal.DOWN
    val week = trend.deltaThisWeek
    val deltaText = when {
        down && week == 0L -> "None this week — nice"
        down -> "${formatCount(week)} this week"
        else -> "+${formatCount(week)} this week"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (trend.type.mode == PointMode.DAILY) "Today" else "Total",
            style = TextStyle(fontFamily = Hanken, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatCount(trend.value),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "counter value" },
            )
            if (trend.type.unit.isNotEmpty()) {
                Text(
                    text = trend.type.unit,
                    style = TextStyle(fontFamily = Hanken, fontSize = 26.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (down) MaterialTheme.colorScheme.outline else accent),
            )
            Text(
                text = deltaText,
                style = TextStyle(fontFamily = Hanken, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CounterControls(
    trend: PointTrend,
    enabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onIncrementBy: (Long) -> Unit,
) {
    val step = trend.type.step.coerceAtLeast(1L)
    val chips = if (step >= 10) listOf(step, step * 2, step * 5) else listOf(1L, 5L, 10L)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(
            size = 64.dp,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            enabled = enabled,
            onClick = onDecrement,
            description = "decrement",
        ) { StrokeIcon(IconPaths.MINUS, size = 26.dp) }
        RoundButton(
            size = 96.dp,
            background = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.background,
            enabled = enabled,
            onClick = onIncrement,
            description = "increment",
        ) { StrokeIcon(IconPaths.PLUS, size = 40.dp, tint = MaterialTheme.colorScheme.background) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        chips.forEach { amount ->
            StepChip(text = "+$amount", enabled = enabled) { onIncrementBy(amount) }
        }
        StepChip(text = "−$step", enabled = enabled, onClick = onDecrement)
    }
}

@Composable
private fun RoundButton(
    size: androidx.compose.ui.unit.Dp,
    background: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
            content = content,
        )
    }
}

@Composable
private fun StepChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Hanken, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsRow(trend: PointTrend) {
    val week = trend.deltaThisWeek
    val weekText = if (week >= 0 && trend.type.goal != PointGoal.DOWN) "+${formatCount(week)}" else formatCount(week)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Stat(label = "This week", value = weekText, modifier = Modifier.weight(1f))
        Stat(label = "Best day", value = formatCount(trend.bestDay), modifier = Modifier.weight(1f))
        Stat(label = "Active days", value = trend.activeDays.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = Hanken, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = value,
            style = TextStyle(fontFamily = Hanken, fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChartCard(
    trend: PointTrend,
    chart: ChartStyle,
    range: TrendRange,
    accent: Color,
    onSetChart: (ChartStyle) -> Unit,
    onSetRange: (TrendRange) -> Unit,
) {
    val series = remember(trend, range) { trend.series(range) }
    val heatmap = remember(trend) { trend.heatmap(weeksBack = 20) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (chart) {
                    ChartStyle.TREND -> "TREND"
                    ChartStyle.BARS -> "PER PERIOD"
                    ChartStyle.CALENDAR -> "CALENDAR"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
            Segmented(
                options = listOf("Trend", "Bars", "Cal"),
                selected = chart.ordinal,
                onSelect = { onSetChart(ChartStyle.entries[it]) },
            )
        }

        when (chart) {
            ChartStyle.TREND -> AreaTrendChart(trend = trend, line = series.line, range = range, accent = accent)
            ChartStyle.BARS -> TrendBarChart(bars = series.bars, labels = series.labels, accent = accent)
            ChartStyle.CALENDAR -> HeatmapChart(heatmap = heatmap, bestDay = trend.bestDay, accent = accent)
        }

        if (chart == ChartStyle.TREND) {
            Text(
                text = trend.insight.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                style = TextStyle(fontFamily = Hanken, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (chart != ChartStyle.CALENDAR) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Segmented(
                    options = listOf("Week", "Month", "Year"),
                    selected = range.ordinal,
                    onSelect = { onSetRange(TrendRange.entries[it]) },
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val faint = MaterialTheme.colorScheme.outline
                val legendStyle = TextStyle(fontFamily = Hanken, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Last 20 weeks", style = legendStyle, color = faint)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("less", style = legendStyle, color = faint)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(0.5f, 0.35f, 0.6f, 0.85f, 1f).forEachIndexed { i, alpha ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (i == 0) MaterialTheme.colorScheme.surfaceContainerLow
                                        else accent.copy(alpha = alpha),
                                    ),
                            )
                        }
                    }
                    Text("more", style = legendStyle, color = faint)
                }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                text = label,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                style = TextStyle(fontFamily = Hanken, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun ActivityLog(trend: PointTrend, accent: Color) {
    val log = remember(trend) { trend.recentLog(limit = 12) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        if (log.isEmpty()) {
            Text(
                text = "No activity yet — tap + to begin.",
                style = TextStyle(fontFamily = Hanken, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        log.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (entry.delta < 0) MaterialTheme.colorScheme.outline else accent),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateLabelOf(entry.date, trend.today),
                        style = TextStyle(fontFamily = Hanken, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (entry.delta > 0) "Added" else "Adjusted",
                        style = TextStyle(fontFamily = Hanken, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = (if (entry.delta > 0) "+" else "−") + formatCount(kotlin.math.abs(entry.delta)),
                    style = TextStyle(fontFamily = Hanken, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                    color = if (entry.delta < 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (index < log.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow)
            }
        }
    }
}

@Composable
private fun UndoBar(label: String, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = onUndo) { Text("Undo") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun MoreSheet(
    name: String,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 32.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "Resets and deletes here are safe — they're recorded as adjustments, never erased. " +
                    "You can always undo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
            SheetAction(icon = IconPaths.UNDO, text = "Reset to zero", onClick = onReset)
            Spacer(Modifier.height(10.dp))
            SheetAction(icon = IconPaths.TRASH, text = "Remove this point", onClick = onDelete)
        }
    }
}

@Composable
private fun SheetAction(icon: String, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StrokeIcon(icon, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
        Text(
            text = text,
            style = TextStyle(fontFamily = Hanken, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
