package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.EffectOp
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tier-2 list write-back through the reducer: fills, strokes and effects edits rewrite
 * the `style:` lists in the owning SLM source and mirror onto the working document,
 * preserving other sources byte-for-byte. `frame_overview` is authored with a `style:`
 * block (token fill, token stroke) so every list below is addressable there.
 */
class Tier2WriteBackTest {

    private val nodeId = "frame_overview"
    private val owningFile = "mission-overview.layout.md"

    private fun freshState(): DesignEditorState =
        reduceDesignEditor(
            createDesignEditorState(missionDemoDocuments()),
            DesignEditorIntent.SelectNode(nodeId),
        )

    private fun DesignEditorState.sourceOf(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }).content

    private fun DesignEditorState.assertWroteBack(before: DesignEditorState) {
        assertNotEquals(before.sourceOf(owningFile), sourceOf(owningFile), "owning source rewritten")
        before.sources.filterNot { it.fileName == owningFile }.forEach { source ->
            assertEquals(source.content, sourceOf(source.fileName), "${source.fileName} stays byte-identical")
        }
        assertTrue(
            diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back errors: ${diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
    }

    @Test
    fun addFillAppendsSecondFillIntoSource() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.FillCommand(nodeId, FillOp.Add))
        assertEquals(2, next.document?.nodeById(nodeId)?.fills?.size, "a second fill layer is added")
        next.assertWroteBack(before)
    }

    @Test
    fun updateSolidFillWritesFirstFillColor() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.UpdateSolidFill(nodeId, DesignColor(0xFF2244AAL)))
        val first = next.document?.nodeById(nodeId)?.fills?.firstOrNull() as? DesignPaint.Solid
        assertEquals(DesignColor(0xFF2244AAL), first?.color?.literalOrNull())
        assertTrue("#2244aa" in next.sourceOf(owningFile).lowercase(), "fill color written as hex")
        next.assertWroteBack(before)
    }

    @Test
    fun strokeColorWritesIntoStrokesList() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.StrokeCommand(nodeId, StrokeOp.SetColor(DesignColor(0xFF00AA55L))))
        val stroke = (next.document?.nodeById(nodeId)?.strokes?.paints?.firstOrNull() as? DesignPaint.Solid)
        assertEquals(DesignColor(0xFF00AA55L), stroke?.color?.literalOrNull())
        assertTrue("#00aa55" in next.sourceOf(owningFile).lowercase(), "stroke color written as hex")
        next.assertWroteBack(before)
    }

    @Test
    fun addEffectCreatesEffectsBlock() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.EffectCommand(nodeId, EffectOp.Add(EffectType.DropShadow)))
        val effect = next.document?.nodeById(nodeId)?.effects?.firstOrNull()
        assertTrue(effect is DesignEffect.DropShadow, "a drop shadow effect is added")
        val source = next.sourceOf(owningFile)
        assertTrue("effect (dropShadow" in source, "effect phrase created")
        assertTrue("dropShadow" in source, "effect type written")
        next.assertWroteBack(before)
    }
}
