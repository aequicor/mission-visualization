package io.aequicor.visualization.engine.ir.geometry

import kotlin.math.sqrt

/**
 * Pure, Compose-free hit-testing over [PathGeometry]. Curves are flattened to polylines,
 * then closed shapes use a ray-cast (respecting [PathGeometry.fillRule]) and open shapes
 * (lines/arrows) use distance-to-outline. Query points are in the geometry's own coordinate
 * space; callers with a view-box geometry map the point through [meetFit]`.inverse()` first.
 */

private const val CURVE_SUBDIVISIONS = 16

private class Poly(val xs: DoubleArray, val ys: DoubleArray)

/** Flattens the command list into one polyline per sub-path (each MoveTo starts a new one). */
private fun PathGeometry.flatten(): List<Poly> {
    val polys = ArrayList<Poly>()
    var xs = ArrayList<Double>()
    var ys = ArrayList<Double>()
    var curX = 0.0
    var curY = 0.0
    var startX = 0.0
    var startY = 0.0

    fun push() {
        if (xs.size >= 2) polys += Poly(xs.toDoubleArray(), ys.toDoubleArray())
        xs = ArrayList()
        ys = ArrayList()
    }
    fun add(x: Double, y: Double) {
        xs += x; ys += y; curX = x; curY = y
    }

    commands.forEach { c ->
        when (c) {
            is PathCommand.MoveTo -> {
                push()
                startX = c.x; startY = c.y
                add(c.x, c.y)
            }
            is PathCommand.LineTo -> add(c.x, c.y)
            is PathCommand.QuadTo -> {
                val x0 = curX; val y0 = curY
                for (i in 1..CURVE_SUBDIVISIONS) {
                    val t = i.toDouble() / CURVE_SUBDIVISIONS
                    val mt = 1 - t
                    val x = mt * mt * x0 + 2 * mt * t * c.cx + t * t * c.x
                    val y = mt * mt * y0 + 2 * mt * t * c.cy + t * t * c.y
                    add(x, y)
                }
            }
            is PathCommand.CubicTo -> {
                val x0 = curX; val y0 = curY
                for (i in 1..CURVE_SUBDIVISIONS) {
                    val t = i.toDouble() / CURVE_SUBDIVISIONS
                    val mt = 1 - t
                    val x = mt * mt * mt * x0 + 3 * mt * mt * t * c.c1x + 3 * mt * t * t * c.c2x + t * t * t * c.x
                    val y = mt * mt * mt * y0 + 3 * mt * mt * t * c.c1y + 3 * mt * t * t * c.c2y + t * t * t * c.y
                    add(x, y)
                }
            }
            PathCommand.Close -> {
                add(startX, startY)
                push()
            }
        }
    }
    push()
    return polys
}

/** True when the point is inside the (auto-closed) geometry per its fill rule. */
fun contains(geometry: PathGeometry, px: Double, py: Double): Boolean {
    val polys = geometry.flatten()
    return when (geometry.fillRule) {
        PathFillRule.EvenOdd -> evenOddInside(polys, px, py)
        PathFillRule.NonZero -> windingNumber(polys, px, py) != 0
    }
}

private fun evenOddInside(polys: List<Poly>, px: Double, py: Double): Boolean {
    var inside = false
    polys.forEach { poly ->
        val n = poly.xs.size
        var j = n - 1
        for (i in 0 until n) {
            val yi = poly.ys[i]
            val yj = poly.ys[j]
            if ((yi > py) != (yj > py)) {
                val xint = poly.xs[i] + (py - yi) / (yj - yi) * (poly.xs[j] - poly.xs[i])
                if (px < xint) inside = !inside
            }
            j = i
        }
    }
    return inside
}

private fun windingNumber(polys: List<Poly>, px: Double, py: Double): Int {
    var wn = 0
    polys.forEach { poly ->
        val n = poly.xs.size
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly.xs[i]; val yi = poly.ys[i]
            val xj = poly.xs[j]; val yj = poly.ys[j]
            // isLeft of edge j->i relative to the query point
            val isLeft = (xi - xj) * (py - yj) - (px - xj) * (yi - yj)
            if (yj <= py) {
                if (yi > py && isLeft > 0) wn++
            } else {
                if (yi <= py && isLeft < 0) wn--
            }
            j = i
        }
    }
    return wn
}

/** Minimum distance from the point to any (open) segment of the flattened outline. */
fun distanceToOutline(geometry: PathGeometry, px: Double, py: Double): Double {
    var best = Double.POSITIVE_INFINITY
    geometry.flatten().forEach { poly ->
        for (i in 0 until poly.xs.size - 1) {
            val d = pointSegmentDistance(px, py, poly.xs[i], poly.ys[i], poly.xs[i + 1], poly.ys[i + 1])
            if (d < best) best = d
        }
    }
    return best
}

private fun pointSegmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lenSq = dx * dx + dy * dy
    val t = if (lenSq == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
    val cx = ax + t * dx
    val cy = ay + t * dy
    val ex = px - cx
    val ey = py - cy
    return sqrt(ex * ex + ey * ey)
}
