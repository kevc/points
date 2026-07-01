@file:OptIn(ExperimentalLayoutApi::class)

package com.points.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.points.android.ui.theme.PointHue
import com.points.android.ui.theme.accent
import com.points.core.domain.PointGoal
import com.points.core.domain.PointMode
import com.points.core.presentation.edit.CreateEditComponent

/** The picker offers exactly the palette's hues — one swatch per [PointHue]. */
private val HUES = PointHue.entries.map { it.degrees }

/** The create/edit form: name, color, how it's counted, step, optional daily target, and tone. */
@Composable
fun CreateEditScreen(component: CreateEditComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = component::onCancel) { Text("Cancel") }
            Text(
                text = if (state.editing) "Edit point" else "New point",
                style = MaterialTheme.typography.titleLarge,
            )
            Button(onClick = component::onSave) { Text("Save") }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Field("What are you counting?") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = component::onName,
                    singleLine = true,
                    placeholder = { Text("e.g. Days smoke-free") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Field("Color — shows up in its charts") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HUES.forEach { hue ->
                        val selected = state.hue == hue
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PointHue.forDegrees(hue).accent())
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { component.onHue(hue) },
                        )
                    }
                }
            }

            Field("How is it counted?") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(
                        title = "Tally up",
                        subtitle = "A running total that climbs.",
                        selected = state.mode == PointMode.CUMULATIVE,
                        onClick = { component.onMode(PointMode.CUMULATIVE) },
                        modifier = Modifier.weight(1f),
                    )
                    ModeCard(
                        title = "Daily",
                        subtitle = "Resets gently each morning.",
                        selected = state.mode == PointMode.DAILY,
                        onClick = { component.onMode(PointMode.DAILY) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Field("Step — how much each tap adds") {
                Stepper(value = "+${state.step}", onMinus = component::onStepDown, onPlus = component::onStepUp)
            }

            if (state.mode == PointMode.DAILY) {
                Field("Gentle daily target (optional)") {
                    Stepper(
                        value = if (state.target > 0) state.target.toString() else "Off",
                        onMinus = component::onTargetDown,
                        onPlus = component::onTargetUp,
                    )
                    Text(
                        text = if (state.target > 0) {
                            "Counts up to this — never turns red if you go over."
                        } else {
                            "No target — just a gentle daily tally."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (state.mode == PointMode.CUMULATIVE) {
                Field("Tone") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard(
                            title = "Climbing",
                            subtitle = "More feels like progress.",
                            selected = state.goal == PointGoal.UP,
                            onClick = { component.onGoal(PointGoal.UP) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeCard(
                            title = "Easing",
                            subtitle = "Just noticing — no red.",
                            selected = state.goal == PointGoal.DOWN,
                            onClick = { component.onGoal(PointGoal.DOWN) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}
