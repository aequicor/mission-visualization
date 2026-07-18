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

class CrossingAwareRoutingTest {

    // --- RouteCrossingIndex counting semantics ------------------------------------------

    @Test
    fun indexCountsProperCrossingsOnly() {
        val index = RouteCrossingIndex.of(
            listOf(
                // A vertical line at x=100 spanning y 0..200.
                listOf(DiagramPoint(100.0, 0.0), DiagramPoint(100.0, 200.0)),
                // A horizontal line at y=300 spanning x 0..200.
                listOf(DiagramPoint(0.0, 300.0), DiagramPoint(200.0, 300.0)),
            ),
        )
        // Proper perpendicular crossing.
        assertEquals(1, index.crossingsOfHorizontal(y = 100.0, x1 = 0.0, x2 = 200.0))
        assertEquals(1, index.crossingsOfVertical(x = 100.0, y1 = 250.0, y2 = 350.0))
        // T-junction: the run ENDS on the line — an arrival tap, not a crossing.
        assertEquals(0, index.crossingsOfHorizontal(y = 100.0, x1 = 0.0, x2 = 100.0))
        // The run passes beside the line's span.
        assertEquals(0, index.crossingsOfHorizontal(y = 250.0, x1 = 0.0, x2 = 200.0))
        // Touching the line's endpoint row: the vertical span ends at y=200.
        assertEquals(0, index.crossingsOfHorizontal(y = 200.0, x1 = 0.0, x2 = 200.0))
        // Collinear co-run is the nudge's domain, never a crossing.
        assertEquals(0, index.crossingsOfVertical(x = 100.0, y1 = 0.0, y2 = 200.0))
    }

    // --- batch routing takes a bounded detour around placed lines ------------------------

    private fun crossingCount(a: List<DiagramPoint>, b: List<DiagramPoint>): Int {
        var count = 0
        for ((a1, a2) in a.zipWithNext()) {
            for ((b1, b2) in b.zipWithNext()) {
                val aH = a1.y == a2.y
                val bH = b1.y == b2.y
                if (aH == bH) continue
                val (h1, h2, v1, v2) = if (aH) listOf(a1, a2, b1, b2) else listOf(b1, b2, a1, a2)
                val hx1 = minOf(h1.x, h2.x)
                val hx2 = maxOf(h1.x, h2.x)
                val vy1 = minOf(v1.y, v2.y)
                val vy2 = maxOf(v1.y, v2.y)
                if (v1.x > hx1 + 1e-6 && v1.x < hx2 - 1e-6 && h1.y > vy1 + 1e-6 && h1.y < vy2 - 1e-6) count++
            }
        }
        return count
    }

    private fun laneAndLateEdgeGraph() = diagramGraph {
        val laneTop = node("lane_top", x = 170.0, y = 0.0, width = 60.0, height = 30.0)
        val laneBottom = node("lane_bottom", x = 170.0, y = 180.0, width = 60.0, height = 30.0)
        val a = node("a", x = 0.0, y = 60.0, width = 80.0, height = 80.0)
        val b = node("b", x = 360.0, y = 260.0, width = 80.0, height = 80.0)
        // The lane routes first (graph order) and owns the corridor.
        edge("lane", source = DiagramEndpoint.FloatingAnchor(laneTop), target = DiagramEndpoint.FloatingAnchor(laneBottom))
        edge("late", source = DiagramEndpoint.FloatingAnchor(a), target = DiagramEndpoint.FloatingAnchor(b))
    }

    @Test
    fun aLaterEdgeDetoursAroundAPlacedLane() {
        val routes = routeAllEdges(laneAndLateEdgeGraph()).associateBy { it.edgeId.value }
        assertEquals(
            0,
            crossingCount(routes.getValue("late").points, routes.getValue("lane").points),
            "the late edge must dodge the placed lane, got ${routes.getValue("late").points}",
        )
    }

    @Test
    fun zeroPenaltyKeepsTheCrossingShortcut() {
        val routes = routeAllEdges(
            laneAndLateEdgeGraph(),
            RoutingOptions.Default.copy(crossingPenalty = 0.0),
        ).associateBy { it.edgeId.value }
        assertTrue(
            crossingCount(routes.getValue("late").points, routes.getValue("lane").points) >= 1,
            "with the awareness off the shortest route crosses the lane, got ${routes.getValue("late").points}",
        )
    }

    // --- regret pass: a detour must pay for itself against the final snapshot ------------

    private fun routed(id: String, vararg points: DiagramPoint) = RoutedEdge(
        edgeId = DiagramEdgeId(id),
        routing = DiagramRoutingStyle.ORTHOGONAL,
        points = points.toList(),
    )

    private fun probeAndLaneGraph() = diagramGraph {
        edge(
            "lane",
            source = DiagramEndpoint.FreePoint(50.0, 90.0),
            target = DiagramEndpoint.FreePoint(50.0, 110.0),
        )
        edge(
            "probe",
            source = DiagramEndpoint.FreePoint(0.0, 100.0),
            target = DiagramEndpoint.FreePoint(100.0, 100.0),
        )
    }

    /**
     * Runs [crossingAwareRoutes] with canned candidate routes for the probe edge: its
     * crossing-aware pass returns [aware], the blind calls return [blind]. The lane edge
     * always routes to [lane].
     */
    private fun regretSelection(
        lane: RoutedEdge,
        blind: RoutedEdge,
        aware: RoutedEdge,
    ): RoutedEdge {
        val graph = probeAndLaneGraph()
        val routes = crossingAwareRoutes(graph, RoutingOptions.Default, emptyMap()) { edge, crossings ->
            when (edge.id.value) {
                "lane" -> lane
                else -> if (crossings == null) blind else aware
            }
        }
        return requireNotNull(routes[1])
    }

    @Test
    fun anUnpaidSecondPassDetourFallsBackToTheBlindRoute() {
        // The aware detour is longer and saves not a single real crossing against the
        // lane's final position — the classic ghost dodge the Jacobi pass leaves behind.
        val lane = routed("lane", DiagramPoint(50.0, 90.0), DiagramPoint(50.0, 110.0))
        val blind = routed("probe", DiagramPoint(0.0, 200.0), DiagramPoint(100.0, 200.0))
        val aware = routed(
            "probe",
            DiagramPoint(0.0, 200.0),
            DiagramPoint(0.0, 260.0),
            DiagramPoint(100.0, 260.0),
            DiagramPoint(100.0, 200.0),
        )
        assertEquals(blind.points, regretSelection(lane, blind, aware).points)
    }

    @Test
    fun aPayingDetourSurvivesTheRegretPass() {
        // Here the blind route really crosses the lane (penalty 50) while the aware
        // detour spends only 30 units and 2 turns to avoid it — the detour stays.
        val lane = routed("lane", DiagramPoint(50.0, 90.0), DiagramPoint(50.0, 110.0))
        val blind = routed("probe", DiagramPoint(0.0, 100.0), DiagramPoint(100.0, 100.0))
        val aware = routed(
            "probe",
            DiagramPoint(0.0, 100.0),
            DiagramPoint(0.0, 115.0),
            DiagramPoint(100.0, 115.0),
            DiagramPoint(100.0, 100.0),
        )
        assertEquals(aware.points, regretSelection(lane, blind, aware).points)
    }

    // --- spur collapse: stale vias must not leave dead-end whiskers ----------------------

    private fun assertNoSpur(points: List<DiagramPoint>) {
        for (index in 1 until points.size - 1) {
            val a = points[index - 1]
            val b = points[index]
            val c = points[index + 1]
            val horizontal = abs(a.y - b.y) < 1e-6 && abs(b.y - c.y) < 1e-6 &&
                (b.x - a.x) * (c.x - b.x) < 0.0
            val vertical = abs(a.x - b.x) < 1e-6 && abs(b.x - c.x) < 1e-6 &&
                (b.y - a.y) * (c.y - b.y) < 0.0
            assertTrue(!horizontal && !vertical, "route doubles back at $b: $points")
        }
    }

    @Test
    fun aStaleViaRetraceCollapsesInsteadOfLeavingASpur() {
        fun port(id: String, side: DiagramNodeSide, offset: Double): DiagramPort =
            DiagramPort(DiagramPortId(id), DiagramPortAnchor.SideOffset(side, offset))

        // Both ports face LEFT; the first authored via sits far ABOVE the source port
        // row (stale after the node moved down), so the leg into the via and the leg out
        // retrace the same x=45 lane — the dead-end whisker seen on reference files.
        val graph = diagramGraph {
            val src = node(
                "src",
                x = 100.0,
                y = 360.0,
                width = 80.0,
                height = 60.0,
                ports = listOf(port("out", DiagramNodeSide.LEFT, 0.5)),
            )
            val dst = node(
                "dst",
                x = 100.0,
                y = 555.0,
                width = 80.0,
                height = 60.0,
                ports = listOf(port("in", DiagramNodeSide.LEFT, 0.5)),
            )
            edge(
                "hook",
                source = DiagramEndpoint.FixedPort(src, DiagramPortId("out")),
                target = DiagramEndpoint.FixedPort(dst, DiagramPortId("in")),
                waypoints = listOf(DiagramPoint(45.0, 280.0), DiagramPoint(45.0, 585.0)),
            )
        }
        val route = routeEdge(graph, graph.edges.single())
        assertNoSpur(route.points)
        assertTrue(
            route.points.none { it.y < 330.0 },
            "the whisker to the stale via must collapse: ${route.points}",
        )
        assertTrue(
            route.points.any { abs(it.x - 45.0) < 1e-6 },
            "the authored x=45 corridor must survive the collapse: ${route.points}",
        )
    }

    @Test
    fun collapseLoopsExcisesALassoBackToItsRevisitPoint() {
        // The real shape from the reference ER file: the leg out of a twice-displaced
        // via cannot reverse onto the leg that arrived, so the A* paid a full loop
        // around a grid cell and returned to the exact corner it had already visited.
        val lasso = listOf(
            DiagramPoint(2350.0, 819.0),
            DiagramPoint(2238.0, 819.0),
            DiagramPoint(2238.0, 2388.0),
            DiagramPoint(2280.0, 2388.0),
            DiagramPoint(2280.0, 2342.0),
            DiagramPoint(2238.0, 2342.0),
            DiagramPoint(2238.0, 2388.0),
            DiagramPoint(1712.0, 2388.0),
            DiagramPoint(1712.0, 2443.0),
        )
        assertEquals(
            listOf(
                DiagramPoint(2350.0, 819.0),
                DiagramPoint(2238.0, 819.0),
                DiagramPoint(2238.0, 2388.0),
                DiagramPoint(1712.0, 2388.0),
                DiagramPoint(1712.0, 2443.0),
            ),
            collapseLoops(lasso),
        )
    }

    @Test
    fun collapseSpursTrimsPartialAndExactRetraces() {
        // Partial retrace: the route overshoots to x=100 and comes back to x=60.
        assertEquals(
            listOf(DiagramPoint(0.0, 0.0), DiagramPoint(60.0, 0.0), DiagramPoint(60.0, 50.0)),
            collapseSpurs(
                listOf(
                    DiagramPoint(0.0, 0.0),
                    DiagramPoint(100.0, 0.0),
                    DiagramPoint(60.0, 0.0),
                    DiagramPoint(60.0, 50.0),
                ),
            ),
        )
        // Exact retrace: out and back over the same span collapses to nothing.
        assertEquals(
            listOf(DiagramPoint(0.0, 0.0), DiagramPoint(0.0, 50.0)),
            collapseSpurs(
                listOf(
                    DiagramPoint(0.0, 0.0),
                    DiagramPoint(100.0, 0.0),
                    DiagramPoint(0.0, 0.0),
                    DiagramPoint(0.0, 50.0),
                ),
            ),
        )
    }
}
