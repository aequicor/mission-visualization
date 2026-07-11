package io.aequicor.visualization.subsystems.figures

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Degenerate / edge-case hardening for [pathBoolean] and [pathBooleanFold]: empty operands,
 * disjoint and identical operands, concave and many-vertex polygons, self-touching (pinched)
 * loops, plus a coarse termination smoke test on realistic sizes. Every assertion is either a
 * "does not crash" guard or a sane point-membership check — no timing.
 */
class PathBooleanEdgeCaseTest {

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

    private val empty = PathGeometry(emptyList())

    /** Regular n-gon polyline geometry centred at (cx,cy). */
    private fun ngon(cx: Double, cy: Double, r: Double, n: Int): PathGeometry {
        val cmds = ArrayList<PathCommand>(n + 2)
        for (i in 0 until n) {
            val a = 2.0 * PI * i / n
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            cmds += if (i == 0) PathCommand.MoveTo(x, y) else PathCommand.LineTo(x, y)
        }
        cmds += PathCommand.Close
        return PathGeometry(cmds)
    }

    private fun moveCount(g: PathGeometry) = g.commands.count { it is PathCommand.MoveTo }

    @Test
    fun emptyOperandLeavesAUnchanged() {
        val a = rect(0.0, 0.0, 100.0, 100.0)
        val u = pathBoolean(a, empty, PathBooleanOp.Union)
        assertTrue(contains(u, 50.0, 50.0), "A interior preserved")
        assertFalse(contains(u, 150.0, 50.0), "still nothing outside A")
        // Subtract / Intersect with empty behave as set-algebra with the empty set.
        assertTrue(contains(pathBoolean(a, empty, PathBooleanOp.Subtract), 50.0, 50.0), "A - {} == A")
        assertTrue(pathBoolean(a, empty, PathBooleanOp.Intersect).commands.isEmpty(), "A ∩ {} == {}")
    }

    @Test
    fun bothOperandsEmptyIsEmpty() {
        assertTrue(pathBoolean(empty, empty, PathBooleanOp.Union).commands.isEmpty())
        assertTrue(pathBooleanFold(emptyList(), PathBooleanOp.Union).commands.isEmpty())
    }

    @Test
    fun disjointUnionKeepsBothLoops() {
        val left = rect(0.0, 0.0, 20.0, 20.0)
        val right = rect(50.0, 0.0, 20.0, 20.0)
        val u = pathBoolean(left, right, PathBooleanOp.Union)
        assertTrue(contains(u, 10.0, 10.0), "left loop present")
        assertTrue(contains(u, 60.0, 10.0), "right loop present")
        assertFalse(contains(u, 35.0, 10.0), "gap between them is empty")
        assertEquals(2, moveCount(u), "two disjoint loops")
    }

    @Test
    fun identicalOperands() {
        val a = rect(0.0, 0.0, 100.0, 100.0)
        val union = pathBoolean(a, a, PathBooleanOp.Union)
        assertTrue(contains(union, 50.0, 50.0), "A ∪ A == A (inside)")
        assertFalse(contains(union, 150.0, 50.0), "A ∪ A == A (outside)")

        assertTrue(pathBoolean(a, a, PathBooleanOp.Subtract).commands.isEmpty(), "A - A == {}")

        val inter = pathBoolean(a, a, PathBooleanOp.Intersect)
        assertTrue(contains(inter, 50.0, 50.0), "A ∩ A == A (inside)")
        assertFalse(contains(inter, 150.0, 50.0), "A ∩ A == A (outside)")

        assertTrue(pathBoolean(a, a, PathBooleanOp.Exclude).commands.isEmpty(), "A ⊕ A == {}")
    }

    /** An L-shaped (concave) polygon: interior in the arms, empty in the notch. */
    private fun lShape(): PathGeometry = PathGeometry(
        listOf(
            PathCommand.MoveTo(0.0, 0.0),
            PathCommand.LineTo(60.0, 0.0),
            PathCommand.LineTo(60.0, 20.0),
            PathCommand.LineTo(20.0, 20.0),
            PathCommand.LineTo(20.0, 60.0),
            PathCommand.LineTo(0.0, 60.0),
            PathCommand.Close,
        ),
    )

    @Test
    fun concavePolygonNormalizesSanely() {
        val n = pathBooleanFold(listOf(lShape()), PathBooleanOp.Union)
        assertTrue(contains(n, 10.0, 10.0), "vertical arm filled")
        assertTrue(contains(n, 50.0, 10.0), "horizontal arm filled")
        assertFalse(contains(n, 40.0, 40.0), "concave notch stays empty")
    }

    @Test
    fun concaveIntersectRect() {
        // Clip the L to its lower-left quadrant.
        val x = pathBoolean(lShape(), rect(0.0, 0.0, 30.0, 30.0), PathBooleanOp.Intersect)
        assertTrue(contains(x, 10.0, 10.0), "kept corner filled")
        assertFalse(contains(x, 50.0, 10.0), "right arm clipped away")
        assertFalse(contains(x, 40.0, 40.0), "notch still empty")
    }

    @Test
    fun manyVertexPolygonIntersectRect() {
        // 60-gon (radius 40 at (50,50)) intersected with the left half-plane rect x in [0,50].
        val disk = ngon(50.0, 50.0, 40.0, 60)
        val x = pathBoolean(disk, rect(0.0, 0.0, 50.0, 100.0), PathBooleanOp.Intersect)
        assertFalse(x.commands.isEmpty(), "non-empty intersection")
        assertTrue(contains(x, 30.0, 50.0), "left of centre, inside disk")
        assertFalse(contains(x, 70.0, 50.0), "right half clipped away")
        assertFalse(contains(x, 5.0, 50.0), "outside the disk radius")
    }

    @Test
    fun selfTouchingPinchedPolygonDoesNotCrash() {
        // Two lobes that meet at a single shared point (40,40) rather than crossing.
        val pinched = PathGeometry(
            listOf(
                PathCommand.MoveTo(0.0, 0.0),
                PathCommand.LineTo(40.0, 40.0),
                PathCommand.LineTo(80.0, 0.0),
                PathCommand.LineTo(80.0, 80.0),
                PathCommand.LineTo(40.0, 40.0),
                PathCommand.LineTo(0.0, 80.0),
                PathCommand.Close,
            ),
        )
        val n = pathBooleanFold(listOf(pinched), PathBooleanOp.Union)
        // Primary guard: the pinch must not throw and must produce well-formed geometry.
        // (Winding through the shared point makes per-lobe fill ambiguous, so we don't pin it;
        // but well outside every lobe stays empty, and hit-testing does not crash.)
        assertFalse(contains(n, 300.0, 300.0), "far outside stays empty")
    }

    @Test
    fun perfSmokeFoldOfEightOverlappingRectsFlattens() {
        // Eight rects marching diagonally, each overlapping its neighbour.
        val rects = (0 until 8).map { i -> rect(i * 10.0, i * 5.0, 30.0, 30.0) }
        val fold = pathBooleanFold(rects, PathBooleanOp.Union)
        assertFalse(fold.commands.isEmpty(), "union terminates with geometry")
        val polys = flattenToPolygons(fold)
        assertTrue(polys.isNotEmpty() && polys.any { it.size >= 3 }, "flatten yields a real ring")
        assertTrue(contains(fold, 15.0, 15.0), "first rect region covered")
    }

    @Test
    fun perfSmokeOutline60Gon() {
        val outline = strokeOutline(ngon(50.0, 50.0, 40.0, 60), width = 6.0, cap = "butt")
        assertFalse(outline.commands.isEmpty(), "60-gon stroke outline terminates non-empty")
    }
}
