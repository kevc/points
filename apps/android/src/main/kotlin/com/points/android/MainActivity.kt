package com.points.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.points.android.ui.HomeScreen
import com.points.android.ui.theme.PointsTheme
import com.points.core.domain.SyncStatus
import com.points.core.presentation.counter.CounterComponent
import com.points.core.presentation.root.RootComponent
import com.points.core.presentation.sync.SyncComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * Single launcher activity. Builds the shared Decompose [RootComponent] (retained across configuration
 * changes) from Koin and renders its navigation stack — the home grid, or a per-type counter when a tile is
 * tapped — with an unobtrusive sync-status overlay. Each foreground (`ON_RESUME`) nudges a reconcile.
 */
class MainActivity : ComponentActivity() {

    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = retainedComponent { componentContext ->
            GlobalContext.get().get<RootComponent> { parametersOf(componentContext) }
        }

        setContent {
            PointsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Children(stack = root.stack) { child ->
                            when (val instance = child.instance) {
                                is RootComponent.Child.Home -> HomeScreen(instance.component)
                                is RootComponent.Child.Detail -> CounterScreen(instance.component)
                            }
                        }
                        SyncStatusIndicator(
                            component = root.sync,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        root.sync.onAppForegrounded() // reconcile when brought to the foreground
    }
}

/** Per-type counter (the interim detail screen until M5): a back affordance, the value, and ± controls. */
@Composable
private fun CounterScreen(component: CounterComponent) {
    val state by component.state.subscribeAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = component::onBack) { Text("← Back") }
        }
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
}

/** Compact, secondary sync-status row: a spinner while syncing, otherwise a short label. */
@Composable
private fun SyncStatusIndicator(component: SyncComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    val label = when (state.status) {
        SyncStatus.Idle -> "Idle"
        SyncStatus.Syncing -> "Syncing…"
        SyncStatus.Synced -> "Synced"
        SyncStatus.Offline -> "Offline"
        SyncStatus.Failed -> "Sync failed"
    }
    Row(
        modifier = modifier.semantics { contentDescription = "sync status: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.status == SyncStatus.Syncing) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
