package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Coverage for [solveResizeSnap]: moving-edge alignment, dimension matching, corners and hysteresis. */
class ResizeSnapTest {

    private val catch = 7.0
    private val release = 16.0
    private val right = MovingEdges(left = false, right = true, top = false, bottom = false)
    private val left = MovingEdges(left = true, right = false, top = false, bottom = false)
    private val bottomRight = MovingEdges(left = false, right = true, top = false, bottom = true)

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }

    @Test
    fun rightEdgeSnapsToNeighbourEdge() {
        val candidate = SnapBox(0.0, 0.0, 100.0, 50.0) // right edge at 100
        val sibling = SnapBox(104.0, 0.0, 40.0, 40.0)  // left edge at 104 (4px away)
        val out = solveResizeSnap(candidate, right, emptyList(), listOf(sibling), catch, release, ResizeSnapState.None)
        assertClose(4.0, out.dx)
        assertClose(0.0, out.dy)
        assertFalse(out.match.widthMatched)
        assertClose(104.0, out.match.width)     // width grew from 100 to 104
        assertEquals(1, out.guides.size)
        assertEquals(AnchorKind.Alignment, out.guides.single().kind)
        assertClose(104.0, out.guides.single().line.x1)
    }

    @Test
    fun rightEdgeMatchesSiblingWidth() {
        // No neighbour edge near the moving edge, but a far sibling is 60 wide and the box is ~60 → match.
        val candidate = SnapBox(0.0, 0.0, 58.0, 50.0) // width 58, right edge at 58
        val sibling = SnapBox(500.0, 0.0, 60.0, 40.0) // 60 wide, edges far from the moving edge
        val out = solveResizeSnap(candidate, right, emptyList(), listOf(sibling), catch, release, ResizeSnapState.None)
        assertClose(2.0, out.dx)                 // right edge nudged from 58 → 60 so width == 60
        assertTrue(out.match.widthMatched)
        assertClose(60.0, out.match.width)
        assertTrue(out.guides.isEmpty())         // a dimension match draws the badge, not a guide line
    }

    @Test
    fun leftEdgeSnapsToContainerEdgeAndGrowsWidth() {
        val candidate = SnapBox(4.0, 0.0, 100.0, 50.0) // left edge at 4
        val container = SnapBox(0.0, 0.0, 1000.0, 1000.0)
        val out = solveResizeSnap(candidate, left, listOf(container), emptyList(), catch, release, ResizeSnapState.None)
        assertClose(-4.0, out.dx)                // left edge pulled from 4 → 0
        assertClose(104.0, out.match.width)      // opposite (right) edge pinned → width grows by 4
        assertClose(0.0, out.guides.single().line.x1)
    }

    @Test
    fun cornerSnapsBothAxes() {
        val candidate = SnapBox(0.0, 0.0, 100.0, 50.0) // right edge 100, bottom edge 50
        val sibX = SnapBox(103.0, 0.0, 40.0, 40.0)     // left 103 (3px from right edge)
        val sibY = SnapBox(0.0, 53.0, 40.0, 40.0)      // top 53 (3px from bottom edge)
        val out = solveResizeSnap(candidate, bottomRight, emptyList(), listOf(sibX, sibY), catch, release, ResizeSnapState.None)
        assertClose(3.0, out.dx)
        assertClose(3.0, out.dy)
        assertEquals(2, out.guides.size)
    }

    @Test
    fun latchHoldsInsideReleaseBand() {
        val sibling = SnapBox(104.0, 0.0, 40.0, 40.0)
        val candidate = SnapBox(0.0, 0.0, 92.0, 50.0) // right edge at 92 → 12px from the sibling edge
        val held = solveResizeSnap(candidate, right, emptyList(), listOf(sibling), catch, release, ResizeSnapState(latchX = 104.0))
        assertClose(12.0, held.dx)               // within release (16) while latched → still snaps
        val free = solveResizeSnap(candidate, right, emptyList(), listOf(sibling), catch, release, ResizeSnapState.None)
        assertClose(0.0, free.dx)                // 12px with no latch is beyond catch (7) → no snap
    }

    @Test
    fun inactiveAxisReturnsZero() {
        val candidate = SnapBox(0.0, 0.0, 100.0, 50.0)
        val sibY = SnapBox(0.0, 53.0, 40.0, 40.0) // a Y-axis neighbour the right handle must ignore
        val out = solveResizeSnap(candidate, right, emptyList(), listOf(sibY), catch, release, ResizeSnapState.None)
        assertClose(0.0, out.dy)
        assertFalse(out.match.heightMatched)
    }
}
