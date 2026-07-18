package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipboardOpsTest {

    private fun graphWithPair() = diagramGraph {
        val a = node(
            "a",
            x = 0.0,
            y = 0.0,
            width = 100.0,
            height = 60.0,
            ports = listOf(DiagramPort(DiagramPortId("p"), DiagramPortAnchor.SideOffset(DiagramNodeSide.RIGHT, 0.5))),
        )
        val b = node("b", x = 200.0, y = 0.0, width = 100.0, height = 60.0)
        node("outside", x = 400.0, y = 0.0, width = 100.0, height = 60.0)
        edge(
            "inner",
            source = DiagramEndpoint.FixedPort(a, DiagramPortId("p")),
            target = DiagramEndpoint.FloatingAnchor(b),
            waypoints = listOf(DiagramPoint(150.0, 30.0)),
        )
        edge("outgoing", b, DiagramNodeId("outside"))
    }

    @Test
    fun pasteCopiesNodesAndInnerEdgesWithFreshIdsAndOffset() {
        val graph = graphWithPair()
        val clipNodes = listOf(graph.nodeById(DiagramNodeId("a"))!!, graph.nodeById(DiagramNodeId("b"))!!)
        val clipEdges = listOf(graph.edgeById(DiagramEdgeId("inner"))!!)
        val pasted = graph.pasteElements(
            nodes = clipNodes,
            edges = clipEdges,
            nodeIds = mapOf(
                DiagramNodeId("a") to DiagramNodeId("node-1"),
                DiagramNodeId("b") to DiagramNodeId("node-2"),
            ),
            edgeIds = mapOf(DiagramEdgeId("inner") to DiagramEdgeId("edge-1")),
            offsetX = 24.0,
            offsetY = 24.0,
        )

        val copyA = assertNotNull(pasted.nodeById(DiagramNodeId("node-1")))
        assertEquals(24.0, copyA.x)
        assertEquals(24.0, copyA.y)
        // The copy is self-contained: the authored port rode along.
        assertNotNull(copyA.portById(DiagramPortId("p")))

        val copyEdge = assertNotNull(pasted.edgeById(DiagramEdgeId("edge-1")))
        assertEquals(
            DiagramEndpoint.FixedPort(DiagramNodeId("node-1"), DiagramPortId("p")),
            copyEdge.source,
        )
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("node-2")), copyEdge.target)
        assertEquals(listOf(DiagramPoint(174.0, 54.0)), copyEdge.waypoints)
        // Originals untouched.
        assertEquals(0.0, pasted.nodeById(DiagramNodeId("a"))!!.x)
    }

    @Test
    fun anEdgeLeavingTheCopiedSetIsDropped() {
        val graph = graphWithPair()
        val pasted = graph.pasteElements(
            nodes = listOf(graph.nodeById(DiagramNodeId("b"))!!),
            edges = listOf(graph.edgeById(DiagramEdgeId("outgoing"))!!),
            nodeIds = mapOf(DiagramNodeId("b") to DiagramNodeId("node-1")),
            edgeIds = mapOf(DiagramEdgeId("outgoing") to DiagramEdgeId("edge-1")),
        )
        assertNotNull(pasted.nodeById(DiagramNodeId("node-1")))
        // The copy must not rewire the ORIGINAL "outside" node.
        assertNull(pasted.edgeById(DiagramEdgeId("edge-1")))
    }

    @Test
    fun aTakenIdNeverReplacesAnExistingElement() {
        val graph = graphWithPair()
        val pasted = graph.pasteElements(
            nodes = listOf(graph.nodeById(DiagramNodeId("a"))!!),
            edges = emptyList(),
            nodeIds = mapOf(DiagramNodeId("a") to DiagramNodeId("outside")),
            edgeIds = emptyMap(),
        )
        // "outside" already exists: the paste is dropped, the original survives.
        assertEquals(400.0, pasted.nodeById(DiagramNodeId("outside"))!!.x)
        assertTrue(pasted.nodes.size == graph.nodes.size)
    }
}
