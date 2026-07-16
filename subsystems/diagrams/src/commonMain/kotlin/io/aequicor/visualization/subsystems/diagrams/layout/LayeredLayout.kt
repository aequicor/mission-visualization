package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.sqrt

/** Barycenter down/up sweep rounds stop as soon as the orders stabilize, or after this many. */
private const val MAX_BARYCENTER_ROUNDS = 8

/**
 * Layered (Sugiyama-lite) auto-layout for class/component/deployment/flowchart diagrams.
 *
 * Pipeline: undirected connected-component split → per component: DFS cycle breaking →
 * longest-path layer assignment → barycenter ordering inside layers (seeded by BFS
 * discovery order, down/up sweeps until stable) → coordinates with even gaps, every
 * layer centered on the common axis → shelf-packing of the component blocks, so
 * disconnected clusters and isolated nodes do not inflate layer 0 of the main flow.
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
    val components = adjacency.connectedComponents()
    if (components.size == 1) return componentPositions(nodesById, components.single(), config)

    val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT

    data class PlacedComponent(
        val positions: Map<DiagramNodeId, DiagramPoint>,
        val mainExtent: Double,
        val crossExtent: Double,
    )

    val placed = components.map { component ->
        val positions = componentPositions(nodesById, component, config)
        var mainMax = 0.0
        var crossMax = 0.0
        for ((id, point) in positions) {
            val node = nodesById.getValue(id)
            mainMax = maxOf(mainMax, if (horizontalFlow) point.x + node.width else point.y + node.height)
            crossMax = maxOf(crossMax, if (horizontalFlow) point.y + node.height else point.x + node.width)
        }
        PlacedComponent(positions, mainMax, crossMax)
    }

    // Shelf packing: component blocks flow across the cross axis and wrap into a new band
    // when a row would outgrow the largest block (or a near-square target), so a main flow
    // plus strays reads as one tidy block instead of a single long strip.
    val gap = config.nodeGap * 2.0
    val totalArea = placed.sumOf { (it.mainExtent + gap) * (it.crossExtent + gap) }
    val rowLimit = maxOf(placed.maxOf { it.crossExtent }, sqrt(totalArea) * 1.5)
    val result = mutableMapOf<DiagramNodeId, DiagramPoint>()
    var mainOffset = 0.0
    var rowCross = 0.0
    var rowMainMax = 0.0
    for (component in placed) {
        if (rowCross > 0.0 && rowCross + component.crossExtent > rowLimit) {
            mainOffset += rowMainMax + config.layerGap
            rowCross = 0.0
            rowMainMax = 0.0
        }
        for ((id, point) in component.positions) {
            result[id] = if (horizontalFlow) {
                DiagramPoint(x = mainOffset + point.x, y = rowCross + point.y)
            } else {
                DiagramPoint(x = rowCross + point.x, y = mainOffset + point.y)
            }
        }
        rowCross += component.crossExtent + gap
        rowMainMax = maxOf(rowMainMax, component.mainExtent)
    }
    return result
}

/**
 * Undirected connected components, largest first (ties by first member id). Members and
 * links keep their id-sorted order within each component.
 */
private fun ScopeAdjacency.connectedComponents(): List<ScopeAdjacency> {
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

/** Layered positions of one connected component, local coordinates starting at (0,0). */
private fun componentPositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
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
    for (member in bfsSeedOrder(adjacency.members, predecessors, successors)) {
        layers[layerAssignments.getValue(member)] += member
    }

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

    var round = 0
    var stable = false
    while (!stable && round < MAX_BARYCENTER_ROUNDS) {
        val before = layers.map { it.toList() }
        sweep(predecessors, 1 until layerCount)
        sweep(successors, (layerCount - 2) downTo 0)
        stable = layers.map { it.toList() } == before
        round++
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

/**
 * BFS discovery order over the acyclic links from the component's sources (id-sorted,
 * successors visited in id order): seeds each layer so connected runs start out near
 * each other instead of scattered by id, giving the barycenter sweeps a better start.
 */
private fun bfsSeedOrder(
    members: List<DiagramNodeId>,
    predecessors: Map<DiagramNodeId, List<DiagramNodeId>>,
    successors: Map<DiagramNodeId, List<DiagramNodeId>>,
): List<DiagramNodeId> {
    val order = mutableListOf<DiagramNodeId>()
    val enqueued = mutableSetOf<DiagramNodeId>()
    val queue = ArrayDeque<DiagramNodeId>()
    for (member in members) {
        if (predecessors[member].orEmpty().isEmpty() && enqueued.add(member)) queue += member
    }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        order += current
        for (next in successors[current].orEmpty().sortedBy { it.value }) {
            if (enqueued.add(next)) queue += next
        }
    }
    // Safety net for members not reached from a source (cannot happen after cycle
    // breaking, which leaves every component with at least one source).
    for (member in members) {
        if (enqueued.add(member)) order += member
    }
    return order
}
