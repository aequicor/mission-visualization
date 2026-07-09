package io.aequicor.visualization.engine.scene.sample

import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.scene.runtime.SceneSnapshot

/** Per-layer overrides for the outgoing (`from`) and incoming (`to`) screens plus paint order. */
data class TransitionSample(
    val from: VisualOverride,
    val to: VisualOverride,
    /** Ascending z: the larger paints on top. */
    val fromZ: Int,
    val toZ: Int,
)

/**
 * Samples a transition into `from`/`to` [VisualOverride]s. [progress] is the ALREADY-EASED value
 * in `[0,1]` (the caller applies [EasingSampler]); this sampler is pure geometry. Direction-aware
 * translation is expressed in viewport units so a Push/Slide covers the whole screen.
 *
 * `SmartAnimate` is an advanced type; the MVP delegates it to a dissolve via [SmartAnimateMatcher].
 */
object TransitionSampler {

    fun sample(
        spec: DesignTransition,
        progress: Double,
        viewportW: Double,
        viewportH: Double,
    ): TransitionSample {
        val p = progress.coerceIn(0.0, 1.0)
        val horizontal = spec.direction == TransitionDirection.Left || spec.direction == TransitionDirection.Right
        val span = if (horizontal) viewportW else viewportH
        // Incoming start offset and outgoing end offset along the motion axis.
        val inStart: Double
        val outEnd: Double
        when (spec.direction) {
            TransitionDirection.Left -> { inStart = span; outEnd = -span }
            TransitionDirection.Right -> { inStart = -span; outEnd = span }
            TransitionDirection.Top -> { inStart = span; outEnd = -span }
            TransitionDirection.Bottom -> { inStart = -span; outEnd = span }
        }
        fun axis(value: Double): Pair<Double, Double> = if (horizontal) value to 0.0 else 0.0 to value

        return when (spec.type) {
            TransitionType.Instant -> {
                val swapped = p >= 0.5
                TransitionSample(
                    from = VisualOverride(opacity = if (swapped) 0.0 else 1.0),
                    to = VisualOverride(opacity = if (swapped) 1.0 else 0.0),
                    fromZ = 0, toZ = 1,
                )
            }
            TransitionType.Dissolve, TransitionType.SmartAnimate -> TransitionSample(
                from = VisualOverride(opacity = 1.0 - p),
                to = VisualOverride(opacity = p),
                fromZ = 0, toZ = 1,
            )
            TransitionType.Push -> {
                val (fx, fy) = axis(outEnd * p)
                val (tx, ty) = axis(inStart * (1.0 - p))
                TransitionSample(
                    from = VisualOverride(translateX = fx, translateY = fy),
                    to = VisualOverride(translateX = tx, translateY = ty),
                    fromZ = 0, toZ = 1,
                )
            }
            TransitionType.MoveIn, TransitionType.SlideIn -> {
                // New screen enters over a stationary old screen.
                val (tx, ty) = axis(inStart * (1.0 - p))
                TransitionSample(
                    from = VisualOverride.Identity,
                    to = VisualOverride(translateX = tx, translateY = ty),
                    fromZ = 0, toZ = 1,
                )
            }
            TransitionType.MoveOut, TransitionType.SlideOut -> {
                // Old screen leaves, revealing the new screen underneath.
                val (fx, fy) = axis(outEnd * p)
                TransitionSample(
                    from = VisualOverride(translateX = fx, translateY = fy),
                    to = VisualOverride.Identity,
                    fromZ = 1, toZ = 0,
                )
            }
        }
    }
}

/** Seam for smart-animate node matching. MVP delegates to a dissolve (no per-node matching). */
interface SmartAnimateMatcher {
    fun match(from: SceneSnapshot, to: SceneSnapshot, progress: Double): List<NodeOverride>
}

/** Default matcher: no per-node overrides (the layer-level dissolve carries the transition). */
object NoOpSmartAnimateMatcher : SmartAnimateMatcher {
    override fun match(from: SceneSnapshot, to: SceneSnapshot, progress: Double): List<NodeOverride> = emptyList()
}
