package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers separation of edges that used to lie on top of each other: fan-out of edges
 * sharing one fixed port, authored via corners surviving far from the port axis, and
 * nudging of router-chosen legs of waypointed edges.
 */
class EdgeOverlapSeparationTest {

    private fun port(id: String, side: DiagramNodeSide, offset: Double): DiagramPort =
        DiagramPort(DiagramPortId(id), DiagramPortAnchor.SideOffset(side, offset))

    private fun assertOnRoute(route: RoutedEdge, point: DiagramPoint) {
        val onRoute = route.points.zipWithNext().any { (a, b) ->
            val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
            abs(cross) < 1e-6 &&
                point.x >= minOf(a.x, b.x) - 1e-6 && point.x <= maxOf(a.x, b.x) + 1e-6 &&
                point.y >= minOf(a.y, b.y) - 1e-6 && point.y <= maxOf(a.y, b.y) + 1e-6
        }
        assertTrue(onRoute, "route ${route.points} does not pass through $point")
    }

    // --- same-port fan-out --------------------------------------------------------------

    @Test
    fun edgesSharingAFixedPortFanOutAlongTheSide() {
        val graph = diagramGraph {
            val parent = node(
                "parent",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("id", DiagramNodeSide.RIGHT, 0.5)),
            )
            val top = node("top", x = 400.0, y = -160.0, width = 160.0, height = 80.0)
            val bottom = node("bottom", x = 400.0, y = 200.0, width = 160.0, height = 80.0)
            edge(
                "to_top",
                source = DiagramEndpoint.FixedPort(parent, DiagramPortId("id")),
                target = DiagramEndpoint.FloatingAnchor(top),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
            edge(
                "to_bottom",
                source = DiagramEndpoint.FixedPort(parent, DiagramPortId("id")),
                target = DiagramEndpoint.FloatingAnchor(bottom),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val routes = routeAllEdges(graph).associateBy { it.edgeId.value }
        val toTop = routes.getValue("to_top").sourcePoint
        val toBottom = routes.getValue("to_bottom").sourcePoint

        // Both stay on the right side, spread by the fan separation, centered on the port.
        assertTrue(abs(toTop.x - 200.0) < 1e-6, "fan must stay on the port's side, got $toTop")
        assertTrue(abs(toBottom.x - 200.0) < 1e-6, "fan must stay on the port's side, got $toBottom")
        val separation = RoutingOptions.Default.portFanSeparation
        assertTrue(
            abs(toTop.y - toBottom.y) >= separation - 1e-6,
            "same-port anchors must fan out, got $toTop / $toBottom",
        )
        assertEquals(60.0, (toTop.y + toBottom.y) / 2.0, 1e-6)
        // The edge heading up takes the upper slot so the fan does not cross itself.
        assertTrue(toTop.y < toBottom.y, "fan order must follow route headings")
    }

    @Test
    fun aLonePortEdgeKeepsTheExactPortPoint() {
        val graph = diagramGraph {
            val parent = node(
                "parent",
                x = 0.0,
                y = 0.0,
                width = 200.0,
                height = 120.0,
                ports = listOf(port("id", DiagramNodeSide.RIGHT, 0.5)),
            )
            val other = node("other", x = 400.0, y = 0.0, width = 160.0, height = 80.0)
            edge(
                "only",
                source = DiagramEndpoint.FixedPort(parent, DiagramPortId("id")),
                target = DiagramEndpoint.FloatingAnchor(other),
                routing = DiagramRoutingStyle.ENTITY_RELATION,
            )
        }
        val route = routeAllEdges(graph).single()
        assertEquals(DiagramPoint(200.0, 60.0), route.sourcePoint)
    }

    // --- authored vias far from the port axis -------------------------------------------

    @Test
    fun farViaKeepsItsAuthoredCorner() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("out", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 300.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("in", DiagramNodeSide.LEFT, 0.5)),
            )
            edge(
                "e",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("out")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("in")),
                waypoints = listOf(DiagramPoint(150.0, 200.0), DiagramPoint(300.0, 200.0)),
            )
        }
        val route = routeAllEdges(graph).single()
        // Both vias sit far from their port axes (y=30 / y=330) — they are deliberate
        // corners and must stay exact mandatory pass-through points.
        assertOnRoute(route, DiagramPoint(150.0, 200.0))
        assertOnRoute(route, DiagramPoint(300.0, 200.0))
    }

    @Test
    fun staleViaNearThePortAxisIsStillHealed() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("out", DiagramNodeSide.RIGHT, 0.5)),
            )
            val b = node(
                "b",
                x = 400.0,
                y = 300.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(port("in", DiagramNodeSide.LEFT, 0.5)),
            )
            edge(
                "e",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("out")),
                target = DiagramEndpoint.FixedPort(b, DiagramPortId("in")),
                // First via drifted 8 units off the port row after a resize: still healed.
                waypoints = listOf(DiagramPoint(150.0, 38.0), DiagramPoint(150.0, 330.0)),
            )
        }
        val route = routeAllEdges(graph).single()
        assertOnRoute(route, DiagramPoint(150.0, 30.0))
        assertTrue(
            route.points.none { abs(it.y - 38.0) < 1e-6 },
            "stale via row must be healed onto the port row, got ${route.points}",
        )
    }

    // --- nudging of waypointed edges ------------------------------------------------------

    private fun routed(id: String, vararg points: DiagramPoint): RoutedEdge =
        RoutedEdge(DiagramEdgeId(id), DiagramRoutingStyle.ORTHOGONAL, points.toList())

    @Test
    fun routerChosenLegsOfWaypointedEdgesStillSpreadApart() {
        // Two waypointed edges co-run on an interior vertical the router (not the author)
        // picked; their vias sit on other segments, far from the corridor.
        val first = routed(
            "first",
            DiagramPoint(0.0, 0.0),
            DiagramPoint(100.0, 0.0),
            DiagramPoint(100.0, 200.0),
            DiagramPoint(200.0, 200.0),
        )
        val second = routed(
            "second",
            DiagramPoint(0.0, 50.0),
            DiagramPoint(100.0, 50.0),
            DiagramPoint(100.0, 250.0),
            DiagramPoint(200.0, 250.0),
        )
        val nudged = nudgeRoutedEdges(
            routes = listOf(first, second),
            waypointsByEdge = mapOf(
                DiagramEdgeId("first") to listOf(DiagramPoint(170.0, 200.0)),
                DiagramEdgeId("second") to listOf(DiagramPoint(170.0, 250.0)),
            ),
        ).associateBy { it.edgeId.value }
        val firstVertical = nudged.getValue("first").points[1].x
        val secondVertical = nudged.getValue("second").points[1].x
        assertTrue(
            abs(firstVertical - secondVertical) > 1.0,
            "co-running verticals of waypointed edges must spread, got $firstVertical / $secondVertical",
        )
        // The via-carrying last segments still pass through their vias.
        assertOnRoute(nudged.getValue("first"), DiagramPoint(170.0, 200.0))
        assertOnRoute(nudged.getValue("second"), DiagramPoint(170.0, 250.0))
    }

    @Test
    fun viaCarryingSegmentsNeverMove() {
        val pinned = routed(
            "pinned",
            DiagramPoint(0.0, 0.0),
            DiagramPoint(100.0, 0.0),
            DiagramPoint(100.0, 200.0),
            DiagramPoint(200.0, 200.0),
        )
        val free = routed(
            "free",
            DiagramPoint(0.0, 50.0),
            DiagramPoint(100.0, 50.0),
            DiagramPoint(100.0, 250.0),
            DiagramPoint(200.0, 250.0),
        )
        val nudged = nudgeRoutedEdges(
            routes = listOf(pinned, free),
            // The via sits right on the pinned edge's interior vertical.
            waypointsByEdge = mapOf(DiagramEdgeId("pinned") to listOf(DiagramPoint(100.0, 100.0))),
        ).associateBy { it.edgeId.value }
        assertEquals(pinned.points, nudged.getValue("pinned").points)
        assertTrue(
            abs(nudged.getValue("free").points[1].x - 100.0) > 1.0,
            "the movable co-runner must give way instead",
        )
    }
}
