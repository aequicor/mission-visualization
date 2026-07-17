package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
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
 * Whether the relation's notation points at the semantic parent: generalization and
 * realization run child → parent, and the layout wants the parent at the smaller rank
 * (inheritance points up), so their layout links flow parent → child.
 */
private val DiagramRelation.pointsAtParent: Boolean
    get() = this == DiagramRelation.Generalization || this == DiagramRelation.Realization

/**
 * Builds the adjacency of the scope owned by [parentId] (`null` = top level).
 * Edge endpoints attached to nested nodes are lifted to their ancestor inside the scope;
 * self-links and links leaving the scope are dropped. Generalization/realization edges
 * are oriented parent → child so the supertype ranks above its subtypes; every other
 * link touching a pair that already carries such an inheritance edge is snapped to the
 * same parent → child orientation, so a coexisting composition/dependency in the child →
 * parent direction cannot form an artificial 2-cycle that the cycle break would resolve
 * against the hierarchy. Members and links come out sorted by id for determinism.
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

    // Lift every surviving edge to a directed (from, to) pair inside the scope, with
    // inheritance already reversed to parent → child.
    val directed = edges.mapNotNull { edge ->
        val from = liftToScope(edge.source.attachedNodeId) ?: return@mapNotNull null
        val to = liftToScope(edge.target.attachedNodeId) ?: return@mapNotNull null
        when {
            from == to -> null
            edge.relation.pointsAtParent -> Triple(to, from, true)
            else -> Triple(from, to, false)
        }
    }

    // The forced orientation of any pair carrying an inheritance edge: parent → child.
    val inheritanceOrientation = directed
        .filter { it.third }
        .associate { setOf(it.first.value, it.second.value) to ScopeLink(it.first, it.second) }

    val links = directed
        .map { (from, to, _) ->
            inheritanceOrientation[setOf(from.value, to.value)] ?: ScopeLink(from, to)
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
 * Undirected connected components, largest first (ties by first member id). Members and
 * links keep their id-sorted order within each component.
 */
internal fun ScopeAdjacency.connectedComponents(): List<ScopeAdjacency> {
    val neighbors = mutableMapOf<DiagramNodeId, MutableList<DiagramNodeId>>()
    for (link in links) {
        neighbors.getOrPut(link.from) { mutableListOf() } += link.to
        neighbors.getOrPut(link.to) { mutableListOf() } += link.from
    }
    val componentOf = mutableMapOf<DiagramNodeId, Int>()
    var count = 0
    for (member in members) {
        if (member in componentOf) continue
        val stack = ArrayDeque<DiagramNodeId>()
        stack += member
        componentOf[member] = count
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (next in neighbors[current].orEmpty()) {
                if (next !in componentOf) {
                    componentOf[next] = count
                    stack += next
                }
            }
        }
        count++
    }
    if (count == 1) return listOf(this)
    return (0 until count)
        .map { index ->
            ScopeAdjacency(
                members = members.filter { componentOf.getValue(it) == index },
                links = links.filter { componentOf.getValue(it.from) == index },
            )
        }
        .sortedWith(
            compareByDescending<ScopeAdjacency> { it.members.size }
                .thenBy { it.members.first().value },
        )
}

/**
 * Semantic relations of the scope's lifted links, one entry per surviving edge (before
 * [ScopeAdjacency]'s dedup): endpoints lifted to the scope like [scopeAdjacency] does,
 * self-links and links leaving the scope dropped. Feeds [classifyTopology]'s ER share.
 */
internal fun DiagramGraph.scopeLinkRelations(parentId: DiagramNodeId?): List<DiagramRelation> {
    val memberSet = nodes.filter { it.parentId == parentId }.map { it.id }.toSet()

    fun liftToScope(id: DiagramNodeId?): DiagramNodeId? {
        var current = id
        while (current != null && current !in memberSet) {
            current = nodeById(current)?.parentId
        }
        return current
    }

    return edges.mapNotNull { edge ->
        val from = liftToScope(edge.source.attachedNodeId) ?: return@mapNotNull null
        val to = liftToScope(edge.target.attachedNodeId) ?: return@mapNotNull null
        if (from == to) null else edge.relation
    }
}

/**
 * Runs [positionsFor] on every container scope top-down (root scope first, then each
 * container's children inside the container's new position). Only node x/y change;
 * edges, ids, z-order and structure stay intact. Moving a member drags its whole
 * container subtree along.
 *
 * Scope origin: the root scope keeps its previous bounding-box top-left (the diagram
 * does not fly away); a container scope starts at the container's top-left plus
 * [DiagramLayoutConfig.containerPadding] — **unless** [keepScopeOrigin] is set, when
 * every scope (containers included) keeps its own previous bounding-box top-left. The
 * position-preserving Tidy pass uses that so container children stay where the author
 * put them instead of snapping to the padded corner.
 */
internal fun layoutHierarchically(
    graph: DiagramGraph,
    config: DiagramLayoutConfig,
    keepScopeOrigin: Boolean = false,
    positionsFor: (
        parentId: DiagramNodeId?,
        nodesById: Map<DiagramNodeId, DiagramNode>,
        adjacency: ScopeAdjacency,
    ) -> Map<DiagramNodeId, DiagramPoint>,
): DiagramGraph {
    if (graph.nodes.isEmpty()) return graph
    val current = graph.nodes.associateBy { it.id }.toMutableMap()

    fun placeScope(parentId: DiagramNodeId?) {
        val adjacency = graph.scopeAdjacency(parentId)
        if (adjacency.members.isEmpty()) return
        val membersById = adjacency.members.associateWith { current.getValue(it) }
        val local = positionsFor(parentId, membersById, adjacency)

        val originX: Double
        val originY: Double
        if (parentId == null || keepScopeOrigin) {
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
