package io.aequicor.visualization.engine.scene.runtime

import io.aequicor.visualization.engine.ir.model.MotionKeyframes

enum class PlaybackState { Idle, Playing, Paused }

enum class AnimationKind { Transition, Motion }

/** One animation currently contributing visual overrides at the timeline clock. */
data class ActiveAnimation(
    val id: String,
    val kind: AnimationKind,
    /** Motion clips target a node (by source id); a transition targets none. */
    val nodeId: String? = null,
    val keyframes: MotionKeyframes? = null,
    val startTimeMs: Double,
    val durationMs: Double,
    val loop: Boolean = false,
)

/**
 * Pure clock state. No wall clock lives here — time moves only through
 * [SceneRuntime.advance]/[SceneRuntime.seek], so playback is deterministic and replayable.
 */
data class TimelineState(
    val currentTimeMs: Double = 0.0,
    val playback: PlaybackState = PlaybackState.Idle,
    /** 1.0 = realtime; 0.25 = slow motion; applied inside `advance`. */
    val speed: Double = 1.0,
    /** Active span (a transition's duration, or a looping clip's period). */
    val durationMs: Double = 0.0,
    val activeAnimations: List<ActiveAnimation> = emptyList(),
)
