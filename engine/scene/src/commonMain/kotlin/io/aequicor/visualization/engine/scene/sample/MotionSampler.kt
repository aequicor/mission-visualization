package io.aequicor.visualization.engine.scene.sample

import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.scene.runtime.ActiveAnimation

/**
 * Samples keyframe [MotionKeyframes] into a [VisualOverride] at a normalized time. Property
 * keys (`opacity`, `x`, `y`, `scale`, `rotation`) are sampled as independent tracks so sparse
 * keyframes interpolate correctly; a property no keyframe declares keeps its identity value.
 */
object MotionSampler {

    fun sample(frames: MotionKeyframes, normalizedTime: Double): VisualOverride {
        if (frames.frames.isEmpty()) return VisualOverride.Identity
        val t = normalizedTime.coerceIn(0.0, 1.0)
        val opacity = track(frames, "opacity", t) ?: 1.0
        val x = track(frames, "x", t) ?: 0.0
        val y = track(frames, "y", t) ?: 0.0
        val scale = track(frames, "scale", t) ?: 1.0
        val rotation = track(frames, "rotation", t) ?: 0.0
        return VisualOverride(
            opacity = opacity,
            translateX = x,
            translateY = y,
            scaleX = scale,
            scaleY = scale,
            rotationDeg = rotation,
        )
    }

    /** Normalized 0..1 phase of [anim] at [nowMs]; loop-aware, clamps for non-looping clips. */
    fun localNormalized(anim: ActiveAnimation, nowMs: Double): Double {
        if (anim.durationMs <= 0.0) return 0.0
        val elapsed = nowMs - anim.startTimeMs
        if (elapsed <= 0.0) return 0.0
        return if (anim.loop) {
            val phase = elapsed % anim.durationMs
            phase / anim.durationMs
        } else {
            (elapsed / anim.durationMs).coerceIn(0.0, 1.0)
        }
    }

    /** Linear-interpolated value of one property across the frames that declare it, or null. */
    private fun track(frames: MotionKeyframes, key: String, t: Double): Double? {
        val points = frames.frames
            .mapNotNull { frame -> frame.properties[key]?.let { frame.at to it } }
            .sortedBy { it.first }
        if (points.isEmpty()) return null
        if (t <= points.first().first) return points.first().second
        if (t >= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val (a, av) = points[i]
            val (b, bv) = points[i + 1]
            if (t in a..b) {
                if (b == a) return bv
                val f = (t - a) / (b - a)
                return av + (bv - av) * f
            }
        }
        return points.last().second
    }
}
