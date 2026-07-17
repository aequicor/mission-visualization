package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Layered (Sugiyama) auto-layout for class/component/deployment/flowchart diagrams.
 *
 * Pipeline: undirected connected-component split → per component: DFS cycle breaking →
 * longest-path layer assignment → dummy vertices for edges spanning two or more layers
 * (they order like nodes and reserve a cross-axis corridor for the edge) → crossing
 * minimization (BFS-seeded weighted-median sweeps + exact-count transpose, keep-best;
 * see [minimizeCrossings]) → coordinates with even gaps, every layer centered on the
 * common axis → shelf-packing of the component blocks, so disconnected clusters and
 * isolated nodes do not inflate layer 0 of the main flow.
 *
 * Only node positions change; edges are untouched (the router re-routes them).
 * Deterministic: identical input always yields the identical output.
 */
fun layeredLayout(
    graph: DiagramGraph,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph = layoutHierarchically(graph, config) { _, nodesById, adjacency ->
    layeredPositions(nodesById, adjacency, config)
}

/** Scope-local layered positions: top-left corners in local coordinates starting at (0,0). */
internal fun layeredPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
    if (adjacency.members.isEmpty()) return emptyMap()
    val components = adjacency.connectedComponents()
    if (components.size == 1) return componentPositions(nodesById, components.single(), config)

    val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT

    // Extents are measured over ALL slots — a trailing dummy corridor is part of the
    // component's block, otherwise the next packed component would sit in the lane
    // reserved for a long edge.
    val blocks = components.map { component ->
        val layout = layoutComponent(nodesById, component, config)
        var mainMax = 0.0
        var crossMax = 0.0
        for (slot in layout.graph.slots) {
            val point = layout.positions.getValue(slot.key)
            val node = slot.realId?.let(nodesById::getValue)
            val width = node?.width ?: if (horizontalFlow) 0.0 else config.dummySize
            val height = node?.height ?: if (horizontalFlow) config.dummySize else 0.0
            mainMax = maxOf(mainMax, if (horizontalFlow) point.x + width else point.y + height)
            crossMax = maxOf(crossMax, if (horizontalFlow) point.y + height else point.x + width)
        }
        ComponentBlock(realPositions(layout), mainMax, crossMax)
    }
    return packComponentBlocks(blocks, config)
}

/** Layered positions of one connected component, local coordinates starting at (0,0). */
private fun componentPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> = realPositions(layoutComponent(nodesById, adjacency, config))

/** Real-node positions of a slot-level layout (dummies dropped). */
private fun realPositions(layout: ComponentLayout): Map<DiagramNodeId, DiagramPoint> = buildMap {
    for (slot in layout.graph.slots) {
        val id = slot.realId ?: continue
        put(id, layout.positions.getValue(slot.key))
    }
}

/**
 * Slot-level layered layout of one connected component: longest-path layering, dummy
 * insertion for long edges, BFS-seeded crossing minimization ([minimizeCrossings]),
 * and coordinates. Dummies are part of the returned positions — the seam tests use to
 * see reserved corridors.
 */
internal fun layoutComponent(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): ComponentLayout {
    val links = adjacency.acyclicLinks()
    val predecessors = links.groupBy({ it.to }, { it.from })
    val layerAssignments = assignLongestPathLayers(adjacency.members, predecessors)
    val layered = buildLayeredGraph(adjacency.members, links, layerAssignments)
    seedLayersByBfs(layered)
    minimizeCrossings(layered)
    return ComponentLayout(layered, slotPositions(layered, nodesById, config))
}

/**
 * Reorders every layer by BFS discovery over the down links from the sources
 * (key-sorted, neighbors visited in key order): connected runs start out near each
 * other instead of scattered by id, giving the crossing-minimization sweeps a better start.
 */
private fun seedLayersByBfs(layered: LayeredGraph) {
    val allKeys = layered.slots.map { it.key }
    val order = mutableListOf<String>()
    val enqueued = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    for (key in allKeys.sorted()) {
        if (layered.up[key].orEmpty().isEmpty() && enqueued.add(key)) queue += key
    }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        order += current
        for (next in layered.down[current].orEmpty().sorted()) {
            if (enqueued.add(next)) queue += next
        }
    }
    // Safety net for slots not reached from a source (cannot happen after cycle
    // breaking, which leaves every component with at least one source).
    for (key in allKeys) {
        if (enqueued.add(key)) order += key
    }
    val rank = order.withIndex().associate { (index, key) -> key to index }
    for (layer in layered.layers) layer.sortBy { rank.getValue(it.key) }
}

/**
 * Coordinates: layers advance along the flow axis; across it every slot takes its
 * Brandes–Köpf center ([assignCrossCenters]) — aligned parent/child runs and long-edge
 * dummy chains share one coordinate instead of stairstepping. A dummy occupies
 * [DiagramLayoutConfig.dummySize] across the flow and nothing along it.
 */
private fun slotPositions(
    layered: LayeredGraph,
    nodesById: Map<DiagramNodeId, DiagramNode>,
    config: DiagramLayoutConfig,
): Map<String, DiagramPoint> {
    val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT
    fun mainExtent(slot: LayerSlot): Double = slot.realId?.let {
        val node = nodesById.getValue(it)
        if (horizontalFlow) node.width else node.height
    } ?: 0.0

    fun crossExtent(slot: LayerSlot): Double = slot.realId?.let {
        val node = nodesById.getValue(it)
        if (horizontalFlow) node.height else node.width
    } ?: config.dummySize

    val centers = assignCrossCenters(layered, config.nodeGap, ::crossExtent)
    val minEdge = layered.slots.minOf { centers.getValue(it.key) - crossExtent(it) / 2.0 }

    val layers = layered.layers
    val layerMainExtents = layers.map { layer -> layer.maxOf(::mainExtent) }
    val positions = mutableMapOf<String, DiagramPoint>()
    var main = 0.0
    layers.forEachIndexed { layerIndex, layer ->
        for (slot in layer) {
            val slotMain = main + (layerMainExtents[layerIndex] - mainExtent(slot)) / 2.0
            val cross = centers.getValue(slot.key) - crossExtent(slot) / 2.0 - minEdge
            positions[slot.key] = if (horizontalFlow) {
                DiagramPoint(x = slotMain, y = cross)
            } else {
                DiagramPoint(x = cross, y = slotMain)
            }
        }
        main += layerMainExtents[layerIndex] + config.layerGap
    }
    return positions
}
