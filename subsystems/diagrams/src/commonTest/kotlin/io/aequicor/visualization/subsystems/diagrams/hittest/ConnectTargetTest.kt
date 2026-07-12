package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectTargetTest {

    private val a = DiagramNodeId("a")

    @Test
    fun connectionPortsAreTheFourSideMidpointsWhenNoneDeclared() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        val ids = graph.nodeById(a)!!.connectionPorts().map { it.id.value }.toSet()
        assertEquals(setOf("top", "right", "bottom", "left"), ids)
    }

    @Test
    fun resolveSnapsToNearestSideMidpointWithinRadius() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        // Pointer just inside the right edge near its midpoint (100, 30).
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(-50.0, 30.0),
            pointer = DiagramPoint(98.0, 31.0),
        )
        assertTrue(target is ConnectTarget.Port)
        target as ConnectTarget.Port
        assertEquals("right", target.port.id.value)
        assertEquals(DiagramPoint(100.0, 30.0), target.snapPoint)
    }

    @Test
    fun resolveFloatsOnPerimeterFacingSourceWhenOverBodyAwayFromPorts() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        // Node center (50, 30): every side midpoint is > snap radius away, so floating.
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(-100.0, 30.0),
            pointer = DiagramPoint(50.0, 30.0),
        )
        assertTrue(target is ConnectTarget.Floating)
        target as ConnectTarget.Floating
        assertEquals(a, target.nodeId)
        // Source is to the left, so the perimeter crossing is the left-edge midpoint.
        assertEquals(DiagramPoint(0.0, 30.0), target.snapPoint)
    }

    @Test
    fun resolveIsFreeWhenNoNodeUnderPointer() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(0.0, 0.0),
            pointer = DiagramPoint(500.0, 500.0),
        )
        assertEquals(ConnectTarget.Free(DiagramPoint(500.0, 500.0)), target)
    }

    @Test
    fun resolveExcludesTheSourceNode() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(0.0, 0.0),
            pointer = DiagramPoint(50.0, 30.0),
            excludeNodeId = a,
        )
        assertTrue(target is ConnectTarget.Free)
    }
}
