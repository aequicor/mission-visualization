package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinateAssignmentTest {

    private fun DiagramGraph.componentLayout(
        config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
    ): ComponentLayout = layoutComponent(
        nodesById = nodes.associateBy { it.id },
        adjacency = scopeAdjacency(null),
        config = config,
    )

    private fun ComponentLayout.centerX(id: String): Double {
        val slot = graph.slots.first { it.realId?.value == id }
        return positions.getValue(slot.key).x
    }

    @Test
    fun chainEdgesAreCollinear() {
        val graph = diagramGraph {
            val a = node("a")
            val b = node("b")
            val c = node("c")
            edge("a-b", a, b)
            edge("b-c", b, c)
        }
        val layout = graph.componentLayout()
        assertEquals(layout.centerX("a"), layout.centerX("b"), 1e-9)
        assertEquals(layout.centerX("b"), layout.centerX("c"), 1e-9)
    }

    @Test
    fun longEdgeDummyChainSharesOneCross() {
        val graph = diagramGraph {
            val a = node("a")
            val b = node("b")
            val c = node("c")
            val d = node("d")
            edge("a-b", a, b)
            edge("b-c", b, c)
            edge("c-d", c, d)
            edge("a-d", a, d)
        }
        val layout = graph.componentLayout()
        val dummyCenters = layout.graph.slots
            .filter { it.isDummy }
            .map { layout.positions.getValue(it.key).x }
        assertEquals(2, dummyCenters.size)
        assertEquals(dummyCenters[0], dummyCenters[1], 1e-9, "long-edge corridor must run straight")
        // The parallel real chain stays straight too.
        assertEquals(layout.centerX("b"), layout.centerX("c"), 1e-9)
    }

    @Test
    fun variableWidthNeighborsNeverOverlapAndKeepTheGap() {
        val graph = diagramGraph {
            val a = node("a", width = 200.0)
            val b1 = node("b1", width = 80.0)
            val b2 = node("b2", width = 260.0)
            val b3 = node("b3", width = 40.0)
            val c = node("c", width = 120.0)
            edge("a-b1", a, b1)
            edge("a-b2", a, b2)
            edge("a-b3", a, b3)
            edge("b1-c", b1, c)
            edge("b2-c", b2, c)
            edge("b3-c", b3, c)
        }
        val config = DiagramLayoutConfig.Default
        val layout = graph.componentLayout(config)
        for (layer in layout.graph.layers) {
            for (index in 0 until layer.size - 1) {
                val left = layer[index]
                val right = layer[index + 1]
                val leftWidth = left.realId?.let { graph.nodeById(it)!!.width } ?: config.dummySize
                val leftEnd = layout.positions.getValue(left.key).x + leftWidth
                val rightStart = layout.positions.getValue(right.key).x
                assertTrue(
                    rightStart - leftEnd >= config.nodeGap - 1e-9,
                    "gap violated between ${left.key} and ${right.key}: $leftEnd .. $rightStart",
                )
            }
        }
    }

    @Test
    fun balancedCoordinatesAreDeterministic() {
        val graph = diagramGraph {
            val a = node("a")
            val b1 = node("b1")
            val b2 = node("b2")
            val c1 = node("c1")
            val c2 = node("c2")
            val d = node("d")
            edge("a-b1", a, b1)
            edge("a-b2", a, b2)
            edge("b1-c1", b1, c1)
            edge("b2-c2", b2, c2)
            edge("b2-c1", b2, c1)
            edge("c1-d", c1, d)
            edge("c2-d", c2, d)
            edge("a-d", a, d)
        }
        assertEquals(graph.componentLayout().positions, graph.componentLayout().positions)
    }
}
