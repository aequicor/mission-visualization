package io.aequicor.visualization.engine.scene.runtime

/** How a pointer touched a node; the renderer has already hit-tested to a node id. */
enum class PointerKind { Click, PressDown, PressUp, HoverEnter, HoverExit, Move, DragStart }

/** An input or internal signal fed to [SceneRuntime.dispatch]. */
sealed interface SceneEvent {
    /** A pointer event on an already-hit-tested node. */
    data class Pointer(val kind: PointerKind, val nodeId: String) : SceneEvent

    data class Key(val key: String) : SceneEvent

    /** Internal re-entry after a variable changed (for `onVariableChange` triggers). */
    data class VariableChanged(val variable: String) : SceneEvent

    /** Internal timer fan-out. */
    data object Tick : SceneEvent
}

/** How Scene playback begins: a named flow, or a screen (a start HINT, e.g. the Canvas selection). */
sealed interface SceneEntry {
    data class Flow(val flowId: String) : SceneEntry

    data class Screen(val screenId: String) : SceneEntry
}

/** The full transient session: runtime + timeline, advanced together as one immutable value. */
data class SceneSession(
    val runtime: SceneRuntimeState,
    val timeline: TimelineState,
)

/** The result of one runtime step: the next session plus the trace appended during this step. */
data class SceneStep(
    val session: SceneSession,
    val trace: List<TraceEntry>,
)
