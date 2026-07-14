package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Canvas resize preview must still queue and persist the matching final ResizeNode command. */
class AtomicResizeWriteBackTest {
    private val source = """
        ---
        screen: storyboardEditor
        sourceLocale: ru-RU
        ---

        # Storyboard Editor

        ## Frame: Desktop Workspace id desktop_workspace 1600 by 1000 position 0 0

        ### Frame: Storyboard And Export Panel id storyboard_export_panel 416 by 880 position 1160 96

        #### Frame: Storyboard Frame List id storyboard_frame_list 368 by 484 position 24 104 clip overflow (y auto) scroll (direction vertical) color #161C21 stroke #37414B 1 center radius 8

        ##### Frame: Storyboard Row 01 id storyboard_row_01 352 by 88 position 8 8 color #242B32 stroke #4C8CCA 2 center radius 7

        Rectangle id row_01_thumbnail 124 by 70 position 8 9 radius 5 color #344A5C
    """.trimIndent() + "\n"

    private val autoSizeTextSource = """
        ---
        screen: textResize
        sourceLocale: ru-RU
        ---

        # Text Resize

        ## Frame: Screen id screen 128 by 66 position 0 0

        ### Text: id text characters «ТевваВ» name «Text» width hug height hug position 8 4 color #182733 size 16 autosize both
    """.trimIndent() + "\n"

    @Test
    fun canvasInteractionPersistsContainerResizeWithoutStyleDrift() {
        val originalSource = MissionDocumentSource("storyboard-editor.layout.md", source)
        var state = createDesignEditorState(compileMissionDocuments(listOf(originalSource)))

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateSize("storyboard_frame_list", 230.8, 404.0))
        state = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("storyboard_frame_list", 230.8, 404.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        val messages = state.diagnostics.joinToString("\n") { it.message }
        val node = assertNotNull(state.document?.nodeById("storyboard_frame_list"), messages)
        assertEquals(230.8, node.size.width, messages)
        assertEquals(404.0, node.size.height, messages)
        assertEquals(StrokeAlign.Center, node.strokes?.align, messages)
        assertNotEquals(source, state.sources.single().content)
        assertTrue(state.diagnostics.none { it.severity == DesignSeverity.Error }, messages)
    }

    @Test
    fun cornerResizePinsBothAxesOfAutoSizeText() {
        var state = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("text-resize.layout.md", autoSizeTextSource))),
        )

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateSize("text", width = 42.0, height = 18.0))

        var text = assertNotNull(state.document?.nodeById("text"))
        assertEquals(TextAutoResize.None, (text.kind as DesignNodeKind.Text).autoResize)
        assertEquals(SizingMode.Fixed, text.sizing?.horizontal)
        assertEquals(SizingMode.Fixed, text.sizing?.vertical)

        state = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("text", width = 42.0, height = 18.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        val messages = state.diagnostics.joinToString("\n") { it.message }
        text = assertNotNull(state.document?.nodeById("text"), messages)
        assertEquals(42.0, text.size.width, messages)
        assertEquals(18.0, text.size.height, messages)
        assertEquals(TextAutoResize.None, (text.kind as DesignNodeKind.Text).autoResize, messages)
        assertEquals(SizingMode.Fixed, text.sizing?.horizontal, messages)
        assertEquals(SizingMode.Fixed, text.sizing?.vertical, messages)
        assertTrue("autosize" !in state.sources.single().content, state.sources.single().content)
        assertTrue(state.diagnostics.none { it.severity == DesignSeverity.Error }, messages)
    }

    @Test
    fun horizontalResizeKeepsAutoHeightForTextWrapping() {
        var state = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("text-resize.layout.md", autoSizeTextSource))),
        )

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateSize("text", width = 42.0))

        var text = assertNotNull(state.document?.nodeById("text"))
        assertEquals(TextAutoResize.Height, (text.kind as DesignNodeKind.Text).autoResize)
        assertEquals(SizingMode.Fixed, text.sizing?.horizontal)
        assertEquals(SizingMode.Hug, text.sizing?.vertical)

        state = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("text", width = 42.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        val messages = state.diagnostics.joinToString("\n") { it.message }
        text = assertNotNull(state.document?.nodeById("text"), messages)
        assertEquals(42.0, text.size.width, messages)
        assertEquals(TextAutoResize.Height, (text.kind as DesignNodeKind.Text).autoResize, messages)
        assertEquals(SizingMode.Fixed, text.sizing?.horizontal, messages)
        assertEquals(SizingMode.Hug, text.sizing?.vertical, messages)
        assertTrue("autosize height" in state.sources.single().content, state.sources.single().content)
        assertTrue(state.diagnostics.none { it.severity == DesignSeverity.Error }, messages)
    }
}
