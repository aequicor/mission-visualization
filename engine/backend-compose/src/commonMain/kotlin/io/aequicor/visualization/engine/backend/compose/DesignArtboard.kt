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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedInteraction
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.FontProvider
import io.aequicor.visualization.subsystems.typography.compose.NoFonts

/**
 * Document→screen transform for the canvas: `screen = doc * zoom + pan`. When passed
 * to [DesignArtboard] the artboard renders under this exact transform, so the editor
 * (which owns zoom/pan) can hit-test and place handles with the same math.
 */
data class CanvasViewport(val zoom: Float, val panX: Float, val panY: Float) {
    fun toScreen(x: Double, y: Double): Offset = Offset((x * zoom + panX).toFloat(), (y * zoom + panY).toFloat())

    fun toDocX(screenX: Float): Double = ((screenX - panX) / zoom).toDouble()

    fun toDocY(screenY: Float): Double = ((screenY - panY) / zoom).toDouble()
}

internal fun LayoutBox.withRootDocumentOrigin(): LayoutBox {
    val position = node.position ?: return this
    if (position.x == 0.0 && position.y == 0.0) return this
    return translateBy(position.x, position.y)
}

private fun LayoutBox.translateBy(dx: Double, dy: Double): LayoutBox =
    copy(
        x = x + dx,
        y = y + dy,
        children = children.map { it.translateBy(dx, dy) },
    )

/**
 * Renders the first top-level frame of a page through the resolve → layout → draw
 * pipeline. A pure renderer: it draws content and reports the laid-out tree, but performs
 * no drag/resize itself — the editor overlays those gestures using [onLayoutComputed]
 * geometry and the shared [viewport] transform.
 *
 * When [viewport] is null the artboard falls back to fitting the frame into the
 * composable bounds (top-left aligned), the original behavior.
 *
 * With [showSelection] true (the default), [selectedNodeIds] draws a plain axis-aligned
 * blue selection box (with handles on the last id) per node and [hoveredNodeId] draws a
 * thin hover outline — a simple built-in overlay for a non-editor caller. The interactive
 * Mission Editor (`EditorCanvasPane`) passes `showSelection = false` and draws its own
 * rotation-aware selection/hover/handles/rotate-affordance overlay instead (design-book
 * §18), since a plain axis-aligned box doesn't follow a rotated node's visual geometry.
 * Tap precedence is unchanged: a tapped node (or ancestor) carrying an `onClick`/`onPress`
 * interaction routes to [onInteraction] and suppresses [onSelectNode] for that tap.
 */
@Composable
fun DesignArtboard(
    document: DesignDocument,
    pageId: String,
    modifier: Modifier = Modifier,
    deviceWidth: Double? = null,
    deviceHeight: Double? = null,
    selectedNodeId: String = "",
    selectedNodeIds: Set<String> = emptySet(),
    hoveredNodeId: String = "",
    /** Node in vector point-edit mode: its object resize handles are suppressed. */
    vectorEditNodeId: String = "",
    viewport: CanvasViewport? = null,
    showSelection: Boolean = true,
    /** When false the artboard installs no tap handler; the caller owns all gestures. */
    interactive: Boolean = true,
    overlayOptions: DesignOverlayOptions = DesignOverlayOptions(),
    /** Dereferences `pathRef`/`iconRef` shapes to drawable geometry; app-supplied. */
    vectorAssets: VectorAssetProvider = NoVectorAssets,
    /** Resolves document font families; app-supplied (defaults to no bundled fonts). */
    fontProvider: FontProvider = NoFonts,
    onInteraction: ((ResolvedInteraction, LayoutBox) -> Unit)? = null,
    /** Invoked when a tap lands on a hyperlink range of a text node (takes tap precedence). */
    onLinkClick: ((io.aequicor.visualization.engine.ir.model.TextLink) -> Unit)? = null,
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
    // One measurer/cache is shared by the layout engine and the draw path; a font
    // arriving (generation bump) invalidates the cache and re-lays-out with real metrics.
    val typographyMeasurer = remember(textMeasurer, density, fontProvider) {
        ComposeTypographyMeasurer(textMeasurer, density, fontProvider)
    }
    val engine = remember(typographyMeasurer) {
        DesignLayoutEngine(ComposeDesignTextMeasurer(typographyMeasurer))
    }
    val layoutBox = remember(resolved, engine, deviceWidth, deviceHeight, fontProvider.generation) {
        resolved?.let { engine.layout(it, deviceWidth, deviceHeight).withRootDocumentOrigin() }
    }
    val currentOnSelectNode = rememberUpdatedState(onSelectNode)
    val currentOnInteraction = rememberUpdatedState(onInteraction)
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val currentOnLayoutComputed = rememberUpdatedState(onLayoutComputed)
    LaunchedEffect(layoutBox) {
        currentOnLayoutComputed.value(layoutBox)
    }
    if (layoutBox == null) return

    val drawDesignContext = remember(textMeasurer, density, vectorAssets, typographyMeasurer) {
        DesignDrawContext(textMeasurer, density, vectorAssets, typographyMeasurer)
    }

    val allSelected = if (selectedNodeId.isNotBlank()) selectedNodeIds + selectedNodeId else selectedNodeIds

    val tapModifier = if (interactive) {
        modifier.pointerInput(layoutBox, viewport) {
            detectTapGestures { offset ->
                val zoom = viewport?.zoom ?: zoomFor(layoutBox, Size(size.width.toFloat(), size.height.toFloat()))
                if (zoom <= 0f) return@detectTapGestures
                val panX = viewport?.panX ?: fallbackPanX(layoutBox, zoom)
                val panY = viewport?.panY ?: fallbackPanY(layoutBox, zoom)
                val docX = ((offset.x - panX) / zoom).toDouble()
                val docY = ((offset.y - panY) / zoom).toDouble()
                val hit = layoutBox.hitTest(docX, docY) ?: layoutBox
                // A hyperlink hit takes precedence over selection/interaction.
                currentOnLinkClick.value?.let { onLink ->
                    linkAtPoint(hit, docX, docY, typographyMeasurer)?.let { link ->
                        onLink(link)
                        return@detectTapGestures
                    }
                }
                val interactionCallback = currentOnInteraction.value
                if (interactionCallback != null) {
                    val clickable = clickableInteractionAt(layoutBox, hit)
                    if (clickable != null) {
                        interactionCallback(clickable.first, clickable.second)
                        return@detectTapGestures
                    }
                }
                currentOnSelectNode.value(selectableNodeId(hit))
            }
        }
    } else {
        modifier
    }
    Canvas(modifier = tapModifier) {
        val zoom = viewport?.zoom ?: zoomFor(layoutBox, size)
        if (zoom <= 0f) return@Canvas
        val panX = viewport?.panX ?: fallbackPanX(layoutBox, zoom)
        val panY = viewport?.panY ?: fallbackPanY(layoutBox, zoom)
        translate(panX, panY) {
            scale(zoom, zoom, pivot = Offset.Zero) {
                drawDesignBox(layoutBox, drawDesignContext)
                if (overlayOptions.anyEnabled) {
                    drawDesignOverlays(layoutBox, overlayOptions, drawDesignContext, hairline = 1f / zoom)
                }
            }
        }
        if (hoveredNodeId.isNotBlank() && hoveredNodeId !in allSelected) {
            layoutBox.findBySourceId(hoveredNodeId)?.let { drawHoverOutline(it, zoom, panX, panY) }
        }
        if (showSelection) {
            allSelected.forEach { id ->
                if (id != layoutBox.node.sourceId || allSelected.size == 1) {
                    layoutBox.findBySourceId(id)?.let { box ->
                        // Point-edit mode replaces object handles with path anchors, so suppress them.
                        val handles = allSelected.size == 1 && id != vectorEditNodeId
                        drawSelectionOverlay(box, zoom, panX, panY, handles = handles)
                    }
                }
            }
        }
    }
}

/**
 * Maps a hit box to the node the editor can select: instance internals collapse to
 * their outermost enclosing instance.
 */
fun selectableNodeId(box: LayoutBox): String = box.node.selectableId

private fun zoomFor(box: LayoutBox, size: Size): Float {
    if (box.width <= 0.0 || box.height <= 0.0) return 0f
    return minOf(size.width / box.width.toFloat(), size.height / box.height.toFloat())
}

private fun fallbackPanX(box: LayoutBox, zoom: Float): Float = (-box.x * zoom).toFloat()

private fun fallbackPanY(box: LayoutBox, zoom: Float): Float = (-box.y * zoom).toFloat()

private val SelectionBlue = Color(0xFF1E88FF)

private fun screenRect(box: LayoutBox, zoom: Float, panX: Float, panY: Float): Rect = Rect(
    (box.x * zoom + panX).toFloat(),
    (box.y * zoom + panY).toFloat(),
    (box.right * zoom + panX).toFloat(),
    (box.bottom * zoom + panY).toFloat(),
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHoverOutline(
    box: LayoutBox,
    zoom: Float,
    panX: Float,
    panY: Float,
) {
    val rect = screenRect(box, zoom, panX, panY)
    drawRect(color = SelectionBlue.copy(alpha = 0.85f), topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 1.5f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionOverlay(
    selection: LayoutBox,
    zoom: Float,
    panX: Float,
    panY: Float,
    handles: Boolean,
) {
    val rect = screenRect(selection, zoom, panX, panY)
    drawRect(color = SelectionBlue, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 1.5f))
    if (!handles) return
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
