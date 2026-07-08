package io.aequicor.visualization.editor.presentation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure, Compose-free canvas overlay geometry: rotated selection bounds/handles, resize
 * cursor orientation under rotation, move-drag center anchor lines and Alt-measurement
 * gaps. Kept out of the UI so the math is unit-testable headlessly (mirrors
 * [computeResize] in [ResizeMath]). Nothing here mutates the document; every function is
 * a pure read of already-laid-out geometry.
 */

/** Axis-aligned rectangle in document coordinates (the box *before* rotation is applied). */
data class BoundsBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0
}

/** A point in document coordinates. */
data class GeoPoint(val x: Double, val y: Double)

/** A line segment in document coordinates. */
data class LineSegment(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

// --- Rotation -----------------------------------------------------------------

/**
 * Rotates [point] around [center] by [degrees], matching the *visual* rotation applied
 * at render time (`DrawScope.rotate(degrees, pivot)` in the Compose backend, and its
 * exact inverse used by `LayoutBox.hitTest`). Positive degrees appear clockwise on
 * screen (document Y grows downward).
 */
fun rotatePointAroundCenter(point: GeoPoint, center: GeoPoint, degrees: Double): GeoPoint {
    if (degrees == 0.0) return point
    val radians = degrees * kotlin.math.PI / 180.0
    val dx = point.x - center.x
    val dy = point.y - center.y
    val cosR = cos(radians)
    val sinR = sin(radians)
    return GeoPoint(
        center.x + dx * cosR - dy * sinR,
        center.y + dx * sinR + dy * cosR,
    )
}

/** Rotates a displacement vector; used to move a drag delta between frames. A vector rotation is a point rotation around the origin. */
fun rotateVector(dx: Double, dy: Double, degrees: Double): GeoPoint =
    rotatePointAroundCenter(GeoPoint(dx, dy), GeoPoint(0.0, 0.0), degrees)

/** Normalizes an angle into `[0, 360)`. */
fun normalizeAngleDegrees(degrees: Double): Double {
    val mod = degrees % 360.0
    return if (mod < 0.0) mod + 360.0 else mod
}

/** Snaps [degrees] to the nearest multiple of [increment] (degrees). */
fun snapAngleToIncrement(degrees: Double, increment: Double): Double {
    if (increment <= 0.0) return degrees
    return kotlin.math.round(degrees / increment) * increment
}

/**
 * Angle from [center] to [point], in degrees clockwise from "up" (12 o'clock) — the same
 * convention as [DesignNode.rotation]: rotating a node by this many degrees turns its
 * "up" edge to point at the corresponding clock position.
 */
fun angleFromCenterDegrees(center: GeoPoint, point: GeoPoint): Double {
    val dx = point.x - center.x
    val dy = point.y - center.y
    return normalizeAngleDegrees(atan2(dx, -dy) * 180.0 / kotlin.math.PI)
}

/** The box's four corners (unrotated local space) rotated by [degrees], in TL/TR/BR/BL order. */
fun rotatedCorners(box: BoundsBox, degrees: Double): List<GeoPoint> {
    val center = GeoPoint(box.centerX, box.centerY)
    val local = listOf(
        GeoPoint(box.x, box.y),
        GeoPoint(box.right, box.y),
        GeoPoint(box.right, box.bottom),
        GeoPoint(box.x, box.bottom),
    )
    return local.map { rotatePointAroundCenter(it, center, degrees) }
}

/** The eight handle anchor points (corners + edge midpoints) of [box] rotated by [degrees]. */
fun rotatedHandlePoints(box: BoundsBox, degrees: Double): Map<ResizeHandle, GeoPoint> {
    val center = GeoPoint(box.centerX, box.centerY)
    val local = mapOf(
        ResizeHandle.TopLeft to GeoPoint(box.x, box.y),
        ResizeHandle.Top to GeoPoint(box.centerX, box.y),
        ResizeHandle.TopRight to GeoPoint(box.right, box.y),
        ResizeHandle.Left to GeoPoint(box.x, box.centerY),
        ResizeHandle.Right to GeoPoint(box.right, box.centerY),
        ResizeHandle.BottomLeft to GeoPoint(box.x, box.bottom),
        ResizeHandle.Bottom to GeoPoint(box.centerX, box.bottom),
        ResizeHandle.BottomRight to GeoPoint(box.right, box.bottom),
    )
    return local.mapValues { (_, point) -> rotatePointAroundCenter(point, center, degrees) }
}

/**
 * Position of the rotate affordance for [box]: a point [offset] document-units above the
 * (unrotated) top edge center, then rotated along with the box by [degrees] — so it orbits
 * the selection exactly like the handles do.
 */
fun rotateAffordancePoint(box: BoundsBox, degrees: Double, offset: Double): GeoPoint =
    rotatePointAroundCenter(GeoPoint(box.centerX, box.y - offset), GeoPoint(box.centerX, box.centerY), degrees)

/** The axis-aligned bounding box of [points] (e.g. a rotated box's transformed visual bounds). */
fun axisAlignedBounds(points: List<GeoPoint>): BoundsBox {
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }
    return BoundsBox(minX, minY, maxX - minX, maxY - minY)
}

// --- Resize cursor orientation --------------------------------------------------

/** The four cursor orientations a resize handle can show. */
enum class ResizeCursorKind { Horizontal, Vertical, DiagonalTopLeftBottomRight, DiagonalTopRightBottomLeft }

/** Unrotated direction angle of each handle, clockwise from "up" (matches [angleFromCenterDegrees]). */
private fun baseHandleAngleDegrees(handle: ResizeHandle): Double = when (handle) {
    ResizeHandle.Top -> 0.0
    ResizeHandle.TopRight -> 45.0
    ResizeHandle.Right -> 90.0
    ResizeHandle.BottomRight -> 135.0
    ResizeHandle.Bottom -> 180.0
    ResizeHandle.BottomLeft -> 225.0
    ResizeHandle.Left -> 270.0
    ResizeHandle.TopLeft -> 315.0
}

private fun circularDistance(a: Double, b: Double, period: Double): Double {
    val diff = abs(a - b) % period
    return min(diff, period - diff)
}

/**
 * Which cursor a [handle] should show once the component is rotated by [rotationDegrees]:
 * the handle's drag axis rotates with the component, so its effective angle is bucketed
 * (mod 180°, an axis is bidirectional) into the nearest of the four cursor orientations
 * the platform actually has ([EditorCursors]).
 */
fun resizeCursorKindForHandle(handle: ResizeHandle, rotationDegrees: Double): ResizeCursorKind {
    val angle = normalizeAngleDegrees(baseHandleAngleDegrees(handle) + rotationDegrees) % 180.0
    val buckets = listOf(
        0.0 to ResizeCursorKind.Vertical,
        45.0 to ResizeCursorKind.DiagonalTopRightBottomLeft,
        90.0 to ResizeCursorKind.Horizontal,
        135.0 to ResizeCursorKind.DiagonalTopLeftBottomRight,
    )
    return buckets.minBy { (bucketAngle, _) -> circularDistance(angle, bucketAngle, 180.0) }.second
}

// --- Move-drag center anchor lines ---------------------------------------------

/** Center anchor lines through [box]'s center, extended to [parent]'s edges (design-book §18). */
data class CenterAnchorLines(val horizontal: LineSegment, val vertical: LineSegment)

fun centerAnchorLines(box: BoundsBox, parent: BoundsBox): CenterAnchorLines =
    CenterAnchorLines(
        horizontal = LineSegment(parent.x, box.centerY, parent.right, box.centerY),
        vertical = LineSegment(box.centerX, parent.y, box.centerX, parent.bottom),
    )

// --- Alt measurement gaps --------------------------------------------------------

/**
 * Directional gaps and/or center distances between [selected] and [target] (design-book
 * §18 "Alt measurement preview"). When [target] fully contains [selected] (the common
 * "measure against parent frame" case) every side reports its padding. Otherwise each
 * axis reports a directional gap when the boxes don't overlap on it, or a center-to-center
 * distance when they do (a raw edge gap would be negative/misleading there).
 */
data class GapMeasurement(
    val top: Double? = null,
    val right: Double? = null,
    val bottom: Double? = null,
    val left: Double? = null,
    val centerXDistance: Double? = null,
    val centerYDistance: Double? = null,
)

fun measureGaps(selected: BoundsBox, target: BoundsBox): GapMeasurement {
    val contains = target.x <= selected.x && target.y <= selected.y &&
        target.right >= selected.right && target.bottom >= selected.bottom
    if (contains) {
        return GapMeasurement(
            top = selected.y - target.y,
            right = target.right - selected.right,
            bottom = target.bottom - selected.bottom,
            left = selected.x - target.x,
        )
    }
    val overlapsX = selected.x < target.right && target.x < selected.right
    val overlapsY = selected.y < target.bottom && target.y < selected.bottom
    val left = if (!overlapsX && target.right <= selected.x) selected.x - target.right else null
    val right = if (!overlapsX && selected.right <= target.x) target.x - selected.right else null
    val top = if (!overlapsY && target.bottom <= selected.y) selected.y - target.bottom else null
    val bottom = if (!overlapsY && selected.bottom <= target.y) target.y - selected.bottom else null
    val centerXDistance = if (overlapsX) abs(selected.centerX - target.centerX) else null
    val centerYDistance = if (overlapsY) abs(selected.centerY - target.centerY) else null
    return GapMeasurement(top, right, bottom, left, centerXDistance, centerYDistance)
}

// --- Auto layout reorder-by-drag ------------------------------------------------

/**
 * Nearest insertion index for a flow-container reorder drag, among [siblings] (the
 * dragged node's flow siblings *excluding itself*, in document order) given the
 * pointer's position along the layout's main axis ([pointerMain]: document X for a
 * horizontal flow, document Y for a vertical one). The result indexes directly into the
 * post-removal sibling list — i.e. it's ready to use as the `index` of an insert-after-
 * remove reparent (design-book §18 "Auto layout children should reorder ... during
 * drag").
 */
fun flowInsertionIndex(siblings: List<BoundsBox>, pointerMain: Double, horizontal: Boolean): Int {
    siblings.forEachIndexed { index, sibling ->
        val center = if (horizontal) sibling.centerX else sibling.centerY
        if (pointerMain < center) return index
    }
    return siblings.size
}

/** Where the insertion-line indicator sits: a line across [parent]'s cross axis, at the boundary [index] represents among [siblings]. */
fun flowInsertionLine(siblings: List<BoundsBox>, index: Int, parent: BoundsBox, horizontal: Boolean): LineSegment {
    val clamped = index.coerceIn(0, siblings.size)
    val position = when {
        siblings.isEmpty() -> if (horizontal) parent.centerX else parent.centerY
        clamped <= 0 -> if (horizontal) siblings.first().x else siblings.first().y
        clamped >= siblings.size -> if (horizontal) siblings.last().right else siblings.last().bottom
        else -> {
            val before = siblings[clamped - 1]
            val after = siblings[clamped]
            if (horizontal) (before.right + after.x) / 2.0 else (before.bottom + after.y) / 2.0
        }
    }
    return if (horizontal) {
        LineSegment(position, parent.y, position, parent.bottom)
    } else {
        LineSegment(parent.x, position, parent.right, position)
    }
}

// --- Move-drag smart snapping (design-book §18 "snap ... to parent center, parent edges,
//     sibling edges and sibling centers") -----------------------------------------------

/** Per-axis snap correction (doc units) to add to the raw drag, plus the guide lines to draw. */
data class SnapResult(
    val dx: Double,
    val dy: Double,
    val guides: List<LineSegment>,
)

/** Translates the box by ([dx], [dy]); size is unchanged. */
fun BoundsBox.translate(dx: Double, dy: Double): BoundsBox = copy(x = x + dx, y = y + dy)

/** One axis's winning alignment: how far to nudge, the line it aligned to, and which target owns it. */
private data class AxisSnap(val delta: Double, val aligned: Double, val target: BoundsBox)

/**
 * Best alignment on one axis: compares each of [sourceLines] (a dragged box's left/center/right
 * or top/center/bottom, tagged with whether it is the *center*) against every target's
 * corresponding lines ([targetLinesOf]); keeps the pair with the smallest `|delta|` within
 * [threshold]. Exact ties prefer a center-involving alignment (rank 0 center↔center, 1 mixed,
 * 2 edge↔edge); remaining ties keep the earlier one (fixed iteration order → deterministic).
 */
private fun snapAxis(
    sourceLines: List<Pair<Double, Boolean>>,
    targets: List<BoundsBox>,
    targetLinesOf: (BoundsBox) -> List<Pair<Double, Boolean>>,
    threshold: Double,
): AxisSnap? {
    var best: AxisSnap? = null
    var bestDistance = Double.MAX_VALUE
    var bestRank = Int.MAX_VALUE
    targets.forEach { target ->
        val targetLines = targetLinesOf(target)
        sourceLines.forEach { (src, srcCenter) ->
            targetLines.forEach { (dst, dstCenter) ->
                val delta = dst - src
                val distance = abs(delta)
                if (distance <= threshold) {
                    val rank = if (srcCenter && dstCenter) 0 else if (srcCenter || dstCenter) 1 else 2
                    val better = distance < bestDistance || (distance == bestDistance && rank < bestRank)
                    if (better) {
                        bestDistance = distance
                        bestRank = rank
                        best = AxisSnap(delta = delta, aligned = dst, target = target)
                    }
                }
            }
        }
    }
    return best
}

/**
 * Figma-style magnetic snap for a free-move drag. [moved] is the dragged selection's proposed
 * union bounds (already displaced by the raw drag) in document coords; [targets] are the
 * candidate boxes to align against (each sibling box + the parent frame box); [threshold] is the
 * snap radius in DOCUMENT units (the caller converts a fixed screen-px radius via `/ zoom`, so
 * the feel is constant on screen). Snaps X and Y independently to the single nearest candidate
 * line within [threshold]; returns the delta to add to [moved] plus one guide [LineSegment] per
 * snapped axis (spanning the union extent of the snapped box and its winning target). Pure and
 * deterministic. Empty [targets], non-positive [threshold], or nothing in range → a zero result.
 */
/**
 * Backward-compatible alignment-only snapping: [targets] are treated as a flat list of
 * boxes to align edges/centers against, with no container-relative "beautiful" anchors.
 * Retained for its regression suite and as the edge/center-only entry point; delegates to
 * [computeAnchors] (the single source of truth) with no containers.
 */
fun computeSnap(moved: BoundsBox, targets: List<BoundsBox>, threshold: Double): SnapResult {
    val result = computeAnchors(moved, containers = emptyList(), siblings = targets, threshold = threshold)
    return SnapResult(result.dx, result.dy, result.guides.map { it.line })
}

// --- Beautiful-anchor snapping (design-book §18 + "beautiful positions") ----------------
//
// Extends the edge/center alignment above with the positions a designer reads as
// "beautiful" relative to the container(s) and the sibling components:
//   • center / edges           — Figma's alignment guides (via [snapAxis], highest priority);
//   • equal spacing            — the moving box centered between two flanking siblings so the
//                                gap on each side is equal (Figma's distribution bars);
//   • equal margin             — centered between a sibling and the container wall on the other side;
//   • match gap                — the gap to a neighbour duplicates an existing gap between two
//                                other siblings (Figma's "keep the rhythm" snapping);
//   • golden ratio             — a box line landing on a container's 0.382 / 0.618 section line;
//   • simple proportions       — thirds (1/3, 2/3) and quarters (1/4, 3/4) of a container.
//
// Pure and Compose-free. Resolves each axis independently: it picks the single nearest
// candidate within [threshold], breaking ties by a fixed priority (alignment > equal spacing >
// match gap > equal margin > golden ratio > proportion), and returns the per-axis snap delta
// plus the overlay guides and spacing bars to draw. `halves` is intentionally omitted from
// proportions: a box centered on a container is already the container-center *alignment* candidate.

/**
 * The kind of "beautiful" relationship a winning anchor expresses; drives overlay styling.
 * [EqualSpacing], [EqualMargin] and [MatchGap] are the "equal distances" family — all drawn as
 * green spacing bars rather than a guide line.
 */
enum class AnchorKind {
    Alignment,      // edge/center aligned to a container or sibling
    EqualSpacing,   // centered between two flanking siblings (equal gap each side)
    EqualMargin,    // equal gap to a sibling and to the container wall on the opposite side
    MatchGap,       // gap to a neighbour matches an existing gap between two other siblings
    GoldenRatio,    // a box line on a container's 0.382 / 0.618 section line
    Proportion,     // a box line on a container's 1/3, 2/3, 1/4 or 3/4 line
}

/** A guide line to draw for a winning anchor, tagged with its [kind] and an optional [label] ("φ", "1/3", …). */
data class AnchorGuide(val line: LineSegment, val kind: AnchorKind, val label: String? = null)

/** An equal-spacing measurement bar: the gap [segment] in document space and its [gap] length in doc units. */
data class SpacingBar(val segment: LineSegment, val gap: Double)

/** Result of [computeAnchors]: per-axis snap correction plus the overlay guides / spacing bars to draw. */
data class AnchorResult(
    val dx: Double,
    val dy: Double,
    val guides: List<AnchorGuide>,
    val spacing: List<SpacingBar> = emptyList(),
)

/** Golden-section fractions of a container interior: 0.382 = 1/φ², 0.618 = 1/φ (they sum to 1). */
private val GoldenFractions = listOf(0.3819660112501051, 0.6180339887498949)

/**
 * Figma-style magnetic anchoring for a free-move drag. [moved] is the dragged selection's
 * proposed union bounds (already displaced by the raw drag) in document coords. [containers]
 * are the boxes whose interior yields center/edge *and* golden/proportion anchors — the
 * immediate parent frame plus any ancestors up to the root, so a deeply nested node can still
 * anchor to an outer container. [siblings] are the co-resident components: they contribute
 * edge/center alignment and are the only boxes considered for equal-spacing distribution.
 * [threshold] is the snap radius in DOCUMENT units (the caller converts a fixed screen-px
 * radius via `/ zoom`, so the feel is constant on screen).
 *
 * Snaps X and Y independently to the single nearest anchor within [threshold]; returns the
 * delta to add to [moved] plus the guides / spacing bars. Pure and deterministic. Non-positive
 * [threshold] or nothing in range → a zero result.
 */
fun computeAnchors(
    moved: BoundsBox,
    containers: List<BoundsBox>,
    siblings: List<BoundsBox>,
    threshold: Double,
): AnchorResult {
    if (threshold <= 0.0) return AnchorResult(0.0, 0.0, emptyList())
    val alignBoxes = containers + siblings

    val alignX = snapAxis(
        sourceLines = listOf(moved.x to false, moved.centerX to true, moved.right to false),
        targets = alignBoxes,
        targetLinesOf = { listOf(it.x to false, it.centerX to true, it.right to false) },
        threshold = threshold,
    )
    val alignY = snapAxis(
        sourceLines = listOf(moved.y to false, moved.centerY to true, moved.bottom to false),
        targets = alignBoxes,
        targetLinesOf = { listOf(it.y to false, it.centerY to true, it.bottom to false) },
        threshold = threshold,
    )
    val xWinner = chooseAxisWinner(AxisX, alignX, bestBeautyAnchor(AxisX, moved, containers, siblings, threshold))
    val yWinner = chooseAxisWinner(AxisY, alignY, bestBeautyAnchor(AxisY, moved, containers, siblings, threshold))

    val dx = xWinner?.delta ?: 0.0
    val dy = yWinner?.delta ?: 0.0
    val snapped = moved.translate(dx, dy)

    val guides = ArrayList<AnchorGuide>()
    val spacing = ArrayList<SpacingBar>()
    xWinner?.let { buildAxisOverlay(it, alongX = true, snapped, guides, spacing) }
    yWinner?.let { buildAxisOverlay(it, alongX = false, snapped, guides, spacing) }
    return AnchorResult(dx, dy, guides, spacing)
}

/** One axis of a [BoundsBox] as accessors, so the beautiful-anchor math is written once and applied to X and Y. */
private class BoxAxis(
    val lo: (BoundsBox) -> Double,
    val hi: (BoundsBox) -> Double,
    val center: (BoundsBox) -> Double,
    val crossLo: (BoundsBox) -> Double,
    val crossHi: (BoundsBox) -> Double,
)

private val AxisX = BoxAxis({ it.x }, { it.right }, { it.centerX }, { it.y }, { it.bottom })
private val AxisY = BoxAxis({ it.y }, { it.bottom }, { it.centerY }, { it.x }, { it.right })

/**
 * How to draw the green bars for an "equal distances" anchor. [lowBoundary] / [highBoundary]
 * are edges the moved box's own edges are measured against (drawn relative to the box's final
 * position). [refLo] / [refHi] is an optional fixed reference gap (the existing sibling gap a
 * [AnchorKind.MatchGap] snap duplicates), drawn where it actually is, not tied to the box.
 */
private data class SpacingPlan(
    val lowBoundary: Double? = null,
    val highBoundary: Double? = null,
    val refLo: Double? = null,
    val refHi: Double? = null,
)

/** A candidate golden/proportion/equal-distance anchor on one axis, before comparing against alignment. */
private data class BeautyAnchor(
    val delta: Double,
    val target: Double,
    val kind: AnchorKind,
    val rank: Int,
    val label: String? = null,
    val crossLo: Double? = null,   // container cross extent for the guide line; null for the equal-distance family (bars instead)
    val crossHi: Double? = null,
    val plan: SpacingPlan? = null, // the spacing bars to draw (equal-distance family only)
)

/** The single axis winner across both alignment (via [snapAxis]) and the beautiful anchors, normalized for overlay building. */
private data class AxisWinner(
    val delta: Double,
    val target: Double,
    val kind: AnchorKind,
    val label: String?,
    val crossLo: Double?,
    val crossHi: Double?,
    val plan: SpacingPlan? = null,
)

private fun rangesOverlap(aLo: Double, aHi: Double, bLo: Double, bHi: Double): Boolean = aLo < bHi && bLo < aHi

/**
 * Best golden/proportion/equal-distance anchor on [axis] within [threshold], or null. Golden and
 * proportion lines come from every [containers] interior and match whichever of the moved box's
 * lines (near edge / center / far edge) is closest. The equal-distance family uses [siblings]
 * that overlap the moved box on the perpendicular axis: centering it between two of them (equal
 * spacing) or between one and the container wall (equal margin), or duplicating an existing gap
 * between two other siblings (match gap). Ties break by [BeautyAnchor.rank] (equal spacing 3 <
 * match gap 4 < equal margin 5 < golden 6 < proportion 7).
 */
private fun bestBeautyAnchor(
    axis: BoxAxis,
    moved: BoundsBox,
    containers: List<BoundsBox>,
    siblings: List<BoundsBox>,
    threshold: Double,
): BeautyAnchor? {
    val mLo = axis.lo(moved)
    val mCenter = axis.center(moved)
    val mHi = axis.hi(moved)
    val mSize = mHi - mLo
    val sources = listOf(mLo, mCenter, mHi)
    val candidates = ArrayList<BeautyAnchor>()

    containers.forEach { container ->
        val cLo = axis.lo(container)
        val span = axis.hi(container) - cLo
        if (span <= 0.0) return@forEach
        val crossLo = axis.crossLo(container)
        val crossHi = axis.crossHi(container)
        fun consider(fraction: Double, kind: AnchorKind, rank: Int, label: String) {
            val target = cLo + span * fraction
            val nearest = sources.map { target - it }.minByOrNull { abs(it) } ?: return
            if (abs(nearest) <= threshold) {
                candidates += BeautyAnchor(nearest, target, kind, rank, label, crossLo, crossHi)
            }
        }
        GoldenFractions.forEach { consider(it, AnchorKind.GoldenRatio, 6, "φ") }
        consider(1.0 / 3.0, AnchorKind.Proportion, 7, "1/3")
        consider(2.0 / 3.0, AnchorKind.Proportion, 7, "2/3")
        consider(0.25, AnchorKind.Proportion, 7, "1/4")
        consider(0.75, AnchorKind.Proportion, 7, "3/4")
    }

    // Equal-distance family: only siblings that overlap the moved box on the perpendicular
    // axis are "in the same row/column" and can form a real gap to balance or match.
    val flankable = siblings.filter { rangesOverlap(axis.crossLo(moved), axis.crossHi(moved), axis.crossLo(it), axis.crossHi(it)) }
    val lowSibling = flankable.filter { axis.center(it) < mCenter }.maxByOrNull { axis.center(it) }
    val highSibling = flankable.filter { axis.center(it) > mCenter }.minByOrNull { axis.center(it) }
    val wall = containers.firstOrNull() // the immediate container provides the "wall" for equal margins

    // (A) Center the box between the nearest boundary on each side, where a boundary is a
    //     flanking sibling's inner edge or the container wall. Two siblings → equal spacing;
    //     one sibling + a wall → equal margin. (Two walls would just be the container center,
    //     which is already an alignment candidate — so require at least one sibling.)
    run {
        val lowEdge = lowSibling?.let { axis.hi(it) } ?: wall?.let { axis.lo(it) }
        val highEdge = highSibling?.let { axis.lo(it) } ?: wall?.let { axis.hi(it) }
        val hasSibling = lowSibling != null || highSibling != null
        if (lowEdge != null && highEdge != null && hasSibling && highEdge - lowEdge > mSize) {
            val target = (lowEdge + highEdge) / 2.0
            val delta = target - mCenter
            if (abs(delta) <= threshold) {
                val bothSiblings = lowSibling != null && highSibling != null
                val kind = if (bothSiblings) AnchorKind.EqualSpacing else AnchorKind.EqualMargin
                val rank = if (bothSiblings) 3 else 5
                candidates += BeautyAnchor(delta, target, kind, rank, plan = SpacingPlan(lowBoundary = lowEdge, highBoundary = highEdge))
            }
        }
    }

    // (B) Match an existing sibling-to-sibling gap on either side of the box (duplication
    //     snapping): drag the box a distance G from a neighbour where G already occurs between
    //     two other siblings. Snaps the box's leading edge (low side) or trailing edge (high side).
    val existingGaps = existingGaps(axis, flankable)
    existingGaps.forEach { (gap, refLo, refHi) ->
        lowSibling?.let { neighbour ->
            val edge = axis.hi(neighbour)
            val delta = (edge + gap) - mLo
            if (abs(delta) <= threshold) {
                candidates += BeautyAnchor(delta, edge + gap, AnchorKind.MatchGap, 4, plan = SpacingPlan(lowBoundary = edge, refLo = refLo, refHi = refHi))
            }
        }
        highSibling?.let { neighbour ->
            val edge = axis.lo(neighbour)
            val delta = (edge - gap) - mHi
            if (abs(delta) <= threshold) {
                candidates += BeautyAnchor(delta, edge - gap, AnchorKind.MatchGap, 4, plan = SpacingPlan(highBoundary = edge, refLo = refLo, refHi = refHi))
            }
        }
    }

    return candidates.minWithOrNull(compareBy({ abs(it.delta) }, { it.rank }))
}

/** Distinct positive gaps between consecutive [flankable] siblings (sorted along [axis]), each with the inner edges that bound it. */
private fun existingGaps(axis: BoxAxis, flankable: List<BoundsBox>): List<Triple<Double, Double, Double>> {
    if (flankable.size < 2) return emptyList()
    val sorted = flankable.sortedBy { axis.lo(it) }
    val gaps = ArrayList<Triple<Double, Double, Double>>()
    for (i in 0 until sorted.size - 1) {
        val lo = axis.hi(sorted[i])
        val hi = axis.lo(sorted[i + 1])
        if (hi - lo > 0.0) gaps += Triple(hi - lo, lo, hi)
    }
    return gaps.distinctBy { kotlin.math.round(it.first * 100.0) / 100.0 }
}

/** Picks the axis winner: real-geometry [align]ment wins ties over the [beauty] anchors (its higher priority). */
private fun chooseAxisWinner(axis: BoxAxis, align: AxisSnap?, beauty: BeautyAnchor?): AxisWinner? {
    val alignWinner = align?.let {
        AxisWinner(it.delta, it.aligned, AnchorKind.Alignment, null, axis.crossLo(it.target), axis.crossHi(it.target))
    }
    val beautyWinner = beauty?.let {
        AxisWinner(it.delta, it.target, it.kind, it.label, it.crossLo, it.crossHi, it.plan)
    }
    return when {
        alignWinner == null -> beautyWinner
        beautyWinner == null -> alignWinner
        abs(alignWinner.delta) <= abs(beautyWinner.delta) -> alignWinner
        else -> beautyWinner
    }
}

/**
 * Appends the overlay for one axis [winner] to [guides] / [spacing]. [alongX] true means the X
 * axis won, drawing a vertical guide (or horizontal spacing bars); false means the Y axis won.
 * [snapped] is [moved] after both axes' deltas are applied, so cross-axis spans use the final
 * position.
 */
private fun buildAxisOverlay(
    winner: AxisWinner,
    alongX: Boolean,
    snapped: BoundsBox,
    guides: MutableList<AnchorGuide>,
    spacing: MutableList<SpacingBar>,
) {
    val plan = winner.plan
    if (plan != null) {
        if (alongX) {
            val y = snapped.centerY
            plan.lowBoundary?.let { spacing += SpacingBar(LineSegment(it, y, snapped.x, y), snapped.x - it) }
            plan.highBoundary?.let { spacing += SpacingBar(LineSegment(snapped.right, y, it, y), it - snapped.right) }
            if (plan.refLo != null && plan.refHi != null) spacing += SpacingBar(LineSegment(plan.refLo, y, plan.refHi, y), plan.refHi - plan.refLo)
        } else {
            val x = snapped.centerX
            plan.lowBoundary?.let { spacing += SpacingBar(LineSegment(x, it, x, snapped.y), snapped.y - it) }
            plan.highBoundary?.let { spacing += SpacingBar(LineSegment(x, snapped.bottom, x, it), it - snapped.bottom) }
            if (plan.refLo != null && plan.refHi != null) spacing += SpacingBar(LineSegment(x, plan.refLo, x, plan.refHi), plan.refHi - plan.refLo)
        }
        return
    }
    val crossLo = winner.crossLo ?: return
    val crossHi = winner.crossHi ?: return
    val line = if (alongX) {
        LineSegment(winner.target, minOf(snapped.y, crossLo), winner.target, maxOf(snapped.bottom, crossHi))
    } else {
        LineSegment(minOf(snapped.x, crossLo), winner.target, maxOf(snapped.right, crossHi), winner.target)
    }
    guides += AnchorGuide(line, winner.kind, winner.label)
}
