package io.aequicor.visualization.subsystems.anchoring

/**
 * Pure, framework-free geometry value types for the anchoring subsystem. The editor maps its
 * own `BoundsBox`/`LineSegment` onto these at the module boundary (see `AnchoringMappers` in
 * `:shared`), keeping this engine independent of the app's presentation layer.
 */

/** Axis-aligned rectangle in document coordinates (the box *before* rotation is applied). */
data class SnapBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0
}

/** A point in document coordinates. */
data class SnapPoint(val x: Double, val y: Double)

/** A line segment in document coordinates. */
data class SnapLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

/** Translates the box by ([dx], [dy]); size is unchanged. */
fun SnapBox.translate(dx: Double, dy: Double): SnapBox = copy(x = x + dx, y = y + dy)
