package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId

/**
 * Longest-path layering of an acyclic component: sources sit at layer 0, every other
 * node at `max(layer(predecessor)) + 1`. Every rank `0..max` ends up occupied by at
 * least one real node (a node at layer `k` has a predecessor at exactly `k - 1`).
 * Iterative (no recursion) and deterministic: members are visited in list order.
 */
internal fun assignLongestPathLayers(
    members: List<DiagramNodeId>,
    predecessors: Map<DiagramNodeId, List<DiagramNodeId>>,
): Map<DiagramNodeId, Int> {
    val layers = mutableMapOf<DiagramNodeId, Int>()
    for (member in members) {
        if (member in layers) continue
        val stack = ArrayDeque<DiagramNodeId>()
        stack.addLast(member)
        while (stack.isNotEmpty()) {
            val current = stack.last()
            val pending = predecessors[current].orEmpty().filter { it !in layers }
            if (pending.isEmpty()) {
                stack.removeLast()
                if (current !in layers) {
                    layers[current] =
                        predecessors[current].orEmpty().maxOfOrNull { layers.getValue(it) + 1 } ?: 0
                }
            } else {
                pending.forEach(stack::addLast)
            }
        }
    }
    return layers
}
