@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.points.android.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.points.android.ui.theme.PointHue
import com.points.android.ui.theme.accent
import com.points.core.presentation.home.HomeComponent
import com.points.core.presentation.home.HomeTile
import com.points.core.presentation.home.Suggestion
import com.points.core.presentation.home.pointSuggestions

/** The home grid: an app bar plus a 2-column grid of ring-gauge tiles, one per active point type. */
@Composable
fun HomeScreen(component: HomeComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
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
            .clickable(onClick = onClick)
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
