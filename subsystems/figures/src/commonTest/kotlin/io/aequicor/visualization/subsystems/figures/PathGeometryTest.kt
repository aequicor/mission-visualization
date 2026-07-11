package io.aequicor.visualization.subsystems.figures

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathGeometryTest {

    private fun assertClose(expected: Double, actual: Double, eps: Double = 1e-6) {
        assertTrue(abs(expected - actual) <= eps, "expected $expected but got $actual")
    }

    // --- parametric primitives ---

    @Test
    fun regularPolygonHasVertexPerSideAndClosesAtTop() {
        val g = regularPolygonGeometry(RectD(0.0, 0.0, 100.0, 100.0), 3)
        assertEquals(4, g.commands.size) // MoveTo + 2 LineTo + Close
        val first = g.commands.first() as PathCommand.MoveTo
        assertClose(50.0, first.x)
        assertClose(0.0, first.y) // first vertex at 12 o'clock
        assertTrue(g.commands.last() is PathCommand.Close)
        assertTrue(g.isClosed)
    }

    @Test
    fun polygonCoercesToAtLeastThreeSides() {
        val g = regularPolygonGeometry(RectD(0.0, 0.0, 10.0, 10.0), 1)
        val vertices = g.commands.count { it is PathCommand.MoveTo || it is PathCommand.LineTo }
        assertEquals(3, vertices)
    }

    @Test
    fun starAlternatesOuterAndInnerRadius() {
        val g = starGeometry(RectD(0.0, 0.0, 100.0, 100.0), 5, 0.4)
        val points = g.commands.count { it is PathCommand.MoveTo || it is PathCommand.LineTo }
        assertEquals(10, points) // 5 outer + 5 inner
        val first = g.commands.first() as PathCommand.MoveTo
        assertClose(50.0, first.x)
        assertClose(0.0, first.y)
    }

    @Test
    fun lineIsOpenHorizontalCenterline() {
        val g = lineGeometry(RectD(10.0, 20.0, 110.0, 40.0))
        assertFalse(g.isClosed)
        val m = g.commands[0] as PathCommand.MoveTo
        val l = g.commands[1] as PathCommand.LineTo
        assertClose(10.0, m.x); assertClose(30.0, m.y)
        assertClose(110.0, l.x); assertClose(30.0, l.y)
    }

    @Test
    fun arrowIsOpenAndHeadScalesWithWeight() {
        val g = arrowGeometry(RectD(0.0, 0.0, 100.0, 20.0), strokeWeight = 4.0)
        assertFalse(g.isClosed)
        // tip reaches the right edge at center height
        val tip = g.commands.filterIsInstance<PathCommand.LineTo>().first { abs(it.x - 100.0) < 1e-9 }
        assertClose(10.0, tip.y) // center of a 20-tall box
    }

    @Test
    fun ellipseIsFourCubics() {
        val g = ellipseGeometry(RectD(0.0, 0.0, 100.0, 50.0))
        assertEquals(4, g.commands.count { it is PathCommand.CubicTo })
        assertTrue(g.isClosed)
        val m = g.commands.first() as PathCommand.MoveTo
        assertClose(0.0, m.x); assertClose(25.0, m.y) // leftmost point
    }

    @Test
    fun roundedRectWithZeroRadiiIsPlainRect() {
        val g = roundedRectGeometry(RectD(0.0, 0.0, 10.0, 10.0), 0.0, 0.0, 0.0, 0.0)
        assertEquals(0, g.commands.count { it is PathCommand.CubicTo })
        assertEquals(5, g.commands.size) // MoveTo + 3 LineTo + Close
    }

    @Test
    fun roundedRectRadiusClampsToHalfMinDimension() {
        val g = roundedRectGeometry(RectD(0.0, 0.0, 20.0, 20.0), 1000.0, 1000.0, 1000.0, 1000.0)
        assertEquals(4, g.commands.count { it is PathCommand.CubicTo })
        // start point x = left + clamped radius (10) => 10
        val m = g.commands.first() as PathCommand.MoveTo
        assertClose(10.0, m.x); assertClose(0.0, m.y)
    }

    // --- SVG parsing ---

    @Test
    fun parsesBasicCommandsAndClose() {
        val g = parseSvgPathToGeometry("M0 0 L10 0 L10 10 Z")
        assertEquals(
            listOf(
                PathCommand.MoveTo(0.0, 0.0),
                PathCommand.LineTo(10.0, 0.0),
                PathCommand.LineTo(10.0, 10.0),
                PathCommand.Close,
            ),
            g.commands,
        )
    }

    @Test
    fun relativeCommandsAccumulate() {
        val g = parseSvgPathToGeometry("m5 5 l10 0 l0 10")
        assertEquals(PathCommand.MoveTo(5.0, 5.0), g.commands[0])
        assertEquals(PathCommand.LineTo(15.0, 5.0), g.commands[1])
        assertEquals(PathCommand.LineTo(15.0, 15.0), g.commands[2])
    }

    @Test
    fun implicitLineToAfterMoveTo() {
        val g = parseSvgPathToGeometry("M0 0 10 0 20 0")
        assertEquals(PathCommand.MoveTo(0.0, 0.0), g.commands[0])
        assertEquals(PathCommand.LineTo(10.0, 0.0), g.commands[1])
        assertEquals(PathCommand.LineTo(20.0, 0.0), g.commands[2])
    }

    @Test
    fun smoothCubicReflectsControlPoint() {
        val g = parseSvgPathToGeometry("M0 0 C10 0 10 10 20 10 S30 20 40 10")
        val s = g.commands[2] as PathCommand.CubicTo
        // reflected first control = 2*current - lastControl = 2*(20,10) - (10,10) = (30,10)
        assertClose(30.0, s.c1x); assertClose(10.0, s.c1y)
    }

    @Test
    fun windingRulePropagatesToFillRule() {
        val g = parseSvgPathToGeometry("M0 0 L1 0 L1 1 Z", PathFillRule.EvenOdd)
        assertEquals(PathFillRule.EvenOdd, g.fillRule)
    }

    @Test
    fun malformedPathReturnsPartial() {
        val g = parseSvgPathToGeometry("M0 0 L10 10 L")
        assertEquals(2, g.commands.size) // trailing dangling L dropped
    }

    // --- arcs ---

    @Test
    fun arcEndsAtExactEndpoint() {
        val g = parseSvgPathToGeometry("M0 0 A50 50 0 0 1 100 0")
        val last = g.commands.last() as PathCommand.CubicTo
        assertClose(100.0, last.x, 1e-6)
        assertClose(0.0, last.y, 1e-6)
    }

    @Test
    fun semicircleArcApexBulgesByRadius() {
        // exact semicircle from (0,0) to (100,0) with r=50 => two 90° cubics; apex at (50, ±50)
        val g = parseSvgPathToGeometry("M0 0 A50 50 0 0 1 100 0")
        val cubics = g.commands.filterIsInstance<PathCommand.CubicTo>()
        assertEquals(2, cubics.size)
        val apex = cubics[0] // endpoint of the first 90° segment is the arc midpoint
        assertClose(50.0, apex.x, 0.5)
        assertClose(50.0, abs(apex.y), 0.5)
    }

    @Test
    fun zeroRadiusArcDegradesToLine() {
        val commands = arcToCubics(0.0, 0.0, 0.0, 50.0, 0.0, false, true, 30.0, 40.0)
        assertEquals(listOf(PathCommand.LineTo(30.0, 40.0)), commands)
    }

    @Test
    fun oversizeRadiiAreScaledUpWithoutNaN() {
        // radii too small to span the endpoints => algorithm must grow them; endpoint stays exact
        val commands = arcToCubics(0.0, 0.0, 1.0, 1.0, 0.0, false, true, 100.0, 0.0)
        val last = commands.last() as PathCommand.CubicTo
        assertClose(100.0, last.x, 1e-6)
        assertClose(0.0, last.y, 1e-6)
        assertFalse(last.x.isNaN())
    }

    @Test
    fun relativeArcAddsToCurrentPoint() {
        val abs = parseSvgPathToGeometry("M10 10 A20 20 0 0 1 50 10").commands.last() as PathCommand.CubicTo
        val rel = parseSvgPathToGeometry("M10 10 a20 20 0 0 1 40 0").commands.last() as PathCommand.CubicTo
        assertClose(abs.x, rel.x, 1e-6)
        assertClose(abs.y, rel.y, 1e-6)
    }

    // --- meetFit ---

    @Test
    fun meetFitPreservesAspectAndCenters() {
        val fit = meetFit(RectD(0.0, 0.0, 10.0, 10.0), RectD(0.0, 0.0, 100.0, 200.0))
        // uniform scale = min(100/10, 200/10) = 10; centered vertically
        val (x, y) = fit.apply(10.0, 10.0)
        assertClose(100.0, x)
        assertClose(150.0, y) // top offset (200-100)/2 = 50, +10*10
    }

    @Test
    fun meetFitSubtractsViewBoxOrigin() {
        val fit = meetFit(RectD(4.0, 8.0, 24.0, 28.0), RectD(0.0, 0.0, 100.0, 100.0))
        val (x, y) = fit.apply(4.0, 8.0) // maps the viewBox origin
        assertClose(0.0, x)
        assertClose(0.0, y)
    }

    @Test
    fun affineInverseRoundTrips() {
        val fit = meetFit(RectD(2.0, 3.0, 22.0, 33.0), RectD(5.0, 5.0, 205.0, 305.0))
        val (x, y) = fit.apply(7.0, 9.0)
        val (bx, by) = fit.inverse().apply(x, y)
        assertClose(7.0, bx, 1e-9)
        assertClose(9.0, by, 1e-9)
    }

    // --- hit-testing ---

    @Test
    fun containsInsideAndOutsidePolygon() {
        val g = regularPolygonGeometry(RectD(0.0, 0.0, 100.0, 100.0), 4) // diamond
        assertTrue(contains(g, 50.0, 50.0)) // center
        assertFalse(contains(g, 2.0, 2.0)) // corner of bbox, outside the diamond
    }

    @Test
    fun containsMissesStarNotch() {
        val g = starGeometry(RectD(0.0, 0.0, 100.0, 100.0), 5, 0.4)
        assertTrue(contains(g, 50.0, 50.0)) // center is solid
        assertFalse(contains(g, 2.0, 50.0)) // far-left notch between points is empty
    }

    @Test
    fun evenOddCutsOutInnerLoop() {
        // outer 0..100 square, inner 25..75 square, evenodd => donut with a hole
        val donut = parseSvgPathToGeometry(
            "M0 0 L100 0 L100 100 L0 100 Z M25 25 L75 25 L75 75 L25 75 Z",
            PathFillRule.EvenOdd,
        )
        assertTrue(contains(donut, 10.0, 10.0)) // in the ring
        assertFalse(contains(donut, 50.0, 50.0)) // in the hole
    }

    @Test
    fun nonzeroFillsInnerLoopWhenSameDirection() {
        val filled = parseSvgPathToGeometry(
            "M0 0 L100 0 L100 100 L0 100 Z M25 25 L75 25 L75 75 L25 75 Z",
            PathFillRule.NonZero,
        )
        assertTrue(contains(filled, 50.0, 50.0)) // same-winding inner loop stays filled
    }

    @Test
    fun distanceToOutlineForOpenLine() {
        val g = lineGeometry(RectD(0.0, 0.0, 100.0, 0.0)) // centerline along y=0
        assertClose(0.0, distanceToOutline(g, 50.0, 0.0))
        assertClose(5.0, distanceToOutline(g, 50.0, 5.0))
    }
}
