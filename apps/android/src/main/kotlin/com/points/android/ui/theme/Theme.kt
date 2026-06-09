package com.points.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    background         = BgLight,
    surface            = SurfaceLight,
    surfaceContainerHigh    = SurfaceHighLight,
    surfaceContainerHighest = RaisedLight,
    onBackground       = InkLight,
    onSurface          = InkLight,
    onSurfaceVariant   = InkDimLight,
    outline            = InkFaintLight,
    outlineVariant     = LineLight,
    surfaceContainerLow = LineSoftLight,
    scrim              = ScrimLight,
    // primary is set per-screen from the point's PointHue.accent()
    primary            = InkLight,
    onPrimary          = BgLight,
)

private val DarkColors = darkColorScheme(
    background         = BgDark,
    surface            = SurfaceDark,
    surfaceContainerHigh    = SurfaceHighDark,
    surfaceContainerHighest = RaisedDark,
    onBackground       = InkDark,
    onSurface          = InkDark,
    onSurfaceVariant   = InkDimDark,
    outline            = InkFaintDark,
    outlineVariant     = LineDark,
    surfaceContainerLow = LineSoftDark,
    scrim              = ScrimDark,
    primary            = InkDark,
    onPrimary          = BgDark,
)

@Composable
fun PointsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography  = PointsTypography,
        shapes      = PointsShapes,
        content     = content,
    )
}
