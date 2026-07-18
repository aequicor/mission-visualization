package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.cnl.emitCnlContainerSection
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compile-level coverage of the `## Diagram: …` CNL container: the body sentences land as
 * [DesignNodeKind.Diagram] on the container node (typed end to end, no YAML) and the
 * heading properties stay on the design node. Includes the [emitCnlContainerSection]
 * hook round trip.
 */
class DiagramCnlCompileTest {

    private fun cnlDocument(body: List<String>, heading: String): String = (
        listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            heading,
            "",
        ) + body
        ).joinToString("\n")

    private val classHeading =
        "## Diagram: Class Diagram id class_canvas 600 by 400 position 20 30"

    /** §9.2 migration sample: the golden YAML class diagram translated to CNL sentences. */
    private val classSentences: List<String> = listOf(
        "Node class shape «Shape» abstract 180 by 120 position 40 40 field (+ «origin: Point») method (+ abstract «area(): Double»)",
        "Node class circle «Circle» 180 by 100 position 40 220 field (- «radius: Double») method (+ «area(): Double»)",
        "Node class drawing «Drawing» stereotype «entity» 200 by 100 position 300 40 method (+ «render(): Unit»)",
        "",
        "Edge e1 from circle to shape relation generalization",
        "Edge e2 from drawing to circle relation composition",
        "Edge e3 from drawing to shape relation association label «draws»",
    )

    @Test
    fun cnlAuthoredDiagramCompilesToTheExpectedGraph() {
        val cnlResult = compileWithDiagrams(cnlDocument(classSentences, classHeading))
        assertTrue(
            cnlResult.diagnostics.none { it.severity == DesignSeverity.Error },
            "unexpected errors: ${cnlResult.diagnostics}",
        )
        val cnlGraph = cnlResult.diagramGraphOf("class_canvas")

        assertEquals(listOf("shape", "circle", "drawing"), cnlGraph.nodes.map { it.id.value })
        val shape = cnlGraph.nodes.first().payload as UmlClassNode
        assertTrue(shape.abstract)
        assertEquals("Shape", shape.name)
        assertEquals(listOf("origin: Point"), shape.attributes.map { it.text })
        assertTrue(shape.operations.single().abstract)
        val drawing = cnlGraph.nodes.last().payload as UmlClassNode
        assertEquals("entity", drawing.stereotype)
        assertEquals(listOf("e1", "e2", "e3"), cnlGraph.edges.map { it.id.value })
        assertEquals("draws", cnlGraph.edges.last().labels.single().label.text)

        // The canonical emitter reproduces the authored body exactly (grammar symmetry).
        assertEquals(
            classSentences.filter { it.isNotBlank() },
            DiagramCnlWriter.sentences(cnlGraph),
        )
    }

    @Test
    fun headingPropertiesLandOnTheDesignNodeNotTheGraph() {
        val result = compileWithDiagrams(cnlDocument(classSentences, classHeading))
        val document = assertNotNull(result.document)
        val node = assertNotNull(document.nodeById("class_canvas"))
        assertEquals("diagram", node.type)
        assertEquals("Class Diagram", node.name)
        assertEquals(600.0, node.size.width)
        assertEquals(400.0, node.size.height)
        assertEquals(DesignPoint(20.0, 30.0), node.position)
    }

    @Test
    fun emptyDiagramContainerYieldsTheEmptyGraph() {
        val result = compileWithDiagrams(cnlDocument(emptyList(), "## Diagram: Blank id blank_canvas"))
        assertEquals(DiagramGraph.Empty, result.diagramGraphOf("blank_canvas"))
    }

    @Test
    fun globalElementNounsAreInactiveInsideTheDiagramBody() {
        val body = listOf(
            "Node rectangle r1 100 by 40 position 0 0",
            "",
            "Just a prose note here",
            "Rectangle 10 by 20 color #FFFFFF",
            "",
            "Group g1 «Cluster» members (r1)",
        )
        val result = compileWithDiagrams(cnlDocument(body, "## Diagram: Scoped id scoped_canvas"))
        val graph = result.diagramGraphOf("scoped_canvas")

        assertEquals(listOf("r1"), graph.nodes.map { it.id.value })
        assertEquals(DiagramNodePayload.BasicShape(), graph.nodes.single().payload)
        assertEquals(listOf("g1"), graph.groups.map { it.id.value })
        assertEquals("Cluster", graph.groups.single().name)

        // The global `Rectangle` noun must NOT create a shape child inside the container.
        val document = assertNotNull(result.document)
        val diagramNode = assertNotNull(document.nodeById("scoped_canvas"))
        assertTrue(
            collectSubtree(diagramNode).none { it.kind is DesignNodeKind.Shape },
            "global CNL noun leaked into the diagram scope",
        )

        val proseWarnings = result.diagnostics.filter { "is not a diagram sentence" in it.message }
        assertEquals(2, proseWarnings.size, "expected 2 typo-guard warnings, got: ${result.diagnostics}")
    }

    @Test
    fun sectionAfterTheDiagramContainerLeavesTheScope() {
        val source = cnlDocument(
            body = listOf(
                "Node rectangle r1 100 by 40 position 0 0",
                "",
                "## After",
                "",
                "Rectangle 10 by 20 color #336699",
            ),
            heading = "## Diagram: Scoped id scoped_canvas",
        )
        val result = compileWithDiagrams(source)
        val graph = result.diagramGraphOf("scoped_canvas")
        assertEquals(1, graph.nodes.size)

        // Outside the container the global noun is active again.
        val document = assertNotNull(result.document)
        val shapes = document.pages
            .flatMap { page -> page.children.flatMap(::collectSubtree) }
            .filter { it.kind is DesignNodeKind.Shape }
        assertEquals(1, shapes.size, "the Rectangle after the container must be a design node")
    }

    @Test
    fun emitCnlContainerSectionRoundTripsThroughCompile() {
        val first = compileWithDiagrams(cnlDocument(classSentences, classHeading))
        val document = assertNotNull(first.document)
        val node = assertNotNull(document.nodeById("class_canvas"))
        val section = assertNotNull(
            emitCnlContainerSection(node, level = 2, extensions = DiagramSlmExtension.registry()),
            "diagram node must emit a CNL container section",
        )
        assertTrue(section.first().startsWith("## Diagram:"), "heading line expected, got: ${section.first()}")

        val recompiled = compileWithDiagrams(
            (listOf("---", "screen: diagramScreen", "---", "", "# Diagram Screen", "") + section).joinToString("\n"),
        )
        assertTrue(
            recompiled.diagnostics.none { it.severity == DesignSeverity.Error },
            "re-emitted section must compile cleanly: ${recompiled.diagnostics}\n${section.joinToString("\n")}",
        )
        val recompiledGraph = recompiled.diagramGraphOf("class_canvas")
        assertEquals(first.diagramGraphOf("class_canvas"), recompiledGraph)
        val recompiledNode = assertNotNull(assertNotNull(recompiled.document).nodeById("class_canvas"))
        assertEquals("Class Diagram", recompiledNode.name)
    }

    private fun collectSubtree(node: DesignNode): List<DesignNode> =
        listOf(node) + node.children.flatMap { collectSubtree(it) }

    /**
     * Real agent-authored prose quotes with raw nested «…» inside a phrase (the
     * house-inspection ER reference file broke the whole folder open on this): the
     * balanced scanner must accept it, and the canonical writer re-emits it escaped
     * so the round trip is exact.
     */
    @Test
    fun nestedGuillemetsInsideNotePhraseCompileAndRoundTrip() {
        val heading = "## Diagram: Nested id nested_canvas 600 by 400 position 0 0"
        val body = listOf(
            "Node note rule «Признак «отверстие отсутствует» — не ноль; ключ — «квартира + обследование».» 300 by 120 position 20 20",
        )
        val result = compileWithDiagrams(cnlDocument(body, heading))
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "nested guillemets must compile: ${result.diagnostics}",
        )
        val graph = result.diagramGraphOf("nested_canvas")
        val note = assertNotNull(graph.nodes.single().payload as? UmlNoteNode)
        assertEquals("Признак «отверстие отсутствует» — не ноль; ключ — «квартира + обследование».", note.text)

        val emitted = DiagramCnlWriter.sentences(graph).single()
        assertTrue("""\«""" in emitted && """\»""" in emitted, "canonical form escapes nesting: $emitted")
        val reparsed = compileWithDiagrams(cnlDocument(listOf(emitted), heading))
        assertTrue(
            reparsed.diagnostics.none { it.severity == DesignSeverity.Error },
            "canonical form must recompile: ${reparsed.diagnostics}",
        )
        assertEquals(graph, reparsed.diagramGraphOf("nested_canvas"))
    }
}
