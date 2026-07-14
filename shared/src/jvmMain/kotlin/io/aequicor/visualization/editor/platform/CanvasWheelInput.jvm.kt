package io.aequicor.visualization.editor.platform

/**
 * Compose Desktop exposes AWT preciseWheelRotation as scrollDelta: one regular wheel notch is
 * normally 1, not one screen pixel. Keep fractional high-resolution deltas proportional.
 */
internal actual fun platformCanvasWheelPanPixels(scrollUnits: Float, density: Float): Float =
    scrollUnits * DesktopWheelPanDpPerUnit * density

internal actual fun platformCanvasWheelZoomUnits(scrollUnits: Float): Float =
    scrollUnits * DesktopWheelZoomUnitsPerTick

private const val DesktopWheelPanDpPerUnit = 64f

/** With the shared 0.025 exponential sensitivity, one notch zooms by exp(0.15), about 16%. */
private const val DesktopWheelZoomUnitsPerTick = 6f
