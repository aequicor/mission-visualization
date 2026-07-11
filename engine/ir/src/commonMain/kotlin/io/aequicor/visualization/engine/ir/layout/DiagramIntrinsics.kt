package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph

/**
 * Intrinsic size of a `diagram` leaf in document layout: the extent of the graph's
 * bounding box from the diagram origin `(0,0)` — graph coordinates are local to the
 * node's box with the origin at its top-left, so the box must reach the content's
 * max right/bottom edge. Covers node bounds, manual edge waypoints, and free edge
 * endpoints. Returns `null` for an empty graph (the layout falls back to the
 * authored w/h).
 */
internal fun diagramIntrinsicWidth(graph: DiagramGraph): Double? =
    diagramExtent(graph) { x, _ -> x }

internal fun diagramIntrinsicHeight(graph: DiagramGraph): Double? =
    diagramExtent(graph) { _, y -> y }

private fun diagramExtent(
    graph: DiagramGraph,
    pick: (x: Double, y: Double) -> Double,
): Double? {
    val values = buildList {
        graph.nodes.forEach { node ->
            add(pick(node.x + node.width, node.y + node.height))
        }
        graph.edges.forEach { edge ->
            edge.waypoints.forEach { add(pick(it.x, it.y)) }
            (edge.source as? DiagramEndpoint.FreePoint)?.let { add(pick(it.x, it.y)) }
            (edge.target as? DiagramEndpoint.FreePoint)?.let { add(pick(it.x, it.y)) }
        }
    }
    return values.maxOrNull()?.coerceAtLeast(0.0)
}
