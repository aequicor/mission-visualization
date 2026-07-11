package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathBooleanTest {

    private fun rect(x: Double, y: Double, w: Double, h: Double): PathGeometry =
        PathGeometry(
            listOf(
                PathCommand.MoveTo(x, y),
                PathCommand.LineTo(x + w, y),
                PathCommand.LineTo(x + w, y + h),
                PathCommand.LineTo(x, y + h),
                PathCommand.Close,
            ),
        )

    // A at (0,0,100,100); B at (50,0,100,100) -> overlap is x in [50,100].
    private val a = rect(0.0, 0.0, 100.0, 100.0)
    private val b = rect(50.0, 0.0, 100.0, 100.0)

    private fun inResult(g: PathGeometry, x: Double, y: Double) = contains(g, x, y)

    @Test
    fun union() {
        val u = pathBoolean(a, b, PathBooleanOp.Union)
        assertTrue(inResult(u, 25.0, 50.0), "A-only")
        assertTrue(inResult(u, 75.0, 50.0), "overlap")
        assertTrue(inResult(u, 125.0, 50.0), "B-only")
        assertFalse(inResult(u, 200.0, 50.0), "outside")
    }

    @Test
    fun subtract() {
        val s = pathBoolean(a, b, PathBooleanOp.Subtract)
        assertTrue(inResult(s, 25.0, 50.0), "A-only kept")
        assertFalse(inResult(s, 75.0, 50.0), "overlap removed")
        assertFalse(inResult(s, 125.0, 50.0), "B-only never in A")
    }

    @Test
    fun intersect() {
        val x = pathBoolean(a, b, PathBooleanOp.Intersect)
        assertTrue(inResult(x, 75.0, 50.0), "overlap in")
        assertFalse(inResult(x, 25.0, 50.0), "A-only out")
        assertFalse(inResult(x, 125.0, 50.0), "B-only out")
    }

    @Test
    fun exclude() {
        val e = pathBoolean(a, b, PathBooleanOp.Exclude)
        assertTrue(inResult(e, 25.0, 50.0), "A-only in")
        assertTrue(inResult(e, 125.0, 50.0), "B-only in")
        assertFalse(inResult(e, 75.0, 50.0), "overlap out")
    }

    @Test
    fun sharedEdgeUnionHasNoInteriorGap() {
        // B touches A's right edge exactly (x=100).
        val u = pathBoolean(a, rect(100.0, 0.0, 100.0, 100.0), PathBooleanOp.Union)
        assertTrue(inResult(u, 50.0, 50.0))
        assertTrue(inResult(u, 100.0, 50.0), "the shared seam is interior, still filled")
        assertTrue(inResult(u, 150.0, 50.0))
        assertFalse(inResult(u, 250.0, 50.0))
    }

    @Test
    fun containmentSubtractYieldsHole() {
        val outer = rect(0.0, 0.0, 100.0, 100.0)
        val hole = rect(40.0, 40.0, 20.0, 20.0)
        val ring = pathBoolean(outer, hole, PathBooleanOp.Subtract)
        assertTrue(inResult(ring, 10.0, 10.0), "ring band inside")
        assertFalse(inResult(ring, 50.0, 50.0), "hole center empty")
    }

    @Test
    fun containmentUnionIsOuter() {
        val outer = rect(0.0, 0.0, 100.0, 100.0)
        val inner = rect(40.0, 40.0, 20.0, 20.0)
        val u = pathBoolean(outer, inner, PathBooleanOp.Union)
        assertTrue(inResult(u, 50.0, 50.0))
        assertTrue(inResult(u, 10.0, 10.0))
        assertFalse(inResult(u, 150.0, 50.0))
    }

    @Test
    fun bowtieSelfUnionDoesNotCrashAndFills() {
        // Figure-8 / bowtie: a single self-intersecting loop.
        val bowtie = PathGeometry(
            listOf(
                PathCommand.MoveTo(0.0, 0.0),
                PathCommand.LineTo(100.0, 100.0),
                PathCommand.LineTo(0.0, 100.0),
                PathCommand.LineTo(100.0, 0.0),
                PathCommand.Close,
            ),
        )
        val n = pathBooleanFold(listOf(bowtie), PathBooleanOp.Union)
        // Each triangular lobe interior is filled; the crossing center is on the boundary.
        assertTrue(inResult(n, 50.0, 20.0) || inResult(n, 20.0, 50.0), "a lobe is filled")
    }

    @Test
    fun evenOddOperandHandled() {
        // A ring (outer 100 with an even-odd 40..60 hole) unioned with a small rect in the hole.
        val ring = PathGeometry(
            rect(0.0, 0.0, 100.0, 100.0).commands + rect(40.0, 40.0, 20.0, 20.0).commands,
            PathFillRule.EvenOdd,
        )
        assertFalse(contains(ring, 50.0, 50.0), "hole is empty in the operand")
        val filled = pathBoolean(ring, rect(45.0, 45.0, 10.0, 10.0), PathBooleanOp.Union)
        assertTrue(inResult(filled, 10.0, 10.0), "band still filled")
        assertTrue(inResult(filled, 50.0, 50.0), "small rect fills part of the hole")
    }

    @Test
    fun disjointIntersectIsEmpty() {
        val far = rect(500.0, 500.0, 10.0, 10.0)
        val x = pathBoolean(a, far, PathBooleanOp.Intersect)
        assertTrue(x.commands.isEmpty(), "disjoint intersect is empty")
    }

    @Test
    fun foldUnionOfThree() {
        val u = pathBooleanFold(listOf(rect(0.0, 0.0, 30.0, 30.0), rect(20.0, 0.0, 30.0, 30.0), rect(40.0, 0.0, 30.0, 30.0)), PathBooleanOp.Union)
        assertTrue(inResult(u, 5.0, 15.0))
        assertTrue(inResult(u, 35.0, 15.0))
        assertTrue(inResult(u, 65.0, 15.0))
        assertFalse(inResult(u, 100.0, 15.0))
    }
}
