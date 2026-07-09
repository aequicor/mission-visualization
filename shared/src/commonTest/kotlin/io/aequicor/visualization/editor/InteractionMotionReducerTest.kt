package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.InteractionOp
import io.aequicor.visualization.editor.presentation.MotionOp
import io.aequicor.visualization.editor.presentation.MotionPreset
import io.aequicor.visualization.editor.presentation.ProtoActionKind
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P2 reducer: [DesignEditorIntent.InteractionCommand] / [DesignEditorIntent.MotionCommand] mirror
 * onto the working document AND patch the owning SLM source (or fall back in-memory), through the
 * op-command idiom. `overview_wide` is a heading-anchored shape in mission-overview.layout.md with
 * no authored behavior, so it is a clean canvas.
 */
class InteractionMotionReducerTest {

    private val nodeId = "overview_wide"

    private fun freshState(): DesignEditorState =
        createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())())

    private fun DesignEditorState.node() = assertNotNull(document?.nodeById(nodeId))

    private fun changedSource(before: DesignEditorState, after: DesignEditorState): MissionDocumentSource? =
        after.sources.firstOrNull { s -> before.sources.first { it.fileName == s.fileName }.content != s.content }

    private fun DesignEditorState.assertNoErrors() =
        assertTrue(diagnostics.none { it.severity == DesignSeverity.Error }, "errors: ${diagnostics.filter { it.severity == DesignSeverity.Error }}")

    @Test
    fun addInteractionMirrorsAndPatchesSource() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))

        val interaction = next.node().interactions.single()
        assertEquals(InteractionTrigger.OnClick, interaction.trigger)
        assertEquals("missionTelemetry", (interaction.actions.single() as DesignAction.Navigate).to)

        val changed = assertNotNull(changedSource(before, next), "one source must be patched")
        assertTrue("interaction:" in changed.content)
        next.assertNoErrors()
        assertEquals(listOf(before.sources), next.previousSources, "source undo captured")
    }

    @Test
    fun retargetAndRemoveRoundTripThroughSource() {
        val added = reduceDesignEditor(freshState(), DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        val retargeted = reduceDesignEditor(added, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.SetActionTarget(0, 0, "missionEventLog")))
        assertEquals("missionEventLog", (retargeted.node().interactions.single().actions.single() as DesignAction.Navigate).to)
        assertTrue("missionEventLog" in assertNotNull(changedSource(freshState(), retargeted)).content)

        val removed = reduceDesignEditor(retargeted, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.RemoveAt(0)))
        assertTrue(removed.node().interactions.isEmpty())
        // Add + remove restores the owning source to its original bytes (the neighbouring hero's
        // authored interaction block is untouched throughout), so nothing differs from a fresh load.
        assertNull(changedSource(freshState(), removed), "add + remove round-trips the source")
        removed.assertNoErrors()
    }

    @Test
    fun changeActionTypeToBackDropsTransition() {
        val added = reduceDesignEditor(freshState(), DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        val back = reduceDesignEditor(added, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.SetActionType(0, 0, ProtoActionKind.Back)))
        assertEquals(DesignAction.Back, back.node().interactions.single().actions.single())
        back.assertNoErrors()
    }

    @Test
    fun cubicBezierTransitionFallsBackInMemory() {
        val added = reduceDesignEditor(freshState(), DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        val bezier = DesignTransition(TransitionType.Push, easing = DesignEasing.CubicBezier(0.25, 0.1, 0.25, 1.0), durationMs = 300.0)
        val next = reduceDesignEditor(added, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.SetActionTransition(0, 0, bezier)))
        // Mirrored onto the working document...
        assertEquals(bezier, (next.node().interactions.single().actions.single() as DesignAction.Navigate).transition)
        // ...but the source is byte-identical to before this inexpressible edit (in-memory fallback).
        assertNull(changedSource(added, next), "cubic-bezier is inexpressible → source untouched")
    }

    @Test
    fun motionPresetEnablesAndPatchesSource() {
        val before = freshState()
        val enabled = reduceDesignEditor(before, DesignEditorIntent.MotionCommand(nodeId, MotionOp.SetPreset(MotionPreset.Pulse)))
        val motion = assertNotNull(enabled.node().motion?.fallback)
        assertTrue(motion.loop)
        assertEquals(900.0, motion.durationMs)
        assertTrue("motion:" in assertNotNull(changedSource(before, enabled)).content)

        val disabled = reduceDesignEditor(enabled, DesignEditorIntent.MotionCommand(nodeId, MotionOp.SetEnabled(false)))
        assertNull(disabled.node().motion)
    }

    @Test
    fun undoRestoresDocumentAndSources() {
        val before = freshState()
        val added = reduceDesignEditor(before, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        val undone = reduceDesignEditor(added, DesignEditorIntent.Undo)
        assertTrue(undone.node().interactions.isEmpty(), "undo reverts the document")
    }
}
