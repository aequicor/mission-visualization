package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph

/**
 * Auto-layout entry point.
 *
 * [LayoutKind.AUTO] inspects the top-level connectivity: a forest (acyclic, at most one
 * incoming link per node) gets [treeLayout]; anything with merges or cycles (DAGs,
 * cyclic graphs) gets [layeredLayout]. Explicit kinds force the algorithm.
 */
fun autoLayout(
    graph: DiagramGraph,
    kind: LayoutKind = LayoutKind.AUTO,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph = when (kind) {
    LayoutKind.LAYERED -> layeredLayout(graph, config)
    LayoutKind.TREE -> treeLayout(graph, config)
    LayoutKind.AUTO -> if (graph.scopeAdjacency(null).isForest()) {
        treeLayout(graph, config)
    } else {
        layeredLayout(graph, config)
    }
}
