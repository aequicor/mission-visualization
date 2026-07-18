package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadExtent
import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadHalfWidth
import io.aequicor.visualization.subsystems.diagrams.arrows.fittedTo
import io.aequicor.visualization.subsystems.diagrams.arrows.resolvedArrowheads
import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.hypot

/**
 * The box an endpoint marker (arrowhead / ER cardinality glyph) occupies at one edge
 * end: [tip] is the attachment point, [rect] spans the marker's reach back along the
 * line by its lateral half-width. A foreign line that cuts this box slices the glyph
 * itself — a crow's foot with a stranger's lane through it stops reading as a crow's
 * foot — so the crossing-aware router prices these boxes like crossings and the
 * `MarkerCovered` lint rule reports any cut that still survives.
 */
internal data class EndpointMarkerZone(
    val tip: DiagramPoint,
    val rect: DiagramRect,
)

/**
 * Marker zones at both ends of [route]. With [fitToRun] the heads are first scaled down
 * to the straight run each end actually got ([fittedTo]) — exactly what the renderers
 * draw; without it the zones keep the notation's ideal extent, which is what routing
 * wants kept clear so markers never need that fallback in the first place.
 */
internal fun endpointMarkerZones(
    edge: DiagramEdge,
    route: RoutedEdge,
    fitToRun: Boolean = false,
): List<EndpointMarkerZone> = endpointMarkerZones(edge, route.points, fitToRun)

/** [endpointMarkerZones] for surfaces that carry bare polylines instead of [RoutedEdge]s. */
internal fun endpointMarkerZones(
    edge: DiagramEdge,
    points: List<DiagramPoint>,
    fitToRun: Boolean = false,
): List<EndpointMarkerZone> {
    if (points.size < 2) return emptyList()
    val heads = resolvedArrowheads(edge)
    return listOfNotNull(
        markerZone(heads.source, points[0], points[1], fitToRun),
        markerZone(heads.target, points[points.size - 1], points[points.size - 2], fitToRun),
    )
}

/** Ideal-extent marker rects of every routed edge, keyed by the owning edge. */
internal fun markerRectsByEdge(
    graph: DiagramGraph,
    routes: List<RoutedEdge>,
): Map<DiagramEdgeId, List<DiagramRect>> =
    buildMap {
        for (route in routes) {
            val edge = graph.edgeById(route.edgeId) ?: continue
            val zones = endpointMarkerZones(edge, route)
            if (zones.isNotEmpty()) put(route.edgeId, zones.map { it.rect })
        }
    }

private fun markerZone(
    head: DiagramArrowhead,
    tip: DiagramPoint,
    neighbor: DiagramPoint,
    fitToRun: Boolean,
): EndpointMarkerZone? {
    val dx = neighbor.x - tip.x
    val dy = neighbor.y - tip.y
    val run = hypot(dx, dy)
    if (run < GEOMETRY_EPSILON) return null
    val fitted = if (fitToRun) head.fittedTo(run) else head
    val extent = arrowheadExtent(fitted)
    if (extent < GEOMETRY_EPSILON) return null
    val halfWidth = arrowheadHalfWidth(fitted)
    if (halfWidth < GEOMETRY_EPSILON) return null
    val ux = dx / run
    val uy = dy / run
    val back = DiagramPoint(tip.x + ux * extent, tip.y + uy * extent)
    val px = -uy * halfWidth
    val py = ux * halfWidth
    val left = minOf(tip.x + px, tip.x - px, back.x + px, back.x - px)
    val right = maxOf(tip.x + px, tip.x - px, back.x + px, back.x - px)
    val top = minOf(tip.y + py, tip.y - py, back.y + py, back.y - py)
    val bottom = maxOf(tip.y + py, tip.y - py, back.y + py, back.y - py)
    return EndpointMarkerZone(
        tip = tip,
        rect = DiagramRect(x = left, y = top, width = right - left, height = bottom - top),
    )
}
