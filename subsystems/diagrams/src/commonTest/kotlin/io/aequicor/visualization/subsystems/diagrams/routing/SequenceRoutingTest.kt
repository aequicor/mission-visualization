package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SequenceRoutingTest {

    private fun message(kind: UmlMessageKind = UmlMessageKind.SYNC) =
        DiagramRelation.Message(kind)

    /** Three lifelines side by side, N messages between them in declaration order. */
    private fun sequenceGraph() = diagramGraph {
        val a = node("a", x = 40.0, y = 0.0, width = 150.0, height = 440.0, payload = UmlLifelineNode("A"))
        val b = node("b", x = 340.0, y = 0.0, width = 150.0, height = 440.0, payload = UmlLifelineNode("B"))
        val c = node("c", x = 640.0, y = 0.0, width = 150.0, height = 440.0, payload = UmlLifelineNode("C"))
        edge("m1", from = a, to = b, relation = message())
        edge("m2", from = b, to = c, relation = message(UmlMessageKind.ASYNC))
        edge("m3", from = c, to = a, relation = message(UmlMessageKind.RETURN))
        edge("m4", from = b, to = b, relation = message()) // self-message
    }

    @Test
    fun messagesAreHorizontalRowsSteppingDown() {
        val graph = sequenceGraph()
        val routes = sequenceMessageRoutes(graph)
        assertEquals(4, routes.size)

        // Each non-self message is a single horizontal segment between lifeline center lines.
        val m1 = routes[graph.edges[0].id]!!
        assertEquals(2, m1.points.size)
        assertEquals(115.0, m1.sourcePoint.x, "source at lifeline a center") // 40 + 150/2
        assertEquals(415.0, m1.targetPoint.x, "target at lifeline b center") // 340 + 150/2
        assertTrue(abs(m1.sourcePoint.y - m1.targetPoint.y) < 1e-9, "m1 must be horizontal")

        // Rows step strictly downward in declaration order.
        val ys = listOf("m1", "m2", "m3", "m4").map { id ->
            routes.entries.first { it.key.value == id }.value.sourcePoint.y
        }
        ys.zipWithNext().forEach { (upper, lower) ->
            assertTrue(lower > upper, "row $lower must sit below $upper")
        }

        // All rows fall inside the lifeline body (below heads, above feet).
        ys.forEach { y ->
            assertTrue(y in 0.0..440.0, "row $y inside lifeline body")
        }
    }

    @Test
    fun selfMessageIsAClosedRightSideLoop() {
        val graph = sequenceGraph()
        val routes = sequenceMessageRoutes(graph)
        val self = routes.entries.first { it.key.value == "m4" }.value
        assertEquals(4, self.points.size, "self-message loops with 4 points")
        // Bows out to the right of lifeline b's center (415) and returns to it.
        assertEquals(415.0, self.points.first().x)
        assertEquals(415.0, self.points.last().x)
        assertTrue(self.points[1].x > 415.0, "loop bows right")
        assertTrue(self.points.last().y > self.points.first().y, "loop returns lower")
    }

    @Test
    fun sequenceRoutesFlowThroughRouteAllEdges() {
        val graph = sequenceGraph()
        val all = routeAllEdgesLenient(graph)
        // m1 stays a straight horizontal 2-point row, not an orthogonal perimeter route.
        val m1 = all[graph.edges[0].id]!!
        assertEquals(DiagramRoutingStyle.STRAIGHT, m1.routing)
        assertEquals(2, m1.points.size)
        assertTrue(abs(m1.sourcePoint.y - m1.targetPoint.y) < 1e-9)
    }

    @Test
    fun nonLifelineGraphIsUntouched() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            val b = node("b", x = 300.0, y = 0.0, width = 100.0, height = 60.0)
            edge("e", from = a, to = b, relation = DiagramRelation.Generalization)
        }
        assertTrue(sequenceMessageRoutes(graph).isEmpty())
    }
}
