package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AutoLayoutTest {

    private fun DiagramGraph.node(id: String): DiagramNode =
        nodeById(DiagramNodeId(id)) ?: error("node $id missing")

    private fun diamond(): DiagramGraph = diagramGraph {
        val a = node("a")
        val b = node("b")
        val c = node("c")
        val d = node("d")
        edge("a-b", a, b)
        edge("a-c", a, c)
        edge("b-d", b, d)
        edge("c-d", c, d)
    }

    private fun cycle(): DiagramGraph = diagramGraph {
        val a = node("a")
        val b = node("b")
        val c = node("c")
        edge("a-b", a, b)
        edge("b-c", b, c)
        edge("c-a", c, a)
    }

    private fun fanTree(): DiagramGraph = diagramGraph {
        val r = node("r")
        val c1 = node("c1")
        val c2 = node("c2")
        val c3 = node("c3")
        edge("r-c1", r, c1)
        edge("r-c2", r, c2)
        edge("r-c3", r, c3)
    }

    // --- layered: diamond DAG ---

    @Test
    fun layeredLaysDiamondInThreeLayers() {
        val laid = layeredLayout(diamond())
        val a = laid.node("a")
        val b = laid.node("b")
        val c = laid.node("c")
        val d = laid.node("d")

        assertEquals(3, setOf(a.y, b.y, c.y, d.y).size, "diamond must occupy 3 layers")
        assertEquals(b.y, c.y, "b and c share the middle layer")
        assertTrue(a.y < b.y && b.y < d.y, "layers flow top-down")
        // Even gaps: node height 60 + layerGap 80.
        assertEquals(140.0, b.y - a.y)
        assertEquals(140.0, d.y - b.y)
        // Middle layer spreads with nodeGap 40 and no overlap.
        assertEquals(160.0, c.x - b.x)
        // Single-node layers are centered over the widest layer.
        assertEquals((b.bounds.centerX + c.bounds.centerX) / 2.0, a.bounds.centerX)
        assertEquals(a.bounds.centerX, d.bounds.centerX)
    }

    @Test
    fun layeredLeftRightFlowsAlongX() {
        val laid = layeredLayout(
            diamond(),
            DiagramLayoutConfig(direction = LayoutDirection.LEFT_RIGHT),
        )
        val a = laid.node("a")
        val b = laid.node("b")
        val c = laid.node("c")
        val d = laid.node("d")

        assertEquals(b.x, c.x)
        assertTrue(a.x < b.x && b.x < d.x, "layers flow left-right")
        assertEquals(200.0, b.x - a.x, "node width 120 + layerGap 80")
        assertEquals((b.bounds.centerY + c.bounds.centerY) / 2.0, a.bounds.centerY)
    }

    // --- tree ---

    @Test
    fun treeCentersParentOverChildren() {
        val laid = treeLayout(fanTree())
        val r = laid.node("r")
        val c1 = laid.node("c1")
        val c2 = laid.node("c2")
        val c3 = laid.node("c3")

        assertEquals((c1.bounds.centerX + c3.bounds.centerX) / 2.0, r.bounds.centerX)
        assertEquals(r.bounds.centerX, c2.bounds.centerX, "middle child sits under the root")
        assertTrue(r.y < c1.y, "root above children")
        assertEquals(setOf(c1.y), setOf(c1.y, c2.y, c3.y), "children share one level")
        assertEquals(160.0, c2.x - c1.x, "even sibling gaps (width 120 + nodeGap 40)")
        assertEquals(160.0, c3.x - c2.x)
    }

    @Test
    fun treeSeparatesForestRoots() {
        val graph = diagramGraph {
            val r1 = node("r1")
            val leaf = node("r1leaf")
            edge("e", r1, leaf)
            node("r2")
        }
        val laid = treeLayout(graph)
        val r1 = laid.node("r1")
        val r2 = laid.node("r2")
        assertEquals(r1.y, r2.y, "both roots on level 0")
        assertNotEquals(r1.x, r2.x, "roots do not overlap")
        assertTrue(!r1.bounds.intersects(r2.bounds))
    }

    // --- cycles must not hang ---

    @Test
    fun cycleDoesNotHangAndStillLayers() {
        val laid = layeredLayout(cycle())
        val a = laid.node("a")
        val b = laid.node("b")
        val c = laid.node("c")
        assertTrue(a.y < b.y && b.y < c.y, "back edge c→a is broken, chain layers remain")
    }

    @Test
    fun cycleDoesNotHangTreeLayout() {
        val laid = treeLayout(cycle())
        val a = laid.node("a")
        val b = laid.node("b")
        val c = laid.node("c")
        assertTrue(a.y < b.y && b.y < c.y, "cycle degrades to a chain")
    }

    // --- component packing ---

    @Test
    fun layeredPacksDisconnectedComponentsWithoutOverlap() {
        val graph = diagramGraph {
            val a = node("a")
            val b = node("b")
            val c = node("c")
            edge("a-b", a, b)
            edge("b-c", b, c)
            node("x", x = 999.0, y = 999.0)
            node("y", x = 500.0, y = -500.0)
        }
        val laid = layeredLayout(graph)
        val chain = listOf(laid.node("a"), laid.node("b"), laid.node("c"))
        val strays = listOf(laid.node("x"), laid.node("y"))

        // The chain still layers top-down with even gaps.
        assertTrue(chain[0].y < chain[1].y && chain[1].y < chain[2].y)
        assertEquals(140.0, chain[1].y - chain[0].y)

        // Strays no longer share layer 0's band centered against the chain: they pack
        // beside the chain's block, outside its bounding box, and never overlap anything.
        val chainRight = chain.maxOf { it.x + it.width }
        strays.forEach { stray ->
            assertTrue(stray.x >= chainRight, "stray ${stray.id.value} must pack beside the chain")
        }
        val all = laid.nodes
        for (first in all.indices) {
            for (second in first + 1 until all.size) {
                assertTrue(
                    !all[first].bounds.intersects(all[second].bounds),
                    "${all[first].id.value} overlaps ${all[second].id.value}",
                )
            }
        }
    }

    // --- determinism ---

    @Test
    fun layoutIsDeterministic() {
        val graph = diagramGraph {
            val a = node("a", x = 13.0, y = 7.0)
            val b = node("b", x = 300.0, y = 5.0, width = 80.0)
            val c = node("c", x = 40.0, y = 200.0, height = 90.0)
            val d = node("d", x = 500.0, y = 500.0)
            node("isolated", x = 999.0, y = 999.0)
            edge("a-b", a, b)
            edge("b-c", b, c)
            edge("c-a", c, a)
            edge("a-d", a, d)
        }
        assertEquals(autoLayout(graph), autoLayout(graph))
        assertEquals(layeredLayout(graph), layeredLayout(graph))
        assertEquals(treeLayout(graph), treeLayout(graph))
    }

    // --- auto kind selection ---

    @Test
    fun autoPicksTreeForForestAndLayeredOtherwise() {
        val forest = fanTree()
        assertEquals(treeLayout(forest), autoLayout(forest))

        val dag = diamond()
        assertEquals(layeredLayout(dag), autoLayout(dag))

        val cyclic = cycle()
        assertEquals(layeredLayout(cyclic), autoLayout(cyclic))
    }

    @Test
    fun explicitKindForcesAlgorithm() {
        val forest = fanTree()
        assertEquals(layeredLayout(forest), autoLayout(forest, LayoutKind.LAYERED))
        val dag = diamond()
        assertEquals(treeLayout(dag), autoLayout(dag, LayoutKind.TREE))
    }

    @Test
    fun emptyGraphIsUntouched() {
        assertEquals(DiagramGraph.Empty, autoLayout(DiagramGraph.Empty))
    }

    // --- containers ---

    @Test
    fun containerChildrenAreLaidOutInsideParent() {
        val graph = diagramGraph {
            val p = node(
                "p",
                x = 100.0,
                y = 50.0,
                width = 400.0,
                height = 300.0,
                payload = DiagramNodePayload.ContainerNode(),
            )
            val x = node("x", x = 900.0, y = 900.0, parentId = p)
            val y = node("y", x = 950.0, y = 950.0, parentId = p)
            edge("x-y", x, y)
            val q = node("q", x = 700.0, y = 10.0)
            edge("p-q", p, q)
        }
        val laid = layeredLayout(graph)
        val p = laid.node("p")
        val x = laid.node("x")
        val y = laid.node("y")
        val q = laid.node("q")

        // Children start at the parent's padded origin and stack inside it.
        assertEquals(p.x + 24.0, x.x)
        assertEquals(p.y + 24.0, x.y)
        assertEquals(x.x, y.x)
        assertEquals(x.y + 60.0 + 80.0, y.y, "child layers use layerGap inside the parent")
        assertTrue(p.bounds.contains(x.bounds.center), "children stay within the container")
        assertTrue(p.bounds.contains(y.bounds.center))

        // Root scope layers p above q (edge p→q), children follow the parent.
        assertTrue(p.y < q.y)
    }

    @Test
    fun rootScopeKeepsOriginalTopLeftOrigin() {
        val graph = diagramGraph {
            val a = node("a", x = 250.0, y = 130.0)
            val b = node("b", x = 400.0, y = 700.0)
            edge("a-b", a, b)
        }
        val laid = layeredLayout(graph)
        val minX = laid.nodes.minOf { it.x }
        val minY = laid.nodes.minOf { it.y }
        assertEquals(250.0, minX, "layout keeps the previous bounding-box left")
        assertEquals(130.0, minY, "layout keeps the previous bounding-box top")
    }
}
