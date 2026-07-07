package io.aequicor.visualization

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.aequicor.visualization.ui_engine.compose_ui.UiVisualization
import io.aequicor.visualization.ui_engine.compose_ui.UiVisualizationTheme
import io.aequicor.visualization.ui_engine.compose_ui.rememberUiVisualizationState

@Composable
@Preview
fun App() {
    UiVisualizationTheme {
        val uiState = rememberUiVisualizationState()
        UiVisualization(
            document = uiState.value.documentOrNull,
            state = uiState.value,
            onEvent = uiState::dispatch,
            modifier = Modifier,
        )
    }
}
