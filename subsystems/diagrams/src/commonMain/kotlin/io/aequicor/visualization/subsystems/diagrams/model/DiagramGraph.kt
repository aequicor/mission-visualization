package io.aequicor.visualization.subsystems.diagrams.model

/** A named layer; nodes/edges reference it via `layerId`. */
data class DiagramLayer(
    val id: DiagramLayerId,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
)

/** A selection group of nodes (transient grouping, unlike container parent/child nesting). */
data class DiagramGroup(
    val id: DiagramGroupId,
    val memberIds: List<DiagramNodeId>,
    val name: String? = null,
) {
    init {
        require(memberIds.isNotEmpty()) { "group ${id.value} must have at least one member" }
        require(memberIds.toSet().size == memberIds.size) {
            "group ${id.value} has duplicate members"
        }
    }
}

/**
 * The whole diagram: nodes, edges, layers, groups.
 *
 * Z-order conventions:
 * - [layers] are ordered bottom → top; nodes/edges with `layerId == null` belong to the
 *   implicit default layer below all explicit layers.
 * - within a layer, [nodes] list order is the z-order (earlier = further back); edges of
 *   a layer render above that layer's nodes.
 */
data class DiagramGraph(
    val nodes: List<DiagramNode> = emptyList(),
    val edges: List<DiagramEdge> = emptyList(),
    val layers: List<DiagramLayer> = emptyList(),
    val groups: List<DiagramGroup> = emptyList(),
) {
    init {
        require(nodes.map { it.id }.toSet().size == nodes.size) { "duplicate node ids" }
        require(edges.map { it.id }.toSet().size == edges.size) { "duplicate edge ids" }
        require(layers.map { it.id }.toSet().size == layers.size) { "duplicate layer ids" }
        require(groups.map { it.id }.toSet().size == groups.size) { "duplicate group ids" }
    }

    fun nodeById(id: DiagramNodeId): DiagramNode? = nodes.firstOrNull { it.id == id }

    fun edgeById(id: DiagramEdgeId): DiagramEdge? = edges.firstOrNull { it.id == id }

    fun layerById(id: DiagramLayerId): DiagramLayer? = layers.firstOrNull { it.id == id }

    fun groupById(id: DiagramGroupId): DiagramGroup? = groups.firstOrNull { it.id == id }

    /** Direct children of a container node. */
    fun childrenOf(id: DiagramNodeId): List<DiagramNode> = nodes.filter { it.parentId == id }

    /** Edges attached (floating or fixed) to the node. */
    fun edgesConnectedTo(id: DiagramNodeId): List<DiagramEdge> = edges.filter { edge ->
        edge.source.attachedNodeId == id || edge.target.attachedNodeId == id
    }

    /** The node plus all transitive children (container subtree). */
    fun subtreeIds(id: DiagramNodeId): Set<DiagramNodeId> {
        val result = mutableSetOf(id)
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            frontier = nodes
                .filter { it.parentId in frontier && it.id !in result }
                .map { it.id }
                .toSet()
            result += frontier
        }
        return result
    }

    companion object {
        val Empty: DiagramGraph = DiagramGraph()
    }
}

/** Adds the node, or replaces the existing node with the same id in place (z-order kept). */
fun DiagramGraph.withNode(node: DiagramNode): DiagramGraph = copy(
    nodes = if (nodes.any { it.id == node.id }) {
        nodes.map { if (it.id == node.id) node else it }
    } else {
        nodes + node
    },
)

/** Adds the edge, or replaces the existing edge with the same id in place. */
fun DiagramGraph.withEdge(edge: DiagramEdge): DiagramGraph = copy(
    edges = if (edges.any { it.id == edge.id }) {
        edges.map { if (it.id == edge.id) edge else it }
    } else {
        edges + edge
    },
)

/** Adds the layer, or replaces the existing layer with the same id in place. */
fun DiagramGraph.withLayer(layer: DiagramLayer): DiagramGraph = copy(
    layers = if (layers.any { it.id == layer.id }) {
        layers.map { if (it.id == layer.id) layer else it }
    } else {
        layers + layer
    },
)

/** Adds the group, or replaces the existing group with the same id in place. */
fun DiagramGraph.withGroup(group: DiagramGroup): DiagramGraph = copy(
    groups = if (groups.any { it.id == group.id }) {
        groups.map { if (it.id == group.id) group else it }
    } else {
        groups + group
    },
)

/** Applies [transform] to the node with [id]; no-op if absent. The id must not change. */
fun DiagramGraph.updateNode(
    id: DiagramNodeId,
    transform: (DiagramNode) -> DiagramNode,
): DiagramGraph = copy(
    nodes = nodes.map { node ->
        if (node.id == id) {
            transform(node).also {
                require(it.id == id) { "updateNode must not change the node id" }
            }
        } else {
            node
        }
    },
)

/** Applies [transform] to the edge with [id]; no-op if absent. The id must not change. */
fun DiagramGraph.updateEdge(
    id: DiagramEdgeId,
    transform: (DiagramEdge) -> DiagramEdge,
): DiagramGraph = copy(
    edges = edges.map { edge ->
        if (edge.id == id) {
            transform(edge).also {
                require(it.id == id) { "updateEdge must not change the edge id" }
            }
        } else {
            edge
        }
    },
)

/**
 * Removes the node **cascading**: its whole container subtree, every edge attached to a
 * removed node, and group memberships (groups left empty are dropped). No-op if absent.
 */
fun DiagramGraph.removeNode(id: DiagramNodeId): DiagramGraph {
    if (nodes.none { it.id == id }) return this
    val removed = subtreeIds(id)
    return copy(
        nodes = nodes.filter { it.id !in removed },
        edges = edges.filter { edge ->
            edge.source.attachedNodeId !in removed && edge.target.attachedNodeId !in removed
        },
        groups = groups.mapNotNull { group ->
            val remaining = group.memberIds.filter { it !in removed }
            when {
                remaining.size == group.memberIds.size -> group
                remaining.isEmpty() -> null
                else -> group.copy(memberIds = remaining)
            }
        },
    )
}

/** Removes the edge; no-op if absent. */
fun DiagramGraph.removeEdge(id: DiagramEdgeId): DiagramGraph =
    copy(edges = edges.filter { it.id != id })
