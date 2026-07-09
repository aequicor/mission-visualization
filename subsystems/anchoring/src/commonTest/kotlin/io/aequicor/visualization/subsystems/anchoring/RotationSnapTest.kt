package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Coverage for [solveRotateSnap]: key-angle & neighbour magnets, wrap-around, tie-breaks and hysteresis. */
class RotationSnapTest {

    private val catch = 4.0
    private val release = 9.0

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }

    private fun snap(free: Double, neighbors: List<Double> = emptyList(), prior: RotateSnapState = RotateSnapState.None) =
        solveRotateSnap(free, KeyRotationAngles, neighborRotationCandidates(neighbors), catch, release, prior)

    @Test
    fun catchesNearestKeyAngle() {
        val out = snap(free = 47.0)
        assertTrue(out.caught)
        assertEquals(RotationSnapTargetKind.KeyAngle, out.kind)
        assertClose(45.0, out.angle)
        assertClose(45.0, out.state.latch!!)
    }

    @Test
    fun doesNotCatchOutsideRadius() {
        val out = snap(free = 52.0) // 7° from 45, beyond catch (4)
        assertFalse(out.caught)
        assertClose(52.0, out.angle)
        assertEquals(null, out.state.latch)
    }

    @Test
    fun wrapsAroundZero() {
        val out = snap(free = 358.0) // 2° from 0/360
        assertTrue(out.caught)
        assertClose(0.0, out.angle)
    }

    @Test
    fun catchesNeighbourRotation() {
        val out = snap(free = 32.0, neighbors = listOf(30.0)) // 2° from a neighbour at 30°
        assertTrue(out.caught)
        assertEquals(RotationSnapTargetKind.Neighbor, out.kind)
        assertClose(30.0, out.angle)
    }

    @Test
    fun keyAngleWinsTieOverNeighbour() {
        // 44° is 1° from the key angle 45 and 1° from a neighbour at 43 — the key angle wins.
        val out = snap(free = 44.0, neighbors = listOf(43.0))
        assertTrue(out.caught)
        assertEquals(RotationSnapTargetKind.KeyAngle, out.kind)
        assertClose(45.0, out.angle)
    }

    @Test
    fun holdsInsideReleaseBandWhileLatched() {
        // 7° from 45: beyond catch (4) but within release (9) — holds only when already latched.
        val held = snap(free = 52.0, prior = RotateSnapState(latch = 45.0))
        assertTrue(held.caught)
        assertClose(45.0, held.angle)
        val free = snap(free = 52.0)
        assertFalse(free.caught)
    }

    @Test
    fun neighbourCandidatesIncludePerpendicular() {
        val candidates = neighborRotationCandidates(listOf(30.0))
        assertTrue(candidates.any { abs(it - 30.0) < 1e-9 })
        assertTrue(candidates.any { abs(it - 120.0) < 1e-9 })
    }
}
