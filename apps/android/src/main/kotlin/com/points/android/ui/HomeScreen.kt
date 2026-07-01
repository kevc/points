@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.points.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.points.android.ui.theme.PointHue
import com.points.core.presentation.home.HomeComponent
import com.points.core.presentation.home.HomeTile

/** The home grid: an app bar plus a 2-column grid of ring-gauge tiles, one per active point type. */
@Composable
fun HomeScreen(component: HomeComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 8.dp)) {
            Text("Points", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "${state.tiles.size} things, quietly counting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.tiles, key = { it.id.toString() }) { tile ->
                Tile(tile = tile, onClick = { component.onTileClicked(tile.id) })
            }
        }
    }
}

@Composable
private fun Tile(tile: HomeTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ring(ring = tile.ring, hue = PointHue.forDegrees(tile.hue)) {
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
    }
}
