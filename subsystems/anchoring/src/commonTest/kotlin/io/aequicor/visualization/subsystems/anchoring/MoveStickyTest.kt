package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hysteresis (catch/release) coverage for [solveMoveSnap]. Uses one huge container so a single
 * isolated anchor — the container's left edge at x=0 — is the only candidate on X, and the box's
 * Y band sits far from every container Y line, so X can be exercised in isolation.
 */
class MoveStickyTest {

    private val container = SnapBox(x = 0.0, y = 0.0, width = 100_000.0, height = 100_000.0)
    private val catch = 7.0
    private val release = 16.0

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }

    /** A 40x40 box at [x], parked at y=30000 (far from every container Y line) unless [y] overrides it. */
    private fun solve(x: Double, prior: MoveSnapState, y: Double = 30_000.0, allowX: Boolean = true, allowY: Boolean = true) =
        solveMoveSnap(SnapBox(x, y, 40.0, 40.0), listOf(container), emptyList(), catch, release, allowX, allowY, prior)

    @Test
    fun catchEngagesWithinCatchRadius() {
        val out = solve(x = 4.0, prior = MoveSnapState.None) // left edge 4px from the container edge (≤ catch)
        assertTrue(out.snappedX)
        assertClose(-4.0, out.dx)          // pulled onto the edge
        assertClose(0.0, out.state.latchX!!)
        assertFalse(out.snappedY)
        assertNull(out.state.latchY)
    }

    @Test
    fun freeDoesNotCatchInsideHysteresisBand() {
        // 10px away, no prior latch: beyond catch (7) and must NOT engage — engagement needs catch.
        val out = solve(x = 10.0, prior = MoveSnapState.None)
        assertFalse(out.snappedX)
        assertClose(0.0, out.dx)
        assertNull(out.state.latchX)
    }

    @Test
    fun holdsInsideHysteresisBandWhileLatched() {
        // Same 10px offset, but already latched to the edge: it holds (searches at the release radius).
        val out = solve(x = 10.0, prior = MoveSnapState(latchX = 0.0))
        assertTrue(out.snappedX)
        assertClose(-10.0, out.dx)
        assertClose(0.0, out.state.latchX!!)
    }

    @Test
    fun releasesBeyondReleaseRadius() {
        // 20px away (> release 16): the latch drops and the axis is free again.
        val out = solve(x = 20.0, prior = MoveSnapState(latchX = 0.0))
        assertFalse(out.snappedX)
        assertNull(out.state.latchX)
    }

    @Test
    fun releaseIsCoercedToAtLeastCatch() {
        // release < catch is nonsensical; it must be coerced up to catch. Latched, 5px away, with
        // release=3: were 3 honoured the box (5px out) would release, but coerced to catch=7 it holds.
        val out = solveMoveSnap(
            SnapBox(5.0, 30_000.0, 40.0, 40.0), listOf(container), emptyList(),
            catch = 7.0, release = 3.0, allowX = true, allowY = true, prior = MoveSnapState(latchX = 0.0),
        )
        assertTrue(out.snappedX)
        assertClose(-5.0, out.dx)
    }

    @Test
    fun axisLockDropsThatAxisLatch() {
        // A Shift axis-lock (allowX = false) zeroes X even though the box is within catch, and clears its latch.
        val out = solve(x = 4.0, prior = MoveSnapState(latchX = 0.0, latchY = 1234.0), allowX = false)
        assertFalse(out.snappedX)
        assertClose(0.0, out.dx)
        assertNull(out.state.latchX)
    }

    @Test
    fun snappedFlagsAreIndependentPerAxis() {
        // Box in both corners of the container: both edges within catch → both axes snap.
        val out = solve(x = 4.0, y = 4.0, prior = MoveSnapState.None)
        assertTrue(out.snappedX)
        assertTrue(out.snappedY)
        assertEquals(0.0, out.state.latchX)
        assertEquals(0.0, out.state.latchY)
    }
}
