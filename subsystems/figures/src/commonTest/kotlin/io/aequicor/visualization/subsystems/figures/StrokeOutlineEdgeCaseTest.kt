package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Degenerate / edge-case hardening for [strokeOutline]: zero-length subpaths, closed (annular)
 * strokes, the miter→bevel fallback past the limit on a very acute angle, and round-join arc fill.
 */
class StrokeOutlineEdgeCaseTest {

    @Test
    fun zeroLengthSubpathDoesNotCrash() {
        // A subpath that never moves (degenerate) followed by a real segment.
        val g = PathGeometry(
            listOf(
                PathCommand.MoveTo(10.0, 10.0),
                PathCommand.LineTo(10.0, 10.0),
                PathCommand.MoveTo(20.0, 50.0),
                PathCommand.LineTo(80.0, 50.0),
            ),
        )
        val out = strokeOutline(g, width = 8.0, cap = "butt")
        // The real segment still produces a band; the degenerate one is simply skipped.
        assertTrue(contains(out, 50.0, 50.0), "real segment stroked")
    }

    @Test
    fun bareMoveOnlySubpathDoesNotCrash() {
        val g = PathGeometry(listOf(PathCommand.MoveTo(5.0, 5.0)))
        val out = strokeOutline(g, width = 6.0)
        assertTrue(out.commands.isEmpty(), "nothing to stroke -> empty, no crash")
    }

    @Test
    fun closedTriangleStrokeIsAnnular() {
        val triangle = PathGeometry(
            listOf(
                PathCommand.MoveTo(10.0, 10.0),
                PathCommand.LineTo(90.0, 10.0),
                PathCommand.LineTo(50.0, 80.0),
                PathCommand.Close,
            ),
        )
        val g = strokeOutline(triangle, width = 8.0, cap = "butt")
        assertTrue(contains(g, 50.0, 10.0), "bottom edge is stroked")
        assertFalse(contains(g, 50.0, 40.0), "hollow interior is not filled (annular)")
    }

    // A near fold-back spike: A -> V at a shallow return angle so the interior corner is very acute.
    private fun acuteSpike(): PathGeometry = PathGeometry(
        listOf(
            PathCommand.MoveTo(0.0, 10.0),
            PathCommand.LineTo(100.0, 10.0),
            PathCommand.LineTo(0.0, 13.0),
        ),
    )

    @Test
    fun acuteMiterFallsBackToBevelApexNotFilled() {
        // Past the miter limit the join degrades to bevel, so the far apex region stays empty.
        // Only the miter wedge could ever reach beyond the x=100 endpoints of both segments.
        val g = strokeOutline(acuteSpike(), width = 10.0, join = "miter", miterLimit = 4.0)
        assertFalse(contains(g, 112.0, 11.5), "acute apex is NOT filled (bevel fallback)")
    }

    @Test
    fun generousMiterLimitFillsAcuteApex() {
        // Same geometry, but a limit large enough to keep the miter: the apex is now filled,
        // confirming the previous test's emptiness is the fallback, not the geometry.
        val g = strokeOutline(acuteSpike(), width = 10.0, join = "miter", miterLimit = 1000.0)
        assertTrue(contains(g, 112.0, 11.5), "large miter limit fills the acute apex")
    }

    // An L: (10,50) -> (50,50) -> (50,10); right-angle join at (50,50), outer side toward (55,55).
    private fun elbow(): PathGeometry = PathGeometry(
        listOf(PathCommand.MoveTo(10.0, 50.0), PathCommand.LineTo(50.0, 50.0), PathCommand.LineTo(50.0, 10.0)),
    )

    @Test
    fun roundJoinFillsTheArc() {
        val g = strokeOutline(elbow(), width = 10.0, join = "round")
        // Inside the join disc (radius 5): distance from the corner is ~4.2 -> filled by the arc.
        assertTrue(contains(g, 53.0, 53.0), "round join fills within its arc radius")
        // Just past the disc radius the arc no longer covers the corner (miter's (54,54) is empty here).
        assertFalse(contains(g, 55.0, 55.0), "outside the arc radius is empty")
    }
}
