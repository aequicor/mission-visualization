package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.arrowGeometry
import io.aequicor.visualization.subsystems.figures.ellipseArcGeometry
import io.aequicor.visualization.subsystems.figures.ellipseGeometry
import io.aequicor.visualization.subsystems.figures.lineGeometry
import io.aequicor.visualization.subsystems.figures.regularPolygonGeometry
import io.aequicor.visualization.subsystems.figures.roundedRectGeometry
import io.aequicor.visualization.subsystems.figures.starGeometry
import io.aequicor.visualization.subsystems.figures.toNetwork

/**
 * Bakes a parametric shape (its box `0..width × 0..height`) into an editable [VectorNetwork] for
 * "convert to editable vector". Curved primitives (ellipse, rounded rect) become bezier vertices.
 *
 * Stays in the editor layer because it unwraps the IR node model ([DesignNodeKind.Shape]) — the
 * pure geometry builders and network conversion it delegates to live in `:subsystems:figures`.
 */
fun parametricToNetwork(shape: DesignNodeKind.Shape, size: DesignSize): VectorNetwork {
    val width = size.width ?: 100.0
    val height = size.height ?: 100.0
    val rect = RectD(0.0, 0.0, width, height)
    val geometry = when (shape.shape) {
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
        ShapeType.Arrow -> arrowGeometry(rect, 2.0)
        ShapeType.Rectangle, ShapeType.Vector -> roundedRectGeometry(rect, 0.0, 0.0, 0.0, 0.0)
    }
    return geometry.toNetwork()
}
