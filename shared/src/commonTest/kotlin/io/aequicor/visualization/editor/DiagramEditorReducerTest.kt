package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.data.MissionDiagramsSlm
import io.aequicor.visualization.editor.data.ProjectStructureSlm
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
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramEdgeEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Diagram intents through [reduceDesignEditor]: every graph edit runs a pure
 * `:subsystems:diagrams` op and writes the canonical `## Diagram:` CNL body back into the owning
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
        |# Diagrams Test id frame_root name «Root»
        |
        |## Diagram: Canvas id canvas
        |
        |Node rectangle a 120 by 60 position 20 20 label «A»
        |Node rectangle b 120 by 60 position 220 20 label «B»
        |Node table grid 240 by 64 position 20 160 row 32 row 32 col 120 col 120
        |Edge e1 from a to b label («src» at source) label («dst» at target)
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

        assertTrue("Node ellipse c" in next.sourceContent(), "new element persisted as a CNL sentence")
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
        assertTrue("Edge e2 from a to grid" in next.sourceContent(), "edge persisted as a CNL sentence")

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
    fun reconnectEdgeRepinsTheDraggedEndAndRefusesMissingNodes() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.ReconnectDiagramEdge(
                nodeId = "canvas",
                edgeId = "e1",
                end = DiagramEdgeEnd.TARGET,
                endpoint = DiagramEndpoint.FloatingAnchor(DiagramNodeId("grid")),
            ),
        )
        val edge = assertNotNull(next.graphOf("canvas").edgeById(DiagramEdgeId("e1")))
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")), edge.source, "source end untouched")
        assertEquals(DiagramEndpoint.FloatingAnchor(DiagramNodeId("grid")), edge.target, "target re-pinned to grid")
        assertTrue(next.previousSources.isNotEmpty(), "reconnect wrote back to the source")
        assertTrue("Edge e1 from a to grid" in next.sourceContent(), "re-pin persisted as a CNL sentence")

        // Re-pinning to a missing node is refused: state unchanged.
        val invalid = reduceDesignEditor(
            next,
            DiagramEditorIntent.ReconnectDiagramEdge(
                nodeId = "canvas",
                edgeId = "e1",
                end = DiagramEdgeEnd.SOURCE,
                endpoint = DiagramEndpoint.FloatingAnchor(DiagramNodeId("missing")),
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
        assertTrue("Node rectangle a" !in next.sourceContent(), "deleted element left the source")
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
    fun tidyAlignSnapsRowsAndWritesBack() {
        // Knock `b` slightly off `a`'s row, then tidy: the near-row snaps back onto one
        // shared axis and the snapped positions persist into the SLM source.
        val nudged = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.MoveDiagramNode("canvas", "b", 0.0, 14.0),
        )
        val next = reduceDesignEditor(nudged, DiagramEditorIntent.ApplyDiagramTidyAlign("canvas"))

        val graph = next.graphOf("canvas")
        val a = assertNotNull(graph.nodeById(DiagramNodeId("a")))
        val b = assertNotNull(graph.nodeById(DiagramNodeId("b")))
        assertEquals(a.y, b.y, "near-row snaps onto one shared y")
        assertTrue(a.x < b.x, "x order preserved")

        val recompiled = compileMissionDocuments(next.sources)
        val recompiledGraph =
            assertIs<DesignNodeKind.Diagram>(assertNotNull(recompiled.document?.nodeById("canvas")).kind).graph
        assertEquals(graph, recompiledGraph, "tidied positions persist into the source")
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error })
    }

    @Test
    fun autoLayoutPresetChangesSpacingAndWritesBack() {
        // PUBLICATION spreads layers wider than DEFAULT — the preset must reach the engine
        // through the reducer, not be hardcoded, and the new coordinates must persist.
        fun layerPitch(preset: io.aequicor.visualization.subsystems.diagrams.layout.DiagramLayoutPreset): Double {
            val next = reduceDesignEditor(
                freshState(),
                DiagramEditorIntent.ApplyDiagramAutoLayout("canvas", preset = preset),
            )
            val graph = next.graphOf("canvas")
            val a = assertNotNull(graph.nodeById(DiagramNodeId("a")))
            val b = assertNotNull(graph.nodeById(DiagramNodeId("b")))
            // Verify the write-back round-trips through a full recompile.
            val recompiled = compileMissionDocuments(next.sources)
            val recompiledGraph =
                assertIs<DesignNodeKind.Diagram>(assertNotNull(recompiled.document?.nodeById("canvas")).kind).graph
            assertEquals(graph, recompiledGraph, "laid-out positions persist into the source")
            return kotlin.math.abs(b.bounds.centerY - a.bounds.centerY) +
                kotlin.math.abs(b.bounds.centerX - a.bounds.centerX)
        }
        val default = layerPitch(io.aequicor.visualization.subsystems.diagrams.layout.DiagramLayoutPreset.DEFAULT)
        val publication = layerPitch(io.aequicor.visualization.subsystems.diagrams.layout.DiagramLayoutPreset.PUBLICATION)
        assertTrue(publication > default, "publication preset spreads wider ($publication vs $default)")
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
    fun fixtureDiagramScreensCompileWithDiagramNodes() {
        val documents = compileMissionDocuments(
            listOf(
                MissionDocumentSource("diagrams.layout.md", MissionDiagramsSlm),
                MissionDocumentSource("project-structure.layout.md", ProjectStructureSlm),
            ),
        )
        assertTrue(
            documents.diagnostics.none { it.severity == DesignSeverity.Error },
            "fixture sources must compile clean: ${documents.diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        val document = assertNotNull(documents.document)
        val classDiagram = assertIs<DesignNodeKind.Diagram>(assertNotNull(document.nodeById("class_diagram")).kind)
        assertEquals(3, classDiagram.graph.nodes.size)
        assertEquals(3, classDiagram.graph.edges.size)
        val stateMachine = assertIs<DesignNodeKind.Diagram>(assertNotNull(document.nodeById("state_machine")).kind)
        assertEquals(4, stateMachine.graph.nodes.size)
        val moduleGraph = assertIs<DesignNodeKind.Diagram>(assertNotNull(document.nodeById("module_graph")).kind)
        assertEquals(21, moduleGraph.graph.nodes.size)
        assertEquals(27, moduleGraph.graph.edges.size)
    }

    @Test
    fun bundledDiagramScreensAreAuthoredAsPureCnl() {
        // Migration pin: the bundled Welcome screens author diagrams as `## Diagram:`
        // CNL containers; no raw `diagram:` YAML block appears in any bundled source.
        val sources = DefaultDesignDocumentRepository().missionDocumentSources()
        val umlScreen = assertNotNull(
            sources.firstOrNull { it.fileName == "welcome-uml.layout.md" },
            "the Welcome UML screen is bundled",
        )
        assertTrue("## Diagram:" in umlScreen.content, "${umlScreen.fileName} authors diagrams as CNL containers")
        sources.forEach { source ->
            assertTrue(
                !Regex("^\\s*diagram:", RegexOption.MULTILINE).containsMatchIn(source.content),
                "${source.fileName} must not carry a raw diagram: YAML block",
            )
        }
    }

    @Test
    fun editOnBundledCnlDiagramPersistsAsCnlSentences() {
        val diagrams = MissionDocumentSource("diagrams.layout.md", MissionDiagramsSlm)
        val state = createDesignEditorState(compileMissionDocuments(listOf(diagrams)))

        val next = reduceDesignEditor(
            state,
            DiagramEditorIntent.MoveDiagramNode(
                nodeId = "class_diagram",
                elementId = "circle",
                dx = 10.0,
                dy = 5.0,
            ),
        )

        val moved = assertNotNull(next.graphOf("class_diagram").nodeById(DiagramNodeId("circle")))
        assertEquals(70.0, moved.x)
        assertEquals(225.0, moved.y)
        val content = assertNotNull(next.sources.single().content)
        assertTrue(
            "Node class circle «Circle» 180 by 100 position 70 225 field (- «radius: Double») method (+ «area(): Double»)" in content,
            "moved element persisted as a canonical CNL sentence:\n$content",
        )
        assertTrue("diagram:" !in content, "no YAML splice into the CNL source")
        assertTrue(
            next.diagnostics.none { "kept in memory" in it.message },
            "write-back must not fall back to memory: ${next.diagnostics}",
        )
    }
}
