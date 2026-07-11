package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The single adapter from the diagrams' neutral path format to a Compose [Path]
 * (mirrors `PathGeometry.toComposePath` in `:subsystems:figures-compose`).
 *
 * SVG-style [DiagramPathSegment.ArcTo] segments are lowered to cubic Béziers
 * (Compose paths have no endpoint-parameterized elliptical arcs).
 */
fun DiagramPath.toComposePath(): Path {
    val path = Path()
    var current = DiagramPoint.Zero
    var subpathStart = DiagramPoint.Zero
    segments.forEach { segment ->
        when (segment) {
            is DiagramPathSegment.MoveTo -> {
                path.moveTo(segment.point.x.toFloat(), segment.point.y.toFloat())
                current = segment.point
                subpathStart = segment.point
            }

            is DiagramPathSegment.LineTo -> {
                path.lineTo(segment.point.x.toFloat(), segment.point.y.toFloat())
                current = segment.point
            }

            is DiagramPathSegment.QuadTo -> {
                path.quadraticTo(
                    segment.control.x.toFloat(),
                    segment.control.y.toFloat(),
                    segment.end.x.toFloat(),
                    segment.end.y.toFloat(),
                )
                current = segment.end
            }

            is DiagramPathSegment.CubicTo -> {
                path.cubicTo(
                    segment.control1.x.toFloat(),
                    segment.control1.y.toFloat(),
                    segment.control2.x.toFloat(),
                    segment.control2.y.toFloat(),
                    segment.end.x.toFloat(),
                    segment.end.y.toFloat(),
                )
                current = segment.end
            }

            is DiagramPathSegment.ArcTo -> {
                appendSvgArc(path, current, segment)
                current = segment.end
            }

            DiagramPathSegment.Close -> {
                path.close()
                current = subpathStart
            }
        }
    }
    return path
}

/** Maps a packed diagram color (`0xAARRGGBB`) to a Compose [Color]. */
fun DiagramColor.toComposeColor(): Color = Color(argb.toUInt().toInt())

/**
 * Dash [PathEffect] for a stroke pattern, scaled by the stroke width.
 * Returns `null` for [DiagramStrokePattern.SOLID] with a zero [phase].
 */
fun strokePatternEffect(
    pattern: DiagramStrokePattern,
    strokeWidth: Float,
    phase: Float = 0f,
): PathEffect? {
    val intervals = strokePatternIntervals(pattern, strokeWidth) ?: return null
    return PathEffect.dashPathEffect(intervals, phase)
}

/** Dash intervals for a pattern; `null` when the stroke is solid. */
internal fun strokePatternIntervals(
    pattern: DiagramStrokePattern,
    strokeWidth: Float,
): FloatArray? {
    val unit = strokeWidth.coerceAtLeast(1f)
    return when (pattern) {
        DiagramStrokePattern.SOLID -> null
        DiagramStrokePattern.DASHED -> floatArrayOf(unit * 6f, unit * 4f)
        DiagramStrokePattern.DOTTED -> floatArrayOf(unit * 1.2f, unit * 2.6f)
    }
}

/**
 * Appends an SVG endpoint-parameterized elliptical arc as cubic Béziers
 * (standard endpoint → center conversion, then ≤90° slices).
 */
private fun appendSvgArc(path: Path, from: DiagramPoint, arc: DiagramPathSegment.ArcTo) {
    val x1 = from.x
    val y1 = from.y
    val x2 = arc.end.x
    val y2 = arc.end.y
    var rx = abs(arc.radiusX)
    var ry = abs(arc.radiusY)
    if (rx < ARC_EPSILON || ry < ARC_EPSILON ||
        (abs(x1 - x2) < ARC_EPSILON && abs(y1 - y2) < ARC_EPSILON)
    ) {
        path.lineTo(x2.toFloat(), y2.toFloat())
        return
    }

    val phi = arc.rotationDegrees * PI / 180.0
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)

    // Step 1: midpoint-relative coordinates in the ellipse frame.
    val dx = (x1 - x2) / 2.0
    val dy = (y1 - y2) / 2.0
    val x1p = cosPhi * dx + sinPhi * dy
    val y1p = -sinPhi * dx + cosPhi * dy

    // Step 2: scale radii up if they cannot span the endpoints.
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1.0) {
        val scale = sqrt(lambda)
        rx *= scale
        ry *= scale
    }

    // Step 3: center in the ellipse frame.
    val rx2 = rx * rx
    val ry2 = ry * ry
    val numerator = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p
    val denominator = rx2 * y1p * y1p + ry2 * x1p * x1p
    val coefficient = if (denominator < ARC_EPSILON) {
        0.0
    } else {
        val sign = if (arc.largeArc != arc.sweep) 1.0 else -1.0
        sign * sqrt((numerator / denominator).coerceAtLeast(0.0))
    }
    val cxp = coefficient * rx * y1p / ry
    val cyp = -coefficient * ry * x1p / rx

    // Step 4: center in document coordinates.
    val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0

    // Step 5: start angle and sweep.
    val startAngle = vectorAngle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var sweepAngle = vectorAngle(
        (x1p - cxp) / rx,
        (y1p - cyp) / ry,
        (-x1p - cxp) / rx,
        (-y1p - cyp) / ry,
    )
    if (!arc.sweep && sweepAngle > 0.0) sweepAngle -= 2.0 * PI
    if (arc.sweep && sweepAngle < 0.0) sweepAngle += 2.0 * PI

    // Step 6: cubic approximation in slices of at most 90°.
    val sliceCount = ceil(abs(sweepAngle) / (PI / 2.0)).toInt().coerceAtLeast(1)
    val delta = sweepAngle / sliceCount
    val alpha = 4.0 / 3.0 * tan(delta / 4.0)

    fun pointAt(theta: Double): DiagramPoint {
        val ex = rx * cos(theta)
        val ey = ry * sin(theta)
        return DiagramPoint(
            cx + cosPhi * ex - sinPhi * ey,
            cy + sinPhi * ex + cosPhi * ey,
        )
    }

    fun derivativeAt(theta: Double): DiagramPoint {
        val ex = -rx * sin(theta)
        val ey = ry * cos(theta)
        return DiagramPoint(
            cosPhi * ex - sinPhi * ey,
            sinPhi * ex + cosPhi * ey,
        )
    }

    var theta = startAngle
    var start = pointAt(theta)
    repeat(sliceCount) {
        val next = theta + delta
        val end = pointAt(next)
        val d1 = derivativeAt(theta)
        val d2 = derivativeAt(next)
        path.cubicTo(
            (start.x + alpha * d1.x).toFloat(),
            (start.y + alpha * d1.y).toFloat(),
            (end.x - alpha * d2.x).toFloat(),
            (end.y - alpha * d2.y).toFloat(),
            end.x.toFloat(),
            end.y.toFloat(),
        )
        theta = next
        start = end
    }
}

private fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
    val dot = ux * vx + uy * vy
    val lengths = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
    if (lengths < ARC_EPSILON) return 0.0
    var angle = kotlin.math.acos((dot / lengths).coerceIn(-1.0, 1.0))
    if (ux * vy - uy * vx < 0.0) angle = -angle
    return angle
}

private const val ARC_EPSILON = 1e-9
