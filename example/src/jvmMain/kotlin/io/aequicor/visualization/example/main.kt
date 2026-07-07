package io.aequicor.visualization.example

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mission Visualization Example",
    ) {
        ExampleApp()
    }
}
