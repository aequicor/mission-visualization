package io.aequicor.visualization.subsystems.figures

import kotlin.math.min

/**
 * A `preserveAspectRatio="xMidYMid meet"` fit: the uniform scale that fits [viewBox]
 * entirely inside [box], centered, with the view-box origin subtracted. This is the
 * single transform shared by the renderer, the canvas overlay, and hit-testing so that
 * what is painted, what is clicked, and where handles land all agree.
 */
fun meetFit(viewBox: RectD, box: RectD): Affine2D {
    val vbW = if (viewBox.width != 0.0) viewBox.width else 1.0
    val vbH = if (viewBox.height != 0.0) viewBox.height else 1.0
    val scale = min(box.width / vbW, box.height / vbH)
    val tx = box.left + (box.width - vbW * scale) / 2.0 - viewBox.left * scale
    val ty = box.top + (box.height - vbH * scale) / 2.0 - viewBox.top * scale
    return Affine2D(a = scale, b = 0.0, c = 0.0, d = scale, e = tx, f = ty)
}

/** Applies an [Affine2D] to every command, producing geometry in the transformed space. */
fun PathGeometry.mapped(transform: Affine2D): PathGeometry {
    fun p(x: Double, y: Double): Pair<Double, Double> = transform.apply(x, y)
    val mapped = commands.map { c ->
        when (c) {
            is PathCommand.MoveTo -> p(c.x, c.y).let { PathCommand.MoveTo(it.first, it.second) }
            is PathCommand.LineTo -> p(c.x, c.y).let { PathCommand.LineTo(it.first, it.second) }
            is PathCommand.QuadTo -> {
                val (cx, cy) = p(c.cx, c.cy)
                val (x, y) = p(c.x, c.y)
                PathCommand.QuadTo(cx, cy, x, y)
            }
            is PathCommand.CubicTo -> {
                val (c1x, c1y) = p(c.c1x, c.c1y)
                val (c2x, c2y) = p(c.c2x, c.c2y)
                val (x, y) = p(c.x, c.y)
                PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
            }
            PathCommand.Close -> PathCommand.Close
        }
    }
    return PathGeometry(mapped, fillRule)
}

/** Bakes a view-box geometry into absolute coordinates fitted into [box] (meet fit). */
fun PathGeometry.fittedInto(box: RectD): PathGeometry {
    val viewBox = sourceViewBox ?: return this
    return mapped(meetFit(viewBox, box))
}

/** Axis-aligned bounds of a geometry's on-curve points and control points (in its own space). */
fun PathGeometry.bounds(): RectD? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun include(x: Double, y: Double) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    commands.forEach { c ->
        when (c) {
            is PathCommand.MoveTo -> include(c.x, c.y)
            is PathCommand.LineTo -> include(c.x, c.y)
            is PathCommand.QuadTo -> {
                include(c.cx, c.cy); include(c.x, c.y)
            }
            is PathCommand.CubicTo -> {
                include(c.c1x, c.c1y); include(c.c2x, c.c2y); include(c.x, c.y)
            }
            PathCommand.Close -> Unit
        }
    }
    if (minX > maxX || minY > maxY) return null
    return RectD(minX, minY, maxX, maxY)
}
