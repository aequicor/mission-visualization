package io.aequicor.visualization.subsystems.diagrams.geometry

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect

/** `√2`, the ratio between an ellipse's bounding box and its inscribed rectangle. */
private const val SQRT_2 = 1.4142135623730951

/**
 * The rectangle a node's caption may occupy, by perimeter family ([perimeterKind]) — the
 * single source of truth shared by the canvas renderer, the SVG exporter and the inline
 * editor's plate, so the three cannot disagree about where text goes.
 *
 * - `RECTANGLE` — the bounding box, inset by [padding].
 * - `ELLIPSE` — the largest axis-aligned rectangle inscribed in the ellipse (`w/√2 × h/√2`),
 *   centered, then inset. The bounding box overstates a use-case ellipse by ~41% per axis.
 * - `RHOMBUS` — the inscribed rectangle (`w/2 × h/2`), centered, then inset.
 * - `OUTLINE` — v1 gap: falls back to the bounding box inset. Exact inscribed rectangles for
 *   triangles/hexagons/trapezoids are out of scope; the triangle is the worst offender.
 *
 * Degenerate results are clamped to a non-negative, empty rect rather than inverted.
 */
public fun DiagramNode.labelBox(padding: Double): DiagramRect {
    val bounds = this.bounds
    val inner = when (perimeterKind()) {
        DiagramPerimeterKind.RECTANGLE, DiagramPerimeterKind.OUTLINE -> bounds
        DiagramPerimeterKind.ELLIPSE -> bounds.centeredInner(bounds.width / SQRT_2, bounds.height / SQRT_2)
        DiagramPerimeterKind.RHOMBUS -> bounds.centeredInner(bounds.width / 2.0, bounds.height / 2.0)
    }
    return inner.insetClamped(padding)
}

/**
 * The node bounds that would give a caption of [textWidth] × [textHeight] exactly its
 * [labelBox] at [padding] — the inverse of [labelBox], used to hug a shape to its text.
 *
 * `OUTLINE` inverts as `RECTANGLE` (mirroring [labelBox]'s documented v1 gap).
 */
public fun boundsForLabel(
    kind: DiagramPerimeterKind,
    textWidth: Double,
    textHeight: Double,
    padding: Double,
): DiagramSize {
    val boxWidth = (textWidth + 2 * padding).coerceAtLeast(0.0)
    val boxHeight = (textHeight + 2 * padding).coerceAtLeast(0.0)
    return when (kind) {
        DiagramPerimeterKind.RECTANGLE, DiagramPerimeterKind.OUTLINE -> DiagramSize(boxWidth, boxHeight)
        DiagramPerimeterKind.ELLIPSE -> DiagramSize(boxWidth * SQRT_2, boxHeight * SQRT_2)
        DiagramPerimeterKind.RHOMBUS -> DiagramSize(boxWidth * 2.0, boxHeight * 2.0)
    }
}

/** A width/height pair in document px. */
public data class DiagramSize(
    val width: Double,
    val height: Double,
)

/**
 * The padding a node's centered caption is drawn with, per payload — shared so the canvas and
 * the SVG export inset by the same amount.
 *
 * Payloads whose caption lives in a header band (container, swimlane) or in rows (class, ER,
 * table) are not centered-label payloads and are not described here.
 */
public fun DiagramNode.labelPadding(): Double = when (payload) {
    is DiagramNodePayload.BasicShape -> 4.0
    is DiagramNodePayload.FlowchartNode -> 6.0
    is DiagramNodePayload.BpmnNode -> 6.0
    is UmlNoteNode -> 6.0
    is UmlPackageNode -> 6.0
    else -> 8.0
}

private fun DiagramRect.centeredInner(innerWidth: Double, innerHeight: Double): DiagramRect =
    DiagramRect(
        x = x + (width - innerWidth) / 2.0,
        y = y + (height - innerHeight) / 2.0,
        width = innerWidth,
        height = innerHeight,
    )

private fun DiagramRect.insetClamped(padding: Double): DiagramRect =
    DiagramRect(
        x = x + padding,
        y = y + padding,
        width = (width - 2 * padding).coerceAtLeast(0.0),
        height = (height - 2 * padding).coerceAtLeast(0.0),
    )
