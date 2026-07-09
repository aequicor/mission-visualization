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

    // --- Center anchor lines -----------------------------------------------------

    @Test
    fun centerAnchorLinesSpanParentEdges() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val box = BoundsBox(x = 150.0, y = 80.0, width = 60.0, height = 40.0)
        val lines = centerAnchorLines(box, parent)
        assertEquals(LineSegment(0.0, 100.0, 400.0, 100.0), lines.horizontal)
        assertEquals(LineSegment(180.0, 0.0, 180.0, 300.0), lines.vertical)
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

    // --- Move-drag smart snapping -------------------------------------------------

    @Test
    fun snapEdgeToEdgeWithinThresholdSnapsLeftEdge() {
        // Only the left edges are within 6px; wider target keeps center/right out of range.
        val moved = BoundsBox(x = 103.0, y = 0.0, width = 40.0, height = 40.0)
        val target = BoundsBox(x = 100.0, y = 100.0, width = 60.0, height = 60.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(-3.0, snap.dx)
        assertClose(0.0, snap.dy)
        assertEquals(1, snap.guides.size)
        assertEquals(LineSegment(100.0, 0.0, 100.0, 160.0), snap.guides.first())
    }

    @Test
    fun snapCenterToCenterAlignsCenters() {
        // Only the centers align (2px); edges are far outside the threshold.
        val moved = BoundsBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val target = BoundsBox(x = 22.0, y = 500.0, width = 200.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(2.0, snap.dx)
        assertClose(0.0, snap.dy)
        // Guide sits on the aligned center x = target.centerX = 122.
        assertEquals(1, snap.guides.size)
        assertClose(122.0, snap.guides.first().x1)
        assertClose(122.0, snap.guides.first().x2)
    }

    @Test
    fun snapThresholdBoundaryIsInclusive() {
        val moved = BoundsBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        // Left edges exactly 6px apart: snaps (inclusive boundary).
        val onEdge = computeSnap(moved, listOf(BoundsBox(x = 106.0, y = 500.0, width = 200.0, height = 40.0)), threshold = 6.0)
        assertClose(6.0, onEdge.dx)
        // 7px apart: outside the threshold, no snap.
        val justOver = computeSnap(moved, listOf(BoundsBox(x = 107.0, y = 500.0, width = 200.0, height = 40.0)), threshold = 6.0)
        assertClose(0.0, justOver.dx)
        assertTrue(justOver.guides.isEmpty())
    }

    @Test
    fun snapNearestTargetWins() {
        val moved = BoundsBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val far = BoundsBox(x = 105.0, y = 500.0, width = 40.0, height = 40.0) // 5px
        val near = BoundsBox(x = 102.0, y = 600.0, width = 40.0, height = 40.0) // 2px
        val snap = computeSnap(moved, listOf(far, near), threshold = 6.0)
        assertClose(2.0, snap.dx)
    }

    @Test
    fun snapTieBreakPrefersCenterOverEdge() {
        // Equal-width target: left/center/right all 3px away → center alignment wins the tie.
        val moved = BoundsBox(x = 103.0, y = 0.0, width = 40.0, height = 40.0)
        val target = BoundsBox(x = 100.0, y = 500.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(-3.0, snap.dx)
        // The guide is on the center line (x = 120), not an edge (100 or 140).
        assertClose(120.0, snap.guides.single().x1)
    }

    @Test
    fun snapNoCandidateInRangeReturnsZero() {
        val moved = BoundsBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val target = BoundsBox(x = 200.0, y = 200.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(0.0, snap.dx)
        assertClose(0.0, snap.dy)
        assertTrue(snap.guides.isEmpty())
    }

    @Test
    fun snapToParentEdge() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val moved = BoundsBox(x = 3.0, y = 100.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(parent), threshold = 6.0)
        assertClose(-3.0, snap.dx)
        assertClose(0.0, snap.guides.single().x1) // snapped onto the parent's left edge
    }

    @Test
    fun snapAxesSnapIndependentlyToDifferentTargets() {
        val parent = BoundsBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val sibling = BoundsBox(x = 200.0, y = 100.0, width = 40.0, height = 40.0)
        val moved = BoundsBox(x = 197.0, y = 3.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(sibling, parent), threshold = 6.0)
        assertClose(3.0, snap.dx) // X aligns to the sibling
        assertClose(-3.0, snap.dy) // Y aligns to the parent's top edge
    }

    @Test
    fun snapGuideSpansUnionOfSnappedBoxAndTarget() {
        val moved = BoundsBox(x = 103.0, y = 0.0, width = 40.0, height = 200.0)
        val target = BoundsBox(x = 100.0, y = 300.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        // Vertical guide at the aligned center x, spanning from the top of the snapped box
        // (0) to the bottom of the lower target (340).
        assertEquals(LineSegment(120.0, 0.0, 120.0, 340.0), snap.guides.single())
    }

    @Test
    fun snapEmptyTargetsOrZeroThresholdReturnsZero() {
        val moved = BoundsBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val noTargets = computeSnap(moved, emptyList(), threshold = 6.0)
        assertClose(0.0, noTargets.dx)
        assertClose(0.0, noTargets.dy)
        assertTrue(noTargets.guides.isEmpty())
        val zeroThreshold = computeSnap(moved, listOf(BoundsBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)), threshold = 0.0)
        assertClose(0.0, zeroThreshold.dx)
        assertTrue(zeroThreshold.guides.isEmpty())
    }

    // --- Beautiful-anchor snapping (golden ratio / proportions / equal spacing) -----------

    /** 0.618 = 1/φ, matching CanvasGeometry's golden fraction. */
    private val goldenMajor = 0.6180339887498949

    /** A tall container whose Y "beautiful" lines are all far from a box placed at y=50, h=40 (sources 50/70/90). */
    private val wideFrame = BoundsBox(x = 0.0, y = 0.0, width = 1000.0, height = 400.0)

    @Test
    fun anchorSnapsBoxCenterToContainerGoldenLine() {
        // Box center at 616; the container's 0.618 golden line sits at 618.03 → snaps +2.03.
        val goldenX = goldenMajor * 1000.0
        val moved = BoundsBox(x = 596.0, y = 50.0, width = 40.0, height = 40.0)
        val result = computeAnchors(moved, containers = listOf(wideFrame), siblings = emptyList(), threshold = 6.0)
        assertClose(goldenX - 616.0, result.dx)
        assertClose(0.0, result.dy)
        val guide = result.guides.single()
        assertEquals(AnchorKind.GoldenRatio, guide.kind)
        assertEquals("φ", guide.label)
        // Vertical guide on the golden line, spanning the container's full height.
        assertClose(goldenX, guide.line.x1)
        assertClose(goldenX, guide.line.x2)
        assertClose(0.0, guide.line.y1)
        assertClose(400.0, guide.line.y2)
        assertTrue(result.spacing.isEmpty())
    }

    @Test
    fun anchorSnapsToContainerThirdLine() {
        // 1/3 of a 900-wide container = 300; box center at 297 → snaps +3, labelled "1/3".
        val frame = BoundsBox(x = 0.0, y = 0.0, width = 900.0, height = 400.0)
        val moved = BoundsBox(x = 277.0, y = 50.0, width = 40.0, height = 40.0)
        val result = computeAnchors(moved, containers = listOf(frame), siblings = emptyList(), threshold = 6.0)
        assertClose(3.0, result.dx)
        val guide = result.guides.single()
        assertEquals(AnchorKind.Proportion, guide.kind)
        assertEquals("1/3", guide.label)
        assertClose(300.0, guide.line.x1)
    }

    @Test
    fun anchorSnapsToContainerQuarterLine() {
        // 1/4 of an 800-wide container = 200; box center at 202 → snaps -2, labelled "1/4".
        val frame = BoundsBox(x = 0.0, y = 0.0, width = 800.0, height = 400.0)
        val moved = BoundsBox(x = 182.0, y = 50.0, width = 40.0, height = 40.0)
        val result = computeAnchors(moved, containers = listOf(frame), siblings = emptyList(), threshold = 6.0)
        assertClose(-2.0, result.dx)
        assertEquals("1/4", result.guides.single().label)
    }

    @Test
    fun anchorContainersEmptyHasNoGoldenOrProportion() {
        // Same box that snapped to golden above; with no containers there is nothing to anchor to.
        val moved = BoundsBox(x = 596.0, y = 50.0, width = 40.0, height = 40.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = emptyList(), threshold = 6.0)
        assertClose(0.0, result.dx)
        assertClose(0.0, result.dy)
        assertTrue(result.guides.isEmpty())
    }

    @Test
    fun anchorEqualSpacingCentersBoxBetweenFlankingSiblings() {
        // Two siblings leave a 300px slot (x 100..400) that overlaps the box on Y; a 60-wide box
        // whose center (254) is near the balancing center (250) snaps to equalize both gaps (120 each).
        val left = BoundsBox(x = 0.0, y = 100.0, width = 100.0, height = 100.0)
        val right = BoundsBox(x = 400.0, y = 100.0, width = 100.0, height = 100.0)
        val moved = BoundsBox(x = 224.0, y = 160.0, width = 60.0, height = 30.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = listOf(left, right), threshold = 6.0)
        assertClose(-4.0, result.dx)
        assertClose(0.0, result.dy)
        assertTrue(result.guides.isEmpty()) // equal spacing draws bars, not a guide line
        assertEquals(2, result.spacing.size)
        result.spacing.forEach { assertClose(120.0, it.gap) }
    }

    @Test
    fun anchorEqualMarginBalancesSiblingAgainstContainerWall() {
        // One sibling on the left, the container's right wall on the other side. The box snaps so
        // its gap to the sibling equals its gap to the wall (equal margins of 170 each).
        val container = BoundsBox(x = 0.0, y = 0.0, width = 500.0, height = 300.0)
        val sibling = BoundsBox(x = 0.0, y = 100.0, width = 100.0, height = 100.0) // inner edge at 100
        // Box centre near 300 = midpoint of sibling edge (100) and right wall (500).
        val moved = BoundsBox(x = 266.0, y = 132.0, width = 60.0, height = 8.0) // centerX = 296
        val result = computeAnchors(moved, containers = listOf(container), siblings = listOf(sibling), threshold = 6.0)
        assertClose(4.0, result.dx) // 300 - 296
        assertClose(0.0, result.dy)
        assertTrue(result.guides.isEmpty())
        assertEquals(2, result.spacing.size)
        result.spacing.forEach { assertClose(170.0, it.gap) } // (500 - 100 - 60) / 2
    }

    @Test
    fun anchorMatchGapDuplicatesAnExistingSiblingRhythm() {
        // Three siblings 40px apart establish a rhythm; dragging a fourth just past the last snaps
        // so its gap to that sibling is also 40 (and a reference bar marks the gap it matched).
        val s1 = BoundsBox(x = 0.0, y = 100.0, width = 60.0, height = 60.0)
        val s2 = BoundsBox(x = 100.0, y = 100.0, width = 60.0, height = 60.0) // gap s1→s2 = 40
        val s3 = BoundsBox(x = 200.0, y = 100.0, width = 60.0, height = 60.0) // gap s2→s3 = 40
        val moved = BoundsBox(x = 302.0, y = 138.0, width = 60.0, height = 8.0) // wants x=300 → gap 40 to s3
        val result = computeAnchors(moved, containers = emptyList(), siblings = listOf(s1, s2, s3), threshold = 6.0)
        assertClose(-2.0, result.dx) // 300 - 302
        assertClose(0.0, result.dy)
        assertTrue(result.guides.isEmpty())
        assertEquals(2, result.spacing.size) // the new gap + the reference gap it matched
        result.spacing.forEach { assertClose(40.0, it.gap) }
    }

    @Test
    fun anchorEqualSpacingRequiresPerpendicularOverlap() {
        // Same slot, but the siblings sit in a different Y band → no real row gap → no spacing snap.
        val left = BoundsBox(x = 0.0, y = 0.0, width = 100.0, height = 50.0)
        val right = BoundsBox(x = 400.0, y = 0.0, width = 100.0, height = 50.0)
        val moved = BoundsBox(x = 224.0, y = 160.0, width = 60.0, height = 30.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = listOf(left, right), threshold = 6.0)
        assertClose(0.0, result.dx)
        assertTrue(result.spacing.isEmpty())
    }

    @Test
    fun anchorEqualSpacingNeedsRoomForTheBox() {
        // The slot between the siblings (30px) is narrower than the 60px box → cannot balance it.
        val left = BoundsBox(x = 0.0, y = 100.0, width = 100.0, height = 100.0)
        val right = BoundsBox(x = 130.0, y = 100.0, width = 100.0, height = 100.0)
        val moved = BoundsBox(x = 85.0, y = 160.0, width = 60.0, height = 30.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = listOf(left, right), threshold = 6.0)
        assertClose(0.0, result.dx)
        assertTrue(result.spacing.isEmpty())
    }

    @Test
    fun anchorAlignmentWinsTieOverGolden() {
        // The box is 3px from both a sibling's left edge and the container's golden line. Real-geometry
        // alignment must win the tie, so it snaps to the sibling edge (598.03), not the golden line (618.03).
        val goldenX = goldenMajor * 1000.0
        val moved = BoundsBox(x = goldenX - 23.0, y = 50.0, width = 40.0, height = 40.0) // center = goldenX - 3
        // Narrow sibling far off on Y: its left edge is 3px right of moved.x, its center nowhere near golden.
        val sibling = BoundsBox(x = goldenX - 20.0, y = -500.0, width = 10.0, height = 40.0)
        val result = computeAnchors(moved, containers = listOf(wideFrame), siblings = listOf(sibling), threshold = 6.0)
        assertClose(3.0, result.dx)
        val vertical = result.guides.single { it.line.x1 == it.line.x2 }
        assertEquals(AnchorKind.Alignment, vertical.kind)
        assertTrue(result.guides.none { it.kind == AnchorKind.GoldenRatio })
    }

    @Test
    fun anchorResolvesEachAxisIndependently() {
        // X snaps to the container's golden line; Y snaps to a sibling's center — different kinds per axis.
        val goldenX = goldenMajor * 1000.0
        val sibling = BoundsBox(x = -500.0, y = 240.0, width = 40.0, height = 40.0) // centerY = 260
        val moved = BoundsBox(x = 596.0, y = 238.0, width = 40.0, height = 40.0) // centerX=616, centerY=258
        val result = computeAnchors(moved, containers = listOf(wideFrame), siblings = listOf(sibling), threshold = 6.0)
        assertClose(goldenX - 616.0, result.dx)
        assertClose(2.0, result.dy) // 260 - 258
        assertTrue(result.guides.any { it.kind == AnchorKind.GoldenRatio })
        assertTrue(result.guides.any { it.kind == AnchorKind.Alignment })
    }

    @Test
    fun anchorGoldenThresholdBoundaryIsInclusive() {
        val goldenX = goldenMajor * 1000.0
        // Center exactly 6px below the golden line → snaps; 7px → out of range.
        val onEdge = computeAnchors(
            BoundsBox(x = goldenX - 26.0, y = 50.0, width = 40.0, height = 40.0), // center = goldenX - 6
            containers = listOf(wideFrame), siblings = emptyList(), threshold = 6.0,
        )
        assertClose(6.0, onEdge.dx)
        val justOver = computeAnchors(
            BoundsBox(x = goldenX - 27.0, y = 50.0, width = 40.0, height = 40.0), // center = goldenX - 7
            containers = listOf(wideFrame), siblings = emptyList(), threshold = 6.0,
        )
        assertClose(0.0, justOver.dx)
        assertTrue(justOver.guides.isEmpty())
    }
}
