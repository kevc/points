package com.points.android.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.points.android.ui.Ring
import com.points.android.ui.theme.PointHue
import com.points.android.ui.theme.PointsTheme
import com.points.core.domain.RingState

/**
 * Compose `@Preview`s rendered to PNGs by the Compose Preview Screenshot Testing plugin — the image we embed in
 * a PR that changes UI. Record/refresh the reference PNGs with `./gradlew :apps:android:updateDebugScreenshotTest`
 * (they land under `src/debug/screenshotTest/reference/`); `validateDebugScreenshotTest` diffs against them.
 */
@Preview(name = "ColoredTileRings", showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun ColoredTileRingsPreview() {
    PointsTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Tile(PointHue.GREEN, RingState(progress = 0.73f, ticks = 4, over = false), "42", "Books")
                Tile(PointHue.BLUE, RingState(progress = 0.4f, ticks = 4, over = false), "5", "Water")
                Tile(PointHue.VIOLET, RingState(progress = 1f, ticks = 4, over = false), "30", "Calm")
            }
        }
    }
}

@Composable
private fun Tile(hue: PointHue, ring: RingState, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(ring = ring, hue = hue) {
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
