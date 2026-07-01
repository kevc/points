package com.points.android.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.points.android.ui.Ring
import com.points.android.ui.theme.PointHue
import com.points.android.ui.theme.PointsTheme
import com.points.core.domain.RingState
import org.junit.Rule
import org.junit.Test

/**
 * A host-side (no emulator) render of the tile ring in a few point hues — the kind of image we embed in a PR
 * that changes UI. Regenerate the golden PNG with `./gradlew :apps:android:recordPaparazziDebug`; the output
 * lands under `src/test/snapshots/images/`.
 */
class RingScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun coloredTileRings() {
        paparazzi.snapshot { ColoredTileRings() }
    }
}

@Composable
private fun ColoredTileRings() {
    PointsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Tile(PointHue.GREEN, RingState(progress = 0.73f, ticks = 4, over = false), "42", "Books")
                    Tile(PointHue.BLUE, RingState(progress = 0.4f, ticks = 4, over = false), "5", "Water")
                    Tile(PointHue.VIOLET, RingState(progress = 1f, ticks = 4, over = false), "30", "Calm")
                }
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
