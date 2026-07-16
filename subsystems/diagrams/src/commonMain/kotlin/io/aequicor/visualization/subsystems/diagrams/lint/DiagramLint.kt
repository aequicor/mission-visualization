package io.aequicor.visualization.subsystems.diagrams.lint

import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAnchorPoint
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelAvoidRects
import io.aequicor.visualization.subsystems.diagrams.hittest.edgeLabelObstacleRoutes
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.diagrams.routing.RoutedEdge
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdgesLenient
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Vision-test: formalized "good taste" rules for rendered diagrams. [lintDiagram] takes
 * a graph plus its routed edges and reports where the picture stops reading well —
 * lines cutting through nodes, many arrows funneling into one spot, edges lying on top
 * of each other, crossing pile-ups, and labels covering foreign nodes. Pure and
 * deterministic; findings are ordered by rule, then by the ids involved.
 */
data class DiagramLintOptions(
    /**
     * Two edge endpoints closer than this on one node count as the same funnel spot
     * (kept below the router's anchorSeparation so properly spread anchors never match).
     */
    val anchorBunchRadius: Double = 20.0,
    /** Endpoints per funnel spot from which a bunch is reported. */
    val anchorBunchLimit: Int = 3,
    /** Max distance between near-parallel segments that still counts as overlap. */
    val overlapDistance: Double = 2.0,
    /** Shared span (document units) from which co-running segments count as overlap. */
    val overlapMinLength: Double = 12.0,
    /** Crossing points closer than this cluster into one hotspot. */
    val hotspotRadius: Double = 32.0,
    /** Crossings per cluster from which a hotspot is reported. */
    val hotspotLimit: Int = 3,
    /** Approximate glyph width used to estimate label boxes. */
    val labelCharWidth: Double = 7.0,
    /** Approximate label box height. */
    val labelHeight: Double = 16.0,
) {
    companion object {
        val Default: DiagramLintOptions = DiagramLintOptions()
    }
}

/** One vision-test finding; [message] is a ready human-readable warning line. */
sealed interface DiagramLintFinding {
    val message: String

    /** Two connected nodes' bodies overlap (neither fully contains the other). */
    data class NodeOverlap(
        val first: DiagramNodeId,
        val second: DiagramNodeId,
    ) : DiagramLintFinding {
        override val message: String
            get() = "nodes '${first.value}' and '${second.value}' overlap"
    }

    /** A routed edge passes through the interior of a node it is not attached to. */
    data class EdgeThroughNode(
        val edgeId: DiagramEdgeId,
        val nodeId: DiagramNodeId,
    ) : DiagramLintFinding {
        override val message: String
            get() = "edge '${edgeId.value}' cuts through node '${nodeId.value}'"
    }

    /** Too many edge endpoints funnel into one spot on a node. */
    data class AnchorBunch(
        val nodeId: DiagramNodeId,
        val edgeIds: List<DiagramEdgeId>,
        val at: DiagramPoint,
    ) : DiagramLintFinding {
        override val message: String
            get() = "${edgeIds.size} edges funnel into one spot on '${nodeId.value}' " +
                "(${edgeIds.joinToString(", ") { it.value }})"
    }

    /** Two edges run on top of each other for a visible stretch. */
    data class EdgeOverlap(
        val first: DiagramEdgeId,
        val second: DiagramEdgeId,
        val length: Double,
    ) : DiagramLintFinding {
        override val message: String
            get() = "edges '${first.value}' and '${second.value}' overlap for ${length.toInt()} units"
    }

    /** Many crossings piled into one small area. */
    data class CrossingHotspot(
        val at: DiagramPoint,
        val count: Int,
        val edgeIds: List<DiagramEdgeId>,
    ) : DiagramLintFinding {
        override val message: String
            get() = "$count edge crossings pile up near (${at.x.toInt()}, ${at.y.toInt()})"
    }

    /** An edge label sits on top of a node the edge is not attached to. */
    data class LabelOverNode(
        val edgeId: DiagramEdgeId,
        val nodeId: DiagramNodeId,
    ) : DiagramLintFinding {
        override val message: String
            get() = "label of edge '${edgeId.value}' covers node '${nodeId.value}'"
    }
}

/** Runs every vision-test rule; [routes] defaults to routing the graph leniently. */
fun lintDiagram(
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, RoutedEdge> = routeAllEdgesLenient(graph),
    options: DiagramLintOptions = DiagramLintOptions.Default,
): List<DiagramLintFinding> {
    val ordered = graph.edges.mapNotNull { routes[it.id] }
    return buildList {
        addAll(nodeOverlapFindings(graph))
        addAll(edgeThroughNodeFindings(graph, ordered))
        addAll(anchorBunchFindings(graph, ordered, options))
        addAll(edgeOverlapFindings(ordered, options))
        addAll(crossingHotspotFindings(ordered, options))
        addAll(labelOverNodeFindings(graph, routes, options))
    }
}

/** Plain-text report of [lintDiagram] findings (one warning per line). */
fun List<DiagramLintFinding>.lintReport(): String =
    if (isEmpty()) "vision-test: clean" else joinToString("\n") { "warning: ${it.message}" }

// --- rule: node bodies overlapping -------------------------------------------------------

private fun nodeOverlapFindings(graph: DiagramGraph): List<DiagramLintFinding> {
    val connectedIds = buildSet {
        for (edge in graph.edges) {
            edge.source.attachedNodeId?.let(::add)
            edge.target.attachedNodeId?.let(::add)
        }
    }
    val nodes = graph.nodes.filter { it.visible && it.id in connectedIds }
    return buildList {
        for (first in nodes.indices) {
            for (second in first + 1 until nodes.size) {
                val a = nodes[first].bounds
                val b = nodes[second].bounds
                val intersects = a.left < b.right - 1.0 && b.left < a.right - 1.0 &&
                    a.top < b.bottom - 1.0 && b.top < a.bottom - 1.0
                if (!intersects) continue
                // Full containment is deliberate grouping, not a collision.
                val aInsideB = a.left >= b.left && a.right <= b.right && a.top >= b.top && a.bottom <= b.bottom
                val bInsideA = b.left >= a.left && b.right <= a.right && b.top >= a.top && b.bottom <= a.bottom
                if (aInsideB || bInsideA) continue
                add(DiagramLintFinding.NodeOverlap(nodes[first].id, nodes[second].id))
            }
        }
    }
}

// --- rule: edges through nodes ----------------------------------------------------------

private fun edgeThroughNodeFindings(
    graph: DiagramGraph,
    routes: List<RoutedEdge>,
): List<DiagramLintFinding> {
    val connectedIds = buildSet {
        for (edge in graph.edges) {
            edge.source.attachedNodeId?.let(::add)
            edge.target.attachedNodeId?.let(::add)
        }
    }
    val obstacles = graph.nodes.filter { it.visible && it.id in connectedIds }
    val endpointsByEdge = graph.edges.associate { edge ->
        edge.id to setOfNotNull(edge.source.attachedNodeId, edge.target.attachedNodeId)
    }
    return buildList {
        for (route in routes) {
            val own = endpointsByEdge[route.edgeId].orEmpty()
            for (node in obstacles) {
                if (node.id in own) continue
                val bounds = node.bounds
                val hit = route.points.zipWithNext().any { (a, b) ->
                    segmentCrossesInterior(a, b, bounds)
                }
                if (hit) add(DiagramLintFinding.EdgeThroughNode(route.edgeId, node.id))
            }
        }
    }
}

/** Whether segment `a..b` passes through the strict interior of [rect]. */
private fun segmentCrossesInterior(a: DiagramPoint, b: DiagramPoint, rect: DiagramRect): Boolean {
    val inset = 1.0
    val left = rect.left + inset
    val right = rect.right - inset
    val top = rect.top + inset
    val bottom = rect.bottom - inset
    if (left >= right || top >= bottom) return false
    // Liang–Barsky clip: a positive-length parameter range inside means the segment enters.
    var t0 = 0.0
    var t1 = 1.0
    val dx = b.x - a.x
    val dy = b.y - a.y
    val checks = listOf(
        -dx to (a.x - left),
        dx to (right - a.x),
        -dy to (a.y - top),
        dy to (bottom - a.y),
    )
    for ((p, q) in checks) {
        if (abs(p) < 1e-12) {
            if (q < 0) return false
        } else {
            val r = q / p
            if (p < 0) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
        }
    }
    return t1 - t0 > 1e-9
}

// --- rule: anchor bunches ---------------------------------------------------------------

private fun anchorBunchFindings(
    graph: DiagramGraph,
    routes: List<RoutedEdge>,
    options: DiagramLintOptions,
): List<DiagramLintFinding> {
    data class Attachment(val edgeId: DiagramEdgeId, val at: DiagramPoint)

    val routesById = routes.associateBy { it.edgeId }
    val perNode = mutableMapOf<DiagramNodeId, MutableList<Attachment>>()
    for (edge in graph.edges) {
        val route = routesById[edge.id] ?: continue
        edge.source.attachedNodeId?.let {
            perNode.getOrPut(it) { mutableListOf() } += Attachment(edge.id, route.sourcePoint)
        }
        edge.target.attachedNodeId?.let {
            perNode.getOrPut(it) { mutableListOf() } += Attachment(edge.id, route.targetPoint)
        }
    }
    return buildList {
        for ((nodeId, attachments) in perNode.entries.sortedBy { it.key.value }) {
            if (attachments.size < options.anchorBunchLimit) continue
            // Single-link clustering over attachment points.
            val clusterOf = IntArray(attachments.size) { it }
            fun rootOf(index: Int): Int {
                var current = index
                while (clusterOf[current] != current) current = clusterOf[current]
                return current
            }
            for (first in attachments.indices) {
                for (second in first + 1 until attachments.size) {
                    val a = attachments[first].at
                    val b = attachments[second].at
                    if (hypot(a.x - b.x, a.y - b.y) < options.anchorBunchRadius) {
                        clusterOf[rootOf(second)] = rootOf(first)
                    }
                }
            }
            val clusters = attachments.indices.groupBy(::rootOf)
            for ((_, members) in clusters.entries.sortedBy { it.key }) {
                if (members.size < options.anchorBunchLimit) continue
                val points = members.map { attachments[it].at }
                val center = DiagramPoint(
                    points.sumOf { it.x } / points.size,
                    points.sumOf { it.y } / points.size,
                )
                add(
                    DiagramLintFinding.AnchorBunch(
                        nodeId = nodeId,
                        edgeIds = members.map { attachments[it].edgeId },
                        at = center,
                    ),
                )
            }
        }
    }
}

// --- rule: edge-on-edge overlap ---------------------------------------------------------

private fun edgeOverlapFindings(
    routes: List<RoutedEdge>,
    options: DiagramLintOptions,
): List<DiagramLintFinding> = buildList {
    for (first in routes.indices) {
        for (second in first + 1 until routes.size) {
            var longest = 0.0
            for ((a1, a2) in routes[first].points.zipWithNext()) {
                for ((b1, b2) in routes[second].points.zipWithNext()) {
                    longest = maxOf(longest, coRunLength(a1, a2, b1, b2, options.overlapDistance))
                }
            }
            if (longest >= options.overlapMinLength) {
                add(
                    DiagramLintFinding.EdgeOverlap(
                        first = routes[first].edgeId,
                        second = routes[second].edgeId,
                        length = longest,
                    ),
                )
            }
        }
    }
}

/**
 * Length of the stretch where segment `b1..b2` runs alongside `a1..a2`: both segments
 * near-parallel, closer than [maxDistance], measured as the overlap of their projections.
 */
private fun coRunLength(
    a1: DiagramPoint,
    a2: DiagramPoint,
    b1: DiagramPoint,
    b2: DiagramPoint,
    maxDistance: Double,
): Double {
    val ax = a2.x - a1.x
    val ay = a2.y - a1.y
    val aLength = hypot(ax, ay)
    if (aLength < 1e-9) return 0.0
    val ux = ax / aLength
    val uy = ay / aLength
    fun offsetOf(p: DiagramPoint): Double = (p.x - a1.x) * -uy + (p.y - a1.y) * ux
    val offset1 = offsetOf(b1)
    val offset2 = offsetOf(b2)
    if (abs(offset1) > maxDistance || abs(offset2) > maxDistance) return 0.0
    fun alongOf(p: DiagramPoint): Double = (p.x - a1.x) * ux + (p.y - a1.y) * uy
    val bStart = minOf(alongOf(b1), alongOf(b2))
    val bEnd = maxOf(alongOf(b1), alongOf(b2))
    return maxOf(0.0, minOf(aLength, bEnd) - maxOf(0.0, bStart))
}

// --- rule: crossing hotspots ------------------------------------------------------------

private fun crossingHotspotFindings(
    routes: List<RoutedEdge>,
    options: DiagramLintOptions,
): List<DiagramLintFinding> {
    data class Crossing(val at: DiagramPoint, val first: DiagramEdgeId, val second: DiagramEdgeId)

    val crossings = buildList {
        for (first in routes.indices) {
            for (second in first + 1 until routes.size) {
                for ((a1, a2) in routes[first].points.zipWithNext()) {
                    for ((b1, b2) in routes[second].points.zipWithNext()) {
                        val at = segmentIntersection(a1, a2, b1, b2) ?: continue
                        add(Crossing(at, routes[first].edgeId, routes[second].edgeId))
                    }
                }
            }
        }
    }
    if (crossings.isEmpty()) return emptyList()
    val clusterOf = IntArray(crossings.size) { it }
    fun rootOf(index: Int): Int {
        var current = index
        while (clusterOf[current] != current) current = clusterOf[current]
        return current
    }
    for (first in crossings.indices) {
        for (second in first + 1 until crossings.size) {
            val a = crossings[first].at
            val b = crossings[second].at
            if (hypot(a.x - b.x, a.y - b.y) <= options.hotspotRadius) {
                clusterOf[rootOf(second)] = rootOf(first)
            }
        }
    }
    return buildList {
        for ((_, members) in crossings.indices.groupBy(::rootOf).entries.sortedBy { it.key }) {
            if (members.size < options.hotspotLimit) continue
            val points = members.map { crossings[it].at }
            val center = DiagramPoint(
                points.sumOf { it.x } / points.size,
                points.sumOf { it.y } / points.size,
            )
            val edges = members
                .flatMap { listOf(crossings[it].first, crossings[it].second) }
                .distinctBy { it.value }
                .sortedBy { it.value }
            add(DiagramLintFinding.CrossingHotspot(at = center, count = members.size, edgeIds = edges))
        }
    }
}

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
    val margin = 1e-3
    if (t <= margin || t >= 1.0 - margin || u <= margin || u >= 1.0 - margin) return null
    return DiagramPoint(a1.x + rx * t, a1.y + ry * t)
}

// --- rule: labels over foreign nodes ----------------------------------------------------

private fun labelOverNodeFindings(
    graph: DiagramGraph,
    routes: Map<DiagramEdgeId, RoutedEdge>,
    options: DiagramLintOptions,
): List<DiagramLintFinding> {
    val routePoints = routes.mapValues { it.value.points }
    val connectedIds = buildSet {
        for (edge in graph.edges) {
            edge.source.attachedNodeId?.let(::add)
            edge.target.attachedNodeId?.let(::add)
        }
    }
    val obstacles = graph.nodes.filter { it.visible && it.id in connectedIds }
    return buildList {
        for (edge in graph.edges) {
            val route = routePoints[edge.id] ?: continue
            if (route.size < 2) continue
            val own = setOfNotNull(edge.source.attachedNodeId, edge.target.attachedNodeId)
            for (label in edge.labels) {
                if (label.label.text.isBlank()) continue
                val anchor = edgeLabelAnchorPoint(route, label, edgeLabelObstacleRoutes(graph, routePoints, edge.id), edgeLabelAvoidRects(graph, edge.id))
                val halfWidth = label.label.text.length * options.labelCharWidth / 2.0
                val halfHeight = options.labelHeight / 2.0
                for (node in obstacles) {
                    if (node.id in own) continue
                    val bounds = node.bounds
                    val overlaps = anchor.x + halfWidth > bounds.left + 1.0 &&
                        anchor.x - halfWidth < bounds.right - 1.0 &&
                        anchor.y + halfHeight > bounds.top + 1.0 &&
                        anchor.y - halfHeight < bounds.bottom - 1.0
                    if (overlaps) add(DiagramLintFinding.LabelOverNode(edge.id, node.id))
                }
            }
        }
    }
}
