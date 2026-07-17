package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StressLayoutTest {

    private fun DiagramGraph.node(id: String): DiagramNode =
        nodeById(DiagramNodeId(id)) ?: error("node $id missing")

    private fun centerDistance(first: DiagramNode, second: DiagramNode): Double =
        hypot(
            first.bounds.centerX - second.bounds.centerX,
            first.bounds.centerY - second.bounds.centerY,
        )

    private fun assertNoOverlaps(graph: DiagramGraph) {
        val nodes = graph.nodes
        for (first in nodes.indices) {
            for (second in first + 1 until nodes.size) {
                assertTrue(
                    !nodes[first].bounds.intersects(nodes[second].bounds),
                    "${nodes[first].id.value} overlaps ${nodes[second].id.value}",
                )
            }
        }
    }

    private fun sixCycle(): DiagramGraph = diagramGraph {
        val ids = (0 until 6).map { node("n$it") }
        for (index in ids.indices) {
            edge("e$index", ids[index], ids[(index + 1) % ids.size])
        }
    }

    private fun denseEr(): DiagramGraph = diagramGraph {
        val order = node("order", width = 160.0, height = 90.0)
        val customer = node("customer", width = 150.0, height = 80.0)
        val product = node("product", width = 140.0, height = 100.0)
        val invoice = node("invoice", width = 130.0, height = 70.0)
        val relation = DiagramRelation.EntityRelation()
        edge("r1", customer, order, relation)
        edge("r2", order, product, relation)
        edge("r3", customer, invoice, relation)
        edge("r4", order, invoice, relation)
        edge("r5", product, invoice, relation)
    }

    @Test
    fun cycleSpreadsRound() {
        val laid = stressLayout(sixCycle())
        assertNoOverlaps(laid)
        // Graph-theoretic structure survives: opposite nodes of the 6-cycle sit farther
        // apart than adjacent ones (a false hierarchy would stack them arbitrarily).
        val adjacent = centerDistance(laid.node("n0"), laid.node("n1"))
        val opposite = centerDistance(laid.node("n0"), laid.node("n3"))
        assertTrue(
            opposite > adjacent * 1.5,
            "opposite pair ($opposite) must be well beyond adjacent pair ($adjacent)",
        )
    }

    @Test
    fun layoutIsDeterministicAndInteger() {
        val graph = denseEr()
        val first = stressLayout(graph)
        val second = stressLayout(graph)
        assertEquals(first, second, "stress layout must be run-twice equal")
        for (node in first.nodes) {
            assertEquals(node.x, kotlin.math.round(node.x), "x of ${node.id.value} on integer grid")
            assertEquals(node.y, kotlin.math.round(node.y), "y of ${node.id.value} on integer grid")
        }
    }

    @Test
    fun denseErHasNoOverlapsAndKeepsClearance() {
        val laid = stressLayout(denseEr())
        assertNoOverlaps(laid)
        val nodes = laid.nodes
        for (first in nodes.indices) {
            for (second in first + 1 until nodes.size) {
                val a = nodes[first].bounds
                val b = nodes[second].bounds
                val xClear = maxOf(a.left - b.right, b.left - a.right)
                val yClear = maxOf(a.top - b.bottom, b.top - a.bottom)
                assertTrue(
                    maxOf(xClear, yClear) >= 39.0,
                    "${nodes[first].id.value}/${nodes[second].id.value} must keep ~nodeGap clearance",
                )
            }
        }
    }

    @Test
    fun spreadsWiderThanTall() {
        // PCA alignment lands the principal axis horizontally: ER reads left-to-right.
        val laid = stressLayout(denseEr())
        val width = laid.nodes.maxOf { it.bounds.right } - laid.nodes.minOf { it.bounds.left }
        val height = laid.nodes.maxOf { it.bounds.bottom } - laid.nodes.minOf { it.bounds.top }
        assertTrue(width >= height, "layout must not come out portrait (w=$width, h=$height)")
    }

    @Test
    fun rootScopeKeepsOriginalTopLeft() {
        val graph = diagramGraph {
            val a = node("a", x = 250.0, y = 130.0)
            val b = node("b", x = 400.0, y = 700.0)
            val c = node("c", x = 10.0, y = 300.0)
            edge("a-b", a, b)
            edge("b-c", b, c)
            edge("c-a", c, a)
        }
        val laid = stressLayout(graph)
        assertEquals(10.0, laid.nodes.minOf { it.x }, "keeps the previous bounding-box left")
        assertEquals(130.0, laid.nodes.minOf { it.y }, "keeps the previous bounding-box top")
    }

    @Test
    fun disconnectedComponentsPackWithoutOverlap() {
        val graph = diagramGraph {
            val a = node("a")
            val b = node("b")
            val c = node("c")
            edge("a-b", a, b)
            edge("b-c", b, c)
            edge("c-a", c, a)
            node("stray", x = 5000.0, y = 5000.0)
        }
        val laid = stressLayout(graph)
        assertNoOverlaps(laid)
        val right = laid.nodes.maxOf { it.bounds.right }
        val bottom = laid.nodes.maxOf { it.bounds.bottom }
        assertTrue(right < 2000.0 && bottom < 2000.0, "stray must pack near the main component")
    }

    @Test
    fun containerChildrenLaidOutInsideParent() {
        val graph = diagramGraph {
            val parent = node(
                "p",
                x = 100.0,
                y = 50.0,
                width = 400.0,
                height = 300.0,
                payload = io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload.ContainerNode(),
            )
            val first = node("x", x = 900.0, y = 900.0, parentId = parent)
            val second = node("y", x = 950.0, y = 950.0, parentId = parent)
            val third = node("z", x = 970.0, y = 970.0, parentId = parent)
            edge("x-y", first, second)
            edge("y-z", second, third)
            edge("z-x", third, first)
        }
        val laid = stressLayout(graph)
        val parent = laid.node("p")
        for (childId in listOf("x", "y", "z")) {
            val child = laid.node(childId)
            assertTrue(
                child.x >= parent.x && child.y >= parent.y,
                "child $childId starts inside the parent's padded origin",
            )
        }
    }
}
