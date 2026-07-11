package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Undo/redo for the Figma-parity figure operations. Each op writes back to its owning SLM
 * source (proven in [FigureParityWriteBackTest]); here we prove the same ops are wired into
 * the document history: one [DesignEditorIntent.Undo] restores the pre-edit document and a
 * following [DesignEditorIntent.Redo] re-applies the edit. The source-undo entry is captured
 * too (the reducer stacks the pre-edit sources into `previousSources`), guarding the "restore
 * the prior document and its source" contract.
 */
class FigureUndoRedoTest {

    private fun freshState(selecting: String): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())()),
            DesignEditorIntent.SelectNode(selecting),
        )

    /**
     * Applies [intent] to a fresh state selecting [selecting], then asserts the edit is a real,
     * undoable document mutation: the document changed, one Undo restores the pre-edit document
     * and its captured source, and one Redo re-applies the edit.
     */
    private fun assertUndoRedo(selecting: String, intent: DesignEditorIntent) {
        val before = freshState(selecting)
        val after = reduceDesignEditor(before, intent)

        assertNotEquals(before.document, after.document, "edit must mutate the document")
        assertEquals(before.document, after.undoStack.lastOrNull(), "pre-edit document pushed to history")
        assertEquals(listOf(before.sources), after.previousSources, "source undo captured pre-edit sources")

        val undone = reduceDesignEditor(after, DesignEditorIntent.Undo)
        assertEquals(before.document, undone.document, "one Undo restores the prior document")
        assertTrue(undone.redoStack.isNotEmpty(), "Undo populated the redo stack")

        val redone = reduceDesignEditor(undone, DesignEditorIntent.Redo)
        assertEquals(after.document, redone.document, "Redo re-applies the edit")
    }

    /**
     * Document-level undo/redo for edits that land in-memory only (flatten / outline are
     * multi-section structural rewrites the CNL patcher cannot express surgically, so the
     * anti-corruption veto keeps them off the SLM source). The document history still round-trips;
     * no source-undo entry is captured because the source did not change.
     */
    private fun assertDocumentUndoRedo(selecting: String, intent: DesignEditorIntent) {
        val before = freshState(selecting)
        val after = reduceDesignEditor(before, intent)

        assertNotEquals(before.document, after.document, "edit must mutate the document")
        assertEquals(before.document, after.undoStack.lastOrNull(), "pre-edit document pushed to history")

        val undone = reduceDesignEditor(after, DesignEditorIntent.Undo)
        assertEquals(before.document, undone.document, "one Undo restores the prior document")
        assertTrue(undone.redoStack.isNotEmpty(), "Undo populated the redo stack")

        val redone = reduceDesignEditor(undone, DesignEditorIntent.Redo)
        assertEquals(after.document, redone.document, "Redo re-applies the edit")
    }

    @Test
    fun setArcSweepIsUndoable() =
        assertUndoRedo("showcase_ellipse", DesignEditorIntent.SetArcSweep("showcase_ellipse", 270.0))

    @Test
    fun setVertexCornerRadiusIsUndoable() =
        assertUndoRedo("showcase_network", DesignEditorIntent.SetVertexCornerRadius("showcase_network", 1, 8.0))

    @Test
    fun setRegionFillIsUndoable() {
        val fill = listOf(DesignPaint.Solid(DesignColor(0xFFEF476F).bindable()))
        assertUndoRedo("showcase_network", DesignEditorIntent.SetRegionFill("showcase_network", 0, fill))
    }

    @Test
    fun setStrokeJoinIsUndoable() =
        assertUndoRedo("showcase_arrow", DesignEditorIntent.StrokeCommand("showcase_arrow", StrokeOp.SetJoin("bevel")))

    @Test
    fun flattenNodeIsUndoable() =
        assertDocumentUndoRedo("showcase_union", DesignEditorIntent.FlattenNode("showcase_union"))

    @Test
    fun outlineStrokeIsUndoable() =
        assertDocumentUndoRedo("showcase_arrow", DesignEditorIntent.OutlineStroke("showcase_arrow"))
}
