package io.aequicor.visualization

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Compose owns only #compose-root; the intro overlay (#mv-loader) is a separate
    // sibling in index.html, so mounting Compose never touches or removes it — we
    // fade it out ourselves once the first frame is painted.
    ComposeViewport(viewportContainerId = "compose-root") {
        MissionEditorApp()
        // Dismiss the HTML loading overlay only after Compose has actually painted its
        // first frame, so the handoff never flashes an empty canvas. The overlay owns
        // its own minimum-display floor (see index.html), so fast loads still get the
        // full intro before it fades.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            dismissLoadingOverlay()
        }
    }
}

/** Fades out and removes the `#mv-loader` intro overlay defined in `index.html`. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun dismissLoadingOverlay() {
    js("if (typeof window.__mvHideLoader === 'function') window.__mvHideLoader();")
}
