package io.aequicor.visualization.subsystems.figures

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathFitTest {

    private fun polygonGeometry(points: List<PointD>, closed: Boolean): PathGeometry {
        val cmds = ArrayList<PathCommand>()
        cmds += PathCommand.MoveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) cmds += PathCommand.LineTo(points[i].x, points[i].y)
        if (closed) cmds += PathCommand.Close
        return PathGeometry(cmds)
    }

    private fun cubicCount(g: PathGeometry): Int = g.commands.count { it is PathCommand.CubicTo }

    @Test
    fun flattenedCircleRefitsToFewCurvesWithinTolerance() {
        val rect = RectD(0.0, 0.0, 200.0, 200.0)
        // ellipse -> dense polygon of LineTos (simulate boolean/outline flattened output).
        val polygon = flattenToPolygons(ellipseGeometry(rect), tolerance = 0.25).first()
        val flattened = polygonGeometry(polygon, closed = true)
        assertEquals(0, cubicCount(flattened), "precondition: input is a pure polyline")

        val refit = flattened.refitCurves(tolerance = 0.8)
        val curves = cubicCount(refit)
        assertTrue(curves in 1..12, "circle should refit to few cubics, got $curves")
        assertTrue(refit.isClosed, "closed circle stays closed")

        // Sample points on the ideal circle; refit outline must pass close to each.
        val cx = 100.0
        val cy = 100.0
        val r = 100.0
        for (k in 0 until 32) {
            val a = (k / 32.0) * 2.0 * kotlin.math.PI
            val px = cx + r * cos(a)
            val py = cy + r * sin(a)
            val d = distanceToOutline(refit, px, py)
            assertTrue(d <= 1.5, "sample $k off outline by $d")
        }
    }

    @Test
    fun rectangleEdgesRefitToLinesNotCurves() {
        val corners = listOf(
            PointD(10.0, 20.0),
            PointD(110.0, 20.0),
            PointD(110.0, 80.0),
            PointD(10.0, 80.0),
        )
        // Densify each edge into many collinear points (mimicking a flattened stroke outline).
        val dense = ArrayList<PointD>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val steps = 10
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                dense += PointD(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        val flattened = polygonGeometry(dense, closed = true)
        val refit = flattened.refitCurves(tolerance = 0.8)

        assertEquals(0, cubicCount(refit), "straight rectangle edges must not become curves")
        assertTrue(refit.isClosed, "rectangle stays closed")
        // Every rectangle corner must appear among the emitted vertices.
        val verts = refit.commands.mapNotNull {
            when (it) {
                is PathCommand.MoveTo -> PointD(it.x, it.y)
                is PathCommand.LineTo -> PointD(it.x, it.y)
                else -> null
            }
        }
        for (corner in corners) {
            assertTrue(
                verts.any { kotlin.math.abs(it.x - corner.x) < 0.5 && kotlin.math.abs(it.y - corner.y) < 0.5 },
                "corner $corner missing from refit vertices $verts",
            )
        }
    }

    @Test
    fun closedTriangleStaysClosedTriangleWithoutCurves() {
        val tri = listOf(
            PointD(0.0, 0.0),
            PointD(100.0, 0.0),
            PointD(50.0, 80.0),
        )
        val refit = polygonGeometry(tri, closed = true).refitCurves(tolerance = 0.8)
        assertEquals(0, cubicCount(refit), "triangle edges are straight")
        assertTrue(refit.isClosed, "closed triangle stays closed")
        // Interior point is inside, far outside point is not — shape preserved.
        assertTrue(contains(refit, 50.0, 20.0), "centroid-ish point inside")
        assertTrue(!contains(refit, 200.0, 200.0), "far point outside")
    }

    @Test
    fun openSubpathStaysOpen() {
        val poly = listOf(
            PointD(0.0, 0.0),
            PointD(50.0, 40.0),
            PointD(120.0, 0.0),
        )
        val refit = polygonGeometry(poly, closed = false).refitCurves(tolerance = 0.8)
        assertTrue(!refit.isClosed, "open subpath must not gain a Close")
    }

    @Test
    fun smallSubpathPassesThroughUnchanged() {
        val g = polygonGeometry(listOf(PointD(0.0, 0.0), PointD(10.0, 10.0)), closed = false)
        val refit = g.refitCurves()
        assertEquals(0, cubicCount(refit), "two-point run stays a line")
        assertTrue(!refit.isClosed)
    }

    @Test
    fun booleanFoldOfTwoEllipsesRefitsWithCurves() {
        // Mirrors what the reducer's Flatten does: fold ellipses, then refit before serializing.
        val a = ellipseGeometry(RectD(0.0, 0.0, 100.0, 100.0))
        val b = ellipseGeometry(RectD(50.0, 0.0, 150.0, 100.0))
        val folded = pathBooleanFold(listOf(a, b), PathBooleanOp.Union)
        assertEquals(0, folded.commands.count { it is PathCommand.CubicTo }, "boolean output is polyline")
        val refit = folded.refitCurves()
        assertTrue(refit.commands.any { it is PathCommand.CubicTo }, "refit union should carry curves")
        assertTrue(refit.toSvgPathData().contains("C"), "d-string should contain a cubic command")
    }
}
