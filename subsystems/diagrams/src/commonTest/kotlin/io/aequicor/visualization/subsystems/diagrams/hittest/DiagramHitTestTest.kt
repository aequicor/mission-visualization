package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.SwimlaneLane
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramEdgeEnd
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DiagramHitTestTest {

    @Test
    fun selfLoopLabelAnchorsAboveTheLoopTop() {
        // A self-message loop (routeMessage shape): (x0,y) → (x1,y) → (x1,y+lh) → (x0,y+lh).
        val loop = listOf(
            DiagramPoint(415.0, 100.0),
            DiagramPoint(449.0, 100.0),
            DiagramPoint(449.0, 118.0),
            DiagramPoint(415.0, 118.0),
        )
        val anchor = edgeLabelAnchorPoint(loop, DiagramEdgeLabel(DiagramLabel("do work")))
        // Centered on the top edge (x0..x1) and lifted clear above it, never beside the loop.
        assertEquals(432.0, anchor.x, 1e-9)
        assertEquals(100.0 - EDGE_LABEL_LINE_GAP, anchor.y, 1e-9)
    }

    @Test
    fun straightEdgeLabelIsUnaffectedBySelfLoopRule() {
        // A plain horizontal segment must keep the arc-length + perpendicular-lift placement.
        val line = listOf(DiagramPoint(0.0, 50.0), DiagramPoint(200.0, 50.0))
        val anchor = edgeLabelAnchorPoint(line, DiagramEdgeLabel(DiagramLabel("x")))
        assertEquals(100.0, anchor.x, 1e-9)
        assertEquals(50.0 - EDGE_LABEL_LINE_GAP, anchor.y, 1e-9)
    }

    @Test
    fun middleLabelSlidesOffCrossingsWithOtherRoutes() {
        val route = listOf(DiagramPoint(0.0, 50.0), DiagramPoint(200.0, 50.0))
        // Another route crosses exactly at the default center anchor (fraction 0.5 = x 100).
        val crossing = listOf(DiagramPoint(100.0, 0.0), DiagramPoint(100.0, 100.0))
        val label = DiagramEdgeLabel(DiagramLabel("x"))
        val anchor = edgeLabelAnchorPoint(route, label, otherRoutes = listOf(crossing))
        // The label leaves the crossing but stays on (and lifted off) the line.
        assertEquals(50.0 - EDGE_LABEL_LINE_GAP, anchor.y, 1e-9)
        kotlin.test.assertTrue(
            kotlin.math.abs(anchor.x - 100.0) >= 15.0,
            "label must slide off the crossing, got x=${anchor.x}",
        )
    }

    @Test
    fun draggedMiddleLabelKeepsTheCenterFraction() {
        val route = listOf(DiagramPoint(0.0, 50.0), DiagramPoint(200.0, 50.0))
        val crossing = listOf(DiagramPoint(100.0, 0.0), DiagramPoint(100.0, 100.0))
        val label = DiagramEdgeLabel(DiagramLabel("x"), offsetX = 5.0, offsetY = -3.0)
        val anchor = edgeLabelAnchorPoint(route, label, otherRoutes = listOf(crossing))
        // The manual offset is relative to the untouched center anchor.
        assertEquals(105.0, anchor.x, 1e-9)
        assertEquals(50.0 - EDGE_LABEL_LINE_GAP - 3.0, anchor.y, 1e-9)
    }

    @Test
    fun emptySpaceHitsNothing() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0) }
        assertNull(hitTest(graph, emptyMap(), DiagramPoint(500.0, 500.0)))
    }

    @Test
    fun nodeBodyHitsNode() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0) }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(50.0, 50.0))
        assertEquals(DiagramHit.Node(DiagramNodeId("a")), hit)
    }

    @Test
    fun shapedNodeDoesNotHitThroughItsBoundingBoxCorner() {
        val graph = diagramGraph {
            node(
                "diamond",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 100.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS),
            )
        }

        assertNull(hitTest(graph, emptyMap(), DiagramPoint(2.0, 2.0), tolerance = 5.0))
        assertEquals(
            DiagramHit.Node(DiagramNodeId("diamond")),
            hitTest(graph, emptyMap(), DiagramPoint(50.0, 50.0), tolerance = 0.0),
        )
    }

    @Test
    fun transparentCornerOfForegroundShapeLetsTheNodeBehindWin() {
        val graph = diagramGraph {
            node("back", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node(
                "front",
                x = 50.0,
                y = 50.0,
                width = 100.0,
                height = 100.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS),
            )
        }

        assertEquals(
            DiagramHit.Node(DiagramNodeId("back")),
            hitTest(graph, emptyMap(), DiagramPoint(60.0, 60.0), tolerance = 0.0),
        )
    }

    @Test
    fun portBeatsNodeBody() {
        val graph = diagramGraph {
            node(
                "a",
                x = 0.0, y = 0.0, width = 100.0, height = 100.0,
                ports = DiagramPort.standardPorts(),
            )
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(99.0, 50.0), tolerance = 5.0)
        assertEquals(DiagramHit.Port(DiagramNodeId("a"), DiagramPortId("right")), hit)
    }

    @Test
    fun virtualConnectionPointIsOfferedOnlyOutsideTheNodeBody() {
        val graph = diagramGraph {
            node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
        }

        // Just OUTSIDE the top edge, within tolerance of the auto top-q1 grid point: an edge can be
        // started from the node's perimeter (draw.io connection cross), so a virtual Port is offered.
        val outside = hitTest(graph, emptyMap(), DiagramPoint(25.0, -1.0), tolerance = 5.0)
        assertEquals(DiagramHit.Port(DiagramNodeId("a"), DiagramPortId("top-q1")), outside)

        // The SAME neighbourhood but just INSIDE the body selects the node — a plain click there has
        // to select/move the node, not get swallowed by the virtual grid (which the caller would turn
        // into an edge-drag with no click-without-move fallback).
        val inside = hitTest(graph, emptyMap(), DiagramPoint(25.0, 1.0), tolerance = 5.0)
        assertEquals(DiagramHit.Node(DiagramNodeId("a")), inside)

        // The virtual grid is a pure hit-test convenience: it never mutates the node's declared ports.
        assertEquals(emptyList(), graph.nodeById(DiagramNodeId("a"))!!.ports, "hit-test must not persist the virtual port")
    }

    @Test
    fun foregroundBodyBeatsAnOccludedNodesVirtualPort() {
        // D1 z-order: a background node's virtual connection point that lands under a foreground
        // node's body must NOT be returned in front of that body.
        val graph = diagramGraph {
            node("back", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("front", x = 50.0, y = 0.0, width = 100.0, height = 100.0)
        }
        // (100, 50) is "back"'s right mid-side virtual port AND sits inside "front"'s body (its center).
        val hit = hitTest(graph, emptyMap(), DiagramPoint(100.0, 50.0), tolerance = 5.0)
        assertEquals(DiagramHit.Node(DiagramNodeId("front")), hit)
    }

    @Test
    fun defaultNodePerimeterClicksSelectTheNode() {
        // D3 selectability: a node with NO declared ports must stay selectable near its corners and
        // sides, where pre-fix the auto grid swallowed the click as a Port (no click fallback in the
        // caller, so the node could be neither selected nor moved there).
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0) }

        // Just inside the top-left corner — coincides with the top-left virtual grid point.
        assertEquals(
            DiagramHit.Node(DiagramNodeId("a")),
            hitTest(graph, emptyMap(), DiagramPoint(2.0, 2.0), tolerance = 5.0),
        )
        // Just inside the left edge — coincides with the left mid-side virtual point.
        assertEquals(
            DiagramHit.Node(DiagramNodeId("a")),
            hitTest(graph, emptyMap(), DiagramPoint(2.0, 50.0), tolerance = 5.0),
        )
    }

    @Test
    fun declaredPortStillOutranksBodyAfterVirtualGridFix() {
        // A DECLARED port is an intentional handle and keeps its priority over the node body it sits
        // on, even though the virtual grid is now resolved AFTER bodies. An interior anchor proves it
        // is the declared-port pass (not the body pass) that answers.
        val interiorPort = DiagramPort(DiagramPortId("hub"), DiagramPortAnchor.RelativePoint(0.5, 0.5))
        val graph = diagramGraph {
            node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0, ports = listOf(interiorPort))
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(51.0, 50.0), tolerance = 5.0)
        assertEquals(DiagramHit.Port(DiagramNodeId("a"), DiagramPortId("hub")), hit)
    }

    @Test
    fun resizeHandleBeatsPortOnSelectedNode() {
        val cornerPort = DiagramPort(DiagramPortId("tl"), DiagramPortAnchor.RelativePoint(0.0, 0.0))
        val graph = diagramGraph {
            node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0, ports = listOf(cornerPort))
        }
        val point = DiagramPoint(0.0, 0.0)
        val selected = hitTest(
            graph, emptyMap(), point,
            tolerance = 5.0,
            selectedNodeIds = setOf(DiagramNodeId("a")),
        )
        assertEquals(DiagramHit.ResizeHandle(DiagramNodeId("a"), DiagramResizeHandle.TOP_LEFT), selected)

        val unselected = hitTest(graph, emptyMap(), point, tolerance = 5.0)
        assertEquals(DiagramHit.Port(DiagramNodeId("a"), DiagramPortId("tl")), unselected)
    }

    @Test
    fun rhombusResizeHandleIsOnTheBlueOutline() {
        val graph = diagramGraph {
            node(
                "diamond",
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 100.0,
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RHOMBUS),
            )
        }
        val selected = setOf(DiagramNodeId("diamond"))

        assertEquals(
            DiagramHit.ResizeHandle(DiagramNodeId("diamond"), DiagramResizeHandle.TOP_LEFT),
            hitTest(graph, emptyMap(), DiagramPoint(25.0, 25.0), tolerance = 2.0, selectedNodeIds = selected),
        )
        assertNull(hitTest(graph, emptyMap(), DiagramPoint(0.0, 0.0), tolerance = 2.0, selectedNodeIds = selected))
    }

    @Test
    fun edgeBeatsNodeBody() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 40.0, height = 40.0)
            val b = node("b", x = 200.0, y = 0.0, width = 40.0, height = 40.0)
            node("under", x = 80.0, y = 0.0, width = 60.0, height = 60.0)
            edge("a-b", a, b)
        }
        val routes = mapOf(
            DiagramEdgeId("a-b") to listOf(DiagramPoint(40.0, 20.0), DiagramPoint(200.0, 20.0)),
        )
        val hit = hitTest(graph, routes, DiagramPoint(110.0, 20.0), tolerance = 4.0)
        assertEquals(DiagramHit.Edge(DiagramEdgeId("a-b"), segmentIndex = 0), hit)
    }

    @Test
    fun waypointHandleBeatsEdgeOnSelectedEdge() {
        val graph = diagramGraph {
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
                waypoints = listOf(DiagramPoint(50.0, 50.0)),
            )
        }
        val point = DiagramPoint(52.0, 50.0)
        val selected = hitTest(
            graph, emptyMap(), point,
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(DiagramHit.WaypointHandle(DiagramEdgeId("e"), waypointIndex = 0), selected)

        val unselected = hitTest(graph, emptyMap(), point)
        assertIs<DiagramHit.Edge>(unselected)
    }

    @Test
    fun endpointHandlesOnSelectedEdgeResolveSourceAndTarget() {
        val graph = diagramGraph {
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
            )
        }
        val nearSource = hitTest(
            graph, emptyMap(), DiagramPoint(2.0, 50.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(DiagramHit.EndpointHandle(DiagramEdgeId("e"), DiagramEdgeEnd.SOURCE), nearSource)

        val nearTarget = hitTest(
            graph, emptyMap(), DiagramPoint(98.0, 50.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(DiagramHit.EndpointHandle(DiagramEdgeId("e"), DiagramEdgeEnd.TARGET), nearTarget)
    }

    @Test
    fun endpointHandleBeatsEdgeBodyWithinTolerance() {
        val graph = diagramGraph {
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
            )
        }
        // A point on the line but within tolerance of the source end: the endpoint grab wins.
        val atEnd = hitTest(
            graph, emptyMap(), DiagramPoint(2.0, 50.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(DiagramHit.EndpointHandle(DiagramEdgeId("e"), DiagramEdgeEnd.SOURCE), atEnd)

        // Mid-line, clear of both ends: still the edge body, not an endpoint.
        val midLine = hitTest(
            graph, emptyMap(), DiagramPoint(50.0, 50.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertIs<DiagramHit.Edge>(midLine)
    }

    @Test
    fun unselectedEdgeExposesNoEndpointHandle() {
        val graph = diagramGraph {
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 50.0),
                target = DiagramEndpoint.FreePoint(100.0, 50.0),
            )
        }
        // Same point that yields an endpoint handle when selected falls through to the edge body.
        val hit = hitTest(graph, emptyMap(), DiagramPoint(2.0, 50.0))
        assertIs<DiagramHit.Edge>(hit)
    }

    @Test
    fun endpointHandlesFollowTheProvidedRoute() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 40.0, height = 40.0)
            val b = node("b", x = 200.0, y = 200.0, width = 40.0, height = 40.0)
            edge("e", a, b)
        }
        // An L-shaped route: endpoints are picked from the polyline ends, not the node centers.
        val routes = mapOf(
            DiagramEdgeId("e") to listOf(
                DiagramPoint(40.0, 20.0),
                DiagramPoint(120.0, 20.0),
                DiagramPoint(120.0, 220.0),
                DiagramPoint(200.0, 220.0),
            ),
        )
        val atSource = hitTest(
            graph, routes, DiagramPoint(41.0, 20.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(DiagramHit.EndpointHandle(DiagramEdgeId("e"), DiagramEdgeEnd.SOURCE), atSource)

        val atTarget = hitTest(
            graph, routes, DiagramPoint(199.0, 220.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(DiagramHit.EndpointHandle(DiagramEdgeId("e"), DiagramEdgeEnd.TARGET), atTarget)
    }

    @Test
    fun labelHandleOnSelectedEdgeAtRouteMidpoint() {
        val graph = diagramGraph {
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(100.0, 0.0),
                label = "hello",
            )
        }
        // The label is lifted EDGE_LABEL_LINE_GAP above a horizontal edge, so its grab handle
        // sits above the midpoint, not on the line.
        val hit = hitTest(
            graph, emptyMap(), DiagramPoint(50.0, -EDGE_LABEL_LINE_GAP + 1.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertEquals(
            DiagramHit.LabelHandle(DiagramEdgeId("e"), DiagramEdgeLabelPosition.MIDDLE),
            hit,
        )
        // The bare midpoint on the line is now the edge line, not the label handle.
        val onLine = hitTest(
            graph, emptyMap(), DiagramPoint(50.0, 0.0),
            selectedEdgeIds = setOf(DiagramEdgeId("e")),
        )
        assertIs<DiagramHit.Edge>(onLine)
    }

    @Test
    fun edgeLabelIsHitEvenWhenTheEdgeIsUnselected() {
        val graph = diagramGraph {
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(100.0, 0.0),
                label = "hello",
            )
        }
        // draw.io: the label is part of the edge — hittable straight from hover, no selection.
        // The MIDDLE label sits lifted EDGE_LABEL_LINE_GAP above the horizontal edge's midpoint.
        val hit = hitTest(graph, emptyMap(), DiagramPoint(50.0, -EDGE_LABEL_LINE_GAP))
        assertEquals(
            DiagramHit.LabelHandle(DiagramEdgeId("e"), DiagramEdgeLabelPosition.MIDDLE),
            hit,
        )
    }

    @Test
    fun edgeLabelBeatsAnOverlappingNodeBody() {
        val graph = diagramGraph {
            // A node whose body covers the label anchor (50, -EDGE_LABEL_LINE_GAP).
            node("under", x = 0.0, y = -40.0, width = 120.0, height = 80.0)
            edge(
                "e",
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(100.0, 0.0),
                label = "hello",
            )
        }
        // The label wins even sitting on top of a node body (label pass outranks the node pass).
        val hit = hitTest(graph, emptyMap(), DiagramPoint(50.0, -EDGE_LABEL_LINE_GAP))
        assertEquals(
            DiagramHit.LabelHandle(DiagramEdgeId("e"), DiagramEdgeLabelPosition.MIDDLE),
            hit,
        )
    }

    @Test
    fun laterNodeInListWinsOverlap() {
        val graph = diagramGraph {
            node("back", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("front", x = 50.0, y = 50.0, width = 100.0, height = 100.0)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(75.0, 75.0))
        assertEquals(DiagramHit.Node(DiagramNodeId("front")), hit)
    }

    @Test
    fun explicitLayerRendersAboveDefaultLayer() {
        val graph = diagramGraph {
            val overlay = layer("overlay")
            node("onLayer", x = 0.0, y = 0.0, width = 100.0, height = 100.0, layerId = overlay)
            node("onDefault", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(50.0, 50.0))
        assertEquals(DiagramHit.Node(DiagramNodeId("onLayer")), hit)
    }

    @Test
    fun invisibleAndLockedNodesAreTransparent() {
        val graph = diagramGraph {
            node("base", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("hidden", x = 0.0, y = 0.0, width = 100.0, height = 100.0, visible = false)
            node("locked", x = 0.0, y = 0.0, width = 100.0, height = 100.0, locked = true)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(50.0, 50.0))
        assertEquals(DiagramHit.Node(DiagramNodeId("base")), hit)
    }

    @Test
    fun hiddenLayerIsTransparent() {
        val graph = diagramGraph {
            node("base", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            val hidden = layer("hidden", visible = false)
            node("ghost", x = 0.0, y = 0.0, width = 100.0, height = 100.0, layerId = hidden)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(50.0, 50.0))
        assertEquals(DiagramHit.Node(DiagramNodeId("base")), hit)
    }

    @Test
    fun tableCellPartIsReported() {
        val table = TableNode(
            rows = listOf(TableRow(32.0), TableRow(32.0)),
            columns = listOf(TableColumn(60.0), TableColumn(60.0)),
        )
        val graph = diagramGraph {
            node("t", x = 0.0, y = 0.0, width = 120.0, height = 64.0, payload = table)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(90.0, 50.0))
        assertEquals(
            DiagramHit.Node(DiagramNodeId("t"), DiagramNodeHitPart.TableCellPart(row = 1, column = 1)),
            hit,
        )
    }

    @Test
    fun mergedTableCellReportsAnchorPosition() {
        val table = TableNode(
            rows = listOf(TableRow(32.0), TableRow(32.0)),
            columns = listOf(TableColumn(60.0), TableColumn(60.0)),
            cells = listOf(TableCell(row = 0, column = 0, rowSpan = 2)),
        )
        val graph = diagramGraph {
            node("t", x = 0.0, y = 0.0, width = 120.0, height = 64.0, payload = table)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(30.0, 50.0))
        assertEquals(
            DiagramHit.Node(DiagramNodeId("t"), DiagramNodeHitPart.TableCellPart(row = 0, column = 0)),
            hit,
        )
    }

    @Test
    fun classSectionsSplitTopToBottom() {
        val payload = UmlClassNode(
            name = "C",
            attributes = listOf(UmlMember("a"), UmlMember("b")),
            operations = listOf(UmlMember("f()"), UmlMember("g()")),
        )
        val graph = diagramGraph {
            node("c", x = 0.0, y = 0.0, width = 160.0, height = 100.0, payload = payload)
        }

        fun sectionAt(y: Double): UmlClassSection {
            val hit = hitTest(graph, emptyMap(), DiagramPoint(80.0, y))
            val node = assertIs<DiagramHit.Node>(hit)
            return assertIs<DiagramNodeHitPart.ClassSectionPart>(node.part).section
        }

        assertEquals(UmlClassSection.NAME, sectionAt(10.0))
        assertEquals(UmlClassSection.ATTRIBUTES, sectionAt(30.0))
        assertEquals(UmlClassSection.OPERATIONS, sectionAt(90.0))
    }

    @Test
    fun swimlaneLaneIndexIsReported() {
        val payload = DiagramNodePayload.SwimlaneNode(
            orientation = DiagramOrientation.HORIZONTAL,
            lanes = listOf(SwimlaneLane(size = 50.0), SwimlaneLane(size = 50.0)),
        )
        val graph = diagramGraph {
            node("pool", x = 0.0, y = 0.0, width = 300.0, height = 100.0, payload = payload)
        }
        val hit = hitTest(graph, emptyMap(), DiagramPoint(150.0, 75.0))
        assertEquals(
            DiagramHit.Node(DiagramNodeId("pool"), DiagramNodeHitPart.LanePart(laneIndex = 1)),
            hit,
        )
    }

    @Test
    fun fallbackRouteUsesEndpointsAndWaypoints() {
        val graph = diagramGraph {
            val a = node("a", x = 0.0, y = 0.0, width = 40.0, height = 40.0)
            val b = node("b", x = 200.0, y = 0.0, width = 40.0, height = 40.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                waypoints = listOf(DiagramPoint(120.0, 150.0)),
            )
        }
        // Fallback route: center a (20,20) -> waypoint (120,150) -> center b (220,20).
        val hit = hitTest(graph, emptyMap(), DiagramPoint(120.0, 148.0), tolerance = 5.0)
        assertIs<DiagramHit.Edge>(hit)
    }
}
