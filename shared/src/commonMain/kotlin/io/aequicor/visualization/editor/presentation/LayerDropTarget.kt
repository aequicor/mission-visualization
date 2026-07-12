package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.LayoutMode

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
 *
 * [pointerDepth] is the pointer's horizontal indent level (tree depth, from its x). At the
 * trailing edge of nested groups a gap is ambiguous — the node could join the inner group it
 * sits under or pop out to any ancestor — so the indent picks the level (deep = stay in, shallow
 * = pop out), like Figma/VS Code. Null keeps the legacy shallowest choice (the row below wins).
 */
fun resolveLayerDropTarget(
    document: DesignDocument,
    rows: List<LayerTreeRow>,
    dragId: String,
    pointerRowPosition: Double,
    pointerDepth: Int? = null,
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
            insertGapTarget(document, rows, dragId, gapIndex = if (frac < 0.5) hoverRow else hoverRow + 1, pointerDepth = pointerDepth)
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

/**
 * Position — in the new parent's layout coordinate space — that keeps [dragId] visually
 * put when a layers-tree drop reparents it into a *free* (`mode:none`) container. A canvas
 * drag carries pointer coordinates (see [reparentDropPlacement]); a layers-tree drop does
 * not, so without this the node keeps its old parent-relative position and jumps to a wrong
 * spot in the new container — vanishing outright if that container clips its content. The
 * returned offset is `nodeBox - parentBox` in root-layout space, which a Left/Top absolute
 * child reproduces exactly (`childX = parentX + position.x`).
 *
 * Returns null — leaving prior behavior untouched — when the move needs no repositioning or
 * can't be repositioned by a plain offset:
 *  - the target is an Auto-layout container (the node joins its flow; `position` is ignored);
 *  - it's a same-parent move (parent unchanged — a pure reorder, must stay in flow);
 *  - the node is anchored (logical anchors reposition it and win over `position`); or
 *  - either box is absent from [layout] (e.g. the node/parent is off the current artboard).
 */
fun layerReparentKeepPutPosition(
    document: DesignDocument,
    layout: LayoutBox?,
    dragId: String,
    newParentId: String,
): DesignPoint? {
    val parentNode = document.nodeById(newParentId) ?: return null
    if (parentNode.layout.mode != LayoutMode.None) return null
    val node = document.nodeById(dragId) ?: return null
    if (node.anchors != null) return null
    if (document.parentNodeOf(dragId)?.id == newParentId) return null
    val nodeBox = layout?.findBySourceId(dragId) ?: return null
    val parentBox = layout.findBySourceId(newParentId) ?: return null
    return DesignPoint(nodeBox.x - parentBox.x, nodeBox.y - parentBox.y)
}

/** A resolved insertion slot: [rawIndex] children of [parentId] (before same-parent adjustment). */
private data class GapSlot(val parentId: String, val siblingIds: List<String>, val rawIndex: Int, val depth: Int)

private fun insertGapTarget(
    document: DesignDocument,
    rows: List<LayerTreeRow>,
    dragId: String,
    gapIndex: Int,
    pointerDepth: Int?,
): LayerDropTarget.InsertGap? {
    // The gap above the root row is not a drop slot: the artboard renders the root
    // frame's tree, so a second page-level child would not be visible.
    if (gapIndex <= 0) return null
    val above = rows[gapIndex - 1]
    val below = rows.getOrNull(gapIndex)

    val slot: GapSlot = if (below != null && above.depth <= below.depth) {
        // The row below is a sibling (same depth) or the container above's first child (deeper):
        // it owns the gap, so land directly in front of it in paint order.
        document.gapSlotBeforeRow(below.nodeId, below.depth) ?: return null
    } else {
        // Trailing edge of one or more nested groups (row above is deeper than the row below, or
        // this is the terminal gap). The gap is ambiguous across every ancestor level from the
        // inner group up to the outer container; the pointer's indent picks one — deep stays in
        // the group it visually sits under, shallow pops out. Absent an indent, keep the legacy
        // shallowest choice (the row below, or a direct child of the root).
        val deepest = above.depth
        val shallowest = (below?.depth ?: 1).coerceAtLeast(1)
        if (deepest < shallowest) {
            // Terminal gap under a depth-0 root row (an empty or collapsed root frame): there is no
            // ancestor to pop out to, so land at the back of the root itself. This also avoids an
            // empty `coerceIn(shallowest, deepest)` range. A self-drag of the root is rejected by
            // the self/ancestor guard in [resolveLayerDropTarget].
            document.gapSlotAtBackOf(above.nodeId, above.depth + 1)
        } else {
            val chosen = (pointerDepth ?: shallowest).coerceIn(shallowest, deepest)
            if (below != null && chosen == below.depth) {
                // Shallowest rung that still has a row below it: land in front of that row.
                document.gapSlotBeforeRow(below.nodeId, chosen) ?: return null
            } else {
                // Land at the BACK (document index 0) of the ancestor whose child level is `chosen` —
                // i.e. behind the subtree that ends at `above`.
                val anchorId = ancestorAtDepth(document, above.nodeId, above.depth, chosen) ?: return null
                val parentId = document.parentNodeOf(anchorId)?.id
                    ?: document.topLevelOwnerPage(anchorId)?.id ?: return null
                document.gapSlotAtBackOf(parentId, chosen)
            }
        }
    }
    // Same-parent move: reparent removes the node before inserting, so a target index
    // past the node's current slot shifts down by one.
    val dragIndex = slot.siblingIds.indexOf(dragId)
    val index = if (dragIndex in 0 until slot.rawIndex) slot.rawIndex - 1 else slot.rawIndex
    return LayerDropTarget.InsertGap(parentId = slot.parentId, index = index, gapIndex = gapIndex, depth = slot.depth)
}

/** Slot directly in front of [rowId] in paint order (document index of [rowId] + 1). */
private fun DesignDocument.gapSlotBeforeRow(rowId: String, depth: Int): GapSlot? {
    val parent = parentNodeOf(rowId)
    val parentId = parent?.id ?: topLevelOwnerPage(rowId)?.id ?: return null
    val siblingIds = (parent?.children ?: topLevelOwnerPage(rowId)?.children.orEmpty()).map { it.id }
    val rowIndex = siblingIds.indexOf(rowId)
    if (rowIndex < 0) return null
    return GapSlot(parentId, siblingIds, rowIndex + 1, depth)
}

/** Slot at the back of [containerId] (document index 0 — the bottom of its layers block). */
private fun DesignDocument.gapSlotAtBackOf(containerId: String, depth: Int): GapSlot {
    val childIds = (nodeById(containerId)?.children ?: pages.firstOrNull { it.id == containerId }?.children.orEmpty())
        .map { it.id }
    return GapSlot(containerId, childIds, 0, depth)
}

/** Walks up from [startId] (at [startDepth]) to the ancestor at [targetDepth] (≤ startDepth). */
private fun ancestorAtDepth(document: DesignDocument, startId: String, startDepth: Int, targetDepth: Int): String? {
    var id = startId
    var depth = startDepth
    while (depth > targetDepth) {
        id = document.parentNodeOf(id)?.id ?: return null
        depth--
    }
    return id
}
