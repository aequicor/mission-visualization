package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DefaultDesignDocumentRepository
import io.aequicor.visualization.editor.domain.LoadDesignDocumentUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.EditorLayoutMode
import io.aequicor.visualization.editor.presentation.EffectOp
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillKind
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.pressHitBelongsToSelection
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the in-memory editor commands added on top of the resize write-back path:
 * selection, structure (create/delete/duplicate/reorder), appearance stacks and undo.
 */
class DesignEditorReducerCommandsTest {

    private fun freshState(): DesignEditorState =
        createDesignEditorState(LoadDesignDocumentUseCase(DefaultDesignDocumentRepository())())

    private fun DesignEditorState.rootFrameId(): String =
        assertNotNull(document?.pageById(selectedPageId)?.children?.firstOrNull()?.id, "no root frame")

    // --- Selection ---

    @Test
    fun multiSelectionAddsAndTogglesNodes() {
        var state = freshState()
        val root = state.rootFrameId()
        val second = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id, "root has a child")

        state = reduceDesignEditor(state, DesignEditorIntent.SelectNode(root))
        state = reduceDesignEditor(state, DesignEditorIntent.ToggleNodeSelection(second))
        assertEquals(setOf(root, second), state.selectedNodeIds)
        assertTrue(state.hasMultiSelection)

        state = reduceDesignEditor(state, DesignEditorIntent.ToggleNodeSelection(second))
        assertEquals(setOf(root), state.selectedNodeIds)
        assertEquals(root, state.selectedNodeId)
    }

    @Test
    fun selectAllSelectsTopLevelChildren() {
        val state = reduceDesignEditor(freshState(), DesignEditorIntent.SelectAll)
        val expected = state.document?.pageById(state.selectedPageId)?.children?.map { it.id }?.toSet()
        assertEquals(expected, state.selectedNodeIds)
    }

    // --- Move / nudge ---

    @Test
    fun moveNodesTranslatesCoordinatePositionedNodes() {
        var state = freshState()
        val root = state.rootFrameId()
        val before = assertNotNull(state.document?.nodeById(root)?.position)
        val sizeBefore = state.document?.nodeById(root)?.size
        val fillsBefore = state.document?.nodeById(root)?.fills
        val strokesBefore = state.document?.nodeById(root)?.strokes
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(root), dx = 10.0, dy = -5.0))
        val after = assertNotNull(state.document?.nodeById(root)?.position)
        assertEquals(before.x + 10.0, after.x)
        assertEquals(before.y - 5.0, after.y)
        assertEquals(sizeBefore, state.document?.nodeById(root)?.size)
        assertEquals(fillsBefore, state.document?.nodeById(root)?.fills)
        assertEquals(strokesBefore, state.document?.nodeById(root)?.strokes)
    }

    @Test
    fun lockedNodeDoesNotMove() {
        var state = freshState()
        val root = state.rootFrameId()
        val before = state.document?.nodeById(root)?.position
        state = reduceDesignEditor(state, DesignEditorIntent.SetLocked(root, true))
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(root), 50.0, 50.0))
        assertEquals(before, state.document?.nodeById(root)?.position)
    }

    @Test
    fun moveNodesIsANoOpForAnAutoLayoutFlowChild() {
        // design-book §18 "Auto layout boundary": free positioning only applies to
        // free/absolute children — a flow child's `position` must not change via MoveNodes.
        var state = freshState()
        val root = state.rootFrameId()
        val flowChild = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id, "root has a flow child")
        val before = state.document?.nodeById(flowChild)?.position
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(flowChild), dx = 25.0, dy = 25.0))
        assertEquals(before, state.document?.nodeById(flowChild)?.position)
    }

    @Test
    fun setAbsolutePositionDetachesAnAutoLayoutChildFromTheFlow() {
        var state = freshState()
        val root = state.rootFrameId()
        val flowChild = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id, "root has a flow child")
        assertFalse(state.document?.nodeById(flowChild)?.layoutChild?.absolute ?: true)

        state = reduceDesignEditor(state, DesignEditorIntent.SetAbsolutePosition(flowChild, x = 12.0, y = 34.0))

        val node = assertNotNull(state.document?.nodeById(flowChild))
        assertTrue(node.layoutChild.absolute)
        assertEquals(12.0, node.position?.x)
        assertEquals(34.0, node.position?.y)

        // Now that it's absolute, MoveNodes takes effect (isCoordinatePositioned is true).
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(flowChild), dx = 1.0, dy = 1.0))
        assertEquals(13.0, state.document?.nodeById(flowChild)?.position?.x)
        assertEquals(35.0, state.document?.nodeById(flowChild)?.position?.y)
    }

    // --- Visibility / lock ---

    @Test
    fun visibilityToggleWritesAuthoredNode() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.SetVisible(root, false))
        assertEquals(false, state.document?.nodeById(root)?.visible?.literalOrNull())
    }

    // --- Create / delete / duplicate / reorder ---

    @Test
    fun createObjectAddsChildAndSelectsIt() {
        var state = freshState()
        val root = state.rootFrameId()
        val countBefore = state.document?.nodeById(root)?.children?.size ?: 0
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, parentId = root, x = 20.0, y = 20.0, width = 100.0, height = 60.0),
        )
        val root2 = assertNotNull(state.document?.nodeById(root))
        assertEquals(countBefore + 1, root2.children.size)
        val created = root2.children.last()
        assertEquals(state.selectedNodeId, created.id)
        assertTrue(created.kind is DesignNodeKind.Shape && (created.kind as DesignNodeKind.Shape).shape == ShapeType.Rectangle)
    }

    @Test
    fun createScreenAppendsPageAndSelectsRoot() {
        var state = freshState()
        val pagesBefore = state.document?.pages?.size ?: 0
        state = reduceDesignEditor(state, DesignEditorIntent.CreateScreen(ScreenPreset.Mobile, "New Screen"))
        assertEquals(pagesBefore + 1, state.document?.pages?.size)
        val page = assertNotNull(state.document?.pageById(state.selectedPageId))
        assertEquals("New Screen", page.name)
        assertEquals(page.children.first().id, state.selectedNodeId)
        assertEquals(375.0, page.children.first().size.width)
    }

    @Test
    fun deleteRemovesNodesAndClearsSelection() {
        var state = freshState()
        val root = state.rootFrameId()
        val child = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id)
        state = reduceDesignEditor(state, DesignEditorIntent.SelectNode(child))
        state = reduceDesignEditor(state, DesignEditorIntent.DeleteNodes(setOf(child)))
        assertNull(state.document?.nodeById(child))
        assertFalse(child in state.selectedNodeIds)
    }

    @Test
    fun duplicateCreatesFreshIdsAndSelectsCopies() {
        var state = freshState()
        val root = state.rootFrameId()
        val child = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id)
        val countBefore = state.document?.nodeById(root)?.children?.size ?: 0
        state = reduceDesignEditor(state, DesignEditorIntent.DuplicateNodes(setOf(child)))
        assertEquals(countBefore + 1, state.document?.nodeById(root)?.children?.size)
        assertEquals(1, state.selectedNodeIds.size)
        val copyId = state.selectedNodeIds.first()
        assertTrue(copyId != child)
        assertNotNull(state.document?.nodeById(copyId))
    }

    @Test
    fun reorderMovesNodeToFrontOfSiblings() {
        var state = freshState()
        val root = state.rootFrameId()
        val children = assertNotNull(state.document?.nodeById(root)?.children)
        val first = children.first().id
        state = reduceDesignEditor(state, DesignEditorIntent.ReorderNode(first, ZOrderMove.ToFront))
        assertEquals(first, state.document?.nodeById(root)?.children?.last()?.id)
    }

    @Test
    fun reorderSendsNodeToBackAndBackwardFromBackIsANoOp() {
        var state = freshState()
        val root = state.rootFrameId()
        val children = assertNotNull(state.document?.nodeById(root)?.children)
        if (children.size < 2) return
        val last = children.last().id
        state = reduceDesignEditor(state, DesignEditorIntent.ReorderNode(last, ZOrderMove.ToBack))
        assertEquals(last, state.document?.nodeById(root)?.children?.first()?.id, "ToBack moves the node behind all siblings")

        // Stepping backward from the very back is clamped to a no-op (leaves the document identical).
        val orderAtBack = state.document?.nodeById(root)?.children?.map { it.id }
        state = reduceDesignEditor(state, DesignEditorIntent.ReorderNode(last, ZOrderMove.Backward))
        assertEquals(orderAtBack, state.document?.nodeById(root)?.children?.map { it.id })
    }

    @Test
    fun pressStickinessAppliesToNestedContainersButNotTheTopLevelScreenRoot() {
        val document = assertNotNull(freshState().document)
        val root = assertNotNull(document.pages.firstOrNull()?.children?.firstOrNull(), "screen root frame")
        // A nested container: a non-top-level frame that itself has children.
        val nested = assertNotNull(
            root.allDescendants().firstOrNull { it.children.isNotEmpty() },
            "sample root has a nested container",
        )
        val nestedChild = nested.allDescendants().first().id
        val rootChild = assertNotNull(root.children.firstOrNull()?.id)

        // Pressing the selected node itself always grabs it (root or nested container).
        assertTrue(document.pressHitBelongsToSelection(setOf(root.id), root.id))
        assertTrue(document.pressHitBelongsToSelection(setOf(nested.id), nested.id))
        // A press on a child of a deliberately-selected NESTED container drags that container.
        assertTrue(document.pressHitBelongsToSelection(setOf(nested.id), nestedChild))
        // But while only the top-level screen root is selected (the resting default), a press on a
        // child must NOT stick to the root — the child stays directly selectable/draggable.
        assertFalse(document.pressHitBelongsToSelection(setOf(root.id), rootChild))
        // A blank hit (empty canvas) never belongs.
        assertFalse(document.pressHitBelongsToSelection(setOf(root.id), ""))
        // An unrelated top-level sibling is neither the selection nor a descendant of it.
        val topLevel = document.pages.flatMap { it.children }.map { it.id }
        if (topLevel.size >= 2) {
            assertFalse(document.pressHitBelongsToSelection(setOf(topLevel[0]), topLevel[1]))
        }
    }

    @Test
    fun reorderNodeAtExtremeIsInertAndPreservesRedoStack() {
        var state = freshState()
        val root = state.rootFrameId()
        val children = assertNotNull(state.document?.nodeById(root)?.children)
        if (children.size < 2) return
        val first = children.first().id
        val front = children.last().id // already frontmost (last child paints on top)
        // Make an edit, then undo it so a redo entry is available.
        state = reduceDesignEditor(state, DesignEditorIntent.ReorderNode(first, ZOrderMove.ToFront))
        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        val docBeforeNoOp = state.document
        // Bringing the already-frontmost node to front is a no-op: the reducer returns the state
        // unchanged (no undo entry, redo stack untouched — the z-order shortcut can't wipe redo).
        val afterNoOp = reduceDesignEditor(state, DesignEditorIntent.ReorderNode(front, ZOrderMove.ToFront))
        assertEquals(docBeforeNoOp, afterNoOp.document, "an extreme reorder does not mutate the document")
        // Redo still re-applies the earlier reorder, proving the redo stack survived the no-op.
        val redone = reduceDesignEditor(afterNoOp, DesignEditorIntent.Redo)
        assertEquals(first, redone.document?.nodeById(root)?.children?.last()?.id)
    }

    // --- Fills / strokes / effects ---

    @Test
    fun fillAddToggleAndConvertToGradient() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.FillCommand(root, FillOp.Add))
        val fills = assertNotNull(state.document?.nodeById(root)?.fills)
        val lastIndex = fills.lastIndex
        state = reduceDesignEditor(state, DesignEditorIntent.FillCommand(root, FillOp.SetType(lastIndex, FillKind.LinearGradient)))
        val paint = state.document?.nodeById(root)?.fills?.get(lastIndex)
        assertTrue(paint is DesignPaint.Gradient)

        state = reduceDesignEditor(state, DesignEditorIntent.FillCommand(root, FillOp.ToggleAt(lastIndex)))
        assertEquals(false, state.document?.nodeById(root)?.fills?.get(lastIndex)?.visible?.literalOrNull())
    }

    @Test
    fun strokeAddSetWeightAndRemove() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.StrokeCommand(root, StrokeOp.Add))
        assertNotNull(state.document?.nodeById(root)?.strokes)
        state = reduceDesignEditor(state, DesignEditorIntent.StrokeCommand(root, StrokeOp.SetWeight(4.0)))
        assertEquals(4.0, state.document?.nodeById(root)?.strokes?.weight?.literalOrNull())
        state = reduceDesignEditor(state, DesignEditorIntent.StrokeCommand(root, StrokeOp.Remove))
        assertNull(state.document?.nodeById(root)?.strokes)
    }

    @Test
    fun fillAndStrokeCommandsStayInTheirOwnPaintStacks() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.FillCommand(root, FillOp.Add))
        state = reduceDesignEditor(state, DesignEditorIntent.StrokeCommand(root, StrokeOp.Add))

        val strokeBeforeFillEdit = state.document?.nodeById(root)?.strokes
        state = reduceDesignEditor(state, DesignEditorIntent.FillCommand(root, FillOp.SetColor(0, DesignColor.fromHex("#112233") ?: DesignColor.Black)))
        assertEquals(strokeBeforeFillEdit, state.document?.nodeById(root)?.strokes)

        val fillsBeforeStrokeEdit = state.document?.nodeById(root)?.fills
        state = reduceDesignEditor(state, DesignEditorIntent.StrokeCommand(root, StrokeOp.SetColor(DesignColor.fromHex("#445566") ?: DesignColor.Black)))
        assertEquals(fillsBeforeStrokeEdit, state.document?.nodeById(root)?.fills)
    }

    @Test
    fun effectAddAndToggle() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.EffectCommand(root, EffectOp.Add(EffectType.DropShadow)))
        assertEquals(1, state.document?.nodeById(root)?.effects?.size)
        state = reduceDesignEditor(state, DesignEditorIntent.EffectCommand(root, EffectOp.ToggleAt(0)))
        assertEquals(false, state.document?.nodeById(root)?.effects?.first()?.visible?.literalOrNull())
    }

    // --- Typography ---

    @Test
    fun typographyUpdatesTextStyle() {
        var state = freshState()
        // Find any text node in the document.
        val textId = assertNotNull(
            state.document?.pages?.flatMap { it.allNodes() }?.firstOrNull { it.kind is DesignNodeKind.Text }?.id,
            "sample has a text node",
        )
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateTypography(textId, TypographyPatch(fontSize = 24.0, fontWeight = 700.0)))
        val kind = state.document?.nodeById(textId)?.kind as? DesignNodeKind.Text
        assertEquals(24.0, kind?.textStyle?.fontSize?.literalOrNull())
        assertEquals(700.0, kind?.textStyle?.fontWeight?.literalOrNull())
    }

    // --- Undo / redo ---

    @Test
    fun undoRedoRoundTripsDocument() {
        var state = freshState()
        val root = state.rootFrameId()
        val before = state.document?.nodeById(root)?.opacity?.literalOrNull()
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateOpacity(root, 0.3))
        assertEquals(0.3, state.document?.nodeById(root)?.opacity?.literalOrNull())
        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        assertEquals(before, state.document?.nodeById(root)?.opacity?.literalOrNull())
        state = reduceDesignEditor(state, DesignEditorIntent.Redo)
        assertEquals(0.3, state.document?.nodeById(root)?.opacity?.literalOrNull())
    }

    @Test
    fun interactionCoalescesManyMovesIntoOneUndoEntry() {
        var state = freshState()
        val root = state.rootFrameId()
        val before = state.document?.nodeById(root)?.position
        // Simulate a drag: begin, many move increments, end.
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        repeat(20) { state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(root), 2.0, 1.0)) }
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)
        assertEquals((before?.x ?: 0.0) + 40.0, state.document?.nodeById(root)?.position?.x)
        // One undo reverts the whole drag.
        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        assertEquals(before, state.document?.nodeById(root)?.position)
    }

    @Test
    fun reparentMovesNodeUnderNewParent() {
        var state = freshState()
        val root = state.rootFrameId()
        val children = assertNotNull(state.document?.nodeById(root)?.children)
        // Find a container child to receive a sibling.
        val container = children.firstOrNull { it.children.isNotEmpty() } ?: children.first()
        val mover = children.firstOrNull { it.id != container.id } ?: return
        state = reduceDesignEditor(state, DesignEditorIntent.ReparentNode(mover.id, container.id))
        assertTrue(state.document?.nodeById(container.id)?.children?.any { it.id == mover.id } == true)
        assertFalse(state.document?.nodeById(root)?.children?.any { it.id == mover.id } == true)
    }

    @Test
    fun deleteParentPrunesSelectedChildFromSelection() {
        var state = freshState()
        val root = state.rootFrameId()
        val parent = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull { it.children.isNotEmpty() })
        val child = parent.children.first()
        state = reduceDesignEditor(state, DesignEditorIntent.SelectNodes(setOf(parent.id, child.id)))
        state = reduceDesignEditor(state, DesignEditorIntent.DeleteNodes(setOf(parent.id)))
        assertNull(state.document?.nodeById(child.id), "child removed with its parent")
        assertFalse(child.id in state.selectedNodeIds, "phantom child pruned from selection")
        assertFalse(parent.id in state.selectedNodeIds)
    }

    @Test
    fun resizeWriteBackForksRedoHistory() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateOpacity(root, 0.5))
        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        assertTrue(state.redoStack.isNotEmpty(), "undo populated redo")
        // A resize write-back must fork history so a later Redo can't restore a stale doc.
        state = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("tile_1", width = 300.0))
        assertTrue(state.redoStack.isEmpty(), "resize cleared the redo stack")
    }

    // --- Lock guard ---

    @Test
    fun lockedNodeIsNotResizedMovedOrRestyled() {
        var state = freshState()
        val root = state.rootFrameId()
        val child = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id)
        state = reduceDesignEditor(state, DesignEditorIntent.SetLocked(child, true))
        val sizeBefore = state.document?.nodeById(child)?.size
        val opacityBefore = state.document?.nodeById(child)?.opacity?.literalOrNull()
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateSize(child, width = 999.0, height = 999.0))
        state = reduceDesignEditor(state, DesignEditorIntent.UpdatePosition(child, x = 12.0, y = 34.0))
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateOpacity(child, 0.1))
        state = reduceDesignEditor(state, DesignEditorIntent.FillCommand(child, FillOp.Add))
        assertEquals(sizeBefore, state.document?.nodeById(child)?.size, "locked size unchanged")
        assertEquals(opacityBefore, state.document?.nodeById(child)?.opacity?.literalOrNull(), "locked opacity unchanged")
    }

    @Test
    fun lockedNodeCanStillBeUnlockedAndRevealed() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.SetLocked(root, true))
        // Visibility + unlock must still apply on a locked node.
        state = reduceDesignEditor(state, DesignEditorIntent.SetVisible(root, false))
        assertEquals(false, state.document?.nodeById(root)?.visible?.literalOrNull())
        state = reduceDesignEditor(state, DesignEditorIntent.SetLocked(root, false))
        assertFalse(state.document?.nodeById(root)?.locked ?: true)
    }

    @Test
    fun lockedTextCannotEnterEditMode() {
        var state = freshState()
        val textId = assertNotNull(
            state.document?.pages?.flatMap { it.allNodes() }?.firstOrNull { it.kind is DesignNodeKind.Text }?.id,
        )
        state = reduceDesignEditor(state, DesignEditorIntent.SetLocked(textId, true))
        state = reduceDesignEditor(state, DesignEditorIntent.SetEditingText(textId))
        assertEquals("", state.editingTextNodeId, "locked text stays out of edit mode")
    }

    // --- Interaction lifecycle ---

    @Test
    fun cancelInteractionRevertsDragAndLeavesNoUndoEntry() {
        var state = freshState()
        val root = state.rootFrameId()
        val undoBefore = state.undoStack.size
        val posBefore = state.document?.nodeById(root)?.position
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        repeat(5) { state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(root), 10.0, 10.0)) }
        state = reduceDesignEditor(state, DesignEditorIntent.CancelInteraction)
        assertEquals(posBefore, state.document?.nodeById(root)?.position, "drag reverted")
        assertEquals(undoBefore, state.undoStack.size, "canceled drag left no undo entry")
        assertFalse(state.interacting)
    }

    @Test
    fun inertDragLeavesNoUndoEntry() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(state, DesignEditorIntent.SetLocked(root, true))
        val undoBefore = state.undoStack.size
        // A drag that touches only a locked node changes nothing.
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        repeat(5) { state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(root), 10.0, 10.0)) }
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)
        assertEquals(undoBefore, state.undoStack.size, "inert drag left no dead undo entry")
    }

    @Test
    fun reparentIntoOwnDescendantIsRejected() {
        var state = freshState()
        val root = state.rootFrameId()
        val child = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull { it.children.isNotEmpty() })
        val grandchild = child.children.first()
        val before = state.document
        state = reduceDesignEditor(state, DesignEditorIntent.ReparentNode(child.id, grandchild.id))
        assertEquals(before, state.document, "cyclic reparent is a no-op")
    }

    // --- Detach instance / auto-layout-aware create ---

    @Test
    fun detachInstanceConvertsToEditableFrameThatAcceptsLayoutEdits() {
        var state = freshState()
        val doc = assertNotNull(state.document)
        // A card instance (component with children), so the detached frame has editable children.
        val instanceId = assertNotNull(
            doc.pages.flatMap { it.allNodes() }.firstOrNull { node ->
                val kind = node.kind
                kind is DesignNodeKind.Instance &&
                    (kind.componentId.literalOrNull()?.let { doc.components[it]?.root?.children?.isNotEmpty() } == true)
            }?.id,
            "sample has an instance of a component with children",
        )

        state = reduceDesignEditor(state, DesignEditorIntent.DetachInstance(instanceId))
        val detached = assertNotNull(state.document?.nodeById(instanceId))
        assertTrue(detached.kind is DesignNodeKind.Frame, "instance detached into a frame")
        assertTrue(detached.children.isNotEmpty(), "component subtree baked in as editable children")

        // The whole point of the fix: an Auto layout edit now takes effect (it was ignored on the instance).
        state = reduceDesignEditor(state, DesignEditorIntent.SetLayoutMode(instanceId, EditorLayoutMode.Horizontal))
        assertEquals(LayoutMode.Horizontal, state.document?.nodeById(instanceId)?.layout?.mode)
    }

    @Test
    fun detachIgnoresNonInstanceNodes() {
        var state = freshState()
        val root = state.rootFrameId()
        val before = state.document
        state = reduceDesignEditor(state, DesignEditorIntent.DetachInstance(root))
        assertEquals(before, state.document, "detaching a non-instance frame is a no-op")
    }

    @Test
    fun createObjectFlowsInAutoLayoutParentButFloatsInFreeParent() {
        var state = freshState()
        val doc = assertNotNull(state.document)
        val flowParent = assertNotNull(
            doc.pages.flatMap { it.allNodes() }
                .firstOrNull { it.kind is DesignNodeKind.Frame && it.layout.mode != LayoutMode.None }?.id,
            "sample has an auto-layout frame",
        )
        val before = doc.nodeById(flowParent)!!.children.map { it.id }.toSet()
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Frame, flowParent, x = 10.0, y = 10.0, width = 100.0, height = 80.0),
        )
        val flowed = assertNotNull(
            state.document?.nodeById(flowParent)?.children?.firstOrNull { it.id !in before },
            "a child was created in the auto-layout parent",
        )
        assertFalse(flowed.layoutChild.absolute, "created child flows in an auto-layout parent")

        // The new frame's own layout is Free (None) → an object created inside it stays absolute.
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, flowed.id, x = 5.0, y = 5.0, width = 20.0, height = 20.0),
        )
        val inFree = assertNotNull(state.document?.nodeById(flowed.id)?.children?.firstOrNull())
        assertTrue(inFree.layoutChild.absolute, "created child floats in a free parent")
    }
}
