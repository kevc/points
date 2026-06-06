package com.points.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.points.core.presentation.counter.CounterComponent
import com.points.core.presentation.root.RootComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * Single launcher activity. Builds the shared Decompose [RootComponent] (retained across configuration
 * changes) from Koin, and renders the counter — its value plus increment/decrement buttons.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = retainedComponent { componentContext ->
            GlobalContext.get().get<RootComponent> { parametersOf(componentContext) }
        }

        setContent {
            PointsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CounterScreen(root.counter)
                }
            }
        }
    }
}

@Composable
private fun CounterScreen(component: CounterComponent) {
    val state by component.state.subscribeAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.value.toString(),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { contentDescription = "counter value" },
        )
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = component::onDecrement) { Text("-") }
            Button(onClick = component::onIncrement) { Text("+") }
        }
    }
}

/** Minimal Material3 theme wrapper. Expanded once design tokens are defined. */
@Composable
private fun PointsTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
