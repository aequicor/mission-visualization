package io.aequicor.visualization.engine.backend.compose

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.resolve.ResolvedInteraction
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import kotlin.math.ceil

/**
 * Pure geometry helpers behind the canvas renderer. Everything in this file is
 * Compose-free on purpose: the drawing pass stays a thin brush layer while the
 * math (crop windows, grid slices, hairline positions, mask/hit-test selection)
 * is unit-tested headlessly.
 */

/** Axis-aligned rect in document coordinates. */
internal data class RenderRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
}

// --- Media placeholder ----------------------------------------------------

/**
 * Notional rect the scaled asset would occupy inside (or overflowing) the box,
 * before any real decoding happens:
 *
 * - `Fill`/`Crop` cover the box (scale by the larger ratio) and shift so the
 *   focal point lands as close to the box center as coverage allows.
 * - `Fit` letterboxes (smaller ratio), centered; the focal point never moves a
 *   fitted image.
 * - `Stretch` and `Tile` map the asset onto the box rect exactly (tiles are a
 *   separate checker pattern).
 *
 * Without an intrinsic size the whole box is the notional image.
 */
internal fun mediaContentRect(
    box: RenderRect,
    intrinsicWidth: Double?,
    intrinsicHeight: Double?,
    fillMode: ImageScaleMode,
    focalX: Double = 0.5,
    focalY: Double = 0.5,
): RenderRect {
    if (intrinsicWidth == null || intrinsicHeight == null ||
        intrinsicWidth <= 0.0 || intrinsicHeight <= 0.0 ||
        box.width <= 0.0 || box.height <= 0.0
    ) {
        return box
    }
    return when (fillMode) {
        ImageScaleMode.Fill, ImageScaleMode.Crop -> {
            val scale = maxOf(box.width / intrinsicWidth, box.height / intrinsicHeight)
            val width = intrinsicWidth * scale
            val height = intrinsicHeight * scale
            // Center the focal point, then clamp so the image still covers the box.
            val x = (box.x + box.width / 2.0 - focalX.coerceIn(0.0, 1.0) * width)
                .coerceIn(box.right - width, box.x)
            val y = (box.y + box.height / 2.0 - focalY.coerceIn(0.0, 1.0) * height)
                .coerceIn(box.bottom - height, box.y)
            RenderRect(x, y, width, height)
        }
        ImageScaleMode.Fit -> {
            val scale = minOf(box.width / intrinsicWidth, box.height / intrinsicHeight)
            val width = intrinsicWidth * scale
            val height = intrinsicHeight * scale
            RenderRect(
                x = box.x + (box.width - width) / 2.0,
                y = box.y + (box.height - height) / 2.0,
                width = width,
                height = height,
            )
        }
        ImageScaleMode.Stretch, ImageScaleMode.Tile -> box
    }
}

/** Where the focal crosshair lands: the focal point mapped through the content rect. */
internal fun focalMarker(contentRect: RenderRect, focalX: Double, focalY: Double): Pair<Double, Double> =
    (contentRect.x + focalX.coerceIn(0.0, 1.0) * contentRect.width) to
        (contentRect.y + focalY.coerceIn(0.0, 1.0) * contentRect.height)

/** Dark cells of a checkerboard covering the rect, clipped to its bounds. */
internal fun checkerDarkCells(bounds: RenderRect, cell: Double): List<RenderRect> {
    if (cell <= 0.0 || bounds.width <= 0.0 || bounds.height <= 0.0) return emptyList()
    val columns = ceil(bounds.width / cell).toInt()
    val rows = ceil(bounds.height / cell).toInt()
    val cells = mutableListOf<RenderRect>()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 != 0) continue
            val x = bounds.x + column * cell
            val y = bounds.y + row * cell
            cells += RenderRect(
                x = x,
                y = y,
                width = minOf(cell, bounds.right - x),
                height = minOf(cell, bounds.bottom - y),
            )
        }
    }
    return cells
}

// --- Layout grid overlays ---------------------------------------------------

/** One strip of a columns/rows layout grid along its axis. */
internal data class GridSlice(val start: Double, val size: Double)

/**
 * Strip positions for a columns/rows layout grid definition along one axis
 * (`offset`/`extent` = the frame edge and length on that axis).
 *
 * `Stretch` divides the margin-inset extent into `count` equal strips separated
 * by `gutter`; the other alignments place `count` strips of `size` at the
 * start/center/end (center ignores margin, matching Figma). A missing `count`
 * with a fixed `size` fits as many strips as the extent allows; a definition
 * with neither resolves to nothing.
 */
internal fun layoutGridSlices(
    offset: Double,
    extent: Double,
    count: Int?,
    size: Double?,
    gutter: Double,
    margin: Double,
    alignment: LayoutGridAlignment,
): List<GridSlice> {
    if (extent <= 0.0) return emptyList()
    if (alignment == LayoutGridAlignment.Stretch || size == null) {
        val n = count ?: return emptyList()
        if (n < 1) return emptyList()
        val available = extent - 2 * margin - (n - 1) * gutter
        if (available <= 0.0) return emptyList()
        val sliceSize = available / n
        return List(n) { index -> GridSlice(offset + margin + index * (sliceSize + gutter), sliceSize) }
    }
    if (size <= 0.0) return emptyList()
    val n = count
        ?: ((extent - 2 * margin + gutter) / (size + gutter)).toInt().coerceAtLeast(0)
    if (n < 1) return emptyList()
    val total = n * size + (n - 1) * gutter
    val first = when (alignment) {
        LayoutGridAlignment.Start -> offset + margin
        LayoutGridAlignment.Center -> offset + (extent - total) / 2.0
        LayoutGridAlignment.End -> offset + extent - margin - total
        LayoutGridAlignment.Stretch -> offset + margin // unreachable; kept exhaustive
    }
    return List(n) { index -> GridSlice(first + index * (size + gutter), size) }
}

/** Interior line positions of a square layout grid: every `step` px, edges excluded. */
internal fun gridLinePositions(offset: Double, extent: Double, step: Double): List<Double> {
    if (step <= 0.0 || extent <= step) return emptyList()
    val positions = mutableListOf<Double>()
    var position = offset + step
    while (position < offset + extent - EDGE_EPSILON) {
        positions += position
        position += step
    }
    return positions
}

// --- Table hairlines --------------------------------------------------------

private const val EDGE_EPSILON = 0.5

/**
 * Hairline positions between grid tracks, derived from the laid-out cell start
 * edges along one axis: distinct interior starts (0.5px tolerance), each pulled
 * back by half the track gap so the line runs through the middle of the gutter.
 */
internal fun interiorTrackBoundaries(
    cellStarts: List<Double>,
    contentStart: Double,
    gap: Double,
): List<Double> {
    val distinct = mutableListOf<Double>()
    cellStarts.sorted().forEach { start ->
        if (start <= contentStart + EDGE_EPSILON) return@forEach
        if (distinct.none { kotlin.math.abs(it - start) <= EDGE_EPSILON }) distinct += start
    }
    return distinct.map { it - gap / 2.0 }
}

// --- Mask geometry selection --------------------------------------------------

/** Shape a mask node clips with; Alpha/Luminance masks both approximate to this. */
internal enum class MaskShape { RoundedRect, Ellipse, VectorPath, BoundingBox }

/**
 * Picks the clip geometry for a mask node: frames/rectangles clip with their
 * rounded-rect outline, ellipses with an oval, vectors with their parsed path
 * when one is authored — anything else falls back to the bounding box.
 */
internal fun maskShapeFor(node: ResolvedNode): MaskShape =
    when (node.shape?.shape) {
        null, ShapeType.Rectangle -> MaskShape.RoundedRect
        ShapeType.Ellipse -> MaskShape.Ellipse
        ShapeType.Vector ->
            if (node.shape?.paths.orEmpty().isNotEmpty()) MaskShape.VectorPath else MaskShape.BoundingBox
        ShapeType.Polygon, ShapeType.Star, ShapeType.Line, ShapeType.Arrow -> MaskShape.BoundingBox
    }

/**
 * Whether [mask] (at [maskIndex] among its siblings) clips the sibling at
 * [siblingIndex]: explicit `appliesTo` ids win; an empty list means legacy
 * Figma scope — every following sibling.
 */
internal fun maskAppliesTo(
    mask: LayoutBox,
    maskIndex: Int,
    sibling: LayoutBox,
    siblingIndex: Int,
): Boolean {
    val resolvedMask = mask.node.mask ?: return false
    if (resolvedMask.appliesTo.isEmpty()) return siblingIndex > maskIndex
    return sibling.node.id in resolvedMask.appliesTo || sibling.node.sourceId in resolvedMask.appliesTo
}

// --- Interaction hit-testing ----------------------------------------------------

/**
 * Nearest node at or above [hit] carrying a click-like interaction (`onClick`
 * or `onPress`), walking up through [root]'s box tree. Returns the interaction
 * with the box it lives on, or null when nothing on the ancestor chain is
 * clickable.
 */
internal fun clickableInteractionAt(root: LayoutBox, hit: LayoutBox): Pair<ResolvedInteraction, LayoutBox>? {
    val path = pathToBox(root, hit) ?: return null
    for (box in path.asReversed()) {
        val interaction = box.node.interactions.firstOrNull { interaction ->
            interaction.trigger == InteractionTrigger.OnClick || interaction.trigger == InteractionTrigger.OnPress
        }
        if (interaction != null) return interaction to box
    }
    return null
}

private fun pathToBox(root: LayoutBox, target: LayoutBox): List<LayoutBox>? {
    if (root === target) return listOf(root)
    root.children.forEach { child ->
        pathToBox(child, target)?.let { return listOf(root) + it }
    }
    return null
}
