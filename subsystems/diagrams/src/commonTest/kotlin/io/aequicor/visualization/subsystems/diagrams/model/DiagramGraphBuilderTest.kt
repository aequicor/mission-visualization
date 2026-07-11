package io.aequicor.visualization.subsystems.diagrams.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagramGraphBuilderTest {

    @Test
    fun buildsNodesEdgesLayersGroups() {
        val graph = diagramGraph {
            val background = layer("background")
            val a = node("a", x = 0.0, y = 0.0, width = 100.0, height = 50.0, label = "A")
            val b = node(
                "b",
                x = 200.0,
                y = 0.0,
                layerId = background,
                ports = DiagramPort.standardPorts(),
            )
            edge("a-b", a, b, relation = DiagramRelation.Generalization, label = "extends")
            group("g", listOf(a, b))
        }

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals(1, graph.layers.size)
        assertEquals(1, graph.groups.size)

        val a = graph.nodeById(DiagramNodeId("a"))!!
        assertEquals(listOf(DiagramLabel("A")), a.labels)
        assertEquals(100.0, a.width)

        val b = graph.nodeById(DiagramNodeId("b"))!!
        assertEquals(DiagramLayerId("background"), b.layerId)
        assertEquals(4, b.ports.size)

        val edge = graph.edgeById(DiagramEdgeId("a-b"))!!
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")), edge.source)
        assertEquals(DiagramRelation.Generalization, edge.relation)
        assertEquals(DiagramRoutingStyle.ORTHOGONAL, edge.routing)
        assertEquals(1, edge.labels.size)
        assertEquals(DiagramEdgeLabelPosition.MIDDLE, edge.labels.single().position)
    }

    @Test
    fun zOrderFollowsInsertionOrder() {
        val graph = diagramGraph {
            node("back")
            node("middle")
            node("front")
        }
        assertEquals(
            listOf("back", "middle", "front"),
            graph.nodes.map { it.id.value },
        )
    }

    @Test
    fun duplicateNodeIdsRejected() {
        assertFailsWith<IllegalArgumentException> {
            diagramGraph {
                node("a")
                node("a")
            }
        }
    }

    @Test
    fun edgeAllowsAtMostThreeLabelsAtDistinctPositions() {
        assertFailsWith<IllegalArgumentException> {
            DiagramEdge(
                id = DiagramEdgeId("e"),
                source = DiagramEndpoint.FreePoint(0.0, 0.0),
                target = DiagramEndpoint.FreePoint(10.0, 0.0),
                labels = listOf(
                    DiagramEdgeLabel(DiagramLabel("x"), DiagramEdgeLabelPosition.MIDDLE),
                    DiagramEdgeLabel(DiagramLabel("y"), DiagramEdgeLabelPosition.MIDDLE),
                ),
            )
        }
    }

    @Test
    fun portPositionsResolveAgainstNodeBounds() {
        val node = DiagramNode(
            id = DiagramNodeId("n"),
            x = 10.0,
            y = 20.0,
            width = 100.0,
            height = 40.0,
            ports = DiagramPort.standardPorts() + DiagramPort(
                id = DiagramPortId("custom"),
                anchor = DiagramPortAnchor.RelativePoint(0.25, 0.75),
            ),
        )
        val right = node.portPosition(node.portById(DiagramPortId("right"))!!)
        assertEquals(110.0, right.x)
        assertEquals(40.0, right.y)

        val custom = node.portPosition(node.portById(DiagramPortId("custom"))!!)
        assertEquals(35.0, custom.x)
        assertEquals(50.0, custom.y)
    }

    @Test
    fun reversedEdgeSwapsEndsArrowheadsAndLabels() {
        val edge = DiagramEdge(
            id = DiagramEdgeId("e"),
            source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
            target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")),
            sourceArrowhead = DiagramArrowhead(DiagramArrowheadKind.DIAMOND),
            targetArrowhead = DiagramArrowhead(DiagramArrowheadKind.OPEN),
            labels = listOf(
                DiagramEdgeLabel(DiagramLabel("s"), DiagramEdgeLabelPosition.SOURCE),
                DiagramEdgeLabel(DiagramLabel("m"), DiagramEdgeLabelPosition.MIDDLE),
                DiagramEdgeLabel(DiagramLabel("t"), DiagramEdgeLabelPosition.TARGET),
            ),
        )

        val reversed = edge.reversed()
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")), reversed.source)
        assertEquals(DiagramArrowheadKind.OPEN, reversed.sourceArrowhead.kind)
        assertEquals(DiagramArrowheadKind.DIAMOND, reversed.targetArrowhead.kind)
        val byText = reversed.labels.associate { it.label.text to it.position }
        assertEquals(DiagramEdgeLabelPosition.TARGET, byText["s"])
        assertEquals(DiagramEdgeLabelPosition.MIDDLE, byText["m"])
        assertEquals(DiagramEdgeLabelPosition.SOURCE, byText["t"])
    }

    @Test
    fun notationArrowheadsFollowUmlAndErConventions() {
        assertEquals(
            DiagramArrowheadKind.DIAMOND_FILLED,
            DiagramRelation.Composition.notationArrowheads().source.kind,
        )
        assertEquals(
            DiagramArrowheadKind.TRIANGLE,
            DiagramRelation.Generalization.notationArrowheads().target.kind,
        )
        assertTrue(DiagramRelation.Dependency.isDashedNotation)

        val er = DiagramRelation.EntityRelation(
            sourceCardinality = ErCardinality.ONE,
            targetCardinality = ErCardinality.ZERO_OR_MANY,
        ).notationArrowheads()
        assertEquals(DiagramArrowheadKind.ER_ONE, er.source.kind)
        assertEquals(DiagramArrowheadKind.ER_ZERO_OR_MANY, er.target.kind)
    }
}
