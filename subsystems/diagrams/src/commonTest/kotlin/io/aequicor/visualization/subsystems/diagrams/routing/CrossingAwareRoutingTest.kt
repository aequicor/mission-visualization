package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
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
}
