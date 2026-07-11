package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal fun compileWithDiagrams(source: String): SlmCompileResult =
    compileSlm(source, SlmCompileOptions(extensions = DiagramSlmExtension.registry()))

internal fun SlmCompileResult.diagramGraphOf(nodeId: String): DiagramGraph {
    val document = assertNotNull(document, "document did not compile: $diagnostics")
    val node = assertNotNull(document.nodeById(nodeId), "node '$nodeId' not found")
    assertEquals("diagram", node.type)
    return (node.kind as DesignNodeKind.Diagram).graph
}

internal fun slmDocument(block: String, anchorId: String = "canvas"): String = listOf(
    "---",
    "screen: diagramScreen",
    "---",
    "",
    "# Diagram Screen",
    "",
    "## Canvas",
    "node: { id: $anchorId }",
    block,
).joinToString("\n")

/**
 * Golden round-trips: canonical SLM text -> reader -> writer -> byte-identical text.
 * The literals below are the canonical form emitted by [DiagramYamlWriter].
 */
class DiagramSlmGoldenTest {

    private val classDiagramBlock: String = listOf(
        "diagram:",
        "  nodes:",
        "    - id: shape",
        "      type: class",
        "      x: 40",
        "      y: 40",
        "      w: 180",
        "      h: 120",
        "      name: Shape",
        "      abstract: true",
        "      fields:",
        "        - \"+ origin: Point\"",
        "      methods:",
        "        - text: \"area(): Double\"",
        "          abstract: true",
        "    - id: circle",
        "      type: class",
        "      x: 40",
        "      y: 220",
        "      w: 180",
        "      h: 100",
        "      name: Circle",
        "      fields:",
        "        - \"- radius: Double\"",
        "      methods:",
        "        - \"+ area(): Double\"",
        "    - id: drawing",
        "      type: class",
        "      x: 300",
        "      y: 40",
        "      w: 200",
        "      h: 100",
        "      name: Drawing",
        "      stereotype: entity",
        "      methods:",
        "        - \"+ render(): Unit\"",
        "  edges:",
        "    - id: e1",
        "      from: circle",
        "      to: shape",
        "      relation: generalization",
        "    - id: e2",
        "      from: drawing",
        "      to: circle",
        "      relation: composition",
        "    - id: e3",
        "      from: drawing",
        "      to: shape",
        "      relation: association",
        "      label: draws",
    ).joinToString("\n")

    @Test
    fun classDiagramRoundTripsThroughReaderAndWriter() {
        val result = compileWithDiagrams(slmDocument(classDiagramBlock))
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "unexpected errors: ${result.diagnostics}",
        )
        val graph = result.diagramGraphOf("canvas")

        assertEquals(3, graph.nodes.size)
        assertEquals(3, graph.edges.size)
        val shape = graph.nodes.first()
        val payload = shape.payload as UmlClassNode
        assertEquals("Shape", payload.name)
        assertTrue(payload.abstract)
        assertEquals("origin: Point", payload.attributes.single().text)
        assertEquals(UmlVisibility.PUBLIC, payload.attributes.single().visibility)
        assertTrue(payload.operations.single().abstract)
        val circleFields = (graph.nodes[1].payload as UmlClassNode).attributes
        assertEquals(UmlVisibility.PRIVATE, circleFields.single().visibility)
        assertEquals(DiagramRelation.Generalization, graph.edges[0].relation)
        assertEquals(DiagramRelation.Composition, graph.edges[1].relation)
        assertEquals(DiagramRelation.Association(), graph.edges[2].relation)
        assertEquals("draws", graph.edges[2].labels.single().label.text)

        assertEquals(classDiagramBlock, DiagramSlmExtension.write(graph))
    }

    private val tableBlock: String = listOf(
        "diagram:",
        "  nodes:",
        "    - id: grid",
        "      type: table",
        "      x: 0",
        "      y: 0",
        "      w: 360",
        "      h: 96",
        "      rows:",
        "        - height: 32",
        "          header: true",
        "        - 32",
        "        - 32",
        "      columns:",
        "        - 120",
        "        - 120",
        "        - 120",
        "      cells:",
        "        - row: 0",
        "          col: 0",
        "          colSpan: 3",
        "          label: Header",
        "        - row: 1",
        "          col: 0",
        "          label: Alpha",
        "        - row: 1",
        "          col: 1",
        "          rowSpan: 2",
        "          label: Merged",
    ).joinToString("\n")

    @Test
    fun tableDiagramRoundTripsThroughReaderAndWriter() {
        val result = compileWithDiagrams(slmDocument(tableBlock))
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
        val graph = result.diagramGraphOf("canvas")

        val table = graph.nodes.single().payload as TableNode
        assertEquals(3, table.rowCount)
        assertEquals(3, table.columnCount)
        assertTrue(table.rows[0].header)
        assertEquals(3, table.cells[0].colSpan)
        assertEquals("Merged", assertNotNull(table.cellAt(2, 1)).label?.text)

        assertEquals(tableBlock, DiagramSlmExtension.write(graph))
    }

    private val stateMachineBlock: String = listOf(
        "diagram:",
        "  nodes:",
        "    - id: start",
        "      type: state",
        "      x: 20",
        "      y: 20",
        "      w: 24",
        "      h: 24",
        "      kind: initial",
        "    - id: active",
        "      type: state",
        "      x: 100",
        "      y: 20",
        "      w: 120",
        "      h: 60",
        "      name: Active",
        "    - id: done",
        "      type: state",
        "      x: 280",
        "      y: 20",
        "      w: 24",
        "      h: 24",
        "      kind: final",
        "  edges:",
        "    - id: t1",
        "      from: start",
        "      to: active",
        "      relation: transition",
        "    - id: t2",
        "      from: active",
        "      to: done",
        "      relation: transition",
        "      label: finish",
    ).joinToString("\n")

    @Test
    fun stateMachineRoundTripsThroughReaderAndWriter() {
        val result = compileWithDiagrams(slmDocument(stateMachineBlock))
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
        val graph = result.diagramGraphOf("canvas")

        assertEquals(UmlStateKind.INITIAL, (graph.nodes[0].payload as UmlStateNode).kind)
        assertEquals("Active", (graph.nodes[1].payload as UmlStateNode).name)
        assertEquals(UmlStateKind.FINAL, (graph.nodes[2].payload as UmlStateNode).kind)
        assertEquals(DiagramRelation.Transition, graph.edges[0].relation)

        assertEquals(stateMachineBlock, DiagramSlmExtension.write(graph))
    }

    @Test
    fun emptyDiagramBlockReadsAsEmptyGraphAndWritesBareKey() {
        val result = compileWithDiagrams(slmDocument("diagram:"))
        val graph = result.diagramGraphOf("canvas")
        assertEquals(DiagramGraph.Empty, graph)
        assertEquals("diagram:", DiagramSlmExtension.write(graph))
    }

    @Test
    fun brokenEdgeReferenceProducesReadableDiagnosticWithoutDroppingThePayload() {
        val block = listOf(
            "diagram:",
            "  nodes:",
            "    - id: user",
            "      type: rectangle",
            "      x: 0",
            "      y: 0",
            "      w: 100",
            "      h: 40",
            "  edges:",
            "    - id: e1",
            "      from: user",
            "      to: ghost",
        ).joinToString("\n")
        val result = compileWithDiagrams(slmDocument(block))

        val error = result.diagnostics.firstOrNull {
            it.severity == DesignSeverity.Error && "missing node 'ghost'" in it.message
        }
        assertNotNull(error, "expected a broken-reference error, got: ${result.diagnostics}")
        assertTrue("edge 'e1'" in error.message)

        // The payload still lands on the node (forward-compatible parse).
        val graph = result.diagramGraphOf("canvas")
        assertEquals(1, graph.edges.size)
    }

    @Test
    fun brokenPortAndLayerAndParentReferencesAreReported() {
        val block = listOf(
            "diagram:",
            "  nodes:",
            "    - id: a",
            "      type: rectangle",
            "      x: 0",
            "      y: 0",
            "      w: 10",
            "      h: 10",
            "      parent: nobody",
            "      layer: nolayer",
            "  edges:",
            "    - id: e1",
            "      from: a.nowhere",
            "      to: a",
        ).joinToString("\n")
        val result = compileWithDiagrams(slmDocument(block))
        val messages = result.diagnostics.map { it.message }

        assertTrue(messages.any { "port 'nowhere'" in it }, "missing port diagnostic: $messages")
        assertTrue(messages.any { "missing parent 'nobody'" in it }, "missing parent diagnostic: $messages")
        assertTrue(messages.any { "missing layer 'nolayer'" in it }, "missing layer diagnostic: $messages")
    }

    @Test
    fun duplicateNodeIdsAreDroppedWithError() {
        val block = listOf(
            "diagram:",
            "  nodes:",
            "    - id: a",
            "      type: rectangle",
            "      x: 0",
            "      y: 0",
            "      w: 10",
            "      h: 10",
            "    - id: a",
            "      type: ellipse",
            "      x: 50",
            "      y: 0",
            "      w: 10",
            "      h: 10",
        ).joinToString("\n")
        val result = compileWithDiagrams(slmDocument(block))
        val graph = result.diagramGraphOf("canvas")

        assertEquals(1, graph.nodes.size)
        assertEquals(
            DiagramNodePayload.BasicShape(),
            graph.nodes.single().payload,
            "the first occurrence wins",
        )
        assertTrue(result.diagnostics.any { "duplicate diagram node id 'a'" in it.message })
    }

    @Test
    fun unknownNodeTypeFallsBackToRectangleWithError() {
        val block = listOf(
            "diagram:",
            "  nodes:",
            "    - id: a",
            "      type: dodecahedron",
            "      x: 0",
            "      y: 0",
            "      w: 10",
            "      h: 10",
        ).joinToString("\n")
        val result = compileWithDiagrams(slmDocument(block))
        val graph = result.diagramGraphOf("canvas")

        assertEquals(DiagramNodePayload.BasicShape(), graph.nodes.single().payload)
        assertTrue(result.diagnostics.any { "unknown diagram node type 'dodecahedron'" in it.message })
    }
}
