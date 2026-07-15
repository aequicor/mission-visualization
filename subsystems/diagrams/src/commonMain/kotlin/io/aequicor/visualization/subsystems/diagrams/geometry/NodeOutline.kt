package io.aequicor.visualization.subsystems.diagrams.geometry

import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.path.contains
import io.aequicor.visualization.subsystems.diagrams.path.diagramPath
import io.aequicor.visualization.subsystems.diagrams.path.intersects
import io.aequicor.visualization.subsystems.diagrams.path.rayIntersection

/**
 * The node's closed outline in document coordinates: the contour a floating edge
 * attaches to and the base contour for hit-testing. Decorations (UML class
 * compartments, actor stick figure, package tab, ...) are renderer concerns —
 * their attachment contour stays the payload's perimeter shape.
 *
 * Ignores [DiagramNode.rotation] (like [DiagramNode.bounds]).
 */
fun DiagramNode.outlinePath(): DiagramPath {
    val b = bounds
    return when (val payload = payload) {
        is DiagramNodePayload.BasicShape -> when (payload.shape) {
            DiagramShapeKind.RECTANGLE, DiagramShapeKind.TEXT -> rectanglePath(b)
            DiagramShapeKind.ROUNDED_RECTANGLE -> roundedRectanglePath(b, defaultCornerRadius(b))
            DiagramShapeKind.ELLIPSE, DiagramShapeKind.CLOUD -> ellipsePath(b)
            DiagramShapeKind.RHOMBUS -> rhombusPath(b)
            DiagramShapeKind.TRIANGLE -> trianglePath(b)
            DiagramShapeKind.HEXAGON -> hexagonPath(b)
            DiagramShapeKind.PARALLELOGRAM -> parallelogramPath(b)
            DiagramShapeKind.TRAPEZOID -> trapezoidPath(b)
            DiagramShapeKind.CYLINDER -> cylinderPath(b)
        }

        is DiagramNodePayload.FlowchartNode -> when (payload.kind) {
            FlowchartNodeKind.PROCESS -> rectanglePath(b)
            FlowchartNodeKind.DECISION -> rhombusPath(b)
            FlowchartNodeKind.INPUT_OUTPUT -> parallelogramPath(b)
            FlowchartNodeKind.TERMINATOR -> roundedRectanglePath(b, b.height / 2.0)
        }

        is DiagramNodePayload.BpmnNode -> when (payload.kind) {
            BpmnNodeKind.TASK -> roundedRectanglePath(b, defaultCornerRadius(b))
            BpmnNodeKind.EVENT -> ellipsePath(b)
            BpmnNodeKind.GATEWAY -> rhombusPath(b)
        }

        is UmlStateNode -> when (payload.kind) {
            UmlStateKind.INITIAL, UmlStateKind.FINAL -> ellipsePath(b)
            else -> roundedRectanglePath(b, defaultCornerRadius(b))
        }

        is UmlActivityNode -> when (payload.kind) {
            UmlActivityKind.START, UmlActivityKind.END -> ellipsePath(b)
            UmlActivityKind.DECISION -> rhombusPath(b)
            UmlActivityKind.ACTION -> roundedRectanglePath(b, defaultCornerRadius(b))
            UmlActivityKind.FORK, UmlActivityKind.JOIN -> rectanglePath(b)
        }

        is UmlUseCaseNode -> ellipsePath(b)

        is UmlNoteNode -> notePath(b)

        else -> rectanglePath(b)
    }
}

/** True when [point] lies in the node's rendered body or within [outlineTolerance] of it. */
fun DiagramNode.containsPoint(point: DiagramPoint, outlineTolerance: Double = 0.0): Boolean {
    val tolerance = outlineTolerance.coerceAtLeast(0.0)
    if (point.x < bounds.left - tolerance || point.x > bounds.right + tolerance ||
        point.y < bounds.top - tolerance || point.y > bounds.bottom + tolerance
    ) {
        return false
    }
    return outlinePath().contains(point, tolerance)
}

/** True when the node's rendered body intersects [rect], not merely its bounding box. */
fun DiagramNode.intersectsOutline(rect: DiagramRect): Boolean =
    bounds.intersects(rect) && outlinePath().intersects(rect)

/** Point where a center-originating ray towards [towards] reaches the rendered outline. */
fun DiagramNode.outlineIntersection(towards: DiagramPoint): DiagramPoint? =
    outlinePath().rayIntersection(bounds.center, towards)

/**
 * Intersection with the requested logical [side] while preserving [coordinate] on that side's
 * axis. Orthogonal connectors therefore keep a horizontal/vertical exit while touching the
 * rendered contour instead of the bounding rectangle.
 */
fun DiagramNode.outlineSideIntersection(side: DiagramNodeSide, coordinate: Double): DiagramPoint? {
    val reach = maxOf(width, height, 1.0) + 1.0
    val (origin, towards) = when (side) {
        DiagramNodeSide.TOP -> {
            val x = coordinate.coerceIn(bounds.left, bounds.right)
            DiagramPoint(x, bounds.centerY) to DiagramPoint(x, bounds.top - reach)
        }

        DiagramNodeSide.RIGHT -> {
            val y = coordinate.coerceIn(bounds.top, bounds.bottom)
            DiagramPoint(bounds.centerX, y) to DiagramPoint(bounds.right + reach, y)
        }

        DiagramNodeSide.BOTTOM -> {
            val x = coordinate.coerceIn(bounds.left, bounds.right)
            DiagramPoint(x, bounds.centerY) to DiagramPoint(x, bounds.bottom + reach)
        }

        DiagramNodeSide.LEFT -> {
            val y = coordinate.coerceIn(bounds.top, bounds.bottom)
            DiagramPoint(bounds.centerX, y) to DiagramPoint(bounds.left - reach, y)
        }
    }
    return outlinePath().rayIntersection(origin, towards)
}

/** Eight resize positions on the visible contour, ordered clockwise from top-left. */
fun DiagramNode.outlineResizeHandlePoints(): List<DiagramPoint> {
    val b = bounds
    val directions = listOf(
        DiagramPoint(b.left, b.top),
        DiagramPoint(b.centerX, b.top),
        DiagramPoint(b.right, b.top),
        DiagramPoint(b.right, b.centerY),
        DiagramPoint(b.right, b.bottom),
        DiagramPoint(b.centerX, b.bottom),
        DiagramPoint(b.left, b.bottom),
        DiagramPoint(b.left, b.centerY),
    )
    return directions.map { direction -> outlineIntersection(direction) ?: direction }
}

private fun defaultCornerRadius(bounds: DiagramRect): Double =
    minOf(12.0, bounds.width / 2.0, bounds.height / 2.0)

private fun rectanglePath(b: DiagramRect): DiagramPath = diagramPath {
    moveTo(b.left, b.top)
    lineTo(b.right, b.top)
    lineTo(b.right, b.bottom)
    lineTo(b.left, b.bottom)
    close()
}

private fun roundedRectanglePath(b: DiagramRect, radius: Double): DiagramPath {
    val r = minOf(radius, b.width / 2.0, b.height / 2.0).coerceAtLeast(0.0)
    if (r < GEOMETRY_EPSILON) return rectanglePath(b)
    return diagramPath {
        moveTo(b.left + r, b.top)
        lineTo(b.right - r, b.top)
        arcTo(radiusX = r, radiusY = r, sweep = true, endX = b.right, endY = b.top + r)
        lineTo(b.right, b.bottom - r)
        arcTo(radiusX = r, radiusY = r, sweep = true, endX = b.right - r, endY = b.bottom)
        lineTo(b.left + r, b.bottom)
        arcTo(radiusX = r, radiusY = r, sweep = true, endX = b.left, endY = b.bottom - r)
        lineTo(b.left, b.top + r)
        arcTo(radiusX = r, radiusY = r, sweep = true, endX = b.left + r, endY = b.top)
        close()
    }
}

private fun ellipsePath(b: DiagramRect): DiagramPath {
    val rx = b.width / 2.0
    val ry = b.height / 2.0
    return diagramPath {
        moveTo(b.left, b.centerY)
        arcTo(radiusX = rx, radiusY = ry, sweep = true, endX = b.right, endY = b.centerY)
        arcTo(radiusX = rx, radiusY = ry, sweep = true, endX = b.left, endY = b.centerY)
        close()
    }
}

private fun rhombusPath(b: DiagramRect): DiagramPath = diagramPath {
    moveTo(b.centerX, b.top)
    lineTo(b.right, b.centerY)
    lineTo(b.centerX, b.bottom)
    lineTo(b.left, b.centerY)
    close()
}

private fun trianglePath(b: DiagramRect): DiagramPath = diagramPath {
    moveTo(b.centerX, b.top)
    lineTo(b.right, b.bottom)
    lineTo(b.left, b.bottom)
    close()
}

private fun hexagonPath(b: DiagramRect): DiagramPath {
    val inset = b.width * 0.25
    return diagramPath {
        moveTo(b.left + inset, b.top)
        lineTo(b.right - inset, b.top)
        lineTo(b.right, b.centerY)
        lineTo(b.right - inset, b.bottom)
        lineTo(b.left + inset, b.bottom)
        lineTo(b.left, b.centerY)
        close()
    }
}

private fun parallelogramPath(b: DiagramRect): DiagramPath {
    val skew = b.width * 0.2
    return diagramPath {
        moveTo(b.left + skew, b.top)
        lineTo(b.right, b.top)
        lineTo(b.right - skew, b.bottom)
        lineTo(b.left, b.bottom)
        close()
    }
}

private fun trapezoidPath(b: DiagramRect): DiagramPath {
    val inset = b.width * 0.2
    return diagramPath {
        moveTo(b.left + inset, b.top)
        lineTo(b.right - inset, b.top)
        lineTo(b.right, b.bottom)
        lineTo(b.left, b.bottom)
        close()
    }
}

private fun cylinderPath(b: DiagramRect): DiagramPath {
    val rx = b.width / 2.0
    val ry = minOf(b.height * 0.15, b.height / 2.0)
    return diagramPath {
        moveTo(b.left, b.top + ry)
        arcTo(radiusX = rx, radiusY = ry, sweep = true, endX = b.right, endY = b.top + ry)
        lineTo(b.right, b.bottom - ry)
        arcTo(radiusX = rx, radiusY = ry, sweep = true, endX = b.left, endY = b.bottom - ry)
        close()
    }
}

private fun notePath(b: DiagramRect): DiagramPath {
    val fold = minOf(12.0, b.width / 2.0, b.height / 2.0)
    return diagramPath {
        moveTo(b.left, b.top)
        lineTo(b.right - fold, b.top)
        lineTo(b.right, b.top + fold)
        lineTo(b.right, b.bottom)
        lineTo(b.left, b.bottom)
        close()
    }
}
