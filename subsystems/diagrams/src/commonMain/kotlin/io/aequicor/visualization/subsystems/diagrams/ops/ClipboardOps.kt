package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.withEdge
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Inserts copies of clipboard [nodes] and [edges] into the graph (paste / duplicate):
 * every node is re-identified via [nodeIds] and offset by [offsetX]/[offsetY]; an edge
 * is kept only when every node it attaches to is part of the paste (its endpoints are
 * remapped to the copies) — a dangling edge would rewire the ORIGINAL neighbors. Free
 * endpoints and waypoints shift with the nodes. Copies whose new id is already taken
 * are dropped rather than replacing existing elements, so a stale mint never corrupts
 * the graph.
 *
 * The clipboard entries are snapshots: a node's copy is self-contained (ports, payload,
 * labels ride along), so pasting works even after the originals were edited or deleted.
 */
public fun DiagramGraph.pasteElements(
    nodes: List<DiagramNode>,
    edges: List<DiagramEdge>,
    nodeIds: Map<DiagramNodeId, DiagramNodeId>,
    edgeIds: Map<DiagramEdgeId, DiagramEdgeId>,
    offsetX: Double = PASTE_OFFSET,
    offsetY: Double = PASTE_OFFSET,
): DiagramGraph {
    var result = this
    for (node in nodes) {
        val newId = nodeIds[node.id] ?: continue
        if (result.nodeById(newId) != null) continue
        result = result.withNode(node.copy(id = newId, x = node.x + offsetX, y = node.y + offsetY))
    }
    for (edge in edges) {
        val newId = edgeIds[edge.id] ?: continue
        if (result.edgeById(newId) != null) continue
        val source = edge.source.remapForPaste(nodeIds, offsetX, offsetY) ?: continue
        val target = edge.target.remapForPaste(nodeIds, offsetX, offsetY) ?: continue
        result = result.withEdge(
            edge.copy(
                id = newId,
                source = source,
                target = target,
                waypoints = edge.waypoints.map { DiagramPoint(it.x + offsetX, it.y + offsetY) },
            ),
        )
    }
    return result
}

/** Default paste displacement, chosen to read as "a copy", not a doubled border. */
public const val PASTE_OFFSET: Double = 24.0

private fun DiagramEndpoint.remapForPaste(
    nodeIds: Map<DiagramNodeId, DiagramNodeId>,
    offsetX: Double,
    offsetY: Double,
): DiagramEndpoint? = when (this) {
    is DiagramEndpoint.FreePoint -> DiagramEndpoint.FreePoint(x + offsetX, y + offsetY)
    is DiagramEndpoint.FloatingAnchor -> nodeIds[nodeId]?.let { DiagramEndpoint.FloatingAnchor(it) }
    is DiagramEndpoint.FixedPort -> nodeIds[nodeId]?.let { DiagramEndpoint.FixedPort(it, portId) }
}
