package io.aequicor.visualization.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.aequicor.visualization.mission.MissionVisualization
import io.aequicor.visualization.mission.MissionVisualizationTheme
import io.aequicor.visualization.mission.rememberMissionVisualizationState

@Composable
fun ExampleApp() {
    MissionVisualizationTheme {
        val missionState = rememberMissionVisualizationState(ExampleMissionMarkdown)
        MissionVisualization(
            spec = missionState.value.specOrNull,
            state = missionState.value,
            onEvent = missionState::dispatch,
            modifier = Modifier,
        )
    }
}
