package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixed ports of one facing connector sitting a few pixels off a shared row must fuse
 * into a single straight line (er 'entrance_has_apartments': rows 636 vs 640 drew an
 * irreducible 4px jog mid-corridor), while ports genuinely apart keep their exact rows.
 */
class FacingPortPairAlignmentTest {

    private val outPort = DiagramPortId("out")
    private val inPort = DiagramPortId("in")

    private fun facingGraph(sourceOffset: Double, targetOffset: Double) = diagramGraph {
        val left = node(
            "left",
            x = 0.0,
            y = 0.0,
            width = 200.0,
            height = 100.0,
            ports = listOf(
                DiagramPort(outPort, DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT, sourceOffset)),
            ),
        )
        val right = node(
            "right",
            x = 300.0,
            y = 0.0,
            width = 200.0,
            height = 100.0,
            ports = listOf(
                DiagramPort(inPort, DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT, targetOffset)),
            ),
        )
        edge(
            "link",
            source = DiagramEndpoint.FixedPort(left, outPort),
            target = DiagramEndpoint.FixedPort(right, inPort),
            routing = DiagramRoutingStyle.ORTHOGONAL,
        )
    }

    @Test
    fun nearlyAlignedFacingPortsFuseToOneStraightRow() {
        // Rows 36 vs 40: inside the snap tolerance, both ends meet on the midpoint row.
        val graph = facingGraph(sourceOffset = 0.36, targetOffset = 0.40)
        val route = routeEdge(graph, graph.edges.single())
        assertTrue(
            route.points.all { abs(it.y - 38.0) < 1e-6 },
            "connector must fuse onto the shared row y=38, got ${route.points}",
        )
        assertEquals(2, route.points.size, "fused connector needs no bends: ${route.points}")
    }

    @Test
    fun portsBeyondTheToleranceKeepTheirExactRows() {
        // Rows 30 vs 40: a genuine offset — both ports hold their authored rows.
        val graph = facingGraph(sourceOffset = 0.30, targetOffset = 0.40)
        val route = routeEdge(graph, graph.edges.single())
        assertTrue(
            abs(route.points.first().y - 30.0) < 1e-6 && abs(route.points.last().y - 40.0) < 1e-6,
            "ports beyond the tolerance keep exact rows 30/40, got ${route.points}",
        )
    }
}
