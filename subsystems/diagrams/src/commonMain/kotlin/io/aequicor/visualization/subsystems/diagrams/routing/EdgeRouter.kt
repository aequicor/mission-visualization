package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.arrows.arrowheadExtent
import io.aequicor.visualization.subsystems.diagrams.arrows.resolvedArrowheads
import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.geometry.anchorPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.exitsHorizontally
import io.aequicor.visualization.subsystems.diagrams.geometry.minus
import io.aequicor.visualization.subsystems.diagrams.geometry.nearlyEquals
import io.aequicor.visualization.subsystems.diagrams.geometry.outwardNormal
import io.aequicor.visualization.subsystems.diagrams.geometry.outlineSideIntersection
import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterIntersection
import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterSide
import io.aequicor.visualization.subsystems.diagrams.geometry.plus
import io.aequicor.visualization.subsystems.diagrams.geometry.times
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Routes one edge of [graph] into a concrete [RoutedEdge] polyline/spline.
 *
 * Endpoint resolution:
 * - [DiagramEndpoint.FixedPort] — exact visual port position (route glued to the outline);
 * - [DiagramEndpoint.FloatingAnchor] — the router picks the exit point on the node's
 *   attachment perimeter (see `perimeterIntersection`) facing the next route target;
 * - [DiagramEndpoint.FreePoint] — the point itself.
 *
 * Manual [DiagramEdge.waypoints] are mandatory pass-through points on top of the
 * automatic routing for every style.
 */
fun routeEdge(
    graph: DiagramGraph,
    edge: DiagramEdge,
    options: RoutingOptions = RoutingOptions.Default,
): RoutedEdge = routeEdgeConsidering(graph, edge, options, crossings = null)

/**
 * [routeEdge] with awareness of the edges the batch router has already placed: the grid
 * A* charges [RoutingOptions.crossingPenalty] per line of [crossings] a step would cross.
 * `null` (every single-edge caller) routes crossing-blind, exactly as before.
 */
internal fun routeEdgeConsidering(
    graph: DiagramGraph,
    edge: DiagramEdge,
    options: RoutingOptions,
    crossings: RouteCrossingIndex?,
): RoutedEdge = when (edge.routing) {
    DiagramRoutingStyle.STRAIGHT -> routeThroughPoints(graph, edge, curve = false)
    // CURVED is a smoothed obstacle-aware route (draw.io semantics): the orthogonal
    // pipeline plans anchors and dodges nodes, the renderer splines through the bends.
    DiagramRoutingStyle.CURVED -> routeOrthogonal(graph, edge, options, avoidObstacles = true, crossings)
    DiagramRoutingStyle.ORTHOGONAL -> routeOrthogonal(graph, edge, options, avoidObstacles = true, crossings)
    DiagramRoutingStyle.SIMPLE -> routeOrthogonal(graph, edge, options, avoidObstacles = false, crossings)
    DiagramRoutingStyle.ISOMETRIC -> routeIsometric(graph, edge)
    // ER shares the full obstacle-aware orthogonal pipeline: grid A*, joint anchor
    // planning, and per-side anchor spreading.
    DiagramRoutingStyle.ENTITY_RELATION -> routeOrthogonal(graph, edge, options, avoidObstacles = true, crossings)
}

/**
 * Routes every edge of the graph (input for line-jump computation and rendering) and
 * separates co-running collinear segments of different edges ([nudgeRoutedEdges]).
 *
 * Crossing awareness runs in two passes: a first pass routes in graph order, each edge
 * seeing the routes placed before it, then every edge re-routes against ALL other
 * first-pass routes — so whether a long leg dodges a dense bundle does not depend on
 * where its edge happens to sit in the graph. Each pass charges
 * [RoutingOptions.crossingPenalty] per line a step would cut.
 *
 * Sequence-diagram messages ([sequenceMessageRoutes]) are laid out top-to-bottom as
 * horizontal rows instead of going through the generic per-edge router.
 */
fun routeAllEdges(
    graph: DiagramGraph,
    options: RoutingOptions = RoutingOptions.Default,
): List<RoutedEdge> {
    val sequence = sequenceMessageRoutes(graph)
    val routes = crossingAwareRoutes(graph, options, sequence) { edge, crossings ->
        routeEdgeConsidering(graph, edge, options, crossings)
    }.map { requireNotNull(it) }
    val relaxed = slideEndJogs(graph, routes, options, sequence.keys)
    return nudgeRoutedEdges(
        routes = relaxed,
        options = options,
        pinnedEdgeIds = sequence.keys,
        obstacles = nodeObstacles(graph),
        waypointsByEdge = authoredWaypoints(graph),
        markerObstaclesByEdge = markerRectsByEdge(graph, relaxed),
    )
}

/**
 * The two-pass crossing-aware batch: results align with `graph.edges` (an entry is null
 * only when [routeOne] returned null — the lenient caller's failed edges). With the
 * awareness off this is the plain single pass.
 *
 * A closing regret pass keeps the awareness honest. In the second (Jacobi) pass every
 * edge dodges the FIRST-pass positions of the others — positions the others may have
 * left in their own second pass, so a detour can outlive its reason: a long U-loop that
 * no longer saves a single real crossing. The regret pass re-scores each edge's
 * candidates (second-pass, first-pass, crossing-blind) against the second-pass routes of
 * all OTHER edges — the truest deterministic snapshot available — by the router's own
 * objective (length + turns x turnPenalty + real crossings x crossingPenalty) and keeps
 * the cheapest, ties going to the straighter earlier candidate. A detour survives only
 * while it actually pays for itself.
 */
internal fun crossingAwareRoutes(
    graph: DiagramGraph,
    options: RoutingOptions,
    sequence: Map<DiagramEdgeId, RoutedEdge>,
    routeOne: (DiagramEdge, RouteCrossingIndex?) -> RoutedEdge?,
): List<RoutedEdge?> {
    val placed = mutableListOf<RoutedEdge>().apply { addAll(sequence.values) }
    val first = graph.edges.map { edge ->
        sequence[edge.id] ?: routeOne(edge, crossingIndexOf(graph, placed, options))?.also { placed += it }
    }
    if (options.crossingPenalty <= 0.0) return first
    val second = graph.edges.mapIndexed { index, edge ->
        sequence[edge.id] ?: run {
            val others = first.mapIndexedNotNull { j, routed -> routed.takeIf { j != index } }
            val crossings = if (others.isEmpty()) null else crossingIndexOf(graph, others, options)
            routeOne(edge, crossings)
        }
    }
    return graph.edges.mapIndexed { index, edge ->
        if (sequence[edge.id] != null) return@mapIndexed second[index]
        val aware = second[index] ?: return@mapIndexed null
        val others = second.mapIndexedNotNull { j, routed -> routed.takeIf { j != index } }
        if (others.isEmpty()) return@mapIndexed aware
        val snapshot = RouteCrossingIndex.of(
            others.map { it.points },
            markerRectsByEdge(graph, others).values.flatten(),
        )
        // Priority order on ties: the blind route (shortest by construction), then the
        // first-pass route, then the second-pass one — a detour must strictly earn its keep.
        val candidates = buildList {
            routeOne(edge, null)?.let(::add)
            first[index]?.let(::add)
            add(aware)
        }
        var best = candidates.first()
        var bestScore = routeScore(best.points, snapshot, options)
        for (candidate in candidates.drop(1)) {
            if (candidate.points == best.points) continue
            val score = routeScore(candidate.points, snapshot, options)
            if (score < bestScore - GEOMETRY_EPSILON) {
                best = candidate
                bestScore = score
            }
        }
        best
    }
}

/**
 * The batch router's own objective applied to a finished polyline: total length, plus
 * [RoutingOptions.turnPenalty] per bend, plus [RoutingOptions.crossingPenalty] per real
 * crossing with the [crossings] snapshot. Used by the regret pass to compare candidate
 * routes on equal terms.
 */
private fun routeScore(
    points: List<DiagramPoint>,
    crossings: RouteCrossingIndex,
    options: RoutingOptions,
): Double {
    var length = 0.0
    var turns = 0
    var crossed = 0
    var markerCuts = 0
    var previousHorizontal: Boolean? = null
    for ((a, b) in points.zipWithNext()) {
        val dx = abs(a.x - b.x)
        val dy = abs(a.y - b.y)
        if (dx < GEOMETRY_EPSILON && dy < GEOMETRY_EPSILON) continue
        length += dx + dy
        val horizontal = dx >= dy
        if (previousHorizontal != null && horizontal != previousHorizontal) turns++
        previousHorizontal = horizontal
        crossed += if (horizontal) {
            crossings.crossingsOfHorizontal(a.y, minOf(a.x, b.x), maxOf(a.x, b.x))
        } else {
            crossings.crossingsOfVertical(a.x, minOf(a.y, b.y), maxOf(a.y, b.y))
        }
        markerCuts += if (horizontal) {
            crossings.markerCutsOfHorizontal(a.y, minOf(a.x, b.x), maxOf(a.x, b.x))
        } else {
            crossings.markerCutsOfVertical(a.x, minOf(a.y, b.y), maxOf(a.y, b.y))
        }
    }
    return length + turns * options.turnPenalty + crossed * options.crossingPenalty +
        markerCuts * options.crossingPenalty * MARKER_CUT_PENALTY_FACTOR
}

/**
 * Crossing index over the routes placed so far — segment lanes plus their endpoint-marker
 * boxes — or `null` when the awareness is off.
 */
internal fun crossingIndexOf(
    graph: DiagramGraph,
    placed: List<RoutedEdge>,
    options: RoutingOptions,
): RouteCrossingIndex? {
    if (options.crossingPenalty <= 0.0 || placed.isEmpty()) return null
    return RouteCrossingIndex.of(
        placed.map { it.points },
        markerRectsByEdge(graph, placed).values.flatten(),
    )
}

// --- Endpoint resolution -------------------------------------------------------------

private data class ResolvedEnd(
    val anchor: DiagramPoint,
    val node: DiagramNode?,
    val side: DiagramNodeSide?,
)

private fun endpointNode(graph: DiagramGraph, endpoint: DiagramEndpoint): DiagramNode? =
    when (endpoint) {
        is DiagramEndpoint.FreePoint -> null
        is DiagramEndpoint.FloatingAnchor -> requireNotNull(graph.nodeById(endpoint.nodeId)) {
            "edge endpoint references missing node ${endpoint.nodeId.value}"
        }

        is DiagramEndpoint.FixedPort -> requireNotNull(graph.nodeById(endpoint.nodeId)) {
            "edge endpoint references missing node ${endpoint.nodeId.value}"
        }
    }

/** Coarse endpoint location before perimeter resolution: port point, node center, or free point. */
private fun rawEndpointPoint(graph: DiagramGraph, endpoint: DiagramEndpoint): DiagramPoint =
    when (endpoint) {
        is DiagramEndpoint.FreePoint -> DiagramPoint(endpoint.x, endpoint.y)
        is DiagramEndpoint.FloatingAnchor -> endpointNode(graph, endpoint)!!.bounds.center
        is DiagramEndpoint.FixedPort -> {
            val node = endpointNode(graph, endpoint)!!
            val port = requireNotNull(node.portById(endpoint.portId)) {
                "node ${node.id.value} has no port ${endpoint.portId.value}"
            }
            anchorPoint(node, port)
        }
    }

/** Endpoint resolution for point-to-point styles (straight/curved/isometric). */
private fun resolvePointEnd(
    graph: DiagramGraph,
    endpoint: DiagramEndpoint,
    reference: DiagramPoint,
): ResolvedEnd = when (endpoint) {
    is DiagramEndpoint.FreePoint ->
        ResolvedEnd(DiagramPoint(endpoint.x, endpoint.y), node = null, side = null)

    is DiagramEndpoint.FixedPort -> {
        val node = endpointNode(graph, endpoint)!!
        val anchor = rawEndpointPoint(graph, endpoint)
        ResolvedEnd(anchor, node, perimeterSide(node, anchor))
    }

    is DiagramEndpoint.FloatingAnchor -> {
        val node = endpointNode(graph, endpoint)!!
        val anchor = perimeterIntersection(node, reference)
        ResolvedEnd(anchor, node, perimeterSide(node, anchor))
    }
}

/** Endpoint resolution for orthogonal styles: anchor on the rendered outline + logical exit side. */
private fun resolveOrthogonalEnd(
    graph: DiagramGraph,
    endpoint: DiagramEndpoint,
    reference: DiagramPoint,
    options: RoutingOptions,
): ResolvedEnd = when (endpoint) {
    is DiagramEndpoint.FreePoint ->
        ResolvedEnd(DiagramPoint(endpoint.x, endpoint.y), node = null, side = null)

    is DiagramEndpoint.FixedPort -> {
        val node = endpointNode(graph, endpoint)!!
        val port = requireNotNull(node.portById(endpoint.portId)) {
            "node ${node.id.value} has no port ${endpoint.portId.value}"
        }
        val anchor = anchorPoint(node, port)
        val side = (port.anchor as? DiagramPortAnchor.SideOffset)?.side
            ?: perimeterSide(node, anchor)
        ResolvedEnd(anchor, node, side)
    }

    is DiagramEndpoint.FloatingAnchor -> {
        val node = endpointNode(graph, endpoint)!!
        val bounds = node.bounds
        val side = dominantSide(bounds.center, reference)
        val (low, high) = sideCoordinateRange(bounds, side, options.obstacleMargin)
        val anchor = anchorOnSide(
            node,
            side,
            (if (side.exitsHorizontally) reference.y else reference.x).coerceIn(low, high),
        )
        ResolvedEnd(anchor, node, side)
    }
}

private fun dominantSide(center: DiagramPoint, reference: DiagramPoint): DiagramNodeSide {
    val dx = reference.x - center.x
    val dy = reference.y - center.y
    return if (abs(dx) >= abs(dy)) {
        if (dx >= 0.0) DiagramNodeSide.RIGHT else DiagramNodeSide.LEFT
    } else {
        if (dy >= 0.0) DiagramNodeSide.BOTTOM else DiagramNodeSide.TOP
    }
}

// --- Straight / curved ---------------------------------------------------------------

private fun routeThroughPoints(graph: DiagramGraph, edge: DiagramEdge, curve: Boolean): RoutedEdge {
    val sourceRaw = rawEndpointPoint(graph, edge.source)
    val targetRaw = rawEndpointPoint(graph, edge.target)
    val source = resolvePointEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw)
    val target = resolvePointEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw)
    val points = listOf(source.anchor) + edge.waypoints + listOf(target.anchor)
    val cleaned = if (curve) dedupePoints(points) else simplifyPolyline(points)
    return RoutedEdge(edge.id, edge.routing, atLeastTwo(cleaned), source.side, target.side)
}

// --- Isometric -----------------------------------------------------------------------

private const val ISOMETRIC_STEP_DEGREES = 30.0

private fun routeIsometric(graph: DiagramGraph, edge: DiagramEdge): RoutedEdge {
    val sourceRaw = rawEndpointPoint(graph, edge.source)
    val targetRaw = rawEndpointPoint(graph, edge.target)
    val source = resolvePointEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw)
    val target = resolvePointEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw)
    val through = listOf(source.anchor) + edge.waypoints + listOf(target.anchor)
    val points = mutableListOf(through.first())
    for ((from, to) in through.zipWithNext()) {
        points += isometricLeg(from, to).drop(1)
    }
    return RoutedEdge(edge.id, edge.routing, atLeastTwo(simplifyPolyline(points)), source.side, target.side)
}

/** Connects two points with one or two segments whose angles are multiples of 30°. */
private fun isometricLeg(from: DiagramPoint, to: DiagramPoint): List<DiagramPoint> {
    val delta = to - from
    if (abs(delta.x) < GEOMETRY_EPSILON && abs(delta.y) < GEOMETRY_EPSILON) return listOf(from, to)
    val angle = atan2(delta.y, delta.x) * 180.0 / PI
    val remainder = angle - floor(angle / ISOMETRIC_STEP_DEGREES) * ISOMETRIC_STEP_DEGREES
    if (remainder < 1e-9 || ISOMETRIC_STEP_DEGREES - remainder < 1e-9) return listOf(from, to)
    val lower = floor(angle / ISOMETRIC_STEP_DEGREES) * ISOMETRIC_STEP_DEGREES
    val upper = lower + ISOMETRIC_STEP_DEGREES
    val u1 = directionVector(lower)
    val u2 = directionVector(upper)
    val det = u1.x * u2.y - u1.y * u2.x
    val s = (delta.x * u2.y - delta.y * u2.x) / det
    val bend = from + u1 * s
    return listOf(from, bend, to)
}

private fun directionVector(degrees: Double): DiagramPoint {
    val radians = degrees * PI / 180.0
    return DiagramPoint(cos(radians), sin(radians))
}

// --- Orthogonal / simple -------------------------------------------------------------

private fun routeOrthogonal(
    graph: DiagramGraph,
    edge: DiagramEdge,
    options: RoutingOptions,
    avoidObstacles: Boolean,
    crossings: RouteCrossingIndex? = null,
): RoutedEdge {
    val sourceRaw = rawEndpointPoint(graph, edge.source)
    val targetRaw = rawEndpointPoint(graph, edge.target)
    val planned = planFloatingEnds(graph, edge, options)
    val source = (
        planned?.source
            ?: resolveOrthogonalEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw, options)
        ).let { fannedPortEnd(graph, edge, atSource = true, end = it, options = options) }
    val target = (
        planned?.target
            ?: resolveOrthogonalEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw, options)
        ).let { fannedPortEnd(graph, edge, atSource = false, end = it, options = options) }
    val margin = options.obstacleMargin
    val foreignBounds = if (avoidObstacles) {
        graph.nodes
            .filter {
                it.visible && it.width > GEOMETRY_EPSILON && it.height > GEOMETRY_EPSILON &&
                    it.id != source.node?.id && it.id != target.node?.id
            }
            .map { it.bounds }
    } else {
        emptyList()
    }
    // The stub length must equal the inflation of the node it leaves: a stub ending off
    // that inflated-obstacle boundary — the one the grid A* travels along — would take a
    // corridor-hugging arrival (whose direction the goal state does not constrain) doubling
    // back over it as a visible whisker spur. So an end needing more room than the corridor
    // margin (a marker longer than the margin, see [endpointReach]) grows both together.
    val heads = resolvedArrowheads(edge)
    val sourceReach = endpointReach(heads.source, options)
    val targetReach = endpointReach(heads.target, options)
    val sourceStub = source.side?.let {
        source.anchor + it.outwardNormal() * stubLength(source.anchor, it, sourceReach, foreignBounds)
    } ?: source.anchor
    val targetStub = target.side?.let {
        target.anchor + it.outwardNormal() * stubLength(target.anchor, it, targetReach, foreignBounds)
    } ?: target.anchor
    val mandatory = alignedMandatoryPoints(
        sourceStub = sourceStub,
        sourceSide = source.side,
        waypoints = edge.waypoints,
        targetStub = targetStub,
        targetSide = target.side,
    )

    val obstacles = if (avoidObstacles) {
        graph.nodes
            .filter { it.visible && it.width > GEOMETRY_EPSILON && it.height > GEOMETRY_EPSILON }
            .map {
                // A node this edge attaches to is inflated to its own end's reach so
                // the stub still lands exactly on the boundary A* rides; every other
                // node keeps the shared corridor margin, leaving overall route density
                // untouched.
                val inflation = when (it.id) {
                    source.node?.id -> sourceReach
                    target.node?.id -> targetReach
                    else -> margin
                }
                it.bounds to inflate(it.bounds, inflation)
            }
    } else {
        emptyList()
    }
    val healedMandatory = healViasInsideObstacles(mandatory, obstacles)

    val innerPoints: List<DiagramPoint> = if (healedMandatory.size == 2 && !avoidObstacles) {
        connectOrthogonally(healedMandatory[0], healedMandatory[1], source.side, target.side)
    } else {
        val jogThreshold = margin * 2.0
        val points = mutableListOf(healedMandatory.first())
        var seedDirection = source.side?.toGridDirection()
        for ((from, to) in healedMandatory.zipWithNext()) {
            val leg = if (avoidObstacles) {
                val legObstacles = legObstacles(obstacles, from, to)
                if (axisAligned(from, to) && segmentClear(from, to, legObstacles)) {
                    listOf(from, to)
                } else {
                    orthogonalGridRoute(
                        from,
                        seedDirection,
                        to,
                        legObstacles,
                        options.turnPenalty,
                        crossings,
                        options.crossingPenalty,
                        options.crossingPenalty * MARKER_CUT_PENALTY_FACTOR,
                    )
                        ?.let { collapseJogs(it, legObstacles, jogThreshold) }
                        ?: manhattanLeg(from, to, seedDirection)
                }
            } else {
                manhattanLeg(from, to, seedDirection)
            }
            points += leg.drop(1)
            seedDirection = lastSegmentDirection(leg) ?: seedDirection
        }
        points
    }

    val points = listOf(source.anchor) + innerPoints + listOf(target.anchor)
    // A LONE authored via is an exact mandatory pass-through by contract — an
    // out-and-back antenna through it (a self-edge pulled out by one waypoint) is
    // intent, not a stale artifact, so its reversal must survive the cleanup.
    val protectedVias = if (edge.waypoints.size == 1) {
        healedMandatory.drop(1).dropLast(1)
    } else {
        emptyList()
    }
    return RoutedEdge(
        edgeId = edge.id,
        routing = edge.routing,
        points = atLeastTwo(cleanRoutePolyline(points, protectedVias)),
        sourceSide = source.side,
        targetSide = target.side,
        sourceReach = if (source.side == null) 0.0 else sourceReach,
        targetReach = if (target.side == null) 0.0 else targetReach,
    )
}

/**
 * Final polyline cleanup: spur collapse and loop excision iterated to a fixpoint —
 * excising a loop can expose a NEW 180° reversal at its seam (the arrival into the
 * revisited corner opposing the post-loop departure), which a single pass would leave
 * as exactly the whisker the cleanup exists to remove. Two safety valves: reversals at
 * (and loops through) [protectedVias] survive — a lone authored via is a deliberate
 * pass-through; and when the cleanup would degenerate a drawable route to (near)
 * nothing — a self-edge whose two ends resolve to one anchor — the original polyline
 * wins, because an invisible, unselectable edge is strictly worse than an ugly one.
 */
internal fun cleanRoutePolyline(
    points: List<DiagramPoint>,
    protectedVias: List<DiagramPoint> = emptyList(),
): List<DiagramPoint> {
    val deduped = dedupePoints(points)
    var current = deduped
    var rounds = 0
    while (rounds++ < MAX_CLEANUP_ROUNDS) {
        val next = collapseLoops(collapseSpurs(current, protectedVias), protectedVias)
        if (next == current) break
        current = next
    }
    val cleaned = simplifyPolyline(current)
    val degenerate = manhattanLength(cleaned) < 1.0 && manhattanLength(deduped) >= 1.0
    return if (degenerate) simplifyPolyline(deduped) else cleaned
}

/** Each cleanup round strictly removes points, so this bound is never reached in practice. */
private const val MAX_CLEANUP_ROUNDS = 16

private fun manhattanLength(points: List<DiagramPoint>): Double =
    points.zipWithNext().sumOf { (a, b) -> abs(a.x - b.x) + abs(a.y - b.y) }

// --- End-jog relaxation ------------------------------------------------------------------

/**
 * Slides a short transition jog glued to a route end into the middle of its corridor.
 * Two fixed anchors rarely share an axis exactly — a fan slot sits a few pixels off the
 * facing port row — so SOME jog between the outer runs must exist; the grid A*'s
 * deterministic tie-break tends to place it on the last grid column before the goal,
 * which draws an S-hook right at the endpoint marker. Draw.io puts that transition
 * mid-corridor, where it reads as a calm step instead.
 *
 * Batch-only (like nudging): the slide lengthens the jog's endpoint runs, and an
 * endpoint run is immovable downstream — landed on another edge's immovable segment it
 * would become an inseparable co-run (two sibling drops whose anchors the planner
 * deliberately spread [RoutingOptions.anchorSeparation] apart share exactly that
 * corridor). So a candidate is rejected unless its grown endpoint runs stay a nudge
 * lane away from every other route's immovable segments. Only end-adjacent jogs move
 * (an interior jog is usually a deliberate dodge), authored vias hold their corners,
 * the ends' marker reservations stay intact, and all three replacement segments must
 * stay clear of the route's obstacles.
 */
internal fun slideEndJogs(
    graph: DiagramGraph,
    routes: List<RoutedEdge>,
    options: RoutingOptions,
    pinnedEdgeIds: Set<DiagramEdgeId> = emptySet(),
): List<RoutedEdge> {
    val threshold = options.obstacleMargin * 2.0
    val laneSpacing = options.obstacleMargin * 2.0 / 3.0
    if (routes.isEmpty() || threshold <= 0.0) return routes
    val waypointsByEdge = authoredWaypoints(graph)
    val nodes = graph.nodes.filter {
        it.visible && it.width > GEOMETRY_EPSILON && it.height > GEOMETRY_EPSILON
    }

    data class Lane(
        val horizontal: Boolean,
        val at: Double,
        val from: Double,
        val to: Double,
        val immovable: Boolean,
    )

    fun lanesOf(route: RoutedEdge): List<Lane> = buildList {
        val vias = waypointsByEdge[route.edgeId].orEmpty()
        for (index in 0 until route.points.lastIndex) {
            val a = route.points[index]
            val b = route.points[index + 1]
            val horizontal = isHorizontalSegment(a, b)
            if (!horizontal && !isVerticalSegment(a, b)) continue
            val endpointRun = index == 0 || index + 1 == route.points.lastIndex
            val viaPinned = vias.any { pointNearSegment(it, a, b) }
            add(
                Lane(
                    horizontal = horizontal,
                    at = if (horizontal) a.y else a.x,
                    from = if (horizontal) minOf(a.x, b.x) else minOf(a.y, b.y),
                    to = if (horizontal) maxOf(a.x, b.x) else maxOf(a.y, b.y),
                    immovable = endpointRun || viaPinned,
                ),
            )
        }
    }

    fun collides(candidate: Lane, others: List<Lane>): Boolean = others.any { other ->
        other.immovable && other.horizontal == candidate.horizontal &&
            abs(other.at - candidate.at) < laneSpacing - GEOMETRY_EPSILON &&
            minOf(other.to, candidate.to) - maxOf(other.from, candidate.from) >= laneSpacing
    }

    val result = routes.toMutableList()
    for (routeIndex in result.indices) {
        val route = result[routeIndex]
        if (route.edgeId in pinnedEdgeIds || !route.routing.routesOrthogonally) continue
        val edge = graph.edgeById(route.edgeId) ?: continue
        if (route.points.size < 4) continue
        val vias = waypointsByEdge[route.edgeId].orEmpty()
        val obstacles = nodes.map { node ->
            val inflation = when (node.id) {
                edge.source.attachedNodeId -> route.sourceReach.coerceAtLeast(options.obstacleMargin)
                edge.target.attachedNodeId -> route.targetReach.coerceAtLeast(options.obstacleMargin)
                else -> options.obstacleMargin
            }
            node.bounds to inflate(node.bounds, inflation)
        }
        val otherLanes = result.indices
            .filter { it != routeIndex }
            .flatMap { lanesOf(result[it]) }
        val points = route.points.toMutableList()
        var changed = false
        for (index in 0..points.size - 4) {
            val atStart = index == 0
            val atEnd = index + 3 == points.lastIndex
            if (!atStart && !atEnd) continue
            val p0 = points[index]
            val p1 = points[index + 1]
            val p2 = points[index + 2]
            val p3 = points[index + 3]
            val horizontalJog = isVerticalSegment(p0, p1) && isHorizontalSegment(p1, p2) &&
                isVerticalSegment(p2, p3) && abs(p2.x - p1.x) <= threshold &&
                (p1.y - p0.y) * (p3.y - p2.y) > 0.0
            val verticalJog = isHorizontalSegment(p0, p1) && isVerticalSegment(p1, p2) &&
                isHorizontalSegment(p2, p3) && abs(p2.y - p1.y) <= threshold &&
                (p1.x - p0.x) * (p3.x - p2.x) > 0.0
            if (!horizontalJog && !verticalJog) continue
            // A via anywhere on the jog's three segments is an authored bend or a
            // pass-through merged into a straight run — the jog stays where it is.
            val viaHeld = vias.any { via ->
                pointNearSegment(via, p0, p1) || pointNearSegment(via, p1, p2) ||
                    pointNearSegment(via, p2, p3)
            }
            if (viaHeld) continue
            val outerFrom = if (verticalJog) p0.x else p0.y
            val outerTo = if (verticalJog) p3.x else p3.y
            val lowReserve = if (atStart) {
                maxOf(route.sourceReach, threshold / 2.0)
            } else {
                threshold / 2.0
            }
            val highReserve = if (atEnd) {
                maxOf(route.targetReach, threshold / 2.0)
            } else {
                threshold / 2.0
            }
            val low = minOf(outerFrom, outerTo) +
                (if (outerFrom <= outerTo) lowReserve else highReserve)
            val high = maxOf(outerFrom, outerTo) -
                (if (outerFrom <= outerTo) highReserve else lowReserve)
            if (low >= high) continue
            val ideal = ((outerFrom + outerTo) / 2.0).coerceIn(low, high)
            val (newP1, newP2) = if (verticalJog) {
                DiagramPoint(ideal, p1.y) to DiagramPoint(ideal, p2.y)
            } else {
                DiagramPoint(p1.x, ideal) to DiagramPoint(p2.x, ideal)
            }
            if (newP1.nearlyEquals(p1)) continue
            val clearAgainst = legObstacles(obstacles, p0, p3)
            val clear = segmentClear(p0, newP1, clearAgainst) &&
                segmentClear(newP1, newP2, clearAgainst) &&
                segmentClear(newP2, p3, clearAgainst)
            if (!clear) continue
            val grownRuns = listOf(p0 to newP1, newP2 to p3).map { (a, b) ->
                val horizontal = abs(a.y - b.y) < GEOMETRY_EPSILON
                Lane(
                    horizontal = horizontal,
                    at = if (horizontal) a.y else a.x,
                    from = if (horizontal) minOf(a.x, b.x) else minOf(a.y, b.y),
                    to = if (horizontal) maxOf(a.x, b.x) else maxOf(a.y, b.y),
                    immovable = true,
                )
            }
            if (grownRuns.any { collides(it, otherLanes) }) continue
            points[index + 1] = newP1
            points[index + 2] = newP2
            changed = true
        }
        if (changed) result[routeIndex] = route.copy(points = points.toList())
    }
    return result
}

/**
 * Straight run to reserve at a node-attached end: the corridor [RoutingOptions.obstacleMargin],
 * or as much as the end's marker needs when that is longer — its full extent plus a little
 * breathing room, so the marker's back edge does not land on the bend itself. Capped at
 * [RoutingOptions.endpointClearance]; ends with no marker (or one shorter than the margin)
 * are unaffected, which keeps plain and crow's-foot edges routed exactly as before.
 */
private fun endpointReach(head: DiagramArrowhead, options: RoutingOptions): Double {
    val needed = arrowheadExtent(head) + MARKER_BREATHING
    val cap = maxOf(options.obstacleMargin, options.endpointClearance)
    return needed.coerceIn(options.obstacleMargin, cap)
}

/** Gap kept between an endpoint marker's back edge and the route's first bend. */
private const val MARKER_BREATHING = 4.0

/**
 * A foreign lane through an endpoint-marker box, priced in crossings: slicing a crow's
 * foot or circle destroys the glyph outright, so it must cost more than crossing the
 * plain line ever does — the router detours or crosses elsewhere instead.
 */
private const val MARKER_CUT_PENALTY_FACTOR = 2.0

/**
 * Perpendicular exit-stub length at an anchor: [reach], shortened when a foreign node
 * sits closer than that so the stub ends on the node's near face instead of inside it
 * (which would force the router to drop that node as an obstacle and cut through it).
 * A shortened stub is the one case an endpoint marker cannot get its reserved run; the
 * renderer scales such a marker down to fit rather than let it cross the bend.
 */
private fun stubLength(
    anchor: DiagramPoint,
    side: DiagramNodeSide,
    reach: Double,
    foreignBounds: List<DiagramRect>,
): Double {
    var length = reach
    for (rect in foreignBounds) {
        val onAxis = if (side.exitsHorizontally) {
            anchor.y > rect.top + GEOMETRY_EPSILON && anchor.y < rect.bottom - GEOMETRY_EPSILON
        } else {
            anchor.x > rect.left + GEOMETRY_EPSILON && anchor.x < rect.right - GEOMETRY_EPSILON
        }
        if (!onAxis) continue
        val entry = when (side) {
            DiagramNodeSide.RIGHT -> rect.left - anchor.x
            DiagramNodeSide.LEFT -> anchor.x - rect.right
            DiagramNodeSide.BOTTOM -> rect.top - anchor.y
            DiagramNodeSide.TOP -> anchor.y - rect.bottom
        }
        if (entry >= 0.0 && entry < length) length = entry
    }
    return length
}

/**
 * Obstacles for one route leg. An inflated rectangle that strictly contains a leg
 * endpoint would make the leg unroutable, but dropping the node entirely lets the
 * route cut straight through it (the old behavior). Instead such an obstacle falls
 * back to the node's raw bounds, so the route may use the margin corridor but still
 * respects the node itself; only a node whose raw bounds swallow the endpoint
 * (degenerate overlap) is dropped.
 */
private fun legObstacles(
    obstacles: List<Pair<DiagramRect, DiagramRect>>,
    from: DiagramPoint,
    to: DiagramPoint,
): List<DiagramRect> = obstacles.mapNotNull { (bounds, inflated) ->
    when {
        !strictlyContains(inflated, from) && !strictlyContains(inflated, to) -> inflated
        strictlyContains(bounds, from) || strictlyContains(bounds, to) -> null
        else -> bounds
    }
}

// --- Same-port fan-out -----------------------------------------------------------------

/**
 * Fans out edges authored into one fixed port. Multiple edges on the same port (an ER
 * identifier row, say) would all leave or enter through the exact port point and read
 * as a single forked line; instead their anchors spread
 * [RoutingOptions.portFanSeparation] apart along the port's side, centered on the
 * authored port. Slots are ordered by the angle each route heads off at (first/last via,
 * else the other end), sweeping across the side's axis, so the fan does not cross itself
 * right at the port. A lone edge — and an edge not present in the graph, e.g. an
 * interactive preview — keeps the exact port point.
 *
 * A fan also keeps its lanes clear of ports facing it across the inter-node corridor
 * (see [coordinatedPortLanes]): two fans on facing sides at the same height would
 * otherwise put endpoint whiskers of different edges onto one lane, and nudging cannot
 * separate segments glued to an anchor.
 */
private fun fannedPortEnd(
    graph: DiagramGraph,
    edge: DiagramEdge,
    atSource: Boolean,
    end: ResolvedEnd,
    options: RoutingOptions,
): ResolvedEnd {
    if (options.portFanSeparation <= 0.0) return end
    val endpoint = if (atSource) edge.source else edge.target
    val port = endpoint as? DiagramEndpoint.FixedPort ?: return end
    val node = end.node ?: return end
    val side = end.side ?: return end
    val lanes = coordinatedPortLanes(graph, port, options, HashMap())
    if (lanes.size <= 1) return end
    val lane = lanes.firstOrNull { it.edgeId == edge.id && it.atSource == atSource } ?: return end
    return end.copy(anchor = anchorOnSide(node, side, lane.position))
}

/** Minimum distance kept between endpoint lanes of ports facing each other across a corridor. */
private const val CROSS_PORT_LANE_CLEARANCE = 8.0

/**
 * One endpoint lane of a fixed port: the edge end occupying it and its coordinate along
 * the side. [healRow] is the row of the end's authored via when [alignWaypointToPort]
 * currently heals it onto this lane — a fan shift must keep such a slot within
 * [PORT_LEG_ALIGNMENT_LIMIT] of the via, or the healing breaks and the edge's long leg
 * snaps back to the authored row, right where the facing lanes it was avoiding live.
 */
private data class PortLane(
    val edgeId: DiagramEdgeId,
    val atSource: Boolean,
    val position: Double,
    val healRow: Double? = null,
)

private data class ResolvedPort(
    val port: DiagramEndpoint.FixedPort,
    val node: DiagramNode,
    val side: DiagramNodeSide,
    val anchor: DiagramPoint,
)

private fun resolveFixedPort(
    graph: DiagramGraph,
    port: DiagramEndpoint.FixedPort,
): ResolvedPort? {
    val node = graph.nodeById(port.nodeId) ?: return null
    val nodePort = node.portById(port.portId) ?: return null
    val anchor = anchorPoint(node, nodePort)
    val side = (nodePort.anchor as? DiagramPortAnchor.SideOffset)?.side
        ?: perimeterSide(node, anchor)
    return ResolvedPort(port, node, side, anchor)
}

/**
 * The lanes a fixed port's edge ends actually occupy along its side: the authored point
 * for a lone edge, the spread fan for several — shifted as a whole when its lanes would
 * collide with those of a port facing it across the corridor. Which of two facing ports
 * moves is deterministic: a lone port never does (its authored point is exact user
 * intent), and of two fans the one with the larger `(nodeId, portId)` key yields.
 * Results are memoized in [cache]; recursion terminates because a fan only recurses
 * into strictly smaller keys and lone ports do not recurse at all.
 */
private fun coordinatedPortLanes(
    graph: DiagramGraph,
    port: DiagramEndpoint.FixedPort,
    options: RoutingOptions,
    cache: MutableMap<DiagramEndpoint.FixedPort, List<PortLane>>,
): List<PortLane> {
    cache[port]?.let { return it }
    val resolved = resolveFixedPort(graph, port)
        ?: return emptyList<PortLane>().also { cache[port] = it }

    data class Slot(
        val edgeId: DiagramEdgeId,
        val atSource: Boolean,
        val approachHeading: Double,
        val viaHeading: Double,
        val viaRow: Double?,
    )

    fun slotFor(candidate: DiagramEdge, atSource: Boolean) = Slot(
        edgeId = candidate.id,
        atSource = atSource,
        approachHeading = portHeading(
            graph, candidate, atSource, resolved.anchor, resolved.side, pastHealedVia = true,
        ),
        viaHeading = portHeading(
            graph, candidate, atSource, resolved.anchor, resolved.side, pastHealedVia = false,
        ),
        viaRow = adjacentViaRow(candidate, atSource, resolved.side),
    )

    val slots = mutableListOf<Slot>()
    for (candidate in graph.edges) {
        if (!candidate.routing.routesOrthogonally) continue
        if (candidate.source == port) slots += slotFor(candidate, atSource = true)
        if (candidate.target == port) slots += slotFor(candidate, atSource = false)
    }
    val base = if (resolved.side.exitsHorizontally) resolved.anchor.y else resolved.anchor.x
    if (slots.size <= 1) {
        val lanes = slots.map { PortLane(it.edgeId, it.atSource, base) }
        cache[port] = lanes
        return lanes
    }
    val (low, high) = sideCoordinateRange(resolved.node.bounds, resolved.side, options.obstacleMargin)

    fun assign(ordered: List<Slot>): Pair<List<PortLane>, Boolean> {
        val ideals = List(ordered.size) { slot ->
            (base + options.portFanSeparation * (slot - (ordered.size - 1) / 2.0)).coerceIn(low, high)
        }
        val (spread, healable) = healablePositions(
            spreadPositions(ideals, low, high, options.portFanSeparation),
            ordered.map { it.viaRow },
            low,
            high,
            options,
        )
        val lanes = ordered.mapIndexed { index, slot ->
            PortLane(
                edgeId = slot.edgeId,
                atSource = slot.atSource,
                position = spread[index],
                healRow = slot.viaRow?.takeIf {
                    abs(it - spread[index]) <= PORT_LEG_ALIGNMENT_LIMIT + GEOMETRY_EPSILON
                },
            )
        }
        return lanes to healable
    }

    // The true-approach order untangles the comb; when it hands vias slots their
    // healing windows cannot all reach (opposite-signed drift inside the band), the
    // via-based order — which matches sorted via rows to sorted slots by construction —
    // preserves the heals instead.
    val byApproach = assign(
        slots.sortedWith(compareBy({ it.approachHeading }, { it.edgeId.value }, { it.atSource })),
    )
    val fanned = if (byApproach.second) {
        byApproach.first
    } else {
        assign(
            slots.sortedWith(compareBy({ it.viaHeading }, { it.edgeId.value }, { it.atSource })),
        ).first
    }
    val occupied = occupiedPortLanes(graph, resolved, options, cache)
    val delta = fanAvoidanceDelta(fanned, occupied, low, high, options)
    val lanes = if (delta == 0.0) fanned else fanned.map { it.copy(position = it.position + delta) }
    cache[port] = lanes
    return lanes
}

/** An endpoint lane the fan must keep clear of: the edge occupying it and its coordinate. */
private data class OccupiedLane(val edgeId: DiagramEdgeId, val position: Double)

/**
 * Lanes that [resolved]'s fan must keep clear of: fixed ports facing it across the
 * corridor plus its neighbors on the same node side (their whiskers leave through the
 * same face, so a shifted fan may not land on their lanes either), and the planned
 * anchors of floating ends on those same faces — the floating planner knows nothing
 * about fans, so the fan is the one that dodges. Of the fixed ports only those that
 * hold their ground are collected: lone ports (they never move) and fans with a
 * smaller key. A contested fan with a larger key is skipped — it will dodge this
 * port's lanes instead.
 */
private fun occupiedPortLanes(
    graph: DiagramGraph,
    resolved: ResolvedPort,
    options: RoutingOptions,
    cache: MutableMap<DiagramEndpoint.FixedPort, List<PortLane>>,
): List<OccupiedLane> {
    val lanes = mutableListOf<OccupiedLane>()
    val seen = mutableSetOf<DiagramEndpoint.FixedPort>()
    for (candidate in graph.edges) {
        if (!candidate.routing.routesOrthogonally) continue
        for (endpoint in listOf(candidate.source, candidate.target)) {
            val other = endpoint as? DiagramEndpoint.FixedPort ?: continue
            if (other == resolved.port || !seen.add(other)) continue
            val otherResolved = resolveFixedPort(graph, other) ?: continue
            val sameFace = otherResolved.node.id == resolved.node.id &&
                otherResolved.side == resolved.side
            val facing = otherResolved.side == resolved.side.oppositeSide() &&
                facesAcrossCorridor(resolved.node, resolved.side, otherResolved.node)
            if (!sameFace && !facing) continue
            val otherYields = portEdgeEndCount(graph, other) > 1 && portKeyLess(resolved.port, other)
            if (otherYields) continue
            lanes += coordinatedPortLanes(graph, other, options, cache)
                .map { OccupiedLane(it.edgeId, it.position) }
        }
    }
    for (node in graph.nodes) {
        val sameNode = node.id == resolved.node.id
        if (!sameNode && !facesAcrossCorridor(resolved.node, resolved.side, node)) continue
        val side = if (sameNode) resolved.side else resolved.side.oppositeSide()
        lanes += sideAttachments(graph, options, node, side)
            .map { OccupiedLane(it.edgeId, it.position) }
    }
    return lanes
}

/**
 * Pulls the whole spread minimally so that every slot with an authored adjacent via
 * NEAR its lane lands inside that via's healing window ([PORT_LEG_ALIGNMENT_LIMIT]).
 * A slot assigned a hair outside the window (a five-slot fan spans exactly the limit)
 * breaks its healing, and the edge's long leg then runs down the authored row across
 * the rest of the fan. Only vias within one fan step beyond the window take part: a
 * genuinely distant via is a deliberate corner elsewhere on the route, and chasing it
 * would drag the whole fan away from its authored port. When the windows have no
 * common shift the spread is returned as is, flagged infeasible — the caller may try
 * a different slot order.
 */
private fun healablePositions(
    spread: List<Double>,
    viaRows: List<Double?>,
    low: Double,
    high: Double,
    options: RoutingOptions,
): Pair<List<Double>, Boolean> {
    if (spread.isEmpty()) return spread to true
    val nearLimit = PORT_LEG_ALIGNMENT_LIMIT + options.portFanSeparation
    var lo = low - spread.first()
    var hi = high - spread.last()
    for (index in spread.indices) {
        val viaRow = viaRows[index] ?: continue
        if (abs(viaRow - spread[index]) > nearLimit) continue
        lo = maxOf(lo, viaRow - PORT_LEG_ALIGNMENT_LIMIT - spread[index])
        hi = minOf(hi, viaRow + PORT_LEG_ALIGNMENT_LIMIT - spread[index])
    }
    if (lo > hi) return spread to false
    val shift = 0.0.coerceIn(lo, hi)
    return (if (shift == 0.0) spread else spread.map { it + shift }) to true
}

private fun portEdgeEndCount(graph: DiagramGraph, port: DiagramEndpoint.FixedPort): Int {
    var count = 0
    for (candidate in graph.edges) {
        if (!candidate.routing.routesOrthogonally) continue
        if (candidate.source == port) count++
        if (candidate.target == port) count++
    }
    return count
}

private fun portKeyLess(a: DiagramEndpoint.FixedPort, b: DiagramEndpoint.FixedPort): Boolean =
    compareValuesBy(a, b, { it.nodeId.value }, { it.portId.value }) < 0

/**
 * Row (along the side's lane axis) of the authored via adjacent to this edge end —
 * the one [alignWaypointToPort] may heal onto the port lane. `null` when the healing
 * never touches this edge's waypoints: none at all, or a lone waypoint, which stays an
 * exact mandatory pass-through point (see [alignedMandatoryPoints]) whatever the lane.
 */
private fun adjacentViaRow(
    edge: DiagramEdge,
    atSource: Boolean,
    side: DiagramNodeSide,
): Double? {
    if (edge.waypoints.size < 2) return null
    val via = if (atSource) edge.waypoints.first() else edge.waypoints.last()
    return if (side.exitsHorizontally) via.y else via.x
}

/** Whether [other]'s opposing face looks back at [node]'s [side] from across the corridor. */
private fun facesAcrossCorridor(
    node: DiagramNode,
    side: DiagramNodeSide,
    other: DiagramNode,
): Boolean {
    if (other.id == node.id) return false
    return when (side) {
        DiagramNodeSide.RIGHT -> other.bounds.left >= node.bounds.right - GEOMETRY_EPSILON
        DiagramNodeSide.LEFT -> other.bounds.right <= node.bounds.left + GEOMETRY_EPSILON
        DiagramNodeSide.BOTTOM -> other.bounds.top >= node.bounds.bottom - GEOMETRY_EPSILON
        DiagramNodeSide.TOP -> other.bounds.bottom <= node.bounds.top + GEOMETRY_EPSILON
    }
}

private fun DiagramNodeSide.oppositeSide(): DiagramNodeSide = when (this) {
    DiagramNodeSide.LEFT -> DiagramNodeSide.RIGHT
    DiagramNodeSide.RIGHT -> DiagramNodeSide.LEFT
    DiagramNodeSide.TOP -> DiagramNodeSide.BOTTOM
    DiagramNodeSide.BOTTOM -> DiagramNodeSide.TOP
}

/**
 * Shift applied to a whole fan so every lane stays [CROSS_PORT_LANE_CLEARANCE] away from
 * every [occupied] lane of a different edge. Lanes of the same edge never conflict — an
 * edge connecting the two facing ports may legitimately run straight through both.
 * Candidates place some fan lane exactly one clearance off some occupied lane; the
 * smallest shift that clears all conflicts inside `[low, high]` wins, and no shift may
 * exceed twice [RoutingOptions.portFanSeparation] — the fan must stay recognizably
 * centered near its authored port. A slot whose authored via is currently healed onto
 * it ([PortLane.healRow]) must additionally stay within [PORT_LEG_ALIGNMENT_LIMIT] of
 * that via, or the healing would break and the edge's long leg would snap back to the
 * authored row. When the full clearance cannot be met it is halved once — the floor
 * stays above the lint co-run threshold, a tighter fit could not silence a warning
 * anyway — before giving up and leaving the fan where it was.
 */
private fun fanAvoidanceDelta(
    lanes: List<PortLane>,
    occupied: List<OccupiedLane>,
    low: Double,
    high: Double,
    options: RoutingOptions,
): Double {
    if (occupied.isEmpty()) return 0.0
    val maxShift = options.portFanSeparation * 2.0
    var clearance = CROSS_PORT_LANE_CLEARANCE
    while (clearance >= CROSS_PORT_LANE_CLEARANCE / 2.0 - GEOMETRY_EPSILON) {
        val candidates = buildList {
            add(0.0)
            for (lane in lanes) {
                for (occ in occupied) {
                    if (occ.edgeId == lane.edgeId) continue
                    add(occ.position + clearance - lane.position)
                    add(occ.position - clearance - lane.position)
                }
            }
        }.sortedWith(compareBy({ abs(it) }, { it }))
        for (delta in candidates) {
            if (abs(delta) > maxShift + GEOMETRY_EPSILON) continue
            val inRange = lanes.all {
                val position = it.position + delta
                position >= low - GEOMETRY_EPSILON &&
                    position <= high + GEOMETRY_EPSILON &&
                    (
                        it.healRow == null ||
                            abs(it.healRow - position) <= PORT_LEG_ALIGNMENT_LIMIT + GEOMETRY_EPSILON
                        )
            }
            if (!inRange) continue
            val clear = lanes.all { lane ->
                occupied.all { occ ->
                    occ.edgeId == lane.edgeId ||
                        abs(lane.position + delta - occ.position) >= clearance - GEOMETRY_EPSILON
                }
            }
            if (clear) return delta
        }
        clearance /= 2.0
    }
    return 0.0
}

/**
 * Where [edge]'s end at this port heads off to, as an angle over the port's side: 0 is
 * straight out along the outward normal, negative leans toward the side's low corner
 * (top/left), positive toward the high corner. Sorting a port's slots by this angle
 * lays the fan out in the same order the routes diverge.
 *
 * With [pastHealedVia] the reference looks one point past an adjacent via that
 * [alignWaypointToPort] will heal onto the lane (two or more waypoints, within
 * [PORT_LEG_ALIGNMENT_LIMIT] of the row): authored corridors are drawn INTO the port
 * row, so every fan member's adjacent via carries no ordering information — the point
 * behind it tells where the corridor truly comes from (a riser from above should take
 * a top slot, one from below a bottom slot, or the two cross every lane between their
 * slots). A via the healing never moves — a lone exact pass-through, or one authored
 * off the row — stays the reference: the route really bends there, and ordering by
 * anything farther reintroduces port-face crossings.
 */
private fun portHeading(
    graph: DiagramGraph,
    edge: DiagramEdge,
    atSource: Boolean,
    anchor: DiagramPoint,
    side: DiagramNodeSide,
    pastHealedVia: Boolean,
): Double {
    val chain = if (atSource) {
        edge.waypoints + rawEndpointPoint(graph, edge.target)
    } else {
        edge.waypoints.asReversed() + rawEndpointPoint(graph, edge.source)
    }
    fun along(point: DiagramPoint): Double =
        if (side.exitsHorizontally) point.y - anchor.y else point.x - anchor.x
    val reference = if (
        pastHealedVia && edge.waypoints.size >= 2 &&
        abs(along(chain.first())) <= PORT_LEG_ALIGNMENT_LIMIT
    ) {
        chain[1]
    } else {
        chain.first()
    }
    val delta = reference - anchor
    val normal = side.outwardNormal()
    val outward = delta.x * normal.x + delta.y * normal.y
    return atan2(along(reference), outward)
}

// --- Floating-pair anchor planning ----------------------------------------------------

private data class PlannedEnds(val source: ResolvedEnd?, val target: ResolvedEnd?)

/**
 * Joint anchor planning for orthogonal-family edges with floating anchors and no manual
 * waypoints: instead of resolving each floating end against the other's center (which
 * lands anchors on node corners and picks sides facing adjacent nodes), the exit side is
 * chosen across the wider inter-node gap, anchors sit on the midpoint of the nodes'
 * projection overlap (a straight segment whenever the corridor allows), and all planned
 * attachments sharing a node side are spread apart so no two edges leave or enter
 * through the same point. An edge with one floating end (the other on a fixed port)
 * gets a plan for the floating end only. Returns `null` when the plan does not apply
 * (waypoints, free points, self-loops, or bounds overlapping on both axes) — callers
 * fall back to per-end resolution for any unplanned end.
 */
private fun planFloatingEnds(
    graph: DiagramGraph,
    edge: DiagramEdge,
    options: RoutingOptions,
): PlannedEnds? {
    if (edge.waypoints.isNotEmpty()) return null
    val sourceNode = edge.source.attachedNodeId?.let(graph::nodeById)
    val targetNode = edge.target.attachedNodeId?.let(graph::nodeById)
    if (sourceNode == null || targetNode == null || sourceNode.id == targetNode.id) return null
    val sides = facingSides(sourceNode.bounds, targetNode.bounds) ?: return null
    val source = (edge.source as? DiagramEndpoint.FloatingAnchor)?.let {
        ResolvedEnd(
            plannedAnchor(graph, options, sourceNode, sides.sourceSide, targetNode, edge.id),
            sourceNode,
            sides.sourceSide,
        )
    }
    val target = (edge.target as? DiagramEndpoint.FloatingAnchor)?.let {
        ResolvedEnd(
            plannedAnchor(graph, options, targetNode, sides.targetSide, sourceNode, edge.id),
            targetNode,
            sides.targetSide,
        )
    }
    if (source == null && target == null) return null
    return PlannedEnds(source, target)
}

private data class FacingSides(val sourceSide: DiagramNodeSide, val targetSide: DiagramNodeSide)

/**
 * Opposite facing sides of two disjoint bounds across the wider gap (vertical wins ties),
 * or `null` when the bounds overlap on both axes.
 */
private fun facingSides(source: DiagramRect, target: DiagramRect): FacingSides? {
    val verticalGap = when {
        target.top >= source.bottom -> target.top - source.bottom
        source.top >= target.bottom -> source.top - target.bottom
        else -> -1.0
    }
    val horizontalGap = when {
        target.left >= source.right -> target.left - source.right
        source.left >= target.right -> source.left - target.right
        else -> -1.0
    }
    return when {
        verticalGap >= 0.0 && verticalGap >= horizontalGap ->
            if (target.top >= source.bottom) {
                FacingSides(DiagramNodeSide.BOTTOM, DiagramNodeSide.TOP)
            } else {
                FacingSides(DiagramNodeSide.TOP, DiagramNodeSide.BOTTOM)
            }

        horizontalGap >= 0.0 ->
            if (target.left >= source.right) {
                FacingSides(DiagramNodeSide.RIGHT, DiagramNodeSide.LEFT)
            } else {
                FacingSides(DiagramNodeSide.LEFT, DiagramNodeSide.RIGHT)
            }

        else -> null
    }
}

/**
 * Final anchor of [edgeId] on [side] of [node]: this edge's slot in the deterministic
 * per-side attachment layout (see [sideAttachments]). An edge not contained in the
 * graph (e.g. an interactive preview routed before insertion) has no slot and gets its
 * pairwise ideal coordinate toward [other] directly.
 */
private fun plannedAnchor(
    graph: DiagramGraph,
    options: RoutingOptions,
    node: DiagramNode,
    side: DiagramNodeSide,
    other: DiagramNode,
    edgeId: DiagramEdgeId,
): DiagramPoint {
    val coordinate = sideAttachments(graph, options, node, side)
        .firstOrNull { it.edgeId == edgeId }
        ?.position
        ?: run {
            val (low, high) = sideCoordinateRange(node.bounds, side, options.obstacleMargin)
            idealSideCoordinate(node.bounds, other.bounds, side, low, high)
        }
    return anchorOnSide(node, side, coordinate)
}

private data class SideAttachment(val edgeId: DiagramEdgeId, val position: Double)

/**
 * Deterministic attachment layout of one node side: every plannable edge of the graph
 * attaching to `(node, side)` through a floating end gets its ideal coordinate
 * (projection-overlap midpoint, else the other node's center, clamped inside the side
 * with a corner inset), then the sorted ideals are spread to at least
 * [RoutingOptions.anchorSeparation] apart. Edges whose other end sits on a fixed port
 * take part too — their floating end must not stack on the planned ones.
 *
 * Waypointed edges never get a plan, but their floating ends still land on the side via
 * the per-end fallback ([resolveOrthogonalEnd] aimed at the adjacent via). Those lanes
 * join the layout as PINNED entries: the via dictates them, so the planner spreads its
 * own anchors around them instead of stacking a planned arrival onto a via-dictated
 * departure (the classic same-port fork: two edges sharing one perimeter point).
 */
private fun sideAttachments(
    graph: DiagramGraph,
    options: RoutingOptions,
    node: DiagramNode,
    side: DiagramNodeSide,
): List<SideAttachment> {
    data class Pending(
        val edgeId: DiagramEdgeId,
        val ideal: Double,
        val otherCenter: Double,
        val pinned: Boolean,
    )

    val (low, high) = sideCoordinateRange(node.bounds, side, options.obstacleMargin)
    val pending = mutableListOf<Pending>()
    for (candidate in graph.edges) {
        if (!candidate.routing.routesOrthogonally) continue
        val sourceNodeId = candidate.source.attachedNodeId
        val targetNodeId = candidate.target.attachedNodeId
        if (sourceNodeId == null || targetNodeId == null || sourceNodeId == targetNodeId) continue
        val isSourceEnd = when (node.id) {
            sourceNodeId -> true
            targetNodeId -> false
            else -> continue
        }
        val thisEnd = if (isSourceEnd) candidate.source else candidate.target
        if (thisEnd !is DiagramEndpoint.FloatingAnchor) continue
        val other = graph.nodeById(if (isSourceEnd) targetNodeId else sourceNodeId) ?: continue
        val otherCenter = if (side.exitsHorizontally) other.bounds.centerY else other.bounds.centerX
        if (candidate.waypoints.isEmpty()) {
            val candidateSide = if (isSourceEnd) {
                facingSides(node.bounds, other.bounds)?.sourceSide
            } else {
                facingSides(other.bounds, node.bounds)?.targetSide
            }
            if (candidateSide != side) continue
            pending += Pending(
                edgeId = candidate.id,
                ideal = idealSideCoordinate(node.bounds, other.bounds, side, low, high),
                otherCenter = otherCenter,
                pinned = false,
            )
        } else {
            // Mirror the fallback resolution exactly: the end aims at its adjacent via.
            val reference = if (isSourceEnd) candidate.waypoints.first() else candidate.waypoints.last()
            if (dominantSide(node.bounds.center, reference) != side) continue
            pending += Pending(
                edgeId = candidate.id,
                ideal = (if (side.exitsHorizontally) reference.y else reference.x).coerceIn(low, high),
                otherCenter = otherCenter,
                pinned = true,
            )
        }
    }
    val sorted = pending.sortedWith(
        compareBy({ it.ideal }, { it.otherCenter }, { it.edgeId.value }),
    )
    val positions = spreadPositionsWithPins(
        sorted.map { it.ideal },
        sorted.map { it.pinned },
        low,
        high,
        options.anchorSeparation,
    )
    return sorted.mapIndexed { index, entry -> SideAttachment(entry.edgeId, positions[index]) }
}

/**
 * Ideal attachment coordinate on [side] of [bounds] toward [other]: midpoint of the
 * projection overlap when the nodes overlap on the side's axis (straight connector),
 * otherwise the other node's center; always clamped into `[low, high]`.
 */
private fun idealSideCoordinate(
    bounds: DiagramRect,
    other: DiagramRect,
    side: DiagramNodeSide,
    low: Double,
    high: Double,
): Double {
    val raw = if (side.exitsHorizontally) {
        val overlapLow = maxOf(bounds.top, other.top)
        val overlapHigh = minOf(bounds.bottom, other.bottom)
        if (overlapHigh > overlapLow) (overlapLow + overlapHigh) / 2.0 else other.centerY
    } else {
        val overlapLow = maxOf(bounds.left, other.left)
        val overlapHigh = minOf(bounds.right, other.right)
        if (overlapHigh > overlapLow) (overlapLow + overlapHigh) / 2.0 else other.centerX
    }
    return raw.coerceIn(low, high)
}

/**
 * Spreads sorted [ideals] so consecutive positions stay at least [separation] apart
 * inside `[low, high]`, moving values as little as possible; when the range cannot fit
 * the full separation the positions are distributed evenly across it.
 */
private fun spreadPositions(
    ideals: List<Double>,
    low: Double,
    high: Double,
    separation: Double,
): List<Double> {
    val count = ideals.size
    if (count <= 1 || separation <= 0.0) return ideals
    val span = high - low
    if (span < separation * (count - 1)) {
        return List(count) { index -> low + span * index / (count - 1.0) }
    }
    val positions = DoubleArray(count) { ideals[it] }
    for (index in 1 until count) {
        positions[index] = maxOf(positions[index], positions[index - 1] + separation)
    }
    if (positions[count - 1] > high) {
        positions[count - 1] = high
        for (index in count - 2 downTo 0) {
            positions[index] = minOf(positions[index], positions[index + 1] - separation)
        }
    }
    return positions.toList()
}

/**
 * [spreadPositions] with immovable entries: a pinned position holds exactly its ideal —
 * a via-dictated lane is authored intent — and each run of movable entries between pins
 * spreads inside the interval the pins leave for it. Pins packed too tight to leave a
 * movable entry any room degrade to the raw ideal: a stacked lane beats an anchor pushed
 * off the side.
 */
private fun spreadPositionsWithPins(
    ideals: List<Double>,
    pinned: List<Boolean>,
    low: Double,
    high: Double,
    separation: Double,
): List<Double> {
    if (pinned.none { it }) return spreadPositions(ideals, low, high, separation)
    val result = ideals.toMutableList()
    var start = 0
    while (start < ideals.size) {
        if (pinned[start]) {
            start++
            continue
        }
        var end = start
        while (end + 1 < ideals.size && !pinned[end + 1]) end++
        val runLow = if (start > 0) ideals[start - 1] + separation else low
        val runHigh = if (end + 1 < ideals.size) ideals[end + 1] - separation else high
        if (runLow <= runHigh + GEOMETRY_EPSILON) {
            // spreadPositions leaves a lone ideal untouched, so clamp explicitly: the
            // run's interval is what keeps it a separation away from its pins.
            val spread = spreadPositions(ideals.subList(start, end + 1), runLow, runHigh, separation)
                .map { it.coerceIn(runLow, runHigh) }
            for (index in start..end) result[index] = spread[index - start]
        }
        start = end + 1
    }
    return result
}

/**
 * Valid attachment coordinates along [side] of [bounds]: the side's axis range inset by
 * [margin] on both ends (collapsing to the side center for tiny nodes), keeping anchors
 * off the corners.
 */
private fun sideCoordinateRange(
    bounds: DiagramRect,
    side: DiagramNodeSide,
    margin: Double,
): Pair<Double, Double> {
    val (low, high) = if (side.exitsHorizontally) {
        bounds.top to bounds.bottom
    } else {
        bounds.left to bounds.right
    }
    val inset = minOf(margin, (high - low) / 2.0)
    return (low + inset) to (high - inset)
}

private fun anchorOnSide(
    node: DiagramNode,
    side: DiagramNodeSide,
    coordinate: Double,
): DiagramPoint = node.outlineSideIntersection(side, coordinate) ?: when (side) {
    DiagramNodeSide.TOP -> DiagramPoint(coordinate, node.bounds.top)
    DiagramNodeSide.BOTTOM -> DiagramPoint(coordinate, node.bounds.bottom)
    DiagramNodeSide.LEFT -> DiagramPoint(node.bounds.left, coordinate)
    DiagramNodeSide.RIGHT -> DiagramPoint(node.bounds.right, coordinate)
}

// --- Route post-processing -------------------------------------------------------------

/**
 * Collapses short zig-jogs left by the grid router: in a vertical-horizontal-vertical
 * (or horizontal-vertical-horizontal) triple whose middle segment is at most [threshold]
 * long, the jog is shifted to whichever end keeps both replacement segments clear of the
 * [obstacles]' strict interiors, removing two bends. Repeats until no jog collapses.
 */
private fun collapseJogs(
    points: List<DiagramPoint>,
    obstacles: List<DiagramRect>,
    threshold: Double,
): List<DiagramPoint> {
    if (points.size < 4) return points
    val result = points.toMutableList()
    var changed = true
    while (changed && result.size >= 4) {
        changed = false
        for (index in 0..result.size - 4) {
            val p0 = result[index]
            val p1 = result[index + 1]
            val p2 = result[index + 2]
            val p3 = result[index + 3]
            val verticalJog = isVerticalSegment(p0, p1) && isHorizontalSegment(p1, p2) &&
                isVerticalSegment(p2, p3) && abs(p2.x - p1.x) <= threshold
            val horizontalJog = isHorizontalSegment(p0, p1) && isVerticalSegment(p1, p2) &&
                isHorizontalSegment(p2, p3) && abs(p2.y - p1.y) <= threshold
            if (!verticalJog && !horizontalJog) continue
            val jogAtEnd = if (verticalJog) DiagramPoint(p0.x, p3.y) else DiagramPoint(p3.x, p0.y)
            val jogAtStart = if (verticalJog) DiagramPoint(p3.x, p0.y) else DiagramPoint(p0.x, p3.y)
            val replacement = when {
                segmentClear(p0, jogAtEnd, obstacles) &&
                    segmentClear(jogAtEnd, p3, obstacles) -> jogAtEnd

                segmentClear(p0, jogAtStart, obstacles) &&
                    segmentClear(jogAtStart, p3, obstacles) -> jogAtStart

                else -> null
            } ?: continue
            result[index + 1] = replacement
            result.removeAt(index + 2)
            changed = true
            break
        }
    }
    return result
}

/**
 * Removes zero-width spurs from an orthogonal polyline: an interior point where the
 * route reverses 180° back over the segment it just drew. The A* bans immediate
 * reversals, but leg BOUNDARIES do not — a stale authored via (its node moved since
 * authoring) makes the leg into the via and the leg out of it retrace the same lane,
 * leaving a dead-end whisker past the real corner (the straight-leg shortcut and the
 * Manhattan fallback are direction-blind too). The collapsed route is a strict spatial
 * subset of the original — the union of the two reversing segments collapses to their
 * difference — so no obstacle or crossing can be newly hit. Repeats until no reversal
 * remains (a collapse can bring an earlier and a later segment into a new reversal).
 */
internal fun collapseSpurs(
    points: List<DiagramPoint>,
    protectedVias: List<DiagramPoint> = emptyList(),
): List<DiagramPoint> {
    if (points.size < 3) return points
    val result = points.toMutableList()
    var index = 1
    while (index < result.size - 1) {
        val a = result[index - 1]
        val b = result[index]
        val c = result[index + 1]
        val reversal =
            (isHorizontalSegment(a, b) && isHorizontalSegment(b, c) && (b.x - a.x) * (c.x - b.x) < 0.0) ||
                (isVerticalSegment(a, b) && isVerticalSegment(b, c) && (b.y - a.y) * (c.y - b.y) < 0.0)
        if (reversal && protectedVias.any { it.nearlyEquals(b) }) {
            index++
            continue
        }
        if (reversal) {
            result.removeAt(index)
            if (a.nearlyEquals(c)) {
                // The retrace was exact: drop the duplicate and re-check around the seam.
                result.removeAt(index)
            }
            if (index > 1) index--
        } else {
            index++
        }
    }
    return result
}

/**
 * Excises closed loops from an orthogonal polyline: when the route revisits a point it
 * already passed through, everything between the two visits is a lasso — pure ink that
 * adds zero connectivity. The A* leg leaving a healed via cannot reverse onto the leg
 * that arrived (the seed direction bans a 180° first move), so when the only sane
 * continuation is straight back it pays a full loop around a grid cell instead; the
 * ghost of a twice-displaced via is not worth that loop. Only exact revisits collapse —
 * a U-detour never returns to the same point.
 */
internal fun collapseLoops(
    points: List<DiagramPoint>,
    protectedVias: List<DiagramPoint> = emptyList(),
): List<DiagramPoint> {
    if (points.size < 4) return points
    val result = points.toMutableList()
    var index = 0
    while (index < result.size - 2) {
        // The LAST excisable revisit wins: one excision removes the largest loop through
        // this point that does not swallow a protected via (a lone authored via inside
        // the loop makes the loop intent — see the caller).
        var revisit = -1
        for (later in result.size - 1 downTo index + 2) {
            if (!result[later].nearlyEquals(result[index])) continue
            val loopHoldsVia = (index + 1..later).any { inside ->
                protectedVias.any { it.nearlyEquals(result[inside]) }
            }
            if (loopHoldsVia) continue
            revisit = later
            break
        }
        if (revisit > index) {
            result.subList(index + 1, revisit + 1).clear()
        }
        index++
    }
    return result
}

private fun isVerticalSegment(a: DiagramPoint, b: DiagramPoint): Boolean =
    abs(a.x - b.x) < GEOMETRY_EPSILON && abs(a.y - b.y) > GEOMETRY_EPSILON

private fun isHorizontalSegment(a: DiagramPoint, b: DiagramPoint): Boolean =
    abs(a.y - b.y) < GEOMETRY_EPSILON && abs(a.x - b.x) > GEOMETRY_EPSILON

private fun axisAligned(a: DiagramPoint, b: DiagramPoint): Boolean =
    abs(a.x - b.x) < GEOMETRY_EPSILON || abs(a.y - b.y) < GEOMETRY_EPSILON

/** Whether the axis-aligned segment `a..b` avoids every obstacle's strict interior. */
private fun segmentClear(
    a: DiagramPoint,
    b: DiagramPoint,
    obstacles: List<DiagramRect>,
): Boolean {
    if (abs(a.y - b.y) < GEOMETRY_EPSILON) {
        val x1 = minOf(a.x, b.x)
        val x2 = maxOf(a.x, b.x)
        return obstacles.none { rect ->
            a.y > rect.top + GEOMETRY_EPSILON && a.y < rect.bottom - GEOMETRY_EPSILON &&
                x2 > rect.left + GEOMETRY_EPSILON && x1 < rect.right - GEOMETRY_EPSILON
        }
    }
    val y1 = minOf(a.y, b.y)
    val y2 = maxOf(a.y, b.y)
    return obstacles.none { rect ->
        a.x > rect.left + GEOMETRY_EPSILON && a.x < rect.right - GEOMETRY_EPSILON &&
            y2 > rect.top + GEOMETRY_EPSILON && y1 < rect.bottom - GEOMETRY_EPSILON
    }
}

/**
 * Obstacle-blind orthogonal connection of two stub ends honoring exit axes: opposite
 * horizontal (or vertical) exits get a symmetric mid split, mixed exits a single bend.
 */
private fun connectOrthogonally(
    from: DiagramPoint,
    to: DiagramPoint,
    sourceSide: DiagramNodeSide?,
    targetSide: DiagramNodeSide?,
): List<DiagramPoint> {
    if (abs(from.x - to.x) < GEOMETRY_EPSILON || abs(from.y - to.y) < GEOMETRY_EPSILON) {
        return listOf(from, to)
    }
    val fromHorizontal = sourceSide?.exitsHorizontally
    val toHorizontal = targetSide?.exitsHorizontally
    return when {
        fromHorizontal == true && toHorizontal == true -> {
            val midX = (from.x + to.x) / 2.0
            listOf(from, DiagramPoint(midX, from.y), DiagramPoint(midX, to.y), to)
        }

        fromHorizontal == false && toHorizontal == false -> {
            val midY = (from.y + to.y) / 2.0
            listOf(from, DiagramPoint(from.x, midY), DiagramPoint(to.x, midY), to)
        }

        fromHorizontal == true -> listOf(from, DiagramPoint(to.x, from.y), to)
        fromHorizontal == false -> listOf(from, DiagramPoint(from.x, to.y), to)
        toHorizontal == true -> listOf(from, DiagramPoint(from.x, to.y), to)
        toHorizontal == false -> listOf(from, DiagramPoint(to.x, from.y), to)
        else -> manhattanLeg(from, to, seedDirection = null)
    }
}

/** Single L-shaped Manhattan leg; the first move follows [seedDirection]'s axis when given. */
private fun manhattanLeg(
    from: DiagramPoint,
    to: DiagramPoint,
    seedDirection: GridDirection?,
): List<DiagramPoint> {
    if (abs(from.x - to.x) < GEOMETRY_EPSILON || abs(from.y - to.y) < GEOMETRY_EPSILON) {
        return listOf(from, to)
    }
    val horizontalFirst = seedDirection?.isHorizontal ?: (abs(to.x - from.x) >= abs(to.y - from.y))
    val bend = if (horizontalFirst) DiagramPoint(to.x, from.y) else DiagramPoint(from.x, to.y)
    return listOf(from, bend, to)
}

private fun lastSegmentDirection(points: List<DiagramPoint>): GridDirection? {
    for (index in points.size - 1 downTo 1) {
        val delta = points[index] - points[index - 1]
        if (abs(delta.x) > GEOMETRY_EPSILON) {
            return if (delta.x > 0.0) GridDirection.RIGHT else GridDirection.LEFT
        }
        if (abs(delta.y) > GEOMETRY_EPSILON) {
            return if (delta.y > 0.0) GridDirection.DOWN else GridDirection.UP
        }
    }
    return null
}

private fun inflate(rect: DiagramRect, margin: Double): DiagramRect = DiagramRect(
    x = rect.x - margin,
    y = rect.y - margin,
    width = rect.width + margin * 2.0,
    height = rect.height + margin * 2.0,
)

private fun strictlyContains(rect: DiagramRect, point: DiagramPoint): Boolean =
    point.x > rect.left + GEOMETRY_EPSILON && point.x < rect.right - GEOMETRY_EPSILON &&
        point.y > rect.top + GEOMETRY_EPSILON && point.y < rect.bottom - GEOMETRY_EPSILON

// --- Polyline utilities --------------------------------------------------------------

/**
 * Keeps authored `via` points exact except for sub-pixel rounding against an adjacent
 * stub/waypoint. Without this snap, e.g. a rounded `x=600` next to a port at `x=599.2`
 * forces a tiny extra jog before an otherwise straight mandatory run.
 */
private fun alignedMandatoryPoints(
    sourceStub: DiagramPoint,
    sourceSide: DiagramNodeSide?,
    waypoints: List<DiagramPoint>,
    targetStub: DiagramPoint,
    targetSide: DiagramNodeSide?,
): List<DiagramPoint> {
    if (waypoints.isEmpty()) return dedupePoints(listOf(sourceStub, targetStub))
    val authored = listOf(sourceStub) + waypoints + listOf(targetStub)
    val aligned = authored.toMutableList()
    for (index in 1 until aligned.lastIndex) {
        val point = aligned[index]
        val previous = aligned[index - 1]
        val next = authored[index + 1]
        val x = when {
            abs(point.x - previous.x) <= WAYPOINT_ALIGNMENT_TOLERANCE -> previous.x
            abs(point.x - next.x) <= WAYPOINT_ALIGNMENT_TOLERANCE -> next.x
            else -> point.x
        }
        val y = when {
            abs(point.y - previous.y) <= WAYPOINT_ALIGNMENT_TOLERANCE -> previous.y
            abs(point.y - next.y) <= WAYPOINT_ALIGNMENT_TOLERANCE -> next.y
            else -> point.y
        }
        aligned[index] = DiagramPoint(x, y)
    }

    if (waypoints.size >= 2) {
        // With two or more waypoints, an endpoint-adjacent point NEAR the port axis is
        // the corner where the route leaves/enters a side port. Keep that leg
        // perpendicular to the node face even when a node was resized after authoring;
        // otherwise a stale coordinate becomes a short dangling jog. The healing is
        // bounded ([PORT_LEG_ALIGNMENT_LIMIT]): a via authored far off the axis is a
        // deliberate corner elsewhere on the route, and snapping it used to erase the
        // authored corridor and collapse deliberately separated edges onto one grid
        // corridor. A lone waypoint remains an exact mandatory pass-through point
        // because it may intentionally define both endpoint bends.
        aligned[1] = alignWaypointToPort(aligned[1], sourceStub, sourceSide)
        aligned[aligned.lastIndex - 1] = alignWaypointToPort(
            aligned[aligned.lastIndex - 1],
            targetStub,
            targetSide,
        )
    }
    return dedupePoints(aligned)
}

private fun alignWaypointToPort(
    waypoint: DiagramPoint,
    stub: DiagramPoint,
    side: DiagramNodeSide?,
): DiagramPoint = when (side) {
    // The epsilon keeps a lane placed exactly on the window boundary (the fan's
    // healable-shift rescue lands there) from losing its healing to float rounding.
    DiagramNodeSide.LEFT,
    DiagramNodeSide.RIGHT,
    -> if (abs(waypoint.y - stub.y) <= PORT_LEG_ALIGNMENT_LIMIT + GEOMETRY_EPSILON) {
        waypoint.copy(y = stub.y)
    } else {
        waypoint
    }
    DiagramNodeSide.TOP,
    DiagramNodeSide.BOTTOM,
    -> if (abs(waypoint.x - stub.x) <= PORT_LEG_ALIGNMENT_LIMIT + GEOMETRY_EPSILON) {
        waypoint.copy(x = stub.x)
    } else {
        waypoint
    }
    null -> waypoint
}

/**
 * How far off the port axis an endpoint-adjacent via may sit and still be healed onto
 * it. Covers coordinates gone stale after node moves/resizes (single-digit drift in
 * practice) while leaving genuinely authored corners — hundreds of units away — exact.
 */
internal const val PORT_LEG_ALIGNMENT_LIMIT = 24.0

/**
 * Pushes mandatory vias that sit INSIDE a node body out to that node's inflated corridor
 * boundary. A via inside a node — stale after the node moved or grew — breaks routing
 * outright: the A* legs that start or end inside an obstacle cannot route and fall back
 * to straight cuts through the node (the lint's EdgeThroughNode). Only points strictly
 * inside the RAW bounds move (a via in the margin ring is a legitimate tight corridor);
 * they land on the inflated boundary the grid A* rides. A node that also contains BOTH
 * neighbouring mandatory points is acting as a container (a background frame, a
 * swimlane) — the same rule [legObstacles] applies to legs — and is not healed against.
 * Of the axis-push candidates the one keeping the total prev → via → next manhattan
 * length shortest wins, and ties go to the axis that keeps the via collinear with a
 * neighbour — the authored corridor direction survives, only the broken corner moves.
 */
private fun healViasInsideObstacles(
    mandatory: List<DiagramPoint>,
    obstacles: List<Pair<DiagramRect, DiagramRect>>,
): List<DiagramPoint> {
    if (mandatory.size <= 2 || obstacles.isEmpty()) return mandatory
    val healed = mandatory.toMutableList()
    for (index in 1 until healed.lastIndex) {
        var point = healed[index]
        // A pushed-out via can land inside a neighbouring node; a couple of passes settle it.
        var attempts = 0
        while (attempts < 3) {
            val hit = obstacles.firstOrNull { (bounds, _) ->
                strictlyContains(bounds, point) &&
                    !(strictlyContains(bounds, healed[index - 1]) && strictlyContains(bounds, healed[index + 1]))
            } ?: break
            val inflated = hit.second
            val prev = healed[index - 1]
            val next = healed[index + 1]
            val candidates = listOf(
                point.copy(x = inflated.left),
                point.copy(x = inflated.right),
                point.copy(y = inflated.top),
                point.copy(y = inflated.bottom),
            )
            fun detour(candidate: DiagramPoint): Double =
                abs(candidate.x - prev.x) + abs(candidate.y - prev.y) +
                    abs(candidate.x - next.x) + abs(candidate.y - next.y)
            fun keepsCorridor(candidate: DiagramPoint): Boolean =
                (candidate.x == point.x && (point.x == prev.x || point.x == next.x)) ||
                    (candidate.y == point.y && (point.y == prev.y || point.y == next.y))
            val best = candidates.minByOrNull { detour(it) } ?: break
            val bestScore = detour(best)
            point = candidates
                .filter { detour(it) <= bestScore + GEOMETRY_EPSILON && keepsCorridor(it) }
                .minByOrNull { abs(it.x - point.x) + abs(it.y - point.y) }
                ?: best
            attempts++
        }
        healed[index] = point
    }
    return healed
}

private fun dedupePoints(points: List<DiagramPoint>): List<DiagramPoint> {
    val result = mutableListOf<DiagramPoint>()
    for (point in points) {
        if (result.isEmpty() || !result.last().nearlyEquals(point)) result += point
    }
    return result
}

/** Drops duplicate points and interior points collinear with (and between) their neighbors. */
internal fun simplifyPolyline(points: List<DiagramPoint>): List<DiagramPoint> {
    val deduped = dedupePoints(points)
    if (deduped.size <= 2) return deduped
    val result = mutableListOf(deduped.first())
    for (index in 1 until deduped.size - 1) {
        val previous = result.last()
        val current = deduped[index]
        val next = deduped[index + 1]
        val a = current - previous
        val b = next - current
        val cross = a.x * b.y - a.y * b.x
        val dot = a.x * b.x + a.y * b.y
        val collinearForward = abs(cross) < GEOMETRY_EPSILON && dot >= 0.0
        if (!collinearForward) result += current
    }
    result += deduped.last()
    return result
}

private fun atLeastTwo(points: List<DiagramPoint>): List<DiagramPoint> =
    if (points.size >= 2) points else List(2) { points.firstOrNull() ?: DiagramPoint.Zero }

private const val WAYPOINT_ALIGNMENT_TOLERANCE = 1.0
