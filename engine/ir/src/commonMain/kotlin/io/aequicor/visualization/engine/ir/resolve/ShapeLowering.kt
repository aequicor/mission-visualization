package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.subsystems.figures.PathFillRule
import io.aequicor.visualization.subsystems.figures.PathGeometry
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.subsystems.figures.bounds
import io.aequicor.visualization.subsystems.figures.networkToGeometry
import io.aequicor.visualization.subsystems.figures.parseSvgPathToGeometry
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.VectorPath

/**
 * Lowers a shape's box-independent geometry (structural network or inline SVG paths) into the
 * device-independent [PathGeometry] IR in view-box coordinate space. Returns null for
 * parametric primitives (built draw-time from the laid-out box) and for pure `pathRef`/`iconRef`
 * references (dereferenced by the asset provider). Precedence: network > inline paths > (null).
 * Network lowering itself lives in `:subsystems:figures` ([networkToGeometry]).
 */
internal fun lowerShapeGeometry(shape: DesignNodeKind.Shape): PathGeometry? {
    shape.network?.takeIf { it.isNotEmpty() }?.let { return networkToGeometry(it, shape.viewBox) }
    if (shape.paths.isNotEmpty()) return lowerInlinePaths(shape.paths, shape.viewBox)
    return null
}

private fun lowerInlinePaths(paths: List<VectorPath>, viewBox: DesignViewBox?): PathGeometry {
    val fillRule = fillRuleOf(paths.firstOrNull()?.windingRule)
    val commands = paths.flatMap { parseSvgPathToGeometry(it.d).commands }
    val geometry = PathGeometry(commands, fillRule)
    return geometry.copy(sourceViewBox = viewBox.toRectD() ?: geometry.bounds())
}

private fun fillRuleOf(windingRule: String?): PathFillRule =
    if (windingRule == "evenodd") PathFillRule.EvenOdd else PathFillRule.NonZero

private fun DesignViewBox?.toRectD(): RectD? =
    if (this != null && width > 0.0 && height > 0.0) RectD(x, y, x + width, y + height) else null
