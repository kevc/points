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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Single launcher activity. This is the Milestone 1 placeholder screen: it renders
 * a static "Points" label with no shared Kotlin state. Wiring the Decompose root
 * component and shared presentation logic happens in a later task (#16).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PointsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PointsPlaceholder()
                }
            }
        }
    }
}

/** Minimal Material3 theme wrapper. Expanded once design tokens are defined. */
@Composable
private fun PointsTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun PointsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Points",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PointsPlaceholderPreview() {
    PointsTheme {
        PointsPlaceholder()
    }
}
