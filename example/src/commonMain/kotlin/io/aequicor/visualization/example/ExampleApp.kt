package io.aequicor.visualization.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.aequicor.visualization.ui_engine.compose_ui.UiVisualization
import io.aequicor.visualization.ui_engine.compose_ui.UiVisualizationTheme
import io.aequicor.visualization.ui_engine.compose_ui.rememberUiVisualizationState

@Composable
fun ExampleApp() {
    UiVisualizationTheme {
        val uiState = rememberUiVisualizationState(ExampleUiYaml)
        UiVisualization(
            document = uiState.value.documentOrNull,
            state = uiState.value,
            onEvent = uiState::dispatch,
            modifier = Modifier,
        )
    }
}
