package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.model.reversed
import io.aequicor.visualization.subsystems.diagrams.model.updateEdge
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/** Which end of an edge an operation targets. */
enum class DiagramEdgeEnd { SOURCE, TARGET }

/**
 * Reconnects one end of the edge to a new endpoint. No-op if the edge is missing or the
 * endpoint references a node/port that does not exist.
 */
public fun DiagramGraph.reconnectEdge(
    id: DiagramEdgeId,
    end: DiagramEdgeEnd,
    endpoint: DiagramEndpoint,
): DiagramGraph {
    val attached = endpoint.attachedNodeId
    if (attached != null) {
        val node = nodeById(attached) ?: return this
        if (endpoint is DiagramEndpoint.FixedPort && node.portById(endpoint.portId) == null) return this
    }
    return updateEdge(id) { edge ->
        when (end) {
            DiagramEdgeEnd.SOURCE -> edge.copy(source = endpoint)
            DiagramEdgeEnd.TARGET -> edge.copy(target = endpoint)
        }
    }
}

/** Inserts a manual waypoint at [index] (coerced into `0..waypoints.size`). */
public fun DiagramGraph.addWaypoint(
    id: DiagramEdgeId,
    index: Int,
    point: DiagramPoint,
): DiagramGraph = updateEdge(id) { edge ->
    val at = index.coerceIn(0, edge.waypoints.size)
    edge.copy(waypoints = edge.waypoints.take(at) + point + edge.waypoints.drop(at))
}

/** Moves the waypoint at [index]; no-op if the index is out of bounds. */
public fun DiagramGraph.moveWaypoint(
    id: DiagramEdgeId,
    index: Int,
    point: DiagramPoint,
): DiagramGraph = updateEdge(id) { edge ->
    if (index in edge.waypoints.indices) {
        edge.copy(waypoints = edge.waypoints.mapIndexed { i, wp -> if (i == index) point else wp })
    } else {
        edge
    }
}

/** Removes the waypoint at [index]; no-op if the index is out of bounds. */
public fun DiagramGraph.removeWaypoint(id: DiagramEdgeId, index: Int): DiagramGraph =
    updateEdge(id) { edge ->
        if (index in edge.waypoints.indices) {
            edge.copy(waypoints = edge.waypoints.filterIndexed { i, _ -> i != index })
        } else {
            edge
        }
    }

/**
 * Reverses the edge direction: swaps source/target endpoints and arrowheads, reverses
 * waypoints, and swaps SOURCE/TARGET label positions (see [reversed]).
 */
public fun DiagramGraph.reverseEdge(id: DiagramEdgeId): DiagramGraph =
    updateEdge(id) { it.reversed() }

/** Sets the semantic relation of the edge. */
public fun DiagramGraph.setEdgeRelation(id: DiagramEdgeId, relation: DiagramRelation): DiagramGraph =
    updateEdge(id) { it.copy(relation = relation) }

/** Sets the routing style of the edge. */
public fun DiagramGraph.setEdgeRouting(id: DiagramEdgeId, routing: DiagramRoutingStyle): DiagramGraph =
    updateEdge(id) { it.copy(routing = routing) }

/** Sets the edge style. */
public fun DiagramGraph.setEdgeStyle(id: DiagramEdgeId, style: DiagramStyle): DiagramGraph =
    updateEdge(id) { it.copy(style = style) }

/**
 * Sets or removes the label at [position]: `text == null` removes it, otherwise the
 * existing label's text is replaced (keeping its manual offset) or a new label is added.
 */
public fun DiagramGraph.setEdgeLabel(
    id: DiagramEdgeId,
    position: DiagramEdgeLabelPosition,
    text: String?,
    markdown: Boolean = false,
): DiagramGraph = updateEdge(id) { edge ->
    val others = edge.labels.filter { it.position != position }
    val existing = edge.labels.firstOrNull { it.position == position }
    edge.copy(
        labels = when {
            text == null -> others
            existing != null -> others + existing.copy(label = DiagramLabel(text, markdown))
            else -> others + DiagramEdgeLabel(DiagramLabel(text, markdown), position)
        },
    )
}

/** Sets the manual drag offset of the label at [position]; no-op if there is no label there. */
public fun DiagramGraph.moveEdgeLabel(
    id: DiagramEdgeId,
    position: DiagramEdgeLabelPosition,
    offsetX: Double,
    offsetY: Double,
): DiagramGraph = updateEdge(id) { edge ->
    edge.copy(
        labels = edge.labels.map { label ->
            if (label.position == position) label.copy(offsetX = offsetX, offsetY = offsetY) else label
        },
    )
}
