package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Authored vias that sit INSIDE a node body (stale after the node moved or grew — the
 * house-inspection ER reference project has three) must not make the route cut straight
 * through the node: the router pushes such a via out to the node's inflated boundary
 * and the legs route around as usual.
 */
class ViaObstacleHealingTest {

    /** True when any segment of [route] passes through the interior of node [id]. */
    private fun cutsThrough(graph: DiagramGraph, route: RoutedEdge, id: String): Boolean {
        val bounds = graph.nodeById(DiagramNodeId(id))!!.bounds
        return route.points.zipWithNext().any { (a, b) ->
            if (abs(a.x - b.x) < 1e-6) {
                a.x > bounds.left + 1e-6 && a.x < bounds.right - 1e-6 &&
                    min(a.y, b.y) < bounds.bottom - 1e-6 && max(a.y, b.y) > bounds.top + 1e-6
            } else if (abs(a.y - b.y) < 1e-6) {
                a.y > bounds.top + 1e-6 && a.y < bounds.bottom - 1e-6 &&
                    min(a.x, b.x) < bounds.right - 1e-6 && max(a.x, b.x) > bounds.left + 1e-6
            } else {
                false
            }
        }
    }

    @Test
    fun viaInsideAForeignNodeDoesNotCutThroughIt() {
        // Mirrors the ER file's appliance_extends_method: a via corner sits inside a
        // bystander table the corridor was authored before.
        val graph = diagramGraph {
            val src = node("src", x = 700.0, y = 880.0, width = 200.0, height = 56.0)
            val dst = node("dst", x = 300.0, y = 500.0, width = 130.0, height = 130.0)
            node("bystander", x = 920.0, y = 560.0, width = 520.0, height = 154.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(src),
                target = DiagramEndpoint.FloatingAnchor(dst),
                routing = DiagramRoutingStyle.ORTHOGONAL,
                waypoints = listOf(DiagramPoint(950.0, 908.0), DiagramPoint(950.0, 660.0)),
            )
        }

        val route = routeEdge(graph, graph.edges.single())

        assertTrue(!cutsThrough(graph, route, "bystander"), "route must not cut the bystander: ${route.points}")
    }

    @Test
    fun viaCorridorSegmentThroughANodeIsRoutedAround() {
        // Mirrors inspection_has_chimneys: the second via lands inside a table and the
        // corridor between the two vias used to slice straight through it.
        val graph = diagramGraph {
            val src = node("src", x = 1250.0, y = 1100.0, width = 200.0, height = 80.0)
            val dst = node("dst", x = 900.0, y = 2400.0, width = 200.0, height = 150.0)
            node("table", x = 1150.0, y = 1700.0, width = 720.0, height = 334.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(src),
                target = DiagramEndpoint.FloatingAnchor(dst),
                routing = DiagramRoutingStyle.ORTHOGONAL,
                waypoints = listOf(
                    DiagramPoint(1210.0, 1176.0),
                    DiagramPoint(1210.0, 1980.0),
                    DiagramPoint(1000.0, 1980.0),
                ),
            )
        }

        val route = routeEdge(graph, graph.edges.single())

        assertTrue(!cutsThrough(graph, route, "table"), "route must not cut the table: ${route.points}")
    }

    @Test
    fun viaOutsideEveryNodeStaysExact() {
        // A legitimate corridor via (in free space) keeps its authored position.
        val graph = diagramGraph {
            val src = node("src", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val dst = node("dst", x = 400.0, y = 400.0, width = 100.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(src),
                target = DiagramEndpoint.FloatingAnchor(dst),
                routing = DiagramRoutingStyle.ORTHOGONAL,
                waypoints = listOf(DiagramPoint(250.0, 200.0)),
            )
        }

        val route = routeEdge(graph, graph.edges.single())

        // The via may be simplified into a longer straight segment; the route must still
        // pass through the authored point exactly.
        val passesThrough = route.points.zipWithNext().any { (a, b) ->
            (abs(a.x - b.x) < 1e-6 && abs(a.x - 250.0) < 1e-6 && 200.0 in min(a.y, b.y)..max(a.y, b.y)) ||
                (abs(a.y - b.y) < 1e-6 && abs(a.y - 200.0) < 1e-6 && 250.0 in min(a.x, b.x)..max(a.x, b.x))
        }
        assertTrue(passesThrough, "free-space via must stay on the route: ${route.points}")
    }
}
