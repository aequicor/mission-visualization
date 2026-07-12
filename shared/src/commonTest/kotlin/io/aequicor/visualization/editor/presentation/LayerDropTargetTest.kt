package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pins the visual→document index mapping of the layers-tree insertion line. The tree is
 * front-first (top row = last child in document order), so a gap's visual position must
 * reverse into a document index — dropping at the visual bottom of a group means index 0.
 */
class LayerDropTargetTest {

    private fun frame(id: String, vararg children: DesignNode): DesignNode =
        DesignNode(id = id, type = "frame", kind = DesignNodeKind.Frame, name = id, children = children.toList())

    private fun shape(id: String): DesignNode =
        DesignNode(id = id, type = "shape", kind = DesignNodeKind.Shape(ShapeType.Rectangle), name = id)

    /**
     * root
     * ├── a (shape)      doc index 0 (back)  -> visual row 3 (bottom)
     * ├── b (frame)      doc index 1         -> visual row 2, children:
     * │   └── b1 (shape)                     -> visual row 3... (expanded below b)
     * └── c (shape)      doc index 2 (front) -> visual row 1 (top)
     */
    private val document = DesignDocument(
        pages = listOf(
            DesignPage(
                id = "page",
                name = "Page",
                children = listOf(frame("root", shape("a"), frame("b", shape("b1")), shape("c"))),
            ),
        ),
    )

    /** The flattened front-first rows the UI displays (all groups expanded). */
    private val rows = listOf(
        LayerTreeRow("root", 0, isContainer = true), // row 0
        LayerTreeRow("c", 1, isContainer = false), //   row 1
        LayerTreeRow("b", 1, isContainer = true), //    row 2
        LayerTreeRow("b1", 2, isContainer = false), //  row 3
        LayerTreeRow("a", 1, isContainer = false), //   row 4
    )

    @Test
    fun gapAboveTopRowIsRejected() {
        // Directly above the root row there is no valid slot (nothing renders outside the root).
        assertNull(resolveLayerDropTarget(document, rows, dragId = "a", pointerRowPosition = 0.1))
    }

    @Test
    fun middleBandOfContainerNestsIntoIt() {
        val target = resolveLayerDropTarget(document, rows, dragId = "a", pointerRowPosition = 2.5)
        val into = assertIs<LayerDropTarget.IntoContainer>(target)
        assertEquals("b", into.nodeId)
        assertEquals(2, into.rowIndex)
    }

    @Test
    fun topBandInsertsBeforeRowVisually_frontOfItInPaintOrder() {
        // Gap above row 1 ("c", doc index 2): the node goes visually above c = in front
        // of it in paint order = doc index 3 -> clamped append at the end of root's list.
        val target = resolveLayerDropTarget(document, rows, dragId = "a", pointerRowPosition = 1.1)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("root", gap.parentId)
        assertEquals(1, gap.gapIndex)
        // "a" (doc index 0) precedes the raw slot 3, so the same-parent move adjusts to 2.
        assertEquals(2, gap.index)
    }

    @Test
    fun bottomBandInsertsAfterRowVisually() {
        // Gap below row 1 ("c") = above row 2 ("b", doc index 1): visually between c and b
        // -> in front of b in paint order -> raw doc index 2, adjusted to 1 for "a"'s removal.
        val target = resolveLayerDropTarget(document, rows, dragId = "a", pointerRowPosition = 1.9)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("root", gap.parentId)
        assertEquals(2, gap.gapIndex)
        assertEquals(1, gap.index)
    }

    @Test
    fun terminalGapDropsAtTheEndOfTheList() {
        // Below the last row: the visual end of the root's list = back of paint order = index 0.
        val target = resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 5.0)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("root", gap.parentId)
        assertEquals(rows.size, gap.gapIndex)
        assertEquals(0, gap.index)
    }

    @Test
    fun gapUnderExpandedContainerHeaderInsertsAsItsFirstVisualChild() {
        // Gap between "b" (row 2) and its child "b1" (row 3): parent = b, front of b's
        // paint order (visual top of its group) = doc index 1 (b has one child).
        val target = resolveLayerDropTarget(document, rows, dragId = "a", pointerRowPosition = 2.9)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("b", gap.parentId)
        assertEquals(3, gap.gapIndex)
        assertEquals(1, gap.index)
        assertEquals(2, gap.depth)
    }

    @Test
    fun gapBelowLastChildOfNestedGroupTargetsTheShallowerParent() {
        // Gap between "b1" (row 3, depth 2) and "a" (row 4, depth 1): with no pointer indent the
        // row below wins, so the slot is in root, visually above "a" (doc index 0) = index 1.
        val target = resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 3.9)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("root", gap.parentId)
        assertEquals(4, gap.gapIndex)
        assertEquals(1, gap.index)
        assertEquals(1, gap.depth)
    }

    @Test
    fun deepPointerAtTheTrailingGapStaysInsideTheGroup() {
        // Same b1/a boundary, but the pointer is indented to depth 2 (over the group's content):
        // the node joins "b" at the back of its list (doc index 0) instead of escaping to root.
        // This is the reported "can't drop under the last child of a group" case.
        val target = resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 3.9, pointerDepth = 2)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("b", gap.parentId)
        assertEquals(0, gap.index)
        assertEquals(2, gap.depth)
    }

    @Test
    fun shallowPointerAtTheTrailingGapPopsOutToTheOuterContainer() {
        // Indent depth 1 selects the outer container explicitly — same as the legacy default.
        val target = resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 3.9, pointerDepth = 1)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("root", gap.parentId)
        assertEquals(1, gap.index)
        assertEquals(1, gap.depth)
    }

    @Test
    fun pointerDepthIsClampedToTheAvailableBand() {
        // Past the deepest rung clamps to the inner group; below the shallowest clamps to root.
        val deep = assertIs<LayerDropTarget.InsertGap>(
            resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 3.9, pointerDepth = 9),
        )
        assertEquals("b", deep.parentId)
        val shallow = assertIs<LayerDropTarget.InsertGap>(
            resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 3.9, pointerDepth = 0),
        )
        assertEquals("root", shallow.parentId)
    }

    @Test
    fun deepPointerStillRejectsDroppingIntoOwnSubtree() {
        // Dragging "b" into its own back (deep pointer at the b1/a boundary) resolves to parent b
        // and is rejected, exactly like the shallow path.
        assertNull(resolveLayerDropTarget(document, rows, dragId = "b", pointerRowPosition = 3.9, pointerDepth = 2))
    }

    @Test
    fun middleBandOfLeafFallsBackToNearestGap() {
        // Row 1 ("c") is a leaf; frac 0.4 in the middle band -> nearest gap above (gapIndex 1).
        val target = resolveLayerDropTarget(document, rows, dragId = "b1", pointerRowPosition = 1.4)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals(1, gap.gapIndex)
        assertEquals("root", gap.parentId)
        // b1 comes from another parent: no same-parent adjustment; slot in front of c = 3.
        assertEquals(3, gap.index)
    }

    @Test
    fun droppingIntoOwnSubtreeIsRejected() {
        // Dragging "b" onto the gap inside itself (between b and b1) resolves to parent b -> rejected.
        assertNull(resolveLayerDropTarget(document, rows, dragId = "b", pointerRowPosition = 2.9))
        // Middle band of itself is also rejected.
        assertNull(resolveLayerDropTarget(document, rows, dragId = "b", pointerRowPosition = 2.5))
    }

    @Test
    fun reorderWithinOwnParentIsAllowed() {
        // Dragging "b1" to the gap right under its parent header ("b") must resolve — the
        // subtree-rejection only guards the inverse direction (parent inside the dragged node).
        val target = resolveLayerDropTarget(document, rows, dragId = "b1", pointerRowPosition = 2.9)
        val gap = assertIs<LayerDropTarget.InsertGap>(target)
        assertEquals("b", gap.parentId)
        assertEquals(0, gap.index)
    }

    @Test
    fun sameParentMoveDownAdjustsForOwnRemoval() {
        // Move "c" (doc index 2) to the gap between b and a (gapIndex 4): raw slot = in front
        // of a = index 1... c's current index 2 is NOT below the slot, so no adjustment applies.
        val target = resolveLayerDropTarget(document, rows, dragId = "c", pointerRowPosition = 3.95)
        val gap = assertNotNull(assertIs<LayerDropTarget.InsertGap>(target))
        assertEquals(1, gap.index)
        // And moving "a" (doc index 0) up to the same gap keeps index 1 unadjusted too,
        // because removing a (index 0) shifts the raw slot 1 down to 0? No: raw slot is
        // "in front of a" = 1; a itself sits at 0 < 1, so adjusted = 0 — dropping a next
        // to itself is a no-op position, pinned here to document the arithmetic.
        val self = resolveLayerDropTarget(document, rows, dragId = "a", pointerRowPosition = 3.95)
        val selfGap = assertIs<LayerDropTarget.InsertGap>(self)
        assertEquals(0, selfGap.index)
    }

    // --- Depth-0 terminal gap (empty / collapsed root): must not crash on the empty [1,0] band ---

    @Test
    fun terminalGapUnderEmptyRootDoesNotThrowAndRejectsSelfDrag() {
        // An empty (or collapsed) root frame is the only row, at depth 0. Dropping below it used to
        // throw IllegalArgumentException (coerceIn(shallowest=1, deepest=0)); now it resolves to the
        // back of the root, and a self-drag of the root is cleanly rejected by the subtree guard.
        val doc = DesignDocument(
            pages = listOf(DesignPage(id = "page", name = "Page", children = listOf(frame("root")))),
        )
        val single = listOf(LayerTreeRow("root", 0, isContainer = true))
        assertNull(resolveLayerDropTarget(doc, single, dragId = "root", pointerRowPosition = 0.9, pointerDepth = 5))
    }

    @Test
    fun terminalGapUnderCollapsedRootDropsChildAtItsBack() {
        // Root collapsed → only its row shows; dragging the hidden child to the very bottom lands it
        // at the back (document index 0) of the root, not off the tree or into the page.
        val doc = DesignDocument(
            pages = listOf(DesignPage(id = "page", name = "Page", children = listOf(frame("root", shape("x"))))),
        )
        val collapsed = listOf(LayerTreeRow("root", 0, isContainer = true))
        val gap = assertIs<LayerDropTarget.InsertGap>(
            resolveLayerDropTarget(doc, collapsed, dragId = "x", pointerRowPosition = 0.9, pointerDepth = 0),
        )
        assertEquals("root", gap.parentId)
        assertEquals(0, gap.index)
    }

    @Test
    fun terminalGapUnderDepth0BackmostRowDoesNotThrow() {
        // Two top-level rows at depth 0 (the backmost is the tree's bottom row). Dropping below it
        // used to throw; now it resolves without crashing.
        val doc = DesignDocument(
            pages = listOf(DesignPage(id = "page", name = "Page", children = listOf(shape("x"), shape("y")))),
        )
        // Front-first: [y, x]; terminal gap under x (depth 0).
        val two = listOf(LayerTreeRow("y", 0, isContainer = false), LayerTreeRow("x", 0, isContainer = false))
        val gap = assertIs<LayerDropTarget.InsertGap>(
            resolveLayerDropTarget(doc, two, dragId = "y", pointerRowPosition = 1.9, pointerDepth = 3),
        )
        assertEquals("x", gap.parentId)
    }
}
