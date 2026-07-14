package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.InteractionOp
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fidelity veto on the CNL write-back path: an edit a CNL sentence cannot express is regenerated
 * via tier-3 (whole-sentence re-emit). If that re-emit would recompile to a DIFFERENT node than the
 * edit intends — dropping an inexpressible interaction, effect or stroke — the reducer keeps the
 * faithful in-memory edit and leaves the source byte-identical rather than silently corrupting it.
 *
 * `go` is a CNL-authored button whose `onClick navigate` interaction lives inline in its heading
 * sentence, so it is a cnlOwner and its edits route through [CnlWriter], not the YAML path.
 */
class CnlWriteBackVetoTest {
    private val nodeId = "go"

    private val source = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen id root name «Screen»

        ## Button: Go id go onClick navigate (details)
    """.trimIndent() + "\n"

    private fun state(): DesignEditorState =
        createDesignEditorState(compileMissionDocuments(listOf(MissionDocumentSource("s.layout.md", source))))

    private fun DesignEditorState.node() = assertNotNull(document?.nodeById(nodeId))
    private fun DesignEditorState.sourceText() = sources.single().content

    @Test
    fun authoredInteractionIsACnlOwner() {
        val s = state()
        assertTrue(s.diagnostics.none { it.severity == DesignSeverity.Error }, s.diagnostics.toString())
        val action = s.node().interactions.single().actions.single()
        assertEquals("details", (action as DesignAction.Navigate).to)
        assertTrue("onClick navigate (details)" in s.sourceText(), s.sourceText())
    }

    @Test
    fun inexpressibleTransitionEditKeepsSourceUncorrupted() {
        val before = state()
        val bezier = DesignTransition(TransitionType.Push, easing = DesignEasing.CubicBezier(0.25, 0.1, 0.25, 1.0), durationMs = 300.0)
        val next = reduceDesignEditor(before, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.SetActionTransition(0, 0, bezier)))

        // A cubic-bezier transition is inexpressible in CNL. The atomic contract rejects the whole
        // operation instead of leaving a document-only value that disappears after reopen.
        assertEquals(before.document, next.document)
        assertEquals(before.sourceText(), next.sourceText(), "inexpressible edit → source untouched")
        assertTrue("onClick navigate (details)" in next.sourceText(), "original interaction preserved")
        assertTrue(next.diagnostics.any { it.severity == DesignSeverity.Error && "does not support SLM write-back" in it.message })
    }
}
