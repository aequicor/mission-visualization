package io.aequicor.visualization.subsystems.diagrams.path

import kotlin.math.abs
import kotlin.math.round

/**
 * Neutral, renderer-independent path format for the diagrams subsystem.
 *
 * Deliberately autonomous (no dependency on `:subsystems:figures`): the diagrams core is a
 * pure leaf module. Coordinates are [Double]; adapters (Compose, SVG) narrow as needed.
 */

/** A point in diagram document coordinates. */
data class DiagramPoint(
    val x: Double,
    val y: Double,
) {
    companion object {
        val Zero: DiagramPoint = DiagramPoint(0.0, 0.0)
    }
}

/** A width/height pair. */
data class DiagramSize(
    val width: Double,
    val height: Double,
)

/** An axis-aligned rectangle: origin + size. */
data class DiagramRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val left: Double get() = x
    val top: Double get() = y
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0
    val center: DiagramPoint get() = DiagramPoint(centerX, centerY)

    fun contains(point: DiagramPoint): Boolean =
        point.x >= left && point.x <= right && point.y >= top && point.y <= bottom

    fun intersects(other: DiagramRect): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top
}

/** A single path drawing command. */
sealed interface DiagramPathSegment {
    data class MoveTo(val point: DiagramPoint) : DiagramPathSegment

    data class LineTo(val point: DiagramPoint) : DiagramPathSegment

    data class QuadTo(
        val control: DiagramPoint,
        val end: DiagramPoint,
    ) : DiagramPathSegment

    data class CubicTo(
        val control1: DiagramPoint,
        val control2: DiagramPoint,
        val end: DiagramPoint,
    ) : DiagramPathSegment

    /** SVG-style elliptical arc (`A rx ry rotation largeArc sweep x y`). */
    data class ArcTo(
        val radiusX: Double,
        val radiusY: Double,
        val rotationDegrees: Double,
        val largeArc: Boolean,
        val sweep: Boolean,
        val end: DiagramPoint,
    ) : DiagramPathSegment

    data object Close : DiagramPathSegment
}

/** An immutable sequence of path segments (node outline, edge route, arrowhead, ...). */
data class DiagramPath(
    val segments: List<DiagramPathSegment>,
) {
    val isEmpty: Boolean get() = segments.isEmpty()

    companion object {
        val Empty: DiagramPath = DiagramPath(emptyList())
    }
}

/** Builder DSL: `diagramPath { moveTo(0.0, 0.0); lineTo(10.0, 0.0); close() }`. */
fun diagramPath(build: DiagramPathBuilder.() -> Unit): DiagramPath =
    DiagramPathBuilder().apply(build).build()

class DiagramPathBuilder internal constructor() {
    private val segments = mutableListOf<DiagramPathSegment>()

    fun moveTo(x: Double, y: Double) {
        segments += DiagramPathSegment.MoveTo(DiagramPoint(x, y))
    }

    fun moveTo(point: DiagramPoint) {
        segments += DiagramPathSegment.MoveTo(point)
    }

    fun lineTo(x: Double, y: Double) {
        segments += DiagramPathSegment.LineTo(DiagramPoint(x, y))
    }

    fun lineTo(point: DiagramPoint) {
        segments += DiagramPathSegment.LineTo(point)
    }

    fun quadTo(controlX: Double, controlY: Double, endX: Double, endY: Double) {
        segments += DiagramPathSegment.QuadTo(
            control = DiagramPoint(controlX, controlY),
            end = DiagramPoint(endX, endY),
        )
    }

    fun cubicTo(
        control1X: Double,
        control1Y: Double,
        control2X: Double,
        control2Y: Double,
        endX: Double,
        endY: Double,
    ) {
        segments += DiagramPathSegment.CubicTo(
            control1 = DiagramPoint(control1X, control1Y),
            control2 = DiagramPoint(control2X, control2Y),
            end = DiagramPoint(endX, endY),
        )
    }

    fun arcTo(
        radiusX: Double,
        radiusY: Double,
        rotationDegrees: Double = 0.0,
        largeArc: Boolean = false,
        sweep: Boolean = false,
        endX: Double,
        endY: Double,
    ) {
        segments += DiagramPathSegment.ArcTo(
            radiusX = radiusX,
            radiusY = radiusY,
            rotationDegrees = rotationDegrees,
            largeArc = largeArc,
            sweep = sweep,
            end = DiagramPoint(endX, endY),
        )
    }

    fun close() {
        segments += DiagramPathSegment.Close
    }

    internal fun build(): DiagramPath = DiagramPath(segments.toList())
}

/** Serializes the path to SVG path data (`d` attribute) with absolute commands. */
fun DiagramPath.toSvgPathData(): String = segments.joinToString(" ") { segment ->
    when (segment) {
        is DiagramPathSegment.MoveTo ->
            "M ${segment.point.x.toSvgNumber()} ${segment.point.y.toSvgNumber()}"

        is DiagramPathSegment.LineTo ->
            "L ${segment.point.x.toSvgNumber()} ${segment.point.y.toSvgNumber()}"

        is DiagramPathSegment.QuadTo ->
            "Q ${segment.control.x.toSvgNumber()} ${segment.control.y.toSvgNumber()} " +
                "${segment.end.x.toSvgNumber()} ${segment.end.y.toSvgNumber()}"

        is DiagramPathSegment.CubicTo ->
            "C ${segment.control1.x.toSvgNumber()} ${segment.control1.y.toSvgNumber()} " +
                "${segment.control2.x.toSvgNumber()} ${segment.control2.y.toSvgNumber()} " +
                "${segment.end.x.toSvgNumber()} ${segment.end.y.toSvgNumber()}"

        is DiagramPathSegment.ArcTo ->
            "A ${segment.radiusX.toSvgNumber()} ${segment.radiusY.toSvgNumber()} " +
                "${segment.rotationDegrees.toSvgNumber()} " +
                "${if (segment.largeArc) 1 else 0} ${if (segment.sweep) 1 else 0} " +
                "${segment.end.x.toSvgNumber()} ${segment.end.y.toSvgNumber()}"

        DiagramPathSegment.Close -> "Z"
    }
}

/** Formats a coordinate with up to four decimals and no scientific notation. */
internal fun Double.toSvgNumber(): String {
    val scaled = round(this * 10_000.0).toLong()
    if (scaled == 0L) return "0"
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val whole = magnitude / 10_000
    val fraction = (magnitude % 10_000).toString().padStart(4, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}
