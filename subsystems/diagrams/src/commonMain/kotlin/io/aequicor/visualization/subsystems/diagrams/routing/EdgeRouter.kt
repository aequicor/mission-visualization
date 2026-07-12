package io.aequicor.visualization.subsystems.diagrams.routing

import io.aequicor.visualization.subsystems.diagrams.geometry.GEOMETRY_EPSILON
import io.aequicor.visualization.subsystems.diagrams.geometry.exitsHorizontally
import io.aequicor.visualization.subsystems.diagrams.geometry.minus
import io.aequicor.visualization.subsystems.diagrams.geometry.nearlyEquals
import io.aequicor.visualization.subsystems.diagrams.geometry.outwardNormal
import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterIntersection
import io.aequicor.visualization.subsystems.diagrams.geometry.perimeterSide
import io.aequicor.visualization.subsystems.diagrams.geometry.plus
import io.aequicor.visualization.subsystems.diagrams.geometry.times
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
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
 * - [DiagramEndpoint.FixedPort] — exact port position (route glued to the port);
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
): RoutedEdge = when (edge.routing) {
    DiagramRoutingStyle.STRAIGHT -> routeThroughPoints(graph, edge, curve = false)
    DiagramRoutingStyle.CURVED -> routeThroughPoints(graph, edge, curve = true)
    DiagramRoutingStyle.ORTHOGONAL -> routeOrthogonal(graph, edge, options, avoidObstacles = true)
    DiagramRoutingStyle.SIMPLE -> routeOrthogonal(graph, edge, options, avoidObstacles = false)
    DiagramRoutingStyle.ISOMETRIC -> routeIsometric(graph, edge)
    DiagramRoutingStyle.ENTITY_RELATION -> routeEntityRelation(graph, edge, options)
}

/**
 * Routes every edge of the graph (input for line-jump computation and rendering) and
 * separates co-running collinear segments of different edges ([nudgeRoutedEdges]).
 *
 * Sequence-diagram messages ([sequenceMessageRoutes]) are laid out top-to-bottom as
 * horizontal rows instead of going through the generic per-edge router.
 */
fun routeAllEdges(
    graph: DiagramGraph,
    options: RoutingOptions = RoutingOptions.Default,
): List<RoutedEdge> {
    val sequence = sequenceMessageRoutes(graph)
    return nudgeRoutedEdges(
        routes = graph.edges.map { sequence[it.id] ?: routeEdge(graph, it, options) },
        options = options,
        pinnedEdgeIds = waypointedEdgeIds(graph) + sequence.keys,
        obstacles = nodeObstacles(graph),
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
            node.portPosition(port)
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

/** Endpoint resolution for orthogonal styles: anchor on a bounding-box side + exit side. */
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
        val anchor = node.portPosition(port)
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
            bounds,
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
): RoutedEdge {
    val sourceRaw = rawEndpointPoint(graph, edge.source)
    val targetRaw = rawEndpointPoint(graph, edge.target)
    val planned = planFloatingEnds(graph, edge, options)
    val source = planned?.source
        ?: resolveOrthogonalEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw, options)
    val target = planned?.target
        ?: resolveOrthogonalEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw, options)
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
    val sourceStub = source.side?.let {
        source.anchor + it.outwardNormal() * stubLength(source.anchor, it, margin, foreignBounds)
    } ?: source.anchor
    val targetStub = target.side?.let {
        target.anchor + it.outwardNormal() * stubLength(target.anchor, it, margin, foreignBounds)
    } ?: target.anchor
    val mandatory = dedupePoints(listOf(sourceStub) + edge.waypoints + listOf(targetStub))

    val innerPoints: List<DiagramPoint> = if (mandatory.size == 2 && !avoidObstacles) {
        connectOrthogonally(mandatory[0], mandatory[1], source.side, target.side)
    } else {
        val obstacles = if (avoidObstacles) {
            graph.nodes
                .filter { it.visible && it.width > GEOMETRY_EPSILON && it.height > GEOMETRY_EPSILON }
                .map { it.bounds to inflate(it.bounds, margin) }
        } else {
            emptyList()
        }
        val jogThreshold = margin * 2.0
        val points = mutableListOf(mandatory.first())
        var seedDirection = source.side?.toGridDirection()
        for ((from, to) in mandatory.zipWithNext()) {
            val leg = if (avoidObstacles) {
                val legObstacles = legObstacles(obstacles, from, to)
                orthogonalGridRoute(from, seedDirection, to, legObstacles, options.turnPenalty)
                    ?.let { collapseJogs(it, legObstacles, jogThreshold) }
                    ?: manhattanLeg(from, to, seedDirection)
            } else {
                manhattanLeg(from, to, seedDirection)
            }
            points += leg.drop(1)
            seedDirection = lastSegmentDirection(leg) ?: seedDirection
        }
        points
    }

    val points = listOf(source.anchor) + innerPoints + listOf(target.anchor)
    return RoutedEdge(edge.id, edge.routing, atLeastTwo(simplifyPolyline(points)), source.side, target.side)
}

/**
 * Perpendicular exit-stub length at an anchor: [margin], shortened when a foreign node
 * sits closer than that so the stub ends on the node's near face instead of inside it
 * (which would force the router to drop that node as an obstacle and cut through it).
 */
private fun stubLength(
    anchor: DiagramPoint,
    side: DiagramNodeSide,
    margin: Double,
    foreignBounds: List<DiagramRect>,
): Double {
    var length = margin
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

// --- Floating-pair anchor planning ----------------------------------------------------

private data class PlannedEnds(val source: ResolvedEnd, val target: ResolvedEnd)

/**
 * Joint anchor planning for orthogonal edges with floating anchors on both ends and no
 * manual waypoints: instead of resolving each end against the other's center (which lands
 * anchors on node corners and picks sides facing adjacent nodes), the pair of exit sides
 * is chosen together across the wider inter-node gap, anchors sit on the midpoint of the
 * nodes' projection overlap (a straight segment whenever the corridor allows), and all
 * planned attachments sharing a node side are spread apart so no two edges leave or enter
 * through the same point. Returns `null` when the plan does not apply (waypoints, ports,
 * free points, self-loops, or bounds overlapping on both axes) — callers then fall back
 * to per-end resolution.
 */
private fun planFloatingEnds(
    graph: DiagramGraph,
    edge: DiagramEdge,
    options: RoutingOptions,
): PlannedEnds? {
    if (edge.waypoints.isNotEmpty()) return null
    if (edge.source !is DiagramEndpoint.FloatingAnchor) return null
    if (edge.target !is DiagramEndpoint.FloatingAnchor) return null
    val sourceNode = endpointNode(graph, edge.source)!!
    val targetNode = endpointNode(graph, edge.target)!!
    if (sourceNode.id == targetNode.id) return null
    val sides = facingSides(sourceNode.bounds, targetNode.bounds) ?: return null
    val sourceAnchor = plannedAnchor(graph, options, sourceNode, sides.sourceSide, targetNode, edge.id)
    val targetAnchor = plannedAnchor(graph, options, targetNode, sides.targetSide, sourceNode, edge.id)
    return PlannedEnds(
        ResolvedEnd(sourceAnchor, sourceNode, sides.sourceSide),
        ResolvedEnd(targetAnchor, targetNode, sides.targetSide),
    )
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
    return anchorOnSide(node.bounds, side, coordinate)
}

private data class SideAttachment(val edgeId: DiagramEdgeId, val position: Double)

/**
 * Deterministic attachment layout of one node side: every plannable edge of the graph
 * attaching to `(node, side)` gets its ideal coordinate (projection-overlap midpoint,
 * else the other node's center, clamped inside the side with a corner inset), then the
 * sorted ideals are spread to at least [RoutingOptions.anchorSeparation] apart.
 */
private fun sideAttachments(
    graph: DiagramGraph,
    options: RoutingOptions,
    node: DiagramNode,
    side: DiagramNodeSide,
): List<SideAttachment> {
    data class Pending(val edgeId: DiagramEdgeId, val ideal: Double, val otherCenter: Double)

    val (low, high) = sideCoordinateRange(node.bounds, side, options.obstacleMargin)
    val pending = mutableListOf<Pending>()
    for (candidate in graph.edges) {
        if (candidate.waypoints.isNotEmpty()) continue
        if (candidate.routing != DiagramRoutingStyle.ORTHOGONAL &&
            candidate.routing != DiagramRoutingStyle.SIMPLE
        ) {
            continue
        }
        val candidateSource = candidate.source as? DiagramEndpoint.FloatingAnchor ?: continue
        val candidateTarget = candidate.target as? DiagramEndpoint.FloatingAnchor ?: continue
        if (candidateSource.nodeId == candidateTarget.nodeId) continue
        val other = when (node.id) {
            candidateSource.nodeId -> graph.nodeById(candidateTarget.nodeId) ?: continue
            candidateTarget.nodeId -> graph.nodeById(candidateSource.nodeId) ?: continue
            else -> continue
        }
        val candidateSide = if (node.id == candidateSource.nodeId) {
            facingSides(node.bounds, other.bounds)?.sourceSide
        } else {
            facingSides(other.bounds, node.bounds)?.targetSide
        }
        if (candidateSide != side) continue
        pending += Pending(
            edgeId = candidate.id,
            ideal = idealSideCoordinate(node.bounds, other.bounds, side, low, high),
            otherCenter = if (side.exitsHorizontally) other.bounds.centerY else other.bounds.centerX,
        )
    }
    val sorted = pending.sortedWith(
        compareBy({ it.ideal }, { it.otherCenter }, { it.edgeId.value }),
    )
    val positions = spreadPositions(sorted.map { it.ideal }, low, high, options.anchorSeparation)
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
    bounds: DiagramRect,
    side: DiagramNodeSide,
    coordinate: Double,
): DiagramPoint = when (side) {
    DiagramNodeSide.TOP -> DiagramPoint(coordinate, bounds.top)
    DiagramNodeSide.BOTTOM -> DiagramPoint(coordinate, bounds.bottom)
    DiagramNodeSide.LEFT -> DiagramPoint(bounds.left, coordinate)
    DiagramNodeSide.RIGHT -> DiagramPoint(bounds.right, coordinate)
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

private fun isVerticalSegment(a: DiagramPoint, b: DiagramPoint): Boolean =
    abs(a.x - b.x) < GEOMETRY_EPSILON && abs(a.y - b.y) > GEOMETRY_EPSILON

private fun isHorizontalSegment(a: DiagramPoint, b: DiagramPoint): Boolean =
    abs(a.y - b.y) < GEOMETRY_EPSILON && abs(a.x - b.x) > GEOMETRY_EPSILON

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

// --- Entity relation -----------------------------------------------------------------

private fun routeEntityRelation(
    graph: DiagramGraph,
    edge: DiagramEdge,
    options: RoutingOptions,
): RoutedEdge {
    val sourceRaw = rawEndpointPoint(graph, edge.source)
    val targetRaw = rawEndpointPoint(graph, edge.target)
    val source =
        resolveEntityRelationEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw, options)
    val target =
        resolveEntityRelationEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw, options)
    val stub = options.entityRelationStub
    val sourceStub = source.side?.let { source.anchor + it.outwardNormal() * stub } ?: source.anchor
    val targetStub = target.side?.let { target.anchor + it.outwardNormal() * stub } ?: target.anchor
    val mandatory = dedupePoints(listOf(sourceStub) + edge.waypoints + listOf(targetStub))

    val innerPoints: List<DiagramPoint> = if (mandatory.size == 2) {
        connectOrthogonally(mandatory[0], mandatory[1], source.side, target.side)
    } else {
        val points = mutableListOf(mandatory.first())
        var seedDirection = source.side?.toGridDirection()
        for ((from, to) in mandatory.zipWithNext()) {
            val leg = manhattanLeg(from, to, seedDirection)
            points += leg.drop(1)
            seedDirection = lastSegmentDirection(leg) ?: seedDirection
        }
        points
    }

    val points = listOf(source.anchor) + innerPoints + listOf(target.anchor)
    return RoutedEdge(edge.id, edge.routing, atLeastTwo(simplifyPolyline(points)), source.side, target.side)
}

/** ER ends always exit horizontally (LEFT/RIGHT) unless a fixed port dictates otherwise. */
private fun resolveEntityRelationEnd(
    graph: DiagramGraph,
    endpoint: DiagramEndpoint,
    reference: DiagramPoint,
    options: RoutingOptions,
): ResolvedEnd = when (endpoint) {
    is DiagramEndpoint.FreePoint ->
        ResolvedEnd(DiagramPoint(endpoint.x, endpoint.y), node = null, side = null)

    is DiagramEndpoint.FixedPort ->
        resolveOrthogonalEnd(graph, endpoint, reference, options)

    is DiagramEndpoint.FloatingAnchor -> {
        val node = endpointNode(graph, endpoint)!!
        val bounds = node.bounds
        val side = if (reference.x >= bounds.centerX) DiagramNodeSide.RIGHT else DiagramNodeSide.LEFT
        val x = if (side == DiagramNodeSide.RIGHT) bounds.right else bounds.left
        ResolvedEnd(DiagramPoint(x, bounds.centerY), node, side)
    }
}

// --- Polyline utilities --------------------------------------------------------------

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
