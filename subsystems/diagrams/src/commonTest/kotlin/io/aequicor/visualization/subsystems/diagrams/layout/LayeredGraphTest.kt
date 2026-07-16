package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayeredGraphTest {

    private fun DiagramGraph.node(id: String): DiagramNode =
        nodeById(DiagramNodeId(id)) ?: error("node $id missing")

    private fun DiagramGraph.componentLayout(
        config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
    ): ComponentLayout = layoutComponent(
        nodesById = nodes.associateBy { it.id },
        adjacency = scopeAdjacency(null),
        config = config,
    )

    @Test
    fun longEdgeGetsOneDummyPerIntermediateRank() {
        val members = listOf("a", "b", "c", "d").map(::DiagramNodeId)
        val links = listOf(
            ScopeLink(DiagramNodeId("a"), DiagramNodeId("b")),
            ScopeLink(DiagramNodeId("b"), DiagramNodeId("c")),
            ScopeLink(DiagramNodeId("c"), DiagramNodeId("d")),
            ScopeLink(DiagramNodeId("a"), DiagramNodeId("d")),
        )
        val layers = assignLongestPathLayers(members, links.groupBy({ it.to }, { it.from }))
        assertEquals(0, layers.getValue(DiagramNodeId("a")))
        assertEquals(3, layers.getValue(DiagramNodeId("d")))

        val layered = buildLayeredGraph(members, links, layers)
        val dummies = layered.slots.filter { it.isDummy }
        assertEquals(2, dummies.size, "edge spanning 0->3 needs dummies at ranks 1 and 2")
        assertEquals(1, layered.layers[1].count { it.isDummy })
        assertEquals(1, layered.layers[2].count { it.isDummy })

        // The chain is wired through both dummies: a -> d1 -> d2 -> d.
        val first = layered.layers[1].single { it.isDummy }.key
        val second = layered.layers[2].single { it.isDummy }.key
        assertTrue(
            first in layered.down.getValue(realKey(DiagramNodeId("a"))),
            "layer-1 dummy hangs off the source",
        )
        assertEquals(listOf(second), layered.down.getValue(first))
        assertEquals(listOf(realKey(DiagramNodeId("d"))), layered.down.getValue(second))
    }

    @Test
    fun spaceContainingIdsNeverCollideDummyChains() {
        // Ids are arbitrary strings: "a b"->"c" and "a"->"b c" used to mint the same
        // space-joined dummy key and silently merge two unrelated long edges.
        val members = listOf("a", "a b", "b c", "c", "m1", "m2").map(::DiagramNodeId)
        fun link(from: String, to: String) = ScopeLink(DiagramNodeId(from), DiagramNodeId(to))
        val links = listOf(
            link("a", "m1"), link("m1", "b c"),
            link("a b", "m2"), link("m2", "c"),
            link("a", "b c"),
            link("a b", "c"),
        )
        val layers = assignLongestPathLayers(members, links.groupBy({ it.to }, { it.from }))
        val layered = buildLayeredGraph(members, links, layers)

        val dummies = layered.slots.filter { it.isDummy }
        assertEquals(2, dummies.size, "each long edge owns its own dummy")
        assertEquals(2, dummies.map { it.key }.toSet().size, "dummy keys must be distinct")
        // Each chain stays wired to its own endpoints.
        val ofLongA = layered.down.getValue(realKey(DiagramNodeId("a"))).single { key ->
            dummies.any { it.key == key }
        }
        assertEquals(listOf(realKey(DiagramNodeId("b c"))), layered.down.getValue(ofLongA))
        val ofLongAB = layered.down.getValue(realKey(DiagramNodeId("a b"))).single { key ->
            dummies.any { it.key == key }
        }
        assertEquals(listOf(realKey(DiagramNodeId("c"))), layered.down.getValue(ofLongAB))
    }

    @Test
    fun dummyReservesACorridorNoRealNodeOccupies() {
        val graph = diagramGraph {
            val a = node("a")
            val b1 = node("b1")
            val b2 = node("b2")
            val c = node("c")
            edge("a-b1", a, b1)
            edge("a-b2", a, b2)
            edge("b1-c", b1, c)
            edge("b2-c", b2, c)
            edge("a-c", a, c)
        }
        val config = DiagramLayoutConfig.Default
        val layout = graph.componentLayout(config)

        val middleLayer = layout.graph.layers[1]
        assertEquals(3, middleLayer.size, "layer 1 holds b1, b2 and the a->c dummy")
        val dummy = middleLayer.single { it.isDummy }
        val dummyX = layout.positions.getValue(dummy.key).x
        val corridor = dummyX..(dummyX + config.dummySize)

        for (slot in middleLayer) {
            val id = slot.realId ?: continue
            val node = graph.nodeById(id)!!
            val x = layout.positions.getValue(slot.key).x
            assertTrue(
                x >= corridor.endInclusive || x + node.width <= corridor.start,
                "${id.value} sits in the long edge's corridor $corridor",
            )
        }

        // The corridor widens the layer by its extent plus one more gap.
        val b1 = graph.nodeById(DiagramNodeId("b1"))!!
        val b2 = graph.nodeById(DiagramNodeId("b2"))!!
        val expectedWidth = b1.width + b2.width + config.dummySize + config.nodeGap * 2
        val xs = middleLayer.map { layout.positions.getValue(it.key).x }
        val widths = middleLayer.map { slot ->
            slot.realId?.let { graph.nodeById(it)!!.width } ?: config.dummySize
        }
        val span = (xs.indices).maxOf { xs[it] + widths[it] } - xs.min()
        assertEquals(expectedWidth, span, 1e-9)
    }

    @Test
    fun dummyFollowsItsNeighborsInOrdering() {
        // Two fan-outs from m and z; z also owns a long edge z->t (dummy at layer 1).
        // The dummy must order beside z's other children, not sit leftmost by key.
        val graph = diagramGraph {
            val m = node("m")
            val z = node("z")
            val x1 = node("x1")
            val x2 = node("x2")
            val v = node("v")
            val t = node("t")
            edge("m-x1", m, x1)
            edge("m-x2", m, x2)
            edge("x1-t", x1, t)
            edge("x2-t", x2, t)
            edge("z-v", z, v)
            edge("v-t", v, t)
            edge("z-t", z, t)
        }
        val layout = graph.componentLayout()
        val middle = layout.graph.layers[1]
        val keys = middle.map { it.key }
        val dummyIndex = middle.indexOfFirst { it.isDummy }
        fun realIndex(id: String) = middle.indexOfFirst { it.realId?.value == id }
        assertTrue(dummyIndex > 0, "dummy must not be stuck at the layer start: $keys")
        assertTrue(
            dummyIndex > realIndex("x1") && dummyIndex > realIndex("x2"),
            "dummy of z->t belongs on z's side of the layer: $keys",
        )
    }

    @Test
    fun layoutWithDummiesIsDeterministic() {
        val graph = diagramGraph {
            val a = node("a")
            val b1 = node("b1")
            val b2 = node("b2")
            val c = node("c")
            edge("a-b1", a, b1)
            edge("a-b2", a, b2)
            edge("b1-c", b1, c)
            edge("b2-c", b2, c)
            edge("a-c", a, c)
        }
        assertEquals(graph.componentLayout().positions, graph.componentLayout().positions)
        assertEquals(layeredLayout(graph), layeredLayout(graph))
    }

    @Test
    fun packedNeighborComponentStaysClearOfTrailingDummyCorridor() {
        // Component 1's widest layer ends in the z->t corridor (trailing dummy); the
        // isolated q must be packed past the corridor, not into it.
        val graph = diagramGraph {
            val m = node("m")
            val z = node("z")
            val x1 = node("x1")
            val x2 = node("x2")
            val t = node("t")
            edge("m-x1", m, x1)
            edge("m-x2", m, x2)
            edge("x1-t", x1, t)
            edge("x2-t", x2, t)
            edge("z-t", z, t)
            node("q", x = 500.0, y = 500.0)
        }
        val laid = layeredLayout(graph)
        // Layer 1 is the widest: x1 (0..120), x2 (160..280), corridor (320..332).
        // The slot-level extent is 332; q packs at 332 + nodeGap*2, clear of the lane.
        assertEquals(412.0, laid.node("q").x)
    }

    @Test
    fun realNodesNeverOverlapWithLongEdgesPresent() {
        val graph = diagramGraph {
            val a = node("a")
            val b1 = node("b1")
            val b2 = node("b2")
            val c = node("c")
            val d = node("d")
            edge("a-b1", a, b1)
            edge("a-b2", a, b2)
            edge("b1-c", b1, c)
            edge("b2-c", b2, c)
            edge("c-d", c, d)
            edge("a-d", a, d)
            edge("a-c", a, c)
        }
        val laid = layeredLayout(graph)
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
}
