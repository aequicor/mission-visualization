package io.aequicor.visualization.engine.scene.sample

import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.scene.runtime.ActiveAnimation
import io.aequicor.visualization.engine.scene.runtime.AnimationKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplerTest {

    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= eps, "expected ~$expected but was $actual")

    @Test
    fun easingEndpointsAreExactForEveryKind() {
        val easings = EasingKind.entries.map { DesignEasing.Named(it) } +
            DesignEasing.CubicBezier(0.42, 0.0, 0.58, 1.0) +
            DesignEasing.Spring(mass = 1.0, stiffness = 100.0, damping = 15.0)
        easings.forEach { easing ->
            near(0.0, EasingSampler.evaluate(easing, 0.0))
            near(1.0, EasingSampler.evaluate(easing, 1.0))
        }
    }

    @Test
    fun easeInOutIsMonotonicAndSymmetric() {
        val e = DesignEasing.Named(EasingKind.EaseInOut)
        var prev = -1.0
        var t = 0.0
        while (t <= 1.0001) {
            val v = EasingSampler.evaluate(e, t)
            assertTrue(v >= prev - 1e-9, "EaseInOut must be non-decreasing at t=$t")
            prev = v
            t += 0.05
        }
        near(0.5, EasingSampler.evaluate(e, 0.5))
    }

    @Test
    fun cubicBezierStaysWithinUnitRangeAndEasesOut() {
        val e = DesignEasing.CubicBezier(0.25, 0.1, 0.25, 1.0)
        val mid = EasingSampler.evaluate(e, 0.5)
        assertTrue(mid in 0.0..1.0)
        assertTrue(mid > 0.5, "an ease-out bezier is ahead of linear at the midpoint")
    }

    @Test
    fun motionInterpolatesSparseOpacityTrack() {
        val frames = MotionKeyframes(
            durationMs = 900.0,
            loop = true,
            frames = listOf(
                MotionFrame(0.0, mapOf("opacity" to 0.4)),
                MotionFrame(0.5, mapOf("opacity" to 1.0)),
                MotionFrame(1.0, mapOf("opacity" to 0.4)),
            ),
        )
        near(0.4, MotionSampler.sample(frames, 0.0).opacity)
        near(1.0, MotionSampler.sample(frames, 0.5).opacity)
        near(0.7, MotionSampler.sample(frames, 0.25).opacity)
        // Unanimated properties keep identity.
        near(0.0, MotionSampler.sample(frames, 0.25).translateX)
        near(1.0, MotionSampler.sample(frames, 0.25).scaleX)
    }

    @Test
    fun loopingClipWrapsPhase() {
        val anim = ActiveAnimation(
            id = "m", kind = AnimationKind.Motion, nodeId = "dot",
            startTimeMs = 0.0, durationMs = 900.0, loop = true,
        )
        near(0.5, MotionSampler.localNormalized(anim, 450.0))
        near(0.0, MotionSampler.localNormalized(anim, 900.0))
        near(0.5, MotionSampler.localNormalized(anim, 1350.0))
    }

    @Test
    fun pushTransitionTranslatesBothLayersAcrossViewport() {
        val spec = DesignTransition(type = TransitionType.Push, direction = TransitionDirection.Left, durationMs = 300.0)
        val at0 = TransitionSampler.sample(spec, 0.0, viewportW = 400.0, viewportH = 800.0)
        near(400.0, at0.to.translateX) // incoming starts fully off-screen right
        near(0.0, at0.from.translateX)

        val mid = TransitionSampler.sample(spec, 0.5, 400.0, 800.0)
        near(200.0, mid.to.translateX)
        near(-200.0, mid.from.translateX)

        val at1 = TransitionSampler.sample(spec, 1.0, 400.0, 800.0)
        near(0.0, at1.to.translateX)
        near(-400.0, at1.from.translateX)
        assertEquals(1, at1.toZ)
    }

    @Test
    fun dissolveCrossfadesOpacity() {
        val spec = DesignTransition(type = TransitionType.Dissolve, durationMs = 200.0)
        val mid = TransitionSampler.sample(spec, 0.3, 400.0, 800.0)
        near(0.7, mid.from.opacity)
        near(0.3, mid.to.opacity)
    }
}
