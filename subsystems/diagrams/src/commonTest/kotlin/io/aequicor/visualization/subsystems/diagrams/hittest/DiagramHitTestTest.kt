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
    fun virtualDrawIoConnectionPointBeatsNodeBody() {
        val graph = diagramGraph {
            node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
        }

        val hit = hitTest(graph, emptyMap(), DiagramPoint(25.0, 1.0), tolerance = 5.0)

        assertEquals(DiagramHit.Port(DiagramNodeId("a"), DiagramPortId("top-q1")), hit)
        assertEquals(emptyList(), graph.nodeById(DiagramNodeId("a"))!!.ports, "hit-test must not persist the virtual port")
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
