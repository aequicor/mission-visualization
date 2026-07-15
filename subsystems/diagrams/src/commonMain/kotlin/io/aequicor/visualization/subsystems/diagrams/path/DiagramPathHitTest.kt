package io.aequicor.visualization.subsystems.diagrams.path

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Maximum deviation, in document units, when curves are flattened for hit-testing. */
private const val HIT_FLATTEN_TOLERANCE = 0.25
private const val HIT_EPSILON = 1e-9
private const val MAX_FLATTEN_DEPTH = 18

private data class FlattenedSubpath(
    val points: List<DiagramPoint>,
    val closed: Boolean,
)

/**
 * Whether [point] is inside this path's closed area or within [outlineTolerance] of its contour.
 *
 * The path is flattened without a rendering dependency, so the same geometry drives pointer,
 * hover, connection and export behaviour on every target. Open subpaths contribute only their
 * contour tolerance and never acquire an implicit filled area.
 */
fun DiagramPath.contains(point: DiagramPoint, outlineTolerance: Double = 0.0): Boolean {
    val subpaths = flattenForHitTest()
    val tolerance = outlineTolerance.coerceAtLeast(0.0)
    if (distanceToOutline(subpaths, point) <= max(tolerance, HIT_EPSILON)) return true

    var winding = 0
    subpaths.filter { it.closed }.forEach { subpath ->
        val points = subpath.points
        if (points.size < 3) return@forEach
        for (index in points.indices) {
            val from = points[index]
            val to = points[(index + 1) % points.size]
            val isLeft = (to.x - from.x) * (point.y - from.y) -
                (point.x - from.x) * (to.y - from.y)
            if (from.y <= point.y) {
                if (to.y > point.y && isLeft > 0.0) winding++
            } else if (to.y <= point.y && isLeft < 0.0) {
                winding--
            }
        }
    }
    return winding != 0
}

/** Whether this path's filled area or contour intersects [rect]. */
fun DiagramPath.intersects(rect: DiagramRect): Boolean {
    val subpaths = flattenForHitTest()
    if (subpaths.isEmpty()) return false

    if (subpaths.any { subpath -> subpath.points.any(rect::contains) }) return true

    val corners = listOf(
        DiagramPoint(rect.left, rect.top),
        DiagramPoint(rect.right, rect.top),
        DiagramPoint(rect.right, rect.bottom),
        DiagramPoint(rect.left, rect.bottom),
    )
    if (corners.any { contains(it) }) return true

    val rectEdges = corners.indices.map { index ->
        corners[index] to corners[(index + 1) % corners.size]
    }
    return subpaths.any { subpath ->
        val segmentCount = if (subpath.closed) subpath.points.size else subpath.points.size - 1
        (0 until segmentCount).any { index ->
            val from = subpath.points[index]
            val to = subpath.points[(index + 1) % subpath.points.size]
            rectEdges.any { (rectFrom, rectTo) -> segmentsIntersect(from, to, rectFrom, rectTo) }
        }
    }
}

/**
 * First point where the ray from [origin] through [towards] meets this path's contour.
 * Returns `null` for a zero-length ray or a path it does not cross.
 */
fun DiagramPath.rayIntersection(origin: DiagramPoint, towards: DiagramPoint): DiagramPoint? {
    return rayIntersection(flattenForHitTest(), origin, towards)
}

/** Batch form of [rayIntersection] that flattens the contour only once. */
internal fun DiagramPath.rayIntersections(
    origin: DiagramPoint,
    targets: List<DiagramPoint>,
): List<DiagramPoint?> {
    val subpaths = flattenForHitTest()
    return targets.map { target -> rayIntersection(subpaths, origin, target) }
}

private fun rayIntersection(
    subpaths: List<FlattenedSubpath>,
    origin: DiagramPoint,
    towards: DiagramPoint,
): DiagramPoint? {
    val rayX = towards.x - origin.x
    val rayY = towards.y - origin.y
    if (abs(rayX) <= HIT_EPSILON && abs(rayY) <= HIT_EPSILON) return null

    var bestRayParameter = Double.POSITIVE_INFINITY
    subpaths.forEach { subpath ->
        val segmentCount = if (subpath.closed) subpath.points.size else subpath.points.size - 1
        for (index in 0 until segmentCount) {
            val start = subpath.points[index]
            val end = subpath.points[(index + 1) % subpath.points.size]
            val segmentX = end.x - start.x
            val segmentY = end.y - start.y
            val denominator = cross(rayX, rayY, segmentX, segmentY)
            if (abs(denominator) <= HIT_EPSILON) continue

            val offsetX = start.x - origin.x
            val offsetY = start.y - origin.y
            val rayParameter = cross(offsetX, offsetY, segmentX, segmentY) / denominator
            val segmentParameter = cross(offsetX, offsetY, rayX, rayY) / denominator
            if (rayParameter >= -HIT_EPSILON &&
                segmentParameter >= -HIT_EPSILON && segmentParameter <= 1.0 + HIT_EPSILON &&
                rayParameter < bestRayParameter
            ) {
                bestRayParameter = rayParameter.coerceAtLeast(0.0)
            }
        }
    }
    return bestRayParameter
        .takeIf(Double::isFinite)
        ?.let { parameter -> DiagramPoint(origin.x + rayX * parameter, origin.y + rayY * parameter) }
}

private fun DiagramPath.flattenForHitTest(): List<FlattenedSubpath> {
    val result = mutableListOf<FlattenedSubpath>()
    var points = mutableListOf<DiagramPoint>()
    var current = DiagramPoint.Zero
    var start = DiagramPoint.Zero
    var closed = false

    fun flush() {
        if (points.size >= 2) result += FlattenedSubpath(points.toList(), closed)
        points = mutableListOf()
        closed = false
    }

    fun moveTo(point: DiagramPoint) {
        flush()
        current = point
        start = point
        points += point
    }

    fun lineTo(point: DiagramPoint) {
        if (points.isEmpty()) {
            moveTo(current)
        }
        if (points.last() != point) points += point
        current = point
    }

    segments.forEach { segment ->
        when (segment) {
            is DiagramPathSegment.MoveTo -> moveTo(segment.point)
            is DiagramPathSegment.LineTo -> lineTo(segment.point)
            is DiagramPathSegment.QuadTo -> {
                flattenQuad(current, segment.control, segment.end, HIT_FLATTEN_TOLERANCE, emit = ::lineTo)
            }

            is DiagramPathSegment.CubicTo -> {
                flattenCubic(
                    current,
                    segment.control1,
                    segment.control2,
                    segment.end,
                    HIT_FLATTEN_TOLERANCE,
                    emit = ::lineTo,
                )
            }

            is DiagramPathSegment.ArcTo -> {
                flattenArc(current, segment, HIT_FLATTEN_TOLERANCE, emit = ::lineTo)
            }

            DiagramPathSegment.Close -> {
                closed = true
                current = start
                flush()
            }
        }
    }
    flush()
    return result
}

private fun flattenQuad(
    start: DiagramPoint,
    control: DiagramPoint,
    end: DiagramPoint,
    tolerance: Double,
    depth: Int = 0,
    emit: (DiagramPoint) -> Unit,
) {
    if (depth >= MAX_FLATTEN_DEPTH || pointLineDistance(control, start, end) <= tolerance) {
        emit(end)
        return
    }
    val startControl = midpoint(start, control)
    val controlEnd = midpoint(control, end)
    val middle = midpoint(startControl, controlEnd)
    flattenQuad(start, startControl, middle, tolerance, depth + 1, emit)
    flattenQuad(middle, controlEnd, end, tolerance, depth + 1, emit)
}

private fun flattenCubic(
    start: DiagramPoint,
    control1: DiagramPoint,
    control2: DiagramPoint,
    end: DiagramPoint,
    tolerance: Double,
    depth: Int = 0,
    emit: (DiagramPoint) -> Unit,
) {
    val flatness = pointLineDistance(control1, start, end) + pointLineDistance(control2, start, end)
    if (depth >= MAX_FLATTEN_DEPTH || flatness <= tolerance) {
        emit(end)
        return
    }
    val a = midpoint(start, control1)
    val b = midpoint(control1, control2)
    val c = midpoint(control2, end)
    val d = midpoint(a, b)
    val e = midpoint(b, c)
    val middle = midpoint(d, e)
    flattenCubic(start, a, d, middle, tolerance, depth + 1, emit)
    flattenCubic(middle, e, c, end, tolerance, depth + 1, emit)
}

/** SVG endpoint-parameterized elliptical arc flattened directly to points. */
private fun flattenArc(
    start: DiagramPoint,
    arc: DiagramPathSegment.ArcTo,
    tolerance: Double,
    emit: (DiagramPoint) -> Unit,
) {
    val end = arc.end
    if (start == end) return
    var radiusX = abs(arc.radiusX)
    var radiusY = abs(arc.radiusY)
    if (radiusX <= HIT_EPSILON || radiusY <= HIT_EPSILON) {
        emit(end)
        return
    }

    val phi = arc.rotationDegrees * PI / 180.0
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)
    val deltaX = (start.x - end.x) / 2.0
    val deltaY = (start.y - end.y) / 2.0
    val transformedX = cosPhi * deltaX + sinPhi * deltaY
    val transformedY = -sinPhi * deltaX + cosPhi * deltaY

    val scale = transformedX * transformedX / (radiusX * radiusX) +
        transformedY * transformedY / (radiusY * radiusY)
    if (scale > 1.0) {
        val correction = sqrt(scale)
        radiusX *= correction
        radiusY *= correction
    }

    val radiusX2 = radiusX * radiusX
    val radiusY2 = radiusY * radiusY
    val transformedX2 = transformedX * transformedX
    val transformedY2 = transformedY * transformedY
    val denominator = radiusX2 * transformedY2 + radiusY2 * transformedX2
    val numerator = max(0.0, radiusX2 * radiusY2 - radiusX2 * transformedY2 - radiusY2 * transformedX2)
    var centerScale = if (denominator <= HIT_EPSILON) 0.0 else sqrt(numerator / denominator)
    if (arc.largeArc == arc.sweep) centerScale = -centerScale
    val centerXPrime = centerScale * radiusX * transformedY / radiusY
    val centerYPrime = centerScale * -radiusY * transformedX / radiusX
    val centerX = cosPhi * centerXPrime - sinPhi * centerYPrime + (start.x + end.x) / 2.0
    val centerY = sinPhi * centerXPrime + cosPhi * centerYPrime + (start.y + end.y) / 2.0

    val startVectorX = (transformedX - centerXPrime) / radiusX
    val startVectorY = (transformedY - centerYPrime) / radiusY
    val endVectorX = (-transformedX - centerXPrime) / radiusX
    val endVectorY = (-transformedY - centerYPrime) / radiusY
    val startAngle = atan2(startVectorY, startVectorX)
    var sweepAngle = atan2(
        startVectorX * endVectorY - startVectorY * endVectorX,
        startVectorX * endVectorX + startVectorY * endVectorY,
    )
    if (!arc.sweep && sweepAngle > 0.0) sweepAngle -= 2.0 * PI
    if (arc.sweep && sweepAngle < 0.0) sweepAngle += 2.0 * PI

    val maxRadius = max(radiusX, radiusY)
    val maximumStep = if (maxRadius <= tolerance) {
        PI / 2.0
    } else {
        minOf(PI / 8.0, 2.0 * acos((1.0 - tolerance / maxRadius).coerceIn(-1.0, 1.0)))
    }.coerceAtLeast(PI / 180.0)
    val steps = max(1, ceil(abs(sweepAngle) / maximumStep).toInt())
    for (index in 1..steps) {
        if (index == steps) {
            emit(end)
        } else {
            val angle = startAngle + sweepAngle * index / steps
            val unitX = cos(angle)
            val unitY = sin(angle)
            emit(
                DiagramPoint(
                    x = centerX + radiusX * unitX * cosPhi - radiusY * unitY * sinPhi,
                    y = centerY + radiusX * unitX * sinPhi + radiusY * unitY * cosPhi,
                ),
            )
        }
    }
}

private fun distanceToOutline(subpaths: List<FlattenedSubpath>, point: DiagramPoint): Double {
    var best = Double.POSITIVE_INFINITY
    subpaths.forEach { subpath ->
        val segmentCount = if (subpath.closed) subpath.points.size else subpath.points.size - 1
        for (index in 0 until segmentCount) {
            val distance = pointSegmentDistance(
                point,
                subpath.points[index],
                subpath.points[(index + 1) % subpath.points.size],
            )
            if (distance < best) best = distance
        }
    }
    return best
}

private fun pointSegmentDistance(point: DiagramPoint, start: DiagramPoint, end: DiagramPoint): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared <= HIT_EPSILON) {
        0.0
    } else {
        ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
    }.coerceIn(0.0, 1.0)
    return hypot(point.x - (start.x + t * dx), point.y - (start.y + t * dy))
}

private fun pointLineDistance(point: DiagramPoint, start: DiagramPoint, end: DiagramPoint): Double {
    val length = hypot(end.x - start.x, end.y - start.y)
    if (length <= HIT_EPSILON) return hypot(point.x - start.x, point.y - start.y)
    return abs((end.x - start.x) * (start.y - point.y) - (start.x - point.x) * (end.y - start.y)) / length
}

private fun segmentsIntersect(a: DiagramPoint, b: DiagramPoint, c: DiagramPoint, d: DiagramPoint): Boolean {
    fun orientation(p: DiagramPoint, q: DiagramPoint, r: DiagramPoint): Double =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)

    fun onSegment(point: DiagramPoint, start: DiagramPoint, end: DiagramPoint): Boolean =
        abs(orientation(start, end, point)) <= HIT_EPSILON &&
            point.x >= minOf(start.x, end.x) - HIT_EPSILON &&
            point.x <= maxOf(start.x, end.x) + HIT_EPSILON &&
            point.y >= minOf(start.y, end.y) - HIT_EPSILON &&
            point.y <= maxOf(start.y, end.y) + HIT_EPSILON

    val abc = orientation(a, b, c)
    val abd = orientation(a, b, d)
    val cda = orientation(c, d, a)
    val cdb = orientation(c, d, b)
    if ((abc > HIT_EPSILON && abd < -HIT_EPSILON || abc < -HIT_EPSILON && abd > HIT_EPSILON) &&
        (cda > HIT_EPSILON && cdb < -HIT_EPSILON || cda < -HIT_EPSILON && cdb > HIT_EPSILON)
    ) {
        return true
    }
    return onSegment(c, a, b) || onSegment(d, a, b) || onSegment(a, c, d) || onSegment(b, c, d)
}

private fun midpoint(a: DiagramPoint, b: DiagramPoint): DiagramPoint =
    DiagramPoint((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)

private fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx
