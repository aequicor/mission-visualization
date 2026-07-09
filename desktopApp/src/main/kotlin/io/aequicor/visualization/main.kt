package io.aequicor.visualization

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState()
    Window(
        state = windowState,
        icon = painterResource("icons/mission-logo.png"),
        onCloseRequest = ::exitApplication,
        title = "Mission Visualization",
        // F11 / F10 toggle OS window fullscreen (either key — the user may reach for either).
        // Handled at the window root (preview phase) so it fires regardless of what's focused.
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.F11 || event.key == Key.F10)) {
                windowState.placement =
                    if (windowState.placement == WindowPlacement.Fullscreen) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Fullscreen
                    }
                true
            } else {
                false
            }
        },
    ) {
        MissionEditorApp()
    }
}
