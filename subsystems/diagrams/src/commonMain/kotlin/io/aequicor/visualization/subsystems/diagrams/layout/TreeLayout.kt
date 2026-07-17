package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Tidy-tree auto-layout for state/activity/use-case diagrams.
 *
 * A Walker-lite recursion: subtrees are placed left to right with even gaps and every
 * parent is centered over the span of its children (midpoint between the first and last
 * child centers). Roots are the nodes without incoming links; cycles are broken by DFS
 * first, extra parents are ignored (first parent by id order wins), so the algorithm
 * never hangs. Container children are laid out inside their parent.
 *
 * Only node positions change; edges are untouched (the router re-routes them).
 * Deterministic: identical input always yields the identical output.
 */
fun treeLayout(
    graph: DiagramGraph,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph = layoutHierarchically(graph, config) { _, nodesById, adjacency ->
    treePositions(nodesById, adjacency, config)
}

/** Scope-local tidy-tree positions: top-left corners in local coordinates starting at (0,0). */
internal fun treePositions(
    nodesById: Map<DiagramNodeId, DiagramNode>,
    adjacency: ScopeAdjacency,
    config: DiagramLayoutConfig,
): Map<DiagramNodeId, DiagramPoint> {
    if (adjacency.members.isEmpty()) return emptyMap()
    val links = adjacency.acyclicLinks()

    // Force a forest: the first link (in sorted order) to reach a node becomes its
    // tree link; remaining incoming links are ignored for placement.
    val parentOf = mutableMapOf<DiagramNodeId, DiagramNodeId>()
    for (link in links) {
        if (link.to !in parentOf) parentOf[link.to] = link.from
    }
    val childrenOf = parentOf.entries
        .groupBy({ it.value }, { it.key })
        .mapValues { (_, children) -> children.sortedBy { it.value } }
    val roots = adjacency.members.filter { it !in parentOf }

    // Depths (levels along the flow axis).
    val depthOf = mutableMapOf<DiagramNodeId, Int>()
    fun assignDepth(id: DiagramNodeId, depth: Int) {
        if (id in depthOf) return
        depthOf[id] = depth
        childrenOf[id].orEmpty().forEach { assignDepth(it, depth + 1) }
    }
    roots.forEach { assignDepth(it, 0) }
    // Safety net: anything unreachable still gets placed as an extra root.
    adjacency.members.forEach { assignDepth(it, 0) }

    val horizontalFlow = config.direction == LayoutDirection.LEFT_RIGHT
    fun mainExtent(node: DiagramNode): Double = if (horizontalFlow) node.width else node.height
    fun crossExtent(node: DiagramNode): Double = if (horizontalFlow) node.height else node.width
    fun crossExtent(id: DiagramNodeId): Double = crossExtent(nodesById.getValue(id))

    val depthCount = depthOf.values.max() + 1
    val levelMainExtents = DoubleArray(depthCount)
    for ((id, depth) in depthOf) {
        levelMainExtents[depth] =
            maxOf(levelMainExtents[depth], mainExtent(nodesById.getValue(id)))
    }
    val levelMain = DoubleArray(depthCount)
    for (depth in 1 until depthCount) {
        levelMain[depth] = levelMain[depth - 1] + levelMainExtents[depth - 1] + config.layerGap
    }

    // Cross-axis placement: post-order recursion with a running cursor per level.
    val cross = mutableMapOf<DiagramNodeId, Double>()
    val levelCursor = DoubleArray(depthCount)

    fun shiftSubtree(id: DiagramNodeId, delta: Double) {
        for (child in childrenOf[id].orEmpty()) {
            val shifted = cross.getValue(child) + delta
            cross[child] = shifted
            val childDepth = depthOf.getValue(child)
            levelCursor[childDepth] =
                maxOf(levelCursor[childDepth], shifted + crossExtent(child) + config.nodeGap)
            shiftSubtree(child, delta)
        }
    }

    fun place(id: DiagramNodeId) {
        if (id in cross) return
        val depth = depthOf.getValue(id)
        val extent = crossExtent(id)
        val children = childrenOf[id].orEmpty()
        if (children.isEmpty()) {
            cross[id] = levelCursor[depth]
        } else {
            children.forEach { place(it) }
            val firstCenter = cross.getValue(children.first()) + crossExtent(children.first()) / 2.0
            val lastCenter = cross.getValue(children.last()) + crossExtent(children.last()) / 2.0
            val desired = (firstCenter + lastCenter) / 2.0 - extent / 2.0
            val actual = maxOf(desired, levelCursor[depth])
            cross[id] = actual
            if (actual > desired) shiftSubtree(id, actual - desired)
        }
        levelCursor[depth] = cross.getValue(id) + extent + config.nodeGap
    }

    roots.forEach { place(it) }
    adjacency.members.forEach { place(it) }

    return adjacency.members.associateWith { id ->
        val node = nodesById.getValue(id)
        val depth = depthOf.getValue(id)
        val nodeMain = levelMain[depth] + (levelMainExtents[depth] - mainExtent(node)) / 2.0
        if (horizontalFlow) {
            DiagramPoint(x = nodeMain, y = cross.getValue(id))
        } else {
            DiagramPoint(x = cross.getValue(id), y = nodeMain)
        }
    }
}
