package io.aequicor.visualization.subsystems.annotations

/**
 * Tiny pure geometry value types for the annotations core. Deliberately local to this
 * module (no dependency on :engine:* or other subsystems), same spirit as
 * `SnapPoint`/`SnapBox` in anchoring and `PointD`/`RectD` in figures.
 */

/** A point in `Double` coordinates. */
public data class AnnotationPoint(val x: Double, val y: Double)

/** An axis-aligned rectangle in `Double` coordinates. */
public data class AnnotationRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    public companion object {
        public fun fromSize(x: Double, y: Double, width: Double, height: Double): AnnotationRect =
            AnnotationRect(x, y, x + width, y + height)
    }
}
