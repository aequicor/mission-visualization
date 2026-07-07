package io.aequicor.visualization.ui_engine.canvas_overlays

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.runtime_state.UiVisualizationState
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument

private val OverlaySelection = Color(0xFF1F5FA8)
private val OverlayScenario = Color(0xFF2BB8A8)
private val OverlayComment = Color(0xFFE97155)

@Composable
fun UiCanvasOverlays(
    document: UiDocument,
    state: UiVisualizationState,
    rootBounds: Rect?,
    bounds: Map<String, Rect>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val root = rootBounds ?: return@Canvas
        val selectedId = state.selectedNodeId.ifBlank { state.selectedScreenId }

        state.selectedScenario?.steps
            ?.mapNotNull { step -> bounds[step.nodeId.ifBlank { step.screenId }]?.relativeTo(root) }
            ?.windowed(size = 2, step = 1)
            ?.forEach { pair ->
                drawLine(
                    color = OverlayScenario.copy(alpha = 0.55f),
                    start = pair[0].center,
                    end = pair[1].center,
                    strokeWidth = 2.dp.toPx(),
                )
            }

        document.comments.forEach { comment ->
            bounds[comment.targetId]?.relativeTo(root)?.let { rect ->
                drawCircle(
                    color = OverlayComment,
                    radius = 4.dp.toPx(),
                    center = Offset(rect.right - 6.dp.toPx(), rect.top + 6.dp.toPx()),
                )
            }
        }

        bounds[selectedId]?.relativeTo(root)?.let { rect ->
            drawRoundRect(
                color = OverlaySelection,
                topLeft = Offset(rect.left - 4.dp.toPx(), rect.top - 4.dp.toPx()),
                size = Size(rect.width + 8.dp.toPx(), rect.height + 8.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun Rect.relativeTo(root: Rect): Rect =
    Rect(left - root.left, top - root.top, right - root.left, bottom - root.top)

