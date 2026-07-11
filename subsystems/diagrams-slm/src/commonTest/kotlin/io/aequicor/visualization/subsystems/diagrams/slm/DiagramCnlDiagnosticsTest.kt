package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Negative-path diagnostics of the diagram CNL grammar (spec §8), with source lines. */
class DiagramCnlDiagnosticsTest {

    private fun document(body: List<String>): String = (
        listOf(
            "---",
            "screen: diagramScreen",
            "---",
            "",
            "# Diagram Screen",
            "",
            "## Diagram: Canvas id canvas",
            "",
        ) + body
        ).joinToString("\n")

    /** 1-based source line of the [index]-th body line in [document]'s layout. */
    private fun bodyLine(index: Int): Int = 9 + index

    @Test
    fun duplicateNodeIdIsDroppedWithErrorAndFirstWins() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0",
                    "Node ellipse a 10 by 10 position 50 0",
                ),
            ),
        )
        val graph = result.diagramGraphOf("canvas")
        assertEquals(1, graph.nodes.size)
        assertEquals(DiagramNodePayload.BasicShape(), graph.nodes.single().payload, "the first occurrence wins")
        val error = assertNotNull(
            result.diagnostics.firstOrNull { "duplicate diagram node id 'a'" in it.message },
            "duplicate-id error expected: ${result.diagnostics}",
        )
        assertEquals(DesignSeverity.Error, error.severity)
        assertEquals(bodyLine(1), error.location?.line, "error must point at the duplicate sentence")
    }

    @Test
    fun danglingEdgeReferenceReportsAtTheEdgeSentenceLine() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle user 100 by 40 position 0 0",
                    "",
                    "Edge e1 from user to ghost",
                ),
            ),
        )
        val error = assertNotNull(
            result.diagnostics.firstOrNull {
                it.severity == DesignSeverity.Error && "missing node 'ghost'" in it.message
            },
            "broken-reference error expected: ${result.diagnostics}",
        )
        assertTrue("edge 'e1'" in error.message)
        assertTrue("`to`" in error.message)
        assertEquals(bodyLine(2), error.location?.line)
        // Forward-compatible parse: the edge itself is kept, validation reports it.
        assertEquals(1, result.diagramGraphOf("canvas").edges.size)
    }

    @Test
    fun danglingPortParentAndLayerReferencesAreReported() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0 parent nobody layer nolayer",
                    "Edge e1 from a.nowhere to a",
                ),
            ),
        )
        val messages = result.diagnostics.map { it.message }
        assertTrue(messages.any { "port 'nowhere'" in it }, "missing port diagnostic: $messages")
        assertTrue(messages.any { "missing parent 'nobody'" in it }, "missing parent diagnostic: $messages")
        assertTrue(messages.any { "missing layer 'nolayer'" in it }, "missing layer diagnostic: $messages")
    }

    @Test
    fun missingGroupMemberIsAWarning() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0",
                    "Group g1 members (a ghost)",
                ),
            ),
        )
        val warning = assertNotNull(
            result.diagnostics.firstOrNull { "diagram group 'g1' references missing node 'ghost'" in it.message },
        )
        assertEquals(DesignSeverity.Warning, warning.severity)
        assertEquals(bodyLine(1), warning.location?.line)
    }

    @Test
    fun ambiguousDottedEndpointIsAnErrorAndTheEdgeIsDropped() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0 port (b right)",
                    "Node rectangle «a.b» 10 by 10 position 50 0",
                    "Edge e1 from a.b to a",
                ),
            ),
        )
        val graph = result.diagramGraphOf("canvas")
        assertEquals(0, graph.edges.size, "the ambiguous edge must be dropped")
        val error = assertNotNull(
            result.diagnostics.firstOrNull { "ambiguous endpoint 'a.b'" in it.message },
            "ambiguity error expected: ${result.diagnostics}",
        )
        assertEquals(DesignSeverity.Error, error.severity)
    }

    @Test
    fun unambiguousDottedTokenResolvesToTheFixedPort() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0 port (b right)",
                    "Edge e1 from a.b to a",
                ),
            ),
        )
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
        val edge = result.diagramGraphOf("canvas").edges.single()
        val fixed = edge.source as DiagramEndpoint.FixedPort
        assertEquals("a", fixed.nodeId.value)
        assertEquals("b", fixed.portId.value)
    }

    @Test
    fun dottedNodeIdWithoutPortResolvesToTheFloatingAnchor() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle «a.b» 10 by 10 position 0 0",
                    "Node rectangle c 10 by 10 position 50 0",
                    "Edge e1 from a.b to c",
                ),
            ),
        )
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
        val edge = result.diagramGraphOf("canvas").edges.single()
        assertEquals("a.b", (edge.source as DiagramEndpoint.FloatingAnchor).nodeId.value)
    }

    @Test
    fun unknownNodeTypeWordDropsTheSentenceWithAnError() {
        val result = compileWithDiagrams(
            document(listOf("Node dodecahedron d1 10 by 10 position 0 0")),
        )
        assertEquals(0, result.diagramGraphOf("canvas").nodes.size)
        assertTrue(result.diagnostics.any { "unknown diagram node type 'dodecahedron'" in it.message })
    }

    @Test
    fun missingRequiredKindWordDropsTheSentence() {
        val result = compileWithDiagrams(
            document(listOf("Node flowchart f1 140 by 80 position 0 0")),
        )
        assertEquals(0, result.diagramGraphOf("canvas").nodes.size)
        assertTrue(result.diagnostics.any { "missing its flowchart kind word" in it.message })
    }

    @Test
    fun portOffsetOutsideRangeIsCoercedWithAWarning() {
        val result = compileWithDiagrams(
            document(listOf("Node rectangle a 10 by 10 position 0 0 port (out right 1.5)")),
        )
        val graph = result.diagramGraphOf("canvas")
        val port = graph.nodes.single().ports.single()
        assertTrue(result.diagnostics.any { "offset coerced into 0..1" in it.message })
        assertEquals("out", port.id.value)
    }

    @Test
    fun extraLabelAtTheSamePositionKeepsTheFirstWithAWarning() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0",
                    "Node rectangle b 10 by 10 position 40 0",
                    "Edge e1 from a to b label «first» label «second»",
                ),
            ),
        )
        val edge = result.diagramGraphOf("canvas").edges.single()
        assertEquals(listOf("first"), edge.labels.map { it.label.text })
        assertTrue(result.diagnostics.any { "multiple labels at middle" in it.message })
    }

    @Test
    fun emptyGroupMembersDropTheSentenceWithAnError() {
        val result = compileWithDiagrams(
            document(listOf("Group g1 members ()")),
        )
        assertEquals(0, result.diagramGraphOf("canvas").groups.size)
        assertTrue(result.diagnostics.any { "must list at least one member" in it.message })
    }

    @Test
    fun duplicateGroupMembersAreDroppedWithAnError() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a 10 by 10 position 0 0",
                    "Group g1 members (a a)",
                ),
            ),
        )
        val group = result.diagramGraphOf("canvas").groups.single()
        assertEquals(listOf("a"), group.memberIds.map { it.value })
        assertTrue(result.diagnostics.any { "duplicate members" in it.message })
    }

    @Test
    fun outOfGridTableCellIsDroppedWithAnError() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node table t 100 by 60 position 0 0 row 32 col 100 cell (0 0 «ok») cell (1 0 «out»)",
                ),
            ),
        )
        val table = result.diagramGraphOf("canvas").nodes.single().payload
        val cells = (table as TableNode).cells
        assertEquals(listOf("ok"), cells.mapNotNull { it.label?.text })
        assertTrue(result.diagnostics.any { "out of the 1x1 grid" in it.message })
    }

    @Test
    fun nodeMissingSizeOrPositionIsDropped() {
        val result = compileWithDiagrams(
            document(
                listOf(
                    "Node rectangle a position 0 0",
                    "Node rectangle b 10 by 10",
                ),
            ),
        )
        assertEquals(0, result.diagramGraphOf("canvas").nodes.size)
        assertTrue(result.diagnostics.any { "missing its `<w> by <h>` size" in it.message })
        assertTrue(result.diagnostics.any { "missing its `position <x> <y>`" in it.message })
    }
}
