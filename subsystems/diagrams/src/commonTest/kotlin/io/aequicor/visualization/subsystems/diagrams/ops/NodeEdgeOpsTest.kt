package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NodeEdgeOpsTest {

    private val a = DiagramNodeId("a")
    private val b = DiagramNodeId("b")
    private val e = DiagramEdgeId("e")

    @Test
    fun moveNodeMovesWholeContainerSubtree() {
        val graph = diagramGraph {
            val parent = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("child", x = 10.0, y = 10.0, parentId = parent)
        }
        val moved = graph.moveNode(a, 5.0, 7.0)
        assertEquals(5.0, moved.nodeById(a)!!.x)
        assertEquals(15.0, moved.nodeById(DiagramNodeId("child"))!!.x)
        assertEquals(17.0, moved.nodeById(DiagramNodeId("child"))!!.y)
    }

    @Test
    fun moveNodeCarriesWaypointsOfFullyInternalEdges() {
        val graph = diagramGraph {
            val parent = node("a", x = 0.0, y = 0.0, width = 200.0, height = 200.0)
            val childA = node("c1", x = 10.0, y = 10.0, parentId = parent)
            val childB = node("c2", x = 100.0, y = 10.0, parentId = parent)
            edge(
                "internal",
                source = DiagramEndpoint.FloatingAnchor(childA),
                target = DiagramEndpoint.FloatingAnchor(childB),
                waypoints = listOf(DiagramPoint(50.0, 50.0)),
            )
            edge("external", childA, node("outside", x = 400.0, y = 0.0))
        }
        val moved = graph.moveNode(a, 10.0, 0.0)
        assertEquals(
            listOf(DiagramPoint(60.0, 50.0)),
            moved.edgeById(DiagramEdgeId("internal"))!!.waypoints,
        )
    }

    @Test
    fun moveMissingNodeIsNoOp() {
        val graph = diagramGraph { node("a") }
        assertSame(graph, graph.moveNode(DiagramNodeId("ghost"), 1.0, 1.0))
    }

    @Test
    fun resizeNodeWithoutChildrenKeepsChildGeometry() {
        val graph = diagramGraph {
            val parent = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("child", x = 10.0, y = 10.0, width = 20.0, height = 20.0, parentId = parent)
        }
        val resized = graph.resizeNode(a, DiagramRect(0.0, 0.0, 200.0, 200.0))
        assertEquals(200.0, resized.nodeById(a)!!.width)
        val child = resized.nodeById(DiagramNodeId("child"))!!
        assertEquals(10.0, child.x)
        assertEquals(20.0, child.width)
    }

    @Test
    fun resizeNodeScalesSubtreeWhenRequested() {
        val graph = diagramGraph {
            val parent = node("a", x = 0.0, y = 0.0, width = 100.0, height = 100.0)
            node("child", x = 10.0, y = 10.0, width = 20.0, height = 20.0, parentId = parent)
        }
        val resized = graph.resizeNode(a, DiagramRect(0.0, 0.0, 200.0, 200.0), resizeChildren = true)
        val child = resized.nodeById(DiagramNodeId("child"))!!
        assertEquals(20.0, child.x)
        assertEquals(20.0, child.y)
        assertEquals(40.0, child.width)
        assertEquals(40.0, child.height)
    }

    @Test
    fun reverseEdgeSwapsEndpointsArrowheadsAndSourceTargetLabels() {
        val graph = diagramGraph {
            val from = node("a")
            val to = node("b", x = 200.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(from),
                target = DiagramEndpoint.FloatingAnchor(to),
                waypoints = listOf(DiagramPoint(1.0, 1.0), DiagramPoint(2.0, 2.0)),
                labels = listOf(
                    DiagramEdgeLabel(DiagramLabel("near source"), DiagramEdgeLabelPosition.SOURCE),
                    DiagramEdgeLabel(DiagramLabel("center"), DiagramEdgeLabelPosition.MIDDLE),
                    DiagramEdgeLabel(DiagramLabel("near target"), DiagramEdgeLabelPosition.TARGET),
                ),
            )
        }
        val reversed = graph.reverseEdge(e).edgeById(e)!!
        assertEquals(DiagramEndpoint.FloatingAnchor(b), reversed.source)
        assertEquals(DiagramEndpoint.FloatingAnchor(a), reversed.target)
        assertEquals(listOf(DiagramPoint(2.0, 2.0), DiagramPoint(1.0, 1.0)), reversed.waypoints)
        val byText = reversed.labels.associate { it.label.text to it.position }
        assertEquals(DiagramEdgeLabelPosition.TARGET, byText.getValue("near source"))
        assertEquals(DiagramEdgeLabelPosition.MIDDLE, byText.getValue("center"))
        assertEquals(DiagramEdgeLabelPosition.SOURCE, byText.getValue("near target"))
    }

    @Test
    fun reconnectEdgeToExistingPort() {
        val graph = diagramGraph {
            val from = node("a", ports = DiagramPort.standardPorts())
            val to = node("b", x = 200.0)
            edge("e", from, to)
        }
        val reconnected = graph.reconnectEdge(
            e,
            DiagramEdgeEnd.SOURCE,
            DiagramEndpoint.FixedPort(a, DiagramPortId("right")),
        )
        assertEquals(DiagramEndpoint.FixedPort(a, DiagramPortId("right")), reconnected.edgeById(e)!!.source)
    }

    @Test
    fun reconnectEdgeToMissingPortIsNoOp() {
        val graph = diagramGraph {
            edge("e", node("a"), node("b", x = 200.0))
        }
        val result = graph.reconnectEdge(
            e,
            DiagramEdgeEnd.TARGET,
            DiagramEndpoint.FixedPort(b, DiagramPortId("ghost")),
        )
        assertSame(graph, result)
    }

    @Test
    fun waypointAddMoveRemove() {
        val graph = diagramGraph { edge("e", node("a"), node("b", x = 200.0)) }
        val withPoint = graph.addWaypoint(e, 0, DiagramPoint(10.0, 10.0))
        assertEquals(listOf(DiagramPoint(10.0, 10.0)), withPoint.edgeById(e)!!.waypoints)

        val movedPoint = withPoint.moveWaypoint(e, 0, DiagramPoint(20.0, 20.0))
        assertEquals(listOf(DiagramPoint(20.0, 20.0)), movedPoint.edgeById(e)!!.waypoints)

        val outOfBounds = movedPoint.moveWaypoint(e, 5, DiagramPoint(0.0, 0.0))
        assertEquals(movedPoint, outOfBounds)

        val removed = movedPoint.removeWaypoint(e, 0)
        assertTrue(removed.edgeById(e)!!.waypoints.isEmpty())
    }

    @Test
    fun removePortDetachesFixedEdgesToFloating() {
        val graph = diagramGraph {
            val from = node("a", ports = DiagramPort.standardPorts())
            val to = node("b", x = 200.0)
            edge(
                "e",
                source = DiagramEndpoint.FixedPort(from, DiagramPortId("right")),
                target = DiagramEndpoint.FloatingAnchor(to),
            )
        }
        val result = graph.removePort(a, DiagramPortId("right"))
        assertEquals(3, result.nodeById(a)!!.ports.size)
        assertEquals(DiagramEndpoint.FloatingAnchor(a), result.edgeById(e)!!.source)
    }

    @Test
    fun addCustomPortRejectsDuplicateId() {
        val graph = diagramGraph { node("a", ports = DiagramPort.standardPorts()) }
        val duplicate = DiagramPort(DiagramPortId("top"), DiagramPortAnchor.RelativePoint(0.5, 0.5))
        assertSame(graph, graph.addCustomPort(a, duplicate))

        val custom = DiagramPort(DiagramPortId("custom"), DiagramPortAnchor.RelativePoint(0.25, 0.0))
        val result = graph.addCustomPort(a, custom)
        assertEquals(5, result.nodeById(a)!!.ports.size)
    }

    @Test
    fun setNodeLabelReplacesAndClears() {
        val graph = diagramGraph { node("a", label = "old") }
        val renamed = graph.setNodeLabel(a, "new")
        assertEquals("new", renamed.nodeById(a)!!.labels.single().text)
        val cleared = renamed.setNodeLabel(a, null)
        assertTrue(cleared.nodeById(a)!!.labels.isEmpty())
    }

    @Test
    fun setEdgeLabelAddsReplacesAndRemoves() {
        val graph = diagramGraph { edge("e", node("a"), node("b", x = 200.0)) }
        val withLabel = graph.setEdgeLabel(e, DiagramEdgeLabelPosition.TARGET, "1..*")
        assertEquals("1..*", withLabel.edgeById(e)!!.labels.single().label.text)

        val moved = withLabel.moveEdgeLabel(e, DiagramEdgeLabelPosition.TARGET, 4.0, -2.0)
        val label = moved.edgeById(e)!!.labels.single()
        assertEquals(4.0, label.offsetX)
        assertEquals(-2.0, label.offsetY)

        val replaced = moved.setEdgeLabel(e, DiagramEdgeLabelPosition.TARGET, "0..1")
        val replacedLabel = replaced.edgeById(e)!!.labels.single()
        assertEquals("0..1", replacedLabel.label.text)
        assertEquals(4.0, replacedLabel.offsetX, "manual offset must survive text edits")

        val removed = replaced.setEdgeLabel(e, DiagramEdgeLabelPosition.TARGET, null)
        assertTrue(removed.edgeById(e)!!.labels.isEmpty())
    }

    @Test
    fun cloneNodeAndConnectCreatesOffsetCopyWithEdge() {
        val graph = diagramGraph { node("a", x = 10.0, y = 20.0, label = "A") }
        val cloneId = DiagramNodeId("a2")
        val edgeId = DiagramEdgeId("a-a2")
        val result = graph.cloneNodeAndConnect(a, cloneId, edgeId, relation = DiagramRelation.Transition)
        val clone = assertNotNull(result.nodeById(cloneId))
        assertEquals(50.0, clone.x)
        assertEquals(60.0, clone.y)
        assertEquals("A", clone.labels.single().text)
        val edge = assertNotNull(result.edgeById(edgeId))
        assertEquals(DiagramEndpoint.FloatingAnchor(a), edge.source)
        assertEquals(DiagramEndpoint.FloatingAnchor(cloneId), edge.target)
        assertEquals(DiagramRelation.Transition, edge.relation)
    }

    @Test
    fun directionalCloneOffsetIsAxisAlignedWithGap() {
        val graph = diagramGraph { node("a", x = 0.0, y = 0.0, width = 120.0, height = 60.0) }
        // One node-extent (120/60) plus the default 40-unit gap, centered on the shared axis.
        assertEquals(160.0 to 0.0, graph.directionalCloneOffset(a, DiagramNodeSide.RIGHT))
        assertEquals(-160.0 to 0.0, graph.directionalCloneOffset(a, DiagramNodeSide.LEFT))
        assertEquals(0.0 to 100.0, graph.directionalCloneOffset(a, DiagramNodeSide.BOTTOM))
        assertEquals(0.0 to -100.0, graph.directionalCloneOffset(a, DiagramNodeSide.TOP))
    }

    @Test
    fun directionalCloneOffsetSkipsPastAnOccupyingNode() {
        val graph = diagramGraph {
            node("a", x = 0.0, y = 0.0, width = 120.0, height = 60.0)
            node("b", x = 160.0, y = 0.0, width = 120.0, height = 60.0)
        }
        // The first right-step lands exactly on b, so it advances another extent+gap.
        assertEquals(320.0 to 0.0, graph.directionalCloneOffset(a, DiagramNodeSide.RIGHT))
    }

    @Test
    fun directionalCloneOffsetIsNullWhenSourceMissing() {
        val graph = diagramGraph { node("a") }
        assertEquals(null, graph.directionalCloneOffset(DiagramNodeId("missing"), DiagramNodeSide.RIGHT))
    }
}
