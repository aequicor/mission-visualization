package io.aequicor.visualization

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        icon = painterResource("icons/mission-logo.png"),
        onCloseRequest = ::exitApplication,
        title = "Mission Visualization",
    ) {
        MissionEditorApp()
    }
}
