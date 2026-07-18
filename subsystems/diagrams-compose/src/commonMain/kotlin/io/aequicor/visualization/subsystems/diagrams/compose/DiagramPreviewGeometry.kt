package io.aequicor.visualization.subsystems.diagrams.compose

import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect

/**
 * Content box of a small preview canvas: [inset] on every side plus [verticalTrim]
 * extra above and below. Dimensions are clamped to zero because the canvas can be
 * degenerate — the first layout frame and narrow dropdown rows measure at (or near)
 * zero — while the synthetic nodes built from this box require non-negative sizes.
 */
fun diagramPreviewContentBox(
    canvasWidth: Double,
    canvasHeight: Double,
    inset: Double,
    verticalTrim: Double = 0.0,
): DiagramRect = DiagramRect(
    x = inset,
    y = inset + verticalTrim,
    width = (canvasWidth - 2 * inset).coerceAtLeast(0.0),
    height = (canvasHeight - 2 * (inset + verticalTrim)).coerceAtLeast(0.0),
)

/** True when the box has no drawable area, so previews skip drawing entirely. */
val DiagramRect.isDegenerate: Boolean get() = width <= 0.0 || height <= 0.0
