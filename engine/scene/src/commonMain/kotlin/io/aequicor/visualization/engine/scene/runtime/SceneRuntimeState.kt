package io.aequicor.visualization.engine.scene.runtime

import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.projection.ComposedScreen

/** One open overlay: which screen it shows and how it is positioned. */
data class OverlayFrame(
    val destinationScreenId: String,
    val settings: OverlaySettings = OverlaySettings(),
)

/** Logical position of the runtime: the current screen plus the overlay stack over it. */
data class SceneLocation(
    val screenId: String,
    val overlayStack: List<OverlayFrame> = emptyList(),
)

/** Live pointer/focus input, modeled as runtime state (not editor selection). */
data class SceneInputState(
    val hoveredNodeId: String = "",
    val pressedNodeId: String = "",
    val focusedNodeId: String = "",
    val draggingNodeId: String = "",
)

/**
 * A frozen visual picture captured when a transition launches. [composed] holds an immutable
 * [ComposedScreen] (an immutable `LayoutBox` tree), so later Canvas edits — which build new
 * trees — cannot change what a running transition shows.
 */
data class SceneSnapshot(
    val snapshotId: String,
    val screenId: String,
    val composed: ComposedScreen,
    val variables: Map<String, VariableValue> = emptyMap(),
    val overlays: List<OverlayFrame> = emptyList(),
    /** Scroll offsets by node source id; MVP leaves this empty. */
    val scroll: Map<String, DesignPoint> = emptyMap(),
)

/** A running screen→screen transition: the two snapshots, its spec, and where it commits to. */
data class TransitionInstance(
    val id: String,
    val from: SceneSnapshot,
    val to: SceneSnapshot,
    val spec: DesignTransition,
    val startTimeMs: Double,
    val targetLocation: SceneLocation,
    val targetVariables: Map<String, VariableValue>,
    /** History stack the runtime commits to when this transition ends. */
    val targetHistory: List<SceneLocation> = emptyList(),
) {
    val endTimeMs: Double get() = startTimeMs + spec.durationMs
}

/**
 * Transition presence as a total type: either a stable single-screen frame, or a running
 * transition. Modeling it as a sealed type (rather than a nullable + a `blockedInput` flag)
 * lets the input-policy gate be a total `when`.
 */
sealed interface RuntimePhase {
    data object Stable : RuntimePhase

    data class Transitioning(val instance: TransitionInstance) : RuntimePhase
}

/** An `afterDelay` interaction waiting to fire; consumed by [SceneRuntime.advance]. */
data class ArmedTimer(
    val nodeId: String,
    val fireAtMs: Double,
    val actions: List<DesignAction>,
)

/**
 * Live prototype-playback state — everything the spec's "Scene Runtime State" lists. It lives
 * outside the IR (compile-enforced by the `:engine:scene → :engine:ir` one-way dependency) and
 * never enters the editor document or its undo stack.
 */
data class SceneRuntimeState(
    /** `DesignFlow.id`, else `screen:<id>` when entered by screen. */
    val sceneId: String,
    val location: SceneLocation,
    val variables: Map<String, VariableValue> = emptyMap(),
    val history: List<SceneLocation> = emptyList(),
    val input: SceneInputState = SceneInputState(),
    val phase: RuntimePhase = RuntimePhase.Stable,
    val armedTimers: List<ArmedTimer> = emptyList(),
    /**
     * The current screen frozen once at the last stable commit, so [SceneProjection] samples a
     * stable frame without re-resolving every timeline tick. Null only for a bare hand-built state.
     */
    val stableSnapshot: SceneSnapshot? = null,
)
