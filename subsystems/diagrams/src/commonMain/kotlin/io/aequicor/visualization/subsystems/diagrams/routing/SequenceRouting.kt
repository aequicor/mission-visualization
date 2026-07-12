package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.model.lifelineTop
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.min

/**
 * Vertical layout for sequence-diagram messages.
 *
 * A generic graph router attaches an inter-lifeline edge to the *midpoint* of each
 * lifeline's perimeter, so every message collapses onto the lifelines' vertical center
 * instead of stepping down the page in declaration order. A sequence diagram is instead
 * read top-to-bottom: each message occupies its own horizontal row, in source order,
 * between the two lifelines' center lines (a self-message is a small right-side loop).
 *
 * [sequenceMessageRoutes] recognizes every edge whose *both* ends attach to a
 * [UmlLifelineNode] (and which has no manual waypoints) and hands back a ready
 * [RoutedEdge] for it, laid out this way. The routing entry points
 * ([routeAllEdges]/[routeAllEdgesLenient]) use these routes verbatim and let the generic
 * router handle everything else. Coordinates are diagram-local, matching the rest of the
 * router.
 */

/** Loop width of a self-message (how far right the arrow bows out). */
private const val SELF_MESSAGE_WIDTH = 34.0

/** Vertical clearance kept above the first and below the last message row. */
private const val ROW_MARGIN = 12.0

/**
 * Routes for every sequence message of [graph], keyed by edge id. Empty when the graph
 * has no lifeline-to-lifeline messages. Pure and non-throwing: it only reads node bounds.
 */
fun sequenceMessageRoutes(graph: DiagramGraph): Map<DiagramEdgeId, RoutedEdge> {
    val lifelines = graph.nodes.mapNotNull { node ->
        (node.payload as? UmlLifelineNode)?.let { node to it }
    }.toMap()
    if (lifelines.isEmpty()) return emptyMap()

    // Messages in declaration order: both ends on lifelines, no manual waypoints.
    val messages = graph.edges.filter { edge ->
        edge.waypoints.isEmpty() &&
            edge.source.lifelineNode(lifelines.keys) != null &&
            edge.target.lifelineNode(lifelines.keys) != null
    }
    if (messages.isEmpty()) return emptyMap()

    // Shared row band: below the tallest head, above the shortest lifeline foot.
    val lifelineNodes = lifelines.keys
    val bandTop = lifelineNodes.maxOf { node ->
        (node.payload as UmlLifelineNode).lifelineTop(node.bounds.top, node.bounds.height)
    } + ROW_MARGIN
    val bandBottom = lifelineNodes.minOf { it.bounds.bottom } - ROW_MARGIN
    val span = (bandBottom - bandTop).coerceAtLeast(0.0)
    val step = span / (messages.size + 1)

    return messages.mapIndexed { index, edge ->
        val y = bandTop + step * (index + 1)
        val sourceNode = edge.source.lifelineNode(lifelineNodes)!!
        val targetNode = edge.target.lifelineNode(lifelineNodes)!!
        edge.id to routeMessage(edge, sourceNode, targetNode, y, step)
    }.toMap()
}

/** The lifeline node this endpoint attaches to, if any (else `null`). */
private fun io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint.lifelineNode(
    lifelines: Set<DiagramNode>,
): DiagramNode? {
    val nodeId = attachedNodeId ?: return null
    return lifelines.firstOrNull { it.id == nodeId }
}

/**
 * One message as a horizontal row at [y] between the two lifelines' center lines, or a
 * small right-side loop when [source]==[target]. [step] bounds a self-loop's height so it
 * never overruns the next row.
 */
private fun routeMessage(
    edge: DiagramEdge,
    source: DiagramNode,
    target: DiagramNode,
    y: Double,
    step: Double,
): RoutedEdge {
    val sourceX = source.bounds.centerX
    if (source.id == target.id) {
        val loopHeight = min(step * 0.6, 22.0)
        val right = sourceX + SELF_MESSAGE_WIDTH
        val points = listOf(
            DiagramPoint(sourceX, y),
            DiagramPoint(right, y),
            DiagramPoint(right, y + loopHeight),
            DiagramPoint(sourceX, y + loopHeight),
        )
        return RoutedEdge(
            edgeId = edge.id,
            routing = DiagramRoutingStyle.STRAIGHT,
            points = points,
            sourceSide = DiagramNodeSide.RIGHT,
            targetSide = DiagramNodeSide.RIGHT,
        )
    }
    val targetX = target.bounds.centerX
    val goingRight = targetX >= sourceX
    return RoutedEdge(
        edgeId = edge.id,
        routing = DiagramRoutingStyle.STRAIGHT,
        points = listOf(DiagramPoint(sourceX, y), DiagramPoint(targetX, y)),
        sourceSide = if (goingRight) DiagramNodeSide.RIGHT else DiagramNodeSide.LEFT,
        targetSide = if (goingRight) DiagramNodeSide.LEFT else DiagramNodeSide.RIGHT,
    )
}
