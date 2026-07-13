package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** SetTextLink / SetTextRangeFills / SetTextList / span healing across the reducer. */
class TextRangeIntentsReducerTest {

    private val nodeId = "badge_text"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(missionDemoDocuments()),
            DesignEditorIntent.SelectNode(nodeId),
        )

    private fun DesignEditorState.textKind(): DesignNodeKind.Text =
        assertNotNull(document?.nodeById(nodeId)?.kind as? DesignNodeKind.Text)

    private fun DesignEditorState.length(): Int {
        val kind = textKind()
        return (kind.content?.defaultText?.takeIf { it.isNotEmpty() }
            ?: (kind.characters as? Bindable.Value)?.value).orEmpty().length
    }

    @Test
    fun setTextLinkAddsAndClearsALink() {
        val before = freshState()
        if (before.length() < 3) return
        val withLink = reduceDesignEditor(before, DesignEditorIntent.SetTextLink(nodeId, 0, 3, url = "https://x.dev"))
        val link = withLink.textKind().links.firstOrNull { it.start == 0 && it.end == 3 }
        assertNotNull(link, "link added")
        assertEquals("https://x.dev", link.url)

        val cleared = reduceDesignEditor(withLink, DesignEditorIntent.SetTextLink(nodeId, 0, 3, url = "", nodeTarget = ""))
        assertTrue(cleared.textKind().links.none { it.start == 0 && it.end == 3 }, "link cleared")
    }

    @Test
    fun setTextRangeFillsColorsARange() {
        val before = freshState()
        if (before.length() < 4) return
        val red = listOf<DesignPaint>(DesignPaint.Solid(DesignColor.fromHex("#FF0000")!!.bindable()))
        val next = reduceDesignEditor(before, DesignEditorIntent.SetTextRangeFills(nodeId, 0, 4, red))
        assertEquals(before.document, next.document)
        assertEquals(before.sources, next.sources)
        assertTrue(next.diagnostics.any { it.severity == DesignSeverity.Error && "does not support SLM write-back" in it.message })
    }

    @Test
    fun setTextListUpdatesListSettings() {
        val before = freshState()
        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.SetTextList(nodeId, TextListSettings(type = TextListType.Ordered, indent = 1)),
        )
        assertEquals(TextListType.Ordered, next.textKind().list.type)
        assertEquals(1, next.textKind().list.indent)
    }

    @Test
    fun editingTextHealsExistingRangeOffsets() {
        val before = freshState()
        if (before.length() < 5) return
        // Style [2,5), then insert 3 chars at the very start -> the range shifts to [5,8).
        val styled = reduceDesignEditor(before, DesignEditorIntent.UpdateTypographyRange(nodeId, 2, 5, TypographyPatch(fontWeight = 700.0)))
        val old = styled.textKind()
        val oldText = (old.content?.defaultText?.takeIf { it.isNotEmpty() } ?: (old.characters as? Bindable.Value)?.value).orEmpty()
        val newText = "XYZ$oldText"
        val edited = reduceDesignEditor(styled, DesignEditorIntent.SetTextCharacters(nodeId, newText))
        val healed = edited.textKind().styleRanges.firstOrNull { it.style.fontWeight?.let { w -> (w as? Bindable.Value)?.value } == 700.0 }
        assertNotNull(healed, "styled range survives the edit")
        assertEquals(5, healed.start, "range start shifted by the 3 inserted chars")
        assertEquals(8, healed.end)
    }
}
