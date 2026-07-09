package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.edit.DeleteSection
import io.aequicor.visualization.engine.frontend.edit.InsertChildSubtree
import io.aequicor.visualization.engine.frontend.edit.LayoutProp
import io.aequicor.visualization.engine.frontend.edit.isInteractionExpressibleInSlm
import io.aequicor.visualization.engine.frontend.edit.MoveSection
import io.aequicor.visualization.engine.frontend.edit.RenameNode as RenameNodeEdit
import io.aequicor.visualization.engine.frontend.edit.SetEffects
import io.aequicor.visualization.engine.frontend.edit.SetFills
import io.aequicor.visualization.engine.frontend.edit.SetInteractions
import io.aequicor.visualization.engine.frontend.edit.SetMotion
import io.aequicor.visualization.engine.frontend.edit.SetLayoutProperty
import io.aequicor.visualization.engine.frontend.edit.SetNodeConstraints
import io.aequicor.visualization.engine.frontend.edit.SetStrokes
import io.aequicor.visualization.engine.frontend.edit.SetSizing
import io.aequicor.visualization.engine.frontend.edit.SetNodePosition
import io.aequicor.visualization.engine.frontend.edit.SetStyleProperty
import io.aequicor.visualization.engine.frontend.edit.SetText as SetTextEdit
import io.aequicor.visualization.engine.frontend.edit.SetTextStyle
import io.aequicor.visualization.engine.frontend.edit.SetTypedBlockScalar
import io.aequicor.visualization.engine.frontend.edit.ScreenSourceWriter
import io.aequicor.visualization.engine.frontend.edit.SizingSpec
import io.aequicor.visualization.engine.frontend.edit.SlmEdit
import io.aequicor.visualization.engine.frontend.edit.StyleProp
import io.aequicor.visualization.engine.frontend.edit.YamlScalarValue
import io.aequicor.visualization.engine.frontend.edit.applySlmEdits
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
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
 * Pure `(State, Intent) -> State` reducer. Selection and workspace-independent document
 * edits live here. Property edits that SLM can express (geometry, sizing, constraints,
 * visibility, lock, rename, text, opacity, radius, layout mode/gap/padding) write back to
 * the owning SLM source via [writeBackEdits] and mirror onto the working document in
 * lock-step, falling back to an in-memory-only edit for non-anchor / in-memory-created
 * nodes. Structural edits and not-yet-expressible property edits mutate only the working
 * document and record undo history ([DesignEditorState.editNode] / [editDocument]).
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
        is DesignEditorIntent.SetEditingText ->
            // Exiting ("" ) is always allowed; entering edit mode on a locked node is not.
            if (intent.nodeId.isNotBlank() && state.isNodeLocked(intent.nodeId)) state
            else state.copy(editingTextNodeId = intent.nodeId)

        // --- Position / size / transform ---
        is DesignEditorIntent.UpdatePosition -> state.editUnlockedNode(intent.nodeId) { node ->
            val current = node.position ?: DesignPoint()
            node.copy(position = DesignPoint(intent.x ?: current.x, intent.y ?: current.y))
        }
        is DesignEditorIntent.PositionNode -> state.positionNodeWriteBack(intent)
        is DesignEditorIntent.UpdateSize -> state.editUnlockedNode(intent.nodeId) { node ->
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
        is DesignEditorIntent.UpdateSizingMode -> state.editUnlockedNode(intent.nodeId) { node ->
            val sizing = node.sizing ?: DesignSizing()
            node.copy(sizing = sizing.copy(
                horizontal = intent.horizontal ?: sizing.horizontal,
                vertical = intent.vertical ?: sizing.vertical,
            ))
        }
        is DesignEditorIntent.UpdateConstraints -> state.updateConstraintsWriteBack(intent)
        is DesignEditorIntent.SetRotation -> state.editUnlockedNode(intent.nodeId) { it.copy(rotation = intent.degrees) }
        is DesignEditorIntent.SetAbsolutePosition -> state.editUnlockedNode(intent.nodeId) { node ->
            node.copy(
                layoutChild = node.layoutChild.copy(absolute = true),
                position = DesignPoint(intent.x, intent.y),
            )
        }
        is DesignEditorIntent.FlipHorizontal -> state.flip(intent.nodeIds, horizontal = true)
        is DesignEditorIntent.FlipVertical -> state.flip(intent.nodeIds, horizontal = false)

        // --- Visibility / lock / structure ---
        // These stay editable on a locked node (respectLock = false) so it can still be
        // revealed, unlocked or renamed; they also write back to the node contract in SLM.
        is DesignEditorIntent.SetVisible -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetTypedBlockScalar(intent.nodeId, TypedBlockKind.Node, listOf("visible"), YamlScalarValue.Bool(intent.visible))),
            respectLock = false,
        ) { it.copy(visible = intent.visible.bindable()) }
        is DesignEditorIntent.SetLocked -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetTypedBlockScalar(intent.nodeId, TypedBlockKind.Node, listOf("locked"), YamlScalarValue.Bool(intent.locked))),
            respectLock = false,
        ) { it.copy(locked = intent.locked) }
        is DesignEditorIntent.RenameNode -> state.writeBackEdits(
            intent.nodeId,
            listOf(RenameNodeEdit(intent.nodeId, intent.name)),
            respectLock = false,
        ) { it.copy(name = intent.name) }
        is DesignEditorIntent.DeleteNodes -> state.deleteNodesWriteBack(intent.nodeIds)
        is DesignEditorIntent.DuplicateNodes -> state.duplicateNodesWriteBack(intent.nodeIds)
        is DesignEditorIntent.ReorderNode -> state.reorderNodeWriteBack(intent)
        is DesignEditorIntent.ReparentNode -> state.reparentNodeWriteBack(intent)
        is DesignEditorIntent.CreateObject -> state.createObject(intent)
        is DesignEditorIntent.CreateScreen -> state.createScreenWriteBack(intent)
        is DesignEditorIntent.DetachInstance -> state.detachInstanceReduce(intent.nodeId)

        // --- Layout container ---
        is DesignEditorIntent.SetLayoutMode -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetLayoutProperty(intent.nodeId, LayoutProp.Mode, YamlScalarValue.Str(layoutModeToken(intent.mode.toLayoutMode())))),
        ) { node -> node.copy(layout = node.layout.copy(mode = intent.mode.toLayoutMode())) }
        is DesignEditorIntent.SetLayoutGap -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetLayoutProperty(intent.nodeId, LayoutProp.Gap, YamlScalarValue.Num(intent.gap))),
        ) { node -> node.copy(layout = node.layout.copy(gap = DesignGap.Fixed(intent.gap.bindable()))) }
        is DesignEditorIntent.SetLayoutPadding -> state.writeBackEdits(
            intent.nodeId,
            paddingWriteBackEdits(intent.nodeId, intent.side, intent.value),
        ) { node -> node.copy(layout = node.layout.copy(padding = node.layout.padding.withSide(intent.side, intent.value))) }
        is DesignEditorIntent.SetLayoutAlign -> state.editUnlockedNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(
                alignItems = intent.alignItems ?: node.layout.alignItems,
                justifyContent = intent.justifyContent ?: node.layout.justifyContent,
            ))
        }
        is DesignEditorIntent.SetClipsContent -> state.editUnlockedNode(intent.nodeId) { node ->
            node.copy(layout = node.layout.copy(clipsContent = intent.clips))
        }
        is DesignEditorIntent.SetSticky -> state.editUnlockedNode(intent.nodeId) { node ->
            node.copy(scroll = node.scroll.copy(sticky = intent.sticky))
        }

        // --- Appearance / fill / stroke / effects ---
        is DesignEditorIntent.UpdateOpacity -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetStyleProperty(intent.nodeId, StyleProp.Opacity, YamlScalarValue.Num(intent.opacity.coerceIn(0.0, 1.0)))),
        ) { it.copy(opacity = intent.opacity.coerceIn(0.0, 1.0).bindable()) }
        is DesignEditorIntent.SetBlendMode -> state.editUnlockedNode(intent.nodeId) { it.copy(blendMode = intent.blendMode) }
        is DesignEditorIntent.UpdateCornerRadius -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetStyleProperty(intent.nodeId, StyleProp.Radius, YamlScalarValue.Num(intent.radius.coerceAtLeast(0.0)))),
        ) { it.copy(cornerRadius = DesignCornerRadius.all(intent.radius.coerceAtLeast(0.0).bindable())) }
        is DesignEditorIntent.UpdateCornerRadiusPerCorner -> state.editUnlockedNode(intent.nodeId) { node ->
            val current = node.cornerRadius ?: DesignCornerRadius()
            node.copy(cornerRadius = current.copy(
                topLeft = intent.topLeft?.coerceAtLeast(0.0)?.bindable() ?: current.topLeft,
                topRight = intent.topRight?.coerceAtLeast(0.0)?.bindable() ?: current.topRight,
                bottomRight = intent.bottomRight?.coerceAtLeast(0.0)?.bindable() ?: current.bottomRight,
                bottomLeft = intent.bottomLeft?.coerceAtLeast(0.0)?.bindable() ?: current.bottomLeft,
            ))
        }
        is DesignEditorIntent.UpdateSolidFill -> state.fillsWriteBack(intent.nodeId) { node ->
            val solid = DesignPaint.Solid(intent.color.bindable())
            val fills = node.fills.orEmpty()
            node.copy(fills = if (fills.isEmpty()) listOf(solid) else listOf(solid) + fills.drop(1), fillStyleId = "")
        }
        is DesignEditorIntent.FillCommand -> state.fillsWriteBack(intent.nodeId) { it.applyFillOp(intent.op) }
        is DesignEditorIntent.UpdateStroke -> state.strokesWriteBack(intent.nodeId) { node ->
            val current = node.strokes ?: DesignStrokes()
            val paints = if (intent.color != null) {
                listOf(DesignPaint.Solid(intent.color.bindable())) + current.paints.drop(1)
            } else {
                current.paints
            }
            node.copy(strokes = current.copy(paints = paints, weight = intent.weight?.bindable() ?: current.weight), strokeStyleId = "")
        }
        is DesignEditorIntent.StrokeCommand -> state.strokesWriteBack(intent.nodeId) { it.applyStrokeOp(intent.op) }
        is DesignEditorIntent.EffectCommand -> state.effectsWriteBack(intent.nodeId) { it.applyEffectOp(intent.op) }

        // --- Typography ---
        is DesignEditorIntent.UpdateTypography -> state.typographyWriteBack(intent)
        is DesignEditorIntent.SetTextCharacters -> state.writeBackEdits(
            intent.nodeId,
            listOf(SetTextEdit(intent.nodeId, intent.text)),
        ) patch@{ node ->
            val kind = node.kind as? DesignNodeKind.Text ?: return@patch node
            node.copy(kind = kind.copy(characters = intent.text.bindable(), content = null))
        }
        is DesignEditorIntent.SetTextAutoResize -> state.editUnlockedNode(intent.nodeId) { node ->
            val kind = node.kind as? DesignNodeKind.Text ?: return@editUnlockedNode node
            node.copy(kind = kind.copy(autoResize = intent.mode))
        }

        // --- Prototype behavior (interactions + motion) ---
        is DesignEditorIntent.InteractionCommand -> state.interactionsWriteBack(intent.nodeId) { node ->
            node.copy(interactions = applyInteractionOp(node.interactions, intent.op, state.defaultNavTarget(intent.nodeId)))
        }
        is DesignEditorIntent.MotionCommand -> state.motionWriteBack(intent.nodeId) { node ->
            node.copy(motion = applyMotionOp(node.motion, intent.op))
        }

        // --- Vector ---
        is DesignEditorIntent.MoveVectorPoint -> state.editUnlockedNode(intent.nodeId) { it.moveVectorPoint(intent) }

        // --- Interaction checkpoints ---
        DesignEditorIntent.BeginInteraction -> state.beginInteraction()
        DesignEditorIntent.EndInteraction -> state.endInteraction()
        DesignEditorIntent.CancelInteraction -> state.cancelInteraction()

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

/**
 * Ends a drag. Drops the checkpoint taken at [beginInteraction] when the drag changed
 * nothing (a locked or auto-layout node that never moved), so an inert drag leaves no
 * "dead" undo entry.
 */
private fun DesignEditorState.endInteraction(): DesignEditorState {
    val document = document
    val inert = document != null && undoStack.lastOrNull() == document
    val trimmed = if (inert) copy(undoStack = undoStack.dropLast(1)) else this
    return trimmed.copy(interacting = false)
}

/**
 * Cancels a drag (Escape): restores the pre-drag document from the checkpoint and drops
 * it, so an aborted drag records no undo entry.
 */
private fun DesignEditorState.cancelInteraction(): DesignEditorState {
    val checkpoint = undoStack.lastOrNull() ?: return copy(interacting = false)
    return copy(
        document = checkpoint,
        undoStack = undoStack.dropLast(1),
        interacting = false,
    ).pruneSelection()
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

/**
 * Deletes [ids] from the working document (the authority + fallback) and, when the whole
 * selection lives in one SLM source and every deleted node is a heading-anchored section,
 * mirrors the delete into that source by dropping each heading footprint ([DeleteSection]).
 * A cross-page (multi-source) selection, or any node the patcher cannot address (ir-splice,
 * prose, the screen root) or that would drift a surviving id, leaves every source byte-identical
 * and keeps the in-memory delete — non-corrupting by construction (see [withStructuralSource]).
 */
private fun DesignEditorState.deleteNodesWriteBack(ids: Set<String>): DesignEditorState {
    val document = document ?: return this
    val deletable = ids.filter { document.nodeById(it)?.locked == false }.toSet()
    if (deletable.isEmpty()) return this
    val inMemory = deleteNodes(deletable)
    // Nothing actually left the tree (e.g. only unknown/locked ids): don't touch sources.
    if (inMemory === this) return this

    // Source write-back only when every deleted node shares one owning SLM source; a
    // cross-page selection (or an unresolvable owner) keeps the in-memory delete.
    val ownerIndices = deletable.map { owningSourceIndex(it) }
    val owner = ownerIndices.firstOrNull()
    if (owner == null || ownerIndices.any { it != owner }) return inMemory

    // Address only the delete roots — ids none of whose ancestors are also being deleted — so a
    // parent+descendant multi-select never tries to delete an already-removed section twice.
    val roots = deletable.filter { id -> document.ancestorIdsOf(id).none { it in deletable } }
    val edits = roots.map { DeleteSection(it) }
    val deletedSubtreeIds = deletable.flatMap { id ->
        document.nodeById(id)?.let { node -> listOf(node.id) + node.allDescendants().map { it.id } } ?: listOf(id)
    }.toSet()
    val ownerIds = compiledResults[owner].document?.pageTreeIds() ?: return inMemory
    val expected = ownerIds - deletedSubtreeIds
    return withStructuralSource(deletable.first(), edits, inMemory, expected)
}

/**
 * One planned duplication: a fresh-id [clone] to insert after [originalId] under [parentId]
 * at [index]. The plan is built by folding a working document so sibling clones that share a
 * prefix get distinct minted ids ([deepCopyWithFreshIds] sees the clones already placed).
 */
private data class DuplicationStep(
    val parentId: String,
    val originalId: String,
    val clone: DesignNode,
    val index: Int,
)

/**
 * Deterministic clone plan for duplicating [ids] (empty when nothing is duplicable). Pure in the
 * document, so the in-memory apply and the SLM write-back edits share identical minted ids.
 */
private fun DesignEditorState.duplicationPlan(ids: Set<String>): List<DuplicationStep> {
    val document = document ?: return emptyList()
    var working = document
    return buildList {
        // Duplicate each node next to itself; offset by (16,16) for visual separation.
        ids.forEach { id ->
            val original = working.nodeById(id) ?: return@forEach
            val clone = original.deepCopyWithFreshIds(working)
            val offset = clone.copy(position = clone.position?.let { DesignPoint(it.x + 16, it.y + 16) })
            val siblings = working.siblingsOf(id)
            val insertIndex = siblings.indexOfFirst { it.id == id }.let { if (it < 0) -1 else it + 1 }
            val parentId = working.parentNodeOf(id)?.id ?: working.topLevelOwnerPage(id)?.id ?: return@forEach
            working = working.insertNode(parentId, offset, insertIndex)
            add(DuplicationStep(parentId, id, offset, insertIndex))
        }
    }
}

/** In-memory duplicate: applies [plan] to the working document and selects the clones. */
private fun DesignEditorState.applyDuplication(plan: List<DuplicationStep>): DesignEditorState {
    val document = document ?: return this
    val working = plan.fold(document) { doc, step -> doc.insertNode(step.parentId, step.clone, step.index) }
    return pushHistory(document).copy(document = working).selectMany(plan.map { it.clone.id }.toSet())
}

private fun DesignEditorState.duplicateNodes(ids: Set<String>): DesignEditorState {
    val plan = duplicationPlan(ids)
    if (plan.isEmpty()) return this
    return applyDuplication(plan)
}

/**
 * Duplicates [ids] in the working document (the authority + fallback) and, when every clone is a
 * heading-expressible subtree owned by one SLM source, mirrors each clone into that source as a
 * fresh child section landing right after its original ([InsertChildSubtree]). A cross-page
 * selection, a clone the writer cannot faithfully round-trip (instance / media / vector / …), an
 * unaddressable parent-or-sibling, or any surviving-id drift keeps every source byte-identical and
 * the in-memory duplicate — non-corrupting by construction (see [withStructuralSource]).
 */
private fun DesignEditorState.duplicateNodesWriteBack(ids: Set<String>): DesignEditorState {
    val plan = duplicationPlan(ids)
    if (plan.isEmpty()) return this
    val inMemory = applyDuplication(plan)

    // Only faithfully-expressible clones write back; anything the section writer can't round-trip
    // (its kind survives the id-set veto but its semantics wouldn't) stays in-memory.
    if (plan.any { !it.clone.isStructurallyExpressible() }) return inMemory
    // Every duplicated node must share one owning SLM source; a cross-page selection (or an
    // unresolvable owner) keeps the in-memory duplicate.
    val owners = plan.map { owningSourceIndex(it.originalId) }
    val owner = owners.firstOrNull()
    if (owner == null || owners.any { it != owner }) return inMemory
    val ownerIds = compiledResults[owner].document?.pageTreeIds() ?: return inMemory
    val mintedIds = plan.flatMap { step -> listOf(step.clone.id) + step.clone.allDescendants().map { it.id } }
    val expected = ownerIds + mintedIds
    // Each clone lands right after its original's footprint (afterSiblingId), preserving the
    // sibling order the working document already reflects.
    val edits = plan.map { InsertChildSubtree(it.parentId, it.clone, afterSiblingId = it.originalId) }
    return withStructuralSource(plan.first().originalId, edits, inMemory, expected)
}

/**
 * Reorders [intent.nodeId] among its siblings in the working document (the authority + fallback)
 * and, when the whole sibling run is heading-addressable in one owning SLM source, mirrors the new
 * z-order into that source by writing an explicit `order:` scalar over every sibling in its new
 * visual order (10, 20, 30, ...). A reorder never changes the node set, so [withStructuralSource]
 * runs with the owning page's id set unchanged (the corruption net still discards any drift). An
 * ir-splice / prose sibling (e.g. `card_2`) has no addressable heading anchor and aborts the whole
 * `order:` batch, so a reorder among that run keeps the in-memory move with every source
 * byte-identical — non-corrupting by construction (see [withStructuralSource]).
 */
private fun DesignEditorState.reorderNodeWriteBack(intent: DesignEditorIntent.ReorderNode): DesignEditorState {
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
    val inMemory = editDocument { it.reorderSibling(intent.nodeId, target) }
    // Defensive: an inert move (never happens once target != current) writes nothing.
    if (inMemory === this) return this

    // Persist the new z-order: rewrite `order:` across the whole sibling run in its new visual
    // order so a recompile's stable order-sort reproduces exactly the in-memory arrangement.
    // Every sibling must be one owning source's heading anchor; any unaddressable sibling aborts
    // the batch and the reorder stays in-memory.
    val newSiblings = inMemory.document?.siblingsOf(intent.nodeId) ?: return inMemory
    val owner = owningSourceIndex(intent.nodeId) ?: return inMemory
    val expected = compiledResults[owner].document?.pageTreeIds() ?: return inMemory
    val edits = newSiblings.mapIndexed { index, sibling ->
        SetTypedBlockScalar(
            nodeId = sibling.id,
            blockKind = TypedBlockKind.Node,
            yamlPath = listOf("order"),
            scalar = YamlScalarValue.Num((index + 1) * 10.0),
        )
    }
    return withStructuralSource(intent.nodeId, edits, inMemory, expected)
}

/**
 * Reparents [intent.nodeId] under [intent.newParentId] in the working document (the authority +
 * fallback) and, when the whole move is expressible as a same-page heading relocation, mirrors it
 * into the owning SLM source by re-leveling and relocating the section ([MoveSection]). A reparent
 * never changes the owning page's node set, so [withStructuralSource] runs with that id set
 * unchanged plus an explicit parent-of(moved)==newParent check (id-set equality alone can't see a
 * wrong-parent placement). The move stays in-memory — every source byte-identical — when: the moved
 * subtree isn't faithfully expressible (instance / media / vector-path); the two ends live on
 * different pages (a two-source transaction a single-source patch can't express); the moved node
 * lands before existing children (only an append/after-sibling relocation is expressible); the
 * post-move heading depth would exceed 6; or any member has no addressable heading anchor (an
 * ir-splice / prose sibling or root), which makes the patch abort — non-corrupting by construction.
 */
private fun DesignEditorState.reparentNodeWriteBack(intent: DesignEditorIntent.ReparentNode): DesignEditorState {
    val document = document ?: return this
    val moving = document.nodeById(intent.nodeId) ?: return this
    val inMemory = editDocument { it.reparent(intent.nodeId, intent.newParentId, intent.index) }
    // No-op (cycle, missing end, or unchanged tree): don't touch sources.
    if (inMemory === this) return inMemory

    // Only faithfully-expressible subtrees write back (instance/media/vector-path stay in-memory).
    if (!moving.isStructurallyExpressible()) return inMemory
    // Both ends must share one owning SLM source; a cross-page move (or a page-id parent, which
    // owningSourceIndex can't resolve) keeps the in-memory reparent.
    val movingOwner = owningSourceIndex(intent.nodeId) ?: return inMemory
    val parentOwner = owningSourceIndex(intent.newParentId) ?: return inMemory
    if (movingOwner != parentOwner) return inMemory
    // Post-move heading depth: the moved root lands one level under the parent (whose source level
    // is its node-ancestor count + 1), and its subtree extends `subtreeHeight` deeper.
    val childLevel = document.ancestorIdsOf(intent.newParentId).size + 2
    if (childLevel + moving.subtreeHeight() > 6) return inMemory

    // Placement: land right after the sibling preceding the moved node in its NEW order, so the
    // source order matches the in-memory tree. Landing before existing children (index 0 with
    // siblings) is not an append/after-sibling relocation, so it stays in-memory.
    val newSiblings = inMemory.document?.siblingsOf(intent.nodeId) ?: return inMemory
    val movedIndex = newSiblings.indexOfFirst { it.id == intent.nodeId }
    if (movedIndex < 0) return inMemory
    val afterSiblingId = when {
        movedIndex == 0 && newSiblings.size == 1 -> null
        movedIndex == 0 -> return inMemory
        else -> newSiblings[movedIndex - 1].id
    }

    val expected = compiledResults[movingOwner].document?.pageTreeIds() ?: return inMemory
    val edit = MoveSection(intent.nodeId, intent.newParentId, afterSiblingId)
    return withStructuralSource(intent.nodeId, listOf(edit), inMemory, expected) { recompiled ->
        recompiled.parentNodeOf(intent.nodeId)?.id == intent.newParentId
    }
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
    // When the parent runs an Auto layout (row/column/grid), a newly created object should
    // flow inside it (Figma: dropping into an Auto layout frame inserts it into the stack)
    // rather than float at absolute coordinates. Free / top-level parents keep the factory's
    // absolute placement.
    val placed = when (document.nodeById(parentId)?.layout?.mode) {
        LayoutMode.Horizontal, LayoutMode.Vertical, LayoutMode.Grid ->
            node.copy(layoutChild = node.layoutChild.copy(absolute = false))
        else -> node
    }
    val next = document.insertNode(parentId, placed)
    if (next == document) return this
    val inMemory = pushHistory(document).copy(
        document = next,
        selectedNodeId = placed.id,
        selectedNodeIds = setOf(placed.id),
        editingTextNodeId = if (intent.enterTextEditing) placed.id else "",
    )
    // Structural write-back: emit `placed` as a fresh child section under the parent's owning
    // source ([InsertChildSubtree], appended at the end of the parent's footprint). A page-id /
    // prose / unaddressable parent, a node the section writer can't faithfully round-trip, or any
    // surviving-id drift keeps the in-memory create with every source byte-identical.
    if (!placed.isStructurallyExpressible()) return inMemory
    val owner = owningSourceIndex(parentId) ?: return inMemory
    val ownerIds = compiledResults[owner].document?.pageTreeIds() ?: return inMemory
    val expected = ownerIds + placed.id + placed.allDescendants().map { it.id }
    return withStructuralSource(parentId, listOf(InsertChildSubtree(parentId, placed)), inMemory, expected)
}

private fun DesignEditorState.detachInstanceReduce(nodeId: String): DesignEditorState {
    val document = document ?: return this
    if (isNodeLocked(nodeId)) return this
    val next = detachInstance(document, nodeId) ?: return this
    if (next == document) return this
    return pushHistory(document).copy(
        document = next,
        selectedNodeId = nodeId,
        selectedNodeIds = setOf(nodeId),
        editingTextNodeId = "",
    )
}

/**
 * Adds a fresh screen [page][EditorNodeFactory.newScreen] to the working document (the authority
 * + fallback) and, when the page renders to a standalone SLM document that compiles back to
 * exactly the minted screen id and root frame id, appends that document as a **new source**
 * ([ScreenSourceWriter]) so the created screen persists — a new screen has no owning source to
 * patch, so it grows the source list rather than editing an existing entry (mirroring
 * [addPage]). A render that fails to compile, or drifts the screen/root id, keeps the in-memory
 * page only, leaving every existing source byte-identical — non-corrupting by construction.
 */
private fun DesignEditorState.createScreenWriteBack(intent: DesignEditorIntent.CreateScreen): DesignEditorState {
    val document = document ?: return this
    val page = EditorNodeFactory.newScreen(document, intent.preset, intent.title)
    val rootId = page.children.firstOrNull()?.id.orEmpty()
    val inMemory = pushHistory(document).copy(
        document = document.addPage(page),
        selectedPageId = page.id,
        selectedNodeId = rootId,
        selectedNodeIds = if (rootId.isBlank()) emptySet() else setOf(rootId),
        editingTextNodeId = "",
    )
    // Source write-back only over a consistent source/compile list.
    if (sources.size != compiledResults.size) return inMemory
    val fileName = "${page.id}.layout.md"
    val sourceLocale = document.i18n.sourceLocale.ifBlank { "en-US" }
    val text = ScreenSourceWriter.render(page, sourceLocale)
    val options = SlmCompileOptions(fileName = fileName)
    val compiled = compileSlm(text, options)
    val compiledDocument = compiled.document ?: return inMemory
    // Id-preservation net: the rendered document must recompile to exactly the screen id we
    // minted (mergeMissionDocuments re-ids the page from `screen`) and the root frame's node
    // id — any drift discards the source and keeps the in-memory-only page.
    val screenMatches = compiledDocument.screen?.id == page.id
    val nodesMatch = compiledDocument.pageTreeIds() == page.allNodes().map { it.id }.toSet()
    if (!screenMatches || !nodesMatch) return inMemory
    return inMemory.copy(
        sources = sources + MissionDocumentSource(fileName, text),
        compiledResults = compiledResults + compiled,
        previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
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

// --- SLM source write-back --------------------------------------------------

/**
 * Applies [edits] to the SLM source that owns [nodeId] (surgical patch + recompile,
 * keeping the fingerprint chain valid for the next write-back) and mirrors the change
 * onto the in-memory working document via [patchNode], in lock-step. The working
 * document stays the single source of truth, so a write-back never discards other
 * in-memory edits or in-memory-created screens.
 *
 * Falls back to an in-memory-only edit when there is no source, the node has no source
 * span (a non-anchor or editor-created node), or the patch/recompile fails — so the
 * canvas always reflects the edit even when SLM cannot express it, surfacing the primary
 * write-back diagnostic when one applies. When [respectLock] is true a locked node is
 * left untouched; visibility, lock and rename pass false so a locked layer stays editable.
 */
private fun DesignEditorState.writeBackEdits(
    nodeId: String,
    edits: List<SlmEdit>,
    respectLock: Boolean = true,
    patchNode: (DesignNode) -> DesignNode,
): DesignEditorState {
    if (nodeId.isBlank() || edits.isEmpty()) return this
    if (respectLock && isNodeLocked(nodeId)) return this
    val document = document
    val inMemory: () -> DesignEditorState =
        { if (respectLock) editUnlockedNode(nodeId, patchNode) else editNode(nodeId, patchNode) }
    if (document == null || sources.isEmpty() || sources.size != compiledResults.size) {
        return inMemory()
    }
    var preferredFailure: List<DesignDiagnostic> = emptyList()
    candidateSourceIndices(nodeId).forEachIndexed { attempt, index ->
        val source = sources[index]
        val options = SlmCompileOptions(fileName = source.fileName)
        val result = applySlmEdits(source.content, edits, compiledResults[index], options)
        val newSource = result.newSource
        if (newSource == null) {
            if (attempt == 0) preferredFailure = result.diagnostics
            return@forEachIndexed
        }
        val recompiled = compileSlm(newSource, options)
        if (!recompiled.isSuccess) {
            if (attempt == 0) preferredFailure = recompiled.diagnostics
            return@forEachIndexed
        }
        val newSources = sources.toMutableList().apply { this[index] = source.copy(content = newSource) }.toList()
        val newCompiled = compiledResults.toMutableList().apply { this[index] = recompiled }.toList()
        return copy(
            document = document.updateNode(nodeId, patchNode),
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
    // No source accepted the edit: apply it in-memory so the canvas still reflects it,
    // keeping the primary write-back diagnostic (e.g. an unaddressable / unknown node).
    val fallback = inMemory()
    return if (preferredFailure.isEmpty()) fallback else fallback.copy(diagnostics = fallback.diagnostics + preferredFailure)
}

/**
 * Routes an interactions edit through SLM write-back. [transform] both derives the whole-set
 * `SetInteractions` payload and mirrors onto the working document; an inexpressible interaction
 * (CubicBezier easing, unknown action, …) or an unaddressable node falls back to in-memory via
 * [writeBackEdits]. An empty list cleanly removes the blocks.
 */
private fun DesignEditorState.interactionsWriteBack(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val node = document?.nodeById(nodeId) ?: return this
    if (isNodeLocked(nodeId)) return this
    return writeBackEdits(nodeId, listOf(SetInteractions(nodeId, transform(node).interactions)), patchNode = transform)
}

/** Routes a motion edit through write-back; a null motion cleanly removes the `motion:` block. */
private fun DesignEditorState.motionWriteBack(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val node = document?.nodeById(nodeId) ?: return this
    if (isNodeLocked(nodeId)) return this
    return writeBackEdits(nodeId, listOf(SetMotion(nodeId, transform(node).motion)), patchNode = transform)
}

/** First screen a Navigate authored on [nodeId] should default to: the first OTHER page id. */
private fun DesignEditorState.defaultNavTarget(nodeId: String): String {
    val doc = document ?: return ""
    val currentPageId = doc.pageOfNode(nodeId)?.id
    return doc.pages.firstOrNull { it.id != currentPageId }?.id ?: currentPageId.orEmpty()
}

/**
 * Routes a fills edit through SLM write-back when the resulting list is non-empty
 * (removing every fill would leave a dangling `fills:`, so that falls back to in-memory).
 * [transform] both derives the payload and mirrors onto the working document.
 */
private fun DesignEditorState.fillsWriteBack(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val node = document?.nodeById(nodeId) ?: return this
    if (isNodeLocked(nodeId)) return this
    val newFills = transform(node).fills.orEmpty()
    return if (newFills.isEmpty()) editUnlockedNode(nodeId, transform)
    else writeBackEdits(nodeId, listOf(SetFills(nodeId, newFills)), patchNode = transform)
}

/**
 * Routes a strokes edit through write-back. Stroke removal (null / no paints) and
 * per-side weights (which the SLM strokes reader cannot express) fall back to in-memory.
 */
private fun DesignEditorState.strokesWriteBack(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val node = document?.nodeById(nodeId) ?: return this
    if (isNodeLocked(nodeId)) return this
    val newStrokes = transform(node).strokes
    return if (newStrokes == null || newStrokes.paints.isEmpty() || newStrokes.weightPerSide != null) {
        editUnlockedNode(nodeId, transform)
    } else {
        writeBackEdits(nodeId, listOf(SetStrokes(nodeId, newStrokes)), patchNode = transform)
    }
}

/** Routes an effects edit through write-back when the resulting list is non-empty. */
private fun DesignEditorState.effectsWriteBack(
    nodeId: String,
    transform: (DesignNode) -> DesignNode,
): DesignEditorState {
    val node = document?.nodeById(nodeId) ?: return this
    if (isNodeLocked(nodeId)) return this
    val newEffects = transform(node).effects
    return if (newEffects.isEmpty()) editUnlockedNode(nodeId, transform)
    else writeBackEdits(nodeId, listOf(SetEffects(nodeId, newEffects)), patchNode = transform)
}

/**
 * Routes a typography edit through SLM write-back. The merged [DesignTextStyle] is
 * serialized into the node's `text.typography` block ([SetTextStyle], creating the block
 * when absent) and mirrored onto the working document. Non-text nodes are a no-op; a node
 * with no source anchor, or a percent-vs-px `lineHeight`/`letterSpacing` merge conflict the
 * patcher can't express in place, falls back to an in-memory-only edit via [writeBackEdits].
 */
private fun DesignEditorState.typographyWriteBack(intent: DesignEditorIntent.UpdateTypography): DesignEditorState {
    val node = document?.nodeById(intent.nodeId) ?: return this
    if (node.kind !is DesignNodeKind.Text) return this
    val style = (node.applyTypography(intent.patch).kind as DesignNodeKind.Text).textStyle ?: return this
    return writeBackEdits(intent.nodeId, listOf(SetTextStyle(intent.nodeId, style))) { it.applyTypography(intent.patch) }
}

private fun DesignEditorState.resizeNodeWriteBack(intent: DesignEditorIntent.ResizeNode): DesignEditorState {
    if (intent.width == null && intent.height == null) return this
    val edit = SetSizing(
        nodeId = intent.nodeId,
        width = intent.width?.let { SizingSpec(mode = SizingMode.Fixed, value = it) },
        height = intent.height?.let { SizingSpec(mode = SizingMode.Fixed, value = it) },
    )
    return writeBackEdits(intent.nodeId, listOf(edit)) { node ->
        val sizing = node.sizing ?: DesignSizing()
        node.copy(
            size = DesignSize(intent.width ?: node.size.width, intent.height ?: node.size.height),
            sizing = sizing.copy(
                horizontal = if (intent.width != null) SizingMode.Fixed else sizing.horizontal,
                vertical = if (intent.height != null) SizingMode.Fixed else sizing.vertical,
            ),
        )
    }
}

private fun DesignEditorState.positionNodeWriteBack(intent: DesignEditorIntent.PositionNode): DesignEditorState =
    writeBackEdits(intent.nodeId, listOf(SetNodePosition(intent.nodeId, intent.x, intent.y))) { node ->
        node.copy(position = DesignPoint(intent.x, intent.y))
    }

private fun DesignEditorState.updateConstraintsWriteBack(intent: DesignEditorIntent.UpdateConstraints): DesignEditorState {
    if (intent.horizontal == null && intent.vertical == null) return this
    return writeBackEdits(
        intent.nodeId,
        listOf(SetNodeConstraints(intent.nodeId, intent.horizontal, intent.vertical)),
    ) { node ->
        node.copy(constraints = node.constraints.copy(
            horizontal = intent.horizontal ?: node.constraints.horizontal,
            vertical = intent.vertical ?: node.constraints.vertical,
        ))
    }
}

/** SLM `layout.mode` token for [mode]; matches `ReaderEnums.layoutMode`. */
private fun layoutModeToken(mode: LayoutMode): String = when (mode) {
    LayoutMode.None -> "none"
    LayoutMode.Horizontal -> "row"
    LayoutMode.Vertical -> "column"
    LayoutMode.Grid -> "grid"
}

/**
 * Padding write-back edits for [side]. Per-side logical keys (blockStart/inlineEnd/…)
 * override any authored `block`/`inline` shorthand on recompile, so [PaddingSide.All]
 * writes all four sides to keep the source and the mirrored [DesignInsets] in agreement.
 */
private fun paddingWriteBackEdits(nodeId: String, side: PaddingSide, value: Double): List<SlmEdit> {
    val scalar = YamlScalarValue.Num(value.coerceAtLeast(0.0))
    val props = when (side) {
        PaddingSide.Top -> listOf(LayoutProp.PaddingTop)
        PaddingSide.Right -> listOf(LayoutProp.PaddingRight)
        PaddingSide.Bottom -> listOf(LayoutProp.PaddingBottom)
        PaddingSide.Left -> listOf(LayoutProp.PaddingLeft)
        PaddingSide.All -> listOf(LayoutProp.PaddingTop, LayoutProp.PaddingRight, LayoutProp.PaddingBottom, LayoutProp.PaddingLeft)
    }
    return props.map { SetLayoutProperty(nodeId, it, scalar) }
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

/** The single SLM source index that owns [nodeId]'s page, or null when it can't be resolved. */
private fun DesignEditorState.owningSourceIndex(nodeId: String): Int? {
    val pageId = document?.pageOfNode(nodeId)?.id ?: return null
    return compiledResults.indices.firstOrNull { index ->
        val compiledDocument = compiledResults[index].document ?: return@firstOrNull false
        val screenId = compiledDocument.screen?.id.orEmpty()
        compiledDocument.pages.any { page -> screenId.ifBlank { page.id } == pageId }
    }
}

// --- Structural SLM source write-back ---------------------------------------

/**
 * Overlays a structural SLM write-back onto an already-computed [inMemory] result: applies
 * [edits] (whole-section insert/delete/move) to the single source owning [ownerNodeId],
 * recompiles it, and — only when the recompiled page's node-id set matches [expectedPageIds]
 * exactly — swaps the patched source and its compile into [inMemory]. The [inMemory] state
 * stays the document/selection/undo authority; the wrapper only touches [sources] /
 * [compiledResults] (leaving every other source byte-identical) and captures the pre-edit
 * source undo entry.
 *
 * The id-set comparison is the corruption net (blueprint's id-preservation strategy): a failed
 * patch, a failed recompile, or ANY id drift — a synthetic id renumbering, a collision suffix,
 * an unexpected loss — discards the patched source and returns [inMemory] unchanged, so a
 * structural edit can never corrupt the authored SLM. The caller supplies [expectedPageIds] as
 * the owning source's pre-edit id set adjusted for the edit (minus a deleted subtree, plus the
 * minted ids of an insert, unchanged for a same-parent move).
 *
 * [verify] is an extra structural assertion run on the recompiled owning document after the id-set
 * veto passes (it defaults to always-true). Reparent uses it to assert parent-of(moved)==newParent,
 * which id-set equality alone cannot see: a wrong-parent placement keeps the same id set but fails
 * [verify], discarding the patch.
 */
private fun DesignEditorState.withStructuralSource(
    ownerNodeId: String,
    edits: List<SlmEdit>,
    inMemory: DesignEditorState,
    expectedPageIds: Set<String>,
    verify: (DesignDocument) -> Boolean = { true },
): DesignEditorState {
    if (edits.isEmpty()) return inMemory
    if (sources.isEmpty() || sources.size != compiledResults.size) return inMemory
    val index = owningSourceIndex(ownerNodeId) ?: return inMemory
    val source = sources[index]
    val options = SlmCompileOptions(fileName = source.fileName)
    val result = applySlmEdits(source.content, edits, compiledResults[index], options)
    val newSource = result.newSource ?: return inMemory
    val recompiled = compileSlm(newSource, options)
    val recompiledDocument = recompiled.document ?: return inMemory
    // Id-preservation veto: the patched source must recompile to EXACTLY the ids we expect.
    if (recompiledDocument.pageTreeIds() != expectedPageIds) return inMemory
    // Identity veto: the recompiled owning document must reproduce the in-memory working tree
    // node-for-node — same id -> intrinsic fingerprint — not merely the same id *set*. Two
    // same-slug id-less nodes trading ids on recompile (an identity SWAP: e.g. a reparent that
    // moves an id-less node next to an id-less same-name sibling, so the slug generator re-mints
    // both by document order) keep the id set intact but diverge here. That discards the patched
    // source and keeps the in-memory edit, so a structural write-back can never persist a swapped
    // (corrupted) mapping — it gracefully falls back instead.
    val intended = inMemory.document
    if (intended != null &&
        expectedPageIds.any { id ->
            intended.nodeById(id)?.identityFingerprint() != recompiledDocument.nodeById(id)?.identityFingerprint()
        }
    ) {
        return inMemory
    }
    // Structural veto: extra placement assertion the id-set check can't see (reparent parent-of).
    if (!verify(recompiledDocument)) return inMemory
    val newSources = sources.toMutableList().apply { this[index] = source.copy(content = newSource) }.toList()
    val newCompiled = compiledResults.toMutableList().apply { this[index] = recompiled }.toList()
    return inMemory.copy(
        diagnostics = result.diagnostics,
        sources = newSources,
        compiledResults = newCompiled,
        previousSources = (previousSources + listOf(sources)).takeLast(MaxSourceHistory),
    )
}

/** Every node id in the page tree (component subtrees live outside pages and are excluded). */
private fun DesignDocument.pageTreeIds(): Set<String> =
    pages.flatMap { it.allNodes() }.map { it.id }.toSet()

/**
 * Parent-, id- and position-independent fingerprint of a node's own intrinsic, authored
 * (pre-layout) properties. Compared per id across a structural write-back's expected id set (see
 * [withStructuralSource]) to catch an identity *swap* — two same-slug id-less nodes trading ids on
 * recompile — that the id-*set* veto cannot see. Deliberately excludes id / order / position /
 * parent (a relocation legitimately changes those) and every resolved/derived field, so a faithful
 * edit's recompiled node fingerprints exactly match the in-memory working tree and never false-trip.
 */
private fun DesignNode.identityFingerprint(): List<Any?> = listOf(
    name,
    type,
    size.width,
    size.height,
    kindFingerprint(kind),
    // Authored behavior must survive a structural rewrite: a section that recompiles without its
    // interactions/motion diverges here and the veto keeps the edit in-memory. Source maps differ
    // between the intended tree and a recompile, so compare interactions without them.
    interactions.map { it.copy(sourceMap = null) },
    motion,
)

/** Kind discriminator for [identityFingerprint]: enough to tell same-name/size nodes of different kinds apart. */
private fun kindFingerprint(kind: DesignNodeKind): Any? = when (kind) {
    DesignNodeKind.Frame -> "frame"
    is DesignNodeKind.Text -> listOf("text", kind.content?.defaultText ?: kind.characters.literalOrNull())
    is DesignNodeKind.Shape -> listOf("shape", kind.shape.name, kind.pointCount, kind.innerRadius)
    is DesignNodeKind.Instance -> listOf("instance", kind.componentId.literalOrNull())
    else -> kind::class.simpleName
}

/**
 * True when this node and its whole subtree are faithfully expressible as fresh SLM heading
 * sections by `NodeSectionWriter` — frame / parametric-shape / text kinds only. Instances,
 * media, tables, boolean ops, slots and vector-path shapes are excluded: the section writer
 * would drop their defining semantics (component ref, media asset, vector `d`, …) while the
 * id-set veto — which only compares ids — cannot see the resulting wrong-kind node. Such a
 * subtree therefore stays in-memory rather than risk writing a section that recompiles wrong.
 */
private fun DesignNode.isStructurallyExpressible(): Boolean {
    val kindOk = when (val nodeKind = kind) {
        DesignNodeKind.Frame -> true
        is DesignNodeKind.Text -> true
        is DesignNodeKind.Shape -> nodeKind.paths.isEmpty() && nodeKind.iconRef.isEmpty() && nodeKind.pathRef.isEmpty()
        else -> false
    }
    // A node carrying an interaction the SLM writer can't round-trip (CubicBezier easing, unknown
    // action, …) would persist a behavior-stripped section — keep the whole subtree in-memory.
    val behaviorOk = interactions.all { isInteractionExpressibleInSlm(it) }
    return kindOk && behaviorOk && children.all { it.isStructurallyExpressible() }
}

/**
 * Longest descendant chain below this node: 0 for a leaf, 1 for a node with only leaf children, etc.
 * Combined with the new parent's heading level it bounds the deepest heading a reparent would emit.
 */
private fun DesignNode.subtreeHeight(): Int =
    if (children.isEmpty()) 0 else 1 + children.maxOf { it.subtreeHeight() }

/** Ids of every ancestor of [nodeId], nearest first; empty for a top-level node. */
private fun DesignDocument.ancestorIdsOf(nodeId: String): Set<String> {
    val result = mutableSetOf<String>()
    var current = parentNodeOf(nodeId)
    while (current != null) {
        result += current.id
        current = parentNodeOf(current.id)
    }
    return result
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
