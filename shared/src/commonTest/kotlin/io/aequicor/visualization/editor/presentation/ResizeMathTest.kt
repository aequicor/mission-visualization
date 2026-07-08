package io.aequicor.visualization.editor.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure geometry coverage for [computeResize] against the design-book §3 handle cases.
 * The reference object is rect x=100, y=100, w=200, h=100; results are expressed as the
 * final absolute box (x, y, w, h) with x = 100 + dx, y = 100 + dy.
 */
class ResizeMathTest {

    private val baseW = 200.0
    private val baseH = 100.0
    private val baseX = 100.0
    private val baseY = 100.0

    /** Applies [computeResize] and returns the final absolute box. */
    private fun resize(
        handle: ResizeHandle,
        dx: Double,
        dy: Double,
        lockRatio: Boolean = false,
    ): List<Double> {
        val r = computeResize(baseW, baseH, handle, dx, dy, lockRatio = lockRatio)
        return listOf(baseX + r.dx, baseY + r.dy, r.width, r.height)
    }

    @Test fun dragRightEdgePlus50() = assertEquals(listOf(100.0, 100.0, 250.0, 100.0), resize(ResizeHandle.Right, 50.0, 0.0))

    @Test fun dragRightEdgeMinus50() = assertEquals(listOf(100.0, 100.0, 150.0, 100.0), resize(ResizeHandle.Right, -50.0, 0.0))

    @Test fun dragLeftEdgeMinus50() = assertEquals(listOf(50.0, 100.0, 250.0, 100.0), resize(ResizeHandle.Left, -50.0, 0.0))

    @Test fun dragLeftEdgePlus50() = assertEquals(listOf(150.0, 100.0, 150.0, 100.0), resize(ResizeHandle.Left, 50.0, 0.0))

    @Test fun dragBottomEdgePlus40() = assertEquals(listOf(100.0, 100.0, 200.0, 140.0), resize(ResizeHandle.Bottom, 0.0, 40.0))

    @Test fun dragBottomEdgeMinus40() = assertEquals(listOf(100.0, 100.0, 200.0, 60.0), resize(ResizeHandle.Bottom, 0.0, -40.0))

    @Test fun dragTopEdgeMinus40() = assertEquals(listOf(100.0, 60.0, 200.0, 140.0), resize(ResizeHandle.Top, 0.0, -40.0))

    @Test fun dragTopEdgePlus40() = assertEquals(listOf(100.0, 140.0, 200.0, 60.0), resize(ResizeHandle.Top, 0.0, 40.0))

    @Test fun dragBottomRightCornerPlus50Plus40() =
        assertEquals(listOf(100.0, 100.0, 250.0, 140.0), resize(ResizeHandle.BottomRight, 50.0, 40.0))

    @Test fun dragTopLeftCornerMinus50Minus40() =
        assertEquals(listOf(50.0, 60.0, 250.0, 140.0), resize(ResizeHandle.TopLeft, -50.0, -40.0))

    // --- Min-size + position clamp (the regression the audit surfaced) ---

    @Test fun leftHandlePastRightEdgePinsRightEdge() {
        // Dragging the left handle far right must clamp width at the minimum AND stop the
        // left edge at (right edge - minWidth); the right edge (300) must not move.
        val box = resize(ResizeHandle.Left, 250.0, 0.0)
        assertEquals(listOf(299.0, 100.0, 1.0, 100.0), box)
        assertEquals(300.0, box[0] + box[2], "right edge stays pinned at 300")
    }

    @Test fun topHandlePastBottomEdgePinsBottomEdge() {
        val box = resize(ResizeHandle.Top, 0.0, 140.0)
        assertEquals(listOf(100.0, 199.0, 200.0, 1.0), box)
        assertEquals(200.0, box[1] + box[3], "bottom edge stays pinned at 200")
    }

    @Test fun rightHandleNeverMovesOrigin() {
        val box = resize(ResizeHandle.Right, -500.0, 0.0)
        assertEquals(100.0, box[0], "x unchanged")
        assertEquals(1.0, box[2], "width clamped at minimum")
    }

    // --- Lock ratio (corner) ---

    @Test fun lockRatioCornerKeepsAspect() {
        // ratio = 200/100 = 2; dragging BR by +50 in x drives height from width.
        val box = resize(ResizeHandle.BottomRight, 50.0, 0.0, lockRatio = true)
        assertEquals(250.0, box[2])
        assertEquals(125.0, box[3])
    }

    @Test fun lockRatioIgnoredForEdgeHandle() {
        // A single edge is not a corner: aspect lock must not distort the other axis.
        val box = resize(ResizeHandle.Right, 50.0, 0.0, lockRatio = true)
        assertEquals(listOf(100.0, 100.0, 250.0, 100.0), box)
    }
}
