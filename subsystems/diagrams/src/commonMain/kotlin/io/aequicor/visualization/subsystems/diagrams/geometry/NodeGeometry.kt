package io.aequicor.visualization.subsystems.diagrams.geometry

import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Perimeter family used for floating edge attachment ([perimeterIntersection]).
 *
 * Most payloads (tables, UML class/component/..., containers) attach on their bounding
 * rectangle; round shapes attach on the inscribed ellipse; decision/gateway shapes on
 * the inscribed rhombus. Polygonal basic shapes (triangle/hexagon/...) approximate
 * with the bounding rectangle in v1.
 */
enum class DiagramPerimeterKind { RECTANGLE, ELLIPSE, RHOMBUS }

/** The perimeter family this node's payload attaches floating edges to. */
fun DiagramNode.perimeterKind(): DiagramPerimeterKind = when (val payload = payload) {
    is DiagramNodePayload.BasicShape -> when (payload.shape) {
        DiagramShapeKind.ELLIPSE -> DiagramPerimeterKind.ELLIPSE
        DiagramShapeKind.RHOMBUS -> DiagramPerimeterKind.RHOMBUS
        else -> DiagramPerimeterKind.RECTANGLE
    }

    is DiagramNodePayload.FlowchartNode -> when (payload.kind) {
        FlowchartNodeKind.DECISION -> DiagramPerimeterKind.RHOMBUS
        else -> DiagramPerimeterKind.RECTANGLE
    }

    is DiagramNodePayload.BpmnNode -> when (payload.kind) {
        BpmnNodeKind.EVENT -> DiagramPerimeterKind.ELLIPSE
        BpmnNodeKind.GATEWAY -> DiagramPerimeterKind.RHOMBUS
        else -> DiagramPerimeterKind.RECTANGLE
    }

    is UmlStateNode -> when (payload.kind) {
        UmlStateKind.INITIAL, UmlStateKind.FINAL -> DiagramPerimeterKind.ELLIPSE
        else -> DiagramPerimeterKind.RECTANGLE
    }

    is UmlActivityNode -> when (payload.kind) {
        UmlActivityKind.START, UmlActivityKind.END -> DiagramPerimeterKind.ELLIPSE
        UmlActivityKind.DECISION -> DiagramPerimeterKind.RHOMBUS
        else -> DiagramPerimeterKind.RECTANGLE
    }

    is UmlUseCaseNode -> DiagramPerimeterKind.ELLIPSE

    else -> DiagramPerimeterKind.RECTANGLE
}

/** Absolute document position of a fixed [port] on [node] (alias of [DiagramNode.portPosition]). */
fun anchorPoint(node: DiagramNode, port: DiagramPort): DiagramPoint = node.portPosition(port)

/** Absolute document position of the port with [portId], or `null` if the node has no such port. */
fun anchorPoint(node: DiagramNode, portId: DiagramPortId): DiagramPoint? =
    node.portById(portId)?.let(node::portPosition)

/**
 * The point where a ray from the node center toward [towards] crosses the node's
 * attachment perimeter (see [perimeterKind]). This is where a floating edge end
 * touches the node. Returns the center for degenerate nodes or when [towards]
 * coincides with the center.
 */
fun perimeterIntersection(node: DiagramNode, towards: DiagramPoint): DiagramPoint {
    val center = node.bounds.center
    val dx = towards.x - center.x
    val dy = towards.y - center.y
    val halfWidth = node.width / 2.0
    val halfHeight = node.height / 2.0
    val degenerate = halfWidth < GEOMETRY_EPSILON || halfHeight < GEOMETRY_EPSILON
    if (degenerate || (abs(dx) < GEOMETRY_EPSILON && abs(dy) < GEOMETRY_EPSILON)) return center
    val t = when (node.perimeterKind()) {
        DiagramPerimeterKind.RECTANGLE -> minOf(
            if (abs(dx) < GEOMETRY_EPSILON) Double.MAX_VALUE else halfWidth / abs(dx),
            if (abs(dy) < GEOMETRY_EPSILON) Double.MAX_VALUE else halfHeight / abs(dy),
        )

        DiagramPerimeterKind.ELLIPSE -> {
            val nx = dx / halfWidth
            val ny = dy / halfHeight
            1.0 / sqrt(nx * nx + ny * ny)
        }

        DiagramPerimeterKind.RHOMBUS -> 1.0 / (abs(dx) / halfWidth + abs(dy) / halfHeight)
    }
    return DiagramPoint(center.x + dx * t, center.y + dy * t)
}

/**
 * The bounding-box side a perimeter/anchor [point] belongs to (nearest side wins;
 * deterministic LEFT → RIGHT → TOP → BOTTOM tie-breaking).
 */
fun perimeterSide(node: DiagramNode, point: DiagramPoint): DiagramNodeSide {
    val bounds = node.bounds
    val distances = listOf(
        DiagramNodeSide.LEFT to abs(point.x - bounds.left),
        DiagramNodeSide.RIGHT to abs(point.x - bounds.right),
        DiagramNodeSide.TOP to abs(point.y - bounds.top),
        DiagramNodeSide.BOTTOM to abs(point.y - bounds.bottom),
    )
    return distances.minByOrNull { it.second }?.first ?: DiagramNodeSide.RIGHT
}

/** Unit vector pointing away from the node through this side. */
fun DiagramNodeSide.outwardNormal(): DiagramPoint = when (this) {
    DiagramNodeSide.TOP -> DiagramPoint(0.0, -1.0)
    DiagramNodeSide.RIGHT -> DiagramPoint(1.0, 0.0)
    DiagramNodeSide.BOTTOM -> DiagramPoint(0.0, 1.0)
    DiagramNodeSide.LEFT -> DiagramPoint(-1.0, 0.0)
}

/** Whether an edge leaving through this side moves horizontally first. */
val DiagramNodeSide.exitsHorizontally: Boolean
    get() = this == DiagramNodeSide.LEFT || this == DiagramNodeSide.RIGHT
