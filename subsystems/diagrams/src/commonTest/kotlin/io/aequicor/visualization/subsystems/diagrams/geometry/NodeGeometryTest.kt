package io.aequicor.visualization.subsystems.diagrams.geometry

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeGeometryTest {

    private fun node(
        payload: DiagramNodePayload = DiagramNodePayload.BasicShape(),
        ports: List<DiagramPort> = emptyList(),
    ): DiagramNode = DiagramNode(
        id = DiagramNodeId("n"),
        x = 0.0,
        y = 0.0,
        width = 100.0,
        height = 60.0,
        payload = payload,
        ports = ports,
    )

    private fun assertPointEquals(expected: DiagramPoint, actual: DiagramPoint) {
        assertTrue(
            abs(expected.x - actual.x) < 1e-9 && abs(expected.y - actual.y) < 1e-9,
            "expected $expected, got $actual",
        )
    }

    @Test
    fun rectanglePerimeterIntersectionHitsSideTowardTarget() {
        val intersection = perimeterIntersection(node(), DiagramPoint(350.0, 30.0))
        assertPointEquals(DiagramPoint(100.0, 30.0), intersection)
    }

    @Test
    fun rectanglePerimeterIntersectionDiagonal() {
        // Toward the bottom-right corner direction: hits the right edge first.
        val intersection = perimeterIntersection(node(), DiagramPoint(250.0, 90.0))
        assertPointEquals(DiagramPoint(100.0, 45.0), intersection)
    }

    @Test
    fun ellipsePerimeterIntersectionOnAxes() {
        val ellipse = node(DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE))
        assertPointEquals(
            DiagramPoint(100.0, 30.0),
            perimeterIntersection(ellipse, DiagramPoint(200.0, 30.0)),
        )
        assertPointEquals(
            DiagramPoint(50.0, 60.0),
            perimeterIntersection(ellipse, DiagramPoint(50.0, 200.0)),
        )
    }

    @Test
    fun rhombusPerimeterIntersection() {
        val rhombus = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS))
        assertPointEquals(
            DiagramPoint(100.0, 30.0),
            perimeterIntersection(rhombus, DiagramPoint(200.0, 30.0)),
        )
        // Direction (100, 60) from center (50, 30): |dx|/50 + |dy|/30 = 1 at t = 1/4.
        assertPointEquals(
            DiagramPoint(75.0, 45.0),
            perimeterIntersection(rhombus, DiagramPoint(150.0, 90.0)),
        )
    }

    @Test
    fun towardCenterReturnsCenter() {
        assertPointEquals(DiagramPoint(50.0, 30.0), perimeterIntersection(node(), DiagramPoint(50.0, 30.0)))
    }

    @Test
    fun perimeterKindFollowsPayload() {
        assertEquals(DiagramPerimeterKind.RECTANGLE, node().perimeterKind())
        assertEquals(
            DiagramPerimeterKind.RECTANGLE,
            node(
                TableNode(rows = listOf(TableRow()), columns = listOf(TableColumn())),
            ).perimeterKind(),
        )
        assertEquals(DiagramPerimeterKind.ELLIPSE, node(UmlUseCaseNode("Login")).perimeterKind())
        assertEquals(
            DiagramPerimeterKind.RHOMBUS,
            node(DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION)).perimeterKind(),
        )
        assertEquals(
            DiagramPerimeterKind.OUTLINE,
            node(DiagramNodePayload.BasicShape(DiagramShapeKind.TRIANGLE)).perimeterKind(),
        )
    }

    @Test
    fun anchorPointResolvesFixedPorts() {
        val withPorts = node(ports = DiagramPort.standardPorts())
        assertPointEquals(
            DiagramPoint(50.0, 0.0),
            anchorPoint(withPorts, DiagramPort.side(DiagramNodeSide.TOP)),
        )
        assertNotNull(anchorPoint(withPorts, DiagramPortId("left"))).also {
            assertPointEquals(DiagramPoint(0.0, 30.0), it)
        }
        assertNull(anchorPoint(withPorts, DiagramPortId("missing")))
    }

    @Test
    fun shapedNodePortsResolveOnTheVisibleContour() {
        val rhombus = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS))
        val leftQuarter = DiagramPort(
            DiagramPortId("left-q1"),
            DiagramPortAnchor.SideOffset(DiagramNodeSide.LEFT, 0.25),
        )
        val legacyCorner = DiagramPort(
            DiagramPortId("top-left"),
            DiagramPortAnchor.RelativePoint(0.0, 0.0),
        )

        assertPointEquals(DiagramPoint(25.0, 15.0), anchorPoint(rhombus, leftQuarter))
        assertPointEquals(DiagramPoint(25.0, 15.0), anchorPoint(rhombus, legacyCorner))
    }

    @Test
    fun perimeterSidePicksNearestSide() {
        assertEquals(DiagramNodeSide.RIGHT, perimeterSide(node(), DiagramPoint(100.0, 30.0)))
        assertEquals(DiagramNodeSide.TOP, perimeterSide(node(), DiagramPoint(50.0, 0.0)))
        assertEquals(DiagramNodeSide.BOTTOM, perimeterSide(node(), DiagramPoint(50.0, 60.0)))
        assertEquals(DiagramNodeSide.LEFT, perimeterSide(node(), DiagramPoint(0.0, 30.0)))
    }

    @Test
    fun rhombusOutlineIsClosedDiamond() {
        val outline = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS)).outlinePath()
        assertEquals(5, outline.segments.size)
        val move = outline.segments.first() as DiagramPathSegment.MoveTo
        assertPointEquals(DiagramPoint(50.0, 0.0), move.point)
        assertTrue(outline.segments.last() is DiagramPathSegment.Close)
    }

    @Test
    fun tableOutlineIsRectangle() {
        val outline = node(
            TableNode(rows = listOf(TableRow()), columns = listOf(TableColumn())),
        ).outlinePath()
        assertEquals(5, outline.segments.size)
        assertTrue(outline.segments.drop(1).dropLast(1).all { it is DiagramPathSegment.LineTo })
    }

    @Test
    fun ellipseOutlineUsesArcs() {
        val outline = node(DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE)).outlinePath()
        assertTrue(outline.segments.count { it is DiagramPathSegment.ArcTo } == 2)
    }

    @Test
    fun nodeHitAreaFollowsTheRenderedOutline() {
        val rhombus = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS))
        val ellipse = node(DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE))
        val triangle = node(DiagramNodePayload.BasicShape(DiagramShapeKind.TRIANGLE))

        assertTrue(rhombus.containsPoint(DiagramPoint(50.0, 30.0)))
        assertFalse(rhombus.containsPoint(DiagramPoint(2.0, 2.0)))
        assertFalse(ellipse.containsPoint(DiagramPoint(2.0, 2.0)))
        assertFalse(triangle.containsPoint(DiagramPoint(2.0, 2.0)))
    }

    @Test
    fun outlineToleranceCreatesAHaloAroundTheActualShape() {
        val rhombus = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS))
        val nearTopLeftEdge = DiagramPoint(24.0, 15.0)

        assertFalse(rhombus.containsPoint(nearTopLeftEdge))
        assertTrue(rhombus.containsPoint(nearTopLeftEdge, outlineTolerance = 1.0))
        assertFalse(rhombus.containsPoint(DiagramPoint(2.0, 2.0), outlineTolerance = 4.0))
    }

    @Test
    fun marqueeIntersectionUsesTheActualNodeArea() {
        val rhombus = node(DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS))

        assertFalse(rhombus.intersectsOutline(DiagramRect(0.0, 0.0, 4.0, 4.0)))
        assertTrue(rhombus.intersectsOutline(DiagramRect(45.0, 25.0, 10.0, 10.0)))
    }

    @Test
    fun polygonPerimeterIntersectionUsesItsOutline() {
        val triangle = node(DiagramNodePayload.BasicShape(DiagramShapeKind.TRIANGLE))

        assertPointEquals(
            DiagramPoint(100.0 / 3.0, 20.0),
            perimeterIntersection(triangle, DiagramPoint(0.0, 0.0)),
        )
    }

    @Test
    fun resizeHandlesFollowTheVisibleOutline() {
        val rhombus = node(UmlActivityNode(UmlActivityKind.DECISION, "Condition?"))

        assertEquals(
            listOf(
                DiagramPoint(25.0, 15.0),
                DiagramPoint(50.0, 0.0),
                DiagramPoint(75.0, 15.0),
                DiagramPoint(100.0, 30.0),
                DiagramPoint(75.0, 45.0),
                DiagramPoint(50.0, 60.0),
                DiagramPoint(25.0, 45.0),
                DiagramPoint(0.0, 30.0),
            ),
            rhombus.outlineResizeHandlePoints(),
        )
    }
}
