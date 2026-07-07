package io.aequicor.visualization.ui_engine.compose_render_engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.ui_document_ir.UiNode
import io.aequicor.visualization.ui_engine.runtime_state.UiVisualizationState

typealias UiNodeRenderer = @Composable (node: UiNode, context: UiRenderContext) -> Unit

interface UiRenderEngine {
    @Composable
    fun Render(
        document: UiDocument,
        state: UiVisualizationState,
        onEvent: (UiCommand) -> Unit,
    )
}

data class UiRenderContext(
    val state: UiVisualizationState,
    val onEvent: (UiCommand) -> Unit,
    val renderChild: @Composable (UiNode) -> Unit,
    val captureBounds: (String, Rect) -> Unit,
)

interface UiComponentRendererProvider {
    val type: String
    val renderer: UiNodeRenderer
}
