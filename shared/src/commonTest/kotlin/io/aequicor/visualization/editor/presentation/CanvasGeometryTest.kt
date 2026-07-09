package io.aequicor.visualization.editor.presentation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure geometry coverage for the rotated selection / move-anchor-line / Alt-measurement
 * math introduced for the Figma-like advanced positioning preview (design-book §18).
 */
class CanvasGeometryTest {

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }

    // --- Rotation ---------------------------------------------------------------

    @Test
    fun rotate90ClockwiseSwingsTopCenterToTheRightByHalfHeight() {
        val box = BoundsBox(x = 100.0, y = 100.0, width = 200.0, height = 100.0)
        val handles = rotatedHandlePoints(box, 90.0)
        val top = handles.getValue(ResizeHandle.Top)
        // A 90deg clockwise rotation swings the top-center handle onto the right side, at
        // the same distance from center it started (height / 2) — not the box's own right
        // edge, since this box isn't square.
        assertClose(box.centerX + box.height / 2.0, top.x)
        assertClose(box.centerY, top.y)
    }

    @Test
    fun rotateZeroLeavesCornersUnchanged() {
        val box = BoundsBox(x = 10.0, y = 20.0, width = 40.0, height = 30.0)
        val corners = rotatedCorners(box, 0.0)
        assertEquals(listOf(10.0, 20.0), listOf(corners[0].x, corners[0].y))
        assertEquals(listOf(50.0, 50.0), listOf(corners[2].x, corners[2].y))
    }

    @Test
    fun rotate180MapsTopLeftToBottomRight() {
        val box = BoundsBox(x = 0.0, y = 0.0, width = 100.0, height = 50.0)
        val corners = rotatedCorners(box, 180.0)
        assertClose(box.right, corners[0].x)
        assertClose(box.bottom, corners[0].y)
    }

    @Test
    fun angleFromCenterMatchesRotationConvention() {
        val center = GeoPoint(0.0, 0.0)
        assertClose(0.0, angleFromCenterDegrees(center, GeoPoint(0.0, -10.0)))
        assertClose(90.0, angleFromCenterDegrees(center, GeoPoint(10.0, 0.0)))
        assertClose(180.0, angleFromCenterDegrees(center, GeoPoint(0.0, 10.0)))
        assertClose(270.0, angleFromCenterDegrees(center, GeoPoint(-10.0, 0.0)))
    }

    @Test
    fun rotatePointAroundCenterInvertsHitTestConvention() {
        // LayoutBox.hitTest inverse-rotates a world point by -rotation around the box
        // center; rotatePointAroundCenter is defined as the exact forward transform, so
        // composing them must be the identity.
        val center = GeoPoint(50.0, 40.0)
        val local = GeoPoint(70.0, 45.0)
        val rotation = 37.0
        val world = rotatePointAroundCenter(local, center, rotation)
        val radians = -rotation * kotlin.math.PI / 180.0
        val dx = world.x - center.x
        val dy = world.y - center.y
        val backX = center.x + dx * kotlin.math.cos(radians) - dy * kotlin.math.sin(radians)
        val backY = center.y + dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)
        assertClose(local.x, backX)
        assertClose(local.y, backY)
    }

    @Test
    fun snapAngleRoundsToNearestIncrement() {
        assertClose(15.0, snapAngleToIncrement(11.0, 15.0))
        assertClose(30.0, snapAngleToIncrement(23.0, 15.0))
        assertClose(0.0, snapAngleToIncrement(-4.0, 15.0))
    }

    @Test
    fun normalizeAngleWrapsIntoZeroTo360() {
        assertClose(350.0, normalizeAngleDegrees(-10.0))
        assertClose(10.0, normalizeAngleDegrees(370.0))
    }

    // --- Inherited (ancestor) rotation -------------------------------------------

    @Test
    fun effectiveTransformWithoutAncestorsIsTheBoxItself() {
        val box = BoundsBox(x = 10.0, y = 20.0, width = 40.0, height = 30.0)
        val t = effectiveTransform(box, ownRotation = 25.0, ancestors = emptyList())
        assertEquals(box, t.box)
        assertClose(25.0, t.rotation)
        assertClose(25.0, t.ownRotation)
    }

    @Test
    fun rotatedRootCarriesAnUnrotatedChildToItsVisualCenter() {
        // Root rotated 90° clockwise about its own center; the child's own rotation is 0.
        val rootCenter = GeoPoint(500.0, 400.0)
        val child = BoundsBox(x = 600.0, y = 380.0, width = 40.0, height = 40.0) // center (620, 400)
        val t = effectiveTransform(child, ownRotation = 0.0, ancestors = listOf(AncestorRotation(rootCenter, 90.0)))
        // A 90° cw rotation about (500,400) maps (620,400) → (500, 400 + 120) = (500, 520).
        assertClose(500.0, t.box.centerX)
        assertClose(520.0, t.box.centerY)
        assertClose(90.0, t.rotation)   // inherited from the root
        assertClose(0.0, t.ownRotation) // the child itself is unrotated
        // Size is preserved (only relocated + rotated).
        assertClose(40.0, t.box.width)
        assertClose(40.0, t.box.height)
    }

    @Test
    fun effectiveTransformReproducesTheRenderersNestedRotationQuad() {
        // The renderer draws a child as R_root(R_child(corner)) about each box's own center.
        // The effective transform must yield the identical visual quad via rotatedCorners.
        val rootCenter = GeoPoint(300.0, 200.0)
        val rootRotation = 37.0
        val child = BoundsBox(x = 340.0, y = 150.0, width = 80.0, height = 60.0)
        val childRotation = 20.0
        val childCenter = GeoPoint(child.centerX, child.centerY)

        val expected = rotatedCorners(child, childRotation).map { corner ->
            rotatePointAroundCenter(corner, rootCenter, rootRotation)
        }

        val t = effectiveTransform(child, ownRotation = childRotation, ancestors = listOf(AncestorRotation(rootCenter, rootRotation)))
        val actual = rotatedCorners(t.box, t.rotation)

        expected.zip(actual).forEach { (e, a) ->
            assertClose(e.x, a.x)
            assertClose(e.y, a.y)
        }
    }

    @Test
    fun ancestorsComposeNearestFirstMatchingNestedRotatePivots() {
        // Two rotated ancestors: immediate parent first, root last (as the renderer nests them).
        val parentCenter = GeoPoint(150.0, 150.0)
        val rootCenter = GeoPoint(0.0, 0.0)
        val node = BoundsBox(x = 190.0, y = 140.0, width = 20.0, height = 20.0) // center (200,150)
        val ancestors = listOf(
            AncestorRotation(parentCenter, 30.0), // nearest (immediate parent) — applied first
            AncestorRotation(rootCenter, 45.0),   // root — applied last
        )
        val expectedCenter = rotatePointAroundCenter(
            rotatePointAroundCenter(GeoPoint(200.0, 150.0), parentCenter, 30.0),
            rootCenter, 45.0,
        )
        val t = effectiveTransform(node, ownRotation = 10.0, ancestors = ancestors)
        assertClose(expectedCenter.x, t.box.centerX)
        assertClose(expectedCenter.y, t.box.centerY)
        assertClose(10.0 + 30.0 + 45.0, t.rotation)
        assertClose(85.0, ancestorRotationDegrees(ancestors) + 10.0)
    }

    @Test
    fun ancestorRotationDegreesSumsOnlyTheAncestors() {
        val ancestors = listOf(
            AncestorRotation(GeoPoint(0.0, 0.0), 30.0),
            AncestorRotation(GeoPoint(1.0, 1.0), -12.5),
        )
        assertClose(17.5, ancestorRotationDegrees(ancestors))
        assertClose(0.0, ancestorRotationDegrees(emptyList()))
    }

    // --- Resize cursor orientation -----------------------------------------------

    @Test
    fun cursorKindUnrotated() {
        assertEquals(ResizeCursorKind.Vertical, resizeCursorKindForHandle(ResizeHandle.Top, 0.0))
        assertEquals(ResizeCursorKind.Vertical, resizeCursorKindForHandle(ResizeHandle.Bottom, 0.0))
        assertEquals(ResizeCursorKind.Horizontal, resizeCursorKindForHandle(ResizeHandle.Left, 0.0))
        assertEquals(ResizeCursorKind.Horizontal, resizeCursorKindForHandle(ResizeHandle.Right, 0.0))
        assertEquals(ResizeCursorKind.DiagonalTopRightBottomLeft, resizeCursorKindForHandle(ResizeHandle.TopRight, 0.0))
        assertEquals(ResizeCursorKind.DiagonalTopRightBottomLeft, resizeCursorKindForHandle(ResizeHandle.BottomLeft, 0.0))
        assertEquals(ResizeCursorKind.DiagonalTopLeftBottomRight, resizeCursorKindForHandle(ResizeHandle.TopLeft, 0.0))
        assertEquals(ResizeCursorKind.DiagonalTopLeftBottomRight, resizeCursorKindForHandle(ResizeHandle.BottomRight, 0.0))
    }

    @Test
    fun cursorKindRotates90DegreesSwapsAxis() {
        // A component rotated 90deg turns its top/bottom edges into what visually reads
        // as left/right, so the resize cursor axis must swap too.
        assertEquals(ResizeCursorKind.Horizontal, resizeCursorKindForHandle(ResizeHandle.Top, 90.0))
        assertEquals(ResizeCursorKind.Vertical, resizeCursorKindForHandle(ResizeHandle.Right, 90.0))
    }

    @Test
    fun cursorKindRotates45DegreesBecomesDiagonal() {
        assertEquals(ResizeCursorKind.DiagonalTopRightBottomLeft, resizeCursorKindForHandle(ResizeHandle.Top, 45.0))
    }

    // --- Alt measurement gaps -----------------------------------------------------

    @Test
    fun gapsToContainingParentAreFourSidedPadding() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val child = BoundsBox(x = 20.0, y = 30.0, width = 100.0, height = 50.0)
        val gaps = measureGaps(child, parent)
        assertEquals(30.0, gaps.top)
        assertEquals(280.0, gaps.right)
        assertEquals(220.0, gaps.bottom)
        assertEquals(20.0, gaps.left)
        assertNull(gaps.centerXDistance)
        assertNull(gaps.centerYDistance)
    }

    @Test
    fun gapsBetweenSeparatedSiblingsAreDirectional() {
        val left = BoundsBox(x = 0.0, y = 0.0, width = 50.0, height = 50.0)
        val right = BoundsBox(x = 100.0, y = 10.0, width = 50.0, height = 50.0)
        val gaps = measureGaps(left, right)
        assertEquals(50.0, gaps.right)
        assertNull(gaps.left)
        assertNull(gaps.top)
        assertNull(gaps.bottom)
    }

    @Test
    fun gapsBetweenHorizontallyOverlappingSiblingsUseCenterDistance() {
        val above = BoundsBox(x = 0.0, y = 0.0, width = 50.0, height = 50.0)
        val below = BoundsBox(x = 10.0, y = 100.0, width = 50.0, height = 50.0)
        val gaps = measureGaps(above, below)
        assertEquals(50.0, gaps.bottom)
        assertNull(gaps.left)
        assertNull(gaps.right)
        assertEquals(10.0, gaps.centerXDistance)
    }

    @Test
    fun axisAlignedBoundsOfRotatedCornersMatchesRotatedBoundingBox() {
        val box = BoundsBox(x = 0.0, y = 0.0, width = 100.0, height = 100.0)
        val bounds = axisAlignedBounds(rotatedCorners(box, 45.0))
        // A 100x100 square rotated 45deg has a bounding box diagonal = side * sqrt(2).
        assertClose(100.0 * kotlin.math.sqrt(2.0), bounds.width)
        assertClose(100.0 * kotlin.math.sqrt(2.0), bounds.height)
    }

    // --- Auto layout reorder-by-drag ----------------------------------------------

    private val row = listOf(
        BoundsBox(x = 0.0, y = 0.0, width = 100.0, height = 50.0),
        BoundsBox(x = 100.0, y = 0.0, width = 100.0, height = 50.0),
        BoundsBox(x = 200.0, y = 0.0, width = 100.0, height = 50.0),
    )

    @Test
    fun flowInsertionIndexBeforeFirstSibling() {
        assertEquals(0, flowInsertionIndex(row, pointerMain = -10.0, horizontal = true))
    }

    @Test
    fun flowInsertionIndexBetweenSiblings() {
        // Pointer inside the first box but past its center (75) lands before the second box.
        assertEquals(1, flowInsertionIndex(row, pointerMain = 80.0, horizontal = true))
    }

    @Test
    fun flowInsertionIndexAfterLastSibling() {
        assertEquals(3, flowInsertionIndex(row, pointerMain = 500.0, horizontal = true))
    }

    @Test
    fun flowInsertionIndexEmptySiblingsIsZero() {
        assertEquals(0, flowInsertionIndex(emptyList(), pointerMain = 42.0, horizontal = true))
    }

    @Test
    fun flowInsertionIndexVerticalUsesYAxis() {
        val column = listOf(
            BoundsBox(x = 0.0, y = 0.0, width = 50.0, height = 100.0),
            BoundsBox(x = 0.0, y = 100.0, width = 50.0, height = 100.0),
        )
        assertEquals(1, flowInsertionIndex(column, pointerMain = 120.0, horizontal = false))
    }

    @Test
    fun flowInsertionLineSitsAtBoundaryBetweenSiblings() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 300.0, height = 50.0)
        val line = flowInsertionLine(row, index = 1, parent = parent, horizontal = true)
        assertEquals(LineSegment(100.0, 0.0, 100.0, 50.0), line)
    }

    @Test
    fun flowInsertionLineAtStartSitsBeforeFirstSibling() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 300.0, height = 50.0)
        val line = flowInsertionLine(row, index = 0, parent = parent, horizontal = true)
        assertEquals(0.0, line.x1)
    }

    @Test
    fun flowInsertionLineAtEndSitsAfterLastSibling() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 300.0, height = 50.0)
        val line = flowInsertionLine(row, index = 3, parent = parent, horizontal = true)
        assertEquals(300.0, line.x1)
    }

    @Test
    fun flowInsertionLineEmptySiblingsFallsBackToParentCenter() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 300.0, height = 50.0)
        val line = flowInsertionLine(emptyList(), index = 0, parent = parent, horizontal = true)
        assertEquals(150.0, line.x1)
    }

}
