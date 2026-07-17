package io.aequicor.visualization.subsystems.diagrams.layout

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation

/** The layout family a scope's connectivity calls for (see [classifyTopology]). */
enum class LayoutTopology {
    /** A forest: no merges, no cycles — tidy-tree reads best. */
    TREE,

    /** DAG-like with merges or few cycles — layered Sugiyama. */
    LAYERED,

    /** ER-dominated or heavily cyclic — stress majorization (no honest hierarchy exists). */
    FORCE,
}

/**
 * Classifies one container scope's connectivity (`null` = top level):
 * - Sequence scopes (any UML message) → [LayoutTopology.LAYERED]: messages between
 *   lifelines have their own vertical routing and must never be forced into a stress
 *   blob, so they stay out of the FORCE branches.
 * - ER-dominated scopes (half or more of the lifted links are entity relations) →
 *   [LayoutTopology.FORCE]: relational schemas are undirected many-to-many structures,
 *   a layered engine would invent a false hierarchy out of edge authoring order.
 * - Forests → [LayoutTopology.TREE].
 * - Heavily cyclic scopes (cyclomatic number of the **underlying undirected graph**
 *   above `max(2, members/3)`) → [LayoutTopology.FORCE]; the DFS cycle-break would
 *   discard too much structure. Counting undirected edges keeps mutual/bidirectional
 *   pairs (a↔b) from double-inflating the count and flipping a clean DAG to FORCE.
 * - Everything else → [LayoutTopology.LAYERED].
 */
fun classifyTopology(graph: DiagramGraph, parentId: DiagramNodeId? = null): LayoutTopology =
    classifyTopology(graph, parentId, graph.scopeAdjacency(parentId))

internal fun classifyTopology(
    graph: DiagramGraph,
    parentId: DiagramNodeId?,
    adjacency: ScopeAdjacency,
): LayoutTopology {
    val relations = graph.scopeLinkRelations(parentId)
    if (relations.any { it is DiagramRelation.Message }) return LayoutTopology.LAYERED
    if (relations.isNotEmpty() &&
        relations.count { it is DiagramRelation.EntityRelation } * 2 >= relations.size
    ) {
        return LayoutTopology.FORCE
    }
    if (adjacency.isForest()) return LayoutTopology.TREE
    val componentCount = adjacency.connectedComponents().size
    // Cyclomatic number of the underlying undirected simple graph: unordered unique
    // endpoint pairs, so a↔b counts once, not twice.
    val undirectedEdges = adjacency.links
        .map { setOf(it.from.value, it.to.value) }
        .distinct()
        .size
    val cyclomatic = undirectedEdges - adjacency.members.size + componentCount
    val cyclomaticLimit = maxOf(2, adjacency.members.size / 3)
    return if (cyclomatic > cyclomaticLimit) LayoutTopology.FORCE else LayoutTopology.LAYERED
}

/** Which auto-layout algorithm [autoLayout] applies. */
enum class LayoutKind {
    /** Pick per scope by [classifyTopology]: TREE → tree, LAYERED → layered, FORCE → stress. */
    AUTO,

    /** Layered (Sugiyama) layout — class/component/deployment/flowchart diagrams. */
    LAYERED,

    /** Tidy-tree layout — state/activity/use-case diagrams. */
    TREE,

    /** Stress-majorization (SMACOF) layout — dense/cyclic ER, undirected many-to-many. */
    FORCE,
}

/**
 * Auto-layout entry point.
 *
 * [LayoutKind.AUTO] classifies every container scope independently via [classifyTopology]
 * and applies the matching engine, so e.g. a class diagram with an embedded ER container
 * gets layered layers outside and a stress layout inside. Explicit kinds force one
 * algorithm for all scopes.
 */
fun autoLayout(
    graph: DiagramGraph,
    kind: LayoutKind = LayoutKind.AUTO,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): DiagramGraph = when (kind) {
    LayoutKind.LAYERED -> layeredLayout(graph, config)
    LayoutKind.TREE -> treeLayout(graph, config)
    LayoutKind.FORCE -> stressLayout(graph, config)
    LayoutKind.AUTO -> layoutHierarchically(graph, config) { parentId, nodesById, adjacency ->
        when (classifyTopology(graph, parentId, adjacency)) {
            LayoutTopology.TREE -> treePositions(nodesById, adjacency, config)
            LayoutTopology.LAYERED -> layeredPositions(nodesById, adjacency, config)
            LayoutTopology.FORCE -> stressPositions(nodesById, adjacency, config)
        }
    }
}
