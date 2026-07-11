package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Diagram intents through [reduceDesignEditor]: every graph edit runs a pure
 * `:subsystems:diagrams` op and writes the whole `diagram:` block back into the owning
 * SLM source (round-trip veto), mirroring onto the working document in lock-step.
 */
class DiagramEditorReducerTest {

    private val fileName = "diagrams-test.layout.md"

    private val source = """
        |---
        |screen: diagramsTest
        |page: Diagrams Test
        |---
        |
        |# Diagrams Test
        |
        |node:
        |  id: frame_root
        |  name: Root
        |
        |## Canvas
        |
        |node:
        |  id: canvas
        |  name: Canvas
        |diagram:
        |  nodes:
        |    - id: a
        |      x: 20
        |      y: 20
        |      w: 120
        |      h: 60
        |      label: A
        |    - id: b
        |      x: 220
        |      y: 20
        |      w: 120
        |      h: 60
        |      label: B
        |    - id: grid
        |      type: table
        |      x: 20
        |      y: 160
        |      w: 240
        |      h: 64
        |      rows:
        |        - 32
        |        - 32
        |      columns:
        |        - 120
        |        - 120
        |  edges:
        |    - id: e1
        |      from: a
        |      to: b
        |      labels:
        |        - text: src
        |          position: source
        |        - text: dst
        |          position: target
    """.trimMargin()

    private fun freshState(): DesignEditorState =
        createDesignEditorState(compileMissionDocuments(listOf(MissionDocumentSource(fileName, source))))

    private fun DesignEditorState.graphOf(nodeId: String): DiagramGraph {
        val node = assertNotNull(document?.nodeById(nodeId), "node $nodeId missing")
        return assertIs<DesignNodeKind.Diagram>(node.kind, "node $nodeId is not a diagram").graph
    }

    private fun DesignEditorState.sourceContent(): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }).content

    @Test
    fun fixtureCompilesToDiagramNodeWithoutErrors() {
        val state = freshState()
        assertTrue(
            state.diagnostics.none { it.severity == DesignSeverity.Error },
            "unexpected errors: ${state.diagnostics}",
        )
        val graph = state.graphOf("canvas")
        assertEquals(3, graph.nodes.size)
        assertEquals(1, graph.edges.size)
    }

    @Test
    fun addDiagramNodeWritesBackIntoOwningSource() {
        val state = freshState()
        val before = state.sources

        val next = reduceDesignEditor(
            state,
            DiagramEditorIntent.AddDiagramNode(
                nodeId = "canvas",
                elementId = "c",
                payload = DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE),
                x = 420.0,
                y = 20.0,
                label = "C",
            ),
        )

        val graph = next.graphOf("canvas")
        val added = assertNotNull(graph.nodeById(DiagramNodeId("c")), "new element present in the graph")
        assertEquals(DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE), added.payload)
        assertEquals("C", added.labels.single().text)

        assertTrue("- id: c" in next.sourceContent(), "new element persisted in the diagram: block")
        assertEquals(listOf(before), next.previousSources, "source undo captured the pre-edit sources")
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error })
    }

    @Test
    fun writeBackRoundTripsThroughFullRecompile() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.AddDiagramNode(nodeId = "canvas", elementId = "c", x = 400.0, y = 40.0),
        )
        val recompiled = compileMissionDocuments(next.sources)
        val recompiledGraph =
            assertIs<DesignNodeKind.Diagram>(assertNotNull(recompiled.document?.nodeById("canvas")).kind).graph
        assertEquals(next.graphOf("canvas"), recompiledGraph, "in-memory graph == recompiled-source graph")
    }

    @Test
    fun connectNodesAddsValidatedEdge() {
        val state = freshState()
        val next = reduceDesignEditor(
            state,
            DiagramEditorIntent.ConnectDiagramNodes(
                nodeId = "canvas",
                edgeId = "e2",
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("grid")),
                label = "uses",
            ),
        )
        val edge = assertNotNull(next.graphOf("canvas").edgeById(DiagramEdgeId("e2")))
        assertEquals("uses", edge.labels.single().label.text)
        assertTrue("- id: e2" in next.sourceContent(), "edge persisted")

        // An endpoint referencing a missing node is refused: state unchanged.
        val invalid = reduceDesignEditor(
            next,
            DiagramEditorIntent.ConnectDiagramNodes(
                nodeId = "canvas",
                edgeId = "e3",
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("missing")),
            ),
        )
        assertSame(next, invalid, "unresolvable endpoint must be a no-op")
    }

    @Test
    fun reverseEdgeSwapsEndpointsAndSourceTargetLabels() {
        val next = reduceDesignEditor(freshState(), DiagramEditorIntent.ReverseDiagramEdge("canvas", "e1"))

        val edge = assertNotNull(next.graphOf("canvas").edgeById(DiagramEdgeId("e1")))
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")), edge.source)
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")), edge.target)
        val byText = edge.labels.associate { it.label.text to it.position }
        assertEquals(DiagramEdgeLabelPosition.TARGET, byText["src"], "source label moved to target")
        assertEquals(DiagramEdgeLabelPosition.SOURCE, byText["dst"], "target label moved to source")
    }

    @Test
    fun addTableRowGrowsGridAndNodeHeight() {
        val next = reduceDesignEditor(freshState(), DiagramEditorIntent.AddDiagramTableRow("canvas", "grid"))

        val grid = assertNotNull(next.graphOf("canvas").nodeById(DiagramNodeId("grid")))
        val table = assertIs<TableNode>(grid.payload)
        assertEquals(3, table.rowCount)
        assertEquals(64.0 + 32.0, grid.height, "row insertion grows the node height by the track size")
        assertTrue(next.previousSources.isNotEmpty(), "table edit wrote back to the source")
    }

    @Test
    fun deleteElementCascadesAttachedEdges() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.DeleteDiagramElement("canvas", elementIds = setOf("a")),
        )
        val graph = next.graphOf("canvas")
        assertEquals(null, graph.nodeById(DiagramNodeId("a")))
        assertEquals(null, graph.edgeById(DiagramEdgeId("e1")), "edge attached to the deleted node died with it")
        assertTrue("- id: a" !in next.sourceContent(), "deleted element left the source")
    }

    @Test
    fun undoRestoresThePreEditGraph() {
        val state = freshState()
        val edited = reduceDesignEditor(
            state,
            DiagramEditorIntent.MoveDiagramNode("canvas", "a", dx = 50.0, dy = 30.0),
        )
        val moved = assertNotNull(edited.graphOf("canvas").nodeById(DiagramNodeId("a")))
        assertEquals(70.0, moved.x)

        val undone = reduceDesignEditor(edited, DesignEditorIntent.Undo)
        val restored = assertNotNull(undone.graphOf("canvas").nodeById(DiagramNodeId("a")))
        assertEquals(20.0, restored.x, "undo restored the pre-move graph")
    }

    @Test
    fun intentsOnNonDiagramNodesAreNoOps() {
        val state = freshState()
        val next = reduceDesignEditor(state, DiagramEditorIntent.MoveDiagramNode("frame_root", "a", 10.0, 10.0))
        assertSame(state, next)
    }

    @Test
    fun insertTemplateMergesToTheRightWithFreshIds() {
        val state = freshState()
        val before = state.graphOf("canvas")
        val next = reduceDesignEditor(
            state,
            DiagramEditorIntent.InsertDiagramTemplate(nodeId = "canvas", templateId = "uml-class"),
        )
        val graph = next.graphOf("canvas")
        assertTrue(graph.nodes.size > before.nodes.size, "template content joined the graph")
        assertTrue(graph.edges.size > before.edges.size)
        // Existing content untouched.
        before.nodes.forEach { node -> assertEquals(node, graph.nodeById(node.id)) }
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error })
    }

    @Test
    fun mermaidImportAddsParsedNodes() {
        val state = freshState()
        val next = reduceDesignEditor(
            state,
            DiagramEditorIntent.ImportDiagramText(
                nodeId = "canvas",
                source = "flowchart TD\n  Start[Begin] --> Finish[End]",
            ),
        )
        val graph = next.graphOf("canvas")
        assertNotNull(graph.nodeById(DiagramNodeId("Start")), "imported node present")
        assertNotNull(graph.nodeById(DiagramNodeId("Finish")))
        assertEquals(2, graph.edges.size)
    }

    @Test
    fun bundledDiagramsScreenCompilesWithDiagramNodes() {
        val documents = compileMissionDocuments(DefaultDesignDocumentRepository().missionDocumentSources())
        assertTrue(
            documents.diagnostics.none { it.severity == DesignSeverity.Error },
            "bundled sources must compile clean: ${documents.diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        val document = assertNotNull(documents.document)
        val classDiagram = assertIs<DesignNodeKind.Diagram>(assertNotNull(document.nodeById("class_diagram")).kind)
        assertEquals(3, classDiagram.graph.nodes.size)
        assertEquals(3, classDiagram.graph.edges.size)
        val stateMachine = assertIs<DesignNodeKind.Diagram>(assertNotNull(document.nodeById("state_machine")).kind)
        assertEquals(4, stateMachine.graph.nodes.size)
    }
}
