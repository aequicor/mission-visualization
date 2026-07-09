package io.aequicor.visualization.editor.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aequicor.visualization.engine.scene.projection.SceneProjection
import io.aequicor.visualization.engine.scene.projection.SceneRenderModel
import io.aequicor.visualization.engine.scene.runtime.PlaybackState
import io.aequicor.visualization.engine.scene.runtime.SceneEntry
import io.aequicor.visualization.engine.scene.runtime.SceneEvent
import io.aequicor.visualization.engine.scene.runtime.SceneRuntime
import io.aequicor.visualization.engine.scene.runtime.SceneSession
import io.aequicor.visualization.engine.scene.runtime.SceneStep
import io.aequicor.visualization.engine.scene.runtime.TimelineState
import io.aequicor.visualization.engine.scene.runtime.TraceEntry

/** How Scene playback begins for the current editor state: a named flow, else the selected screen. */
fun sceneEntryOf(design: DesignEditorState): SceneEntry {
    val flowId = design.document?.screen?.flow?.id?.takeIf { it.isNotBlank() }
    return if (flowId != null) SceneEntry.Flow(flowId) else SceneEntry.Screen(design.selectedPageId)
}

/**
 * Compose state host over the pure [SceneRuntime]. It owns the transient [SceneSession] and the
 * derived [SceneRenderModel] + [trace] as snapshot state, so the UI recomposes when playback moves.
 * It never dispatches a `DesignEditorIntent`, so no runtime state can reach the document undo stack.
 * The clock host (a frame loop) feeds it real deltas via [onFrame]; time stays owned by the pure
 * [TimelineState].
 */
@Stable
class SceneController(
    private val runtime: SceneRuntime,
    private val projection: SceneProjection,
) {
    var session by mutableStateOf<SceneSession?>(null)
        private set

    var renderModel by mutableStateOf<SceneRenderModel?>(null)
        private set

    var trace by mutableStateOf<List<TraceEntry>>(emptyList())
        private set

    val playback: PlaybackState get() = session?.timeline?.playback ?: PlaybackState.Idle
    val currentTimeMs: Double get() = session?.timeline?.currentTimeMs ?: 0.0
    val durationMs: Double get() = session?.timeline?.durationMs ?: 0.0
    val speed: Double get() = session?.timeline?.speed ?: 1.0
    val isRunning: Boolean get() = session != null

    fun start(entry: SceneEntry) {
        trace = emptyList()
        apply(runtime.start(entry))
    }

    fun onEvent(event: SceneEvent) {
        val current = session ?: return
        apply(runtime.dispatch(current, event))
    }

    fun play() = mutateTimeline(runtime::play)

    fun pause() = mutateTimeline(runtime::pause)

    fun setSpeed(value: Double) = mutateTimeline { runtime.setSpeed(it, value) }

    fun seek(timeMs: Double) {
        val current = session ?: return
        apply(runtime.seek(current, timeMs))
    }

    fun restart() {
        val current = session ?: return
        trace = emptyList()
        apply(runtime.restart(current))
    }

    fun stop() {
        session = null
        renderModel = null
        trace = emptyList()
    }

    /** Called by the frame host with a real delta; a no-op unless playing. */
    fun onFrame(deltaMs: Double) {
        val current = session ?: return
        if (current.timeline.playback != PlaybackState.Playing) return
        apply(runtime.advance(current, deltaMs))
    }

    private fun apply(step: SceneStep) {
        session = step.session
        trace = (trace + step.trace).takeLast(MAX_TRACE)
        renderModel = projection.project(step.session)
    }

    private fun mutateTimeline(transform: (TimelineState) -> TimelineState) {
        val current = session ?: return
        val next = current.copy(timeline = transform(current.timeline))
        session = next
        renderModel = projection.project(next)
    }

    companion object {
        const val MAX_TRACE: Int = 200
    }
}
