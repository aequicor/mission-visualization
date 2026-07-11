package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.geometry.length
import io.aequicor.visualization.subsystems.diagrams.geometry.minus
import io.aequicor.visualization.subsystems.diagrams.geometry.plus
import io.aequicor.visualization.subsystems.diagrams.geometry.times
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathBuilder
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.diagramPath
import kotlin.math.abs

/**
 * Builds the final line path of a routed edge — **without arrowheads** (the `arrows`
 * package derives markers separately; the line is expected to be shortened there by
 * the arrowhead inset when needed).
 *
 * - [DiagramStyle.cornerStyle] rounds polyline turns (SHARP = none, ROUNDED = small
 *   quad corners, CURVED = larger ones); route endpoints are never moved.
 * - [lineJumps] inserts ARC/GAP/SHARP jumps where this edge's segments cross segments
 *   of [otherEdges] (crossings with itself are ignored; curve routes never jump).
 * - CURVED routes render as a smooth spline through the route points.
 */
fun routedEdgeToPath(
    routed: RoutedEdge,
    style: DiagramStyle = DiagramStyle.Default,
    lineJumps: LineJumpStyle = LineJumpStyle.NONE,
    otherEdges: List<RoutedEdge> = emptyList(),
    jumpSize: Double = 6.0,
    cornerRadius: Double = 8.0,
    curvedCornerRadius: Double = 16.0,
): DiagramPath {
    if (routed.isCurve) return smoothSplinePath(routed.points)
    val points = routed.points
    val baseRadius = when (style.cornerStyle) {
        DiagramCornerStyle.SHARP -> 0.0
        DiagramCornerStyle.ROUNDED -> cornerRadius
        DiagramCornerStyle.CURVED -> curvedCornerRadius
    }
    val radii = cornerRadii(points, baseRadius)
    val jumps = if (lineJumps == LineJumpStyle.NONE) {
        List(points.size - 1) { emptyList() }
    } else {
        segmentJumpDistances(routed, otherEdges, jumpSize, radii)
    }
    return diagramPath {
        moveTo(points.first())
        for (index in 0 until points.size - 1) {
            val start = points[index]
            val end = points[index + 1]
            val segment = end - start
            val segmentLength = segment.length()
            if (segmentLength < GEOMETRY_EPSILON) continue
            val direction = segment * (1.0 / segmentLength)
            for (jumpCenter in jumps[index]) {
                val before = start + direction * (jumpCenter - jumpSize)
                val after = start + direction * (jumpCenter + jumpSize)
                lineTo(before)
                emitJump(lineJumps, before, after, direction, jumpSize)
            }
            val endInset = radii[index + 1]
            lineTo(end - direction * endInset)
            if (endInset > GEOMETRY_EPSILON) {
                val next = points[index + 2]
                val outgoing = next - end
                val outgoingLength = outgoing.length()
                if (outgoingLength > GEOMETRY_EPSILON) {
                    val outDirection = outgoing * (1.0 / outgoingLength)
                    val cornerEnd = end + outDirection * endInset
                    quadTo(end.x, end.y, cornerEnd.x, cornerEnd.y)
                }
            }
        }
    }
}

private fun DiagramPathBuilder.emitJump(
    kind: LineJumpStyle,
    before: DiagramPoint,
    after: DiagramPoint,
    direction: DiagramPoint,
    jumpSize: Double,
) {
    when (kind) {
        LineJumpStyle.NONE -> lineTo(after)

        LineJumpStyle.ARC -> arcTo(
            radiusX = jumpSize,
            radiusY = jumpSize,
            sweep = true,
            endX = after.x,
            endY = after.y,
        )

        LineJumpStyle.GAP -> moveTo(after)

        LineJumpStyle.SHARP -> {
            // Peak perpendicular to the travel direction (deterministic side).
            val normal = DiagramPoint(direction.y, -direction.x)
            val center = DiagramPoint((before.x + after.x) / 2.0, (before.y + after.y) / 2.0)
            val apex = center + normal * jumpSize
            lineTo(apex)
            lineTo(after)
        }
    }
}

/** Corner radius per vertex: 0 at the ends, clamped to half of each adjacent segment inside. */
private fun cornerRadii(points: List<DiagramPoint>, baseRadius: Double): DoubleArray {
    val radii = DoubleArray(points.size)
    if (baseRadius < GEOMETRY_EPSILON) return radii
    for (index in 1 until points.size - 1) {
        val incoming = (points[index] - points[index - 1]).length()
        val outgoing = (points[index + 1] - points[index]).length()
        radii[index] = minOf(baseRadius, incoming / 2.0, outgoing / 2.0).coerceAtLeast(0.0)
    }
    return radii
}

/**
 * For each segment of [routed], distances (from the segment start) of jump centers over
 * crossings with [otherEdges], sorted ascending, filtered so jumps stay clear of the
 * segment ends, rounded corner zones, and each other.
 */
private fun segmentJumpDistances(
    routed: RoutedEdge,
    otherEdges: List<RoutedEdge>,
    jumpSize: Double,
    radii: DoubleArray,
): List<List<Double>> {
    val points = routed.points
    val result = mutableListOf<List<Double>>()
    for (index in 0 until points.size - 1) {
        val start = points[index]
        val end = points[index + 1]
        val segment = end - start
        val segmentLength = segment.length()
        if (segmentLength < GEOMETRY_EPSILON) {
            result += emptyList<Double>()
            continue
        }
        val crossings = mutableListOf<Double>()
        for (other in otherEdges) {
            if (other.edgeId == routed.edgeId) continue
            for ((otherStart, otherEnd) in other.points.zipWithNext()) {
                val t = segmentCrossingParameter(start, end, otherStart, otherEnd) ?: continue
                crossings += t * segmentLength
            }
        }
        crossings.sort()
        val minCenter = radii[index] + jumpSize
        val maxCenter = segmentLength - radii[index + 1] - jumpSize
        val accepted = mutableListOf<Double>()
        for (center in crossings) {
            if (center < minCenter || center > maxCenter) continue
            if (accepted.isNotEmpty() && center - accepted.last() < jumpSize * 2.0) continue
            accepted += center
        }
        result += accepted
    }
    return result
}

/**
 * Parameter `t` on segment `a1..a2` of a proper interior crossing with `b1..b2`,
 * or `null` when the segments don't cross (parallel/collinear counts as no crossing).
 */
private fun segmentCrossingParameter(
    a1: DiagramPoint,
    a2: DiagramPoint,
    b1: DiagramPoint,
    b2: DiagramPoint,
): Double? {
    val r = a2 - a1
    val s = b2 - b1
    val denominator = r.x * s.y - r.y * s.x
    if (abs(denominator) < GEOMETRY_EPSILON) return null
    val delta = b1 - a1
    val t = (delta.x * s.y - delta.y * s.x) / denominator
    val u = (delta.x * r.y - delta.y * r.x) / denominator
    val interiorMargin = 1e-3
    if (t <= interiorMargin || t >= 1.0 - interiorMargin) return null
    if (u < 0.0 || u > 1.0) return null
    return t
}

/** Smooth Catmull-Rom-style cubic spline through the route points. */
private fun smoothSplinePath(points: List<DiagramPoint>): DiagramPath = diagramPath {
    moveTo(points.first())
    if (points.size == 2) {
        lineTo(points[1])
        return@diagramPath
    }
    for (index in 0 until points.size - 1) {
        val previous = points[maxOf(index - 1, 0)]
        val current = points[index]
        val next = points[index + 1]
        val afterNext = points[minOf(index + 2, points.size - 1)]
        val tangentCurrent = (next - previous) * 0.5
        val tangentNext = (afterNext - current) * 0.5
        val control1 = current + tangentCurrent * (1.0 / 3.0)
        val control2 = next - tangentNext * (1.0 / 3.0)
        cubicTo(control1.x, control1.y, control2.x, control2.y, next.x, next.y)
    }
}
