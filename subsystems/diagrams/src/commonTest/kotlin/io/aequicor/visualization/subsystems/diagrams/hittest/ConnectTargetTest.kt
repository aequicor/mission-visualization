package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.geometry.containsPoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectTargetTest {

    private val a = DiagramNodeId("a")

    @Test
    fun connectionPortsAreTheDrawIoGridWhenNoneDeclared() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        val node = graph.nodeById(a)!!
        val ports = node.connectionPorts()
        val ids = ports.map { it.id.value }

        // 16 unique connection points: 4 mid-sides + 8 side quarter-points + 4 corners.
        assertEquals(16, ids.size, "grid should offer 16 points")
        assertEquals(16, ids.toSet().size, "all 16 ids must be unique")
        assertEquals(
            setOf(
                "top", "right", "bottom", "left",
                "top-q1", "top-q3", "right-q1", "right-q3",
                "bottom-q1", "bottom-q3", "left-q1", "left-q3",
                "top-left", "top-right", "bottom-right", "bottom-left",
            ),
            ids.toSet(),
        )
        // The canonical mid-sides are still present (routing + existing SLM depend on them).
        assertTrue(setOf("top", "right", "bottom", "left").all { it in ids })

        fun position(id: String): DiagramPoint = node.portPosition(ports.first { it.id.value == id })
        // Corners resolve to the box corners.
        assertEquals(DiagramPoint(0.0, 0.0), position("top-left"))
        assertEquals(DiagramPoint(100.0, 0.0), position("top-right"))
        assertEquals(DiagramPoint(100.0, 60.0), position("bottom-right"))
        assertEquals(DiagramPoint(0.0, 60.0), position("bottom-left"))
        // Quarter-points resolve along their side (25% / 75%).
        assertEquals(DiagramPoint(25.0, 0.0), position("top-q1"))
        assertEquals(DiagramPoint(75.0, 0.0), position("top-q3"))
        assertEquals(DiagramPoint(100.0, 15.0), position("right-q1"))
        assertEquals(DiagramPoint(0.0, 45.0), position("left-q3"))
        // Mid-sides unchanged.
        assertEquals(DiagramPoint(50.0, 0.0), position("top"))
        assertEquals(DiagramPoint(100.0, 30.0), position("right"))
    }

    @Test
    fun virtualConnectionGridIsProjectedOntoShapedOutline() {
        val graph = diagramGraph {
            node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS),
            )
        }
        val node = graph.nodeById(a)!!
        val ports = node.connectionPorts()

        fun position(id: String): DiagramPoint = node.portPosition(ports.first { it.id.value == id })
        assertEquals(DiagramPoint(25.0, 15.0), position("top-left"))
        assertEquals(DiagramPoint(50.0, 0.0), position("top"))
        assertTrue(ports.all { node.containsPoint(node.portPosition(it)) })
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
    fun resolveSnapsToNearestCornerWithinRadius() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        // Pointer just inside the top-left corner (0, 0) and well away from any mid-side.
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(-50.0, -50.0),
            pointer = DiagramPoint(3.0, 4.0),
        )
        assertTrue(target is ConnectTarget.Port)
        target as ConnectTarget.Port
        assertEquals(a, target.nodeId)
        assertEquals("top-left", target.port.id.value)
        assertEquals(DiagramPoint(0.0, 0.0), target.snapPoint)
    }

    @Test
    fun resolveKeepsPortMagneticJustOutsideNodeBounds() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        // The pointer has crossed the top edge, but is still within the port's snap halo.
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(25.0, -100.0),
            pointer = DiagramPoint(25.0, -6.0),
        )

        assertTrue(target is ConnectTarget.Port)
        target as ConnectTarget.Port
        assertEquals(a, target.nodeId)
        assertEquals("top-q1", target.port.id.value)
        assertEquals(DiagramPoint(25.0, 0.0), target.snapPoint)
    }

    @Test
    fun resolveDoesNotLetCoveredPortStealForegroundNodeBody() {
        val foreground = DiagramNodeId("foreground")
        val graph = diagramGraph {
            node("background", x = 0.0, y = 0.0, width = 100.0, height = 60.0)
            node("foreground", x = 80.0, y = -10.0, width = 100.0, height = 100.0)
        }
        // This is the background node's right midpoint, but it lies inside the topmost node.
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(250.0, 60.0),
            pointer = DiagramPoint(100.0, 30.0),
        )

        assertTrue(target is ConnectTarget.Floating)
        target as ConnectTarget.Floating
        assertEquals(foreground, target.nodeId)
    }

    @Test
    fun resolveIsFreeOutsidePortMagneticRadius() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 60.0) }
        val pointer = DiagramPoint(25.0, -11.0)
        val target = graph.resolveConnectTarget(
            from = DiagramPoint(25.0, -100.0),
            pointer = pointer,
        )

        assertEquals(ConnectTarget.Free(pointer), target)
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
    fun resolveDoesNotTreatRhombusBoundingCornerAsItsBody() {
        val graph = diagramGraph {
            node(
                "a",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 60.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS),
            )
        }
        val pointer = DiagramPoint(4.0, 4.0)

        assertEquals(
            ConnectTarget.Free(pointer),
            graph.resolveConnectTarget(
                from = DiagramPoint(-100.0, 30.0),
                pointer = pointer,
            ),
        )
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
