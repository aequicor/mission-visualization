package io.aequicor.visualization.editor.ui

import androidx.compose.ui.text.TextRange
import io.aequicor.visualization.editor.presentation.BoundsBox
import io.aequicor.visualization.editor.presentation.TextSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextEditBehaviorTest {

    @Test
    fun entryClickStartsWithCaretAtClickedOffset() {
        assertEquals(
            TextRange(4),
            initialTextFieldSelection(
                textLength = 10,
                nodeId = "title",
                explicitSelection = TextSelection("title", 1, 8),
                clickedOffset = 4,
            ),
        )
    }

    @Test
    fun explicitSelectionIsPreservedWithoutEntryClick() {
        assertEquals(
            TextRange(8, 2),
            initialTextFieldSelection(
                textLength = 10,
                nodeId = "title",
                explicitSelection = TextSelection("title", 8, 2),
                clickedOffset = null,
            ),
        )
    }

    @Test
    fun ordinaryNonPointerEntryUsesEndCaretInsteadOfSelectAll() {
        assertEquals(
            TextRange(6),
            initialTextFieldSelection(
                textLength = 6,
                nodeId = "title",
                explicitSelection = TextSelection("other", 0, 5),
                clickedOffset = null,
            ),
        )
    }

    @Test
    fun entryOffsetsAndExplicitRangesAreClampedToCurrentText() {
        assertEquals(
            TextRange(3),
            initialTextFieldSelection(3, "title", explicitSelection = null, clickedOffset = 99),
        )
        assertEquals(
            TextRange(0, 3),
            initialTextFieldSelection(3, "title", TextSelection("title", -4, 12), clickedOffset = null),
        )
    }

    @Test
    fun activeTextFieldOwnsPressAndSelectionDrag() {
        assertTrue(textEditorOwnsCanvasPress("title", setOf("title"), "title", editingBoundsHit = false, forcePan = false))
        assertTrue(textEditorOwnsCanvasPress("title", setOf("title"), "", editingBoundsHit = true, forcePan = false))
        assertFalse(textEditorOwnsCanvasPress("title", setOf("title"), "other", editingBoundsHit = false, forcePan = false))
        assertFalse(textEditorOwnsCanvasPress("title", setOf("title", "other"), "title", editingBoundsHit = true, forcePan = false))
        assertFalse(textEditorOwnsCanvasPress("title", setOf("title"), "title", editingBoundsHit = true, forcePan = true))
    }

    @Test
    fun endCaretHitSlopBelongsToTheTextEditor() {
        val box = BoundsBox(x = 10.0, y = 20.0, width = 100.0, height = 24.0)

        assertTrue(textEditorHitBoxContains(box, 0.0, docX = 116.0, docY = 32.0, hitSlopDoc = 6.0))
        assertFalse(textEditorHitBoxContains(box, 0.0, docX = 116.01, docY = 32.0, hitSlopDoc = 6.0))
    }

    @Test
    fun inspectorFocusKeepsTheLastNonCollapsedTextRange() {
        val selected = TextRange(2, 7)

        assertEquals(
            RetainedTextSelection(selected, selected),
            retainTextSelectionAcrossInspectorFocus(
                selection = selected,
                fieldFocused = true,
                retainedSelection = null,
            ),
        )
        assertEquals(
            RetainedTextSelection(selected, selected),
            retainTextSelectionAcrossInspectorFocus(
                selection = TextRange(7),
                fieldFocused = false,
                retainedSelection = selected,
            ),
        )
    }

    @Test
    fun caretClickInsideFocusedTextClearsTheRetainedRange() {
        assertEquals(
            RetainedTextSelection(TextRange(4), null),
            retainTextSelectionAcrossInspectorFocus(
                selection = TextRange(4),
                fieldFocused = true,
                retainedSelection = TextRange(2, 7),
            ),
        )
    }

    @Test
    fun pointerSelectionCanReplaceARetainedRangeFromTheEndCaret() {
        assertEquals(
            RetainedTextSelection(TextRange(10), null),
            retainTextSelectionAcrossInspectorFocus(
                selection = TextRange(10),
                fieldFocused = false,
                fieldPointerPressActive = true,
                retainedSelection = TextRange(2, 7),
            ),
        )
    }
}
