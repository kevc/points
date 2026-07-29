@file:OptIn(
    kotlin.uuid.ExperimentalUuidApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.points.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.points.core.presentation.home.HomeComponent
import com.points.core.presentation.home.HomeTile
import com.points.core.presentation.home.Suggestion
import com.points.core.presentation.home.pointSuggestions
import kotlin.uuid.Uuid

/** The home grid: an app bar plus a 2-column grid of ring-gauge tiles, one per active point type. */
@Composable
fun HomeScreen(component: HomeComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    var quickSheetId by remember { mutableStateOf<Uuid?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 8.dp)) {
                Text("Points", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "${state.tiles.size} things, quietly counting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.loaded && state.tiles.isEmpty()) {
                EmptyState(onQuickCreate = component::onQuickCreate)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.tiles, key = { it.id.toString() }) { tile ->
                        Tile(
                            tile = tile,
                            onClick = { component.onTileClicked(tile.id) },
                            onLongClick = { quickSheetId = tile.id },
                            onIncrement = { component.onIncrement(tile.id, tile.step) },
                            onDecrement = { component.onDecrement(tile.id, tile.step) },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = component::onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            text = { Text("New") },
            icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
        )
    }

    // the long-pressed tile's quick actions; looked up live so the value updates as you tap
    val quickTile = state.tiles.firstOrNull { it.id == quickSheetId }
    if (quickTile != null) {
        QuickSheet(
            tile = quickTile,
            onIncrementBy = { component.onIncrement(quickTile.id, it) },
            onDecrement = { component.onDecrement(quickTile.id, quickTile.step) },
            onOpen = { quickSheetId = null; component.onTileClicked(quickTile.id) },
            onDismiss = { quickSheetId = null },
        )
    }
}

/**
 * The home-tile long-press sheet (design `screens-detail.jsx` → `QuickSheet`): the point's name and live
 * value, a ± stepper with custom step chips, and a full-width Open details action.
 */
@Composable
private fun QuickSheet(
    tile: HomeTile,
    onIncrementBy: (Long) -> Unit,
    onDecrement: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val step = tile.step.coerceAtLeast(1L)
    val chips = if (step >= 10) listOf(step, step * 5) else listOf(1L, 5L, 10L)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = tile.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tile.valueText,
                    style = TextStyle(
                        fontFamily = Hanken,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundStepButton(icon = IconPaths.MINUS, description = "decrement") {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDecrement()
                }
                chips.forEach { amount ->
                    OutlinedButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onIncrementBy(amount)
                    }) {
                        Text(
                            text = "+$amount",
                            style = TextStyle(fontFamily = Hanken, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RoundStepButton(icon = IconPaths.PLUS, description = "increment") {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onIncrementBy(step)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
                    .clickable(onClick = onOpen)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Open details",
                    style = TextStyle(fontFamily = Hanken, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.background,
                )
                StrokeIcon(IconPaths.CHEVRON_RIGHT, size = 18.dp, tint = MaterialTheme.colorScheme.background)
            }
        }
    }
}

@Composable
private fun RoundStepButton(icon: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { StrokeIcon(icon, size = 22.dp) }
}

/** Gentle first-run prompt: a question + a few starter chips that quick-create a point type. */
@Composable
private fun EmptyState(onQuickCreate: (Suggestion) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "What would you like to count?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick one to start — you can rename or change it any time. Nothing here is ever permanent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pointSuggestions.forEach { suggestion ->
                val hue = PointHue.forDegrees(suggestion.hue)
                FilledTonalButton(
                    onClick = { onQuickCreate(suggestion) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = hue.accent().copy(alpha = 0.16f),
                        contentColor = hue.accent(),
                    ),
                ) { Text(suggestion.name) }
            }
        }
    }
}

@Composable
private fun Tile(
    tile: HomeTile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val hue = PointHue.forDegrees(tile.hue)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ring(ring = tile.ring, hue = hue) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = tile.valueText, style = MaterialTheme.typography.titleLarge)
                if (tile.unit.isNotEmpty()) {
                    Text(
                        text = tile.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = tile.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = tile.meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The primary action: soft tonal ± buttons carrying the point's own hue.
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDecrement()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) { Text("−") }
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onIncrement()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = hue.accent().copy(alpha = 0.16f),
                    contentColor = hue.accent(),
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) { Text("+${tile.step}") }
        }
    }
}
