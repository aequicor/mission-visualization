package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Layered (Sugiyama-lite) auto-layout for class/component/deployment/flowchart diagrams.
 *
 * Pipeline: DFS cycle breaking → longest-path layer assignment → barycenter ordering
 * inside layers (two down/up sweep rounds) → coordinates with even gaps, every layer
 * centered on the common axis. Container children are laid out inside their parent.
 *
 * Only node positions change; edges are untouched (the router re-routes them).
 * Deterministic: identical input always yields the identical output.
 */
fun layeredLayout(
    graph: DiagramGraph,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph = layoutHierarchically(graph, config) { nodesById, adjacency ->
    layeredPositions(nodesById, adjacency, config)
}

/** Scope-local layered positions: top-left corners in local coordinates starting at (0,0). */
internal fun layeredPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
    if (adjacency.members.isEmpty()) return emptyMap()
    val links = adjacency.acyclicLinks()
    val predecessors = links.groupBy({ it.to }, { it.from })
    val successors = links.groupBy({ it.from }, { it.to })

    // Longest-path layering: sources at layer 0, layer(n) = max(layer(pred)) + 1.
    val layerAssignments = mutableMapOf<DiagramNodeId, Int>()
    fun layerOf(id: DiagramNodeId): Int = layerAssignments.getOrPut(id) {
        predecessors[id].orEmpty().maxOfOrNull { layerOf(it) + 1 } ?: 0
    }
    adjacency.members.forEach { layerOf(it) }

    val layerCount = layerAssignments.values.max() + 1
    val layers = List(layerCount) { mutableListOf<DiagramNodeId>() }
    for (member in adjacency.members) layers[layerAssignments.getValue(member)] += member

    // Barycenter ordering: nodes chase the mean position of their neighbors; nodes
    // without neighbors keep their slot. Stable sort keeps ties deterministic.
    val indexOf = mutableMapOf<DiagramNodeId, Int>()
    fun reindex(layer: MutableList<DiagramNodeId>) {
        layer.forEachIndexed { index, id -> indexOf[id] = index }
    }
    layers.forEach { reindex(it) }

    fun sweep(neighborsOf: Map<DiagramNodeId, List<DiagramNodeId>>, layerOrder: IntProgression) {
        for (layerIndex in layerOrder) {
            val layer = layers[layerIndex]
            val sorted = layer.sortedBy { id ->
                val neighbors = neighborsOf[id].orEmpty()
                if (neighbors.isEmpty()) {
                    indexOf.getValue(id).toDouble()
                } else {
                    neighbors.sumOf { indexOf.getValue(it).toDouble() } / neighbors.size
                }
            }
            layer.clear()
            layer += sorted
            reindex(layer)
        }
    }
    repeat(2) {
        sweep(predecessors, 1 until layerCount)
        sweep(successors, (layerCount - 2) downTo 0)
    }

    // Coordinates: layers advance along the flow axis, nodes spread across it with
    // even gaps; each layer (and each node inside its layer band) is centered.
    val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT
    fun mainExtent(node: DiagramNode): Double = if (horizontalFlow) node.width else node.height
    fun crossExtent(node: DiagramNode): Double = if (horizontalFlow) node.height else node.width

    val layerMainExtents = layers.map { layer ->
        layer.maxOf { mainExtent(nodesById.getValue(it)) }
    }
    val layerCrossTotals = layers.map { layer ->
        layer.sumOf { crossExtent(nodesById.getValue(it)) } + config.nodeGap * (layer.size - 1)
    }
    val maxCrossTotal = layerCrossTotals.max()

    val positions = mutableMapOf<DiagramNodeId, DiagramPoint>()
    var main = 0.0
    layers.forEachIndexed { layerIndex, layer ->
        var cross = (maxCrossTotal - layerCrossTotals[layerIndex]) / 2.0
        for (id in layer) {
            val node = nodesById.getValue(id)
            val nodeMain = main + (layerMainExtents[layerIndex] - mainExtent(node)) / 2.0
            positions[id] = if (horizontalFlow) {
                DiagramPoint(x = nodeMain, y = cross)
            } else {
                DiagramPoint(x = cross, y = nodeMain)
            }
            cross += crossExtent(node) + config.nodeGap
        }
        main += layerMainExtents[layerIndex] + config.layerGap
    }
    return positions
}
