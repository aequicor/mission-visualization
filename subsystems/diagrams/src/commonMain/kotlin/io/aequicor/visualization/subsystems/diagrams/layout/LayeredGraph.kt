package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * One slot of a layer: a real node, or a dummy vertex standing in for one intermediate
 * rank of a long edge (an edge spanning two or more layers). Dummies take part in the
 * ordering sweeps like real nodes and reserve a cross-axis corridor during coordinate
 * assignment, so a long edge gets a lane through every layer it passes instead of the
 * router having to squeeze it around nodes.
 */
internal data class LayerSlot(
    val key: String,
    val realId: DiagramNodeId?,
) {
    val isDummy: Boolean get() = realId == null
}

/**
 * A connected component's layer structure after dummy insertion: ordered slots per
 * layer plus up/down adjacency over slot keys (long links replaced by dummy chains).
 * Layer lists are mutable — the ordering phases permute them in place.
 */
internal class LayeredGraph(
    val layers: List<MutableList<LayerSlot>>,
    val up: Map<String, List<String>>,
    val down: Map<String, List<String>>,
) {
    val slots: List<LayerSlot> get() = layers.flatten()
}

/** Slot-level result of laying out one component: positions keyed by slot key. */
internal class ComponentLayout(
    val graph: LayeredGraph,
    val positions: Map<String, DiagramPoint>,
)

/**
 * Slot keys live in two structurally disjoint namespaces: `n<id>` for real nodes and
 * `d<linkIndex>:<rank>` for dummies. A dummy key carries no id content at all — node
 * ids are arbitrary strings (CNL quoted ids and JSON accept spaces and any other
 * character), so no separator scheme built from id substrings can be collision-free.
 * The link index into the deterministic sorted links list keeps dummy keys unique.
 */
internal fun realKey(id: DiagramNodeId): String = "n${id.value}"

/** Deterministic key of the dummy of link [linkIndex] (in the sorted links list) at [rank]. */
internal fun dummyKey(linkIndex: Int, rank: Int): String = "d$linkIndex:$rank"

/**
 * Builds the layered structure of one component: real nodes fill their assigned layers,
 * every link spanning two or more layers is replaced by a chain
 * `from → dummy(rank+1) → … → dummy(toRank-1) → to`, single-span links connect directly.
 * [links] must be acyclic (see [ScopeAdjacency.acyclicLinks]).
 */
internal fun buildLayeredGraph(
    members: List<DiagramNodeId>,
    links: List<ScopeLink>,
    layerOf: Map<DiagramNodeId, Int>,
): LayeredGraph {
    val layerCount = (members.maxOfOrNull { layerOf.getValue(it) } ?: 0) + 1
    val layers = List(layerCount) { mutableListOf<LayerSlot>() }
    for (member in members) layers[layerOf.getValue(member)] += LayerSlot(realKey(member), member)

    val up = mutableMapOf<String, MutableList<String>>()
    val down = mutableMapOf<String, MutableList<String>>()
    fun connect(from: String, to: String) {
        down.getOrPut(from) { mutableListOf() } += to
        up.getOrPut(to) { mutableListOf() } += from
    }

    links.forEachIndexed { linkIndex, link ->
        val fromLayer = layerOf.getValue(link.from)
        val toLayer = layerOf.getValue(link.to)
        var previous = realKey(link.from)
        for (rank in fromLayer + 1 until toLayer) {
            val key = dummyKey(linkIndex, rank)
            layers[rank] += LayerSlot(key, realId = null)
            connect(previous, key)
            previous = key
        }
        connect(previous, realKey(link.to))
    }
    return LayeredGraph(layers, up, down)
}
