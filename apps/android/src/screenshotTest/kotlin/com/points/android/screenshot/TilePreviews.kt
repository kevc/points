package com.points.android.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.points.android.ui.Tile
import com.points.android.ui.theme.PointsTheme
import com.points.core.domain.RingState
import com.points.core.presentation.home.HomeTile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Screenshot preview of the **production** home `Tile` (from `HomeScreen.kt`) across a few point types — not a
 * reimplementation, so the golden PNG tracks the real component. Record/refresh with
 * `./gradlew :apps:android:updateDebugScreenshotTest` (goldens under `src/debug/screenshotTest/reference/`);
 * `validateDebugScreenshotTest` diffs against them.
 */
@OptIn(ExperimentalUuidApi::class)
@Preview(name = "HomeTiles", showBackground = true, widthDp = 560, heightDp = 300)
@Composable
private fun HomeTilesPreview() {
    PointsTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sampleTiles.forEach { tile ->
                Tile(tile = tile, onClick = {}, onIncrement = {}, onDecrement = {})
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private val sampleTiles = listOf(
    HomeTile(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        name = "Books read", valueText = "42", unit = "", meta = "→ 50",
        ring = RingState(progress = 0.73f, ticks = 4, over = false), hue = 152, step = 1,
    ),
    HomeTile(
        id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        name = "Water", valueText = "5", unit = "glasses", meta = "5 / 8 today",
        ring = RingState(progress = 5f / 8f, ticks = 8, over = false), hue = 215, step = 1,
    ),
    HomeTile(
        id = Uuid.parse("00000000-0000-0000-0000-000000000003"),
        name = "Times angry", valueText = "12", unit = "", meta = "30d calm",
        ring = RingState(progress = 1f, ticks = 4, over = false), hue = 330, step = 1,
    ),
)
