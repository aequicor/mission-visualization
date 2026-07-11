package io.aequicor.visualization.subsystems.diagrams.hittest

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
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.sqrt

/** The eight resize handles on a selected node's bounding box. */
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
 * What the pointer hit. Ordered by pick priority: handles (resize / waypoint / label) win
 * over ports, ports over edges, edges over nodes; nodes resolve top-of-z-order first.
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
 * label handles (selected edges) > ports > edges > nodes. Within each class, elements are
 * scanned top-of-z-order first (explicit layers top→bottom, then the implicit default
 * layer; within a layer, later list entries are on top). Locked or invisible nodes and
 * nodes/edges on locked or invisible layers are transparent to hits. Rotation is ignored
 * (hit boxes are the axis-aligned bounds).
 *
 * @param routes routed polylines per edge (from the routing layer), in document
 *   coordinates. Edges missing from the map fall back to
 *   `source point → waypoints → target point`.
 * @param tolerance pick radius in document units for handles, ports, and edge lines.
 * @param selectedNodeIds nodes currently selected — only these expose resize handles.
 * @param selectedEdgeIds edges currently selected — only these expose waypoint/label handles.
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
        val route = edgeRoute(graph, edge, routes) ?: continue
        edge.labels.forEach { label ->
            if (distance(point, edgeLabelAnchorPoint(route, label)) <= tolerance) {
                return DiagramHit.LabelHandle(edge.id, label.position)
            }
        }
    }

    for (node in nodesTopDown) {
        node.ports.forEach { port ->
            if (distance(point, node.portPosition(port)) <= tolerance) {
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

    for (node in nodesTopDown) {
        if (node.bounds.contains(point)) {
            return DiagramHit.Node(node.id, nodeHitPart(node, point))
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
            if (port != null) node.portPosition(port) else node.bounds.center
        }
    }

/**
 * Anchor point of an edge label on a routed polyline: arc-length fraction 0.1 / 0.5 / 0.9
 * for SOURCE / MIDDLE / TARGET, plus the label's manual offset.
 */
fun edgeLabelAnchorPoint(route: List<DiagramPoint>, label: DiagramEdgeLabel): DiagramPoint {
    val fraction = when (label.position) {
        DiagramEdgeLabelPosition.SOURCE -> 0.1
        DiagramEdgeLabelPosition.MIDDLE -> 0.5
        DiagramEdgeLabelPosition.TARGET -> 0.9
    }
    val base = pointAlongPolyline(route, fraction)
    return DiagramPoint(base.x + label.offsetX, base.y + label.offsetY)
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
    val b = node.bounds
    return listOf(
        DiagramResizeHandle.TOP_LEFT to DiagramPoint(b.left, b.top),
        DiagramResizeHandle.TOP to DiagramPoint(b.centerX, b.top),
        DiagramResizeHandle.TOP_RIGHT to DiagramPoint(b.right, b.top),
        DiagramResizeHandle.RIGHT to DiagramPoint(b.right, b.centerY),
        DiagramResizeHandle.BOTTOM_RIGHT to DiagramPoint(b.right, b.bottom),
        DiagramResizeHandle.BOTTOM to DiagramPoint(b.centerX, b.bottom),
        DiagramResizeHandle.BOTTOM_LEFT to DiagramPoint(b.left, b.bottom),
        DiagramResizeHandle.LEFT to DiagramPoint(b.left, b.centerY),
    )
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
private fun trackIndex(sizes: List<Double>, extent: Double, local: Double): Int? {
    if (sizes.isEmpty()) return null
    val sum = sizes.sum()
    val scaled = if (sum > 0.0 && extent > 0.0) {
        sizes.map { it * extent / sum }
    } else {
        List(sizes.size) { if (extent > 0.0) extent / sizes.size else 0.0 }
    }
    var accumulated = 0.0
    scaled.forEachIndexed { index, size ->
        accumulated += size
        if (local <= accumulated) return index
    }
    return sizes.lastIndex
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
