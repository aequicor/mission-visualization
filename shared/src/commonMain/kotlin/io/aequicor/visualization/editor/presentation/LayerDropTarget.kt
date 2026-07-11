package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignDocument

/**
 * Pure resolution of a layers-tree drag to its drop target. The tree is rendered
 * front-first (top row = front of the paint order, i.e. the LAST child in document
 * order), so a visual insertion gap must be mapped back to a document-order index in
 * reverse: dropping at the visual bottom of a sibling group means document index 0.
 * Kept Compose-free so the mapping is unit-testable headlessly.
 */

/** A row of the flattened front-first layers tree, as the UI displays it. */
data class LayerTreeRow(
    val nodeId: String,
    val depth: Int,
    /** Frame or has children — can adopt a dropped node. */
    val isContainer: Boolean,
)

/** Where a layers-tree drag would drop, resolved against the pointer's row position. */
sealed interface LayerDropTarget {
    /**
     * Insert as a child of [parentId] at document-order [index] (already adjusted for a
     * same-parent move). The insertion line draws at visual [gapIndex] (a boundary
     * between rows, 1..rows.size) with [depth] indent.
     */
    data class InsertGap(val parentId: String, val index: Int, val gapIndex: Int, val depth: Int) : LayerDropTarget

    /** Nest into container [nodeId] (appended at the paint-order front) — row [rowIndex] highlights. */
    data class IntoContainer(val nodeId: String, val rowIndex: Int) : LayerDropTarget
}

/**
 * Resolves the pointer's fractional [pointerRowPosition] (rows from the top of the tree)
 * into a drop target, using three bands per row: the top quarter inserts before the row,
 * the bottom quarter after it, and the middle nests into the row when it is a container
 * (otherwise the nearest gap). Positions past the last row resolve to the terminal gap —
 * the visual end of the root's list, i.e. the back of the paint order. Returns null for
 * drops that would land the node in itself/its own subtree or directly above the root row.
 */
fun resolveLayerDropTarget(
    document: DesignDocument,
    rows: List<LayerTreeRow>,
    dragId: String,
    pointerRowPosition: Double,
): LayerDropTarget? {
    if (rows.isEmpty()) return null
    val position = pointerRowPosition.coerceIn(0.0, rows.size.toDouble())
    val hoverRow = position.toInt().coerceAtMost(rows.size - 1)
    val frac = position - hoverRow
    val row = rows[hoverRow]
    val target = when {
        frac in 0.25..0.75 && row.isContainer && row.nodeId != dragId ->
            LayerDropTarget.IntoContainer(row.nodeId, hoverRow)
        else ->
            insertGapTarget(document, rows, dragId, gapIndex = if (frac < 0.5) hoverRow else hoverRow + 1)
    } ?: return null
    val parentId = when (target) {
        is LayerDropTarget.IntoContainer -> target.nodeId
        is LayerDropTarget.InsertGap -> target.parentId
    }
    // A node can't land in itself or its own subtree (the reducer rejects it too):
    // true when [parentId] is [dragId] itself or a node inside its subtree.
    if (document.isSelfOrAncestor(dragId, parentId)) return null
    return target
}

private fun insertGapTarget(
    document: DesignDocument,
    rows: List<LayerTreeRow>,
    dragId: String,
    gapIndex: Int,
): LayerDropTarget.InsertGap? {
    // The gap above the root row is not a drop slot: the artboard renders the root
    // frame's tree, so a second page-level child would not be visible.
    if (gapIndex <= 0) return null
    val below = rows.getOrNull(gapIndex)
    val parentId: String
    val siblingIds: List<String>
    val rawIndex: Int
    val depth: Int
    if (below == null) {
        // Terminal gap under the last row: the end of the root's visible list — the
        // back of the paint order, document index 0.
        parentId = rows.first().nodeId
        siblingIds = document.nodeById(parentId)?.children?.map { it.id }.orEmpty()
        rawIndex = 0
        depth = rows.first().depth + 1
    } else {
        // Inserting "visually above `below`" = directly in front of it in paint order.
        val parent = document.parentNodeOf(below.nodeId)
        parentId = parent?.id ?: document.topLevelOwnerPage(below.nodeId)?.id ?: return null
        siblingIds = (parent?.children ?: document.topLevelOwnerPage(below.nodeId)?.children.orEmpty()).map { it.id }
        val belowIndex = siblingIds.indexOf(below.nodeId)
        if (belowIndex < 0) return null
        rawIndex = belowIndex + 1
        depth = below.depth
    }
    // Same-parent move: reparent removes the node before inserting, so a target index
    // past the node's current slot shifts down by one.
    val dragIndex = siblingIds.indexOf(dragId)
    val index = if (dragIndex in 0 until rawIndex) rawIndex - 1 else rawIndex
    return LayerDropTarget.InsertGap(parentId = parentId, index = index, gapIndex = gapIndex, depth = depth)
}
