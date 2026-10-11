package com.points.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design system's stroke icon set: geometric, calm, round-capped 24×24 paths — the exact path data from
 * the design handoff (`charts.jsx` → `PATHS`), so the native icons match the prototype stroke for stroke.
 */
object IconPaths {
    const val BACK = "M15 5l-7 7 7 7"
    const val EDIT = "M4 20h4L19 9l-4-4L4 16v4z"
    const val SLIDERS = "M4 8h10M18 8h2M4 16h2M10 16h10M14 6v4M6 14v4"
    const val PLUS = "M12 5v14M5 12h14"
    const val MINUS = "M5 12h14"
    const val UNDO = "M9 7L4 12l5 5M4 12h11a5 5 0 010 10h-1"
    const val EXPAND = "M15 4h5v5M20 4l-6 6M9 20H4v-5M4 20l6-6"
    const val CHEVRON_RIGHT = "M9 5l7 7-7 7"
    const val TRASH = "M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13"
}

/** Draws one [IconPaths] entry as a stroked path, scaled from its 24×24 viewport to [size]. */
@Composable
fun StrokeIcon(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = 1.75.dp,
    tint: Color = LocalContentColor.current,
) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(scale = s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                path = parsed,
                color = tint,
                style = Stroke(
                    // the stroke is specified against the 24-unit viewport, so unscale it
                    width = strokeWidth.toPx() / s,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
