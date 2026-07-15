package io.aequicor.visualization.editor.platform

import kotlin.math.abs

/** Converts platform-specific wheel units to screen pixels used by canvas panning. */
internal expect fun platformCanvasWheelPanPixels(scrollUnits: Float, density: Float): Float

/** Normalizes platform-specific wheel units before applying the shared exponential zoom curve. */
internal expect fun platformCanvasWheelZoomUnits(scrollUnits: Float): Float

/** A native trackpad magnification update plus the pointer position in Compose-scene dp. */
internal data class CanvasMagnificationEvent(
    val magnification: Float,
    val sceneXDp: Float? = null,
    val sceneYDp: Float? = null,
)

internal fun interface CanvasMagnificationHandle {
    fun dispose()
}

internal val NoCanvasMagnification = CanvasMagnificationHandle {}

/** Installs native magnification input where Compose does not expose it as a pointer event. */
internal expect fun installCanvasMagnification(
    onMagnification: (CanvasMagnificationEvent) -> Unit,
): CanvasMagnificationHandle

internal data class CanvasWheelAxes(val x: Float, val y: Float)

/**
 * Resolves Compose's two desktop horizontal-scroll representations without losing web diagonals.
 * Compose Desktop converts an AWT shift/horizontal wheel event to x before it reaches us, while
 * some other targets leave the same event in y and only set Shift.
 */
internal fun canvasWheelPanAxes(deltaX: Float, deltaY: Float, shiftPressed: Boolean): CanvasWheelAxes =
    if (shiftPressed) {
        CanvasWheelAxes(x = if (deltaX != 0f) deltaX else deltaY, y = 0f)
    } else {
        CanvasWheelAxes(x = deltaX, y = deltaY)
    }

/** Pinch-like wheel events are not guaranteed to use y on every desktop backend. */
internal fun canvasWheelZoomAxis(deltaX: Float, deltaY: Float): Float =
    if (abs(deltaY) >= abs(deltaX)) deltaY else deltaX

/** Apple's magnification is a relative scale where 0.1 means ten percent larger. */
internal fun canvasMagnificationFactor(magnification: Float): Float =
    (1f + magnification).coerceIn(MinMagnificationFactor, MaxMagnificationFactor)

private const val MinMagnificationFactor = 0.5f
private const val MaxMagnificationFactor = 2f
