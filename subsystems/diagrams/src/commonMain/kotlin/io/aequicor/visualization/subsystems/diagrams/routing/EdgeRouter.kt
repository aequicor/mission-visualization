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

/** Routes every edge of the graph (input for line-jump computation and rendering). */
fun routeAllEdges(
    graph: DiagramGraph,
    options: RoutingOptions = RoutingOptions.Default,
): List<RoutedEdge> = graph.edges.map { routeEdge(graph, it, options) }

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
        val anchor = when (side) {
            DiagramNodeSide.LEFT ->
                DiagramPoint(bounds.left, reference.y.coerceIn(bounds.top, bounds.bottom))

            DiagramNodeSide.RIGHT ->
                DiagramPoint(bounds.right, reference.y.coerceIn(bounds.top, bounds.bottom))

            DiagramNodeSide.TOP ->
                DiagramPoint(reference.x.coerceIn(bounds.left, bounds.right), bounds.top)

            DiagramNodeSide.BOTTOM ->
                DiagramPoint(reference.x.coerceIn(bounds.left, bounds.right), bounds.bottom)
        }
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
    val source = resolveOrthogonalEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw)
    val target = resolveOrthogonalEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw)
    val margin = options.obstacleMargin
    val sourceStub = source.side?.let { source.anchor + it.outwardNormal() * margin } ?: source.anchor
    val targetStub = target.side?.let { target.anchor + it.outwardNormal() * margin } ?: target.anchor
    val mandatory = dedupePoints(listOf(sourceStub) + edge.waypoints + listOf(targetStub))

    val innerPoints: List<DiagramPoint> = if (mandatory.size == 2 && !avoidObstacles) {
        connectOrthogonally(mandatory[0], mandatory[1], source.side, target.side)
    } else {
        val obstacles = if (avoidObstacles) {
            graph.nodes
                .filter { it.visible && it.width > GEOMETRY_EPSILON && it.height > GEOMETRY_EPSILON }
                .map { inflate(it.bounds, margin) }
        } else {
            emptyList()
        }
        val points = mutableListOf(mandatory.first())
        var seedDirection = source.side?.toGridDirection()
        for ((from, to) in mandatory.zipWithNext()) {
            val leg = if (avoidObstacles) {
                val legObstacles = obstacles.filterNot {
                    strictlyContains(it, from) || strictlyContains(it, to)
                }
                orthogonalGridRoute(from, seedDirection, to, legObstacles, options.turnPenalty)
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
    val source = resolveEntityRelationEnd(graph, edge.source, edge.waypoints.firstOrNull() ?: targetRaw)
    val target = resolveEntityRelationEnd(graph, edge.target, edge.waypoints.lastOrNull() ?: sourceRaw)
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
): ResolvedEnd = when (endpoint) {
    is DiagramEndpoint.FreePoint ->
        ResolvedEnd(DiagramPoint(endpoint.x, endpoint.y), node = null, side = null)

    is DiagramEndpoint.FixedPort -> resolveOrthogonalEnd(graph, endpoint, reference)

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
