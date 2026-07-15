package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.geometry.containsPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.anchorPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.outlinePath
import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterIntersection
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.rayIntersections
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
    val grid = DiagramPort.connectionPointGrid().filter { it.id !in declaredIds }
    val targets = grid.map(::portPosition)
    val projected = outlinePath().rayIntersections(bounds.center, targets)
    val virtual = grid.mapIndexed { index, port ->
        projectVirtualPortToOutline(port, projected[index] ?: targets[index])
    }
    return ports + virtual
}

/** Keeps the dense virtual grid, but places each generated point on the rendered contour. */
private fun DiagramNode.projectVirtualPortToOutline(port: DiagramPort, projected: DiagramPoint): DiagramPort {
    if (width <= 0.0 || height <= 0.0) return port
    return port.copy(
        anchor = DiagramPortAnchor.RelativePoint(
            x = (projected.x - x) / width,
            y = (projected.y - y) / height,
        ),
    )
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
 * node whose rendered body contains [pointer], other than [excludeNodeId]; snaps to that node's nearest
 * connection point when within [portSnapRadius] (a fixed pin), otherwise attaches floating on
 * its perimeter. When the pointer is just outside every node, connection points keep the same
 * magnetic radius so crossing a node boundary does not drop a pending draw.io-style snap.
 * With no node or connection point under the pointer, returns a free point. List order is
 * z-order.
 */
fun DiagramGraph.resolveConnectTarget(
    from: DiagramPoint,
    pointer: DiagramPoint,
    excludeNodeId: DiagramNodeId? = null,
    portSnapRadius: Double = 10.0,
): ConnectTarget {
    val candidates = nodes.asReversed().filter {
        it.visible && !it.locked && it.id != excludeNodeId
    }

    candidates.firstOrNull { it.containsPoint(pointer) }?.let { node ->
        node.nearestConnectionPort(pointer, portSnapRadius)?.let { (port, position) ->
            return ConnectTarget.Port(node.id, port, position)
        }
        return ConnectTarget.Floating(node.id, perimeterIntersection(node, from))
    }

    candidates.forEach { node ->
        node.nearestConnectionPort(pointer, portSnapRadius)?.let { (port, position) ->
            return ConnectTarget.Port(node.id, port, position)
        }
    }

    return ConnectTarget.Free(pointer)
}

private fun DiagramNode.nearestConnectionPort(
    pointer: DiagramPoint,
    portSnapRadius: Double,
): Pair<DiagramPort, DiagramPoint>? = connectionPorts()
    .map { port -> port to anchorPoint(this, port) }
    .minByOrNull { (_, position) -> hypot(position.x - pointer.x, position.y - pointer.y) }
    ?.takeIf { (_, position) -> hypot(position.x - pointer.x, position.y - pointer.y) <= portSnapRadius }
