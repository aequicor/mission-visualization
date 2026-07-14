package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.EffectOp
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Regression coverage for the Fill / Stroke / Effects `+` buttons on a legacy plain-H1 screen. */
class RootAppearanceWriteBackTest {
    private val source = """
        ---
        screen: recorderPip
        sourceLocale: ru-RU
        frame: { width: 128, height: 296 }
        ---

        # Recorder PiP

        ## Frame: Transport Controls id transport_controls 128 by 214 position 0 82
    """.trimIndent() + "\n"

    private fun freshState() = createDesignEditorState(
        compileMissionDocuments(listOf(MissionDocumentSource("recorder-pip.layout.md", source))),
    )

    @Test
    fun addFillUpgradesLegacyRootHeadingAndPreservesScreenTitle() {
        val before = freshState()
        assertFalse(before.compiledResults.single().editIndex.isCnlSentence("recorderPip"))
        val state = reduceDesignEditor(
            before,
            DesignEditorIntent.FillCommand("recorderPip", FillOp.Add),
        )

        val messages = state.diagnostics.joinToString("\n") { it.message }
        assertEquals(1, state.document?.nodeById("recorderPip")?.fills?.size, messages)
        val written = state.sources.single().content
        assertTrue("color #B9C4D2" in written, written)
        assertTrue("# Recorder PiP id recorderPip" in written, "the authored H1 title stays intact:\n$written")
        assertTrue(state.diagnostics.none { it.severity == DesignSeverity.Error }, messages)
    }

    @Test
    fun addStrokeWorksOnLegacyRootWithoutAnExistingStylePhrase() {
        val state = reduceDesignEditor(
            freshState(),
            DesignEditorIntent.StrokeCommand("recorderPip", StrokeOp.Add),
        )

        val messages = state.diagnostics.joinToString("\n") { it.message }
        assertNotNull(state.document?.nodeById("recorderPip")?.strokes, messages)
        assertTrue("stroke #1E88FF" in state.sources.single().content, state.sources.single().content)
        assertTrue(state.diagnostics.none { it.severity == DesignSeverity.Error }, messages)
    }

    @Test
    fun addEffectWorksOnLegacyRootWithoutAnExistingStylePhrase() {
        val state = reduceDesignEditor(
            freshState(),
            DesignEditorIntent.EffectCommand("recorderPip", EffectOp.Add(EffectType.DropShadow)),
        )

        val messages = state.diagnostics.joinToString("\n") { it.message }
        val effect = state.document?.nodeById("recorderPip")?.effects?.singleOrNull()
        assertTrue(effect is DesignEffect.DropShadow, messages)
        assertTrue("effect (dropShadow" in state.sources.single().content, state.sources.single().content)
        assertTrue(state.diagnostics.none { it.severity == DesignSeverity.Error }, messages)
    }
}
