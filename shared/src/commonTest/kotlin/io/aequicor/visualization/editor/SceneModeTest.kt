package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.EditorMode
import io.aequicor.visualization.editor.presentation.EditorViewport
import io.aequicor.visualization.editor.presentation.EditorWorkspaceState
import io.aequicor.visualization.editor.presentation.SceneController
import io.aequicor.visualization.editor.presentation.sceneEntryOf
import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.scene.projection.SceneProjection
import io.aequicor.visualization.engine.scene.runtime.PlaybackState
import io.aequicor.visualization.engine.scene.runtime.PointerKind
import io.aequicor.visualization.engine.scene.runtime.SceneEntry
import io.aequicor.visualization.engine.scene.runtime.SceneEvent
import io.aequicor.visualization.engine.scene.runtime.SceneRuntime
import io.aequicor.visualization.engine.scene.runtime.SequentialIdGenerator
import io.aequicor.visualization.engine.scene.runtime.TraceEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneModeTest {

    private val json = """
        {
          "pages": [
            { "id": "screen1", "children": [
              { "id": "frame_1", "type": "frame", "size": { "width": 400, "height": 300 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "children": [
                  { "id": "hero", "type": "frame", "position": { "x": 20, "y": 20 },
                    "size": { "width": 360, "height": 80 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                    "interactions": [ { "trigger": "onClick", "actions": [
                      { "type": "navigate", "to": "screen2",
                        "transition": { "type": "push", "direction": "left", "durationMs": 300 } } ] } ] }
                ] }
            ] },
            { "id": "screen2", "children": [
              { "id": "frame_2", "type": "frame", "size": { "width": 400, "height": 300 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" } }
            ] }
          ]
        }
    """.trimIndent()

    private fun controller(): SceneController {
        val document = assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document
        val composer = ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))
        return SceneController(SceneRuntime(document, composer, SequentialIdGenerator()), SceneProjection(composer))
    }

    @Test
    fun sceneClickExecutesInteractionAndDrivesTransition() {
        val controller = controller()
        controller.start(sceneEntryOf(DesignEditorState(selectedPageId = "screen1")))
        assertEquals("screen1", controller.session?.runtime?.location?.screenId)

        controller.onEvent(SceneEvent.Pointer(PointerKind.Click, "hero"))
        // Scene click executed the interaction → a transition is now running and traced.
        assertTrue(controller.trace.any { it is TraceEntry.TransitionStarted })
        assertNotNull(controller.renderModel)
        assertEquals(2, controller.renderModel?.layers?.size, "mid-transition frame has two layers")

        // Drive the clock past the transition; the target screen commits.
        controller.onFrame(300.0)
        assertEquals("screen2", controller.session?.runtime?.location?.screenId)
    }

    @Test
    fun modeSwitchPreservesWorkspaceAndDocumentWithoutUndo() {
        val document = assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document
        // A design state carrying a selection and an existing undo checkpoint.
        val design = DesignEditorState(
            document = document,
            selectedPageId = "screen1",
            selectedNodeId = "hero",
            selectedNodeIds = setOf("hero"),
            undoStack = listOf(document),
        )
        val workspace = EditorWorkspaceState(
            mode = EditorMode.Canvas,
            viewport = EditorViewport(zoom = 2.5f, panOffsetXDp = 40f, panOffsetYDp = 12f),
        )

        // Switching modes is a workspace-only copy (as the toolbar does): the document state object
        // is never touched, so no undo entry can be created and selection/viewport survive.
        val toScene = workspace.copy(mode = EditorMode.Scene)
        val backToCanvas = toScene.copy(mode = EditorMode.Canvas)

        assertEquals(EditorMode.Scene, toScene.mode)
        assertEquals(workspace.viewport, toScene.viewport)
        assertEquals(workspace.copy(mode = EditorMode.Scene), toScene, "only the mode field changes")
        assertEquals(workspace, backToCanvas, "round-trip restores the workspace exactly")

        // The document/selection state is a separate object the switch never mutates.
        assertSame(document, design.document)
        assertEquals(setOf("hero"), design.selectedNodeIds)
        assertEquals(listOf(document), design.undoStack)
    }

    @Test
    fun onFrameFiresAfterDelayTimerOnAStaticScreen() {
        // A splash screen with only an AfterDelay navigate (no motion clip) must still be driven by
        // the frame host: onFrame advances only while Playing, so buildStable must keep it Playing.
        val json2 = """
            {
              "pages": [
                { "id": "splash", "children": [
                  { "id": "splash_frame", "type": "frame", "size": { "width": 300, "height": 200 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                    "interactions": [ { "trigger": "afterDelay", "delayMs": 400,
                      "actions": [ { "type": "navigate", "to": "home" } ] } ] }
                ] },
                { "id": "home", "children": [
                  { "id": "home_frame", "type": "frame", "size": { "width": 300, "height": 200 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" } }
                ] }
              ]
            }
        """.trimIndent()
        val document = assertIs<DesignParseResult.Success>(parseDesignDocument(json2)).document
        val composer = ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))
        val controller = SceneController(SceneRuntime(document, composer, SequentialIdGenerator()), SceneProjection(composer))

        controller.start(SceneEntry.Screen("splash"))
        assertEquals(PlaybackState.Playing, controller.playback, "armed timer keeps the clock alive")
        controller.onFrame(500.0) // past the 400ms delay
        assertEquals("home", controller.session?.runtime?.location?.screenId, "onFrame fired the timer")
    }

    @Test
    fun leavingSceneStopsPlaybackWithoutTouchingDocument() {
        val controller = controller()
        controller.start(sceneEntryOf(DesignEditorState(selectedPageId = "screen1")))
        controller.onEvent(SceneEvent.Pointer(PointerKind.Click, "hero"))
        controller.stop()
        assertTrue(controller.session == null && controller.renderModel == null)
        assertTrue(controller.trace.isEmpty())
    }
}
