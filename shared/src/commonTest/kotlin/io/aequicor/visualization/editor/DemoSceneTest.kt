package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
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
 * The authored demo scene must round-trip SLM → IR and then play end-to-end through the runtime:
 * the Overview hero navigates to Telemetry (Push transition), and the Telemetry status dot loops an
 * opacity clip. Exercises the whole real pipeline with the bundled mission documents.
 */
class DemoSceneTest {

    private fun missionDocument(): DesignDocument {
        val documents = compileMissionDocuments(DefaultDesignDocumentRepository().missionDocumentSources())
        // The demo authoring must not introduce compile errors.
        assertTrue(documents.diagnostics.none { it.severity.name == "Error" }, "demo SLM compiles cleanly")
        return assertNotNull(documents.document, "bundled mission docs compile")
    }

    @Test
    fun overviewHeroAuthorsNavigateInteraction() {
        val document = missionDocument()
        val hero = assertNotNull(document.nodeById("overview_hero"), "hero node present")
        val interaction = assertNotNull(
            hero.interactions.firstOrNull { it.trigger == InteractionTrigger.OnClick },
            "hero carries an onClick interaction",
        )
        val navigate = assertNotNull(
            interaction.actions.filterIsInstance<DesignAction.Navigate>().firstOrNull(),
            "onClick navigates",
        )
        assertEquals("missionTelemetry", navigate.to)
        assertNotNull(navigate.transition, "navigation animates (Push)")
    }

    @Test
    fun telemetryDotAuthorsLoopingOpacityMotion() {
        val document = missionDocument()
        val dot = assertNotNull(document.nodeById("badge_dot"), "status dot present")
        val motion = assertNotNull(dot.motion?.fallback, "dot carries keyframe motion")
        assertTrue(motion.loop, "the pulse loops")
        assertEquals(900.0, motion.durationMs)
        assertTrue(motion.frames.any { it.properties["opacity"] == 0.4 })
    }

    @Test
    fun demoPlaysOverviewToTelemetryThroughTheRuntime() {
        val document = missionDocument()
        val composer = ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))
        val runtime = SceneRuntime(document, composer, SequentialIdGenerator())
        val projection = SceneProjection(composer)

        val start = runtime.start(SceneEntry.Screen("missionOverview")).session
        assertEquals("missionOverview", start.runtime.location.screenId)

        val clicked = runtime.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "overview_hero"))
        assertTrue(clicked.trace.any { it is TraceEntry.TransitionStarted }, "click starts the Push transition")
        assertEquals(2, projection.project(clicked.session).layers.size, "two layers mid-transition")

        val arrived = runtime.advance(clicked.session, 320.0).session
        assertEquals("missionTelemetry", arrived.runtime.location.screenId)

        // On Telemetry the status dot runs a motion override.
        val dotOverride = projection.project(arrived).layers.single()
            .nodeOverrides.firstOrNull { it.nodeId == "badge_dot" }
        assertNotNull(dotOverride, "telemetry dot has a live motion override")
    }
}
