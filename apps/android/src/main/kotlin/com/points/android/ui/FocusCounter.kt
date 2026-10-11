@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.points.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.points.core.domain.PointTrend
import com.points.android.ui.theme.Hanken

/**
 * The full-screen Focus counter (design `screens-detail.jsx` → `CounterFS`), reached from the Detail counter
 * row's expand button: the whole screen is the tap target — every tap adds the type's own step with a soft
 * accent ripple where the finger landed and a little pop of the numeral. A round − in the footer corrects
 * overshoots; Done (or back) returns to the Detail screen.
 */
@Composable
fun FocusCounter(
    trend: PointTrend,
    accent: Color,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val ripples = remember { mutableStateListOf<Ripple>() }
    val pop = remember { Animatable(1f) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "done" }) {
                StrokeIcon(IconPaths.BACK)
            }
            Text(
                text = "Focus",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Hanken, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(48.dp))
        }

        // the tap field: ripples draw behind the numeral, newest-last
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    ripples.forEach { ripple ->
                        drawCircle(
                            color = accent.copy(alpha = 0.16f * (1f - ripple.progress.value)),
                            radius = 120.dp.toPx() * ripple.progress.value,
                            center = ripple.center,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        ripples += Ripple(center = offset)
                        onIncrement()
                    })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val valueText = formatCount(trend.value)
            // the numeral shrinks as it grows: 150sp up to 3 chars, 104sp to 5, 78sp beyond
            val numSize = when {
                valueText.length <= 3 -> 150.sp
                valueText.length <= 5 -> 104.sp
                else -> 78.sp
            }
            LaunchedEffect(trend.value) {
                pop.snapTo(1.1f)
                pop.animateTo(1f, tween(durationMillis = 320))
            }
            Text(
                text = trend.type.name,
                style = TextStyle(fontFamily = Hanken, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .scale(pop.value)
                    .semantics { contentDescription = "counter value" },
                style = TextStyle(
                    fontFamily = Hanken,
                    fontSize = numSize,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val step = trend.type.step.coerceAtLeast(1L)
            Text(
                text = if (step > 1) "Tap anywhere to add $step" else "Tap anywhere to add",
                style = TextStyle(fontFamily = Hanken, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDecrement()
                    }
                    .semantics { contentDescription = "decrement" },
                contentAlignment = Alignment.Center,
            ) { StrokeIcon(IconPaths.MINUS, size = 26.dp) }
            Text(
                text = "Done",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .widthIn(min = 120.dp)
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Hanken, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    // each ripple plays its 600ms expansion once, then leaves the list
    ripples.forEach { ripple ->
        LaunchedEffect(ripple) {
            ripple.progress.animateTo(1f, tween(durationMillis = 600))
            ripples.remove(ripple)
        }
    }
}

/** One in-flight tap ripple: where it landed and how far it has expanded. */
private class Ripple(val center: Offset) {
    val progress = Animatable(0f)
}
