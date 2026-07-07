package io.aequicor.visualization.ui_engine.compose_render_engine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.canvas_overlays.UiCanvasOverlays
import io.aequicor.visualization.ui_engine.runtime_state.UiCommand
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.ui_document_ir.UiNode
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScreen
import io.aequicor.visualization.ui_engine.runtime_state.UiVisualizationState
import io.aequicor.visualization.ui_engine.components.fallback.UnknownNodeRenderer

object DefaultUiRenderEngine : UiRenderEngine {
    @Composable
    override fun Render(
        document: UiDocument,
        state: UiVisualizationState,
        onEvent: (UiCommand) -> Unit,
    ) {
        val screen = state.selectedScreen ?: document.screens.firstOrNull()
        val bounds = remember { mutableStateMapOf<String, Rect>() }
        var rootBounds by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(state.selectedScreenId) {
            bounds.clear()
        }

        if (screen == null) return

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootBounds = it.boundsInRoot() },
            contentAlignment = Alignment.TopCenter,
        ) {
            @Composable
            fun RenderNode(node: UiNode) {
                val renderer = DefaultUiNodeRenderers[node.type] ?: UnknownNodeRenderer
                val context = UiRenderContext(
                    state = state,
                    onEvent = onEvent,
                    renderChild = { child -> RenderNode(child) },
                    captureBounds = { id, rect -> bounds[id] = rect },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { context.captureBounds(node.id, it.boundsInRoot()) }
                        .clickable { onEvent(UiCommand.SelectNode(node.id)) },
                ) {
                    renderer(node, context)
                }
            }

            UiScreenPreview(
                screen = screen,
                onEvent = onEvent,
                onBounds = { bounds[screen.id] = it },
                renderNode = { RenderNode(it) },
            )

            UiCanvasOverlays(
                document = document,
                state = state,
                rootBounds = rootBounds,
                bounds = bounds,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun UiScreenPreview(
    screen: UiScreen,
    onEvent: (UiCommand) -> Unit,
    onBounds: (Rect) -> Unit,
    renderNode: @Composable (UiNode) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(20.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFC7D8E8), RoundedCornerShape(8.dp))
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .clickable { onEvent(UiCommand.SelectScreen(screen.id)) }
            .padding(spacingDp(screen.layout.padding).ifDefault(18.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacingDp(screen.layout.gap).ifDefault(12.dp)),
    ) {
        Text(screen.title.ifBlank { screen.id }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = DeepLapis)
        if (screen.description.isNotBlank()) {
            Text(screen.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        screen.children.forEach { renderNode(it) }
    }
}
