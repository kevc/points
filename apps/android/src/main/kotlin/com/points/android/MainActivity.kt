package com.points.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.points.core.presentation.hello.HelloComponent
import com.points.core.presentation.root.RootComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * Single launcher activity. Builds the shared Decompose [RootComponent] (retained
 * across configuration changes) from Koin, and renders its hello state.
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
                    HelloScreen(root.hello)
                }
            }
        }
    }
}

@Composable
private fun HelloScreen(component: HelloComponent) {
    val state by component.state.subscribeAsState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.greeting,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/** Minimal Material3 theme wrapper. Expanded once design tokens are defined. */
@Composable
private fun PointsTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
