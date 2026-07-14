package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Per-range typography ([DesignEditorIntent.UpdateTypographyRange]) splits the node's
 * style ranges and writes them back as a `text.spans` block in the owning source, leaving
 * every other source byte-identical. `badge_text` is heading-anchored in
 * mission-telemetry.layout.md with no ICU params, so span offsets line up with the
 * authored `defaultText` and write-back is enabled.
 */
class TypographyRangeWriteBackTest {

    private val nodeId = "badge_text"
    private val owningFile = "mission-telemetry.layout.md"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(missionDemoDocuments()),
            DesignEditorIntent.SelectNode(nodeId),
        )

    private fun DesignEditorState.textKind(): DesignNodeKind.Text =
        assertNotNull(document?.nodeById(nodeId)?.kind as? DesignNodeKind.Text, "text node")

    private fun DesignEditorState.sourceOf(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing source $fileName").content

    @Test
    fun inlineRangeEditIsExplicitlyRejected() {
        val before = freshState()
        val length = before.textKind().let { kind ->
            (kind.content?.defaultText?.takeIf { it.isNotEmpty() }
                ?: (kind.characters as? io.aequicor.visualization.engine.ir.model.Bindable.Value)?.value).orEmpty().length
        }
        assertTrue(length >= 2, "node must have at least two characters to style a range")
        val end = minOf(length, 3)

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypographyRange(nodeId, 0, end, TypographyPatch(fontWeight = 700.0, italic = true)),
        )

        assertEquals(before.document, next.document)
        assertEquals(before.sources, next.sources)
        assertTrue(next.diagnostics.any { it.severity == DesignSeverity.Error && "does not support SLM write-back" in it.message })
    }

    @Test
    fun repeatedInlineRangeEditsRemainRejectedWithoutDivergence() {
        val before = freshState()
        val length = before.textKind().let { kind ->
            (kind.content?.defaultText?.takeIf { it.isNotEmpty() }
                ?: (kind.characters as? io.aequicor.visualization.engine.ir.model.Bindable.Value)?.value).orEmpty().length
        }
        if (length < 4) return
        val mid = length / 2
        val afterFirst = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypographyRange(nodeId, 0, mid, TypographyPatch(fontWeight = 700.0)),
        )
        val afterSecond = reduceDesignEditor(
            afterFirst,
            DesignEditorIntent.UpdateTypographyRange(nodeId, mid, length, TypographyPatch(italic = true)),
        )
        assertEquals(before.document, afterFirst.document)
        assertEquals(before.sources, afterFirst.sources)
        assertEquals(before.document, afterSecond.document)
        assertEquals(before.sources, afterSecond.sources)
        assertTrue(afterSecond.diagnostics.any { it.severity == DesignSeverity.Error && "does not support SLM write-back" in it.message })
    }
}
