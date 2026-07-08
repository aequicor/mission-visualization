package io.aequicor.visualization.engine.ir.geometry

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure builders for parametric primitive outlines, in absolute document coordinates
 * (so [PathGeometry.sourceViewBox] is null). These replace the private Compose-coupled
 * builders that used to live in the renderer, making the vertex math headless-testable
 * and reusable by hit-testing.
 */

private const val PI = 3.141592653589793
private const val DEG_TO_RAD = PI / 180.0

/** Bezier circle constant: control-handle length for a 90° arc = kappa · radius. */
private const val KAPPA = 0.5522847498307936

/** Regular N-gon inscribed in [rect]; first vertex at 12 o'clock, clockwise. */
fun regularPolygonGeometry(rect: RectD, points: Int): PathGeometry {
    val count = max(points, 3)
    val cx = rect.centerX
    val cy = rect.centerY
    val rx = rect.width / 2.0
    val ry = rect.height / 2.0
    val commands = ArrayList<PathCommand>(count + 2)
    for (i in 0 until count) {
        val radians = (-90.0 + 360.0 * i / count) * DEG_TO_RAD
        val x = cx + rx * cos(radians)
        val y = cy + ry * sin(radians)
        commands += if (i == 0) PathCommand.MoveTo(x, y) else PathCommand.LineTo(x, y)
    }
    commands += PathCommand.Close
    return PathGeometry(commands)
}

/** N-pointed star inscribed in [rect]; [innerRatio] is the valley radius as a fraction of the outer. */
fun starGeometry(rect: RectD, points: Int, innerRatio: Double): PathGeometry {
    val count = max(points, 3)
    val cx = rect.centerX
    val cy = rect.centerY
    val rx = rect.width / 2.0
    val ry = rect.height / 2.0
    val inner = innerRatio.coerceIn(0.05, 1.0)
    val commands = ArrayList<PathCommand>(count * 2 + 2)
    for (i in 0 until count * 2) {
        val radians = (-90.0 + 180.0 * i / count) * DEG_TO_RAD
        val scale = if (i % 2 == 0) 1.0 else inner
        val x = cx + rx * scale * cos(radians)
        val y = cy + ry * scale * sin(radians)
        commands += if (i == 0) PathCommand.MoveTo(x, y) else PathCommand.LineTo(x, y)
    }
    commands += PathCommand.Close
    return PathGeometry(commands)
}

/** Horizontal centerline across [rect]. Open (no close) — meant to be stroked. */
fun lineGeometry(rect: RectD): PathGeometry =
    PathGeometry(
        listOf(
            PathCommand.MoveTo(rect.left, rect.centerY),
            PathCommand.LineTo(rect.right, rect.centerY),
        ),
    )

/**
 * A left-to-right arrow: a shaft plus a chevron head at the right end. Open (stroked).
 * Head size scales with [strokeWeight] so thin and thick arrows both read correctly,
 * clamped so the head never exceeds half the shaft length.
 */
fun arrowGeometry(rect: RectD, strokeWeight: Double): PathGeometry {
    val cy = rect.centerY
    val headLen = min(max(3.0 * strokeWeight, 6.0), rect.width / 2.0)
    val headHalf = headLen * 0.6
    val tipX = rect.right
    val baseX = rect.right - headLen
    return PathGeometry(
        listOf(
            // shaft
            PathCommand.MoveTo(rect.left, cy),
            PathCommand.LineTo(baseX, cy),
            // chevron head
            PathCommand.MoveTo(baseX, cy - headHalf),
            PathCommand.LineTo(tipX, cy),
            PathCommand.LineTo(baseX, cy + headHalf),
        ),
    )
}

/** Ellipse inscribed in [rect], as four cubic arcs. Closed. */
fun ellipseGeometry(rect: RectD): PathGeometry {
    val cx = rect.centerX
    val cy = rect.centerY
    val rx = rect.width / 2.0
    val ry = rect.height / 2.0
    val ox = rx * KAPPA
    val oy = ry * KAPPA
    return PathGeometry(
        listOf(
            PathCommand.MoveTo(cx - rx, cy),
            PathCommand.CubicTo(cx - rx, cy - oy, cx - ox, cy - ry, cx, cy - ry),
            PathCommand.CubicTo(cx + ox, cy - ry, cx + rx, cy - oy, cx + rx, cy),
            PathCommand.CubicTo(cx + rx, cy + oy, cx + ox, cy + ry, cx, cy + ry),
            PathCommand.CubicTo(cx - ox, cy + ry, cx - rx, cy + oy, cx - rx, cy),
            PathCommand.Close,
        ),
    )
}

/**
 * Rounded rectangle with per-corner radii. [inset] shrinks both the edges (via the passed
 * [rect]) and the radii so a stroke centerline stays concentric with the fill. Corners with
 * radius ≤ 0 collapse to sharp corners. Closed.
 */
fun roundedRectGeometry(
    rect: RectD,
    topLeft: Double,
    topRight: Double,
    bottomRight: Double,
    bottomLeft: Double,
    inset: Double = 0.0,
): PathGeometry {
    val limit = min(rect.width, rect.height) / 2.0
    fun clamp(value: Double): Double = (value - inset).coerceIn(0.0, limit)
    val tl = clamp(topLeft)
    val tr = clamp(topRight)
    val br = clamp(bottomRight)
    val bl = clamp(bottomLeft)
    if (tl == 0.0 && tr == 0.0 && br == 0.0 && bl == 0.0) {
        return PathGeometry(
            listOf(
                PathCommand.MoveTo(rect.left, rect.top),
                PathCommand.LineTo(rect.right, rect.top),
                PathCommand.LineTo(rect.right, rect.bottom),
                PathCommand.LineTo(rect.left, rect.bottom),
                PathCommand.Close,
            ),
        )
    }
    val l = rect.left
    val t = rect.top
    val r = rect.right
    val b = rect.bottom
    val commands = ArrayList<PathCommand>(10)
    commands += PathCommand.MoveTo(l + tl, t)
    commands += PathCommand.LineTo(r - tr, t)
    if (tr > 0.0) {
        commands += PathCommand.CubicTo(r - tr * (1 - KAPPA), t, r, t + tr * (1 - KAPPA), r, t + tr)
    }
    commands += PathCommand.LineTo(r, b - br)
    if (br > 0.0) {
        commands += PathCommand.CubicTo(r, b - br * (1 - KAPPA), r - br * (1 - KAPPA), b, r - br, b)
    }
    commands += PathCommand.LineTo(l + bl, b)
    if (bl > 0.0) {
        commands += PathCommand.CubicTo(l + bl * (1 - KAPPA), b, l, b - bl * (1 - KAPPA), l, b - bl)
    }
    commands += PathCommand.LineTo(l, t + tl)
    if (tl > 0.0) {
        commands += PathCommand.CubicTo(l, t + tl * (1 - KAPPA), l + tl * (1 - KAPPA), t, l + tl, t)
    }
    commands += PathCommand.Close
    return PathGeometry(commands)
}
