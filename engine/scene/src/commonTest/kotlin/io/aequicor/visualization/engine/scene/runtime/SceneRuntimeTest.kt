package io.aequicor.visualization.engine.scene.runtime

import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.scene.projection.SceneProjection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneRuntimeTest {

    private val json = """
        {
          "pages": [
            { "id": "screen1", "name": "Screen 1", "children": [
              { "id": "frame_1", "type": "frame", "size": { "width": 400, "height": 300 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "children": [
                  { "id": "hero", "type": "frame", "position": { "x": 20, "y": 20 },
                    "size": { "width": 360, "height": 80 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                    "interactions": [ { "trigger": "onClick", "actions": [
                      { "type": "navigate", "to": "screen2",
                        "transition": { "type": "push", "direction": "left", "easing": "easeOut", "durationMs": 300 } } ] } ] }
                ] }
            ] },
            { "id": "screen2", "name": "Screen 2", "children": [
              { "id": "frame_2", "type": "frame", "size": { "width": 400, "height": 300 },
                "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                "children": [
                  { "id": "dot", "type": "frame", "position": { "x": 10, "y": 10 },
                    "size": { "width": 24, "height": 24 },
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                    "motion": { "fallback": { "durationMs": 900, "loop": true, "frames": [
                      { "at": 0, "opacity": 0.4 }, { "at": 0.5, "opacity": 1.0 }, { "at": 1, "opacity": 0.4 } ] } } }
                ] }
            ] }
          ]
        }
    """.trimIndent()

    private fun document(): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun composer(document: DesignDocument = document()) =
        ScreenComposer(document, DesignLayoutEngine(ApproximateTextMeasurer()))

    private fun runtime(document: DesignDocument = document()): Pair<SceneRuntime, SceneProjection> {
        val comp = composer(document)
        return SceneRuntime(document, comp, SequentialIdGenerator()) to SceneProjection(comp)
    }

    private fun near(a: Double, b: Double, eps: Double = 1e-6) =
        assertTrue(abs(a - b) <= eps, "expected ~$a but was $b")

    @Test
    fun startsOnScreenAndCapturesStableSnapshot() {
        val (rt, _) = runtime()
        val session = rt.start(SceneEntry.Screen("screen1")).session
        assertEquals("screen1", session.runtime.location.screenId)
        assertEquals(RuntimePhase.Stable, session.runtime.phase)
        val snapshot = assertNotNull(session.runtime.stableSnapshot)
        assertEquals("screen1", snapshot.screenId)
    }

    @Test
    fun clickOnHeroStartsTransitionWithFromToSnapshots() {
        val (rt, _) = runtime()
        val start = rt.start(SceneEntry.Screen("screen1")).session
        val step = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "hero"))
        val phase = assertIs<RuntimePhase.Transitioning>(step.session.runtime.phase)
        assertEquals("screen1", phase.instance.from.screenId)
        assertEquals("screen2", phase.instance.to.screenId)
        assertNotEquals(phase.instance.from.screenId, phase.instance.to.screenId)
        // Logical location does not commit until the transition completes.
        assertEquals("screen1", step.session.runtime.location.screenId)
        assertTrue(step.trace.any { it is TraceEntry.TransitionStarted })
    }

    @Test
    fun inputIsBlockedDuringTransition() {
        val (rt, _) = runtime()
        val start = rt.start(SceneEntry.Screen("screen1")).session
        val mid = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "hero")).session
        val blocked = rt.dispatch(mid, SceneEvent.Pointer(PointerKind.Click, "hero"))
        assertEquals(mid, blocked.session, "state must not change while a transition is running")
        assertTrue(blocked.trace.any { it is TraceEntry.Ignored && it.reason.contains("blocked") })
    }

    @Test
    fun advanceCompletesTransitionAndCommitsTargetScreen() {
        val (rt, _) = runtime()
        val start = rt.start(SceneEntry.Screen("screen1")).session
        val mid = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "hero")).session
        val done = rt.advance(mid, 300.0)
        assertEquals(RuntimePhase.Stable, done.session.runtime.phase)
        assertEquals("screen2", done.session.runtime.location.screenId)
        assertTrue(done.trace.any { it is TraceEntry.TransitionEnded })
        // history remembers where we came from.
        assertEquals(listOf("screen1"), done.session.runtime.history.map { it.screenId })
    }

    @Test
    fun transitionFrameHasTwoDistinctLayersStableFrameHasOne() {
        val (rt, projection) = runtime()
        val start = rt.start(SceneEntry.Screen("screen1")).session
        assertEquals(1, projection.project(start).layers.size)

        val mid = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "hero")).session
        val model = projection.project(mid)
        assertEquals(2, model.layers.size)
        assertNotEquals(model.layers[0].layerOverride, model.layers[1].layerOverride)
        assertTrue(model.layers.all { !it.hitTestable }, "no layer is hit-testable during a transition")
        assertNotNull(model.debug.transition)
    }

    @Test
    fun traceReportsEventHitTriggerActionAndTransitionInOrder() {
        val (rt, _) = runtime()
        val start = rt.start(SceneEntry.Screen("screen1")).session
        val trace = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "hero")).trace
        val kinds = trace.map { it::class.simpleName }
        assertEquals(
            listOf("EventReceived", "HitTarget", "TriggerMatched", "ActionExecuted", "TransitionStarted"),
            kinds,
        )
    }

    @Test
    fun clickWithNoInteractionIsIgnoredWithReason() {
        val (rt, _) = runtime()
        val start = rt.start(SceneEntry.Screen("screen1")).session
        val step = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "frame_1"))
        assertEquals(start, step.session)
        assertTrue(step.trace.any { it is TraceEntry.Ignored && it.reason.contains("no interaction") })
    }

    @Test
    fun playbackIsDeterministicAcrossRuns() {
        fun run(): SceneStep {
            val (rt, _) = runtime()
            val s0 = rt.start(SceneEntry.Screen("screen1")).session
            val s1 = rt.dispatch(s0, SceneEvent.Pointer(PointerKind.Click, "hero")).session
            return rt.advance(s1, 150.0)
        }
        val a = run()
        val b = run()
        assertEquals(a.session, b.session, "same inputs → same session")
        assertEquals(a.trace, b.trace, "same inputs → same trace")
    }

    @Test
    fun timelineTransportPlayPauseSeekRestart() {
        val (rt, _) = runtime()
        // screen2 has a looping motion clip → playback starts Playing.
        val s0 = rt.start(SceneEntry.Screen("screen2")).session
        assertEquals(PlaybackState.Playing, s0.timeline.playback)

        val advanced = rt.advance(s0, 100.0).session
        near(100.0, advanced.timeline.currentTimeMs)

        val paused = advanced.copy(timeline = rt.pause(advanced.timeline))
        assertEquals(PlaybackState.Paused, paused.timeline.playback)

        val seeked = rt.seek(paused, 450.0).session
        near(450.0, seeked.timeline.currentTimeMs)
        assertEquals(PlaybackState.Paused, seeked.timeline.playback)

        val restarted = rt.restart(seeked).session
        near(0.0, restarted.timeline.currentTimeMs)
        assertEquals("screen2", restarted.runtime.location.screenId)
    }

    @Test
    fun advanceOnlyProgressesTimeByDeltaTimesSpeed() {
        val (rt, _) = runtime()
        val s0 = rt.start(SceneEntry.Screen("screen2")).session
        val slow = s0.copy(timeline = rt.setSpeed(s0.timeline, 0.5))
        val stepped = rt.advance(slow, 100.0).session
        near(50.0, stepped.timeline.currentTimeMs, eps = 1e-9)
    }

    @Test
    fun motionClipAnimatesOpacityOverrideOnStableScreen() {
        val (rt, projection) = runtime()
        val s0 = rt.start(SceneEntry.Screen("screen2")).session
        val at0 = projection.project(s0)
        val dot0 = assertNotNull(at0.layers.single().nodeOverrides.firstOrNull { it.nodeId == "dot" })
        near(0.4, dot0.override.opacity)

        val quarter = rt.advance(s0, 225.0).session // 225/900 = 0.25 → opacity 0.7
        val dotQuarter = assertNotNull(
            projection.project(quarter).layers.single().nodeOverrides.firstOrNull { it.nodeId == "dot" },
        )
        near(0.7, dotQuarter.override.opacity, eps = 1e-6)
    }

    @Test
    fun snapshotSurvivesLaterDocumentEdits() {
        val document = document()
        val (rt, _) = runtime(document)
        val start = rt.start(SceneEntry.Screen("screen1")).session
        val mid = rt.dispatch(start, SceneEvent.Pointer(PointerKind.Click, "hero")).session
        val phase = assertIs<RuntimePhase.Transitioning>(mid.runtime.phase)
        val frozenRoot = phase.instance.from.composed.root
        // Build a differently-sized document; the frozen snapshot is unaffected (immutable capture).
        val edited = document.updateNode("frame_1") { it.copy(size = it.size.copy(width = 999.0)) }
        assertNotEquals(999.0, frozenRoot.width)
        near(400.0, frozenRoot.width)
        assertNotNull(edited)
    }
}
