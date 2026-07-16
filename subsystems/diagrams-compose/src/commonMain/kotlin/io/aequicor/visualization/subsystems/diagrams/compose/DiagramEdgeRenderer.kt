package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import io.aequicor.visualization.subsystems.diagrams.arrows.ArrowheadGeometry
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadPath
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadsForRelation
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import io.aequicor.visualization.subsystems.diagrams.routing.routedEdgeToPath
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import kotlin.math.sqrt

/** Dash intervals period used by the flow animation on solid edges. */
internal const val FLOW_DASH_ON = 8f
internal const val FLOW_DASH_OFF = 6f

/** How far above its horizontal row a sequence-message caption is drawn. */
private const val SEQUENCE_MESSAGE_LABEL_LIFT = 9.0

/**
 * Draws one routed edge: the rounded line, arrowheads at both ends,
 * the connection mode (LINE / double-stroke LINK / thick ARROW band), the optional
 * flow animation, and up to three labels.
 *
 * @param flowPhase animated 0..1 fraction driving [DiagramEdge.flowAnimation]; `null`
 *   disables the animation (static rendering).
 * @param jumpOverRoutes routes drawn *below* this edge; [DiagramEdge.lineJumps] jumps
 *   are emitted where this edge crosses them (only the upper line jumps, draw.io-style).
 * @param labelObstacleRoutes all *other* edges' routes — undragged MIDDLE labels slide
 *   off crossings with them (must match the hit-test context, see [edgeLabelAnchorPoint]).
 */
internal fun DrawScope.drawDiagramEdge(
    edge: DiagramEdge,
    routed: RoutedEdge,
    colors: DiagramCanvasColors,
    measurer: ComposeTypographyMeasurer,
    flowPhase: Float? = null,
    jumpOverRoutes: List<RoutedEdge> = emptyList(),
    labelObstacleRoutes: List<List<DiagramPoint>> = emptyList(),
) {
    val style = edge.style
    val ink = (style.stroke?.toComposeColor() ?: colors.edgeStroke).applyOpacity(style.opacity)
    val strokeWidth = style.strokeWidth.toFloat().coerceAtLeast(0.5f)
    val seed = sketchSeed(edge.id.value)

    // Notation fallbacks: explicit NONE heads + non-Plain relation use the UML/ER notation.
    val notation = arrowheadsForRelation(edge.relation)
    val sourceHead = edge.sourceArrowhead.orNotation(notation.source)
    val targetHead = edge.targetArrowhead.orNotation(notation.target)
    val pattern = if (style.pattern == DiagramStrokePattern.SOLID) notation.pattern else style.pattern

    // Arrowhead geometry at the untouched route endpoints; the line is then cut back.
    val points = routed.points
    val sourceGeometry = arrowheadPath(sourceHead, tip = points.first(), direction = directionInto(points, source = true))
    val targetGeometry = arrowheadPath(targetHead, tip = points.last(), direction = directionInto(points, source = false))
    val shortened = routed.copy(
        points = shortenPolyline(points, sourceGeometry.lineShorten, targetGeometry.lineShorten),
    )

    val linePath = routedEdgeToPath(
        routed = shortened,
        style = style,
        lineJumps = edge.lineJumps,
        otherEdges = jumpOverRoutes,
    ).let { if (style.sketch) it.sketched(seed) else it }
    val composePath = linePath.toComposePath()

    val dashEffect = when {
        edge.flowAnimation && flowPhase != null -> {
            val intervals = strokePatternIntervals(pattern, strokeWidth)
                ?: floatArrayOf(FLOW_DASH_ON * strokeWidth.coerceAtLeast(1f), FLOW_DASH_OFF * strokeWidth.coerceAtLeast(1f))
            // Negative phase moves the dashes from source to target.
            PathEffect.dashPathEffect(intervals, -flowPhase * (intervals[0] + intervals[1]))
        }

        else -> strokePatternEffect(pattern, strokeWidth)
    }

    when (edge.connectionMode) {
        DiagramConnectionMode.LINE -> drawPath(
            composePath,
            ink,
            style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dashEffect),
        )

        DiagramConnectionMode.LINK -> {
            // Double line: wide colored band with the middle carved out by the surface.
            drawPath(composePath, ink, style = Stroke(strokeWidth * 3f, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = dashEffect))
            drawPath(composePath, colors.surface, style = Stroke(strokeWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round))
        }

        DiagramConnectionMode.ARROW -> {
            // Filled arrow-shaped connector: thick translucent band + solid center line.
            val bandWidth = (strokeWidth * 6f).coerceAtLeast(9f)
            val bandColor = (style.fill?.toComposeColor() ?: ink.copy(alpha = ink.alpha * 0.3f)).applyOpacity(style.opacity)
            drawPath(composePath, bandColor, style = Stroke(bandWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round))
            drawPath(composePath, ink, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dashEffect))
        }
    }

    drawArrowheadGeometry(sourceGeometry, ink, colors.surface, strokeWidth)
    drawArrowheadGeometry(targetGeometry, ink, colors.surface, strokeWidth)

    // Labels: anchored along the original route (arc-length fractions), draggable offsets applied.
    // A sequence message's caption reads above its horizontal row rather than sitting on the line.
    val messageLabelLift = if (edge.relation is DiagramRelation.Message) SEQUENCE_MESSAGE_LABEL_LIFT else 0.0
    edge.labels.forEach { edgeLabel ->
        val anchor = edgeLabelAnchorPoint(points, edgeLabel, labelObstacleRoutes)
        drawCenteredLabel(
            measurer,
            edgeLabel.label,
            anchor.x,
            anchor.y - messageLabelLift,
            colors.labelInk.applyOpacity(style.opacity),
            plateColor = colors.surface.copy(alpha = 0.85f),
        )
    }
}

/** Explicit head, or the relation-notation head when the edge does not override. */
private fun DiagramArrowhead.orNotation(notation: DiagramArrowhead): DiagramArrowhead =
    if (kind == DiagramArrowheadKind.NONE && notation.kind != DiagramArrowheadKind.NONE) notation else this

/** Marker: fill for *_FILLED kinds, surface-plated outline for hollow closed kinds, plain stroke otherwise. */
internal fun DrawScope.drawArrowheadGeometry(
    geometry: ArrowheadGeometry,
    ink: Color,
    surface: Color,
    strokeWidth: Float,
) {
    if (geometry.path.isEmpty) return
    val path = geometry.path.toComposePath()
    if (geometry.filled) {
        drawPath(path, ink, style = Fill)
        return
    }
    val closed = geometry.path.segments.any { it is DiagramPathSegment.Close }
    if (closed) drawPath(path, surface, style = Fill)
    drawPath(path, ink, style = Stroke(strokeWidth.coerceAtLeast(1.1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Unit direction of line travel into the tip at the source (reversed first segment) or target. */
internal fun directionInto(points: List<DiagramPoint>, source: Boolean): DiagramPoint {
    if (source) {
        for (index in 1 until points.size) {
            val dx = points[0].x - points[index].x
            val dy = points[0].y - points[index].y
            if (dx * dx + dy * dy > 1e-12) return DiagramPoint(dx, dy)
        }
    } else {
        val last = points.size - 1
        for (index in last - 1 downTo 0) {
            val dx = points[last].x - points[index].x
            val dy = points[last].y - points[index].y
            if (dx * dx + dy * dy > 1e-12) return DiagramPoint(dx, dy)
        }
    }
    return DiagramPoint(1.0, 0.0)
}

/**
 * Cuts the polyline back from both ends by the arrowheads' line-shorten amounts,
 * consuming whole segments when the cut is longer than the end segment.
 */
internal fun shortenPolyline(
    points: List<DiagramPoint>,
    startShorten: Double,
    endShorten: Double,
): List<DiagramPoint> {
    if (points.size < 2 || (startShorten <= 0.0 && endShorten <= 0.0)) return points
    var result = points
    if (startShorten > 0.0) result = cutFront(result, startShorten)
    if (endShorten > 0.0) result = cutFront(result.reversed(), endShorten).reversed()
    return result
}

private fun cutFront(points: List<DiagramPoint>, amount: Double): List<DiagramPoint> {
    var remaining = amount
    var index = 0
    while (index < points.size - 1) {
        val a = points[index]
        val b = points[index + 1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = sqrt(dx * dx + dy * dy)
        if (length > remaining) {
            val t = remaining / length
            val start = DiagramPoint(a.x + dx * t, a.y + dy * t)
            return listOf(start) + points.subList(index + 1, points.size)
        }
        // Keep at least one full segment so the route never collapses to a point.
        if (index + 2 >= points.size) {
            val t = ((length - 0.5) / length).coerceIn(0.0, 1.0)
            return listOf(DiagramPoint(a.x + dx * t, a.y + dy * t), b)
        }
        remaining -= length
        index++
    }
    return points
}
