package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.edit.SetSizing
import io.aequicor.visualization.engine.frontend.edit.SizingSpec
import io.aequicor.visualization.engine.frontend.edit.applySlmEdit
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure `(State, Intent) -> State` reducer. Selection and workspace-independent
 * document edits live here; [DesignEditorIntent.ResizeNode] also patches the owning
 * SLM source. Structural and property edits mutate the in-memory working document
 * and record undo history ([DesignEditorState.editNode] / [editDocument]).
 */
fun reduceDesignEditor(state: DesignEditorState, intent: DesignEditorIntent): DesignEditorState =
    when (intent) {
        // --- Selection ---
        is DesignEditorIntent.SelectPage -> {
            val page = state.document?.pageById(intent.pageId)
            val first = page?.children?.firstOrNull()?.id.orEmpty()
            state.copy(
                selectedPageId = page?.id ?: intent.pageId,
                selectedNodeId = first,
                selectedNodeIds = if (first.isBlank()) emptySet() else setOf(first),
                editingTextNodeId = "",
            )
        }
        is DesignEditorIntent.SelectNode -> state.selectSingle(intent.nodeId)
        is DesignEditorIntent.SelectNodes -> state.selectMany(intent.nodeIds)
        is DesignEditorIntent.ToggleNodeSelection -> {
            val next = if (intent.nodeId in state.selectedNodeIds) {
                state.selectedNodeIds - intent.nodeId
            } else {
                state.selectedNodeIds + intent.nodeId
            }
            state.selectMany(next)
        }
        DesignEditorIntent.ClearSelection ->
            state.copy(selectedNodeId = "", selectedNodeIds = emptySet(), editingTextNodeId = "")
        DesignEditorIntent.SelectAll -> {
            val ids = state.document?.pageById(state.selectedPageId)?.children?.map { it.id }?.toSet().orEmpty()
            state.selectMany(ids)
        }
        is DesignEditorIntent.SetEditingText -> state.copy(editingTextNodeId = intent.nodeId)

        // --- Position / size / transform ---
        is DesignEditorIntent.UpdatePosition -> state.editNode(intent.nodeId) { node ->
            val current = node.position ?: DesignPoint()
            node.copy(position = DesignPoint(intent.x ?: current.x, intent.y ?: current.y))
        }
        is DesignEditorIntent.UpdateSize -> state.editNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(
                size = DesignSize(intent.width ?: node.size.width, intent.height ?: node.size.height),
                sizing = sizing.copy(
                    horizontal = if (intent.width != null) SizingMode.Fixed else sizing.horizontal,
                    vertical = if (intent.height != null) SizingMode.Fixed else sizing.vertical,
                ),
            )
        }
        is DesignEditorIntent.ResizeNode -> state.resizeNodeWriteBack(intent)
        is DesignEditorIntent.MoveNodes -> state.moveNodes(intent)
        is DesignEditorIntent.UpdateSizingMode -> state.editNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(sizing = sizing.copy(
                horizontal = intent.horizontal ?: sizing.horizontal,
                vertical = intent.vertical ?: sizing.vertical,
            ))
        }
        is DesignEditorIntent.UpdateConstraints -> state.editNode(intent.nodeId) { node ->
            node.copy(constraints = node.constraints.copy(
                horizontal = intent.horizontal ?: node.constraints.horizontal,
                vertical = intent.vertical ?: node.constraints.vertical,
            ))
        }
        is DesignEditorIntent.SetRotation -> state.editNode(intent.nodeId) { it.copy(rotation = intent.degrees) }
        is DesignEditorIntent.FlipHorizontal -> state.flip(intent.nodeIds, horizontal = true)
        is DesignEditorIntent.FlipVertical -> state.flip(intent.nodeIds, horizontal = false)

        // --- Visibility / lock / structure ---
        is DesignEditorIntent.SetVisible -> state.editNode(intent.nodeId) { it.copy(visible = intent.visible.bindable()) }
        is DesignEditorIntent.SetLocked -> state.editNode(intent.nodeId) { it.copy(locked = intent.locked) }
        is DesignEditorIntent.RenameNode -> state.editNode(intent.nodeId) { it.copy(name = intent.name) }
        is DesignEditorIntent.DeleteNodes -> state.deleteNodes(intent.nodeIds)
        is DesignEditorIntent.DuplicateNodes -> state.duplicateNodes(intent.nodeIds)
        is DesignEditorIntent.ReorderNode -> state.reorderNode(intent)
        is DesignEditorIntent.ReparentNode -> state.editDocument { it.reparent(intent.nodeId, intent.newParentId, intent.index) }
        is DesignEditorIntent.CreateObject -> state.createObject(intent)
        is DesignEditorIntent.CreateScreen -> state.createScreen(intent)

        // --- Layout container ---
        is DesignEditorIntent.SetLayoutMode -> state.editNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(mode = intent.mode.toLayoutMode()))
        }
        is DesignEditorIntent.SetLayoutGap -> state.editNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(gap = DesignGap.Fixed(intent.gap.bindable())))
        }
        is DesignEditorIntent.SetLayoutPadding -> state.editNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(padding = node.layout.padding.withSide(intent.side, intent.value)))
        }
        is DesignEditorIntent.SetLayoutAlign -> state.editNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(
                alignItems = intent.alignItems ?: node.layout.alignItems,
                justifyContent = intent.justifyContent ?: node.layout.justifyContent,
            ))
        }
        is DesignEditorIntent.SetClipsContent -> state.editNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(clipsContent = intent.clips))
        }
        is DesignEditorIntent.SetSticky -> state.editNode(intent.nodeId) { node ->
            node.copy(scroll = node.scroll.copy(sticky = intent.sticky))
        }

        // --- Appearance / fill / stroke / effects ---
        is DesignEditorIntent.UpdateOpacity -> state.editNode(intent.nodeId) { it.copy(opacity = intent.opacity.coerceIn(0.0, 1.0).bindable()) }
        is DesignEditorIntent.SetBlendMode -> state.editNode(intent.nodeId) { it.copy(blendMode = intent.blendMode) }
        is DesignEditorIntent.UpdateCornerRadius -> state.editNode(intent.nodeId) { it.copy(cornerRadius = DesignCornerRadius.all(intent.radius.coerceAtLeast(0.0).bindable())) }
        is DesignEditorIntent.UpdateCornerRadiusPerCorner -> state.editNode(intent.nodeId) { node ->
            val current = node.cornerRadius ?: DesignCornerRadius()
            node.copy(cornerRadius = current.copy(
                topLeft = intent.topLeft?.coerceAtLeast(0.0)?.bindable() ?: current.topLeft,
                topRight = intent.topRight?.coerceAtLeast(0.0)?.bindable() ?: current.topRight,
                bottomRight = intent.bottomRight?.coerceAtLeast(0.0)?.bindable() ?: current.bottomRight,
                bottomLeft = intent.bottomLeft?.coerceAtLeast(0.0)?.bindable() ?: current.bottomLeft,
            ))
        }
        is DesignEditorIntent.UpdateSolidFill -> state.editNode(intent.nodeId) { node ->
            val solid = DesignPaint.Solid(intent.color.bindable())
            val fills = node.fills.orEmpty()
            node.copy(fills = if (fills.isEmpty()) listOf(solid) else listOf(solid) + fills.drop(1), fillStyleId = "")
        }
        is DesignEditorIntent.FillCommand -> state.editNode(intent.nodeId) { it.applyFillOp(intent.op) }
        is DesignEditorIntent.UpdateStroke -> state.editNode(intent.nodeId) { node ->
            val current = node.strokes ?: DesignStrokes()
            val paints = if (intent.color != null) {
                listOf(DesignPaint.Solid(intent.color.bindable())) + current.paints.drop(1)
            } else {
                current.paints
            }
            node.copy(strokes = current.copy(paints = paints, weight = intent.weight?.bindable() ?: current.weight), strokeStyleId = "")
        }
        is DesignEditorIntent.StrokeCommand -> state.editNode(intent.nodeId) { it.applyStrokeOp(intent.op) }
        is DesignEditorIntent.EffectCommand -> state.editNode(intent.nodeId) { it.applyEffectOp(intent.op) }

        // --- Typography ---
        is DesignEditorIntent.UpdateTypography -> state.editNode(intent.nodeId) { it.applyTypography(intent.patch) }
        is DesignEditorIntent.SetTextCharacters -> state.editNode(intent.nodeId) { node ->
            val kind = node.kind as? DesignNodeKind.Text ?: return@editNode node
            node.copy(kind = kind.copy(characters = intent.text.bindable(), content = null))
        }
        is DesignEditorIntent.SetTextAutoResize -> state.editNode(intent.nodeId) { node ->
            val kind = node.kind as? DesignNodeKind.Text ?: return@editNode node
            node.copy(kind = kind.copy(autoResize = intent.mode))
        }

        // --- Vector ---
        is DesignEditorIntent.MoveVectorPoint -> state.editNode(intent.nodeId) { it.moveVectorPoint(intent) }

        // --- Interaction checkpoints ---
        DesignEditorIntent.BeginInteraction -> state.beginInteraction()
        DesignEditorIntent.EndInteraction -> state.copy(interacting = false)

        // --- History ---
        DesignEditorIntent.Undo -> state.undo()
        DesignEditorIntent.Redo -> state.redo()
    }

private fun DesignEditorState.beginInteraction(): DesignEditorState {
    val document = document ?: return copy(interacting = true)
    return copy(
        interacting = true,
        undoStack = (undoStack + document).takeLast(MaxDocumentHistory),
        redoStack = emptyList(),
    )
}

// --- Selection helpers -------------------------------------------------------

private fun DesignEditorState.selectSingle(nodeId: String): DesignEditorState {
    if (nodeId.isBlank()) return copy(selectedNodeId = "", selectedNodeIds = emptySet(), editingTextNodeId = "")
    val page = document?.pageOfNode(nodeId)
    return copy(
        selectedNodeId = nodeId,
        selectedNodeIds = setOf(nodeId),
        selectedPageId = page?.id ?: selectedPageId,
        editingTextNodeId = if (editingTextNodeId == nodeId) editingTextNodeId else "",
    )
}

private fun DesignEditorState.selectMany(ids: Set<String>): DesignEditorState {
    val existing = ids.filter { document?.nodeById(it) != null }.toSet()
    if (existing.isEmpty()) return copy(selectedNodeId = "", selectedNodeIds = emptySet(), editingTextNodeId = "")
    val primary = if (selectedNodeId in existing) selectedNodeId else existing.last()
    val page = document?.pageOfNode(primary)
    return copy(
        selectedNodeId = primary,
        selectedNodeIds = existing,
        selectedPageId = page?.id ?: selectedPageId,
        editingTextNodeId = "",
    )
}

// --- Structural edits --------------------------------------------------------

private fun DesignEditorState.moveNodes(intent: DesignEditorIntent.MoveNodes): DesignEditorState {
    val document = document ?: return this
    val movable = intent.nodeIds.filter { id ->
        val node = document.nodeById(id)
        node != null && !node.locked && document.isCoordinatePositioned(id)
    }
    if (movable.isEmpty()) return this
    val moved = movable.fold(document) { doc, id ->
        doc.updateNode(id) { node ->
            val p = node.position ?: DesignPoint()
            node.copy(position = DesignPoint(p.x + intent.dx, p.y + intent.dy))
        }
    }
    return commitDocument(document, moved)
}

private fun DesignEditorState.deleteNodes(ids: Set<String>): DesignEditorState {
    val document = document ?: return this
    val deletable = ids.filter { document.nodeById(it)?.locked == false }.toSet()
    if (deletable.isEmpty()) return this
    val next = document.removeNodes(deletable)
    if (next == document) return this
    // Prune against the resulting tree: removing a node also drops its descendants,
    // so a selected child of a deleted parent must leave the selection too.
    val remaining = selectedNodeIds.filter { next.nodeById(it) != null }.toSet()
    val primary = if (selectedNodeId in remaining) selectedNodeId else remaining.firstOrNull().orEmpty()
    return pushHistory(document).copy(
        document = next,
        selectedNodeIds = remaining,
        selectedNodeId = primary,
        editingTextNodeId = "",
    )
}

private fun DesignEditorState.duplicateNodes(ids: Set<String>): DesignEditorState {
    val document = document ?: return this
    var working = document
    val newIds = mutableSetOf<String>()
    // Duplicate each node next to itself; offset by (16,16) for visual separation.
    ids.forEach { id ->
        val original = working.nodeById(id) ?: return@forEach
        val clone = original.deepCopyWithFreshIds(working)
        val offset = clone.copy(position = clone.position?.let { DesignPoint(it.x + 16, it.y + 16) })
        val siblings = working.siblingsOf(id)
        val insertIndex = siblings.indexOfFirst { it.id == id }.let { if (it < 0) -1 else it + 1 }
        val parentId = working.parentNodeOf(id)?.id ?: working.topLevelOwnerPage(id)?.id
        if (parentId != null) {
            working = working.insertNode(parentId, offset, insertIndex)
            newIds += offset.id
        }
    }
    if (newIds.isEmpty()) return this
    return pushHistory(document).copy(document = working).selectMany(newIds)
}

private fun DesignEditorState.reorderNode(intent: DesignEditorIntent.ReorderNode): DesignEditorState {
    val document = document ?: return this
    val siblings = document.siblingsOf(intent.nodeId)
    val current = siblings.indexOfFirst { it.id == intent.nodeId }
    if (current < 0) return this
    val target = when (intent.move) {
        ZOrderMove.Forward -> current + 1
        ZOrderMove.Backward -> current - 1
        ZOrderMove.ToFront -> siblings.size - 1
        ZOrderMove.ToBack -> 0
    }.coerceIn(0, siblings.size - 1)
    if (target == current) return this
    return editDocument { it.reorderSibling(intent.nodeId, target) }
}

private fun DesignEditorState.createObject(intent: DesignEditorIntent.CreateObject): DesignEditorState {
    val document = document ?: return this
    val node = EditorNodeFactory.newObject(
        document = document,
        kind = intent.kind,
        x = intent.x,
        y = intent.y,
        width = intent.width,
        height = intent.height,
        fixedWidthText = intent.fixedWidthText,
    )
    val parentValid = intent.parentId in document.pages.map { it.id } || document.nodeById(intent.parentId) != null
    val parentId = if (parentValid) intent.parentId else document.pageById(selectedPageId)?.children?.firstOrNull()?.id
        ?: document.pageById(selectedPageId)?.id ?: return this
    val next = document.insertNode(parentId, node)
    if (next == document) return this
    return pushHistory(document).copy(
        document = next,
        selectedNodeId = node.id,
        selectedNodeIds = setOf(node.id),
        editingTextNodeId = if (intent.enterTextEditing) node.id else "",
    )
}

private fun DesignEditorState.createScreen(intent: DesignEditorIntent.CreateScreen): DesignEditorState {
    val document = document ?: return this
    val page = EditorNodeFactory.newScreen(document, intent.preset, intent.title)
    val rootId = page.children.firstOrNull()?.id.orEmpty()
    return pushHistory(document).copy(
        document = document.addPage(page),
        selectedPageId = page.id,
        selectedNodeId = rootId,
        selectedNodeIds = if (rootId.isBlank()) emptySet() else setOf(rootId),
        editingTextNodeId = "",
    )
}

/**
 * Flips the selected nodes by mirroring their parent-relative positions around the
 * selection's common bounding-box center. Note: the IR carries no scale/flip
 * transform, so a single primitive's glyph/geometry is not mirrored — only the
 * arrangement of a multi-selection is (documented gap).
 */
private fun DesignEditorState.flip(ids: Set<String>, horizontal: Boolean): DesignEditorState {
    val document = document ?: return this
    val nodes = ids.mapNotNull { document.nodeById(it) }.filter { it.position != null && !it.locked }
    if (nodes.size < 2) return this
    val lefts = nodes.map { it.position!!.x }
    val rights = nodes.map { it.position!!.x + (it.size.width ?: 0.0) }
    val tops = nodes.map { it.position!!.y }
    val bottoms = nodes.map { it.position!!.y + (it.size.height ?: 0.0) }
    val minX = lefts.min(); val maxX = rights.max()
    val minY = tops.min(); val maxY = bottoms.max()
    val next = nodes.fold(document) { doc, node ->
        doc.updateNode(node.id) { n ->
            val p = n.position ?: DesignPoint()
            val w = n.size.width ?: 0.0
            val h = n.size.height ?: 0.0
            val np = if (horizontal) DesignPoint(minX + maxX - p.x - w, p.y) else DesignPoint(p.x, minY + maxY - p.y - h)
            n.copy(position = np)
        }
    }
    if (next == document) return this
    return pushHistory(document).copy(document = next)
}

// --- History -----------------------------------------------------------------

private fun DesignEditorState.undo(): DesignEditorState {
    val document = document ?: return this
    val previous = undoStack.lastOrNull() ?: return this
    return copy(
        document = previous,
        undoStack = undoStack.dropLast(1),
        redoStack = (redoStack + document).takeLast(MaxDocumentHistory),
    ).pruneSelection()
}

private fun DesignEditorState.redo(): DesignEditorState {
    val document = document ?: return this
    val next = redoStack.lastOrNull() ?: return this
    return copy(
        document = next,
        redoStack = redoStack.dropLast(1),
        undoStack = (undoStack + document).takeLast(MaxDocumentHistory),
    ).pruneSelection()
}

/** Drops selection ids no longer present after an undo/redo swap. */
private fun DesignEditorState.pruneSelection(): DesignEditorState {
    val doc = document ?: return this
    val kept = selectedNodeIds.filter { doc.nodeById(it) != null }.toSet()
    return copy(
        selectedNodeIds = kept,
        selectedNodeId = if (selectedNodeId in kept) selectedNodeId else kept.firstOrNull().orEmpty(),
    )
}

// --- Resize write-back (SLM source) -----------------------------------------

/**
 * Applies [DesignEditorIntent.ResizeNode] both to the SLM source (surgical patch +
 * recompile, keeping the fingerprint chain valid for the next write-back) and to the
 * in-memory working document (fixed sizing on the given axes). The working document
 * stays the single source of truth, so this write-back never discards other in-memory
 * edits or in-memory-created screens.
 */
private fun DesignEditorState.resizeNodeWriteBack(intent: DesignEditorIntent.ResizeNode): DesignEditorState {
    if (intent.width == null && intent.height == null) return this
    if (intent.nodeId.isBlank() || sources.isEmpty() || sources.size != compiledResults.size) return this
    val document = document ?: return this
    val edit = SetSizing(
        nodeId = intent.nodeId,
        width = intent.width?.let { SizingSpec(mode = SizingMode.Fixed, value = it) },
        height = intent.height?.let { SizingSpec(mode = SizingMode.Fixed, value = it) },
    )
    var preferredFailure: List<DesignDiagnostic> = emptyList()
    candidateSourceIndices(intent.nodeId).forEachIndexed { attempt, index ->
        val source = sources[index]
        val result = applySlmEdit(source.content, edit, compiledResults[index])
        val newSource = result.newSource
        if (newSource == null) {
            if (attempt == 0) preferredFailure = result.diagnostics
            return@forEachIndexed
        }
        val recompiled = compileSlm(newSource, SlmCompileOptions(fileName = source.fileName))
        if (!recompiled.isSuccess) {
            if (attempt == 0) preferredFailure = recompiled.diagnostics
            return@forEachIndexed
        }
        val newSources = sources.toMutableList().apply { this[index] = source.copy(content = newSource) }.toList()
        val newCompiled = compiledResults.toMutableList().apply { this[index] = recompiled }.toList()
        val patchedDocument = document.updateNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(
                size = DesignSize(intent.width ?: node.size.width, intent.height ?: node.size.height),
                sizing = sizing.copy(
                    horizontal = if (intent.width != null) SizingMode.Fixed else sizing.horizontal,
                    vertical = if (intent.height != null) SizingMode.Fixed else sizing.vertical,
                ),
            )
        }
        return copy(
            document = patchedDocument,
            diagnostics = result.diagnostics,
            sources = newSources,
            compiledResults = newCompiled,
            previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
            // Fork in-memory history like every other edit: checkpoint the pre-edit
            // document for Undo and clear redo, so a later Redo can't resurrect a
            // stale document that disagrees with the freshly patched sources.
            undoStack = if (interacting) undoStack else (undoStack + document).takeLast(MaxDocumentHistory),
            redoStack = if (interacting) redoStack else emptyList(),
        )
    }
    return copy(diagnostics = diagnostics + preferredFailure)
}

private fun DesignEditorState.candidateSourceIndices(nodeId: String): List<Int> {
    val indices = compiledResults.indices.toList()
    val pageId = document?.pageOfNode(nodeId)?.id ?: return indices
    val owner = indices.firstOrNull { index ->
        val compiledDocument = compiledResults[index].document ?: return@firstOrNull false
        val screenId = compiledDocument.screen?.id.orEmpty()
        compiledDocument.pages.any { page -> screenId.ifBlank { page.id } == pageId }
    } ?: return indices
    return listOf(owner) + indices.filterNot { it == owner }
}

// --- Node property helpers ---------------------------------------------------

/** True when [nodeId] is positioned by coordinates (root frame, absolute child, or child of a free parent). */
internal fun io.aequicor.visualization.engine.ir.model.DesignDocument.isCoordinatePositioned(nodeId: String): Boolean {
    if (topLevelOwnerPage(nodeId) != null) return true
    val node = nodeById(nodeId) ?: return false
    if (node.layoutChild.absolute) return true
    val parent = parentNodeOf(nodeId) ?: return true
    return parent.layout.mode == LayoutMode.None
}

private fun DesignInsets.withSide(side: PaddingSide, value: Double): DesignInsets {
    val v = value.coerceAtLeast(0.0).bindable()
    return when (side) {
        PaddingSide.Top -> copy(top = v)
        PaddingSide.Right -> copy(right = v)
        PaddingSide.Bottom -> copy(bottom = v)
        PaddingSide.Left -> copy(left = v)
        PaddingSide.All -> DesignInsets(v, v, v, v)
    }
}

private val DefaultFillColor: DesignColor = DesignColor.fromHex("#B9C4D2") ?: DesignColor(0xFFB9C4D2)
private val DefaultStrokeColor: DesignColor = DesignColor.fromHex("#1E88FF") ?: DesignColor(0xFF1E88FF)
private val DefaultShadowColor: DesignColor = DesignColor(0x40000000)

private fun DesignNode.applyFillOp(op: FillOp): DesignNode {
    val fills = fills.orEmpty().toMutableList()
    when (op) {
        FillOp.Add -> fills.add(DesignPaint.Solid(DefaultFillColor.bindable()))
        is FillOp.RemoveAt -> if (op.index in fills.indices) fills.removeAt(op.index)
        is FillOp.ToggleAt -> if (op.index in fills.indices) {
            fills[op.index] = fills[op.index].withVisible(!(fills[op.index].visible.literalOrNull() ?: true))
        }
        is FillOp.Move -> if (op.from in fills.indices && op.to in fills.indices) {
            val p = fills.removeAt(op.from); fills.add(op.to.coerceIn(0, fills.size), p)
        }
        is FillOp.SetColor -> if (op.index in fills.indices) {
            val prev = fills[op.index]
            fills[op.index] = DesignPaint.Solid(op.color.bindable(), prev.visible, prev.opacity, prev.blendMode)
        }
        is FillOp.SetOpacity -> if (op.index in fills.indices) {
            fills[op.index] = fills[op.index].withOpacity(op.opacity.coerceIn(0.0, 1.0))
        }
        is FillOp.SetType -> if (op.index in fills.indices) {
            fills[op.index] = convertFill(fills[op.index], op.type)
        }
        is FillOp.AddGradientStop -> if (op.index in fills.indices) {
            (fills[op.index] as? DesignPaint.Gradient)?.let { g ->
                val mid = midStop(g.stops)
                fills[op.index] = g.copy(stops = (g.stops + mid).sortedBy { it.position })
            }
        }
        is FillOp.RemoveGradientStop -> if (op.index in fills.indices) {
            (fills[op.index] as? DesignPaint.Gradient)?.let { g ->
                if (g.stops.size > 2 && op.stopIndex in g.stops.indices) {
                    fills[op.index] = g.copy(stops = g.stops.filterIndexed { i, _ -> i != op.stopIndex })
                }
            }
        }
        is FillOp.SetGradientStopColor -> if (op.index in fills.indices) {
            (fills[op.index] as? DesignPaint.Gradient)?.let { g ->
                if (op.stopIndex in g.stops.indices) {
                    fills[op.index] = g.copy(stops = g.stops.mapIndexed { i, s ->
                        if (i == op.stopIndex) s.copy(color = op.color.bindable()) else s
                    })
                }
            }
        }
        is FillOp.SetGradientStopPosition -> if (op.index in fills.indices) {
            (fills[op.index] as? DesignPaint.Gradient)?.let { g ->
                if (op.stopIndex in g.stops.indices) {
                    fills[op.index] = g.copy(stops = g.stops.mapIndexed { i, s ->
                        if (i == op.stopIndex) s.copy(position = op.position.coerceIn(0.0, 1.0)) else s
                    }.sortedBy { it.position })
                }
            }
        }
        is FillOp.ReverseGradient -> if (op.index in fills.indices) {
            (fills[op.index] as? DesignPaint.Gradient)?.let { g ->
                fills[op.index] = g.copy(stops = g.stops.map { it.copy(position = 1.0 - it.position) }.sortedBy { it.position })
            }
        }
        is FillOp.SetGradientAngle -> if (op.index in fills.indices) {
            (fills[op.index] as? DesignPaint.Gradient)?.let { g ->
                val (from, to) = angleToLine(op.angleDegrees)
                fills[op.index] = g.copy(from = from, to = to)
            }
        }
    }
    return copy(fills = fills.toList(), fillStyleId = "")
}

private fun convertFill(paint: DesignPaint, type: FillKind): DesignPaint {
    val seedColor = (paint as? DesignPaint.Solid)?.color
        ?: (paint as? DesignPaint.Gradient)?.stops?.firstOrNull()?.color
        ?: DefaultFillColor.bindable()
    return when (type) {
        FillKind.Solid -> DesignPaint.Solid(seedColor, paint.visible, paint.opacity, paint.blendMode)
        FillKind.LinearGradient, FillKind.RadialGradient -> {
            val kind = if (type == FillKind.LinearGradient) GradientKind.Linear else GradientKind.Radial
            val (from, to) = angleToLine(90.0)
            DesignPaint.Gradient(
                gradientType = kind,
                from = from,
                to = to,
                stops = listOf(
                    GradientStop(0.0, seedColor),
                    GradientStop(1.0, DesignColor.Transparent.bindable()),
                ),
                visible = paint.visible,
                opacity = paint.opacity,
                blendMode = paint.blendMode,
            )
        }
        FillKind.Image -> DesignPaint.Image(assetId = "", visible = paint.visible, opacity = paint.opacity, blendMode = paint.blendMode)
    }
}

private fun midStop(stops: List<GradientStop>): GradientStop {
    val sorted = stops.sortedBy { it.position }
    val a = sorted.firstOrNull()?.position ?: 0.0
    val b = sorted.lastOrNull()?.position ?: 1.0
    val color = sorted.firstOrNull()?.color ?: DefaultFillColor.bindable()
    return GradientStop((a + b) / 2.0, color)
}

private fun angleToLine(angleDegrees: Double): Pair<DesignPoint, DesignPoint> {
    val rad = angleDegrees * kotlin.math.PI / 180.0
    val dx = cos(rad) * 0.5
    val dy = sin(rad) * 0.5
    return DesignPoint(0.5 - dx, 0.5 - dy) to DesignPoint(0.5 + dx, 0.5 + dy)
}

private fun DesignNode.applyStrokeOp(op: StrokeOp): DesignNode = when (op) {
    StrokeOp.Add -> copy(
        strokes = DesignStrokes(paints = listOf(DesignPaint.Solid(DefaultStrokeColor.bindable())), weight = 1.0.bindable()),
        strokeStyleId = "",
    )
    StrokeOp.Remove -> copy(strokes = null, strokeStyleId = "")
    else -> {
        val strokes = this.strokes ?: DesignStrokes()
        val next = when (op) {
            is StrokeOp.SetVisible -> strokes.copy(paints = strokes.paints.mapIndexed { i, p -> if (i == 0) p.withVisible(op.visible) else p })
            is StrokeOp.SetColor -> strokes.copy(paints = listOf(DesignPaint.Solid(op.color.bindable())) + strokes.paints.drop(1))
            is StrokeOp.SetOpacity -> strokes.copy(paints = strokes.paints.mapIndexed { i, p -> if (i == 0) p.withOpacity(op.opacity.coerceIn(0.0, 1.0)) else p })
            is StrokeOp.SetWeight -> strokes.copy(weight = op.weight.coerceAtLeast(0.0).bindable())
            is StrokeOp.SetAlign -> strokes.copy(align = op.align)
            is StrokeOp.SetCap -> strokes.copy(cap = op.cap)
            is StrokeOp.SetDashed -> strokes.copy(dashPattern = if (op.dashed) listOf(6.0, 4.0) else emptyList())
            is StrokeOp.SetPerSide -> strokes.copy(weightPerSide = strokes.weightPerSide.mergePerSide(op))
            else -> strokes
        }
        copy(strokes = next, strokeStyleId = "")
    }
}

private fun DesignInsets?.mergePerSide(op: StrokeOp.SetPerSide): DesignInsets {
    val base = this ?: DesignInsets()
    return DesignInsets(
        top = op.top?.coerceAtLeast(0.0)?.bindable() ?: base.top,
        right = op.right?.coerceAtLeast(0.0)?.bindable() ?: base.right,
        bottom = op.bottom?.coerceAtLeast(0.0)?.bindable() ?: base.bottom,
        left = op.left?.coerceAtLeast(0.0)?.bindable() ?: base.left,
    )
}

private fun DesignNode.applyEffectOp(op: EffectOp): DesignNode {
    val effects = effects.toMutableList()
    when (op) {
        is EffectOp.Add -> effects.add(defaultEffect(op.type))
        is EffectOp.RemoveAt -> if (op.index in effects.indices) effects.removeAt(op.index)
        is EffectOp.ToggleAt -> if (op.index in effects.indices) {
            effects[op.index] = effects[op.index].withVisible(!(effects[op.index].visible.literalOrNull() ?: true))
        }
        is EffectOp.SetType -> if (op.index in effects.indices) {
            effects[op.index] = defaultEffect(op.type).withVisible(effects[op.index].visible.literalOrNull() ?: true)
        }
        is EffectOp.UpdateShadow -> if (op.index in effects.indices) {
            effects[op.index] = updateShadow(effects[op.index], op)
        }
        is EffectOp.SetBlurRadius -> if (op.index in effects.indices) {
            effects[op.index] = when (val e = effects[op.index]) {
                is DesignEffect.LayerBlur -> e.copy(radius = op.radius.coerceAtLeast(0.0))
                is DesignEffect.BackgroundBlur -> e.copy(radius = op.radius.coerceAtLeast(0.0))
                else -> e
            }
        }
    }
    return copy(effects = effects.toList(), effectStyleId = "")
}

private fun defaultEffect(type: EffectType): DesignEffect = when (type) {
    EffectType.DropShadow -> DesignEffect.DropShadow(color = DefaultShadowColor.bindable(), offset = DesignPoint(0.0, 4.0), blur = 12.0, spread = 0.0)
    EffectType.InnerShadow -> DesignEffect.InnerShadow(color = DefaultShadowColor.bindable(), offset = DesignPoint(0.0, 2.0), blur = 8.0, spread = 0.0)
    EffectType.LayerBlur -> DesignEffect.LayerBlur(radius = 8.0)
    EffectType.BackgroundBlur -> DesignEffect.BackgroundBlur(radius = 12.0)
}

private fun updateShadow(effect: DesignEffect, op: EffectOp.UpdateShadow): DesignEffect = when (effect) {
    is DesignEffect.DropShadow -> effect.copy(
        offset = DesignPoint(op.dx ?: effect.offset.x, op.dy ?: effect.offset.y),
        blur = op.blur?.coerceAtLeast(0.0) ?: effect.blur,
        spread = op.spread ?: effect.spread,
        color = op.color?.bindable() ?: effect.color,
    )
    is DesignEffect.InnerShadow -> effect.copy(
        offset = DesignPoint(op.dx ?: effect.offset.x, op.dy ?: effect.offset.y),
        blur = op.blur?.coerceAtLeast(0.0) ?: effect.blur,
        spread = op.spread ?: effect.spread,
        color = op.color?.bindable() ?: effect.color,
    )
    else -> effect
}

private fun DesignNode.applyTypography(patch: TypographyPatch): DesignNode {
    val kind = kind as? DesignNodeKind.Text ?: return this
    val base = kind.textStyle ?: DesignTextStyle()
    val merged = base.copy(
        fontFamily = patch.fontFamily ?: base.fontFamily,
        fontSize = patch.fontSize?.coerceAtLeast(1.0)?.bindable() ?: base.fontSize,
        fontWeight = patch.fontWeight?.bindable() ?: base.fontWeight,
        lineHeight = patch.lineHeightPercent?.let { UnitValue(DesignUnit.Percent, it) } ?: base.lineHeight,
        letterSpacing = patch.letterSpacing?.let { UnitValue(DesignUnit.Px, it) } ?: base.letterSpacing,
        textAlignHorizontal = patch.alignHorizontal ?: base.textAlignHorizontal,
        textAlignVertical = patch.alignVertical ?: base.textAlignVertical,
    )
    return copy(kind = kind.copy(textStyle = merged))
}

private fun DesignNode.moveVectorPoint(intent: DesignEditorIntent.MoveVectorPoint): DesignNode {
    val kind = kind as? DesignNodeKind.Shape ?: return this
    if (intent.pathIndex !in kind.paths.indices) return this
    val path = kind.paths[intent.pathIndex]
    val moved = translateSvgPoint(path.d, intent.pointIndex, intent.dx, intent.dy) ?: return this
    val newPaths = kind.paths.toMutableList().apply { this[intent.pathIndex] = path.copy(d = moved) }
    return copy(kind = kind.copy(paths = newPaths.toList()))
}

// --- Paint / effect variance helpers ----------------------------------------

private fun DesignPaint.withVisible(v: Boolean): DesignPaint = when (this) {
    is DesignPaint.Solid -> copy(visible = v.bindable())
    is DesignPaint.Gradient -> copy(visible = v.bindable())
    is DesignPaint.Image -> copy(visible = v.bindable())
    is DesignPaint.Video -> copy(visible = v.bindable())
    is DesignPaint.Unknown -> copy(visible = v.bindable())
}

private fun DesignPaint.withOpacity(o: Double): DesignPaint = when (this) {
    is DesignPaint.Solid -> copy(opacity = o.bindable())
    is DesignPaint.Gradient -> copy(opacity = o.bindable())
    is DesignPaint.Image -> copy(opacity = o.bindable())
    is DesignPaint.Video -> copy(opacity = o.bindable())
    is DesignPaint.Unknown -> copy(opacity = o.bindable())
}

private fun DesignEffect.withVisible(v: Boolean): DesignEffect = when (this) {
    is DesignEffect.DropShadow -> copy(visible = v.bindable())
    is DesignEffect.InnerShadow -> copy(visible = v.bindable())
    is DesignEffect.LayerBlur -> copy(visible = v.bindable())
    is DesignEffect.BackgroundBlur -> copy(visible = v.bindable())
    is DesignEffect.Unknown -> copy(visible = v.bindable())
}
