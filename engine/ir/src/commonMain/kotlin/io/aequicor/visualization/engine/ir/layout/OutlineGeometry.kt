package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.engine.ir.geometry.PathGeometry
import io.aequicor.visualization.engine.ir.geometry.RectD
import io.aequicor.visualization.engine.ir.geometry.arrowGeometry
import io.aequicor.visualization.engine.ir.geometry.contains
import io.aequicor.visualization.engine.ir.geometry.distanceToOutline
import io.aequicor.visualization.engine.ir.geometry.ellipseGeometry
import io.aequicor.visualization.engine.ir.geometry.lineGeometry
import io.aequicor.visualization.engine.ir.geometry.meetFit
import io.aequicor.visualization.engine.ir.geometry.regularPolygonGeometry
import io.aequicor.visualization.engine.ir.geometry.roundedRectGeometry
import io.aequicor.visualization.engine.ir.geometry.starGeometry
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode

/** Extra click tolerance (document units) around open shapes' stroked outline. */
private const val HIT_TOLERANCE = 2.0

/**
 * The device-independent outline of a node for path-accurate hit-testing, in the given
 * laid-out [rect]. Returns:
 * - the resolve-lowered [ResolvedNode.geometry] (vector/network/inline `d`, view-box space); else
 * - a primitive outline built from [rect] (ellipse/polygon/star/line/arrow/rounded-rect); else
 * - null when the axis-aligned box already IS the outline (frame/group/text/plain rectangle/empty
 *   vector), so the caller keeps the cheap bounding-box test.
 */
internal fun ResolvedNode.outlineGeometry(rect: RectD): PathGeometry? {
    geometry?.let { return it }
    val shape = shape ?: return null
    return when (shape.shape) {
        ShapeType.Ellipse -> ellipseGeometry(rect)
        ShapeType.Polygon -> regularPolygonGeometry(rect, shape.pointCount ?: 3)
        ShapeType.Star -> starGeometry(rect, shape.pointCount ?: 5, shape.innerRadius ?: 0.4)
        ShapeType.Line -> lineGeometry(rect)
        ShapeType.Arrow -> arrowGeometry(rect, strokes?.weight ?: 1.0)
        ShapeType.Vector -> null // no lowered geometry => empty vector renders as its box
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
