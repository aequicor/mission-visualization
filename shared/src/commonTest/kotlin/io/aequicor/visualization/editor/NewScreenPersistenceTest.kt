package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural edits on a screen created in-session (CreateScreen appends a fresh
 * `<screen>.layout.md` source) must keep persisting: created objects gain sections in
 * that source, and canvas-drag reparents relocate them — same guarantees as edits on a
 * bundled screen.
 */
class NewScreenPersistenceTest {

    private fun freshState(): DesignEditorState =
        createDesignEditorState(missionDemoDocuments())

    private fun DesignEditorState.newScreenSource(): String {
        val fileName = "${selectedPageId}.layout.md"
        return assertNotNull(sources.firstOrNull { it.fileName == fileName }, "created screen source $fileName present").content
    }

    @Test
    fun createScreenAppendsSource() {
        val before = freshState()
        val next = reduceDesignEditor(before, DesignEditorIntent.CreateScreen(ScreenPreset.Desktop, "Fresh"))

        assertEquals(before.sources.size + 1, next.sources.size, "screen appended a source")
        assertTrue(next.selectedNodeId.startsWith("frame_"), "root frame selected: ${next.selectedNodeId}")
        assertTrue(next.selectedNodeId in next.newScreenSource(), "root frame id in the new source")
    }

    @Test
    fun createObjectOnNewScreenWritesSection() {
        var state = reduceDesignEditor(freshState(), DesignEditorIntent.CreateScreen(ScreenPreset.Desktop, "Fresh"))
        val rootId = state.selectedNodeId

        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Frame, parentId = rootId, x = 40.0, y = 40.0, width = 200.0, height = 150.0),
        )
        val created = state.selectedNodeId
        assertNotNull(state.document?.nodeById(created), "created frame in document")
        assertTrue(created in state.newScreenSource(), "created frame id persisted to the new screen source: ${state.newScreenSource()}")
    }

    @Test
    fun reparentOnNewScreenWritesBack() {
        var state = reduceDesignEditor(freshState(), DesignEditorIntent.CreateScreen(ScreenPreset.Desktop, "Fresh"))
        val rootId = state.selectedNodeId
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Frame, parentId = rootId, x = 40.0, y = 40.0, width = 400.0, height = 300.0),
        )
        val frameA = state.selectedNodeId
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Frame, parentId = rootId, x = 600.0, y = 500.0, width = 200.0, height = 150.0),
        )
        val frameB = state.selectedNodeId

        state = reduceDesignEditor(
            state,
            DesignEditorIntent.ReparentNode(frameB, frameA, position = DesignPoint(20.0, 20.0)),
        )

        assertEquals(frameA, state.document?.let { doc -> doc.pages.flatMap { it.allNodes() }.firstOrNull { n -> n.children.any { it.id == frameB } }?.id }, "B nested under A in document")
        val source = state.newScreenSource()
        assertTrue(frameA in source && frameB in source, "both frames in source")
        // B's section must sit under A's (deeper heading level follows A's heading).
        val aAt = source.indexOf("id $frameA")
        val bAt = source.indexOf("id $frameB")
        assertTrue(aAt in 0 until bAt, "B's section follows A's in the source:\n$source")
    }
}
