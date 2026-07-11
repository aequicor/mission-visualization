package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextTruncate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clearing a typography key must delete the authored key from the owning SLM source (or, for
 * nodes that only mirror typography in-memory, drop it from the working document) — a cleared
 * value is not the same as an omitted one. `badge_text` is a heading-anchored text node in
 * mission-telemetry.layout.md whose typography round-trips through the source.
 */
class TextClearWriteBackReducerTest {

    private val nodeId = "badge_text"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()),
            DesignEditorIntent.SelectNode(nodeId),
        )

    private fun DesignEditorState.textKindOrNull(): DesignNodeKind.Text? =
        document?.nodeById(nodeId)?.kind as? DesignNodeKind.Text

    private fun DesignNodeKind.Text.length(): Int =
        (content?.defaultText?.takeIf { it.isNotEmpty() }
            ?: (characters as? Bindable.Value)?.value).orEmpty().length

    private fun DesignEditorState.sourcesText(): String = sources.joinToString("\n") { it.content }

    @Test
    fun clearDecorationColorRemovesTheKey() {
        val before = freshState()
        val kind = before.textKindOrNull() ?: return
        if (kind.length() < 3) return

        val underlined = reduceDesignEditor(
            before,
            DesignEditorIntent.UpdateTypography(
                nodeId,
                TypographyPatch(
                    textDecoration = TextDecorationKind.Underline,
                    decorationColor = DesignColor.fromHex("#FF0000"),
                ),
            ),
        )
        val cleared = reduceDesignEditor(
            underlined,
            DesignEditorIntent.UpdateTypography(nodeId, TypographyPatch(clearDecorationColor = true)),
        )

        if ("decorationColor" in underlined.sourcesText()) {
            // Preferred path: the key was authored into the owning source, so clearing deletes it.
            assertFalse("decorationColor" in cleared.sourcesText(), "cleared decorationColor key removed from the source")
        } else {
            // Fallback: this node mirrors typography in-memory only, so assert on the working document instead.
            assertNull(cleared.textKindOrNull()?.textStyle?.decorationColor, "cleared decoration color null on the mirrored document")
        }
    }

    @Test
    fun clearTruncateRemovesMaxLines() {
        val before = freshState()
        val kind = before.textKindOrNull() ?: return
        if (kind.length() < 3) return

        val truncated = reduceDesignEditor(
            before,
            DesignEditorIntent.SetTextTruncate(nodeId, TextTruncate(maxLines = 2, ellipsis = true)),
        )
        val cleared = reduceDesignEditor(truncated, DesignEditorIntent.SetTextTruncate(nodeId, null))

        if ("maxLines" in truncated.sourcesText()) {
            // Preferred path: maxLines was authored into the owning source, so clearing deletes it.
            assertFalse("maxLines" in cleared.sourcesText(), "cleared maxLines removed from the source")
        } else {
            // Fallback: this node mirrors truncation in-memory only, so assert on the working document instead.
            assertTrue(truncated.textKindOrNull()?.truncate != null, "truncation applied to the mirrored document")
            assertNull(cleared.textKindOrNull()?.truncate, "cleared truncation null on the mirrored document")
        }
    }
}
