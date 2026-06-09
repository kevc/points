package com.points.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.points.core.domain.SyncStatus
import com.points.core.presentation.counter.CounterComponent
import com.points.core.presentation.root.RootComponent
import com.points.core.presentation.sync.SyncComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * Single launcher activity. Builds the shared Decompose [RootComponent] (retained across configuration
 * changes) from Koin, renders the counter, and shows an unobtrusive sync-status indicator. Each
 * foreground (`ON_RESUME`) nudges a reconcile, in addition to the on-start trigger in the sync store.
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
                        SyncStatusIndicator(
                            component = root.sync,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                        )
                        CounterScreen(root.counter)
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

/** Minimal Material3 theme wrapper. Expanded once design tokens are defined. */
@Composable
private fun PointsTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
