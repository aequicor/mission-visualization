package io.aequicor.visualization.engine.scene.runtime

import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Regression tests for the AfterDelay-timer clock/one-shot fixes (review findings #1, #2). */
class SceneTimerTest {

    private val json = """
        {
          "pages": [
            { "id": "splash", "children": [
              { "id": "splash_frame", "type": "frame", "size": { "width": 300, "height": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "interactions": [ { "trigger": "afterDelay", "delayMs": 500,
                  "actions": [ { "type": "navigate", "to": "home" } ] } ] }
            ] },
            { "id": "home", "children": [
              { "id": "home_frame", "type": "frame", "size": { "width": 300, "height": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" } }
            ] },
            { "id": "pulse", "children": [
              { "id": "pulse_frame", "type": "frame", "size": { "width": 300, "height": 200 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "interactions": [ { "trigger": "afterDelay", "delayMs": 100,
                  "actions": [ { "type": "setVariable", "variable": "c", "value": "x" } ] } ] }
            ] }
          ]
        }
    """.trimIndent()

    private fun runtime(): SceneRuntime {
        val document = assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document
        return SceneRuntime(document, ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer())), SequentialIdGenerator())
    }

    @Test
    fun staticScreenWithAfterDelayKeepsClockPlaying() {
        val session = runtime().start(SceneEntry.Screen("splash")).session
        // No motion clip on this screen, but an armed timer must keep the clock Playing so the
        // Compose frame host (which advances only while Playing) actually fires it.
        assertEquals(PlaybackState.Playing, session.timeline.playback)
        assertEquals(1, session.runtime.armedTimers.size)
    }

    @Test
    fun afterDelayNavigatesWhenAdvancedPastDelay() {
        val rt = runtime()
        val start = rt.start(SceneEntry.Screen("splash")).session
        val before = rt.advance(start, 300.0).session
        assertEquals("splash", before.runtime.location.screenId, "timer not yet due")
        val after = rt.advance(before, 300.0).session // total 600 >= 500
        assertEquals("home", after.runtime.location.screenId, "afterDelay navigated")
    }

    @Test
    fun nonNavigatingAfterDelayFiresExactlyOnce() {
        val rt = runtime()
        var session = rt.start(SceneEntry.Screen("pulse")).session
        val allTrace = mutableListOf<TraceEntry>()
        // Advance well past several delay periods; a one-shot afterDelay must fire only once.
        repeat(6) {
            val step = rt.advance(session, 100.0)
            session = step.session
            allTrace += step.trace
        }
        val fires = allTrace.count { it is TraceEntry.VariableSet }
        assertEquals(1, fires, "a non-navigating afterDelay fires once per screen entry, not every period")
        // Once the sole timer has fired and nothing else is animating, the clock idles.
        assertEquals(PlaybackState.Idle, session.timeline.playback)
    }
}
