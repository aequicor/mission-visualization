package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.subsystems.figures.PathGeometry
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.subsystems.figures.arrowGeometry
import io.aequicor.visualization.subsystems.figures.contains
import io.aequicor.visualization.subsystems.figures.distanceToOutline
import io.aequicor.visualization.subsystems.figures.ellipseArcGeometry
import io.aequicor.visualization.subsystems.figures.ellipseGeometry
import io.aequicor.visualization.subsystems.figures.lineGeometry
import io.aequicor.visualization.subsystems.figures.meetFit
import io.aequicor.visualization.subsystems.figures.regularPolygonGeometry
import io.aequicor.visualization.subsystems.figures.roundedRectGeometry
import io.aequicor.visualization.subsystems.figures.starGeometry
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode

/** Extra click tolerance (document units) around open shapes' stroked outline. */
private const val HIT_TOLERANCE = 2.0

/**
 * The device-independent outline of a node for path-accurate hit-testing, in the given
 * laid-out [rect]. Returns:
 * - null for vector/SVG nodes, whose whole laid-out box is intentionally selectable; else
 * - the resolve-lowered [ResolvedNode.geometry] (boolean/network geometry, view-box space); else
 * - a primitive outline built from [rect] (ellipse/polygon/star/line/arrow/rounded-rect); else
 * - null when the axis-aligned box already IS the outline (frame/group/text/plain rectangle/empty
 *   vector), so the caller keeps the cheap bounding-box test.
 */
internal fun ResolvedNode.outlineGeometry(rect: RectD): PathGeometry? {
    // An SVG's transparent padding is still part of the editor object. Using its parsed paths
    // here made hover/selection work only while the pointer was directly over painted pixels.
    if (shape?.shape == ShapeType.Vector) return null
    geometry?.let { return it }
    val shape = shape ?: return null
    return when (shape.shape) {
        ShapeType.Ellipse -> {
            val sweep = shape.arcSweepDeg
            val inner = shape.innerRadius ?: 0.0
            if ((sweep != null && kotlin.math.abs(sweep) < 360.0) || inner > 0.0) {
                ellipseArcGeometry(rect, shape.arcStartDeg ?: 0.0, sweep ?: 360.0, inner)
            } else {
                ellipseGeometry(rect)
            }
        }
        ShapeType.Polygon -> regularPolygonGeometry(rect, shape.pointCount ?: 3)
        ShapeType.Star -> starGeometry(rect, shape.pointCount ?: 5, shape.innerRadius ?: 0.4)
        ShapeType.Line -> lineGeometry(rect)
        ShapeType.Arrow -> arrowGeometry(rect, strokes?.weight ?: 1.0)
        ShapeType.Vector -> null
        ShapeType.Rectangle -> if (cornerRadius.isZero) {
            null
        } else {
            roundedRectGeometry(
                rect,
                cornerRadius.topLeft,
                cornerRadius.topRight,
                cornerRadius.bottomRight,
                cornerRadius.bottomLeft,
            )
        }
    }
}

/**
 * True when ([lx], [ly]) — already in the node's unrotated frame — lies on/inside the node's
 * outline. View-box geometry maps the query point through [meetFit]`.inverse()`; open shapes use
 * stroke distance so lines/arrows stay clickable.
 */
internal fun ResolvedNode.outlineContains(geometry: PathGeometry, rect: RectD, lx: Double, ly: Double): Boolean {
    val (qx, qy) = geometry.sourceViewBox?.let { viewBox ->
        meetFit(viewBox, rect).inverse().apply(lx, ly)
    } ?: (lx to ly)
    return if (geometry.isClosed) {
        contains(geometry, qx, qy)
    } else {
        distanceToOutline(geometry, qx, qy) <= (strokes?.weight ?: 0.0) / 2.0 + HIT_TOLERANCE
    }
}
