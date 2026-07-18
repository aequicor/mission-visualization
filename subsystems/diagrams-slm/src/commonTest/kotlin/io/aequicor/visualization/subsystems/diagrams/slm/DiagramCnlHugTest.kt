package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `hug` bareword: a node may declare that edits re-fit it to its caption.
 *
 * CNL is strict — an unknown word is a hard error, so reader, writer and the authoring doc
 * have to move together, and `<w> by <h>` stays mandatory even with `hug` (it is the last
 * measured result, which keeps a round-trip independent of platform font metrics).
 */
class DiagramCnlHugTest {

    private fun document(body: List<String>): String = (
        listOf("---", "screen: hugScreen", "---", "", "# Hug Screen", "", "## Diagram: Canvas id canvas", "") + body
        ).joinToString("\n")

    @Test
    fun hugIsParsedFromTheNodeSentence() {
        val result = compileWithDiagrams(
            document(listOf("Node use-case uc «Submit mission» 180 by 80 hug position 20 20")),
        )

        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error }, "${result.diagnostics}")
        val node = result.diagramGraphOf("canvas").nodeById(DiagramNodeId("uc"))
        assertEquals(DiagramNodeSizing.Hug, node?.sizing)
        assertEquals(180.0, node?.width, "the authored size survives as the last measured result")
    }

    @Test
    fun aNodeWithoutHugStaysFixed() {
        val result = compileWithDiagrams(
            document(listOf("Node use-case uc «Submit mission» 180 by 80 position 20 20")),
        )

        assertEquals(
            DiagramNodeSizing.Fixed,
            result.diagramGraphOf("canvas").nodeById(DiagramNodeId("uc"))?.sizing,
            "every existing document must keep authored geometry authoritative",
        )
    }

    @Test
    fun hugSurvivesReadWriteRead() {
        val first = compileWithDiagrams(
            document(listOf("Node use-case uc «Submit mission» 180 by 80 hug position 20 20")),
        ).diagramGraphOf("canvas")

        val emitted = DiagramCnlWriter.sentences(first)
        assertTrue(emitted.any { "hug" in it }, "the writer must emit hug: $emitted")
        assertTrue(emitted.any { "180 by 80" in it }, "the size is written alongside hug: $emitted")

        val second = compileWithDiagrams(document(emitted)).diagramGraphOf("canvas")
        assertEquals(first, second, "hug must round-trip")
    }
}
