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
 * A planned floating end whose far end is a fixed port on the directly facing side must
 * aim at the port's own row, not the node-projection midpoint: the midpoint sits a few
 * pixels off the port whenever the two nodes are not perfectly aligned, which leaves an
 * irreducible 2-5px jog in the middle of an otherwise straight connector.
 */
class FloatingAnchorFacingPortTest {

    @Test
    fun floatingEndAlignsToFacingFixedPortRow() {
        // Mirrors the gas-room reference 'factors_yes': the decision's 'yes' port row
        // sits 5px off the target's projection midpoint; the connector must come out as
        // one straight segment on the port row instead of a jogged staircase.
        val portId = DiagramPortId("yes")
        val graph = diagramGraph {
            val notify = node("notify", x = 0.0, y = 0.0, width = 200.0, height = 100.0)
            val decision = node(
                "decision",
                x = 400.0,
                y = 10.0,
                width = 100.0,
                height = 100.0,
                ports = listOf(
                    DiagramPort(portId, DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT, 0.5)),
                ),
            )
            edge(
                "yes_to_notify",
                source = DiagramEndpoint.FixedPort(decision, portId),
                target = DiagramEndpoint.FloatingAnchor(notify),
                routing = DiagramRoutingStyle.ORTHOGONAL,
            )
        }

        val route = routeEdge(graph, graph.edges.single())
        val portRow = 60.0
        assertTrue(
            route.points.all { abs(it.y - portRow) < 1e-6 },
            "connector must run straight on the port row y=$portRow, got ${route.points}",
        )
        assertEquals(2, route.points.size, "straight connector needs no bends: ${route.points}")
    }
}
