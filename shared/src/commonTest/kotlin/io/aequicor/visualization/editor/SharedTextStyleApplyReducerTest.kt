package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Applies / clears a shared document text style over a character range through the reducer. */
class SharedTextStyleApplyReducerTest {

    private val nodeId = "badge_text"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()),
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
    fun setTextRangeStyleRefAppliesAndClearsASharedStyle() {
        val before = freshState()
        if (before.length() < 3) return

        val applied = reduceDesignEditor(before, DesignEditorIntent.SetTextRangeStyleRef(nodeId, 0, 3, "h1"))
        val range = applied.textKind().styleRanges.firstOrNull { it.start == 0 && it.end == 3 }
        assertNotNull(range, "style-ref range set")
        assertEquals("h1", range.styleRef)

        val cleared = reduceDesignEditor(applied, DesignEditorIntent.SetTextRangeStyleRef(nodeId, 0, 3, ""))
        assertTrue(
            cleared.textKind().styleRanges.none { it.start == 0 && it.end == 3 && it.styleRef.isNotBlank() },
            "style-ref cleared",
        )
    }
}
