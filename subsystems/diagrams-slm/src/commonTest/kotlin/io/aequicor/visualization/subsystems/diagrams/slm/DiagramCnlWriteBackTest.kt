package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.updateNode
import io.aequicor.visualization.subsystems.diagrams.model.withEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Write-back routing for CNL-authored diagrams: an edit of a `## Diagram: …` container
 * persists as the canonical CNL body sentences (never a `diagram:` YAML splice), the
 * heading line and sibling sections stay byte-identical, and unaddressable CNL nodes
 * fail the plan so the reducer keeps the edit in memory.
 */
class DiagramCnlWriteBackTest {

    private val baseGraph: DiagramGraph = DiagramGraph(
        nodes = listOf(
            DiagramNode(
                id = DiagramNodeId("a"),
                x = 0.0,
                y = 0.0,
                width = 100.0,
                height = 40.0,
                labels = listOf(DiagramLabel("A")),
            ),
            DiagramNode(
                id = DiagramNodeId("b"),
                x = 200.0,
                y = 0.0,
                width = 100.0,
                height = 40.0,
            ),
        ),
        edges = listOf(
            DiagramEdge(
                id = DiagramEdgeId("e1"),
                source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")),
            ),
        ),
    )

    private val heading = "## Diagram: Canvas id canvas 600 by 400 position 20 30"

    private fun cnlSource(body: List<String> = DiagramCnlWriter.sentences(baseGraph)): String = (
        listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            heading,
            "",
        ) + body + listOf(
            "",
            "## Frame: Sidebar id sidebar 200 by 400 position 640 30",
            "",
        )
        ).joinToString("\n")

    @Test
    fun cnlAuthoredEditPersistsAsCanonicalCnlBody() {
        val source = cnlSource()
        val modified = baseGraph
            .updateNode(DiagramNodeId("a")) { it.copy(x = 50.0, y = 25.0) }
            .withEdge(
                DiagramEdge(
                    id = DiagramEdgeId("e2"),
                    source = DiagramEndpoint.FloatingAnchor(DiagramNodeId("b")),
                    target = DiagramEndpoint.FloatingAnchor(DiagramNodeId("a")),
                    relation = DiagramRelation.Dependency,
                ),
            )

        val result = applyDiagramWriteBack(source, "canvas", modified)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        val newSource = assertNotNull(result.newSource)

        // Golden: the container body is exactly the canonical CNL emission.
        val expectedBody = DiagramCnlWriter.sentences(modified).joinToString("\n")
        assertTrue(expectedBody in newSource, "canonical CNL body expected in:\n$newSource")
        // The heading (design-node side) and the sibling section are untouched.
        assertTrue(heading in newSource, "container heading rewritten:\n$newSource")
        assertTrue("## Frame: Sidebar id sidebar 200 by 400 position 640 30" in newSource)
        // CNL persisted as CNL: no YAML splice anywhere.
        assertFalse(Regex("^\\s*diagram:", RegexOption.MULTILINE).containsMatchIn(newSource), "YAML diagram: block leaked into CNL source:\n$newSource")
        // Round trip.
        assertEquals(modified, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
    }

    @Test
    fun emptyContainerBodyGetsSentencesInsertedBelowHeading() {
        val source = cnlSource(body = emptyList())
        assertEquals(DiagramGraph.Empty, compileWithDiagrams(source).diagramGraphOf("canvas"))

        val result = applyDiagramWriteBack(source, "canvas", baseGraph)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        val newSource = assertNotNull(result.newSource)

        assertTrue(heading in newSource, "heading must stay untouched")
        val lines = newSource.lines()
        val headingIndex = lines.indexOf(heading)
        assertTrue(headingIndex >= 0)
        // Sentences follow the heading (blank-line separated), before the next section.
        val firstSentence = DiagramCnlWriter.sentences(baseGraph).first()
        assertEquals(firstSentence, lines[headingIndex + 2], "body inserted below the heading:\n$newSource")
        assertEquals(baseGraph, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
    }

    @Test
    fun emptiedGraphRemovesTheSentenceLines() {
        val source = cnlSource()
        val result = applyDiagramWriteBack(source, "canvas", DiagramGraph.Empty)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        val newSource = assertNotNull(result.newSource)

        assertTrue(heading in newSource, "heading must stay untouched")
        assertFalse("Node " in newSource, "sentences must be removed:\n$newSource")
        assertFalse("Edge " in newSource, "sentences must be removed:\n$newSource")
        assertEquals(DiagramGraph.Empty, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
    }

    @Test
    fun emptyGraphOnEmptyBodyIsANoOp() {
        val source = cnlSource(body = emptyList())
        val result = applyDiagramWriteBack(source, "canvas", DiagramGraph.Empty)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        assertEquals(source, result.newSource, "no-op edit must leave the source byte-identical")
    }

    @Test
    fun proseOutsideTheSentenceBlockSurvives() {
        val source = cnlSource(
            body = listOf("Review note kept as prose") + DiagramCnlWriter.sentences(baseGraph),
        )
        val modified = baseGraph.updateNode(DiagramNodeId("b")) { it.copy(width = 160.0) }

        val result = applyDiagramWriteBack(source, "canvas", modified)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        val newSource = assertNotNull(result.newSource)
        assertTrue("Review note kept as prose" in newSource, "prose before the sentences lost:\n$newSource")
        assertEquals(modified, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
    }

    @Test
    fun pureCnlFrameWithoutContainerFailsInsteadOfYamlSplice() {
        // A CNL `Frame:` heading cannot express a diagram graph in one surgical splice;
        // the plan must fail (reducer keeps the edit in memory) rather than splice YAML.
        val source = listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            "## Frame: Canvas id canvas 600 by 400 position 20 30",
            "",
        ).joinToString("\n")

        val plan = diagramBlockSetPlan(source, "canvas", baseGraph)
        assertIs<DiagramWriteBackPlan.Failed>(plan)
    }

    @Test
    fun missingNodeFailsThePlan() {
        val plan = diagramBlockSetPlan(cnlSource(), "ghost", baseGraph)
        val failed = assertIs<DiagramWriteBackPlan.Failed>(plan)
        assertTrue("ghost" in failed.message)
    }

    @Test
    fun rawYamlDiagramBlockWarnsAndStaysProse() {
        // Phase-3 guard: the retired `diagram:` YAML block form no longer reaches the
        // extension — the line warns and stays prose, the node never becomes a diagram.
        val source = listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            "## Frame: Canvas id canvas 600 by 400 position 20 30",
            "",
            "diagram:",
            "  nodes:",
            "    - id: a",
            "      x: 0",
            "      y: 0",
            "      w: 100",
            "      h: 40",
            "",
        ).joinToString("\n")

        val result = compileWithDiagrams(source)
        val document = assertNotNull(result.document, "document must still compile: ${result.diagnostics}")
        val canvas = assertNotNull(document.nodeById("canvas"))
        assertFalse(canvas.type == "diagram", "raw YAML must not create a diagram payload")
        assertTrue(
            result.diagnostics.any {
                "Raw YAML typed blocks are no longer supported" in it.message && "`diagram:`" in it.message
            },
            "expected the deprecation warning, got: ${result.diagnostics}",
        )
    }
}
