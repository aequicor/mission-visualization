package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StructureOpsTest {

    private fun ids(graph: DiagramGraph): List<String> = graph.nodes.map { it.id.value }

    @Test
    fun groupNodesFiltersUnknownMembersAndUngroupDissolves() {
        val graph = diagramGraph {
            node("a")
            node("b", x = 200.0)
        }
        val groupId = DiagramGroupId("g")
        val grouped = graph.groupNodes(
            groupId,
            listOf(DiagramNodeId("a"), DiagramNodeId("ghost"), DiagramNodeId("b")),
        )
        assertEquals(
            listOf(DiagramNodeId("a"), DiagramNodeId("b")),
            grouped.groupById(groupId)!!.memberIds,
        )
        val ungrouped = grouped.ungroupNodes(groupId)
        assertNull(ungrouped.groupById(groupId))
        assertEquals(2, ungrouped.nodes.size)
    }

    @Test
    fun groupNodesWithOnlyUnknownMembersIsNoOp() {
        val graph = diagramGraph { node("a") }
        assertSame(graph, graph.groupNodes(DiagramGroupId("g"), listOf(DiagramNodeId("ghost"))))
    }

    @Test
    fun bringToFrontAndSendToBackReorderWithinList() {
        val graph = diagramGraph {
            node("a")
            node("b", x = 200.0)
            node("c", x = 400.0)
        }
        assertEquals(listOf("b", "c", "a"), ids(graph.bringToFront(DiagramNodeId("a"))))
        assertEquals(listOf("c", "a", "b"), ids(graph.sendToBack(DiagramNodeId("c"))))
    }

    @Test
    fun bringForwardAndSendBackwardStepOnce() {
        val graph = diagramGraph {
            node("a")
            node("b", x = 200.0)
            node("c", x = 400.0)
        }
        assertEquals(listOf("b", "a", "c"), ids(graph.bringForward(DiagramNodeId("a"))))
        assertEquals(listOf("a", "c", "b"), ids(graph.sendBackward(DiagramNodeId("c"))))
        // Already at the extremes: no-ops.
        assertEquals(listOf("a", "b", "c"), ids(graph.bringForward(DiagramNodeId("c"))))
        assertEquals(listOf("a", "b", "c"), ids(graph.sendBackward(DiagramNodeId("a"))))
    }

    @Test
    fun zOrderMovesContainerSubtreeAsBlock() {
        val graph = diagramGraph {
            val parent = node("p")
            node("child", x = 10.0, parentId = parent)
            node("other", x = 400.0)
        }
        assertEquals(listOf("other", "p", "child"), ids(graph.bringToFront(DiagramNodeId("p"))))
    }

    @Test
    fun bringForwardSkipsNodesOfOtherLayers() {
        val graph = diagramGraph {
            val overlay = layer("overlay")
            node("a")
            node("x", x = 200.0, layerId = overlay)
            node("b", x = 400.0)
        }
        assertEquals(listOf("x", "b", "a"), ids(graph.bringForward(DiagramNodeId("a"))))
    }

    @Test
    fun removeLayerMovesContentToDefaultLayer() {
        val graph = diagramGraph {
            val overlay = layer("overlay")
            val a = node("a", layerId = overlay)
            val b = node("b", x = 200.0)
            edge(
                "e",
                source = DiagramEndpoint.FloatingAnchor(a),
                target = DiagramEndpoint.FloatingAnchor(b),
                layerId = overlay,
            )
        }
        val result = graph.removeLayer(DiagramLayerId("overlay"))
        assertTrue(result.layers.isEmpty())
        assertNull(result.nodeById(DiagramNodeId("a"))!!.layerId)
        assertNull(result.edgeById(DiagramEdgeId("e"))!!.layerId)
        assertEquals(2, result.nodes.size)
    }

    @Test
    fun moveNodeToLayerMovesSubtreeAndValidatesLayer() {
        val graph = diagramGraph {
            layer("overlay")
            val parent = node("p")
            node("child", parentId = parent)
        }
        val overlay = DiagramLayerId("overlay")
        val moved = graph.moveNodeToLayer(DiagramNodeId("p"), overlay)
        assertEquals(overlay, moved.nodeById(DiagramNodeId("p"))!!.layerId)
        assertEquals(overlay, moved.nodeById(DiagramNodeId("child"))!!.layerId)

        assertSame(graph, graph.moveNodeToLayer(DiagramNodeId("p"), DiagramLayerId("ghost")))
    }

    @Test
    fun layerVisibilityAndLockToggles() {
        val graph = diagramGraph { layer("l") }
        val id = DiagramLayerId("l")
        assertFalse(graph.setLayerVisible(id, false).layerById(id)!!.visible)
        assertTrue(graph.setLayerLocked(id, true).layerById(id)!!.locked)
    }

    @Test
    fun dropIntoContainerRecomputesParentRelativePosition() {
        val graph = diagramGraph {
            node("box", x = 100.0, y = 100.0, width = 300.0, height = 200.0)
            val n = node("n", x = 0.0, y = 0.0, width = 40.0, height = 40.0)
            node("nChild", x = 5.0, y = 5.0, parentId = n)
        }
        val result = graph.dropIntoContainer(
            DiagramNodeId("n"),
            DiagramNodeId("box"),
            positionInContainer = DiagramPoint(10.0, 20.0),
        )
        val dropped = result.nodeById(DiagramNodeId("n"))!!
        assertEquals(DiagramNodeId("box"), dropped.parentId)
        assertEquals(110.0, dropped.x)
        assertEquals(120.0, dropped.y)
        // Child of the dropped node moved by the same delta.
        val child = result.nodeById(DiagramNodeId("nChild"))!!
        assertEquals(115.0, child.x)
        assertEquals(125.0, child.y)
    }

    @Test
    fun dropIntoContainerWithoutPositionKeepsDocumentCoordinates() {
        val graph = diagramGraph {
            node("box", x = 100.0, y = 100.0, width = 300.0, height = 200.0)
            node("n", x = 150.0, y = 150.0)
        }
        val result = graph.dropIntoContainer(DiagramNodeId("n"), DiagramNodeId("box"))
        val dropped = result.nodeById(DiagramNodeId("n"))!!
        assertEquals(DiagramNodeId("box"), dropped.parentId)
        assertEquals(150.0, dropped.x)
        assertEquals(150.0, dropped.y)
    }

    @Test
    fun dropIntoContainerRefusesContainmentCycles() {
        val graph = diagramGraph {
            val parent = node("p")
            node("child", parentId = parent)
        }
        assertSame(graph, graph.dropIntoContainer(DiagramNodeId("p"), DiagramNodeId("child")))
        assertSame(graph, graph.dropIntoContainer(DiagramNodeId("p"), DiagramNodeId("p")))
    }

    @Test
    fun pullOutOfContainerKeepsVisualPosition() {
        val graph = diagramGraph {
            val box = node("box", x = 100.0, y = 100.0, width = 300.0, height = 200.0)
            node("n", x = 150.0, y = 150.0, parentId = box)
        }
        val result = graph.pullOutOfContainer(DiagramNodeId("n"))
        val pulled = result.nodeById(DiagramNodeId("n"))!!
        assertNull(pulled.parentId)
        assertEquals(150.0, pulled.x)
        assertEquals(150.0, pulled.y)
    }
}
