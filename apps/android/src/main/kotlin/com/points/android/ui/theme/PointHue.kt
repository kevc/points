package com.points.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent is NOT a fixed brand color. Every point type owns a hue;
// lightness & chroma are constant so all hues read at one weight.
//   light: L 60% · C 0.095     dark: L 70% · C 0.105
enum class PointHue(val light: Color, val dark: Color) {
    GREEN(Color(0xFF519164), Color(0xFF69B17E)),  // 152°
    BLUE(Color(0xFF278EA4), Color(0xFF3EAFC7)),  // 215°
    VIOLET(Color(0xFF7A78B7), Color(0xFF9895DD)),  // 285°
    AMBER(Color(0xFFB06C54), Color(0xFFD6876C)),  // 40°
    RED(Color(0xFFB2686B), Color(0xFFD88386)),  // 18°
    MAGENTA(Color(0xFFA06B9B), Color(0xFFC386BE)),  // 330°
}

@Composable
fun PointHue.accent(): Color =
    if (isSystemInDarkTheme()) dark else light
