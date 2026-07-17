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

/** Upper bound on the re-cluster/re-spread rounds of [nudgeRoutedEdges]. */
private const val NUDGE_PASSES = 3

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
    val sequence = sequenceMessageRoutes(graph)
    val routed = graph.edges.mapNotNull { edge ->
        sequence[edge.id] ?: runCatching { routeEdge(graph, edge, options) }.getOrNull()
    }
    return nudgeRoutedEdges(routed, options, waypointedEdgeIds(graph) + sequence.keys, nodeObstacles(graph))
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
 * are interior bends move (anchors and stubs never move), but fixed endpoint segments and
 * routes in [pinnedEdgeIds] still reserve their lane so movable segments cannot disappear
 * underneath them. The per-cluster spacing is capped so no segment leaves the routing
 * margin, and a shift that would push a segment against one of the [obstacles] (node
 * bodies) is suppressed. The offset order follows how far each route continues past the
 * shared corridor, which keeps already-separated routes from crossing. Non-orthogonal
 * styles (straight/curved/isometric) pass through untouched.
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

    data class LaneSegment(
        val route: Int,
        val index: Int,
        val coordinate: Double,
        val spanStart: Double,
        val spanEnd: Double,
        val continuation: Double,
        val movable: Boolean,
    )

    fun laneSegments(horizontal: Boolean): List<LaneSegment> = buildList {
        for ((routeIndex, route) in routes.withIndex()) {
            if (route.routing != DiagramRoutingStyle.ORTHOGONAL &&
                route.routing != DiagramRoutingStyle.SIMPLE &&
                route.routing != DiagramRoutingStyle.ENTITY_RELATION &&
                route.routing != DiagramRoutingStyle.CURVED
            ) {
                continue
            }
            val pts = points[routeIndex]
            for (index in 0 until pts.lastIndex) {
                val a = pts[index]
                val b = pts[index + 1]
                val isHorizontal =
                    abs(a.y - b.y) < GEOMETRY_EPSILON && abs(a.x - b.x) > GEOMETRY_EPSILON
                val isVertical =
                    abs(a.x - b.x) < GEOMETRY_EPSILON && abs(a.y - b.y) > GEOMETRY_EPSILON
                if (horizontal != isHorizontal || isHorizontal == isVertical) continue
                val previous = pts.getOrNull(index - 1)
                val next = pts.getOrNull(index + 2)
                add(
                    LaneSegment(
                        route = routeIndex,
                        index = index,
                        coordinate = if (horizontal) a.y else a.x,
                        spanStart = if (horizontal) minOf(a.x, b.x) else minOf(a.y, b.y),
                        spanEnd = if (horizontal) maxOf(a.x, b.x) else maxOf(a.y, b.y),
                        continuation = if (horizontal) {
                            (previous?.y ?: a.y) + (next?.y ?: b.y)
                        } else {
                            (previous?.x ?: a.x) + (next?.x ?: b.x)
                        },
                        movable = route.edgeId !in pinnedEdgeIds && index > 0 && index + 2 < pts.size,
                    ),
                )
            }
        }
    }

    // Segments closer than this across the lane axis share one corridor: exactly
    // collinear runs AND near-misses (a couple of pixels apart read as one fat line)
    // both get spread. Kept below the spread spacing so nudged lanes never re-cluster.
    val spreadTolerance = spacing * 0.75
    // Follow-up passes only repair pairs the first spread happened to land almost on
    // top of each other. The tight tolerance cannot re-chain lanes a packed corridor
    // deliberately spaced wider than that, so repairs stay local instead of
    // reshuffling the whole picture.
    val repairTolerance = NUDGE_CLEARANCE + 0.5

    fun corridorClusters(horizontal: Boolean, laneTolerance: Double): List<List<LaneSegment>> = buildList {
        // First group by lane coordinate (chained, so a pair never straddles a band
        // boundary), then split each band into connected components of actually
        // overlapping spans. Sorting by coordinate alone interleaves segments from
        // unrelated corridors across the diagram, and a single reach-based pass over
        // that order used to chain them into one giant cluster whose spread collapsed
        // to sub-pixel lanes.
        val segments = laneSegments(horizontal).sortedBy { it.coordinate }
        var bandStart = 0
        while (bandStart < segments.size) {
            var bandEnd = bandStart + 1
            while (bandEnd < segments.size &&
                segments[bandEnd].coordinate - segments[bandEnd - 1].coordinate < laneTolerance
            ) {
                bandEnd++
            }
            val band = segments.subList(bandStart, bandEnd).sortedBy { it.spanStart }
            var clusterStart = 0
            while (clusterStart < band.size) {
                var clusterEnd = clusterStart + 1
                var reach = band[clusterStart].spanEnd
                while (clusterEnd < band.size && band[clusterEnd].spanStart < reach - GEOMETRY_EPSILON) {
                    reach = maxOf(reach, band[clusterEnd].spanEnd)
                    clusterEnd++
                }
                add(band.subList(clusterStart, clusterEnd))
                clusterStart = clusterEnd
            }
            bandStart = bandEnd
        }
    }

    fun applyOffsets(horizontal: Boolean, laneTolerance: Double): Boolean {
        var moved = false
        for (cluster in corridorClusters(horizontal, laneTolerance)) {
            if (cluster.map { it.route }.distinct().size >= 2 && cluster.any { it.movable }) {
                val ordered = cluster.sortedWith(
                    compareBy({ it.continuation }, { routes[it.route].edgeId.value }, { it.index }),
                )
                val laneAssignments: List<Pair<LaneSegment, Double>>
                val fixedIndices = ordered.indices.filter { !ordered[it].movable }
                if (fixedIndices.isEmpty()) {
                    laneAssignments = ordered.mapIndexed { slot, segment ->
                        segment to (slot - (ordered.size - 1) / 2.0)
                    }
                } else {
                    // Lane zero belongs to every endpoint/manual segment that cannot move.
                    // Movable runs keep their continuation order on either side of that
                    // baseline, which avoids introducing a new crossing at the adjacent legs.
                    val pivot = fixedIndices.average()
                    val lower = ordered.withIndex()
                        .filter { (index, segment) -> segment.movable && index < pivot }
                    val upper = ordered.withIndex()
                        .filter { (index, segment) -> segment.movable && index >= pivot }
                    laneAssignments = buildList {
                        lower.forEachIndexed { slot, indexed ->
                            add(indexed.value to (slot - lower.size).toDouble())
                        }
                        upper.forEachIndexed { slot, indexed ->
                            add(indexed.value to (slot + 1).toDouble())
                        }
                    }
                }
                // Cap the spread so the outermost offset stays inside the routing margin
                // (grid corridors run at most obstacleMargin from a node face).
                val maxOffset = (options.obstacleMargin - NUDGE_CLEARANCE).coerceAtLeast(0.0)
                val furthestLane = laneAssignments.maxOf { abs(it.second) }
                val clusterSpacing = minOf(spacing, maxOffset / furthestLane)
                if (clusterSpacing < GEOMETRY_EPSILON) continue
                for ((segment, lane) in laneAssignments) {
                    val offset = lane * clusterSpacing
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
                    // clear of (raw-bounds fallback legs may already touch one) — and a
                    // boundary-hugging segment must never be pushed into the interior.
                    val pushedIntoNode = obstacles.any { rect ->
                        (
                            segmentNearRect(shiftedA, shiftedB, rect, NUDGE_CLEARANCE) &&
                                !segmentNearRect(a, b, rect, NUDGE_CLEARANCE)
                            ) || (
                            segmentNearRect(shiftedA, shiftedB, rect, 0.0) &&
                                !segmentNearRect(a, b, rect, 0.0)
                            )
                    }
                    if (pushedIntoNode) continue
                    pts[segment.index] = shiftedA
                    pts[segment.index + 1] = shiftedB
                    moved = true
                }
            }
        }
        return moved
    }

    // Separating two corridors can land their segments almost on top of a third one,
    // so after the full spread run bounded repair passes over the fresh co-runs.
    applyOffsets(horizontal = true, spreadTolerance)
    applyOffsets(horizontal = false, spreadTolerance)
    for (pass in 1 until NUDGE_PASSES) {
        val movedHorizontal = applyOffsets(horizontal = true, repairTolerance)
        val movedVertical = applyOffsets(horizontal = false, repairTolerance)
        if (!movedHorizontal && !movedVertical) break
    }
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
