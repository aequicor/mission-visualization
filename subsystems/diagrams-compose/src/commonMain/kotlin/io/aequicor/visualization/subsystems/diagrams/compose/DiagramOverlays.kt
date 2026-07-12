package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.aequicor.visualization.subsystems.diagrams.hittest.ConnectTarget
import io.aequicor.visualization.subsystems.diagrams.hittest.connectionPorts
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge

/**
 * Chrome colors of the editor overlays (selection frame, handles, port indicators).
 * The editor bridges its theme tokens here; defaults approximate a light accent.
 */
@Immutable
data class DiagramOverlayStyle(
    val accent: Color = Color(0xFF2A7FFF),
    val handleFill: Color = Color.White,
    val handleStroke: Color = Color(0xFF2A7FFF),
    /** Floating connection hints (the draw.io `·` dots on node sides). */
    val floatingIndicator: Color = Color(0xFF2A7FFF),
    /** Fixed connection points (the `×` markers of declared ports). */
    val fixedIndicator: Color = Color(0xFF0E9E6E),
)

/** Selection frame + 8 resize handles around each selected node. */
@Composable
fun DiagramSelectionOverlay(
    graph: DiagramGraph,
    selectedNodeIds: Set<DiagramNodeId>,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    handleSize: Float = 7f,
) {
    Canvas(modifier) {
        selectedNodeIds.forEach { id ->
            val node = graph.nodeById(id) ?: return@forEach
            drawSelectionFrame(node, style, handleSize)
        }
    }
}

internal fun DrawScope.drawSelectionFrame(
    node: DiagramNode,
    style: DiagramOverlayStyle,
    handleSize: Float,
) {
    val bounds = node.bounds
    val topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat())
    val size = Size(bounds.width.toFloat(), bounds.height.toFloat())
    drawRect(style.accent, topLeft = topLeft, size = size, style = Stroke(1.5f))

    val xs = listOf(bounds.left, bounds.centerX, bounds.right)
    val ys = listOf(bounds.top, bounds.centerY, bounds.bottom)
    val half = handleSize / 2f
    ys.forEachIndexed { rowIndex, y ->
        xs.forEachIndexed { columnIndex, x ->
            if (rowIndex == 1 && columnIndex == 1) return@forEachIndexed // center is not a handle
            val corner = Offset(x.toFloat() - half, y.toFloat() - half)
            drawRect(style.handleFill, topLeft = corner, size = Size(handleSize, handleSize))
            drawRect(style.handleStroke, topLeft = corner, size = Size(handleSize, handleSize), style = Stroke(1.2f))
        }
    }
}

/**
 * Connection-point indicators for the given nodes (usually the hovered one):
 * `×` markers on declared (fixed) ports, `·` dots at the side midpoints as floating hints
 * when a side carries no port.
 */
@Composable
fun DiagramPortsOverlay(
    graph: DiagramGraph,
    nodeIds: Set<DiagramNodeId>,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
) {
    Canvas(modifier) {
        nodeIds.forEach { id ->
            val node = graph.nodeById(id) ?: return@forEach
            drawPortIndicators(node, style)
        }
    }
}

internal fun DrawScope.drawPortIndicators(node: DiagramNode, style: DiagramOverlayStyle) {
    // Every connection point (declared ports + the standard side midpoints) drawn as a green ×
    // — the draw.io "you can connect here" affordance shown while hovering a node.
    node.connectionPorts().forEach { port ->
        val point = node.portPosition(port)
        drawFixedPortMarker(Offset(point.x.toFloat(), point.y.toFloat()), style.fixedIndicator)
    }
}

/**
 * Live feedback while dragging an edge out: the node the pointer is over lights up so the
 * user sees where the connector will land and whether it will pin (green cross, fixed) or
 * float (blue perimeter). Driven by the overlay's per-frame [ConnectTarget].
 */
@Composable
fun DiagramConnectTargetOverlay(
    graph: DiagramGraph,
    target: ConnectTarget?,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
) {
    Canvas(modifier) {
        when (target) {
            is ConnectTarget.Port -> {
                val node = graph.nodeById(target.nodeId) ?: return@Canvas
                node.connectionPorts().forEach { port ->
                    val point = node.portPosition(port)
                    drawFixedPortMarker(Offset(point.x.toFloat(), point.y.toFloat()), style.fixedIndicator)
                }
                // The point being snapped to reads brighter, with a filled ring.
                val snap = Offset(target.snapPoint.x.toFloat(), target.snapPoint.y.toFloat())
                drawCircle(style.fixedIndicator.copy(alpha = 0.22f), radius = 6f, center = snap)
                drawCircle(style.fixedIndicator, radius = 6f, center = snap, style = Stroke(1.6f))
            }

            is ConnectTarget.Floating -> {
                val node = graph.nodeById(target.nodeId) ?: return@Canvas
                val bounds = node.bounds
                drawRect(
                    color = style.floatingIndicator,
                    topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
                    size = Size(bounds.width.toFloat(), bounds.height.toFloat()),
                    style = Stroke(1.8f),
                )
                val snap = Offset(target.snapPoint.x.toFloat(), target.snapPoint.y.toFloat())
                drawCircle(style.floatingIndicator, radius = 3.5f, center = snap)
            }

            is ConnectTarget.Free, null -> Unit
        }
    }
}

private fun DrawScope.drawFixedPortMarker(center: Offset, color: Color, arm: Float = 3.5f) {
    drawLine(color, Offset(center.x - arm, center.y - arm), Offset(center.x + arm, center.y + arm), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(color, Offset(center.x - arm, center.y + arm), Offset(center.x + arm, center.y - arm), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

/**
 * Waypoint grabs of selected edges (blue circles at the manual route points) plus
 * diamond grabs at the label anchors — mirrors the core hit-test's
 * `WaypointHandle` / `LabelHandle` geometry so what is drawn is what is hit.
 */
@Composable
fun DiagramWaypointOverlay(
    graph: DiagramGraph,
    selectedEdgeIds: Set<DiagramEdgeId>,
    routes: Map<DiagramEdgeId, RoutedEdge>,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
) {
    Canvas(modifier) {
        selectedEdgeIds.forEach { id ->
            val edge = graph.edgeById(id) ?: return@forEach
            edge.waypoints.forEach { waypoint ->
                drawWaypointHandle(Offset(waypoint.x.toFloat(), waypoint.y.toFloat()), style)
            }
            val route = routes[id]?.points ?: return@forEach
            edge.labels.forEach { label ->
                val anchor = edgeLabelAnchorPoint(route, label)
                drawLabelHandle(Offset(anchor.x.toFloat(), anchor.y.toFloat()), style)
            }
        }
    }
}

internal fun DrawScope.drawWaypointHandle(center: Offset, style: DiagramOverlayStyle, radius: Float = 4f) {
    drawCircle(style.accent, radius = radius, center = center)
    drawCircle(style.handleFill, radius = radius, center = center, style = Stroke(1.4f))
}

private fun DrawScope.drawLabelHandle(center: Offset, style: DiagramOverlayStyle, half: Float = 4f) {
    val diamond = Path().apply {
        moveTo(center.x, center.y - half)
        lineTo(center.x + half, center.y)
        lineTo(center.x, center.y + half)
        lineTo(center.x - half, center.y)
        close()
    }
    drawPath(diamond, style.handleFill)
    drawPath(diamond, style.accent, style = Stroke(1.4f))
}

/**
 * The four directional chevrons shown around a hovered node — the draw.io gesture
 * "hover → arrows → drag to spawn a connected clone".
 */
@Composable
fun DiagramDirectionalArrowsOverlay(
    graph: DiagramGraph,
    nodeId: DiagramNodeId?,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    distance: Float = 14f,
) {
    Canvas(modifier) {
        val node = nodeId?.let { graph.nodeById(it) } ?: return@Canvas
        val bounds = node.bounds
        drawDirectionalArrow(Offset(bounds.centerX.toFloat(), bounds.top.toFloat() - distance), dx = 0f, dy = -1f, style = style)
        drawDirectionalArrow(Offset(bounds.right.toFloat() + distance, bounds.centerY.toFloat()), dx = 1f, dy = 0f, style = style)
        drawDirectionalArrow(Offset(bounds.centerX.toFloat(), bounds.bottom.toFloat() + distance), dx = 0f, dy = 1f, style = style)
        drawDirectionalArrow(Offset(bounds.left.toFloat() - distance, bounds.centerY.toFloat()), dx = -1f, dy = 0f, style = style)
    }
}

/**
 * One hover directional arrow: a filled translucent triangle pointing outward (draw.io's
 * blue directional-arrow affordance), with a slightly stronger outline so it stays legible
 * on top of the shape and the canvas dots.
 */
private fun DrawScope.drawDirectionalArrow(
    tip: Offset,
    dx: Float,
    dy: Float,
    style: DiagramOverlayStyle,
    size: Float = 7f,
) {
    // Perpendicular of the pointing direction, so the base spans across the arrow.
    val px = -dy
    val py = dx
    val base = Offset(tip.x - dx * size, tip.y - dy * size)
    val triangle = Path().apply {
        moveTo(base.x + px * size, base.y + py * size)
        lineTo(tip.x, tip.y)
        lineTo(base.x - px * size, base.y - py * size)
        close()
    }
    drawPath(triangle, style.accent.copy(alpha = 0.5f))
    drawPath(triangle, style.accent.copy(alpha = 0.9f), style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Free helper for hosts that already own a Canvas: same chrome, no extra composable. */
fun DrawScope.drawDiagramSelection(
    graph: DiagramGraph,
    selectedNodeIds: Set<DiagramNodeId>,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    handleSize: Float = 7f,
) {
    selectedNodeIds.forEach { id ->
        val node = graph.nodeById(id) ?: return@forEach
        drawSelectionFrame(node, style, handleSize)
    }
}
