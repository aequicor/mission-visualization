package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode

/**
 * Structural tree editing over a [DesignDocument], used by the in-memory editor
 * commands (create / delete / duplicate / reorder / reparent). These operate on the
 * working document copy — unlike [DesignEditorIntent.ResizeNode] they do not write
 * back to the SLM source, because the surgical [io.aequicor.visualization.engine.frontend.edit.SlmPatcher]
 * cannot insert, delete or move nodes (it keeps authored ids stable by contract).
 * The gap is documented in the editor dev note.
 *
 * A "screen" in this app is a [DesignPage] whose single top-level child is the root
 * frame, mirroring the bundled sample documents.
 */

/** Parent of [nodeId] as a node, or null when the node is a page's top-level child (or absent). */
internal fun DesignDocument.parentNodeOf(nodeId: String): DesignNode? =
    pages.firstNotNullOfOrNull { page -> findParentIn(page.children, nodeId) }

private fun findParentIn(nodes: List<DesignNode>, nodeId: String): DesignNode? {
    nodes.forEach { node ->
        if (node.children.any { it.id == nodeId }) return node
        findParentIn(node.children, nodeId)?.let { return it }
    }
    return null
}

/** Page whose top-level children directly contain [nodeId], or null. */
internal fun DesignDocument.topLevelOwnerPage(nodeId: String): DesignPage? =
    pages.firstOrNull { page -> page.children.any { it.id == nodeId } }

/** The sibling list [nodeId] belongs to (page children or a parent node's children). */
internal fun DesignDocument.siblingsOf(nodeId: String): List<DesignNode> {
    parentNodeOf(nodeId)?.let { return it.children }
    topLevelOwnerPage(nodeId)?.let { return it.children }
    return emptyList()
}

/** True when [candidateAncestorId] is [nodeId] itself or one of its ancestors. */
internal fun DesignDocument.isSelfOrAncestor(nodeId: String, candidateAncestorId: String): Boolean {
    if (nodeId == candidateAncestorId) return true
    val node = nodeById(nodeId) ?: return false
    return node.findById(candidateAncestorId) != null
}

/**
 * True when a canvas press whose top-most hit is [hitId] should drag the current selection
 * rather than re-select the object under the cursor. Two cases stick:
 *  - the press lands on a selected node itself; or
 *  - the press lands on a descendant of a deliberately-selected *nested* container — pressing
 *    inside it drags the container (design-book §10 "drag moves object"; a nested object is
 *    reached by double-click).
 *
 * A top-level frame (a page's screen root) is deliberately excluded from the descendant case:
 * it is the resting/default selection and an ancestor of everything, so honoring its descendants
 * would make a plain press-drag on any child silently grab the (layout-pinned, immovable) screen
 * root instead of the child. Its children therefore stay directly selectable/draggable, matching
 * Figma's top-level frames. An unrelated object stacked on top of the selection is neither
 * selected nor a descendant, so it is not claimed here and still wins the press (design-book §10
 * "topmost selectable layer gets priority").
 */
internal fun DesignDocument.pressHitBelongsToSelection(selectedIds: Set<String>, hitId: String): Boolean {
    if (hitId.isBlank()) return false
    return selectedIds.any { selectedId ->
        if (selectedId == hitId) return@any true
        isSelfOrAncestor(selectedId, hitId) && topLevelOwnerPage(selectedId) == null
    }
}

/**
 * Resolves the Figma-style tap result while a nested container is the sole selection.
 *
 * A plain tap on one of that container's descendants keeps the container selected, so the
 * same press can drag it without unexpectedly grabbing an inner layer. A double-tap drills
 * exactly one hierarchy level toward the deepest hit — it never skips straight to a grandchild.
 * Returning null means the hit is not guarded by a nested selection and the normal canvas
 * selection/edit-mode behavior should run instead. Top-level screen frames are deliberately
 * excluded, matching [pressHitBelongsToSelection]: their children remain directly selectable.
 */
internal fun DesignDocument.nestedSelectionTargetForTap(
    selectedIds: Set<String>,
    hitId: String,
    doubleTap: Boolean,
): String? {
    val selectedId = selectedIds.singleOrNull() ?: return null
    if (hitId.isBlank() || selectedId == hitId || topLevelOwnerPage(selectedId) != null) return null
    val selected = nodeById(selectedId) ?: return null
    val childTowardHit = selected.children.firstOrNull { child -> child.findById(hitId) != null } ?: return null
    return if (doubleTap) childTowardHit.id else selectedId
}

/**
 * Inserts [node] as a child of [parentId] at [index] (clamped; -1 appends). When
 * [parentId] matches a page id or a page's root-frame is intended, resolves to the
 * appropriate container. Returns the document unchanged if the parent is missing.
 */
internal fun DesignDocument.insertNode(parentId: String, node: DesignNode, index: Int = -1): DesignDocument {
    // Parent is a page -> insert at page top level.
    pages.firstOrNull { it.id == parentId }?.let {
        return copy(
            pages = pages.map { page ->
                if (page.id == parentId) page.copy(children = page.children.insertAt(node, index).reindexOrder()) else page
            },
        )
    }
    // Parent is a node.
    if (nodeById(parentId) == null) return this
    return updateNode(parentId) { parent -> parent.copy(children = parent.children.insertAt(node, index).reindexOrder()) }
}

/** Removes the nodes with [ids] anywhere in the tree (top level or nested). */
internal fun DesignDocument.removeNodes(ids: Set<String>): DesignDocument {
    if (ids.isEmpty()) return this
    fun prune(nodes: List<DesignNode>): List<DesignNode> =
        nodes.filterNot { it.id in ids }.map { it.copy(children = prune(it.children)) }
    return copy(pages = pages.map { page -> page.copy(children = prune(page.children)) })
}

/** Reorders [nodeId] within its own sibling list to [newIndex] (clamped). */
internal fun DesignDocument.reorderSibling(nodeId: String, newIndex: Int): DesignDocument {
    val parent = parentNodeOf(nodeId)
    if (parent != null) {
        return updateNode(parent.id) { p -> p.copy(children = p.children.moveWithin(nodeId, newIndex).reindexOrder()) }
    }
    val page = topLevelOwnerPage(nodeId) ?: return this
    return copy(
        pages = pages.map { pg -> if (pg.id == page.id) pg.copy(children = pg.children.moveWithin(nodeId, newIndex).reindexOrder()) else pg },
    )
}

/**
 * Moves [nodeId] to become a child of [newParentId] at [index]. No-op when the move
 * would create a cycle (target is the node or a descendant) or either end is missing.
 * A node dropped into an Auto layout parent (row/column/grid) joins its flow — like
 * Figma, and mirroring object creation — while a positioned drop into a free parent
 * pins it at absolute coordinates.
 */
internal fun DesignDocument.reparent(
    nodeId: String,
    newParentId: String,
    index: Int = -1,
    position: DesignPoint? = null,
    size: DesignSize? = null,
    rotation: Double? = null,
): DesignDocument {
    if (nodeId == newParentId) return this
    if (isSelfOrAncestor(nodeId, newParentId)) return this
    val source = nodeById(nodeId) ?: return this
    val validParent = newParentId in pages.map { it.id } || nodeById(newParentId) != null
    if (!validParent) return this
    val flowParent = when (nodeById(newParentId)?.layout?.mode) {
        LayoutMode.Horizontal, LayoutMode.Vertical, LayoutMode.Grid -> true
        else -> false
    }
    val moving = source.copy(
        position = position ?: source.position,
        size = size ?: source.size,
        sizing = if (size != null) {
            (source.sizing ?: DesignSizing()).copy(
                horizontal = SizingMode.Fixed,
                vertical = SizingMode.Fixed,
            )
        } else {
            source.sizing
        },
        rotation = rotation ?: source.rotation,
        constraints = if (position != null) DesignConstraints() else source.constraints,
        layoutChild = when {
            flowParent -> source.layoutChild.copy(absolute = false)
            position != null -> source.layoutChild.copy(absolute = true)
            else -> source.layoutChild
        },
    )
    return removeNodes(setOf(nodeId)).insertNode(newParentId, moving, index)
}

/** Replaces the whole page list; used when adding a new screen. */
internal fun DesignDocument.addPage(page: DesignPage): DesignDocument = copy(pages = pages + page)

/**
 * Returns a copy of this subtree with every node re-id'd, used when duplicating. Ids
 * are unique against [document] and against the ids already minted within this copy
 * (so sibling clones sharing a prefix never collide). The root gains a " copy" name
 * suffix; descendants keep their names.
 */
internal fun DesignNode.deepCopyWithFreshIds(document: DesignDocument): DesignNode {
    val used = buildSet {
        document.pages.forEach { page ->
            add(page.id)
            page.allNodes().forEach { add(it.id) }
        }
    }.toMutableSet()

    fun mint(hint: String): String {
        val prefix = hint.substringBeforeLast('_').ifBlank { "node" }
        var n = 1
        while ("${prefix}_$n" in used) n++
        val id = "${prefix}_$n"
        used += id
        return id
    }

    fun clone(node: DesignNode, isRoot: Boolean): DesignNode = node.copy(
        id = mint(node.id),
        name = if (isRoot && node.name.isNotBlank()) "${node.name} copy" else node.name,
        sourceMap = null,
        blockSourceMaps = emptyMap(),
        children = node.children.map { clone(it, isRoot = false) },
    )
    return clone(this, isRoot = true)
}

/**
 * Re-materializes explicit sibling [DesignNode.order] from list position, mirroring the compiler's
 * `resolveOrder` (`order = (index + 1) * 10`). The layers tree and every structural mutator treat
 * children-list order as the z-authority, but the canvas resolver paints by the `order` field
 * (`order ?: 0`, stable sort) — so after any list-order change (insert / reorder / reparent) the two
 * drift apart unless `order` is re-synced (e.g. a null-order node stays visually behind an ordered
 * sibling however the layers tree is dragged). `order` is never serialized to the SLM source — z is
 * document order there — so this only refreshes the live in-memory z; it never touches source bytes.
 * For structurally-expressible edits a recompile re-derives the identical `(index + 1) * 10`; for
 * in-memory-only edits (instance / media / vector-path, whose z can't be written back) it is the sole
 * carrier of the new order until the next reload — which is exactly the case this fix exists for.
 */
private fun List<DesignNode>.reindexOrder(): List<DesignNode> =
    mapIndexed { index, child ->
        val order = (index + 1) * 10
        if (child.order == order) child else child.copy(order = order)
    }

private fun List<DesignNode>.insertAt(node: DesignNode, index: Int): List<DesignNode> {
    val clamped = if (index < 0) size else index.coerceIn(0, size)
    return toMutableList().apply { add(clamped, node) }
}

private fun List<DesignNode>.moveWithin(nodeId: String, newIndex: Int): List<DesignNode> {
    val current = indexOfFirst { it.id == nodeId }
    if (current < 0) return this
    val target = newIndex.coerceIn(0, size - 1)
    if (current == target) return this
    val mutable = toMutableList()
    val moved = mutable.removeAt(current)
    mutable.add(target, moved)
    return mutable
}
