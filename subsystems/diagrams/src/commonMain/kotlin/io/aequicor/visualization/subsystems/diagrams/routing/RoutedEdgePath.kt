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
        segmentJumpSpans(routed, otherEdges, jumpSize, radii, otherEndClearance = maxOf(jumpSize, baseRadius))
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
            for (span in jumps[index]) {
                val before = start + direction * (span.center - span.halfWidth)
                val after = start + direction * (span.center + span.halfWidth)
                lineTo(before)
                emitJump(lineJumps, before, after, direction, jumpSize, span.halfWidth)
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
    halfWidth: Double,
) {
    when (kind) {
        LineJumpStyle.NONE -> lineTo(after)

        // radiusX equals half the chord, so a merged multi-crossing span renders as one
        // wide half-ellipse of constant height instead of a chain of touching bumps.
        LineJumpStyle.ARC -> arcTo(
            radiusX = halfWidth,
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

/** A hop over one or more merged crossings: center distance from the segment start. */
private data class JumpSpan(val center: Double, val halfWidth: Double)

/**
 * For each segment of [routed], hop spans over crossings with [otherEdges], sorted
 * ascending and kept clear of the segment ends and rounded corner zones. Crossings
 * whose hops would overlap merge into one wide span (draw.io-style) instead of some
 * of them silently losing their hop.
 */
private fun segmentJumpSpans(
    routed: RoutedEdge,
    otherEdges: List<RoutedEdge>,
    jumpSize: Double,
    radii: DoubleArray,
    otherEndClearance: Double,
): List<List<JumpSpan>> {
    val points = routed.points
    val result = mutableListOf<List<JumpSpan>>()
    for (index in 0 until points.size - 1) {
        val start = points[index]
        val end = points[index + 1]
        val segment = end - start
        val segmentLength = segment.length()
        if (segmentLength < GEOMETRY_EPSILON) {
            result += emptyList<JumpSpan>()
            continue
        }
        val crossings = mutableListOf<Double>()
        for (other in otherEdges) {
            if (other.edgeId == routed.edgeId) continue
            for ((otherStart, otherEnd) in other.points.zipWithNext()) {
                val t = segmentCrossingParameter(start, end, otherStart, otherEnd, otherEndClearance)
                    ?: continue
                crossings += t * segmentLength
            }
        }
        crossings.sort()
        val minCenter = radii[index] + jumpSize
        val maxCenter = segmentLength - radii[index + 1] - jumpSize
        val inWindow = crossings.filter { it in minCenter..maxCenter }
        val spans = mutableListOf<JumpSpan>()
        var clusterFirst = Double.NaN
        var clusterLast = Double.NaN
        fun flush() {
            if (!clusterFirst.isNaN()) {
                spans += JumpSpan(
                    center = (clusterFirst + clusterLast) / 2.0,
                    halfWidth = (clusterLast - clusterFirst) / 2.0 + jumpSize,
                )
            }
        }
        for (center in inWindow) {
            if (clusterFirst.isNaN()) {
                clusterFirst = center
                clusterLast = center
            } else if (center - clusterLast <= jumpSize * 2.0) {
                clusterLast = center
            } else {
                flush()
                clusterFirst = center
                clusterLast = center
            }
        }
        flush()
        result += spans
    }
    return result
}

/**
 * Parameter `t` on segment `a1..a2` of a proper interior crossing with `b1..b2`,
 * or `null` when the segments don't cross (parallel/collinear counts as no crossing).
 *
 * The crossing must also sit at least [otherEndClearance] away from the ends of the
 * other segment: a hop only disambiguates a true transversal crossing. When the other
 * edge terminates on this line (a junction) or turns right next to it, the drawn
 * geometry there is a T-joint or the other edge's rounded corner — hopping over it
 * reads as noise (and the raw polyline the crossing was computed against does not
 * even match what is painted inside the corner zone).
 */
private fun segmentCrossingParameter(
    a1: DiagramPoint,
    a2: DiagramPoint,
    b1: DiagramPoint,
    b2: DiagramPoint,
    otherEndClearance: Double,
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
    val otherLength = s.length()
    val alongOther = u * otherLength
    if (alongOther < otherEndClearance || otherLength - alongOther < otherEndClearance) return null
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
