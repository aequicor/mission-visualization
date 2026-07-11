package io.aequicor.visualization.editor

import io.aequicor.visualization.engine.ir.model.DesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shipped CNL demos must round-trip SLM → IR cleanly and carry the authored behaviour the
 * scene pipeline plays back. The Telemetry status dot loops an opacity clip authored in CNL
 * (`motion duration 900 loop frames …`).
 *
 * The former Overview hero navigate-interaction demo (and the full Overview→Telemetry Push
 * playback) was dropped when the shipped Overview became a Free-layout wireframe with no authored
 * interactions: a navigate interaction now lives in no shipped CNL demo. The author→Play-in-Scene
 * loop is still covered end-to-end by [AuthoredScenePlaysTest], which authors the interaction
 * through the reducer and plays it.
 */
class DemoSceneTest {

    private fun missionDocument(): DesignDocument {
        val documents = missionDemoDocuments()
        // The demo authoring must not introduce compile errors.
        assertTrue(documents.diagnostics.none { it.severity.name == "Error" }, "demo SLM compiles cleanly")
        return assertNotNull(documents.document, "bundled mission docs compile")
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
}
