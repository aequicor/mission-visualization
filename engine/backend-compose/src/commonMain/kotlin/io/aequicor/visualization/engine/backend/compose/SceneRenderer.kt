package io.aequicor.visualization.engine.backend.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignTextMeasurer
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.scene.projection.SceneDebugModel
import io.aequicor.visualization.engine.scene.projection.SceneLayer
import io.aequicor.visualization.engine.scene.projection.SceneRenderModel
import io.aequicor.visualization.engine.scene.runtime.PointerKind
import io.aequicor.visualization.engine.scene.runtime.SceneEvent
import io.aequicor.visualization.engine.scene.sample.VisualOverride

/**
 * The Compose text measurer as the pure [DesignTextMeasurer] interface, so `:shared` can build a
 * `DesignLayoutEngine` / `ScreenComposer` for the Scene runtime with the real Compose metrics —
 * matching the Canvas geometry exactly.
 */
@Composable
fun rememberDesignTextMeasurer(): DesignTextMeasurer {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(textMeasurer, density) { ComposeDesignTextMeasurer(textMeasurer, density) }
}

private val DebugAccent = Color(0xFF1E88FF)
private val DebugWarning = Color(0xFFF5A623)

/**
 * The Scene renderer: consumes a [SceneRenderModel] and draws its layers (one when stable, two
 * mid-transition, plus overlays) under the shared [viewport] transform, applying per-layer and
 * per-node [VisualOverride]s. Pointer taps hit-test the topmost hit-testable layer and are emitted
 * as [SceneEvent.Pointer] — this renderer knows nothing about `Navigate`/`SetVariable`/overlays;
 * action semantics were already resolved by the runtime. It reuses the existing [drawDesignBox]
 * draw path.
 */
@Composable
fun SceneRenderer(
    renderModel: SceneRenderModel,
    viewport: CanvasViewport,
    modifier: Modifier = Modifier,
    debugOverlays: Boolean = true,
    onEvent: (SceneEvent) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = remember(textMeasurer, density) { DesignDrawContext(textMeasurer, density) }
    val currentOnEvent = rememberUpdatedState(onEvent)
    val layers = renderModel.layers.sortedBy { it.zIndex }

    Canvas(
        modifier = modifier.pointerInput(renderModel) {
            detectTapGestures { offset ->
                val target = layers.filter { it.hitTestable }.maxByOrNull { it.zIndex } ?: return@detectTapGestures
                val docX = viewport.toDocX(offset.x)
                val docY = viewport.toDocY(offset.y)
                val hit = target.snapshot.composed.root.hitTest(docX, docY) ?: return@detectTapGestures
                currentOnEvent.value(SceneEvent.Pointer(PointerKind.Click, hit.node.selectableId))
            }
        },
    ) {
        if (viewport.zoom <= 0f) return@Canvas
        translate(viewport.panX, viewport.panY) {
            scale(viewport.zoom, viewport.zoom, pivot = Offset.Zero) {
                layers.forEach { layer -> drawSceneLayer(layer, context) }
            }
        }
        if (debugOverlays) drawSceneDebug(renderModel.debug, size)
    }
}

private fun DrawScope.drawSceneLayer(layer: SceneLayer, context: DesignDrawContext) {
    val root = layer.snapshot.composed.root
    val overridesById = layer.nodeOverrides.associate { it.nodeId to it.override }
    withVisualOverride(root, layer.layerOverride) {
        if (overridesById.isEmpty()) {
            drawDesignBox(root, context)
        } else {
            drawBoxWithOverrides(root, overridesById, context)
        }
    }
}

/**
 * Draws [box]'s subtree applying per-node overrides. When a descendant is overridden, children are
 * drawn through [drawDesignBox]'s child-drawer hook so the container's group opacity, rotation, clip
 * and sibling masks still wrap them; a node's own override wraps its whole subtree (children keep
 * recursing so nested overrides also apply). A subtree with no overrides draws the plain path.
 */
private fun DrawScope.drawBoxWithOverrides(
    box: LayoutBox,
    overrides: Map<String, VisualOverride>,
    context: DesignDrawContext,
) {
    val own = overrides[box.node.sourceId]
    val childDrawer: (DrawScope.(LayoutBox) -> Unit)? =
        if (box.children.any { child -> overrides.keys.any { id -> child.findBySourceId(id) != null } }) {
            { child -> drawBoxWithOverrides(child, overrides, context) }
        } else {
            null
        }
    if (own != null && !own.isIdentity) {
        withVisualOverride(box, own) { drawDesignBox(box, context, childDrawer) }
    } else {
        drawDesignBox(box, context, childDrawer)
    }
}

/** Applies a [VisualOverride] (opacity layer + translate/scale/rotate about the box center). */
private fun DrawScope.withVisualOverride(box: LayoutBox, override: VisualOverride, block: DrawScope.() -> Unit) {
    if (override.isIdentity) {
        block()
        return
    }
    val cx = (box.x + box.width / 2.0).toFloat()
    val cy = (box.y + box.height / 2.0).toFloat()
    withOpacityLayer(override.opacity.toFloat().coerceIn(0f, 1f)) {
        translate(override.translateX.toFloat(), override.translateY.toFloat()) {
            scale(override.scaleX.toFloat(), override.scaleY.toFloat(), pivot = Offset(cx, cy)) {
                rotate(override.rotationDeg.toFloat(), pivot = Offset(cx, cy)) {
                    block()
                }
            }
        }
    }
}

private fun DrawScope.withOpacityLayer(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) {
        block()
        return
    }
    // Huge bounds so overflowing/rotated content is not clipped (mirrors drawDesignBox).
    val bounds = Rect(-1_000_000f, -1_000_000f, 1_000_000f, 1_000_000f)
    val paint = Paint().apply { this.alpha = alpha }
    drawContext.canvas.saveLayer(bounds, paint)
    block()
    drawContext.canvas.restore()
}

/** Minimal in-canvas debug: a transition progress bar. The rich trace UI is a `:shared` pane. */
private fun DrawScope.drawSceneDebug(debug: SceneDebugModel, canvasSize: Size) {
    val transition = debug.transition ?: return
    val barHeight = 3f
    val y = canvasSize.height - barHeight
    drawRect(color = DebugAccent.copy(alpha = 0.18f), topLeft = Offset(0f, y), size = Size(canvasSize.width, barHeight))
    drawRect(
        color = DebugWarning,
        topLeft = Offset(0f, y),
        size = Size(canvasSize.width * transition.progress.toFloat().coerceIn(0f, 1f), barHeight),
    )
    // A thin frame marks that a transition is active (two layers on screen).
    drawRect(color = DebugAccent.copy(alpha = 0.35f), size = canvasSize, style = Stroke(width = 1f))
}
