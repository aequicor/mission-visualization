package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import kotlin.test.Test

class DiagramChecksTest {

    private fun diagramDocument(graph: DiagramGraph): DesignDocument = DesignDocument(
        pages = listOf(
            DesignPage(
                id = "page",
                children = listOf(
                    DesignNode(id = "d1", type = "diagram", kind = DesignNodeKind.Diagram(graph)),
                ),
            ),
        ),
    )

    private fun diagramNode(
        id: String,
        parentId: String? = null,
        layerId: String? = null,
        ports: List<DiagramPort> = emptyList(),
    ): DiagramNode = DiagramNode(
        id = DiagramNodeId(id),
        x = 0.0,
        y = 0.0,
        width = 100.0,
        height = 50.0,
        ports = ports,
        parentId = parentId?.let(::DiagramNodeId),
        layerId = layerId?.let(::DiagramLayerId),
    )

    private fun edge(id: String, source: DiagramEndpoint, target: DiagramEndpoint): DiagramEdge =
        DiagramEdge(id = DiagramEdgeId(id), source = source, target = target)

    private fun floating(nodeId: String): DiagramEndpoint =
        DiagramEndpoint.FloatingAnchor(DiagramNodeId(nodeId))

    @Test
    fun validDiagramProducesNoDiagramDiagnostics() {
        val graph = DiagramGraph(
            nodes = listOf(
                diagramNode("a", layerId = "flow", ports = listOf(DiagramPort.side(DiagramNodeSide.RIGHT))),
                diagramNode("box"),
                diagramNode("b", parentId = "box"),
            ),
            edges = listOf(
                edge(
                    id = "e",
                    source = DiagramEndpoint.FixedPort(DiagramNodeId("a"), DiagramPortId("right")),
                    target = floating("b"),
                ),
            ),
            layers = listOf(DiagramLayer(DiagramLayerId("flow"), "Flow")),
            groups = listOf(
                DiagramGroup(DiagramGroupId("g"), listOf(DiagramNodeId("a"), DiagramNodeId("b"))),
            ),
        )
        val diagnostics = validateDesignDocument(diagramDocument(graph))
        (1..6).forEach { index -> diagnostics.assertNone("IR-DIAGRAM-00$index") }
    }

    @Test
    fun edgeReferencingMissingNodeIsAnError() {
        val graph = DiagramGraph(
            nodes = listOf(diagramNode("a")),
            edges = listOf(edge("e", floating("a"), floating("ghost"))),
        )
        validateDesignDocument(diagramDocument(graph))
            .assertHas("IR-DIAGRAM-001", messagePart = "missing node 'ghost'")
    }

    @Test
    fun edgeReferencingMissingPortIsAnError() {
        val graph = DiagramGraph(
            nodes = listOf(diagramNode("a"), diagramNode("b")),
            edges = listOf(
                edge(
                    id = "e",
                    source = DiagramEndpoint.FixedPort(DiagramNodeId("a"), DiagramPortId("nope")),
                    target = floating("b"),
                ),
            ),
        )
        validateDesignDocument(diagramDocument(graph))
            .assertHas("IR-DIAGRAM-002", messagePart = "missing port 'nope'")
    }

    @Test
    fun missingLayerReferenceIsAnError() {
        val graph = DiagramGraph(
            nodes = listOf(diagramNode("a", layerId = "ghostLayer")),
            edges = listOf(
                edge("e", floating("a"), DiagramEndpoint.FreePoint(5.0, 5.0))
                    .copy(layerId = DiagramLayerId("otherGhost")),
            ),
        )
        val diagnostics = validateDesignDocument(diagramDocument(graph))
        diagnostics.assertHas("IR-DIAGRAM-003", messagePart = "missing layer 'ghostLayer'")
        diagnostics.assertHas("IR-DIAGRAM-003", messagePart = "missing layer 'otherGhost'")
    }

    @Test
    fun missingParentReferenceIsAnError() {
        val graph = DiagramGraph(nodes = listOf(diagramNode("a", parentId = "ghost")))
        validateDesignDocument(diagramDocument(graph))
            .assertHas("IR-DIAGRAM-004", messagePart = "missing parent 'ghost'")
    }

    @Test
    fun parentCycleIsAnError() {
        val graph = DiagramGraph(
            nodes = listOf(
                diagramNode("a", parentId = "b"),
                diagramNode("b", parentId = "a"),
                diagramNode("child", parentId = "a"),
            ),
        )
        validateDesignDocument(diagramDocument(graph))
            .assertHas("IR-DIAGRAM-005", messagePart = "parent cycle")
    }

    @Test
    fun groupMemberReferencingMissingNodeIsAWarning() {
        val graph = DiagramGraph(
            nodes = listOf(diagramNode("a")),
            groups = listOf(
                DiagramGroup(DiagramGroupId("g"), listOf(DiagramNodeId("a"), DiagramNodeId("ghost"))),
            ),
        )
        validateDesignDocument(diagramDocument(graph))
            .assertHas("IR-DIAGRAM-006", messagePart = "missing node 'ghost'")
    }
}
