package io.aequicor.visualization.engine.ir.geometry

/**
 * Device-independent geometry IR. Pure Kotlin, no Compose — this is the single shared
 * seam consumed by the Compose renderer (via a thin adapter), by headless unit tests,
 * and by path-accurate hit-testing. Coordinates are [Double]; adapters narrow to Float.
 */

/** An axis-aligned rectangle in `Double` coordinates. */
data class RectD(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    companion object {
        fun fromSize(x: Double, y: Double, width: Double, height: Double): RectD =
            RectD(x, y, x + width, y + height)
    }
}

/** Fill rule for a path: nonzero winding or even-odd. */
enum class PathFillRule { NonZero, EvenOdd }

/** A single drawing command. Cubic is the canonical curve; quads and arcs lower to it or stay as quad. */
sealed interface PathCommand {
    data class MoveTo(val x: Double, val y: Double) : PathCommand

    data class LineTo(val x: Double, val y: Double) : PathCommand

    data class QuadTo(
        val cx: Double,
        val cy: Double,
        val x: Double,
        val y: Double,
    ) : PathCommand

    data class CubicTo(
        val c1x: Double,
        val c1y: Double,
        val c2x: Double,
        val c2y: Double,
        val x: Double,
        val y: Double,
    ) : PathCommand

    data object Close : PathCommand
}

/**
 * A resolved outline.
 * - [sourceViewBox] == null → [commands] are already in absolute document coordinates
 *   (parametric primitives lowered against the laid-out box).
 * - [sourceViewBox] != null → [commands] are in that view-box coordinate space; the renderer
 *   and hit-test apply a meet-style fit into the node box (vectors / SVG assets / icons).
 */
data class PathGeometry(
    val commands: List<PathCommand>,
    val fillRule: PathFillRule = PathFillRule.NonZero,
    val sourceViewBox: RectD? = null,
) {
    /** Open geometry (no [PathCommand.Close]); hit-test by stroke distance, not ray-cast. */
    val isClosed: Boolean get() = commands.lastOrNull() is PathCommand.Close

    companion object {
        val Empty: PathGeometry = PathGeometry(emptyList())
    }
}

/**
 * A 2D affine transform in SVG matrix order `[a c e; b d f]`:
 * `x' = a·x + c·y + e`, `y' = b·x + d·y + f`.
 */
data class Affine2D(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
) {
    fun apply(x: Double, y: Double): Pair<Double, Double> =
        (a * x + c * y + e) to (b * x + d * y + f)

    fun inverse(): Affine2D {
        val det = a * d - b * c
        require(det != 0.0) { "Affine2D is not invertible (det == 0)" }
        val inv = 1.0 / det
        val ia = d * inv
        val ib = -b * inv
        val ic = -c * inv
        val id = a * inv
        return Affine2D(
            a = ia,
            b = ib,
            c = ic,
            d = id,
            e = -(ia * e + ic * f),
            f = -(ib * e + id * f),
        )
    }

    companion object {
        val Identity: Affine2D = Affine2D(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    }
}
