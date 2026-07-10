package io.aequicor.visualization.subsystems.figures

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Converts a stroked path into the filled outline of its stroke. The stroke band is assembled from
 * convex pieces — an offset quad per flattened segment, a join piece per interior vertex, and cap
 * pieces at open ends — all oriented the same way and then run once through the boolean engine
 * ([pathBoolean] self-union) to produce a single clean [PathFillRule.NonZero] outline (no seams, no
 * bloat). Output is polyline geometry (curves are flattened; accepted v1 tradeoff).
 *
 * - [cap] (open ends): "butt" (flush), "square" (extended by half-width), "round" (disc).
 * - [join]: "miter" (extends to the apex; falls back to "bevel" past [miterLimit]), "bevel", "round".
 * - [align]: "center" (±half-width), "inside"/"outside" clip the band to/against the shape's fill
 *   area (a no-op for open shapes, which have no fill region).
 */
fun strokeOutline(
    geometry: PathGeometry,
    width: Double,
    cap: String = "butt",
    join: String = "miter",
    miterLimit: Double = 4.0,
    align: String = "center",
    tolerance: Double = 0.25,
): PathGeometry {
    val h = width / 2.0
    if (h <= 0.0) return PathGeometry(emptyList())
    val pieces = ArrayList<List<PointD>>()

    for (sub in flattenSubpaths(geometry, tolerance)) {
        val open = !sub.closed
        val pts = if (!open && sub.points.size > 2) sub.points + sub.points.first() else sub.points
        if (pts.size < 2) continue

        for (i in 0 until pts.size - 1) {
            var a = pts[i]
            var b = pts[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            if (len < 1e-9) continue
            val ux = dx / len
            val uy = dy / len
            if (open && cap == "square") {
                if (i == 0) a = PointD(a.x - ux * h, a.y - uy * h)
                if (i == pts.size - 2) b = PointD(b.x + ux * h, b.y + uy * h)
            }
            val nx = -uy * h
            val ny = ux * h
            pieces += ccw(listOf(PointD(a.x + nx, a.y + ny), PointD(b.x + nx, b.y + ny), PointD(b.x - nx, b.y - ny), PointD(a.x - nx, a.y - ny)))
        }

        // Interior joins (and, for closed subpaths, the wrap-around vertex).
        val jointCount = if (open) pts.size - 2 else pts.size - 1
        for (k in 0 until jointCount) {
            val prev = pts[k]
            val v = pts[k + 1]
            val next = pts[(k + 2) % pts.size].let { if (!open && k + 2 == pts.size) pts[1] else it }
            joinPiece(prev, v, next, h, join, miterLimit)?.let { pieces += it }
        }

        if (open && cap == "round") {
            pieces += disc(sub.points.first(), h)
            pieces += disc(sub.points.last(), h)
        }
    }

    if (pieces.isEmpty()) return PathGeometry(emptyList())
    val band = pathBoolean(loopsToGeometry(pieces), PathGeometry(emptyList()), PathBooleanOp.Union, tolerance)

    return when (align) {
        "inside" -> if (geometry.isClosed) pathBoolean(band, geometry, PathBooleanOp.Intersect, tolerance) else band
        "outside" -> if (geometry.isClosed) pathBoolean(band, geometry, PathBooleanOp.Subtract, tolerance) else band
        else -> band
    }
}

/** The outer wedge filler at vertex [v] between segments prev→v and v→next; null if straight. */
private fun joinPiece(prev: PointD, v: PointD, next: PointD, h: Double, join: String, miterLimit: Double): List<PointD>? {
    val d0x = v.x - prev.x; val d0y = v.y - prev.y
    val d1x = next.x - v.x; val d1y = next.y - v.y
    val l0 = hypot(d0x, d0y); val l1 = hypot(d1x, d1y)
    if (l0 < 1e-9 || l1 < 1e-9) return null
    val u0x = d0x / l0; val u0y = d0y / l0
    val u1x = d1x / l1; val u1y = d1y / l1
    val cross = u0x * u1y - u0y * u1x
    if (abs(cross) < 1e-9) return null // collinear: no join gap

    // Outer side of the turn: left normal (+) on a right turn (cross<0), right normal (-) otherwise.
    val outer = if (cross < 0.0) 1.0 else -1.0
    val n0x = -u0y * h * outer; val n0y = u0x * h * outer
    val n1x = -u1y * h * outer; val n1y = u1x * h * outer
    val o0 = PointD(v.x + n0x, v.y + n0y)
    val o1 = PointD(v.x + n1x, v.y + n1y)

    return when (join) {
        "round" -> disc(v, h)
        "miter" -> {
            val m = lineIntersect(o0, u0x, u0y, o1, u1x, u1y)
            if (m != null && hypot(m.x - v.x, m.y - v.y) / h <= miterLimit) {
                ccw(listOf(v, o0, m, o1))
            } else {
                ccw(listOf(v, o0, o1))
            }
        }
        else -> ccw(listOf(v, o0, o1)) // bevel
    }
}

/** Intersection of line through [p] with direction (dx0,dy0) and line through [q] with (dx1,dy1). */
private fun lineIntersect(p: PointD, dx0: Double, dy0: Double, q: PointD, dx1: Double, dy1: Double): PointD? {
    val denom = dx0 * dy1 - dy0 * dx1
    if (abs(denom) < 1e-12) return null
    val t = ((q.x - p.x) * dy1 - (q.y - p.y) * dx1) / denom
    return PointD(p.x + dx0 * t, p.y + dy0 * t)
}

private fun disc(c: PointD, r: Double): List<PointD> {
    val n = 32
    val out = ArrayList<PointD>(n)
    for (i in 0 until n) {
        val a = 2.0 * PI * i / n
        out += PointD(c.x + r * cos(a), c.y + r * sin(a))
    }
    return out // already CCW
}

/** Orients a polygon counter-clockwise (positive signed area) for consistent nonzero winding. */
private fun ccw(points: List<PointD>): List<PointD> {
    var area = 0.0
    for (i in points.indices) {
        val a = points[i]; val b = points[(i + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return if (area < 0.0) points.asReversed() else points
}

private fun loopsToGeometry(loops: List<List<PointD>>): PathGeometry {
    val commands = ArrayList<PathCommand>()
    for (loop in loops) {
        if (loop.size < 3) continue
        commands += PathCommand.MoveTo(loop[0].x, loop[0].y)
        for (i in 1 until loop.size) commands += PathCommand.LineTo(loop[i].x, loop[i].y)
        commands += PathCommand.Close
    }
    return PathGeometry(commands, PathFillRule.NonZero)
}
