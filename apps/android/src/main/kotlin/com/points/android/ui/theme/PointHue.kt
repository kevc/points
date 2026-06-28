package com.points.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Accent is NOT a fixed brand color. Every point type owns a hue;
// lightness & chroma are constant so all hues read at one weight.
//   light: L 60% · C 0.095     dark: L 70% · C 0.105
enum class PointHue(val degrees: Int, val light: Color, val dark: Color) {
    GREEN(152, Color(0xFF519164), Color(0xFF69B17E)),
    BLUE(215, Color(0xFF278EA4), Color(0xFF3EAFC7)),
    VIOLET(285, Color(0xFF7A78B7), Color(0xFF9895DD)),
    AMBER(40, Color(0xFFB06C54), Color(0xFFD6876C)),
    RED(18, Color(0xFFB2686B), Color(0xFFD88386)),
    MAGENTA(330, Color(0xFFA06B9B), Color(0xFFC386BE)),
    ;

    companion object {
        /** Maps a point type's stored hue (color-wheel degrees) to the nearest palette entry. */
        fun forDegrees(hue: Int): PointHue {
            val deg = ((hue % 360) + 360) % 360
            return entries.minBy { entry ->
                val d = abs(entry.degrees - deg)
                minOf(d, 360 - d)
            }
        }
    }
}

@Composable
fun PointHue.accent(): Color =
    if (isSystemInDarkTheme()) dark else light
