package io.aequicor.visualization.editor.ui

import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramHit
import io.aequicor.visualization.subsystems.diagrams.hittest.DiagramResizeHandle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramEdgeEnd
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagramPortPressResolutionTest {

    private val node = DiagramNodeId("node")
    private val port = DiagramHit.Port(node, DiagramPortId("bottom"))

    @Test
    fun selectedNodeKeepsResizeHandleInsteadOfConnectionPort() {
        val resize = DiagramHit.ResizeHandle(node, DiagramResizeHandle.BOTTOM)
        val connectionPort = diagramConnectionPortForSelection(port, setOf(node))

        assertEquals(resize, preferHighlightedDiagramPort(resize, connectionPort))
    }

    @Test
    fun selectedNodeDoesNotOfferConnectionPort() {
        assertNull(diagramConnectionPortForSelection(port, setOf(node)))
    }

    @Test
    fun unselectedHoveredNodeOffersConnectionPort() {
        assertEquals(port, diagramConnectionPortForSelection(port, emptySet()))
    }

    @Test
    fun greenPortBeatsSameNodeBody() {
        assertEquals(port, preferHighlightedDiagramPort(DiagramHit.Node(node), port))
    }

    @Test
    fun existingEdgeEndpointKeepsPriorityOverGreenPort() {
        val endpoint = DiagramHit.EndpointHandle(DiagramEdgeId("edge"), DiagramEdgeEnd.TARGET)

        assertEquals(endpoint, preferHighlightedDiagramPort(endpoint, port))
    }

    @Test
    fun otherNodesResizeHandleKeepsPriority() {
        val resize = DiagramHit.ResizeHandle(DiagramNodeId("foreground"), DiagramResizeHandle.BOTTOM)

        assertEquals(resize, preferHighlightedDiagramPort(resize, port))
    }

    @Test
    fun hoverFindsPortFromJustOutsideItsNode() {
        val graph = diagramGraph { node("node", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }

        assertEquals(
            DiagramHit.Port(node, DiagramPortId("top-q1")),
            hoverDiagramPortAt(graph, DiagramPoint(25.0, -3.0), tolerance = 4.0),
        )
    }

    @Test
    fun foregroundBodyBlocksCoveredNodesGreenPort() {
        val graph = diagramGraph {
            node("background", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("foreground", x = 80.0, y = -10.0, width = 100.0, height = 100.0)
        }

        assertNull(hoverDiagramPortAt(graph, DiagramPoint(100.0, 50.0), tolerance = 5.0))
    }
}
