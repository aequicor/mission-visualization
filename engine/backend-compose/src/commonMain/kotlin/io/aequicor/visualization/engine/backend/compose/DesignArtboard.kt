package io.aequicor.visualization.engine.backend.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.resolve.DesignResolver

/**
 * Renders the first top-level frame of a page through the resolve -> layout -> draw
 * pipeline, with Figma-like click-to-select and a selection overlay.
 *
 * The canvas draws in document pixels under a uniform zoom that fits the frame into
 * the composable bounds; [onLayoutComputed] reports the laid-out tree (document
 * coordinates) so the inspector can show computed geometry.
 */
@Composable
fun DesignArtboard(
    document: DesignDocument,
    pageId: String,
    modifier: Modifier = Modifier,
    deviceWidth: Double? = null,
    deviceHeight: Double? = null,
    selectedNodeId: String = "",
    showSelection: Boolean = true,
    onSelectNode: (String) -> Unit = {},
    onLayoutComputed: (LayoutBox?) -> Unit = {},
) {
    val page = document.pageById(pageId)
    val rootNode = page?.children?.firstOrNull()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val resolved = remember(document, rootNode) {
        rootNode?.let { DesignResolver(document).resolveNodeTree(it) }
    }
    val engine = remember(textMeasurer, density) {
        DesignLayoutEngine(ComposeDesignTextMeasurer(textMeasurer, density))
    }
    val layoutBox = remember(resolved, engine, deviceWidth, deviceHeight) {
        resolved?.let { engine.layout(it, deviceWidth, deviceHeight) }
    }
    val currentOnSelectNode = rememberUpdatedState(onSelectNode)
    val currentOnLayoutComputed = rememberUpdatedState(onLayoutComputed)
    LaunchedEffect(layoutBox) {
        currentOnLayoutComputed.value(layoutBox)
    }
    if (layoutBox == null) return

    val drawDesignContext = remember(textMeasurer, density) {
        DesignDrawContext(textMeasurer, density)
    }

    Canvas(
        modifier = modifier.pointerInput(layoutBox) {
            detectTapGestures { offset ->
                val zoom = zoomFor(layoutBox, Size(size.width.toFloat(), size.height.toFloat()))
                if (zoom <= 0f) return@detectTapGestures
                val hit = layoutBox.hitTest(
                    (offset.x / zoom).toDouble(),
                    (offset.y / zoom).toDouble(),
                ) ?: layoutBox
                currentOnSelectNode.value(selectableNodeId(hit))
            }
        },
    ) {
        val zoom = zoomFor(layoutBox, size)
        if (zoom <= 0f) return@Canvas
        scale(zoom, zoom, pivot = Offset.Zero) {
            drawDesignBox(layoutBox, drawDesignContext)
        }
        if (showSelection && selectedNodeId.isNotBlank() && selectedNodeId != layoutBox.node.sourceId) {
            layoutBox.findBySourceId(selectedNodeId)?.let { selection ->
                drawSelectionOverlay(selection, zoom)
            }
        }
    }
}

/**
 * Maps a hit box to the node the editor can select: instance internals collapse to
 * their outermost enclosing instance. Ids stay opaque — the mapping is carried on
 * the resolved node instead of being parsed out of the id string.
 */
fun selectableNodeId(box: LayoutBox): String =
    box.node.selectableId

private fun zoomFor(box: LayoutBox, size: Size): Float {
    if (box.width <= 0.0 || box.height <= 0.0) return 0f
    return minOf(
        size.width / box.width.toFloat(),
        size.height / box.height.toFloat(),
    )
}

private val SelectionBlue = Color(0xFF1E88FF)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionOverlay(
    selection: LayoutBox,
    zoom: Float,
) {
    val rect = Rect(
        (selection.x * zoom).toFloat(),
        (selection.y * zoom).toFloat(),
        (selection.right * zoom).toFloat(),
        (selection.bottom * zoom).toFloat(),
    )
    drawRect(
        color = SelectionBlue,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 1.5f),
    )
    val handle = 8f
    val positions = listOf(
        rect.topLeft,
        Offset(rect.center.x, rect.top),
        rect.topRight,
        Offset(rect.left, rect.center.y),
        Offset(rect.right, rect.center.y),
        rect.bottomLeft,
        Offset(rect.center.x, rect.bottom),
        rect.bottomRight,
    )
    positions.forEach { center ->
        drawRect(
            color = Color.White,
            topLeft = Offset(center.x - handle / 2, center.y - handle / 2),
            size = Size(handle, handle),
        )
        drawRect(
            color = SelectionBlue,
            topLeft = Offset(center.x - handle / 2, center.y - handle / 2),
            size = Size(handle, handle),
            style = Stroke(width = 1.5f),
        )
    }
}
