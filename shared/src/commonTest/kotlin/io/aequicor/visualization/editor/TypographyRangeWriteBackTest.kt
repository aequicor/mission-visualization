package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
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
    fun inlineRangeEditPersistsInOwningSource() {
        val before = freshState()
        val length = before.textKind().let { kind ->
            (kind.content?.defaultText?.takeIf { it.isNotEmpty() }
                ?: (kind.characters as? io.aequicor.visualization.engine.ir.model.Bindable.Value)?.value).orEmpty().length
        }
        assertTrue(length >= 2, "node must have at least two characters to style a range")
        val end = minOf(length, 3)

        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypographyRange(
                nodeId,
                0,
                end,
                TypographyPatch(fontFamily = "Source Sans 3", fontSize = 24.0),
            ),
        )

        assertNotEquals(before.document, next.document)
        assertNotEquals(before.sourceOf(owningFile), next.sourceOf(owningFile))
        before.sources.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(source.content, next.sourceOf(source.fileName), "unrelated source ${source.fileName}")
        }
        val range = assertNotNull(next.textKind().styleRanges.singleOrNull())
        assertEquals(0, range.start)
        assertEquals(end, range.end)
        assertEquals("Source Sans 3", range.style.fontFamily)
        assertEquals(24.0, range.style.fontSize?.literalOrNull())
        assertTrue("span (range (0 $end)" in next.sourceOf(owningFile))
    }

    @Test
    fun repeatedInlineRangeEditsAccumulateWithoutDivergence() {
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
        assertNotEquals(before.document, afterFirst.document)
        assertNotEquals(afterFirst.document, afterSecond.document)
        assertNotEquals(before.sources, afterSecond.sources)
        val ranges = afterSecond.textKind().styleRanges
        val bold = assertNotNull(ranges.firstOrNull { it.start == 0 && it.end == mid })
        val italic = assertNotNull(ranges.firstOrNull { it.start == mid && it.end == length })
        assertEquals(700.0, bold.style.fontWeight?.literalOrNull())
        assertEquals(true, italic.style.italic)
        assertEquals(afterSecond.document, freshStateFrom(afterSecond).document)
    }

    /** Recompile the emitted sources independently to prove the source is authoritative. */
    private fun freshStateFrom(state: DesignEditorState): DesignEditorState =
        createDesignEditorState(compileMissionDocuments(state.sources))
}
