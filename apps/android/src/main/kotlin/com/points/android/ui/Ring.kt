package com.points.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.points.android.ui.theme.PointHue
import com.points.android.ui.theme.RingTrackDark
import com.points.android.ui.theme.RingTrackLight
import com.points.android.ui.theme.accent
import com.points.core.domain.RingState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The tile gauge: a track, a progress arc (always the point's hue), scale ticks, and an over-target marker —
 * the Compose rendering of the domain [RingState]. [content] is centered inside the ring (the value numeral).
 */
@Composable
fun Ring(
    ring: RingState,
    hue: PointHue,
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
    stroke: Dp = 7.dp,
    content: @Composable () -> Unit,
) {
    val track = if (isSystemInDarkTheme()) RingTrackDark else RingTrackLight
    val arc = hue.accent()
    val notch = MaterialTheme.colorScheme.surface

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arcSize = Size(d, d)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = d / 2f

            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))

            if (ring.progress > 0f) {
                drawArc(
                    color = arc,
                    startAngle = -90f,
                    sweepAngle = 360f * ring.progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }

            if (ring.ticks > 1) {
                val inset = 1.5.dp.toPx()
                val r1 = radius + sw / 2f - inset
                val r2 = radius - sw / 2f + inset
                for (i in 0 until ring.ticks) {
                    val a = (i.toFloat() / ring.ticks) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    drawLine(
                        color = notch,
                        start = Offset(center.x + cos(a) * r1, center.y + sin(a) * r1),
                        end = Offset(center.x + cos(a) * r2, center.y + sin(a) * r2),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (ring.over) {
                drawCircle(arc, radius = sw / 2f + 1.5.dp.toPx(), center = Offset(center.x, center.y - radius))
            }
        }
        content()
    }
}
