package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.geometry.PathCommand
import io.aequicor.visualization.engine.ir.geometry.PathFillRule
import io.aequicor.visualization.engine.ir.geometry.PathGeometry
import io.aequicor.visualization.engine.ir.geometry.RectD
import io.aequicor.visualization.engine.ir.geometry.bounds
import io.aequicor.visualization.engine.ir.geometry.parseSvgPathToGeometry
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignViewBox
import io.aequicor.visualization.engine.ir.model.VectorNetwork
import io.aequicor.visualization.engine.ir.model.VectorPath
import io.aequicor.visualization.engine.ir.model.VectorVertex

/**
 * Lowers a shape's box-independent geometry (structural network or inline SVG paths) into the
 * device-independent [PathGeometry] IR in view-box coordinate space. Returns null for
 * parametric primitives (built draw-time from the laid-out box) and for pure `pathRef`/`iconRef`
 * references (dereferenced by the asset provider). Precedence: network > inline paths > (null).
 */
internal fun lowerShapeGeometry(shape: DesignNodeKind.Shape): PathGeometry? {
    shape.network?.takeIf { it.isNotEmpty() }?.let { return lowerNetwork(it, shape.viewBox) }
    if (shape.paths.isNotEmpty()) return lowerInlinePaths(shape.paths, shape.viewBox)
    return null
}

private fun lowerInlinePaths(paths: List<VectorPath>, viewBox: DesignViewBox?): PathGeometry {
    val fillRule = fillRuleOf(paths.firstOrNull()?.windingRule)
    val commands = paths.flatMap { parseSvgPathToGeometry(it.d).commands }
    val geometry = PathGeometry(commands, fillRule)
    return geometry.copy(sourceViewBox = viewBox.toRectD() ?: geometry.bounds())
}

private fun lowerNetwork(network: VectorNetwork, viewBox: DesignViewBox?): PathGeometry? {
    val vertices = network.vertices
    if (vertices.isEmpty()) return null
    val fillRule = fillRuleOf(network.regions.firstOrNull()?.windingRule)

    // Loops to draw: authored regions when present, otherwise one implicit loop over all
    // segments in declaration order.
    val loops: List<List<Int>> = if (network.regions.isNotEmpty()) {
        network.regions.flatMap { it.loops }
    } else {
        listOf(network.segments.indices.toList())
    }

    val commands = ArrayList<PathCommand>()
    for (loop in loops) {
        val segments = loop.mapNotNull { network.segments.getOrNull(it) }
        if (segments.isEmpty()) continue
        val start = vertices.getOrNull(segments.first().from) ?: continue
        commands += PathCommand.MoveTo(start.x, start.y)
        for (segment in segments) {
            val from = vertices.getOrNull(segment.from) ?: continue
            val to = vertices.getOrNull(segment.to) ?: continue
            commands += segmentToCommand(from, to)
        }
        if (segments.last().to == segments.first().from) commands += PathCommand.Close
    }
    if (commands.isEmpty()) return null
    val geometry = PathGeometry(commands, fillRule)
    return geometry.copy(sourceViewBox = viewBox.toRectD() ?: geometry.bounds())
}

/** A straight segment (no handles) collapses to a line; otherwise a cubic from the offset handles. */
private fun segmentToCommand(from: VectorVertex, to: VectorVertex): PathCommand {
    val out = from.outHandle
    val incoming = to.inHandle
    if (out == null && incoming == null) return PathCommand.LineTo(to.x, to.y)
    return PathCommand.CubicTo(
        c1x = from.x + (out?.dx ?: 0.0),
        c1y = from.y + (out?.dy ?: 0.0),
        c2x = to.x + (incoming?.dx ?: 0.0),
        c2y = to.y + (incoming?.dy ?: 0.0),
        x = to.x,
        y = to.y,
    )
}

private fun fillRuleOf(windingRule: String?): PathFillRule =
    if (windingRule == "evenodd") PathFillRule.EvenOdd else PathFillRule.NonZero

private fun DesignViewBox?.toRectD(): RectD? =
    if (this != null && width > 0.0 && height > 0.0) RectD(x, y, x + width, y + height) else null
