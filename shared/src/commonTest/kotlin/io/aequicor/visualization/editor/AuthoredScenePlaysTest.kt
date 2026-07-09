package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.InteractionOp
import io.aequicor.visualization.editor.presentation.MotionOp
import io.aequicor.visualization.editor.presentation.MotionPreset
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.scene.projection.SceneProjection
import io.aequicor.visualization.engine.scene.runtime.PointerKind
import io.aequicor.visualization.engine.scene.runtime.SceneEntry
import io.aequicor.visualization.engine.scene.runtime.SceneEvent
import io.aequicor.visualization.engine.scene.runtime.SceneRuntime
import io.aequicor.visualization.engine.scene.runtime.SequentialIdGenerator
import io.aequicor.visualization.engine.scene.runtime.TraceEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P3 end-to-end: behavior authored purely through the reducer intents (exactly what the Prototype
 * tab dispatches — no hand-edited SLM) plays back through the Scene runtime. Closes the author →
 * Play-in-Scene loop.
 */
class AuthoredScenePlaysTest {

    private val nodeId = "overview_wide"

    private fun freshDocument(): DesignDocument =
        assertNotNull(createDesignEditorState(legacyMissionDocuments()).document)

    private fun runtimeFor(document: DesignDocument): Pair<SceneRuntime, SceneProjection> {
        val composer = ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))
        return SceneRuntime(document, composer, SequentialIdGenerator()) to SceneProjection(composer)
    }

    @Test
    fun interactionAuthoredInInspectorPlaysInScene() {
        val state = createDesignEditorState(legacyMissionDocuments())
        val authored = reduceDesignEditor(state, DesignEditorIntent.InteractionCommand(nodeId, InteractionOp.Add))
        val document = assertNotNull(authored.document)

        val (runtime, _) = runtimeFor(document)
        val start = runtime.start(SceneEntry.Screen("missionOverview")).session
        val clicked = runtime.dispatch(start, SceneEvent.Pointer(PointerKind.Click, nodeId))
        assertTrue(clicked.trace.any { it is TraceEntry.TransitionStarted }, "authored click starts a transition")

        val arrived = runtime.advance(clicked.session, 400.0).session
        assertEquals("missionTelemetry", arrived.runtime.location.screenId, "navigates to the default target")
    }

    @Test
    fun motionPresetAuthoredInInspectorPlaysInScene() {
        val state = createDesignEditorState(legacyMissionDocuments())
        val authored = reduceDesignEditor(state, DesignEditorIntent.MotionCommand(nodeId, MotionOp.SetPreset(MotionPreset.Pulse)))
        val document = assertNotNull(authored.document)

        val (runtime, projection) = runtimeFor(document)
        val session = runtime.start(SceneEntry.Screen("missionOverview")).session
        val override = projection.project(session).layers.single()
            .nodeOverrides.firstOrNull { it.nodeId == nodeId }
        assertNotNull(override, "the authored pulse produces a motion override on the node")
        assertEquals(0.4, override.override.opacity, 1e-6, "Pulse starts at 0.4 opacity")
    }
}
