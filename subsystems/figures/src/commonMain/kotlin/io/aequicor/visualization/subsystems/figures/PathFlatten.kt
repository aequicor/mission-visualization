package io.aequicor.visualization.subsystems.figures

import kotlin.math.abs

/** A 2D point in `Double` coordinates. */
data class PointD(val x: Double, val y: Double)

/** A flattened subpath: its polyline points and whether it was authored closed (had `Z`). */
data class Subpath(val points: List<PointD>, val closed: Boolean)

/**
 * Adaptive de Casteljau flattening of a [PathGeometry] into subpaths, preserving open/closed. Each
 * cubic/quad subdivides until the control points are within [tolerance] of the chord (depth-capped).
 */
fun flattenSubpaths(geometry: PathGeometry, tolerance: Double = 0.25): List<Subpath> {
    data class Acc(val points: MutableList<PointD>, var closed: Boolean)
    val subs = ArrayList<Acc>()
    var current: Acc? = null
    var startX = 0.0
    var startY = 0.0
    var curX = 0.0
    var curY = 0.0

    fun moveTo(x: Double, y: Double) {
        current = Acc(ArrayList<PointD>().apply { add(PointD(x, y)) }, false).also { subs += it }
        startX = x; startY = y; curX = x; curY = y
    }
    fun lineTo(x: Double, y: Double) {
        (current ?: return).points.add(PointD(x, y)); curX = x; curY = y
    }

    for (command in geometry.commands) {
        when (command) {
            is PathCommand.MoveTo -> moveTo(command.x, command.y)
            is PathCommand.LineTo -> lineTo(command.x, command.y)
            is PathCommand.QuadTo ->
                flattenQuad(curX, curY, command.cx, command.cy, command.x, command.y, tolerance) { x, y -> lineTo(x, y) }
            is PathCommand.CubicTo ->
                flattenCubic(curX, curY, command.c1x, command.c1y, command.c2x, command.c2y, command.x, command.y, tolerance) { x, y -> lineTo(x, y) }
            PathCommand.Close -> {
                current?.closed = true
                curX = startX; curY = startY
            }
        }
    }
    return subs.map { acc ->
        val pts = if (acc.points.size > 1 &&
            abs(acc.points.first().x - acc.points.last().x) < 1e-9 &&
            abs(acc.points.first().y - acc.points.last().y) < 1e-9
        ) {
            acc.points.dropLast(1)
        } else {
            acc.points
        }
        Subpath(pts, acc.closed)
    }.filter { it.points.size >= 2 }
}

/** Flattens into closed polygon rings (subpaths treated as closed). Used by fill-area operations. */
fun flattenToPolygons(geometry: PathGeometry, tolerance: Double = 0.25): List<List<PointD>> =
    flattenSubpaths(geometry, tolerance).map { it.points }

private fun flattenCubic(
    x0: Double, y0: Double, c1x: Double, c1y: Double, c2x: Double, c2y: Double, x1: Double, y1: Double,
    tolerance: Double, depth: Int = 0, emit: (Double, Double) -> Unit,
) {
    // Flatness: max distance of control points from the chord.
    val d1 = pointLineDistance(c1x, c1y, x0, y0, x1, y1)
    val d2 = pointLineDistance(c2x, c2y, x0, y0, x1, y1)
    if (depth >= 18 || (d1 + d2) <= tolerance) {
        emit(x1, y1)
        return
    }
    // Subdivide at t = 0.5.
    val ax = (x0 + c1x) / 2; val ay = (y0 + c1y) / 2
    val bx = (c1x + c2x) / 2; val by = (c1y + c2y) / 2
    val cx = (c2x + x1) / 2; val cy = (c2y + y1) / 2
    val dx = (ax + bx) / 2; val dy = (ay + by) / 2
    val ex = (bx + cx) / 2; val ey = (by + cy) / 2
    val mx = (dx + ex) / 2; val my = (dy + ey) / 2
    flattenCubic(x0, y0, ax, ay, dx, dy, mx, my, tolerance, depth + 1, emit)
    flattenCubic(mx, my, ex, ey, cx, cy, x1, y1, tolerance, depth + 1, emit)
}

private fun flattenQuad(
    x0: Double, y0: Double, cx: Double, cy: Double, x1: Double, y1: Double,
    tolerance: Double, depth: Int = 0, emit: (Double, Double) -> Unit,
) {
    val d = pointLineDistance(cx, cy, x0, y0, x1, y1)
    if (depth >= 18 || d <= tolerance) {
        emit(x1, y1)
        return
    }
    val ax = (x0 + cx) / 2; val ay = (y0 + cy) / 2
    val bx = (cx + x1) / 2; val by = (cy + y1) / 2
    val mx = (ax + bx) / 2; val my = (ay + by) / 2
    flattenQuad(x0, y0, ax, ay, mx, my, tolerance, depth + 1, emit)
    flattenQuad(mx, my, bx, by, x1, y1, tolerance, depth + 1, emit)
}

private fun pointLineDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len < 1e-12) return kotlin.math.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
    return abs(dx * (ay - py) - dy * (ax - px)) / len
}
