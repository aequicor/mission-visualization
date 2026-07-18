package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.abs

/**
 * The nearest placement for a new `width` x `height` element around `(idealX, idealY)`
 * that does not sit on top of existing content (draw.io keeps stamped shapes off other
 * cells). Searches outward ring by ring on a `step` lattice; candidates keep
 * [clearance] air around obstacles. Containers and swimlanes are not obstacles —
 * dropping an entity onto a group's background is the normal way to fill it. Returns
 * the ideal spot unchanged when it is free or when every ring up to [maxRings] is
 * blocked (a fully packed viewport should not teleport the element off-screen).
 */
fun findFreeDiagramPlacement(
    graph: DiagramGraph,
    width: Double,
    height: Double,
    idealX: Double,
    idealY: Double,
    step: Double = 20.0,
    clearance: Double = 10.0,
    maxRings: Int = 24,
    maxX: Double? = null,
    maxY: Double? = null,
): DiagramPoint {
    val obstacles = graph.nodes.filter { it.visible && it.payload.isPlacementObstacle }
    if (obstacles.isEmpty() || step <= 0.0) return DiagramPoint(idealX, idealY)

    fun isFree(x: Double, y: Double): Boolean {
        if (x < 0.0 || y < 0.0) return false
        if (maxX != null && x > maxX) return false
        if (maxY != null && y > maxY) return false
        val candidate = DiagramRect(x - clearance, y - clearance, width + 2 * clearance, height + 2 * clearance)
        return obstacles.none { it.bounds.intersects(candidate) }
    }

    if (isFree(idealX, idealY)) return DiagramPoint(idealX, idealY)
    for (ring in 1..maxRings) {
        val offsets = buildList {
            for (dx in -ring..ring) {
                for (dy in -ring..ring) {
                    if (maxOf(abs(dx), abs(dy)) == ring) add(dx to dy)
                }
            }
        }.sortedWith(compareBy({ it.first * it.first + it.second * it.second }, { it.second }, { it.first }))
        for ((dx, dy) in offsets) {
            val x = idealX + dx * step
            val y = idealY + dy * step
            if (isFree(x, y)) return DiagramPoint(x, y)
        }
    }
    return DiagramPoint(idealX, idealY)
}

private val DiagramNodePayload.isPlacementObstacle: Boolean
    get() = when (this) {
        is DiagramNodePayload.ContainerNode, is DiagramNodePayload.SwimlaneNode -> false
        else -> true
    }
