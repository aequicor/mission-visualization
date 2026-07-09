package io.aequicor.visualization.engine.scene.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SceneStateSmokeTest {

    @Test
    fun constructsDefaultSessionAndPhase() {
        val session = SceneSession(
            runtime = SceneRuntimeState(sceneId = "screen:overview", location = SceneLocation("overview")),
            timeline = TimelineState(),
        )
        assertEquals("overview", session.runtime.location.screenId)
        assertEquals(RuntimePhase.Stable, session.runtime.phase)
        assertEquals(PlaybackState.Idle, session.timeline.playback)
        assertEquals(0.0, session.timeline.currentTimeMs)
    }

    @Test
    fun sequentialIdGeneratorIsMonotonicAndDeterministic() {
        val a = SequentialIdGenerator()
        assertEquals("snap_0", a.next("snap"))
        assertEquals("snap_1", a.next("snap"))
        assertEquals("tr_2", a.next("tr"))
        // A fresh generator restarts from 0 → deterministic replay.
        val b = SequentialIdGenerator()
        assertEquals("snap_0", b.next("snap"))
    }

    @Test
    fun traceEntriesCarryTimestamps() {
        val entry: TraceEntry = TraceEntry.Ignored(timeMs = 42.0, reason = "input blocked during transition")
        assertEquals(42.0, entry.timeMs)
        assertNotEquals(entry, TraceEntry.Ignored(timeMs = 43.0, reason = "input blocked during transition"))
    }
}
