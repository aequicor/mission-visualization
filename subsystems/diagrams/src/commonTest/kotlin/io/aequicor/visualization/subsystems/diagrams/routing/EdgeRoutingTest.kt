package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
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
    fun orthogonalFloatingRouteTouchesRhombusOutline() {
        val graph = diagramGraph {
            val source = node("source", x = 0.0, y = 0.0, width = 100.0, height = 40.0)
            val decision = node(
                "decision",
                x = 300.0,
                y = 0.0,
                width = 100.0,
                height = 100.0,
                payload = UmlActivityNode(UmlActivityKind.DECISION, "Condition?"),
            )
            edge("e", from = source, to = decision, routing = DiagramRoutingStyle.ORTHOGONAL)
        }

        val routed = routeEdge(graph, graph.edges.single())
        assertPointEquals(DiagramPoint(330.0, 20.0), routed.targetPoint)
        assertEquals(DiagramNodeSide.LEFT, routed.targetSide)
        assertAxisAligned(routed.points)
    }

    @Test
    fun orthogonalFixedSidePortTouchesRhombusOutline() {
        val portId = DiagramPortId("left-q1")
        val graph = diagramGraph {
            val source = node("source", x = 0.0, y = 0.0, width = 100.0, height = 50.0)
            val decision = node(
                "decision",
                x = 300.0,
                y = 0.0,
                width = 100.0,
                height = 100.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS),
                ports = listOf(DiagramPort(portId, DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT, 0.25))),
            )
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(source),
                target = DiagramEndpoint.FixedPort(decision, portId),
                routing = DiagramRoutingStyle.ORTHOGONAL,
            )
        }

        val routed = routeEdge(graph, graph.edges.single())
        assertPointEquals(DiagramPoint(325.0, 25.0), routed.targetPoint)
        assertEquals(DiagramNodeSide.LEFT, routed.targetSide)
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
        // Anchors face each other across the wider (horizontal) gap, inset off the corners.
        assertEquals(DiagramNodeSide.RIGHT, routed.sourceSide)
        assertEquals(DiagramNodeSide.LEFT, routed.targetSide)
        assertPointEquals(DiagramPoint(80.0, 48.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(400.0, 212.0), routed.targetPoint)
        // Z-shape with a vertical middle at the midpoint between the stubs.
        assertEquals(4, routed.points.size)
        assertEquals(routed.points[1].x, routed.points[2].x, 1e-6)
    }

    @Test
    fun stackedNodesWithProjectionOverlapConnectStraight() {
        val graph = diagramGraph {
            val top = node("top", x = 170.0, y = 20.0, width = 140.0, height = 48.0)
            val bottom = node("bottom", x = 220.0, y = 140.0, width = 200.0, height = 56.0)
            edge("e", from = top, to = bottom, routing = DiagramRoutingStyle.ORTHOGONAL)
        }
        val routed = routeEdge(graph, graph.edges.single())
        // Overlap of x-projections is [220, 310] — both anchors sit on its midpoint.
        assertEquals(DiagramNodeSide.BOTTOM, routed.sourceSide)
        assertEquals(DiagramNodeSide.TOP, routed.targetSide)
        assertEquals(
            listOf(DiagramPoint(265.0, 68.0), DiagramPoint(265.0, 140.0)),
            routed.points,
        )
    }

    @Test
    fun diagonalFloatingPairPrefersWiderGapAndAvoidsCorners() {
        val graph = diagramGraph {
            val a = node("a", x = 10.0, y = 20.0, width = 140.0, height = 48.0)
            node("blocker", x = 170.0, y = 20.0, width = 140.0, height = 48.0)
            val b = node("b", x = 220.0, y = 140.0, width = 200.0, height = 56.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ORTHOGONAL)
        }
        val routed = routeEdge(graph, graph.edges.single())
        // The old router exited RIGHT through the corner and hugged the blocker's border.
        assertEquals(DiagramNodeSide.BOTTOM, routed.sourceSide)
        assertEquals(DiagramNodeSide.TOP, routed.targetSide)
        assertAxisAligned(routed.points)
        // No segment may touch the blocker (170..310 x, 20..68 y) — a border-hug counts.
        routed.points.zipWithNext().forEach { (a, b) ->
            val horizontal = abs(a.y - b.y) < 1e-6
            val touches = if (horizontal) {
                a.y > 20.0 - 1e-6 && a.y < 68.0 + 1e-6 &&
                    maxOf(a.x, b.x) > 170.0 - 1e-6 && minOf(a.x, b.x) < 310.0 + 1e-6
            } else {
                a.x > 170.0 - 1e-6 && a.x < 310.0 + 1e-6 &&
                    maxOf(a.y, b.y) > 20.0 - 1e-6 && minOf(a.y, b.y) < 68.0 + 1e-6
            }
            assertTrue(!touches, "segment $a -> $b touches the blocker")
        }
    }

    @Test
    fun stubInsideForeignMarginStillAvoidsThatNode() {
        // The blocker sits closer to `a` than the routing margin, so a's exit stub lands
        // inside the blocker's inflated obstacle. The route must still not cut through
        // the blocker itself (the old router dropped it from the obstacle set entirely).
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            node("blocker", x = 108.0, y = -40.0, width = 100.0, height = 140.0)
            val b = node("b", x = 400.0, y = 0.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ORTHOGONAL)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        routed.points.zipWithNext().forEach { (p, q) ->
            val crosses = if (abs(p.y - q.y) < 1e-6) {
                p.y > -40.0 + 1e-6 && p.y < 100.0 - 1e-6 &&
                    maxOf(p.x, q.x) > 108.0 + 1e-6 && minOf(p.x, q.x) < 208.0 - 1e-6
            } else {
                p.x > 108.0 + 1e-6 && p.x < 208.0 - 1e-6 &&
                    maxOf(p.y, q.y) > -40.0 + 1e-6 && minOf(p.y, q.y) < 100.0 - 1e-6
            }
            assertTrue(!crosses, "segment $p -> $q crosses the blocker interior")
        }
    }

    @Test
    fun fanOutFromOneSideSeparatesAnchors() {
        val graph = diagramGraph {
            val hub = node("hub", x = 200.0, y = 0.0, width = 200.0, height = 56.0)
            val left = node("left", x = 0.0, y = 200.0, width = 160.0, height = 56.0)
            val mid = node("mid", x = 220.0, y = 200.0, width = 160.0, height = 56.0)
            val right = node("right", x = 440.0, y = 200.0, width = 160.0, height = 56.0)
            edge("e_left", from = hub, to = left)
            edge("e_mid", from = hub, to = mid)
            edge("e_right", from = hub, to = right)
        }
        val routed = graph.edges.map { routeEdge(graph, it) }
        // All three leave through the hub's bottom side at pairwise distinct anchors.
        routed.forEach { assertEquals(DiagramNodeSide.BOTTOM, it.sourceSide) }
        val anchors = routed.map { it.sourcePoint.x }
        anchors.zipWithNext().forEach { (a, b) ->
            assertTrue(abs(a - b) >= 24.0 - 1e-6, "anchors $anchors closer than the separation")
        }
    }

    @Test
    fun nudgingNeverPushesSegmentsIntoNodes() {
        // Five sources fanning into five targets across one corridor pinned by a large
        // obstacle: unbounded nudge offsets used to push the outer segments onto (and
        // into) the obstacle's body.
        val graph = diagramGraph {
            node("obstacle", x = 100.0, y = 150.0, width = 400.0, height = 300.0)
            repeat(5) { index ->
                val source = node("s$index", x = 0.0, y = 175.0 + index * 20.0, width = 60.0, height = 50.0)
                val target = node("t$index", x = 600.0, y = 175.0 + index * 20.0, width = 60.0, height = 50.0)
                edge("e$index", from = source, to = target)
            }
        }
        val routed = routeAllEdges(graph)
        val obstacle = graph.nodes.first { it.id.value == "obstacle" }.bounds
        routed.forEach { edge ->
            edge.points.zipWithNext().forEach { (a, b) ->
                val crosses = if (abs(a.y - b.y) < 1e-6) {
                    a.y > obstacle.top + 1e-6 && a.y < obstacle.bottom - 1e-6 &&
                        maxOf(a.x, b.x) > obstacle.left + 1e-6 && minOf(a.x, b.x) < obstacle.right - 1e-6
                } else {
                    a.x > obstacle.left + 1e-6 && a.x < obstacle.right - 1e-6 &&
                        maxOf(a.y, b.y) > obstacle.top + 1e-6 && minOf(a.y, b.y) < obstacle.bottom - 1e-6
                }
                assertTrue(
                    !crosses,
                    "${edge.edgeId.value} segment $a -> $b was nudged into the obstacle",
                )
            }
        }
    }

    @Test
    fun nudgingKeepsManualWaypointsOnTheRoute() {
        val waypoint = DiagramPoint(250.0, 125.0)
        val graph = diagramGraph {
            val a1 = node("a1", x = 0.0, y = 0.0, width = 100.0, height = 50.0)
            val a2 = node("a2", x = 400.0, y = 0.0, width = 100.0, height = 50.0)
            val b1 = node("b1", x = 0.0, y = 200.0, width = 100.0, height = 50.0)
            val b2 = node("b2", x = 400.0, y = 200.0, width = 100.0, height = 50.0)
            edge("free", from = a1, to = b2, routing = DiagramRoutingStyle.SIMPLE)
            edge(
                "pinned",
                source = DiagramEndpoint.FloatingAnchor(a2),
                target = DiagramEndpoint.FloatingAnchor(b1),
                routing = DiagramRoutingStyle.SIMPLE,
                waypoints = listOf(waypoint),
            )
        }
        val pinned = routeAllEdges(graph).first { it.edgeId.value == "pinned" }
        val onRoute = pinned.points.zipWithNext().any { (a, b) ->
            if (abs(a.x - b.x) < 1e-6) {
                abs(waypoint.x - a.x) < 1e-6 &&
                    waypoint.y >= minOf(a.y, b.y) - 1e-6 && waypoint.y <= maxOf(a.y, b.y) + 1e-6
            } else {
                abs(waypoint.y - a.y) < 1e-6 &&
                    waypoint.x >= minOf(a.x, b.x) - 1e-6 && waypoint.x <= maxOf(a.x, b.x) + 1e-6
            }
        }
        assertTrue(onRoute, "waypointed route ${pinned.points} no longer passes through $waypoint")
    }

    @Test
    fun routeEdgeToleratesEdgeNotContainedInGraph() {
        val graph = diagramGraph {
            node("a", x = 0.0, y = 0.0, width = 100.0, height = 50.0)
            node("b", x = 0.0, y = 200.0, width = 100.0, height = 50.0)
        }
        val preview = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 50.0)
            val b = node("b", x = 0.0, y = 200.0, width = 100.0, height = 50.0)
            edge("preview", from = a, to = b)
        }.edges.single()
        // The preview edge is not part of graph.edges — routing must not throw.
        val routed = routeEdge(graph, preview)
        assertEquals(DiagramNodeSide.BOTTOM, routed.sourceSide)
        assertEquals(DiagramNodeSide.TOP, routed.targetSide)
        assertAxisAligned(routed.points)
    }

    @Test
    fun coRunningCorridorSegmentsAreNudgedApart() {
        val graph = diagramGraph {
            val hub = node("hub", x = 200.0, y = 0.0, width = 200.0, height = 56.0)
            // Both targets are left-below: the two routes share the corridor under the hub.
            val near = node("near", x = 0.0, y = 150.0, width = 120.0, height = 56.0)
            val far = node("far", x = 0.0, y = 320.0, width = 120.0, height = 56.0)
            edge("e_near", from = hub, to = near)
            edge("e_far", from = hub, to = far)
        }
        val routed = routeAllEdges(graph)
        val horizontals = routed.flatMap { edge ->
            edge.points.zipWithNext().filter { (a, b) -> abs(a.y - b.y) < 1e-6 }
                .map { (a, b) -> Triple(a.y, minOf(a.x, b.x), maxOf(a.x, b.x)) }
        }
        for (i in horizontals.indices) {
            for (j in i + 1 until horizontals.size) {
                val (y1, s1, e1) = horizontals[i]
                val (y2, s2, e2) = horizontals[j]
                val collinearOverlap =
                    abs(y1 - y2) < 1e-6 && minOf(e1, e2) - maxOf(s1, s2) > 1e-6
                assertTrue(
                    !collinearOverlap,
                    "segments y=$y1 x=[$s1,$e1] and y=$y2 x=[$s2,$e2] run on top of each other",
                )
            }
        }
    }

    @Test
    fun interiorSegmentIsNudgedAwayFromAnotherEdgesEndpointSegment() {
        val endpointRoute = RoutedEdge(
            edgeId = DiagramEdgeId("endpoint"),
            routing = DiagramRoutingStyle.ORTHOGONAL,
            points = listOf(
                DiagramPoint(0.0, 0.0),
                DiagramPoint(100.0, 0.0),
                DiagramPoint(100.0, 100.0),
            ),
        )
        val crossingRoute = RoutedEdge(
            edgeId = DiagramEdgeId("crossing"),
            routing = DiagramRoutingStyle.ORTHOGONAL,
            points = listOf(
                DiagramPoint(50.0, -50.0),
                DiagramPoint(100.0, -50.0),
                DiagramPoint(100.0, 50.0),
                DiagramPoint(150.0, 50.0),
            ),
        )

        val nudged = nudgeRoutedEdges(listOf(endpointRoute, crossingRoute))
        val fixed = nudged.first { it.edgeId == endpointRoute.edgeId }
        val moved = nudged.first { it.edgeId == crossingRoute.edgeId }

        assertEquals(endpointRoute.points, fixed.points, "endpoint stubs must stay attached")
        assertTrue(
            abs(moved.points[1].x - 100.0) > 1e-6 && abs(moved.points[2].x - 100.0) > 1e-6,
            "movable segment ${moved.points[1]} -> ${moved.points[2]} still overlaps the endpoint lane",
        )
        assertAxisAligned(moved.points)
    }

    @Test
    fun authoredPortRoutesDoNotGrowDanglingAppendices() {
        fun port(id: String, side: DiagramNodeSide, offset: Double): DiagramPort =
            DiagramPort(DiagramPortId(id), DiagramPortAnchor.SideOffset(side, offset))

        val graph = diagramGraph {
            val configureEntrances = node(
                "configure_entrances",
                x = 70.0,
                y = 480.0,
                width = 300.0,
                height = 110.0,
                ports = listOf(
                    port("entrance", DiagramNodeSide.BOTTOM, 0.5),
                    port("senior", DiagramNodeSide.TOP, 0.8),
                ),
            )
            node("fill_house_representatives", x = 970.0, y = 375.0, width = 280.0, height = 130.0)
            node("propose_representative_change", x = 90.0, y = 1010.0, width = 320.0, height = 120.0)
            node("approve_representative_change", x = 440.0, y = 1120.0, width = 440.0, height = 100.0)
            val mkd = node(
                "mkd",
                x = 430.0,
                y = 280.0,
                width = 360.0,
                height = 170.0,
                ports = listOf(port("entrances", DiagramNodeSide.BOTTOM, 0.47)),
            )
            val chair = node(
                "house_chair",
                x = 240.0,
                y = 520.0,
                width = 300.0,
                height = 150.0,
                ports = listOf(
                    port("filled", DiagramNodeSide.TOP, 0.9),
                    port("approved_change", DiagramNodeSide.LEFT, 0.75),
                ),
            )
            val council = node(
                "house_council_member",
                x = 690.0,
                y = 550.0,
                width = 300.0,
                height = 150.0,
                ports = listOf(port("approved_change", DiagramNodeSide.RIGHT, 0.5)),
            )
            val entrance = node(
                "house_entrance",
                x = 250.0,
                y = 730.0,
                width = 300.0,
                height = 140.0,
                ports = listOf(
                    port("house", DiagramNodeSide.TOP, 0.9),
                    port("configured", DiagramNodeSide.LEFT, 0.15),
                    port("senior", DiagramNodeSide.RIGHT, 0.5),
                ),
            )
            node("entrance_senior", x = 690.0, y = 730.0, width = 320.0, height = 140.0)
            val change = node(
                "representative_change",
                x = 450.0,
                y = 910.0,
                width = 390.0,
                height = 150.0,
                ports = listOf(
                    port("chair", DiagramNodeSide.LEFT, 0.35),
                    port("council", DiagramNodeSide.RIGHT, 0.7),
                ),
            )

            edge(
                "mkd_has_entrances",
                source = DiagramEndpoint.FixedPort(mkd, DiagramPortId("entrances")),
                target = DiagramEndpoint.FixedPort(entrance, DiagramPortId("house")),
                waypoints = listOf(DiagramPoint(600.0, 690.0), DiagramPoint(520.0, 690.0)),
            )
            edge(
                "change_targets_chair",
                source = DiagramEndpoint.FixedPort(change, DiagramPortId("chair")),
                target = DiagramEndpoint.FixedPort(chair, DiagramPortId("approved_change")),
                waypoints = listOf(DiagramPoint(210.0, 963.0), DiagramPoint(210.0, 633.0)),
            )
            edge(
                "change_targets_council",
                source = DiagramEndpoint.FixedPort(change, DiagramPortId("council")),
                target = DiagramEndpoint.FixedPort(council, DiagramPortId("approved_change")),
                // The first via has stale Y after the source node was resized. It is
                // still the corner of the horizontal port leg and must not grow a tail.
                waypoints = listOf(DiagramPoint(1060.0, 1023.5), DiagramPoint(1060.0, 625.0)),
            )
            edge(
                "configure_entrances_changes_entrance",
                source = DiagramEndpoint.FixedPort(configureEntrances, DiagramPortId("entrance")),
                target = DiagramEndpoint.FixedPort(entrance, DiagramPortId("configured")),
                waypoints = listOf(DiagramPoint(230.0, 700.0), DiagramPoint(230.0, 751.0)),
            )
        }

        val routes = routeAllEdgesLenient(graph)
        routes.values.forEach { route ->
            assertAxisAligned(route.points)
        }
        assertEquals(
            listOf(
                DiagramPoint(599.2, 450.0),
                DiagramPoint(599.2, 690.0),
                DiagramPoint(520.0, 690.0),
                DiagramPoint(520.0, 730.0),
            ),
            routes.getValue(DiagramEdgeId("mkd_has_entrances")).points,
        )
        assertEquals(
            listOf(
                DiagramPoint(450.0, 962.5),
                DiagramPoint(210.0, 962.5),
                DiagramPoint(210.0, 632.5),
                DiagramPoint(240.0, 632.5),
            ),
            routes.getValue(DiagramEdgeId("change_targets_chair")).points,
        )
        assertEquals(
            listOf(
                DiagramPoint(840.0, 1015.0),
                DiagramPoint(1060.0, 1015.0),
                DiagramPoint(1060.0, 625.0),
                DiagramPoint(990.0, 625.0),
            ),
            routes.getValue(DiagramEdgeId("change_targets_council")).points,
        )
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
        // The wider gap is horizontal, so the route leaves/enters through the facing sides.
        assertEquals(DiagramNodeSide.RIGHT, routed.sourceSide)
        assertEquals(DiagramNodeSide.LEFT, routed.targetSide)
        assertTrue(abs(routed.sourcePoint.x - 100.0) < 1e-6, "source must sit on a's right face")
        assertTrue(abs(routed.targetPoint.x - 300.0) < 1e-6, "target must sit on b's left face")
        // First and last segments run horizontally out of the entity sides.
        val firstSegmentHorizontal = abs(routed.points[0].y - routed.points[1].y) < 1e-6
        val lastSegmentHorizontal =
            abs(routed.points[routed.points.size - 2].y - routed.points.last().y) < 1e-6
        assertTrue(firstSegmentHorizontal && lastSegmentHorizontal)
    }

    @Test
    fun entityRelationRouteAvoidsEntityBetween() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            node("mid", x = 150.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 400.0, y = 0.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ENTITY_RELATION)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        // No segment may cross the middle entity's interior (150..250 x, 0..60 y).
        routed.points.zipWithNext().forEach { (a, b) ->
            val crosses = if (abs(a.y - b.y) < 1e-6) {
                a.y > 0.0 + 1e-6 && a.y < 60.0 - 1e-6 &&
                    maxOf(a.x, b.x) > 150.0 + 1e-6 && minOf(a.x, b.x) < 250.0 - 1e-6
            } else {
                a.x > 150.0 + 1e-6 && a.x < 250.0 - 1e-6 &&
                    maxOf(a.y, b.y) > 0.0 + 1e-6 && minOf(a.y, b.y) < 60.0 - 1e-6
            }
            assertTrue(!crosses, "segment $a -> $b cuts through the middle entity")
        }
        // The route actually detours around it.
        assertTrue(routed.points.size > 2)
    }

    @Test
    fun entityRelationFanInSpreadsAnchors() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = -100.0, width = 100.0, height = 60.0)
            val c = node("c", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            val d = node("d", x = 300.0, y = 100.0, width = 100.0, height = 60.0)
            edge("e1", from = b, to = a, routing = DiagramRoutingStyle.ENTITY_RELATION)
            edge("e2", from = c, to = a, routing = DiagramRoutingStyle.ENTITY_RELATION)
            edge("e3", from = d, to = a, routing = DiagramRoutingStyle.ENTITY_RELATION)
        }
        val anchors = graph.edges.map { routeEdge(graph, it).targetPoint }
        anchors.forEach { assertTrue(abs(it.x - 100.0) < 1e-6, "anchor $it must sit on a's right face") }
        val ys = anchors.map { it.y }
        assertEquals(ys.toSet().size, ys.size, "fan-in anchors must not coincide: $ys")
    }

    @Test
    fun entityRelationRouteNeverDoublesBackOnItself() {
        // Diagonal placement whose cheapest grid route hugs the target's inflated boundary:
        // with a stub longer than the corridor margin this used to retrace the stub as a
        // visible whisker spur (consecutive anti-parallel segments).
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 130.0, y = 80.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ENTITY_RELATION)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertAxisAligned(routed.points)
        for (index in 0 until routed.points.size - 2) {
            val a = routed.points[index]
            val b = routed.points[index + 1]
            val c = routed.points[index + 2]
            val dot = (b.x - a.x) * (c.x - b.x) + (b.y - a.y) * (c.y - b.y)
            assertTrue(dot >= 0.0, "route doubles back at $b: ${routed.points}")
        }
    }

    @Test
    fun entityRelationRouteExitsVerticallyForStackedEntities() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 0.0, y = 200.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, routing = DiagramRoutingStyle.ENTITY_RELATION)
        }
        val routed = routeEdge(graph, graph.edges.single())
        assertEquals(DiagramNodeSide.BOTTOM, routed.sourceSide)
        assertEquals(DiagramNodeSide.TOP, routed.targetSide)
        // Full projection overlap: a single straight vertical connector at the midpoint.
        assertPointEquals(DiagramPoint(50.0, 60.0), routed.sourcePoint)
        assertPointEquals(DiagramPoint(50.0, 200.0), routed.targetPoint)
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
        // CURVED routes like ORTHOGONAL (obstacle-aware bends) and is rendered as a
        // spline through the route points; the manual waypoint stays mandatory.
        assertTrue(routed.isCurve)
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
