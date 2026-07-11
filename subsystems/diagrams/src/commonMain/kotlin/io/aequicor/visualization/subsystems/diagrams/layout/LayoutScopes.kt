package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.attachedNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Internal plumbing shared by the layout algorithms: extracting the flat graph of one
 * container scope, breaking cycles, and applying scope-local positions back to the
 * document (shifting container subtrees along with their parents).
 *
 * Determinism: members and links are always sorted by id, so every algorithm downstream
 * sees a canonical order regardless of the input list order.
 */

/** A directed connection between two members of the same scope (lifted from real edges). */
internal data class ScopeLink(
    val from: DiagramNodeId,
    val to: DiagramNodeId,
)

/** The flat adjacency of one container scope: direct children + lifted edges between them. */
internal data class ScopeAdjacency(
    val members: List<DiagramNodeId>,
    val links: List<ScopeLink>,
)

/**
 * Builds the adjacency of the scope owned by [parentId] (`null` = top level).
 * Edge endpoints attached to nested nodes are lifted to their ancestor inside the scope;
 * self-links and links leaving the scope are dropped. Members and links come out sorted
 * by id for determinism.
 */
internal fun DiagramGraph.scopeAdjacency(parentId: DiagramNodeId?): ScopeAdjacency {
    val members = nodes
        .filter { it.parentId == parentId }
        .map { it.id }
        .sortedBy { it.value }
    val memberSet = members.toSet()

    fun liftToScope(id: DiagramNodeId?): DiagramNodeId? {
        var current = id
        while (current != null && current !in memberSet) {
            current = nodeById(current)?.parentId
        }
        return current
    }

    val links = edges
        .mapNotNull { edge ->
            val from = liftToScope(edge.source.attachedNodeId) ?: return@mapNotNull null
            val to = liftToScope(edge.target.attachedNodeId) ?: return@mapNotNull null
            if (from == to) null else ScopeLink(from, to)
        }
        .distinct()
        .sortedWith(compareBy({ it.from.value }, { it.to.value }))
    return ScopeAdjacency(members = members, links = links)
}

/**
 * Removes back links found by a DFS over members in sorted order, so the remaining
 * links form a DAG. Deterministic: the same graph always drops the same links.
 */
internal fun ScopeAdjacency.acyclicLinks(): List<ScopeLink> {
    val outgoing = links.groupBy { it.from }
    // 0 = unvisited, 1 = on the current DFS path, 2 = finished.
    val state = mutableMapOf<DiagramNodeId, Int>()
    val backLinks = mutableSetOf<ScopeLink>()

    fun visit(id: DiagramNodeId) {
        state[id] = 1
        for (link in outgoing[id].orEmpty()) {
            when (state[link.to] ?: 0) {
                0 -> visit(link.to)
                1 -> backLinks += link
            }
        }
        state[id] = 2
    }

    for (member in members) {
        if ((state[member] ?: 0) == 0) visit(member)
    }
    return links.filter { it !in backLinks }
}

/** Whether the scope is a forest: acyclic and no member has more than one incoming link. */
internal fun ScopeAdjacency.isForest(): Boolean =
    acyclicLinks().size == links.size &&
        links.groupingBy { it.to }.eachCount().values.all { it == 1 }

/**
 * Runs [positionsFor] on every container scope top-down (root scope first, then each
 * container's children inside the container's new position). Only node x/y change;
 * edges, ids, z-order and structure stay intact. Moving a member drags its whole
 * container subtree along.
 *
 * Scope origin: the root scope keeps its previous bounding-box top-left (the diagram
 * does not fly away); a container scope starts at the container's top-left plus
 * [DiagramLayoutConfig.containerPadding].
 */
internal fun layoutHierarchically(
    graph: DiagramGraph,
    config: DiagramLayoutConfig,
    positionsFor: (Map<DiagramNodeId, DiagramNode>, ScopeAdjacency) -> Map<DiagramNodeId, DiagramPoint>,
): DiagramGraph {
    if (graph.nodes.isEmpty()) return graph
    val current = graph.nodes.associateBy { it.id }.toMutableMap()

    fun placeScope(parentId: DiagramNodeId?) {
        val adjacency = graph.scopeAdjacency(parentId)
        if (adjacency.members.isEmpty()) return
        val membersById = adjacency.members.associateWith { current.getValue(it) }
        val local = positionsFor(membersById, adjacency)

        val originX: Double
        val originY: Double
        if (parentId == null) {
            originX = adjacency.members.minOf { current.getValue(it).x }
            originY = adjacency.members.minOf { current.getValue(it).y }
        } else {
            val parent = current.getValue(parentId)
            originX = parent.x + config.containerPadding
            originY = parent.y + config.containerPadding
        }

        for (id in adjacency.members) {
            val node = current.getValue(id)
            val target = local[id] ?: continue
            val dx = originX + target.x - node.x
            val dy = originY + target.y - node.y
            if (dx == 0.0 && dy == 0.0) continue
            for (movedId in graph.subtreeIds(id)) {
                val moved = current.getValue(movedId)
                current[movedId] = moved.copy(x = moved.x + dx, y = moved.y + dy)
            }
        }

        for (id in adjacency.members) placeScope(id)
    }

    placeScope(null)
    return graph.copy(nodes = graph.nodes.map { current.getValue(it.id) })
}
