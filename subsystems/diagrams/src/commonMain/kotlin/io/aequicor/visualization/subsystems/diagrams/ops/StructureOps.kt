package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.updateEdge
import io.aequicor.visualization.subsystems.diagrams.model.updateNode
import io.aequicor.visualization.subsystems.diagrams.model.withGroup
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

// --- groups -----------------------------------------------------------------------------

/**
 * Creates a selection group from [memberIds] (unknown ids are dropped). No-op if the
 * group id is taken or no member exists.
 */
public fun DiagramGraph.groupNodes(
    id: DiagramGroupId,
    memberIds: List<DiagramNodeId>,
    name: String? = null,
): DiagramGraph {
    if (groupById(id) != null) return this
    val existing = memberIds.distinct().filter { nodeById(it) != null }
    if (existing.isEmpty()) return this
    return withGroup(DiagramGroup(id = id, memberIds = existing, name = name))
}

/** Dissolves the group (members stay in the graph untouched). */
public fun DiagramGraph.ungroupNodes(id: DiagramGroupId): DiagramGraph =
    copy(groups = groups.filter { it.id != id })

// --- z-order ----------------------------------------------------------------------------

/**
 * Z-order note: within a layer the node list order is the z-order, so all four ops move
 * the node **with its whole container subtree** (as a contiguous block, preserving the
 * subtree's internal order) relative to other nodes of the same layer.
 */

/** Moves the node subtree above everything else in its layer. */
public fun DiagramGraph.bringToFront(id: DiagramNodeId): DiagramGraph =
    reorderSubtree(id) { block, rest -> rest + block }

/** Moves the node subtree below everything else in its layer. */
public fun DiagramGraph.sendToBack(id: DiagramNodeId): DiagramGraph =
    reorderSubtree(id) { block, rest -> block + rest }

/** Moves the node subtree one step up: right above the next same-layer node. */
public fun DiagramGraph.bringForward(id: DiagramNodeId): DiagramGraph {
    val node = nodeById(id) ?: return this
    val subtree = subtreeIds(id)
    val lastIndex = nodes.indexOfLast { it.id in subtree }
    val next = nodes.withIndex().firstOrNull { (index, candidate) ->
        index > lastIndex && candidate.id !in subtree && candidate.layerId == node.layerId
    }?.value ?: return this
    return reorderSubtree(id) { block, rest ->
        val insertAfter = rest.indexOfFirst { it.id == next.id } + 1
        rest.take(insertAfter) + block + rest.drop(insertAfter)
    }
}

/** Moves the node subtree one step down: right below the previous same-layer node. */
public fun DiagramGraph.sendBackward(id: DiagramNodeId): DiagramGraph {
    val node = nodeById(id) ?: return this
    val subtree = subtreeIds(id)
    val firstIndex = nodes.indexOfFirst { it.id in subtree }
    val previous = nodes.withIndex().lastOrNull { (index, candidate) ->
        index < firstIndex && candidate.id !in subtree && candidate.layerId == node.layerId
    }?.value ?: return this
    return reorderSubtree(id) { block, rest ->
        val insertBefore = rest.indexOfFirst { it.id == previous.id }
        rest.take(insertBefore) + block + rest.drop(insertBefore)
    }
}

private fun DiagramGraph.reorderSubtree(
    id: DiagramNodeId,
    place: (block: List<DiagramNode>, rest: List<DiagramNode>) -> List<DiagramNode>,
): DiagramGraph {
    if (nodeById(id) == null) return this
    val subtree = subtreeIds(id)
    val block = nodes.filter { it.id in subtree }
    val rest = nodes.filter { it.id !in subtree }
    return copy(nodes = place(block, rest))
}

// --- layers -----------------------------------------------------------------------------

/**
 * Removes the layer; its nodes and edges move to the implicit default layer
 * (`layerId = null`), nothing is deleted.
 */
public fun DiagramGraph.removeLayer(id: DiagramLayerId): DiagramGraph {
    if (layerById(id) == null) return this
    return copy(
        layers = layers.filter { it.id != id },
        nodes = nodes.map { if (it.layerId == id) it.copy(layerId = null) else it },
        edges = edges.map { if (it.layerId == id) it.copy(layerId = null) else it },
    )
}

/**
 * Moves the node **and its whole container subtree** to [layerId] (`null` = default
 * layer). No-op if the target layer does not exist.
 */
public fun DiagramGraph.moveNodeToLayer(id: DiagramNodeId, layerId: DiagramLayerId?): DiagramGraph {
    if (nodeById(id) == null) return this
    if (layerId != null && layerById(layerId) == null) return this
    val subtree = subtreeIds(id)
    return copy(
        nodes = nodes.map { if (it.id in subtree) it.copy(layerId = layerId) else it },
    )
}

/** Moves the edge to [layerId] (`null` = default layer). No-op if the layer does not exist. */
public fun DiagramGraph.moveEdgeToLayer(id: DiagramEdgeId, layerId: DiagramLayerId?): DiagramGraph {
    if (layerId != null && layerById(layerId) == null) return this
    return updateEdge(id) { it.copy(layerId = layerId) }
}

/** Shows/hides the layer. */
public fun DiagramGraph.setLayerVisible(id: DiagramLayerId, visible: Boolean): DiagramGraph =
    copy(layers = layers.map { if (it.id == id) it.copy(visible = visible) else it })

/** Locks/unlocks the layer. */
public fun DiagramGraph.setLayerLocked(id: DiagramLayerId, locked: Boolean): DiagramGraph =
    copy(layers = layers.map { if (it.id == id) it.copy(locked = locked) else it })

// --- containers -------------------------------------------------------------------------

/**
 * Drops the node into a container. [positionInContainer] is the desired top-left of the
 * node **in the container's coordinate system** (relative to the container's top-left);
 * it is converted to document coordinates and the node's subtree moves accordingly.
 * `null` keeps the node's current document position (pure re-parent, no visual jump).
 *
 * No-op if either node is missing, or the drop would create a containment cycle.
 */
public fun DiagramGraph.dropIntoContainer(
    id: DiagramNodeId,
    containerId: DiagramNodeId,
    positionInContainer: DiagramPoint? = null,
): DiagramGraph {
    val node = nodeById(id) ?: return this
    val container = nodeById(containerId) ?: return this
    if (id == containerId || containerId in subtreeIds(id)) return this
    val reparented = updateNode(id) { it.copy(parentId = containerId) }
    if (positionInContainer == null) return reparented
    val dx = container.x + positionInContainer.x - node.x
    val dy = container.y + positionInContainer.y - node.y
    return reparented.moveNode(id, dx, dy)
}

/**
 * Pulls the node out of its container to the top level. Coordinates are document-absolute
 * in the model, so the node keeps its visual position.
 */
public fun DiagramGraph.pullOutOfContainer(id: DiagramNodeId): DiagramGraph =
    updateNode(id) { it.copy(parentId = null) }
