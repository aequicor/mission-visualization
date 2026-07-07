package io.aequicor.visualization.engine.ir.model

/** Motion attachment: a reference into `motionRefs` plus an inline keyframe fallback. */
data class DesignMotion(
    val ref: String = "",
    val fallback: MotionKeyframes? = null,
)

data class MotionKeyframes(
    val durationMs: Double,
    val loop: Boolean = false,
    val frames: List<MotionFrame>,
)

/** One keyframe at normalized time [at]; open property keys: opacity/x/y/scale/rotation. */
data class MotionFrame(
    val at: Double,
    val properties: Map<String, Double>,
)
