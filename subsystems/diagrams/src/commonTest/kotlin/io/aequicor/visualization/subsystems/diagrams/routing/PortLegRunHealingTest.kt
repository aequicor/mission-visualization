package io.aequicor.visualization.subsystems.diagrams.routing

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
import kotlin.test.assertTrue

/**
 * The port heal of an endpoint-adjacent via must drag the collinear authored run along:
 * an author draws a multi-via row a pixel off the port row (er 'vdgo_references_defects':
 * run at y=3452, port row 3451), the heal used to move only the last via, and the 1px
 * seam reappeared one via inward as a tiny staircase right before the marker.
 */
class PortLegRunHealingTest {

    @Test
    fun healedViaDragsItsCollinearRunOntoThePortRow() {
        val outPort = DiagramPortId("out_bottom")
        val inPort = DiagramPortId("in_left")
        val graph = diagramGraph {
            val src = node(
                "src",
                x = 200.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(
                    DiagramPort(outPort, DiagramPortAnchor.SideOffset(DiagramNodeSide.BOTTOM, 0.5)),
                ),
            )
            val dst = node(
                "dst",
                x = 700.0,
                y = 170.0,
                width = 100.0,
                height = 60.0,
                ports = listOf(
                    // Port row y = 170 + 0.5 * 60 = 200; the authored run sits at 201.
                    DiagramPort(inPort, DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT, 0.5)),
                ),
            )
            edge(
                "link",
                source = DiagramEndpoint.FixedPort(src, outPort),
                target = DiagramEndpoint.FixedPort(dst, inPort),
                routing = DiagramRoutingStyle.ORTHOGONAL,
                waypoints = listOf(
                    DiagramPoint(250.0, 120.0),
                    DiagramPoint(250.0, 201.0),
                    DiagramPoint(600.0, 201.0),
                ),
            )
        }

        val route = routeEdge(graph, graph.edges.single())
        assertTrue(
            route.points.none { abs(it.y - 201.0) < 1e-6 },
            "the stale 201 row must heal onto the port row 200: ${route.points}",
        )
        val tail = route.points.takeLast(2)
        assertTrue(
            tail.all { abs(it.y - 200.0) < 1e-6 },
            "the arrival must run straight on the port row 200: ${route.points}",
        )
    }
}
