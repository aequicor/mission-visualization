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

// --- Inherited (ancestor) rotation --------------------------------------------
//
// The layout engine ignores rotation: every box is axis-aligned in one shared root-local
// coordinate space, and the Compose backend applies each node's rotation about its own center
// at draw time by *nesting* `rotate(pivot)` transforms (root outermost). So a node whose own
// `rotation` is 0 is still drawn rotated when an ancestor (e.g. the root frame) is rotated — its
// content follows, but its layout box does not move. The editor overlays (selection outline,
// handles, rotate affordance) map a node's own box + own rotation to the screen, so under a
// rotated ancestor they float in the pre-rotation position: the root-container rotation bug.
// [effectiveTransform] composes the ancestor rotations so the overlays follow the same transform
// the renderer applies.

/**
 * One ancestor's contribution to a descendant's on-screen placement: a rotation by [degrees]
 * about [center] (the ancestor's own center, in the shared root-local coordinate space the
 * layout engine produces).
 */
data class AncestorRotation(val center: GeoPoint, val degrees: Double)

/**
 * A node's on-screen (visual) placement once every rotated ancestor is composed in.
 *
 * A composition of rigid rotations is itself rigid, so the node's axis-aligned layout box maps to
 * a congruent box: same width/height, its center carried to [box].center, rotated by [rotation]
 * about that center — exactly what feeding [box] and [rotation] to [rotatedCorners] /
 * [rotatedHandlePoints] reproduces. [ownRotation] (the node's own `rotation`, unchanged) is kept
 * separately: a resize must undo the *full* visual [rotation] to read the drag in the box's own
 * axes but re-express the resulting position shift in the shared root-local frame — which differs
 * from the box's own frame only by [ownRotation] — and a rotate gesture edits only [ownRotation].
 */
data class EffectiveTransform(
    val box: BoundsBox,
    val rotation: Double,
    val ownRotation: Double,
)

/**
 * Composes [ancestors] (nearest-first: immediate parent first, root last) onto a node's own
 * axis-aligned [box] and [ownRotation], yielding its on-screen [EffectiveTransform]. The node's
 * own rotation fixes its own center, so only the ancestors move it: each rotates the running
 * center about its own center — matching the renderer's outermost-first `rotate` nesting, where
 * the immediate parent's transform is applied to the node's content first and the root's last.
 */
fun effectiveTransform(
    box: BoundsBox,
    ownRotation: Double,
    ancestors: List<AncestorRotation>,
): EffectiveTransform {
    var center = GeoPoint(box.centerX, box.centerY)
    var rotation = ownRotation
    ancestors.forEach { ancestor ->
        center = rotatePointAroundCenter(center, ancestor.center, ancestor.degrees)
        rotation += ancestor.degrees
    }
    val visualBox = BoundsBox(center.x - box.width / 2.0, center.y - box.height / 2.0, box.width, box.height)
    return EffectiveTransform(box = visualBox, rotation = rotation, ownRotation = ownRotation)
}

/**
 * The net rotation a nested node inherits from its [ancestors] (beyond its own). Only the angle
 * matters for de-rotating a drag *vector* — a composition of rotations about different centers is
 * still a rotation by the sum of the angles — so this is the amount to undo when mapping a
 * document-space move delta back into the node's root-local position frame.
 */
fun ancestorRotationDegrees(ancestors: List<AncestorRotation>): Double = ancestors.sumOf { it.degrees }

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

// --- Move-drag geometry helper -------------------------------------------------

/** Translates the box by ([dx], [dy]); size is unchanged. */
fun BoundsBox.translate(dx: Double, dy: Double): BoundsBox = copy(x = x + dx, y = y + dy)

/**
 * One possible parent during a canvas drag-out reparent.
 *
 * [layoutBounds] are the parent's untransformed root-layout bounds. [visualBox] and
 * [visualRotation] describe the same parent after its ancestor rotations are composed.
 * [childAncestorRotations] is the nearest-first transform chain a child of this parent
 * inherits (this parent first, then its ancestors up to the root).
 */
data class CanvasParentFrame(
    val id: String,
    val layoutBounds: BoundsBox,
    val visualBox: BoundsBox,
    val visualRotation: Double,
    val childAncestorRotations: List<AncestorRotation>,
)

/** Parent and parent-relative geometry for an automatic canvas drag-out reparent. */
data class CanvasReparentPlacement(
    val parentId: String,
    val x: Double,
    val y: Double,
    val rotation: Double,
)

/**
 * Resolves the container a moved node should drop into: the deepest [candidates] entry
 * containing the moved visual center — nesting inward into a sibling/cousin frame just as
 * readily as promoting outward to an ancestor. [candidates] must be deepest-first, exclude
 * the dragged subtree, and contain the screen's root frame; the page itself is never a
 * target because the artboard renders the root frame tree.
 *
 * If the node was dragged beyond every container, the root frame ([rootId]) remains the
 * target so the node stays renderable as root overflow. Returns null when the resolved
 * target is the node's current parent (a plain move, not a reparent). The returned
 * position is expressed in the target parent's layout coordinate system, and the node's
 * own rotation compensates for any rotated ancestors it starts or stops inheriting,
 * preserving its on-canvas appearance.
 */
fun reparentDropPlacement(
    movedVisual: EffectiveTransform,
    candidates: List<CanvasParentFrame>,
    currentParentId: String,
    rootId: String,
): CanvasReparentPlacement? {
    val center = GeoPoint(movedVisual.box.centerX, movedVisual.box.centerY)
    val target = candidates.firstOrNull { it.containsVisual(center) }
        ?: candidates.firstOrNull { it.id == rootId }
        ?: return null
    if (target.id == currentParentId) return null

    var layoutCenter = center
    target.childAncestorRotations.asReversed().forEach { transform ->
        layoutCenter = rotatePointAroundCenter(layoutCenter, transform.center, -transform.degrees)
    }
    val inheritedRotation = target.childAncestorRotations.sumOf { it.degrees }
    return CanvasReparentPlacement(
        parentId = target.id,
        x = layoutCenter.x - movedVisual.box.width / 2.0 - target.layoutBounds.x,
        y = layoutCenter.y - movedVisual.box.height / 2.0 - target.layoutBounds.y,
        rotation = movedVisual.rotation - inheritedRotation,
    )
}

/** Point-in-rotated-rectangle test for a parent's composed visual transform. */
private fun CanvasParentFrame.containsVisual(point: GeoPoint): Boolean {
    val local = rotatePointAroundCenter(
        point = point,
        center = GeoPoint(visualBox.centerX, visualBox.centerY),
        degrees = -visualRotation,
    )
    return local.x >= visualBox.x && local.x <= visualBox.right &&
        local.y >= visualBox.y && local.y <= visualBox.bottom
}
