package io.aequicor.visualization.subsystems.diagrams.hittest

import io.aequicor.visualization.subsystems.diagrams.geometry.containsPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.anchorPoint
import io.aequicor.visualization.subsystems.diagrams.geometry.outlineResizeHandlePoints
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.ops.DiagramEdgeEnd
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import kotlin.math.abs
import kotlin.math.sqrt

/** The eight resize handles on a selected node's rendered outline. */
enum class DiagramResizeHandle {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    RIGHT,
    BOTTOM_RIGHT,
    BOTTOM,
    BOTTOM_LEFT,
    LEFT,
}

/** Compartments of a UML class box, top to bottom. */
enum class UmlClassSection { NAME, ATTRIBUTES, OPERATIONS }

/** A structured sub-part of a node the pointer is over (cell / compartment / lane). */
sealed interface DiagramNodeHitPart {

    /** Grid position inside a [TableNode]; for merged cells this is the anchor position. */
    data class TableCellPart(
        val row: Int,
        val column: Int,
    ) : DiagramNodeHitPart

    /** Compartment of a [UmlClassNode]. */
    data class ClassSectionPart(
        val section: UmlClassSection,
    ) : DiagramNodeHitPart

    /** Lane index inside a [DiagramNodePayload.SwimlaneNode]. */
    data class LanePart(
        val laneIndex: Int,
    ) : DiagramNodeHitPart
}

/**
 * What the pointer hit. Ordered by pick priority: handles (resize / waypoint / endpoint /
 * label) win over declared ports, declared ports over edges, edges over node bodies, node
 * bodies over the virtual connection grid; nodes resolve top-of-z-order first. The virtual
 * grid is offered only in empty space (no body under the pointer), so it never shadows a
 * foreground body nor swallows a plain click on a node.
 */
sealed interface DiagramHit {

    /** A resize handle of a selected node. */
    data class ResizeHandle(
        val nodeId: DiagramNodeId,
        val handle: DiagramResizeHandle,
    ) : DiagramHit

    /** A manual waypoint grab of a selected edge. */
    data class WaypointHandle(
        val edgeId: DiagramEdgeId,
        val waypointIndex: Int,
    ) : DiagramHit

    /** An endpoint re-attach grab (source/target ring) of a selected edge. */
    data class EndpointHandle(
        val edgeId: DiagramEdgeId,
        val end: DiagramEdgeEnd,
    ) : DiagramHit

    /** A label grab of a selected edge. */
    data class LabelHandle(
        val edgeId: DiagramEdgeId,
        val position: DiagramEdgeLabelPosition,
    ) : DiagramHit

    /** A connection point of a node. */
    data class Port(
        val nodeId: DiagramNodeId,
        val portId: DiagramPortId,
    ) : DiagramHit

    /** An edge; [segmentIndex] is the polyline segment nearest to the pointer. */
    data class Edge(
        val edgeId: DiagramEdgeId,
        val segmentIndex: Int,
    ) : DiagramHit

    /** A node body, with an optional structured [part] (table cell / class section / lane). */
    data class Node(
        val nodeId: DiagramNodeId,
        val part: DiagramNodeHitPart? = null,
    ) : DiagramHit
}

/**
 * Picks the topmost interactive element at [point].
 *
 * Priority: resize handles (selected nodes) > waypoint handles (selected edges) >
 * endpoint handles (selected edges) > label handles (ANY edge with a label) > declared ports
 * > edges > node bodies > virtual connection grid. Within each class, elements are
 * scanned top-of-z-order first (explicit layers top→bottom, then the implicit default
 * layer; within a layer, later list entries are on top). The virtual draw.io connection grid
 * (auto mid-sides / quarter-points / corners on every node) is offered only when no node body
 * is under the pointer — a click on a node body selects/moves that node, and a lower node's
 * virtual point never wins over a foreground body. Locked or invisible nodes and
 * nodes/edges on locked or invisible layers are transparent to hits. Node bodies follow their
 * rendered outline; rotation is ignored because diagram outline rendering currently ignores it.
 *
 * @param routes routed polylines per edge (from the routing layer), in document
 *   coordinates. Edges missing from the map fall back to
 *   `source point → waypoints → target point`.
 * @param tolerance pick radius in document units for handles, ports, and edge lines.
 * @param selectedNodeIds nodes currently selected — only these expose resize handles.
 * @param selectedEdgeIds edges currently selected — only these expose waypoint/endpoint
 *   handles. Label handles are exposed for every edge that has a label, selected or not.
 */
fun hitTest(
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, List<DiagramPoint>>,
    point: DiagramPoint,
    tolerance: Double = 6.0,
    selectedNodeIds: Set<DiagramNodeId> = emptySet(),
    selectedEdgeIds: Set<DiagramEdgeId> = emptySet(),
): DiagramHit? {
    val nodesTopDown = graph.interactiveNodesTopDown()
    val edgesTopDown = graph.interactiveEdgesTopDown()

    for (node in nodesTopDown) {
        if (node.id !in selectedNodeIds) continue
        resizeHandlePositions(node).forEach { (handle, position) ->
            if (distance(point, position) <= tolerance) {
                return DiagramHit.ResizeHandle(node.id, handle)
            }
        }
    }

    for (edge in edgesTopDown) {
        if (edge.id !in selectedEdgeIds) continue
        edge.waypoints.forEachIndexed { index, waypoint ->
            if (distance(point, waypoint) <= tolerance) {
                return DiagramHit.WaypointHandle(edge.id, index)
            }
        }
    }

    for (edge in edgesTopDown) {
        if (edge.id !in selectedEdgeIds) continue
        // Endpoint ring grabs at the routed polyline's ends (fallback = source/target points),
        // so the hit matches what the overlay draws. Corner grabs the user aims at precisely,
        // hence tested before labels; the nearer end wins for a degenerate near-zero-length edge.
        val route = edgeRoute(graph, edge, routes) ?: continue
        val toSource = distance(point, route.first())
        val toTarget = distance(point, route.last())
        if (toSource <= tolerance || toTarget <= tolerance) {
            val end = if (toSource <= toTarget) DiagramEdgeEnd.SOURCE else DiagramEdgeEnd.TARGET
            return DiagramHit.EndpointHandle(edge.id, end)
        }
    }

    // Edge labels are hit for EVERY edge (not only selected ones), so clicking or dragging a
    // label works straight from hover — draw.io treats the label as part of the edge (click a
    // label = select its edge; press+drag a label = move it, no prior selection needed). The hit
    // area is an approximate text rect around the label anchor (width scales with the character
    // count; height ~ one text line), kept snug so it does not steal nearby node/empty clicks.
    // High priority (above ports/edges/nodes): a label is the deliberate topmost target, beating
    // the thin edge line and any node body it overlaps. Only edges that actually have a label
    // gain hit area, so empty space is unaffected.
    for (edge in edgesTopDown) {
        val route = edgeRoute(graph, edge, routes) ?: continue
        edge.labels.forEach { label ->
            val anchor = edgeLabelAnchorPoint(route, label, edgeLabelObstacleRoutes(graph, routes, edge.id), edgeLabelAvoidRects(graph, edge.id))
            val halfWidth = maxOf(label.label.text.length * 3.5, 12.0) + 4.0
            val halfHeight = 9.0
            if (abs(point.x - anchor.x) <= halfWidth && abs(point.y - anchor.y) <= halfHeight) {
                return DiagramHit.LabelHandle(edge.id, label.position)
            }
        }
    }

    // DECLARED ports only: these are intentional, author-placed connection handles (draw.io's
    // fixed green crosses), so they outrank edges and node bodies — top-of-z-order first. The
    // virtual auto-grid (connectionPorts()) is deliberately NOT tested here: unlike a declared
    // port it exists on every node and would let a lower node's perimeter point shadow a
    // foreground body or swallow a plain node click. It is resolved far lower, after node bodies,
    // and only in empty space (see the two node passes below).
    for (node in nodesTopDown) {
        node.ports.forEach { port ->
            if (distance(point, anchorPoint(node, port)) <= tolerance) {
                return DiagramHit.Port(node.id, port.id)
            }
        }
    }

    for (edge in edgesTopDown) {
        val route = edgeRoute(graph, edge, routes) ?: continue
        var bestSegment = -1
        var bestDistance = Double.MAX_VALUE
        for (index in 0 until route.size - 1) {
            val d = distanceToSegment(point, route[index], route[index + 1])
            if (d < bestDistance) {
                bestDistance = d
                bestSegment = index
            }
        }
        if (bestSegment >= 0 && bestDistance <= tolerance) {
            return DiagramHit.Edge(edge.id, bestSegment)
        }
    }

    // Node body: the top-most node whose rendered outline contains the point wins. Resolving bodies BEFORE
    // any virtual connection point is what makes a plain click select/move the node under the
    // cursor, and it guarantees a virtual port belonging to an occluded (lower) node can never be
    // returned in front of the body that covers it.
    for ((index, node) in nodesTopDown.withIndex()) {
        if (!node.containsPoint(point)) continue
        // A container's body must not bury the connection crosses of the nodes stacked on
        // top of it: inside a background frame the "empty space" around a shape belongs to
        // the container, and without this pass an edge could only ever start from a
        // DECLARED port there. Only nodes ABOVE the hit body are offered — the node's own
        // interior still selects and drags it, and a cross under a covering body stays
        // unreachable (proper occlusion).
        for (above in 0 until index) {
            val candidate = nodesTopDown[above]
            candidate.connectionPorts().forEach { port ->
                if (distance(point, anchorPoint(candidate, port)) <= tolerance) {
                    return DiagramHit.Port(candidate.id, port.id)
                }
            }
        }
        return DiagramHit.Node(node.id, nodeHitPart(node, point))
    }

    // With no node body under the pointer, offer the draw.io virtual connection grid so an edge
    // can be started from a node's perimeter: the top-most node with a connection point within
    // tolerance (the pointer is in the perimeter band just outside that node). Reached only in
    // empty space, so it never shadows a body or a node's own selection. Declared ports were
    // already resolved above; the grid is a pure hit-test convenience, materialized on the node
    // only when a drag actually pins an edge to it.
    for (node in nodesTopDown) {
        node.connectionPorts().forEach { port ->
            if (distance(point, anchorPoint(node, port)) <= tolerance) {
                return DiagramHit.Port(node.id, port.id)
            }
        }
    }

    return null
}

/** Absolute document point an endpoint resolves to (port / node center / free point). */
fun resolveEndpointPoint(graph: DiagramGraph, endpoint: DiagramEndpoint): DiagramPoint? =
    when (endpoint) {
        is DiagramEndpoint.FreePoint -> DiagramPoint(endpoint.x, endpoint.y)
        is DiagramEndpoint.FloatingAnchor -> graph.nodeById(endpoint.nodeId)?.bounds?.center
        is DiagramEndpoint.FixedPort -> graph.nodeById(endpoint.nodeId)?.let { node ->
            val port = node.portById(endpoint.portId)
            if (port != null) anchorPoint(node, port) else node.bounds.center
        }
    }

/**
 * Default perpendicular gap (document units) that lifts an edge label clear of the line it
 * annotates, so the connector no longer runs through the text. Roughly half the label height
 * plus a small breathing gap. The user's manual offset stacks on top and can cancel it.
 */
const val EDGE_LABEL_LINE_GAP: Double = 13.0

/** A MIDDLE label keeps at least this distance from crossings with other routes. */
private const val EDGE_LABEL_CROSSING_CLEARANCE = 18.0

/** MIDDLE label fraction candidates, tried closest-to-center first. */
private val MIDDLE_LABEL_FRACTIONS = listOf(0.5, 0.42, 0.58, 0.34, 0.66, 0.26, 0.74)

/**
 * Anchor point of an edge label on a routed polyline: arc-length fraction 0.1 / 0.5 / 0.9
 * for SOURCE / MIDDLE / TARGET, lifted [EDGE_LABEL_LINE_GAP] perpendicular to the line so the
 * connector does not cross the text, plus the label's manual offset (drag override) on top.
 *
 * With [otherRoutes] and/or [avoidRects] given, an undragged MIDDLE label slides along the
 * route away from crossings with those routes (dense corridors) and away from foreign node
 * bodies, staying as close to the center as a clear spot allows. Every surface reading the
 * anchor (renderer, overlays, hit-test) must pass the same context or labels and their hit
 * areas drift apart.
 */
fun edgeLabelAnchorPoint(
    route: List<DiagramPoint>,
    label: DiagramEdgeLabel,
    otherRoutes: List<List<DiagramPoint>> = emptyList(),
    avoidRects: List<DiagramRect> = emptyList(),
): DiagramPoint {
    selfLoopTopAnchor(route)?.let { top ->
        // A self-message loop (see routeMessage): the caption reads *above* the loop's top
        // edge, centered on it. Anchoring along the arc would land the label beside the loop's
        // right side at mid-height, where a wide centered caption bleeds left over the lifeline
        // and buries the arrow start.
        return DiagramPoint(
            top.x + label.offsetX,
            top.y - EDGE_LABEL_LINE_GAP + label.offsetY,
        )
    }
    val fraction = when (label.position) {
        DiagramEdgeLabelPosition.SOURCE -> 0.1
        DiagramEdgeLabelPosition.MIDDLE -> middleLabelFraction(route, label, otherRoutes, avoidRects)
        DiagramEdgeLabelPosition.TARGET -> 0.9
    }
    val base = pointAlongPolyline(route, fraction)
    val lift = edgeLabelLift(route, fraction)
    val anchored = DiagramPoint(
        base.x + lift.x + label.offsetX,
        base.y + lift.y + label.offsetY,
    )
    return pushedClearOfNodeBodies(anchored, label, listOf(route) + otherRoutes, avoidRects)
}

/** Estimated half extents of a label's text box (matches the lint's defaults). */
internal const val EDGE_LABEL_HALF_CHAR: Double = 3.5
internal const val EDGE_LABEL_HALF_HEIGHT: Double = 8.0

/**
 * Furthest a label anchor is corrected to get its box off a node body. Covers the common
 * defect — a route moved after the label's offset was authored/dragged, so the box now
 * clips a node body — while a label parked DEEP inside a node stays where its author put
 * it (a teleport would be worse than the overlap, and the lint still reports it).
 */
private const val EDGE_LABEL_NODE_PUSH_LIMIT = 72.0

/**
 * Pushes a label anchor the shortest axis distance that takes its estimated text box out
 * of every [avoidRects] body without landing the text on a line: a candidate whose box
 * (inset a little — a line clipping the outermost pixel of a glyph is fine) any of
 * [allRoutes] passes through is rejected, so escaping a node cannot bury the label on its
 * own edge or a neighbouring one. Applied after fraction, lift and the manual offset, on
 * every surface that reads the anchor (renderer, overlays, hit-test) — the correction is
 * part of where the label IS.
 */
private fun pushedClearOfNodeBodies(
    anchor: DiagramPoint,
    label: DiagramEdgeLabel,
    allRoutes: List<List<DiagramPoint>>,
    avoidRects: List<DiagramRect>,
): DiagramPoint {
    if (avoidRects.isEmpty()) return anchor
    val halfWidth = label.label.text.length * EDGE_LABEL_HALF_CHAR
    val halfHeight = EDGE_LABEL_HALF_HEIGHT

    fun overlappedRect(point: DiagramPoint): DiagramRect? = avoidRects.firstOrNull { rect ->
        point.x + halfWidth > rect.left + 1.0 && point.x - halfWidth < rect.right - 1.0 &&
            point.y + halfHeight > rect.top + 1.0 && point.y - halfHeight < rect.bottom - 1.0
    }

    fun lineThroughBox(point: DiagramPoint): Boolean {
        val box = DiagramRect(
            x = point.x - (halfWidth - 4.0).coerceAtLeast(1.0),
            y = point.y - (halfHeight - 2.0).coerceAtLeast(1.0),
            width = ((halfWidth - 4.0) * 2.0).coerceAtLeast(2.0),
            height = ((halfHeight - 2.0) * 2.0).coerceAtLeast(2.0),
        )
        return allRoutes.any { route ->
            route.zipWithNext().any { (a, b) -> segmentIntersectsRect(a, b, box) }
        }
    }

    var result = anchor
    repeat(2) {
        val hit = overlappedRect(result) ?: return result
        val reachable = listOf(
            DiagramPoint(hit.left - halfWidth - 2.0, result.y),
            DiagramPoint(hit.right + halfWidth + 2.0, result.y),
            DiagramPoint(result.x, hit.top - halfHeight - 2.0),
            DiagramPoint(result.x, hit.bottom + halfHeight + 2.0),
        ).filter { abs(it.x - anchor.x) + abs(it.y - anchor.y) <= EDGE_LABEL_NODE_PUSH_LIMIT }
        // A spot with a line through the box is still acceptable as a fallback: the
        // renderers plate labels (85% surface behind the text), so a masked line reads
        // far better than text over a node body. A line-free spot still wins.
        val candidate = reachable
            .filter { !lineThroughBox(it) }
            .minByOrNull { abs(it.x - result.x) + abs(it.y - result.y) }
            ?: reachable.minByOrNull { abs(it.x - result.x) + abs(it.y - result.y) }
            ?: return anchor
        result = candidate
    }
    return if (overlappedRect(result) == null) result else anchor
}

/** Whether segment `a..b` touches [rect] (endpoint inside, or crossing any side). */
private fun segmentIntersectsRect(a: DiagramPoint, b: DiagramPoint, rect: DiagramRect): Boolean {
    fun inside(p: DiagramPoint) =
        p.x >= rect.left && p.x <= rect.right && p.y >= rect.top && p.y <= rect.bottom
    if (inside(a) || inside(b)) return true
    val corners = listOf(
        DiagramPoint(rect.left, rect.top),
        DiagramPoint(rect.right, rect.top),
        DiagramPoint(rect.right, rect.bottom),
        DiagramPoint(rect.left, rect.bottom),
    )
    return (corners + corners.first()).zipWithNext().any { (c1, c2) ->
        segmentIntersection(a, b, c1, c2) != null
    }
}

/**
 * Node bodies an edge label must stay out of — every visible node that participates in
 * an edge, except this edge's own endpoints (decorative containers with no connections
 * would otherwise swallow every candidate). The companion context to
 * [edgeLabelObstacleRoutes] for [edgeLabelAnchorPoint].
 */
fun edgeLabelAvoidRects(graph: DiagramGraph, edgeId: DiagramEdgeId): List<DiagramRect> {
    val edge = graph.edges.firstOrNull { it.id == edgeId } ?: return emptyList()
    val own = setOfNotNull(edge.source.attachedNodeId, edge.target.attachedNodeId)
    val connected = buildSet {
        for (candidate in graph.edges) {
            candidate.source.attachedNodeId?.let(::add)
            candidate.target.attachedNodeId?.let(::add)
        }
    }
    return graph.nodes
        .filter { it.visible && it.id in connected && it.id !in own }
        .map { it.bounds }
}

/**
 * Other edges' route polylines — the obstacle context for [edgeLabelAnchorPoint].
 * Edges on invisible layers are excluded (their lines are not drawn, so they must not
 * push labels around); order follows [DiagramGraph.edges] for determinism.
 */
fun edgeLabelObstacleRoutes(
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, List<DiagramPoint>>,
    edgeId: DiagramEdgeId,
): List<List<DiagramPoint>> = graph.edges
    .filter { edge ->
        edge.id != edgeId &&
            graph.effectiveLayerId(edge.layerId).let { id -> id == null || graph.layerById(id)?.visible == true }
    }
    .mapNotNull { routes[it.id] }

/**
 * The first candidate fraction whose lifted anchor clears every crossing with
 * [otherRoutes] by [EDGE_LABEL_CROSSING_CLEARANCE] and stays outside every [avoidRects]
 * body; when none does, the candidate scoring best (rect-clear outranks crossing
 * clearance, ties keep the centermost). A manually dragged label always stays at the
 * center fraction — the user's offset is relative to it.
 */
private fun middleLabelFraction(
    route: List<DiagramPoint>,
    label: DiagramEdgeLabel,
    otherRoutes: List<List<DiagramPoint>>,
    avoidRects: List<DiagramRect>,
): Double {
    if (label.offsetX != 0.0 || label.offsetY != 0.0) return 0.5
    if (otherRoutes.isEmpty() && avoidRects.isEmpty()) return 0.5
    val crossings = routeCrossings(route, otherRoutes)
    if (crossings.isEmpty() && avoidRects.isEmpty()) return 0.5
    val halfWidth = label.label.text.length * 3.5
    val halfHeight = 8.0

    fun anchorAt(fraction: Double): DiagramPoint {
        val base = pointAlongPolyline(route, fraction)
        val lift = edgeLabelLift(route, fraction)
        return DiagramPoint(base.x + lift.x, base.y + lift.y)
    }

    fun clearOfRects(point: DiagramPoint): Boolean = avoidRects.none { rect ->
        point.x + halfWidth > rect.left + 1.0 && point.x - halfWidth < rect.right - 1.0 &&
            point.y + halfHeight > rect.top + 1.0 && point.y - halfHeight < rect.bottom - 1.0
    }

    var bestFraction = 0.5
    var bestScore = Double.NEGATIVE_INFINITY
    for (candidate in MIDDLE_LABEL_FRACTIONS) {
        val point = anchorAt(candidate)
        val rectClear = clearOfRects(point)
        val clearance = if (crossings.isEmpty()) {
            EDGE_LABEL_CROSSING_CLEARANCE
        } else {
            crossings.minOf { distance(it, point) }
        }
        if (rectClear && clearance >= EDGE_LABEL_CROSSING_CLEARANCE) return candidate
        val score = (if (rectClear) 1000.0 else 0.0) +
            minOf(clearance, EDGE_LABEL_CROSSING_CLEARANCE)
        if (score > bestScore) {
            bestScore = score
            bestFraction = candidate
        }
    }
    return bestFraction
}

/** Intersection points of [route]'s segments with the segments of [otherRoutes]. */
private fun routeCrossings(
    route: List<DiagramPoint>,
    otherRoutes: List<List<DiagramPoint>>,
): List<DiagramPoint> = buildList {
    for ((a1, a2) in route.zipWithNext()) {
        for (other in otherRoutes) {
            for ((b1, b2) in other.zipWithNext()) {
                segmentIntersection(a1, a2, b1, b2)?.let(::add)
            }
        }
    }
}

/** Intersection of segments `a1..a2` and `b1..b2`, or null (parallel counts as none). */
private fun segmentIntersection(
    a1: DiagramPoint,
    a2: DiagramPoint,
    b1: DiagramPoint,
    b2: DiagramPoint,
): DiagramPoint? {
    val rx = a2.x - a1.x
    val ry = a2.y - a1.y
    val sx = b2.x - b1.x
    val sy = b2.y - b1.y
    val denominator = rx * sy - ry * sx
    if (abs(denominator) < 1e-9) return null
    val dx = b1.x - a1.x
    val dy = b1.y - a1.y
    val t = (dx * sy - dy * sx) / denominator
    val u = (dx * ry - dy * rx) / denominator
    if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null
    return DiagramPoint(a1.x + rx * t, a1.y + ry * t)
}

/**
 * Midpoint of the top edge of a right-side self-message loop, or `null` when [route] is not
 * such a loop. Matches the exact 4-point shape emitted by the sequence router's `routeMessage`:
 * `(x0,y0) → (x1,y0) → (x1,y1) → (x0,y1)` with `x1 > x0` (bulges right off the lifeline).
 */
internal fun selfLoopTopAnchor(route: List<DiagramPoint>): DiagramPoint? {
    if (route.size != 4) return null
    val (p0, p1, p2, p3) = route
    val flat = p0.y == p1.y && p2.y == p3.y
    val verticals = p0.x == p3.x && p1.x == p2.x
    if (!flat || !verticals || p1.x <= p0.x) return null
    return DiagramPoint((p0.x + p1.x) / 2.0, p0.y)
}

/**
 * Perpendicular offset that pushes the label off the line at [fraction], of magnitude
 * [EDGE_LABEL_LINE_GAP]. The chosen normal points "up" (negative y); for vertical edges,
 * where both normals are horizontal, it consistently points right (+x). Direction-independent:
 * a left→right and a right→left segment lift the label to the same side.
 */
private fun edgeLabelLift(route: List<DiagramPoint>, fraction: Double): DiagramPoint {
    val tangent = polylineTangent(route, fraction) ?: return DiagramPoint(0.0, 0.0)
    var nx = -tangent.y
    var ny = tangent.x
    if (ny > 0.0 || (ny == 0.0 && nx < 0.0)) {
        nx = -nx
        ny = -ny
    }
    return DiagramPoint(nx * EDGE_LABEL_LINE_GAP, ny * EDGE_LABEL_LINE_GAP)
}

/** Unit travel direction at arc-length [fraction] along the polyline, or null if degenerate. */
private fun polylineTangent(route: List<DiagramPoint>, fraction: Double): DiagramPoint? {
    if (route.size < 2) return null
    val lengths = (0 until route.size - 1).map { distance(route[it], route[it + 1]) }
    val total = lengths.sum()
    if (total <= 0.0) return null
    var remaining = fraction.coerceIn(0.0, 1.0) * total
    for (index in lengths.indices) {
        val segment = lengths[index]
        if ((remaining <= segment || index == lengths.lastIndex) && segment > 0.0) {
            val a = route[index]
            val b = route[index + 1]
            return DiagramPoint((b.x - a.x) / segment, (b.y - a.y) / segment)
        }
        remaining -= segment
    }
    return null
}

/** Point at arc-length [fraction] `0..1` along the polyline. */
fun pointAlongPolyline(route: List<DiagramPoint>, fraction: Double): DiagramPoint {
    require(route.isNotEmpty()) { "route must not be empty" }
    if (route.size == 1) return route.first()
    val lengths = (0 until route.size - 1).map { distance(route[it], route[it + 1]) }
    val total = lengths.sum()
    if (total <= 0.0) return route.first()
    var remaining = fraction.coerceIn(0.0, 1.0) * total
    for (index in lengths.indices) {
        val segment = lengths[index]
        if (remaining <= segment || index == lengths.lastIndex) {
            val t = if (segment > 0.0) (remaining / segment).coerceIn(0.0, 1.0) else 0.0
            val a = route[index]
            val b = route[index + 1]
            return DiagramPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        remaining -= segment
    }
    return route.last()
}

// --- internals -------------------------------------------------------------------------

private fun DiagramGraph.interactiveNodesTopDown(): List<DiagramNode> {
    val interactableLayers = layerInteractivity()
    val ordered = layerOrderBottomUp().flatMap { layerId ->
        nodes.filter { effectiveLayerId(it.layerId) == layerId }
    }
    return ordered
        .asReversed()
        .filter { node ->
            node.visible && !node.locked && interactableLayers.getValue(effectiveLayerId(node.layerId))
        }
}

private fun DiagramGraph.interactiveEdgesTopDown(): List<DiagramEdge> {
    val interactableLayers = layerInteractivity()
    val ordered = layerOrderBottomUp().flatMap { layerId ->
        edges.filter { effectiveLayerId(it.layerId) == layerId }
    }
    return ordered
        .asReversed()
        .filter { edge -> interactableLayers.getValue(effectiveLayerId(edge.layerId)) }
}

/** Layer ids bottom → top; `null` is the implicit default layer below all explicit ones. */
private fun DiagramGraph.layerOrderBottomUp(): List<DiagramLayerId?> =
    listOf<DiagramLayerId?>(null) + layers.map { it.id }

/** Unknown layer references behave as the implicit default layer. */
private fun DiagramGraph.effectiveLayerId(layerId: DiagramLayerId?): DiagramLayerId? =
    layerId?.takeIf { id -> layers.any { it.id == id } }

private fun DiagramGraph.layerInteractivity(): Map<DiagramLayerId?, Boolean> =
    layerOrderBottomUp().associateWith { layerId ->
        val layer = layerId?.let { layerById(it) }
        layer == null || (layer.visible && !layer.locked)
    }

private fun resizeHandlePositions(node: DiagramNode): List<Pair<DiagramResizeHandle, DiagramPoint>> {
    return DiagramResizeHandle.entries.zip(node.outlineResizeHandlePoints())
}

private fun edgeRoute(
    graph: DiagramGraph,
    edge: DiagramEdge,
    routes: Map<DiagramEdgeId, List<DiagramPoint>>,
): List<DiagramPoint>? {
    routes[edge.id]?.takeIf { it.size >= 2 }?.let { return it }
    val source = resolveEndpointPoint(graph, edge.source) ?: return null
    val target = resolveEndpointPoint(graph, edge.target) ?: return null
    return listOf(source) + edge.waypoints + target
}

private fun nodeHitPart(node: DiagramNode, point: DiagramPoint): DiagramNodeHitPart? {
    val localX = point.x - node.x
    val localY = point.y - node.y
    return when (val payload = node.payload) {
        is TableNode -> tableCellPart(payload, node, localX, localY)
        is UmlClassNode -> classSectionPart(payload, node, localY)
        is DiagramNodePayload.SwimlaneNode -> lanePart(payload, node, localX, localY)
        else -> null
    }
}

private fun tableCellPart(
    table: TableNode,
    node: DiagramNode,
    localX: Double,
    localY: Double,
): DiagramNodeHitPart.TableCellPart? {
    if (table.rowCount == 0 || table.columnCount == 0) return null
    val row = trackIndex(table.rows.map { it.height }, node.height, localY) ?: return null
    val column = trackIndex(table.columns.map { it.width }, node.width, localX) ?: return null
    val covering = table.cellAt(row, column)
    return if (covering != null) {
        DiagramNodeHitPart.TableCellPart(covering.row, covering.column)
    } else {
        DiagramNodeHitPart.TableCellPart(row, column)
    }
}

/** Index of the track containing [local], with track sizes scaled to fill [extent]. */
internal fun trackIndex(sizes: List<Double>, extent: Double, local: Double): Int? {
    if (sizes.isEmpty()) return null
    val scaled = scaledTrackSizes(sizes, extent)
    var accumulated = 0.0
    scaled.forEachIndexed { index, size ->
        accumulated += size
        if (local <= accumulated) return index
    }
    return sizes.lastIndex
}

/** Track sizes scaled so they fill [extent] exactly (equal split when the sizes sum to 0). */
internal fun scaledTrackSizes(sizes: List<Double>, extent: Double): List<Double> {
    val sum = sizes.sum()
    return if (sum > 0.0 && extent > 0.0) {
        sizes.map { it * extent / sum }
    } else {
        List(sizes.size) { if (extent > 0.0) extent / sizes.size else 0.0 }
    }
}

private fun classSectionPart(
    payload: UmlClassNode,
    node: DiagramNode,
    localY: Double,
): DiagramNodeHitPart.ClassSectionPart {
    val attributeRows = maxOf(payload.attributes.size, 1)
    val operationRows = maxOf(payload.operations.size, 1)
    val totalRows = 1 + attributeRows + operationRows
    val rowHeight = if (node.height > 0.0) node.height / totalRows else 0.0
    val rowIndex = if (rowHeight > 0.0) (localY / rowHeight).toInt().coerceIn(0, totalRows - 1) else 0
    val section = when {
        rowIndex == 0 -> UmlClassSection.NAME
        rowIndex < 1 + attributeRows -> UmlClassSection.ATTRIBUTES
        else -> UmlClassSection.OPERATIONS
    }
    return DiagramNodeHitPart.ClassSectionPart(section)
}

/**
 * Lane pick: HORIZONTAL pools stack lanes top→bottom (lane `size` = strip height),
 * VERTICAL pools stack them left→right (lane `size` = strip width).
 */
private fun lanePart(
    payload: DiagramNodePayload.SwimlaneNode,
    node: DiagramNode,
    localX: Double,
    localY: Double,
): DiagramNodeHitPart.LanePart? {
    if (payload.lanes.isEmpty()) return null
    val sizes = payload.lanes.map { it.size }
    val index = when (payload.orientation) {
        DiagramOrientation.HORIZONTAL -> trackIndex(sizes, node.height, localY)
        DiagramOrientation.VERTICAL -> trackIndex(sizes, node.width, localX)
    } ?: return null
    return DiagramNodeHitPart.LanePart(index)
}

private fun distance(a: DiagramPoint, b: DiagramPoint): Double {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun distanceToSegment(point: DiagramPoint, a: DiagramPoint, b: DiagramPoint): Double {
    val abX = b.x - a.x
    val abY = b.y - a.y
    val lengthSquared = abX * abX + abY * abY
    if (lengthSquared <= 0.0) return distance(point, a)
    val t = (((point.x - a.x) * abX + (point.y - a.y) * abY) / lengthSquared).coerceIn(0.0, 1.0)
    return distance(point, DiagramPoint(a.x + abX * t, a.y + abY * t))
}
