package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterIntersection
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.hypot

/**
 * The connection points offered while wiring an edge to [this] node: its declared [ports]
 * plus the draw.io-style connection-point grid (mid-sides, side quarter-points and corners;
 * see [DiagramPort.connectionPointGrid]) not already covered by a declared one. These are a
 * render + hit-test convenience (draw.io's green connection crosses) — the grid ones are
 * NOT stored on the node until an edge actually pins to one (then it is materialized so the
 * fixed attachment resolves and round-trips).
 */
fun DiagramNode.connectionPorts(): List<DiagramPort> {
    val declaredIds = ports.map { it.id }.toSet()
    return ports + DiagramPort.connectionPointGrid().filter { it.id !in declaredIds }
}

/**
 * What a dragged edge end would attach to at a given pointer position. [snapPoint] is where
 * the live preview end should sit — the exact connection point for [Port], the perimeter
 * crossing for [Floating], or the raw pointer for [Free]. This is the green-cross (fixed) vs
 * blue-outline (floating) distinction draw.io teaches wordlessly.
 */
sealed interface ConnectTarget {
    val snapPoint: DiagramPoint

    /** Pin to a specific connection point of [nodeId]; the edge always leaves/enters here. */
    data class Port(
        val nodeId: DiagramNodeId,
        val port: DiagramPort,
        override val snapPoint: DiagramPoint,
    ) : ConnectTarget

    /** Attach floating to [nodeId]; the edge auto-picks a side. [snapPoint] is on the perimeter. */
    data class Floating(
        val nodeId: DiagramNodeId,
        override val snapPoint: DiagramPoint,
    ) : ConnectTarget

    /** No node under the pointer; the end is a free point at the pointer. */
    data class Free(
        override val snapPoint: DiagramPoint,
    ) : ConnectTarget
}

/**
 * Resolves what a dragged edge end lands on at [pointer], coming from [from] (the source
 * anchor, used to pick the floating perimeter crossing). Picks the topmost visible, unlocked
 * node containing [pointer], other than [excludeNodeId]; snaps to that node's nearest
 * connection point when within [portSnapRadius] (a fixed pin), otherwise attaches floating on
 * its perimeter; with no node under the pointer, a free point. List order is z-order.
 */
fun DiagramGraph.resolveConnectTarget(
    from: DiagramPoint,
    pointer: DiagramPoint,
    excludeNodeId: DiagramNodeId? = null,
    portSnapRadius: Double = 10.0,
): ConnectTarget {
    val node = nodes.asReversed().firstOrNull {
        it.visible && !it.locked && it.id != excludeNodeId && it.bounds.contains(pointer)
    } ?: return ConnectTarget.Free(pointer)

    val nearest = node.connectionPorts()
        .map { port -> port to node.portPosition(port) }
        .minByOrNull { (_, position) -> hypot(position.x - pointer.x, position.y - pointer.y) }
    if (nearest != null && hypot(nearest.second.x - pointer.x, nearest.second.y - pointer.y) <= portSnapRadius) {
        return ConnectTarget.Port(node.id, nearest.first, nearest.second)
    }
    return ConnectTarget.Floating(node.id, perimeterIntersection(node, from))
}
