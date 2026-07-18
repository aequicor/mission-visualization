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
import io.aequicor.visualization.subsystems.diagrams.geometry.anchorPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.outlinePath
import io.aequicor.visualization.subsystems.diagrams.geometry.outlineResizeHandlePoints
import io.aequicor.visualization.subsystems.diagrams.hittest.connectionPorts
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAvoidRects
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelObstacleRoutes
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import kotlin.math.hypot

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

/** Selection contour + 8 resize handles on the rendered outline of each selected node. */
@Composable
fun DiagramSelectionOverlay(
    graph: DiagramGraph,
    selectedNodeIds: Set<DiagramNodeId>,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    handleSize: Float = 7f,
    chromeScale: Float = 1f,
) {
    Canvas(modifier) {
        selectedNodeIds.forEach { id ->
            val node = graph.nodeById(id) ?: return@forEach
            drawSelectionFrame(node, style, handleSize * chromeScale, chromeScale)
        }
    }
}

internal fun DrawScope.drawSelectionFrame(
    node: DiagramNode,
    style: DiagramOverlayStyle,
    handleSize: Float,
    chromeScale: Float = 1f,
) {
    drawPath(
        node.outlinePath().toComposePath(),
        color = style.accent,
        style = Stroke(1.5f * chromeScale, join = StrokeJoin.Round),
    )

    val half = handleSize / 2f
    node.outlineResizeHandlePoints().forEach { point ->
        val corner = Offset(point.x.toFloat() - half, point.y.toFloat() - half)
        drawRect(style.handleFill, topLeft = corner, size = Size(handleSize, handleSize))
        drawRect(
            style.handleStroke,
            topLeft = corner,
            size = Size(handleSize, handleSize),
            style = Stroke(1.2f * chromeScale),
        )
    }
}

/**
 * Connection-point indicators for the given nodes (usually the selected or hovered one).
 * Every available arrow-start point is a blue `×`; only the point directly under the
 * pointer receives the green hover marker.
 */
@Composable
fun DiagramPortsOverlay(
    graph: DiagramGraph,
    nodeIds: Set<DiagramNodeId>,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    highlightedNodeId: DiagramNodeId? = null,
    highlightedPortId: DiagramPortId? = null,
    chromeScale: Float = 1f,
) {
    Canvas(modifier) {
        nodeIds.forEach { id ->
            val node = graph.nodeById(id) ?: return@forEach
            drawPortIndicators(
                node = node,
                style = style,
                highlightedPortId = highlightedPortId.takeIf { id == highlightedNodeId },
                chromeScale = chromeScale,
            )
        }
    }
}

internal fun DrawScope.drawPortIndicators(
    node: DiagramNode,
    style: DiagramOverlayStyle,
    highlightedPortId: DiagramPortId? = null,
    chromeScale: Float = 1f,
) {
    // Draw the passive grid first so the active marker can sit on top without shifting geometry.
    node.connectionPorts().forEach { port ->
        val point = anchorPoint(node, port)
        val center = Offset(point.x.toFloat(), point.y.toFloat())
        if (port.id == highlightedPortId) {
            drawCircle(style.fixedIndicator.copy(alpha = 0.20f), radius = 7f * chromeScale, center = center)
            drawCircle(
                style.fixedIndicator,
                radius = 7f * chromeScale,
                center = center,
                style = Stroke(1.2f * chromeScale),
            )
            drawFixedPortMarker(center, style.fixedIndicator, chromeScale)
        } else {
            drawFixedPortMarker(center, style.floatingIndicator, chromeScale)
        }
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
    chromeScale: Float = 1f,
) {
    Canvas(modifier) {
        when (target) {
            is ConnectTarget.Port -> {
                val node = graph.nodeById(target.nodeId) ?: return@Canvas
                node.connectionPorts().forEach { port ->
                    val point = anchorPoint(node, port)
                    drawFixedPortMarker(
                        Offset(point.x.toFloat(), point.y.toFloat()),
                        style.fixedIndicator,
                        chromeScale,
                    )
                }
                // The point being snapped to reads brighter, with a filled ring.
                val snap = Offset(target.snapPoint.x.toFloat(), target.snapPoint.y.toFloat())
                drawCircle(style.fixedIndicator.copy(alpha = 0.22f), radius = 6f * chromeScale, center = snap)
                drawCircle(
                    style.fixedIndicator,
                    radius = 6f * chromeScale,
                    center = snap,
                    style = Stroke(1.6f * chromeScale),
                )
            }

            is ConnectTarget.Floating -> {
                val node = graph.nodeById(target.nodeId) ?: return@Canvas
                drawPath(
                    path = node.outlinePath().toComposePath(),
                    color = style.floatingIndicator,
                    style = Stroke(1.8f * chromeScale, join = StrokeJoin.Round),
                )
                val snap = Offset(target.snapPoint.x.toFloat(), target.snapPoint.y.toFloat())
                drawCircle(style.floatingIndicator, radius = 3.5f * chromeScale, center = snap)
            }

            is ConnectTarget.Free, null -> Unit
        }
    }
}

private fun DrawScope.drawFixedPortMarker(center: Offset, color: Color, chromeScale: Float = 1f) {
    val arm = 3.5f * chromeScale
    val strokeWidth = 1.6f * chromeScale
    drawLine(
        color,
        Offset(center.x - arm, center.y - arm),
        Offset(center.x + arm, center.y + arm),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color,
        Offset(center.x - arm, center.y + arm),
        Offset(center.x + arm, center.y - arm),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

/**
 * Grabs of selected edges: endpoint rings at the routed polyline's ends (drag to re-attach),
 * lighter "virtual bend" dots at each straight-segment midpoint (grab to add a bend — backed
 * by the edge-body drag), the manual-waypoint circles, and diamond grabs at the label
 * anchors — mirrors the core hit-test's `EndpointHandle` / `WaypointHandle` / `LabelHandle`
 * geometry so what is drawn is what is hit.
 */
@Composable
fun DiagramWaypointOverlay(
    graph: DiagramGraph,
    selectedEdgeIds: Set<DiagramEdgeId>,
    routes: Map<DiagramEdgeId, RoutedEdge>,
    modifier: Modifier = Modifier,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    chromeScale: Float = 1f,
) {
    Canvas(modifier) {
        val routePoints = routes.mapValues { it.value.points }
        selectedEdgeIds.forEach { id ->
            val edge = graph.edgeById(id) ?: return@forEach
            val route = routes[id]?.points

            // Virtual "grab to bend" dots at each straight-segment midpoint with no manual
            // waypoint on it. Drawn first (and lighter) so the real handles read on top; the
            // dot is a hint only — dragging it hits the edge body under it, which mints a bend.
            if (route != null && route.size >= 2) {
                for (index in 0 until route.size - 1) {
                    val a = route[index]
                    val b = route[index + 1]
                    val midX = (a.x + b.x) / 2.0
                    val midY = (a.y + b.y) / 2.0
                    val segLength = hypot(b.x - a.x, b.y - a.y)
                    val nearManualWaypoint = edge.waypoints.any { wp ->
                        val dx = wp.x - midX
                        val dy = wp.y - midY
                        dx * dx + dy * dy <= VirtualBendDotSuppressRadius * VirtualBendDotSuppressRadius
                    }
                    if (!nearManualWaypoint && segLength >= MinVirtualBendDotSegment) {
                        drawVirtualBendDot(
                            Offset(midX.toFloat(), midY.toFloat()),
                            style,
                            chromeScale = chromeScale,
                        )
                    }
                }
            }

            // Real manual waypoint grabs.
            edge.waypoints.forEach { waypoint ->
                drawWaypointHandle(
                    Offset(waypoint.x.toFloat(), waypoint.y.toFloat()),
                    style,
                    chromeScale = chromeScale,
                )
            }

            if (route != null && route.size >= 2) {
                // Endpoint rings: source at the first routed point, target at the last (over the
                // arrowhead) — the draw.io re-attach grabs, visually distinct from the bend dots.
                drawEndpointHandle(
                    Offset(route.first().x.toFloat(), route.first().y.toFloat()),
                    style,
                    chromeScale = chromeScale,
                )
                drawEndpointHandle(
                    Offset(route.last().x.toFloat(), route.last().y.toFloat()),
                    style,
                    chromeScale = chromeScale,
                )

                edge.labels.forEach { label ->
                    val anchor = edgeLabelAnchorPoint(route, label, edgeLabelObstacleRoutes(graph, routePoints, id), edgeLabelAvoidRects(graph, id, routePoints))
                    drawLabelHandle(
                        Offset(anchor.x.toFloat(), anchor.y.toFloat()),
                        style,
                        chromeScale = chromeScale,
                    )
                }
            }
        }
    }
}

/** A manual waypoint within this many doc units of a segment midpoint suppresses its bend dot. */
private const val VirtualBendDotSuppressRadius = 6.0

/** Segments shorter than this (doc units) skip their bend dot, so it never collides with a ring. */
private const val MinVirtualBendDotSegment = 18.0

internal fun DrawScope.drawWaypointHandle(
    center: Offset,
    style: DiagramOverlayStyle,
    radius: Float = 4f,
    chromeScale: Float = 1f,
) {
    val scaledRadius = radius * chromeScale
    drawCircle(style.accent, radius = scaledRadius, center = center)
    drawCircle(style.handleFill, radius = scaledRadius, center = center, style = Stroke(1.4f * chromeScale))
}

/**
 * An edge endpoint re-attach grab: a slightly larger [DiagramOverlayStyle.handleFill]-filled
 * circle with an [DiagramOverlayStyle.accent] ring — the draw.io source/target endpoint look,
 * heavier than the manual-waypoint dot so the two read differently.
 */
internal fun DrawScope.drawEndpointHandle(
    center: Offset,
    style: DiagramOverlayStyle,
    radius: Float = 5.5f,
    chromeScale: Float = 1f,
) {
    val scaledRadius = radius * chromeScale
    drawCircle(style.handleFill, radius = scaledRadius, center = center)
    drawCircle(style.accent, radius = scaledRadius, center = center, style = Stroke(1.6f * chromeScale))
}

/**
 * A "grab here to bend" hint at a segment midpoint: a small, low-alpha accent dot with a thin
 * accent ring — deliberately lighter than a real waypoint so the affordance reads as virtual.
 */
private fun DrawScope.drawVirtualBendDot(
    center: Offset,
    style: DiagramOverlayStyle,
    radius: Float = 3f,
    chromeScale: Float = 1f,
) {
    val scaledRadius = radius * chromeScale
    drawCircle(style.accent.copy(alpha = 0.35f), radius = scaledRadius, center = center)
    drawCircle(style.accent.copy(alpha = 0.7f), radius = scaledRadius, center = center, style = Stroke(chromeScale))
}

private fun DrawScope.drawLabelHandle(
    center: Offset,
    style: DiagramOverlayStyle,
    half: Float = 4f,
    chromeScale: Float = 1f,
) {
    val scaledHalf = half * chromeScale
    val diamond = Path().apply {
        moveTo(center.x, center.y - scaledHalf)
        lineTo(center.x + scaledHalf, center.y)
        lineTo(center.x, center.y + scaledHalf)
        lineTo(center.x - scaledHalf, center.y)
        close()
    }
    drawPath(diamond, style.handleFill)
    drawPath(diamond, style.accent, style = Stroke(1.4f * chromeScale))
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
    chromeScale: Float = 1f,
) {
    Canvas(modifier) {
        val node = nodeId?.let { graph.nodeById(it) } ?: return@Canvas
        val bounds = node.bounds
        val scaledDistance = distance * chromeScale
        drawDirectionalArrow(
            Offset(bounds.centerX.toFloat(), bounds.top.toFloat() - scaledDistance),
            dx = 0f,
            dy = -1f,
            style = style,
            chromeScale = chromeScale,
        )
        drawDirectionalArrow(
            Offset(bounds.right.toFloat() + scaledDistance, bounds.centerY.toFloat()),
            dx = 1f,
            dy = 0f,
            style = style,
            chromeScale = chromeScale,
        )
        drawDirectionalArrow(
            Offset(bounds.centerX.toFloat(), bounds.bottom.toFloat() + scaledDistance),
            dx = 0f,
            dy = 1f,
            style = style,
            chromeScale = chromeScale,
        )
        drawDirectionalArrow(
            Offset(bounds.left.toFloat() - scaledDistance, bounds.centerY.toFloat()),
            dx = -1f,
            dy = 0f,
            style = style,
            chromeScale = chromeScale,
        )
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
    chromeScale: Float = 1f,
) {
    val scaledSize = size * chromeScale
    // Perpendicular of the pointing direction, so the base spans across the arrow.
    val px = -dy
    val py = dx
    val base = Offset(tip.x - dx * scaledSize, tip.y - dy * scaledSize)
    val triangle = Path().apply {
        moveTo(base.x + px * scaledSize, base.y + py * scaledSize)
        lineTo(tip.x, tip.y)
        lineTo(base.x - px * scaledSize, base.y - py * scaledSize)
        close()
    }
    // Faint blue idle-hint opacities (draw.io parity): the chevrons read as a light hint over the
    // shape until the pointer engages them, not as solid blue.
    drawPath(triangle, style.accent.copy(alpha = 0.18f))
    drawPath(
        triangle,
        style.accent.copy(alpha = 0.45f),
        style = Stroke(1.2f * chromeScale, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Free helper for hosts that already own a Canvas: same chrome, no extra composable. */
fun DrawScope.drawDiagramSelection(
    graph: DiagramGraph,
    selectedNodeIds: Set<DiagramNodeId>,
    style: DiagramOverlayStyle = DiagramOverlayStyle(),
    handleSize: Float = 7f,
    chromeScale: Float = 1f,
) {
    selectedNodeIds.forEach { id ->
        val node = graph.nodeById(id) ?: return@forEach
        drawSelectionFrame(node, style, handleSize * chromeScale, chromeScale)
    }
}
