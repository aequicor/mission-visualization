package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.abs

/** Clearance a nudged segment must keep from node bodies for the shift to be applied. */
private const val NUDGE_CLEARANCE = 2.0

/**
 * Routes every edge of the graph, skipping edges whose endpoints reference missing
 * nodes/ports (they simply do not render), then separates co-running segments with
 * [nudgeRoutedEdges]. This is the routing entry point for interactive surfaces
 * (canvas renderers, hit-testing) that must tolerate transiently inconsistent graphs.
 */
fun routeAllEdgesLenient(
    graph: DiagramGraph,
    options: RoutingOptions = RoutingOptions.Default,
): Map<DiagramEdgeId, RoutedEdge> {
    val routed = graph.edges.mapNotNull { edge ->
        runCatching { routeEdge(graph, edge, options) }.getOrNull()
    }
    return nudgeRoutedEdges(routed, options, waypointedEdgeIds(graph), nodeObstacles(graph))
        .associateBy { it.edgeId }
}

/** Edges pinned by manual waypoints — the nudge pass must not move them. */
internal fun waypointedEdgeIds(graph: DiagramGraph): Set<DiagramEdgeId> =
    graph.edges.asSequence().filter { it.waypoints.isNotEmpty() }.map { it.id }.toSet()

/** Visible node bodies a nudged segment must not be pushed into. */
internal fun nodeObstacles(graph: DiagramGraph): List<DiagramRect> =
    graph.nodes
        .filter { it.visible && it.width > GEOMETRY_EPSILON && it.height > GEOMETRY_EPSILON }
        .map { it.bounds }

/**
 * Simplified orthogonal nudging: when interior segments of different axis-aligned routes
 * run collinearly on top of each other (same corridor), they are spread a few pixels
 * apart so every line stays individually traceable. Only segments whose both endpoints
 * are interior bends move (anchors and stubs never move); the per-cluster spacing is
 * capped so no segment leaves the routing margin, a shift that would push a segment
 * against one of the [obstacles] (node bodies) is suppressed, and edges listed in
 * [pinnedEdgeIds] (manual waypoints) are never touched. The offset order follows how far
 * each route continues past the shared corridor, which keeps already-separated routes
 * from crossing. Non-orthogonal styles (straight/curved/isometric) pass through untouched.
 */
fun nudgeRoutedEdges(
    routes: List<RoutedEdge>,
    options: RoutingOptions = RoutingOptions.Default,
    pinnedEdgeIds: Set<DiagramEdgeId> = emptySet(),
    obstacles: List<DiagramRect> = emptyList(),
): List<RoutedEdge> {
    if (routes.size < 2) return routes
    val spacing = options.obstacleMargin * 2.0 / 3.0
    if (spacing <= 0.0) return routes
    val points = routes.map { it.points.toMutableList() }

    data class Movable(
        val route: Int,
        val index: Int,
        val coordinate: Double,
        val spanStart: Double,
        val spanEnd: Double,
        val continuation: Double,
    )

    fun movableSegments(horizontal: Boolean): List<Movable> = buildList {
        for ((routeIndex, route) in routes.withIndex()) {
            if (route.edgeId in pinnedEdgeIds) continue
            if (route.routing != DiagramRoutingStyle.ORTHOGONAL &&
                route.routing != DiagramRoutingStyle.SIMPLE &&
                route.routing != DiagramRoutingStyle.ENTITY_RELATION
            ) {
                continue
            }
            val pts = points[routeIndex]
            for (index in 1..pts.size - 3) {
                val a = pts[index]
                val b = pts[index + 1]
                val isHorizontal =
                    abs(a.y - b.y) < GEOMETRY_EPSILON && abs(a.x - b.x) > GEOMETRY_EPSILON
                val isVertical =
                    abs(a.x - b.x) < GEOMETRY_EPSILON && abs(a.y - b.y) > GEOMETRY_EPSILON
                if (horizontal != isHorizontal || isHorizontal == isVertical) continue
                val previous = pts[index - 1]
                val next = pts[index + 2]
                add(
                    Movable(
                        route = routeIndex,
                        index = index,
                        coordinate = if (horizontal) a.y else a.x,
                        spanStart = if (horizontal) minOf(a.x, b.x) else minOf(a.y, b.y),
                        spanEnd = if (horizontal) maxOf(a.x, b.x) else maxOf(a.y, b.y),
                        continuation = if (horizontal) previous.y + next.y else previous.x + next.x,
                    ),
                )
            }
        }
    }

    fun applyOffsets(horizontal: Boolean) {
        val segments = movableSegments(horizontal)
            .sortedWith(compareBy({ it.coordinate }, { it.spanStart }))
        var clusterStart = 0
        while (clusterStart < segments.size) {
            var clusterEnd = clusterStart + 1
            var reach = segments[clusterStart].spanEnd
            while (clusterEnd < segments.size &&
                abs(segments[clusterEnd].coordinate - segments[clusterStart].coordinate) < GEOMETRY_EPSILON &&
                segments[clusterEnd].spanStart < reach - GEOMETRY_EPSILON
            ) {
                reach = maxOf(reach, segments[clusterEnd].spanEnd)
                clusterEnd++
            }
            val cluster = segments.subList(clusterStart, clusterEnd)
            if (cluster.size >= 2) {
                val ordered = cluster.sortedWith(
                    compareBy({ it.continuation }, { routes[it.route].edgeId.value }, { it.index }),
                )
                // Cap the spread so the outermost offset stays inside the routing margin
                // (grid corridors run at most obstacleMargin from a node face).
                val maxOffset = (options.obstacleMargin - NUDGE_CLEARANCE).coerceAtLeast(0.0)
                val clusterSpacing = minOf(spacing, 2.0 * maxOffset / (ordered.size - 1))
                if (clusterSpacing < GEOMETRY_EPSILON) {
                    clusterStart = clusterEnd
                    continue
                }
                for ((slot, segment) in ordered.withIndex()) {
                    val offset = (slot - (ordered.size - 1) / 2.0) * clusterSpacing
                    if (abs(offset) < GEOMETRY_EPSILON) continue
                    val pts = points[segment.route]
                    val previous = pts[segment.index - 1]
                    val next = pts[segment.index + 2]
                    val adjacentBefore = if (horizontal) {
                        abs(previous.y - segment.coordinate)
                    } else {
                        abs(previous.x - segment.coordinate)
                    }
                    val adjacentAfter = if (horizontal) {
                        abs(next.y - segment.coordinate)
                    } else {
                        abs(next.x - segment.coordinate)
                    }
                    // A shift at least as long as an adjacent segment would collapse or
                    // reverse it — leave such a segment where it is.
                    if (abs(offset) >= minOf(adjacentBefore, adjacentAfter)) continue
                    val a = pts[segment.index]
                    val b = pts[segment.index + 1]
                    val shiftedA: DiagramPoint
                    val shiftedB: DiagramPoint
                    if (horizontal) {
                        shiftedA = DiagramPoint(a.x, a.y + offset)
                        shiftedB = DiagramPoint(b.x, b.y + offset)
                    } else {
                        shiftedA = DiagramPoint(a.x + offset, a.y)
                        shiftedB = DiagramPoint(b.x + offset, b.y)
                    }
                    // Never push a segment against a node body the original run kept
                    // clear of (raw-bounds fallback legs may already touch one).
                    val pushedIntoNode = obstacles.any { rect ->
                        segmentNearRect(shiftedA, shiftedB, rect, NUDGE_CLEARANCE) &&
                            !segmentNearRect(a, b, rect, NUDGE_CLEARANCE)
                    }
                    if (pushedIntoNode) continue
                    pts[segment.index] = shiftedA
                    pts[segment.index + 1] = shiftedB
                }
            }
            clusterStart = clusterEnd
        }
    }

    applyOffsets(horizontal = true)
    applyOffsets(horizontal = false)
    return routes.mapIndexed { index, route ->
        if (points[index] == route.points) route else route.copy(points = points[index].toList())
    }
}

/** Whether the axis-aligned segment `a..b` crosses [rect] inflated by [clearance]. */
private fun segmentNearRect(
    a: DiagramPoint,
    b: DiagramPoint,
    rect: DiagramRect,
    clearance: Double,
): Boolean {
    val left = rect.left - clearance
    val right = rect.right + clearance
    val top = rect.top - clearance
    val bottom = rect.bottom + clearance
    return if (abs(a.y - b.y) < GEOMETRY_EPSILON) {
        a.y > top + GEOMETRY_EPSILON && a.y < bottom - GEOMETRY_EPSILON &&
            maxOf(a.x, b.x) > left + GEOMETRY_EPSILON && minOf(a.x, b.x) < right - GEOMETRY_EPSILON
    } else {
        a.x > left + GEOMETRY_EPSILON && a.x < right - GEOMETRY_EPSILON &&
            maxOf(a.y, b.y) > top + GEOMETRY_EPSILON && minOf(a.y, b.y) < bottom - GEOMETRY_EPSILON
    }
}
