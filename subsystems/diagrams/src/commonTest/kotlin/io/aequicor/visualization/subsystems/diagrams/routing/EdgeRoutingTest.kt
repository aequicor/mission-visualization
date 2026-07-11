package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeRoutingTest {

    private fun assertPointEquals(expected: DiagramPoint, actual: DiagramPoint) {
        assertTrue(
            abs(expected.x - actual.x) < 1e-6 && abs(expected.y - actual.y) < 1e-6,
            "expected $expected, got $actual",
        )
    }

    private fun assertAxisAligned(points: List<DiagramPoint>) {
        points.zipWithNext().forEach { (a, b) ->
            assertTrue(
                abs(a.x - b.x) < 1e-6 || abs(a.y - b.y) < 1e-6,
                "segment $a -> $b is not axis-aligned",
            )
        }
    }

    private fun twoNodesGraph(routing: DiagramRoutingStyle): DiagramGraph = diagramGraph {
        val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
        val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
        edge("e", from = a, to = b, routing = routing)
    }

    @Test
    fun straightRouteConnectsPerimeters() {
        val graph = twoNodesGraph(DiagramRoutingStyle.STRAIGHT)
        val routed = routeEdge(graph, graph.edges.single())
        assertEquals(2, routed.points.size)
        assertPointEquals(DiagramPoint(100.0, 30.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(300.0, 30.0), routed.targetPoint)
        assertEquals(DiagramNodeSide.RIGHT, routed.sourceSide)
        assertEquals(DiagramNodeSide.LEFT, routed.targetSide)
    }

    @Test
    fun straightRouteHonorsWaypoint() {
        val waypoint = DiagramPoint(200.0, 200.0)
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                routing = DiagramRoutingStyle.STRAIGHT,
                waypoints = listOf(waypoint),
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertTrue(routed.points.any { it.nearly(waypoint) }, "waypoint missing from ${routed.points}")
        // The floating source anchor faces the waypoint, not the target.
        assertTrue(routed.sourcePoint.y > 30.0, "source anchor should lean toward the waypoint")
    }

    @Test
    fun orthogonalRouteAvoidsObstacle() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 80.0, height = 60.0)
            val b = node("b", x = 400.0, y = 0.0, width = 80.0, height = 60.0)
            node("obstacle", x = 200.0, y = -40.0, width = 80.0, height = 140.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ORTHOGONAL)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        assertPointEquals(DiagramPoint(80.0, 30.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(400.0, 30.0), routed.targetPoint)
        // No segment may cross the obstacle interior (200..280 x, -40..100 y).
        routed.points.zipWithNext().forEach { (a, b) ->
            val crosses = if (abs(a.y - b.y) < 1e-6) {
                a.y > -40.0 + 1e-6 && a.y < 100.0 - 1e-6 &&
                    maxOf(a.x, b.x) > 200.0 + 1e-6 && minOf(a.x, b.x) < 280.0 - 1e-6
            } else {
                a.x > 200.0 + 1e-6 && a.x < 280.0 - 1e-6 &&
                    maxOf(a.y, b.y) > -40.0 + 1e-6 && minOf(a.y, b.y) < 100.0 - 1e-6
            }
            assertTrue(!crosses, "segment $a -> $b crosses the obstacle")
        }
        // The route actually detours (more than a straight line).
        assertTrue(routed.points.size > 2)
    }

    @Test
    fun orthogonalRouteIsDeterministic() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 80.0, height = 60.0)
            val b = node("b", x = 400.0, y = 200.0, width = 80.0, height = 60.0)
            node("obstacle", x = 180.0, y = 40.0, width = 100.0, height = 120.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ORTHOGONAL)
        }
        val first = routeEdge(graph, graph.edges.single())
        val second = routeEdge(graph, graph.edges.single())
        assertEquals(first, second)
    }

    @Test
    fun orthogonalRouteHonorsWaypoint() {
        val waypoint = DiagramPoint(240.0, 200.0)
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 80.0, height = 60.0)
            val b = node("b", x = 400.0, y = 0.0, width = 80.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                routing = DiagramRoutingStyle.ORTHOGONAL,
                waypoints = listOf(waypoint),
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        assertTrue(routed.points.any { it.nearly(waypoint) }, "waypoint missing from ${routed.points}")
    }

    @Test
    fun orthogonalRouteFromFixedPortLeavesThroughPortSide() {
        val graph = diagramGraph {
            val a = node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                ports = DiagramPort.standardPorts(),
            )
            val b = node("b", x = 300.0, y = 300.0, width = 100.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FixedPort(a, DiagramPortId("bottom")),
                target = DiagramEndpoint.FloatingAnchor(b),
                routing = DiagramRoutingStyle.ORTHOGONAL,
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertPointEquals(DiagramPoint(50.0, 60.0), routed.sourcePoint)
        assertEquals(DiagramNodeSide.BOTTOM, routed.sourceSide)
        // First segment leaves downward, perpendicular to the bottom side.
        val second = routed.points[1]
        assertTrue(abs(second.x - 50.0) < 1e-6 && second.y > 60.0)
        assertAxisAligned(routed.points)
    }

    @Test
    fun simpleRouteIgnoresObstacles() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 80.0, height = 60.0)
            val b = node("b", x = 400.0, y = 0.0, width = 80.0, height = 60.0)
            node("obstacle", x = 200.0, y = -40.0, width = 80.0, height = 140.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.SIMPLE)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        // Straight through: aligned anchors collapse to a single segment.
        assertEquals(listOf(DiagramPoint(80.0, 30.0), DiagramPoint(400.0, 30.0)), routed.points)
    }

    @Test
    fun simpleRouteSplitsAtMiddleForOppositeSides() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 80.0, height = 60.0)
            val b = node("b", x = 400.0, y = 200.0, width = 80.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.SIMPLE)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        assertPointEquals(DiagramPoint(80.0, 60.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(400.0, 200.0), routed.targetPoint)
        // Z-shape with a vertical middle at the midpoint between the stubs.
        assertEquals(4, routed.points.size)
        assertEquals(routed.points[1].x, routed.points[2].x, 1e-6)
    }

    @Test
    fun isometricSegmentsAreMultiplesOf30Degrees() {
        val graph = diagramGraph {
            node("unused")
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(100.0, 10.0),
                routing = DiagramRoutingStyle.ISOMETRIC,
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertEquals(3, routed.points.size)
        routed.points.zipWithNext().forEach { (a, b) ->
            val degrees = atan2(b.y - a.y, b.x - a.x) * 180.0 / PI
            val remainder = abs(degrees % 30.0)
            assertTrue(
                remainder < 1e-6 || abs(remainder - 30.0) < 1e-6,
                "segment $a -> $b has angle $degrees, not a multiple of 30",
            )
        }
        assertPointEquals(DiagramPoint(0.0, 0.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(100.0, 10.0), routed.targetPoint)
    }

    @Test
    fun isometricRouteHonorsWaypoints() {
        val waypoint = DiagramPoint(60.0, 60.0)
        val graph = diagramGraph {
            node("unused")
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(200.0, 0.0),
                routing = DiagramRoutingStyle.ISOMETRIC,
                waypoints = listOf(waypoint),
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertTrue(routed.points.any { it.nearly(waypoint) })
    }

    @Test
    fun entityRelationRouteUsesHorizontalStubs() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 100.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ENTITY_RELATION)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        assertPointEquals(DiagramPoint(100.0, 30.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(300.0, 130.0), routed.targetPoint)
        // First and last segments run horizontally out of the entity sides.
        val firstSegmentHorizontal = abs(routed.points[0].y - routed.points[1].y) < 1e-6
        val lastSegmentHorizontal =
            abs(routed.points[routed.points.size - 2].y - routed.points.last().y) < 1e-6
        assertTrue(firstSegmentHorizontal && lastSegmentHorizontal)
    }

    @Test
    fun curvedRouteKeepsInterpolationPoints() {
        val waypoint = DiagramPoint(200.0, 150.0)
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                routing = DiagramRoutingStyle.CURVED,
                waypoints = listOf(waypoint),
            )
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertTrue(routed.isCurve)
        assertEquals(3, routed.points.size)
        assertTrue(routed.points.any { it.nearly(waypoint) })
    }

    @Test
    fun routeAllEdgesRoutesEveryEdge() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            val c = node("c", x = 150.0, y = 200.0, width = 100.0, height = 60.0)
            edge("e1", from = a, to = b)
            edge("e2", from = a, to = c, routing = DiagramRoutingStyle.STRAIGHT)
        }
        val routed = routeAllEdges(graph)
        assertEquals(graph.edges.map { it.id }, routed.map { it.edgeId })
    }

    private fun DiagramPoint.nearly(other: DiagramPoint): Boolean =
        abs(x - other.x) < 1e-6 && abs(y - other.y) < 1e-6
}
