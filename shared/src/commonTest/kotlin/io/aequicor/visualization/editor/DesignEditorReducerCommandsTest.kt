package io.aequicor.visualization.editor

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
import io.aequicor.visualization.editor.presentation.isCoordinatePositioned
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.pressHitBelongsToSelection
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.orZero
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.subsystems.figures.ShapeType
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
        createDesignEditorState(missionDemoDocuments())

    /** The id of any auto-layout (row/column/grid) frame's first flow child in the demo bundle. */
    private fun DesignEditorState.autoLayoutFlowChildId(): String {
        val doc = assertNotNull(document)
        val flowParent = assertNotNull(
            doc.pages.flatMap { it.allNodes() }
                .firstOrNull { it.kind is DesignNodeKind.Frame && it.layout.mode != LayoutMode.None && it.children.isNotEmpty() },
            "demo bundle has an auto-layout frame with children",
        )
        return flowParent.children.first().id
    }

    private fun DesignEditorState.rootFrameId(): String =
        assertNotNull(document?.pageById(selectedPageId)?.children?.firstOrNull()?.id, "no root frame")

    /**
     * A structural / typography command that faithfully round-trips into SLM rewrites exactly one
     * owning `*.layout.md` (every other source stays byte-identical), records a source-undo entry,
     * and surfaces no error diagnostics. This proves the command is wired to real write-back, not
     * an in-memory-only edit; the dedicated write-back suites pin the exact serialized bytes.
     */
    private fun DesignEditorState.assertWroteBackToOneSource(before: DesignEditorState) {
        val changed = sources.filter { after ->
            before.sources.firstOrNull { it.fileName == after.fileName }?.content != after.content
        }
        assertEquals(1, changed.size, "exactly one owning source rewritten (changed: ${changed.map { it.fileName }})")
        assertTrue(
            diagnostics.none { it.severity == DesignSeverity.Error },
            "write-back errors: ${diagnostics.filter { it.severity == DesignSeverity.Error }}",
        )
        assertEquals(listOf(before.sources), previousSources, "source undo captured the pre-edit sources")
    }

    // --- Selection ---

    @Test
    fun ancestorContainerJoinsDescendantMultiSelection() {
        var state = freshState()
        val root = state.rootFrameId()
        val document = assertNotNull(state.document)
        val ancestor = assertNotNull(
            document.nodeById(root)?.allDescendants()?.firstOrNull { it.children.isNotEmpty() },
            "screen contains a nested container",
        )
        val child = ancestor.children.first().id

        state = reduceDesignEditor(state, DesignEditorIntent.SelectNode(ancestor.id))
        state = reduceDesignEditor(state, DesignEditorIntent.ToggleNodeSelection(child))

        assertEquals(setOf(ancestor.id, child), state.selectedNodeIds)
        assertEquals(ancestor.id, state.selectedNodeId)
        assertTrue(state.hasMultiSelection)
    }

    @Test
    fun selectedAncestorAndChildMoveAsOneHierarchy() {
        var state = freshState()
        val document = assertNotNull(state.document)
        val ancestor = assertNotNull(
            document.pages.flatMap { it.allNodes() }.firstOrNull { candidate ->
                candidate.children.any { child ->
                    document.isCoordinatePositioned(candidate.id) && document.isCoordinatePositioned(child.id)
                }
            },
            "screen contains a positioned container with a positioned child",
        )
        val child = assertNotNull(ancestor.children.firstOrNull { document.isCoordinatePositioned(it.id) })
        val ancestorBefore = assertNotNull(ancestor.position)
        val childBefore = child.position

        state = reduceDesignEditor(state, DesignEditorIntent.SelectNodes(setOf(ancestor.id, child.id)))
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(state.selectedNodeIds, dx = 12.0, dy = -7.0))

        assertEquals(ancestorBefore.x.orZero + 12.0, state.document?.nodeById(ancestor.id)?.position?.x?.orZero)
        assertEquals(ancestorBefore.y.orZero - 7.0, state.document?.nodeById(ancestor.id)?.position?.y?.orZero)
        assertEquals(childBefore, state.document?.nodeById(child.id)?.position, "child keeps its local position")
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
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(root), dx = 10.0, dy = -5.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)
        val after = assertNotNull(state.document?.nodeById(root)?.position)
        assertEquals(before.x.orZero + 10.0, after.x.orZero)
        assertEquals(before.y.orZero - 5.0, after.y.orZero)
        assertEquals(sizeBefore, state.document?.nodeById(root)?.size)
        assertEquals(fillsBefore, state.document?.nodeById(root)?.fills)
        assertEquals(strokesBefore, state.document?.nodeById(root)?.strokes)
    }

    @Test
    fun multiSelectionMovesAndDeletesAsAGroup() {
        var state = freshState()
        val root = state.rootFrameId()
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, root, x = 20.0, y = 30.0, width = 80.0, height = 50.0),
        )
        val first = state.selectedNodeId
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, root, x = 140.0, y = 160.0, width = 90.0, height = 60.0),
        )
        val second = state.selectedNodeId
        val ids = setOf(first, second)

        state = reduceDesignEditor(state, DesignEditorIntent.SelectNodes(ids))
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(state.selectedNodeIds, dx = 12.0, dy = -7.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        assertEquals(32.0, state.document?.nodeById(first)?.position?.x?.orZero)
        assertEquals(23.0, state.document?.nodeById(first)?.position?.y?.orZero)
        assertEquals(152.0, state.document?.nodeById(second)?.position?.x?.orZero)
        assertEquals(153.0, state.document?.nodeById(second)?.position?.y?.orZero)

        state = reduceDesignEditor(state, DesignEditorIntent.DeleteNodes(state.selectedNodeIds))
        assertNull(state.document?.nodeById(first))
        assertNull(state.document?.nodeById(second))
        assertTrue(state.selectedNodeIds.isEmpty())
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
        val flowChild = state.autoLayoutFlowChildId()
        val before = state.document?.nodeById(flowChild)?.position
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(flowChild), dx = 25.0, dy = 25.0))
        assertEquals(before, state.document?.nodeById(flowChild)?.position)
    }

    @Test
    fun setAbsolutePositionDetachesAnAutoLayoutChildFromTheFlow() {
        var state = freshState()
        val flowChild = state.autoLayoutFlowChildId()
        assertFalse(state.document?.nodeById(flowChild)?.layoutChild?.absolute ?: true)

        state = reduceDesignEditor(state, DesignEditorIntent.SetAbsolutePosition(flowChild, x = 12.0, y = 34.0))

        val node = assertNotNull(state.document?.nodeById(flowChild))
        assertTrue(node.layoutChild.absolute)
        assertEquals(12.0, node.position?.x?.orZero)
        assertEquals(34.0, node.position?.y?.orZero)

        // Now that it's absolute, MoveNodes takes effect (isCoordinatePositioned is true).
        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.MoveNodes(setOf(flowChild), dx = 1.0, dy = 1.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)
        assertEquals(13.0, state.document?.nodeById(flowChild)?.position?.x?.orZero)
        assertEquals(35.0, state.document?.nodeById(flowChild)?.position?.y?.orZero)
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
        val before = freshState()
        val root = before.rootFrameId()
        val countBefore = before.document?.nodeById(root)?.children?.size ?: 0
        val state = reduceDesignEditor(
            before,
            DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, parentId = root, x = 20.0, y = 20.0, width = 100.0, height = 60.0),
        )
        val root2 = assertNotNull(state.document?.nodeById(root))
        assertEquals(countBefore + 1, root2.children.size)
        val created = root2.children.last()
        assertEquals(state.selectedNodeId, created.id)
        assertTrue(created.kind is DesignNodeKind.Shape && (created.kind as DesignNodeKind.Shape).shape == ShapeType.Rectangle)

        // A rectangle under a heading-anchored frame is faithfully expressible → it writes a fresh
        // section carrying the minted id into the owning source (others byte-identical).
        state.assertWroteBackToOneSource(before)
        assertTrue(state.sources.any { created.id in it.content }, "minted id written to a source")
    }

    @Test
    fun createScreenAppendsPageAndSelectsRoot() {
        var state = freshState()
        val pagesBefore = state.document?.pages?.size ?: 0
        val before = state
        state = reduceDesignEditor(state, DesignEditorIntent.CreateScreen(ScreenPreset.Mobile, "New Screen"))
        assertEquals(pagesBefore + 1, state.document?.pages?.size)
        val page = assertNotNull(state.document?.pageById(state.selectedPageId))
        assertEquals("New Screen", page.name)
        assertEquals(page.children.first().id, state.selectedNodeId)
        assertEquals(375.0, page.children.first().size.width)

        // The created screen also grows the source list with its own `*.layout.md`, leaving every
        // pre-existing source byte-identical (a new screen has no owning source to patch).
        assertEquals(before.sources.size + 1, state.sources.size, "a new source was appended")
        assertEquals("${page.id}.layout.md", state.sources.last().fileName)
        before.sources.forEach { source ->
            val kept = assertNotNull(state.sources.firstOrNull { it.fileName == source.fileName })
            assertEquals(source.content, kept.content, "${source.fileName} stays byte-identical")
        }
    }

    @Test
    fun deleteRemovesNodesAndClearsSelection() {
        var state = freshState()
        val root = state.rootFrameId()
        val child = assertNotNull(state.document?.nodeById(root)?.children?.firstOrNull()?.id)
        state = reduceDesignEditor(state, DesignEditorIntent.SelectNode(child))
        val before = state
        state = reduceDesignEditor(state, DesignEditorIntent.DeleteNodes(setOf(child)))
        assertNull(state.document?.nodeById(child))
        assertFalse(child in state.selectedNodeIds)

        // The first child is a heading-anchored shape → the delete drops its section from the owning
        // source and the recompiled id set matches, so the write-back is accepted (not vetoed).
        state.assertWroteBackToOneSource(before)
        assertTrue(state.sources.none { child in it.content }, "deleted node's section removed from source")
    }

    @Test
    fun duplicateCreatesFreshIdsAndSelectsCopies() {
        val before = freshState()
        val root = before.rootFrameId()
        val child = assertNotNull(before.document?.nodeById(root)?.children?.firstOrNull()?.id)
        val countBefore = before.document?.nodeById(root)?.children?.size ?: 0
        val state = reduceDesignEditor(before, DesignEditorIntent.DuplicateNodes(setOf(child)))
        assertEquals(countBefore + 1, state.document?.nodeById(root)?.children?.size)
        assertEquals(1, state.selectedNodeIds.size)
        val copyId = state.selectedNodeIds.first()
        assertTrue(copyId != child)
        assertNotNull(state.document?.nodeById(copyId))

        // The first child is a pure shape → its clone is emitted as a fresh section with the minted
        // id in the owning source, while the original id is preserved (others byte-identical).
        state.assertWroteBackToOneSource(before)
        assertTrue(state.sources.any { copyId in it.content }, "clone id written to a source")
    }

    @Test
    fun reorderMovesNodeToFrontOfSiblings() {
        val before = freshState()
        val root = before.rootFrameId()
        val children = assertNotNull(before.document?.nodeById(root)?.children)
        val first = children.first().id
        val state = reduceDesignEditor(before, DesignEditorIntent.ReorderNode(first, ZOrderMove.ToFront))
        assertEquals(first, state.document?.nodeById(root)?.children?.last()?.id)

        // The whole top-level run is heading-anchored → the reorder persists as a heading-section
        // relocation in the owning source (others byte-identical): the moved node's section now
        // trails its former sibling (z-order = document order in CNL).
        state.assertWroteBackToOneSource(before)
        val second = assertNotNull(children.getOrNull(1)?.id, "root has a second child")
        val owning = state.sources.first { "id $first" in it.content }.content
        assertTrue(
            owning.indexOf("id $first") > owning.indexOf("id $second"),
            "moved node's section relocated behind its former sibling",
        )
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
        val beforeToggle = state
        state = reduceDesignEditor(state, DesignEditorIntent.EffectCommand(root, EffectOp.ToggleAt(0)))
        assertEquals(beforeToggle.document, state.document)
        assertEquals(beforeToggle.sources, state.sources)
        assertTrue(state.diagnostics.any { it.severity == DesignSeverity.Error && "does not support SLM write-back" in it.message })
    }

    // --- Typography ---

    @Test
    fun typographyUpdatesTextStyle() {
        val before = freshState()
        // Find any text node in the document (the sample's first is heading-anchored → expressible).
        val textId = assertNotNull(
            before.document?.pages?.flatMap { it.allNodes() }?.firstOrNull { it.kind is DesignNodeKind.Text }?.id,
            "sample has a text node",
        )
        // The demo's first text node is authored at size 24 bold, so pick distinct values to force
        // a real rewrite (a no-op patch would write back nothing).
        val state = reduceDesignEditor(before, DesignEditorIntent.UpdateTypography(textId, TypographyPatch(fontSize = 28.0, fontWeight = 600.0)))
        val kind = state.document?.nodeById(textId)?.kind as? DesignNodeKind.Text
        assertEquals(28.0, kind?.textStyle?.fontSize?.literalOrNull())
        assertEquals(600.0, kind?.textStyle?.fontWeight?.literalOrNull())

        // The merged text style is serialized into the node's CNL sentence in the owning source
        // (others byte-identical).
        state.assertWroteBackToOneSource(before)
        assertTrue(state.sources.any { "size 28" in it.content }, "font size written to a CNL source")
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
        assertEquals((before?.x?.orZero ?: 0.0) + 40.0, state.document?.nodeById(root)?.position?.x?.orZero)
        // One undo reverts the whole drag.
        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        assertEquals(before, state.document?.nodeById(root)?.position)
    }

    @Test
    fun reparentMovesNodeUnderNewParent() {
        val before = freshState()
        val root = before.rootFrameId()
        val children = assertNotNull(before.document?.nodeById(root)?.children)
        // Find a container child to receive a sibling.
        val container = children.firstOrNull { it.children.isNotEmpty() } ?: children.first()
        val mover = children.firstOrNull { it.id != container.id } ?: return
        val state = reduceDesignEditor(before, DesignEditorIntent.ReparentNode(mover.id, container.id))
        assertTrue(state.document?.nodeById(container.id)?.children?.any { it.id == mover.id } == true)
        assertFalse(state.document?.nodeById(root)?.children?.any { it.id == mover.id } == true)

        // Both ends are same-page, heading-anchored, faithfully-expressible → the mover's section is
        // re-leveled and relocated under the container in the owning source (others byte-identical),
        // and the id + parent-of veto both pass so the patch is accepted.
        state.assertWroteBackToOneSource(before)
    }

    @Test
    fun positionedReparentPreservesGeometryAndIsOneUndoStep() {
        var state = freshState()
        val root = state.rootFrameId()
        val oldParent = assertNotNull(
            state.document?.nodeById(root)?.children?.firstOrNull { parent ->
                parent.children.any { child -> !child.locked }
            },
            "root has a nested movable node",
        )
        val moving = assertNotNull(oldParent.children.firstOrNull { !it.locked })
        val originalPosition = moving.position
        val originalRotation = moving.rotation
        val desiredPosition = DesignPoint(321.0, 123.0)

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.ReparentNode(
                nodeId = moving.id,
                newParentId = root,
                position = desiredPosition,
                rotation = 37.0,
            ),
        )
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        val reparented = assertNotNull(state.document?.nodeById(moving.id))
        assertEquals(root, state.document?.parentNodeOf(moving.id)?.id)
        assertEquals(desiredPosition, reparented.position)
        assertEquals(37.0, reparented.rotation)
        assertTrue(reparented.layoutChild.absolute, "canvas reparent stays out of Auto layout flow")

        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        val restored = assertNotNull(state.document?.nodeById(moving.id))
        assertEquals(oldParent.id, state.document?.parentNodeOf(moving.id)?.id)
        assertEquals(originalPosition, restored.position)
        assertEquals(originalRotation, restored.rotation)
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
        state = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("win_bg", width = 300.0))
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
    fun cornerRadiusDragPreviewsInMemoryAndCommitsAsOneUndoEntry() {
        var state = freshState()
        val root = state.rootFrameId()
        val documentBefore = state.document
        val sourcesBefore = state.sources
        val undoBefore = state.undoStack.size

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        repeat(4) { step ->
            val radius = 8.0 + step
            state = reduceDesignEditor(
                state,
                DesignEditorIntent.PreviewCornerRadiusPerCorner(root, radius, radius, radius, radius),
            )
        }
        assertEquals(sourcesBefore, state.sources, "live preview must not recompile the source per frame")
        state = reduceDesignEditor(state, DesignEditorIntent.UpdateCornerRadiusPerCorner(root, 11.0, 11.0, 11.0, 11.0))
        state = reduceDesignEditor(state, DesignEditorIntent.EndInteraction)

        assertEquals(undoBefore + 1, state.undoStack.size, "whole radius drag has one checkpoint")
        assertEquals(documentBefore, state.undoStack.lastOrNull())
        assertTrue(state.sources != sourcesBefore, "pointer-up persists the radius")
        state = reduceDesignEditor(state, DesignEditorIntent.Undo)
        assertEquals(documentBefore, state.document)
    }

    @Test
    fun cancelCornerRadiusDragRestoresDocumentWithoutTouchingSource() {
        var state = freshState()
        val root = state.rootFrameId()
        val documentBefore = state.document
        val sourcesBefore = state.sources
        val undoBefore = state.undoStack.size

        state = reduceDesignEditor(state, DesignEditorIntent.BeginInteraction)
        state = reduceDesignEditor(state, DesignEditorIntent.PreviewCornerRadiusPerCorner(root, 2.0, 4.0, 6.0, 8.0))
        state = reduceDesignEditor(state, DesignEditorIntent.CancelInteraction)

        assertEquals(documentBefore, state.document)
        assertEquals(sourcesBefore, state.sources)
        assertEquals(undoBefore, state.undoStack.size)
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
