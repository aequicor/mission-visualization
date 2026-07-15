package io.aequicor.visualization.editor.platform

internal actual fun platformCanvasWheelPanPixels(scrollUnits: Float, density: Float): Float = scrollUnits

internal actual fun platformCanvasWheelZoomUnits(scrollUnits: Float): Float = scrollUnits

internal actual fun installCanvasMagnification(
    onMagnification: (CanvasMagnificationEvent) -> Unit,
): CanvasMagnificationHandle = NoCanvasMagnification
