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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagramWriteBackTest {

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

    private fun sourceWithDiagram(): String = listOf(
        "---",
        "screen: diagramScreen",
        "---",
        "",
        "# Diagram Screen",
        "",
        "## Canvas",
        "node: { id: canvas }",
        DiagramSlmExtension.write(baseGraph),
        "style:",
        "  fills:",
        "    - \"#FFFFFF\"",
        "",
        "## Sidebar",
        "node: { id: sidebar }",
        "",
    ).joinToString("\n")

    @Test
    fun replacesExistingDiagramBlockInPlace() {
        val source = sourceWithDiagram()
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

        val plan = diagramBlockSetPlan(source, "canvas", modified)
        val ops = assertIs<DiagramWriteBackPlan.Ops>(plan)
        val newSource = ops.applyTo(source)

        // The diagram round-trips to the modified graph.
        assertEquals(modified, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
        // Sibling entries and other sections stay untouched.
        assertTrue("style:" in newSource)
        assertTrue("\"#FFFFFF\"" in newSource)
        assertTrue("## Sidebar" in newSource)
        assertTrue("node: { id: canvas }" in newSource)
        // Exactly one diagram block remains.
        assertEquals(1, Regex("^diagram:", RegexOption.MULTILINE).findAll(newSource).count())
    }

    @Test
    fun insertsDiagramBlockWhenNodeHasNoneYet() {
        val source = listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            "## Canvas",
            "node: { id: canvas }",
            "",
        ).joinToString("\n")

        val plan = diagramBlockSetPlan(source, "canvas", baseGraph)
        val ops = assertIs<DiagramWriteBackPlan.Ops>(plan)
        val newSource = ops.applyTo(source)

        assertEquals(baseGraph, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
        // Inserted right below the node's typed entry.
        val nodeLine = newSource.lines().indexOfFirst { it == "node: { id: canvas }" }
        assertEquals("diagram:", newSource.lines()[nodeLine + 1])
    }

    @Test
    fun applyDiagramWriteBackAppliesAndVerifiesRoundTrip() {
        val source = sourceWithDiagram()
        val modified = baseGraph.updateNode(DiagramNodeId("b")) { it.copy(width = 160.0) }

        val result = applyDiagramWriteBack(source, "canvas", modified)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        val newSource = assertNotNull(result.newSource)
        assertEquals(modified, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
    }

    @Test
    fun failsForMissingNode() {
        val plan = diagramBlockSetPlan(sourceWithDiagram(), "ghost", baseGraph)
        val failed = assertIs<DiagramWriteBackPlan.Failed>(plan)
        assertTrue("ghost" in failed.message)
    }

    @Test
    fun failsWhenNodeHasNoTypedBlockAnchor() {
        val source = listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            "## Canvas",
            "",
        ).joinToString("\n")
        val compiled = compileWithDiagrams(source)
        val document = assertNotNull(compiled.document)
        // The heading node exists (auto id) but has no typed entries.
        val anchorId = document.pages.single().children.single().id

        val plan = diagramBlockSetPlan(source, anchorId, baseGraph)
        assertIs<DiagramWriteBackPlan.Failed>(plan)
    }

    @Test
    fun preservesIndentationOfNestedDiagramEntries() {
        // A diagram entry indented under a list-item anchor keeps its indent on rewrite.
        val source = listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            "## Canvas",
            "node: { id: canvas }",
            "",
            "- item",
            "  node: { id: listNode }",
            "  " + DiagramSlmExtension.write(baseGraph).lines().joinToString("\n  "),
            "",
        ).joinToString("\n")
        val compiled = compileWithDiagrams(source)
        assertNotNull(compiled.document)
        assertEquals(baseGraph, compiled.diagramGraphOf("listNode"))

        val modified = baseGraph.updateNode(DiagramNodeId("a")) { it.copy(x = 5.0) }
        val plan = diagramBlockSetPlan(source, "listNode", modified)
        val ops = assertIs<DiagramWriteBackPlan.Ops>(plan)
        val newSource = ops.applyTo(source)

        assertEquals(modified, compileWithDiagrams(newSource).diagramGraphOf("listNode"))
        assertTrue("  diagram:" in newSource, "indent lost:\n$newSource")
    }

    @Test
    fun emptyGraphWriteBackReplacesWithBareKey() {
        val source = sourceWithDiagram()
        val result = applyDiagramWriteBack(source, "canvas", DiagramGraph.Empty)
        assertTrue(result.isApplied, "write-back failed: ${result.message}")
        val newSource = assertNotNull(result.newSource)
        assertEquals(DiagramGraph.Empty, compileWithDiagrams(newSource).diagramGraphOf("canvas"))
        assertTrue("diagram:\nstyle:" in newSource, "expected bare diagram key:\n$newSource")
    }
}
