package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.semanticallyEquivalent
import io.aequicor.visualization.engine.ir.model.orZero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Multi-node canvas move previews and their queued position commits must remain atomic. */
class AtomicMoveWriteBackTest {
    private val source = """
        ---
        screen: recorderPip
        sourceLocale: ru-RU
        ---

        # Recorder PiP

        ## Frame: Screen id screen 128 by 296 position 0 0

        ### Frame: Controls id controls 128 by 214 position 0 82

        #### Frame: id screenshot_actions name «Screenshot Actions» 60 by 48 position 34 19

        Rectangle id screenshot_icon 26 by 26 position 2 11

        #### Frame: id record_action name «Record Action» 60 by 60 position 34 77

        Ellipse id record_icon 28 by 28 position 16 16

        #### Frame: Open Editor Action id open_editor_action 48 by 48 position 34 147.5

        Rectangle id open_editor_icon 27 by 27 position 10.5 10.5
    """.trimIndent() + "\n"

    @Test
    fun queuedPositionCommitsKeepTheWholeMultiSelectionPreview() {
        val originalSource = MissionDocumentSource("recorder-pip.layout.md", source)
        var state = createDesignEditorState(compileMissionDocuments(listOf(originalSource)))
        val selected = linkedSetOf(
            "screenshot_actions",
            "screenshot_icon",
            "record_action",
            "record_icon",
            "open_editor_action",
            "open_editor_icon",
        )

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(selected, dx = -18.0, dy = 0.0))

        // Canvas commitMovedPositions emits one final write-back intent per moved outer node.
        state = reduceDesignEditor(state, DesignEditorIntent.PositionNode("screenshot_actions", 16.0, 19.0))
        state = reduceDesignEditor(state, DesignEditorIntent.PositionNode("record_action", 16.0, 77.0))
        state = reduceDesignEditor(state, DesignEditorIntent.PositionNode("open_editor_action", 16.0, 147.5))

        val movedIds = listOf("screenshot_actions", "record_action", "open_editor_action")
        movedIds.forEach { id ->
            assertEquals(16.0, state.document?.nodeById(id)?.position?.x?.orZero, "$id preview was reset by a later commit")
        }
        assertEquals(listOf(originalSource), state.sources, "interaction must not publish partial sources")

        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        val messages = state.diagnostics.joinToString("\n") { it.message }
        movedIds.forEach { id ->
            assertEquals(16.0, state.document?.nodeById(id)?.position?.x?.orZero, "$id was not committed: $messages")
        }
        val recompiled = assertNotNull(compileMissionDocuments(state.sources).document, messages)
        assertTrue(semanticallyEquivalent(assertNotNull(state.document), recompiled), messages)
    }
}
