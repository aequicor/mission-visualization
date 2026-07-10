package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EllipseArcGeometryTest {

    private val box = RectD(0.0, 0.0, 100.0, 100.0)

    @Test
    fun fullSweepEqualsPlainEllipse() {
        assertEquals(ellipseGeometry(box).commands, ellipseArcGeometry(box, 0.0, 360.0, 0.0).commands)
    }

    @Test
    fun sweepBeyondFullCircleClampsToFullEllipse() {
        assertEquals(ellipseGeometry(box).commands, ellipseArcGeometry(box, 0.0, 720.0, 0.0).commands)
    }

    @Test
    fun quadrantPieCoversBottomRight() {
        // 0° is 3 o'clock; a +90° clockwise sweep (screen y-down) covers the bottom-right quadrant.
        val pie = ellipseArcGeometry(box, 0.0, 90.0, 0.0)
        assertTrue(pie.isClosed)
        // A point clearly in the bottom-right wedge is inside; top-left is not.
        assertTrue(contains(pie, 70.0, 70.0))
        assertFalse(contains(pie, 30.0, 30.0))
    }

    @Test
    fun pieStartsAtRequestedAngle() {
        val pie = ellipseArcGeometry(box, 0.0, 90.0, 0.0)
        val move = pie.commands.first() as PathCommand.MoveTo
        // start at 3 o'clock => (right edge midpoint)
        assertTrue(kotlin.math.abs(move.x - 100.0) < 1e-6, "x=${move.x}")
        assertTrue(kotlin.math.abs(move.y - 50.0) < 1e-6, "y=${move.y}")
    }

    @Test
    fun fullDonutHasEvenOddHole() {
        val ring = ellipseArcGeometry(box, 0.0, 360.0, 0.5)
        assertEquals(PathFillRule.EvenOdd, ring.fillRule)
        // The center falls inside the hole -> not contained.
        assertFalse(contains(ring, 50.0, 50.0))
        // A point in the ring band (near the outer edge) is contained.
        assertTrue(contains(ring, 95.0, 50.0))
    }

    @Test
    fun donutSegmentIsClosed() {
        val seg = ellipseArcGeometry(box, 0.0, 120.0, 0.5)
        assertTrue(seg.isClosed)
        // The hole center is not part of a donut segment.
        assertFalse(contains(seg, 50.0, 50.0))
    }

    @Test
    fun zeroSweepIsEmpty() {
        assertTrue(ellipseArcGeometry(box, 30.0, 0.0, 0.0).commands.isEmpty())
    }
}
