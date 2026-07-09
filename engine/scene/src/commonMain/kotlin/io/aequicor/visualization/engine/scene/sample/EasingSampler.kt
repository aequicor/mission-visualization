package io.aequicor.visualization.engine.scene.sample

import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.EasingKind
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure easing evaluation: `evaluate(easing, t)` maps normalized time `t ∈ [0,1]` to normalized
 * progress. Named kinds are closed form, cubic-bézier is Newton-solved, spring is a bounded
 * damped-oscillator MVP. Endpoints are exactly 0 and 1 for every easing.
 */
object EasingSampler {

    fun evaluate(easing: DesignEasing, t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return when (easing) {
            is DesignEasing.Named -> named(easing.kind, x)
            is DesignEasing.CubicBezier -> cubicBezierY(easing.x1, easing.y1, easing.x2, easing.y2, x)
            is DesignEasing.Spring -> spring(easing, x)
        }
    }

    private const val Back = 1.70158

    private fun named(kind: EasingKind, t: Double): Double = when (kind) {
        EasingKind.Linear -> t
        EasingKind.EaseIn -> t * t
        EasingKind.EaseOut -> 1.0 - (1.0 - t) * (1.0 - t)
        EasingKind.EaseInOut -> if (t < 0.5) 2.0 * t * t else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
        EasingKind.EaseInBack -> t * t * ((Back + 1.0) * t - Back)
        EasingKind.EaseOutBack -> 1.0 + (Back + 1.0) * (t - 1.0).pow(3) + Back * (t - 1.0).pow(2)
    }

    /** Solves bézier x(s)=t by Newton iteration, returns y(s). x1/x2 are the control abscissae. */
    private fun cubicBezierY(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0
        fun bez(a1: Double, a2: Double, s: Double): Double {
            val ms = 1.0 - s
            return 3.0 * ms * ms * s * a1 + 3.0 * ms * s * s * a2 + s * s * s
        }
        fun bezDeriv(a1: Double, a2: Double, s: Double): Double {
            val ms = 1.0 - s
            return 3.0 * ms * ms * a1 + 6.0 * ms * s * (a2 - a1) + 3.0 * s * s * (1.0 - a2)
        }
        var s = t
        repeat(8) {
            val error = bez(x1, x2, s) - t
            val d = bezDeriv(x1, x2, s)
            if (d < 1e-6 && d > -1e-6) return@repeat
            s = (s - error / d).coerceIn(0.0, 1.0)
        }
        return bez(y1, y2, s)
    }

    /**
     * Bounded damped-spring progress on the normalized window `[0,1]`. Underdamped springs may
     * overshoot 1 before settling; endpoints are pinned to 0 and 1. MVP approximation — good
     * enough to visualize easing feel; not a physical simulation.
     */
    private fun spring(spec: DesignEasing.Spring, t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0
        val mass = spec.mass.coerceAtLeast(1e-3)
        val stiffness = spec.stiffness.coerceAtLeast(1e-3)
        val zeta = (spec.damping / (2.0 * sqrt(stiffness * mass))).coerceIn(0.0, 4.0)
        // Fixed decay window so t≈1 is essentially settled across a plausible parameter range.
        val decay = exp(-zeta * 6.0 * t)
        val angular = 6.0 * sqrt(1.0 - (zeta * zeta).coerceAtMost(1.0))
        val oscillation = if (zeta < 1.0) cosApprox(angular * t) else 1.0 - t
        return 1.0 - decay * oscillation
    }

    // A dependency-light cosine (kotlin.math.cos is available, but keeps intent explicit here).
    private fun cosApprox(x: Double): Double = kotlin.math.cos(x)
}
