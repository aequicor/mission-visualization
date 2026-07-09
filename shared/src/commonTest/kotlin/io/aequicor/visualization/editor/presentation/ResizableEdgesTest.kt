package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignLayoutChild
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [resizableEdges]: which edges of a node a canvas handle may move under the current
 * layout. The fixture is a page → root frame (a container in some layout mode) → three children;
 * the middle child ("c2") is the flow child the reported bug is about.
 */
class ResizableEdgesTest {

    private val middle = "c2"

    /** Builds a page → root(container) → [c1, c2, c3]; the container's layout is parameterized. */
    private fun doc(
        parentMode: LayoutMode,
        alignItems: AlignItems = AlignItems.Start,
        middleAlignSelf: AlignItems? = null,
        middleAbsolute: Boolean = false,
        middleSizing: DesignSizing? = null,
    ): DesignDocument {
        fun frame(id: String, layoutChild: DesignLayoutChild = DesignLayoutChild(), sizing: DesignSizing? = null) =
            DesignNode(id = id, type = "frame", kind = DesignNodeKind.Frame, layoutChild = layoutChild, sizing = sizing)
        val root = frame("root").copy(
            layout = DesignAutoLayout(mode = parentMode, alignItems = alignItems),
            children = listOf(
                frame("c1"),
                frame(middle, DesignLayoutChild(alignSelf = middleAlignSelf, absolute = middleAbsolute), middleSizing),
                frame("c3"),
            ),
        )
        return DesignDocument(pages = listOf(DesignPage(id = "page", children = listOf(root))))
    }

    private fun edges(doc: DesignDocument, nodeId: String = middle) = doc.resizableEdges(nodeId)

    // --- Horizontal flow: main-axis left is pinned, right grows; cross axis by alignment ---

    @Test fun horizontalStartPinsLeftAndTop() =
        assertEquals(ResizableEdges(left = false, right = true, top = false, bottom = true), edges(doc(LayoutMode.Horizontal)))

    @Test fun horizontalCenterKeepsCrossEdgesMovable() =
        assertEquals(
            ResizableEdges(left = false, right = true, top = true, bottom = true),
            edges(doc(LayoutMode.Horizontal, alignItems = AlignItems.Center)),
        )

    @Test fun horizontalEndPinsBottom() =
        assertEquals(
            ResizableEdges(left = false, right = true, top = true, bottom = false),
            edges(doc(LayoutMode.Horizontal, alignItems = AlignItems.End)),
        )

    @Test fun horizontalStretchPinsBothCrossEdges() =
        assertEquals(
            ResizableEdges(left = false, right = true, top = false, bottom = false),
            edges(doc(LayoutMode.Horizontal, alignItems = AlignItems.Stretch)),
        )

    @Test fun horizontalFillVerticalPinsBothCrossEdges() =
        assertEquals(
            ResizableEdges(left = false, right = true, top = false, bottom = false),
            edges(doc(LayoutMode.Horizontal, middleSizing = DesignSizing(vertical = SizingMode.Fill))),
        )

    @Test fun alignSelfOverridesParentAlignItems() =
        assertEquals(
            ResizableEdges(left = false, right = true, top = true, bottom = true),
            edges(doc(LayoutMode.Horizontal, alignItems = AlignItems.Start, middleAlignSelf = AlignItems.Center)),
        )

    @Test fun baselineAlignBehavesLikeStart() =
        assertEquals(
            ResizableEdges(left = false, right = true, top = false, bottom = true),
            edges(doc(LayoutMode.Horizontal, middleAlignSelf = AlignItems.Baseline)),
        )

    // --- Vertical flow: main-axis top is pinned, bottom grows; cross axis = horizontal ---

    @Test fun verticalStartPinsTopAndLeft() =
        assertEquals(ResizableEdges(left = false, right = true, top = false, bottom = true), edges(doc(LayoutMode.Vertical)))

    @Test fun verticalEndPinsRight() =
        assertEquals(
            ResizableEdges(left = true, right = false, top = false, bottom = true),
            edges(doc(LayoutMode.Vertical, alignItems = AlignItems.End)),
        )

    // --- Coordinate-positioned nodes move on all four edges ---

    @Test fun topLevelFrameMovesAllEdges() =
        assertEquals(ResizableEdges.All, edges(doc(LayoutMode.Horizontal), nodeId = "root"))

    @Test fun absoluteChildMovesAllEdges() =
        assertEquals(ResizableEdges.All, edges(doc(LayoutMode.Horizontal, middleAbsolute = true)))

    @Test fun freeParentChildMovesAllEdges() =
        assertEquals(ResizableEdges.All, edges(doc(LayoutMode.None)))

    // --- Grid: conservative — start edges pinned to the cell, end edges can grow ---

    @Test fun gridPinsStartEdges() =
        assertEquals(ResizableEdges(left = false, right = true, top = false, bottom = true), edges(doc(LayoutMode.Grid)))

    // --- Composed with computeResize: the exact reported scenario, end to end ---
    // Middle of three components in a horizontal auto-layout, box 200x100. This is what the
    // canvas apply-path does: resolve the node's movable edges, then run computeResize gated by
    // them. Dragging the flow-pinned LEFT handle must be a no-op; the RIGHT handle still resizes.

    @Test fun horizontalFlowLeftHandleDragIsNoOp() {
        val e = edges(doc(LayoutMode.Horizontal))
        val r = computeResize(
            baseWidth = 200.0, baseHeight = 100.0, handle = ResizeHandle.Left,
            docDx = -50.0, docDy = 0.0,
            canMoveLeft = e.left, canMoveRight = e.right, canMoveTop = e.top, canMoveBottom = e.bottom,
        )
        assertEquals(200.0, r.width, "width unchanged — left edge is flow-pinned")
        assertEquals(0.0, r.dx, "no origin shift, so nothing grows the other way")
    }

    @Test fun horizontalFlowRightHandleStillResizes() {
        val e = edges(doc(LayoutMode.Horizontal))
        val r = computeResize(
            baseWidth = 200.0, baseHeight = 100.0, handle = ResizeHandle.Right,
            docDx = 60.0, docDy = 0.0,
            canMoveLeft = e.left, canMoveRight = e.right, canMoveTop = e.top, canMoveBottom = e.bottom,
        )
        assertEquals(260.0, r.width, "right edge grows in the drag direction")
        assertEquals(0.0, r.dx)
    }
}
