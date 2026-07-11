package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class DiagramIntrinsicsTest {

    private val graph = DiagramGraph(
        nodes = listOf(
            DiagramNode(id = DiagramNodeId("a"), x = 40.0, y = 24.0, width = 180.0, height = 120.0),
            DiagramNode(id = DiagramNodeId("b"), x = 320.0, y = 60.0, width = 160.0, height = 90.0),
        ),
        edges = listOf(
            DiagramEdge(
                id = DiagramEdgeId("e"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                target = DiagramEndpoint.FreePoint(500.0, 30.0),
                waypoints = listOf(DiagramPoint(250.0, 200.0)),
            ),
        ),
    )

    private fun diagramLeaf(graph: DiagramGraph, size: DesignSize = DesignSize()): ResolvedNode =
        ResolvedNode(id = "d", sourceId = "d", type = "diagram", name = "", diagram = graph, size = size)

    @Test
    fun diagramLeafHugsTheGraphBoundingBox() {
        val engine = DesignLayoutEngine()
        val node = diagramLeaf(graph)
        // max right: free endpoint x=500; max bottom: waypoint y=200.
        assertEquals(500.0, engine.naturalWidth(node))
        assertEquals(200.0, engine.naturalHeight(node, 500.0))
    }

    @Test
    fun authoredFixedSizeWinsOverTheGraphBoundingBox() {
        val engine = DesignLayoutEngine()
        val node = diagramLeaf(graph, size = DesignSize(width = 640.0, height = 360.0))
        assertEquals(640.0, engine.naturalWidth(node))
        assertEquals(360.0, engine.naturalHeight(node, 640.0))
    }

    @Test
    fun emptyGraphFallsBackToAuthoredSize() {
        val engine = DesignLayoutEngine()
        val node = diagramLeaf(DiagramGraph.Empty, size = DesignSize(width = 200.0, height = 100.0))
        assertEquals(200.0, engine.naturalWidth(node))
        assertEquals(100.0, engine.naturalHeight(node, 200.0))
    }
}
