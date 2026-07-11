package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokeOutlineTest {

    private fun horizontalLine(): PathGeometry = PathGeometry(
        listOf(PathCommand.MoveTo(10.0, 50.0), PathCommand.LineTo(90.0, 50.0)),
    )

    @Test
    fun buttLineOutlinesToABand() {
        val g = strokeOutline(horizontalLine(), width = 10.0, cap = "butt")
        assertEquals(PathFillRule.NonZero, g.fillRule)
        // A point on the stroke centre band is inside; well above/below is not.
        assertTrue(contains(g, 50.0, 50.0))
        assertTrue(contains(g, 50.0, 53.0))
        assertFalse(contains(g, 50.0, 60.0))
        // Butt cap: nothing beyond the endpoints.
        assertFalse(contains(g, 96.0, 50.0))
    }

    @Test
    fun squareCapExtendsBeyondEndpoint() {
        val g = strokeOutline(horizontalLine(), width = 10.0, cap = "square")
        // Square cap extends by half-width (5) past x=90.
        assertTrue(contains(g, 93.0, 50.0))
        assertFalse(contains(g, 97.0, 50.0))
    }

    @Test
    fun roundCapCoversSemicircleEnd() {
        val g = strokeOutline(horizontalLine(), width = 10.0, cap = "round")
        // Round cap disc of radius 5 at x=90.
        assertTrue(contains(g, 93.0, 50.0))
        assertTrue(contains(g, 90.0, 54.0))
    }

    @Test
    fun closedRectStrokeFormsAnnularBand() {
        val rect = roundedRectGeometry(RectD(0.0, 0.0, 100.0, 100.0), 0.0, 0.0, 0.0, 0.0)
        val g = strokeOutline(rect, width = 8.0, cap = "butt")
        // The band covers the rectangle's edges; the hollow centre is not part of the stroke.
        assertTrue(contains(g, 0.0, 50.0)) // on the left edge
        assertFalse(contains(g, 50.0, 50.0)) // interior hole
    }

    @Test
    fun zeroWidthIsEmpty() {
        assertTrue(strokeOutline(horizontalLine(), width = 0.0).commands.isEmpty())
    }

    // An L: (10,50) -> (50,50) -> (50,10); right-angle join at (50,50).
    private fun elbow(): PathGeometry = PathGeometry(
        listOf(PathCommand.MoveTo(10.0, 50.0), PathCommand.LineTo(50.0, 50.0), PathCommand.LineTo(50.0, 10.0)),
    )

    @Test
    fun miterJoinFillsOuterCorner() {
        val g = strokeOutline(elbow(), width = 10.0, join = "miter")
        // The outer miter apex is at (55,55); a point just inside it is only covered by the join.
        assertTrue(contains(g, 54.0, 54.0), "miter fills the outer corner")
    }

    @Test
    fun bevelJoinLeavesOuterCorner() {
        val g = strokeOutline(elbow(), width = 10.0, join = "bevel")
        // Bevel cuts the corner off along (50,55)-(55,50); the apex region stays empty.
        assertFalse(contains(g, 54.0, 54.0), "bevel does not reach the apex")
    }

    @Test
    fun insideAlignClipsToFill() {
        val rect = roundedRectGeometry(RectD(0.0, 0.0, 100.0, 100.0), 0.0, 0.0, 0.0, 0.0)
        val g = strokeOutline(rect, width = 10.0, align = "inside")
        assertTrue(contains(g, 2.0, 50.0), "inside band is within the fill")
        assertFalse(contains(g, -2.0, 50.0), "inside band never crosses outside the fill")
    }

    @Test
    fun outsideAlignClipsOutsideFill() {
        val rect = roundedRectGeometry(RectD(0.0, 0.0, 100.0, 100.0), 0.0, 0.0, 0.0, 0.0)
        val g = strokeOutline(rect, width = 10.0, align = "outside")
        assertTrue(contains(g, -2.0, 50.0), "outside band is outside the fill")
        assertFalse(contains(g, 2.0, 50.0), "outside band never enters the fill")
    }
}
