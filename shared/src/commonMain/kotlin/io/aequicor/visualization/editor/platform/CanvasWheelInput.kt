package io.aequicor.visualization.editor.platform

/** Converts platform-specific wheel units to screen pixels used by canvas panning. */
internal expect fun platformCanvasWheelPanPixels(scrollUnits: Float, density: Float): Float

/** Normalizes platform-specific wheel units before applying the shared exponential zoom curve. */
internal expect fun platformCanvasWheelZoomUnits(scrollUnits: Float): Float
