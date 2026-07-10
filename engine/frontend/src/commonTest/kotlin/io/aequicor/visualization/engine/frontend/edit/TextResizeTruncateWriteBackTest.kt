package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextTruncate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Write-back of a text node's auto-resize ([SetTextAutoResize] -> `text.resizing`) and
 * truncation ([SetTextTruncate] -> `text.maxLines` / `overflow`). Each edit lands the
 * expected shorthand and round-trips through recompile.
 */
class TextResizeTruncateWriteBackTest {

    private fun textKind(source: String): DesignNodeKind.Text =
        assertIs(compileForEdit(source).requireDocument().requireNode("label").kind)

    @Test
    fun widthAndHeightResizeWritesBothHug() {
        val new = applySlmEdit(DOC, SetTextAutoResize("label", TextAutoResize.WidthAndHeight), compileForEdit(DOC))
            .requireNewSource()
        assertTrue("resizing:" in new, "resizing block written: $new")
        assertTrue("width: hug" in new && "height: hug" in new, "both axes hug: $new")
        assertEquals(TextAutoResize.WidthAndHeight, textKind(new).autoResize)
    }

    @Test
    fun heightOnlyResizeWritesFixedWidthHugHeight() {
        val new = applySlmEdit(DOC, SetTextAutoResize("label", TextAutoResize.Height), compileForEdit(DOC))
            .requireNewSource()
        assertTrue("width: fixed" in new && "height: hug" in new, "fixed width, hug height: $new")
        assertEquals(TextAutoResize.Height, textKind(new).autoResize)
    }

    @Test
    fun truncateWritesMaxLinesAndOverflow() {
        val compiled = compileForEdit(DOC)
        val result = applySlmEdit(DOC, SetTextTruncate("label", TextTruncate(maxLines = 2)), compiled)
        val new = result.requireNewSource()
        assertTrue("maxLines: 2" in new, "maxLines written: $new")
        assertTrue("overflow: truncate" in new, "ellipsis truncation writes overflow: truncate: $new")
        assertLosslessOutside(DOC, new, assertNotNull(result.appliedRange))
        assertEquals(TextTruncate(maxLines = 2, ellipsis = true), textKind(new).truncate)
    }

    @Test
    fun truncateWithoutEllipsisWritesVisibleOverflow() {
        val new = applySlmEdit(DOC, SetTextTruncate("label", TextTruncate(3, ellipsis = false)), compileForEdit(DOC))
            .requireNewSource()
        assertTrue("maxLines: 3" in new && "overflow: visible" in new, "no-ellipsis truncation: $new")
        assertEquals(TextTruncate(3, ellipsis = false), textKind(new).truncate)
    }

    @Test
    fun nullTruncateRemovesAuthoredMaxLines() {
        // First author truncation, then clear it: the maxLines key must be deleted (not just overridden).
        val withTruncate = applySlmEdit(DOC, SetTextTruncate("label", TextTruncate(maxLines = 2)), compileForEdit(DOC))
            .requireNewSource()
        assertTrue("maxLines: 2" in withTruncate)
        val cleared = applySlmEdit(withTruncate, SetTextTruncate("label", truncate = null), compileForEdit(withTruncate))
            .requireNewSource()
        assertTrue("maxLines" !in cleared, "authored maxLines removed on clear: $cleared")
        assertNull(textKind(cleared).truncate, "truncation fully cleared after removal")
    }

    private companion object {
        private val DOC = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Text: Title
            node:
              type: text
              id: label
              name: Title
            text:
              key: screen.title
              defaultText: Hello
            style:
              fills:
                - color: "#101010"
        """.trimIndent() + "\n"
    }
}
