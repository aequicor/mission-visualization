package io.aequicor.visualization.subsystems.diagrams.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramGraphOpsTest {

    private fun containerGraph(): DiagramGraph = diagramGraph {
        val root = node("container", payload = DiagramNodePayload.ContainerNode())
        val childA = node("childA", parentId = root)
        node("grandchild", parentId = childA)
        val other = node("other")
        edge("container-other", root, other)
        edge("childA-other", childA, other)
        edge("other-loop", other, other)
        group("g1", listOf(root, other))
        group("g2", listOf(childA))
    }

    @Test
    fun withNodeReplacesInPlaceKeepingZOrder() {
        val graph = diagramGraph {
            node("a")
            node("b")
            node("c")
        }
        val moved = graph.nodeById(DiagramNodeId("b"))!!.copy(x = 500.0)
        val updated = graph.withNode(moved)

        assertEquals(listOf("a", "b", "c"), updated.nodes.map { it.id.value })
        assertEquals(500.0, updated.nodeById(DiagramNodeId("b"))!!.x)
    }

    @Test
    fun withNodeAppendsNewNode() {
        val graph = DiagramGraph.Empty.withNode(
            DiagramNode(id = DiagramNodeId("a"), x = 0.0, y = 0.0, width = 10.0, height = 10.0),
        )
        assertEquals(1, graph.nodes.size)
    }

    @Test
    fun updateNodeTransformsSingleNode() {
        val graph = containerGraph().updateNode(DiagramNodeId("other")) { it.copy(locked = true) }
        assertTrue(graph.nodeById(DiagramNodeId("other"))!!.locked)
        assertTrue(graph.nodes.filter { it.id.value != "other" }.none { it.locked })
    }

    @Test
    fun removeNodeCascadesToSubtreeEdgesAndGroups() {
        val graph = containerGraph().removeNode(DiagramNodeId("container"))

        assertEquals(listOf("other"), graph.nodes.map { it.id.value })
        assertEquals(listOf("other-loop"), graph.edges.map { it.id.value })
        assertEquals(listOf("g1"), graph.groups.map { it.id.value })
        assertEquals(
            listOf(DiagramNodeId("other")),
            graph.groupById(DiagramGroupId("g1"))!!.memberIds,
        )
    }

    @Test
    fun removeMidLevelNodeKeepsSiblingsAndContainer() {
        val graph = containerGraph().removeNode(DiagramNodeId("childA"))

        assertEquals(setOf("container", "other"), graph.nodes.map { it.id.value }.toSet())
        assertEquals(
            setOf("container-other", "other-loop"),
            graph.edges.map { it.id.value }.toSet(),
        )
        // g2 held only childA and is dropped once empty.
        assertEquals(listOf("g1"), graph.groups.map { it.id.value })
    }

    @Test
    fun removeEdgeAndMissingNodeAreNoOpsWhenAbsent() {
        val graph = containerGraph()
        assertEquals(graph, graph.removeNode(DiagramNodeId("missing")))
        assertEquals(
            graph.edges.size - 1,
            graph.removeEdge(DiagramEdgeId("other-loop")).edges.size,
        )
        assertEquals(graph, graph.removeEdge(DiagramEdgeId("missing")))
    }

    @Test
    fun subtreeIdsCollectsTransitiveChildren() {
        val graph = containerGraph()
        assertEquals(
            setOf("container", "childA", "grandchild"),
            graph.subtreeIds(DiagramNodeId("container")).map { it.value }.toSet(),
        )
    }

    @Test
    fun edgesConnectedToSeesBothEndpointKinds() {
        val graph = containerGraph().withEdge(
            DiagramEdge(
                id = DiagramEdgeId("fixed"),
                source = DiagramEndpoint.FixedPort(DiagramNodeId("other"), DiagramPortId("top")),
                target = DiagramEndpoint.FreePoint(400.0, 400.0),
            ),
        )
        assertEquals(
            setOf("container-other", "childA-other", "other-loop", "fixed"),
            graph.edgesConnectedTo(DiagramNodeId("other")).map { it.id.value }.toSet(),
        )
    }
}
