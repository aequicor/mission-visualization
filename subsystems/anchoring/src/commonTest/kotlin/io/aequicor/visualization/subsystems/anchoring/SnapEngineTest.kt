package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the pure move-drag snapping engine (edge/center alignment plus the
 * "beautiful" golden/proportion/equal-distance anchors, design-book §18). Migrated from the
 * editor's CanvasGeometryTest when the engine moved into `:subsystems:anchoring`.
 */
class SnapEngineTest {

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }

    // --- Center anchor lines -----------------------------------------------------

    @Test
    fun centerAnchorLinesSpanParentEdges() {
        val parent = SnapBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val box = SnapBox(x = 150.0, y = 80.0, width = 60.0, height = 40.0)
        val lines = centerAnchorLines(box, parent)
        assertEquals(SnapLine(0.0, 100.0, 400.0, 100.0), lines.horizontal)
        assertEquals(SnapLine(180.0, 0.0, 180.0, 300.0), lines.vertical)
    }

    // --- Move-drag smart snapping -------------------------------------------------

    @Test
    fun snapEdgeToEdgeWithinThresholdSnapsLeftEdge() {
        // Only the left edges are within 6px; wider target keeps center/right out of range.
        val moved = SnapBox(x = 103.0, y = 0.0, width = 40.0, height = 40.0)
        val target = SnapBox(x = 100.0, y = 100.0, width = 60.0, height = 60.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(-3.0, snap.dx)
        assertClose(0.0, snap.dy)
        assertEquals(1, snap.guides.size)
        assertEquals(SnapLine(100.0, 0.0, 100.0, 160.0), snap.guides.first())
    }

    @Test
    fun snapCenterToCenterAlignsCenters() {
        // Only the centers align (2px); edges are far outside the threshold.
        val moved = SnapBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val target = SnapBox(x = 22.0, y = 500.0, width = 200.0, height = 40.0)
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
        val moved = SnapBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        // Left edges exactly 6px apart: snaps (inclusive boundary).
        val onEdge = computeSnap(moved, listOf(SnapBox(x = 106.0, y = 500.0, width = 200.0, height = 40.0)), threshold = 6.0)
        assertClose(6.0, onEdge.dx)
        // 7px apart: outside the threshold, no snap.
        val justOver = computeSnap(moved, listOf(SnapBox(x = 107.0, y = 500.0, width = 200.0, height = 40.0)), threshold = 6.0)
        assertClose(0.0, justOver.dx)
        assertTrue(justOver.guides.isEmpty())
    }

    @Test
    fun snapNearestTargetWins() {
        val moved = SnapBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val far = SnapBox(x = 105.0, y = 500.0, width = 40.0, height = 40.0) // 5px
        val near = SnapBox(x = 102.0, y = 600.0, width = 40.0, height = 40.0) // 2px
        val snap = computeSnap(moved, listOf(far, near), threshold = 6.0)
        assertClose(2.0, snap.dx)
    }

    @Test
    fun snapTieBreakPrefersCenterButDrawsEveryCoincidentEdge() {
        // Equal-width target: left/center/right all 3px away → center alignment wins the tie.
        val moved = SnapBox(x = 103.0, y = 0.0, width = 40.0, height = 40.0)
        val target = SnapBox(x = 100.0, y = 500.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(-3.0, snap.dx)
        // The center remains the deterministic magnetic winner, while the overlay also exposes the
        // simultaneously aligned left/right edges instead of silently hiding those relationships.
        assertEquals(setOf(100.0, 120.0, 140.0), snap.guides.map { it.x1 }.toSet())
    }

    @Test
    fun snapNoCandidateInRangeReturnsZero() {
        val moved = SnapBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val target = SnapBox(x = 200.0, y = 200.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        assertClose(0.0, snap.dx)
        assertClose(0.0, snap.dy)
        assertTrue(snap.guides.isEmpty())
    }

    @Test
    fun snapToParentEdge() {
        val parent = SnapBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val moved = SnapBox(x = 3.0, y = 100.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(parent), threshold = 6.0)
        assertClose(-3.0, snap.dx)
        assertClose(0.0, snap.guides.single().x1) // snapped onto the parent's left edge
    }

    @Test
    fun snapAxesSnapIndependentlyToDifferentTargets() {
        val parent = SnapBox(x = 0.0, y = 0.0, width = 400.0, height = 300.0)
        val sibling = SnapBox(x = 200.0, y = 100.0, width = 40.0, height = 40.0)
        val moved = SnapBox(x = 197.0, y = 3.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(sibling, parent), threshold = 6.0)
        assertClose(3.0, snap.dx) // X aligns to the sibling
        assertClose(-3.0, snap.dy) // Y aligns to the parent's top edge
    }

    @Test
    fun snapGuideSpansUnionOfSnappedBoxAndTarget() {
        val moved = SnapBox(x = 103.0, y = 0.0, width = 40.0, height = 200.0)
        val target = SnapBox(x = 100.0, y = 300.0, width = 40.0, height = 40.0)
        val snap = computeSnap(moved, listOf(target), threshold = 6.0)
        // Vertical guide at the aligned center x, spanning from the top of the snapped box
        // (0) to the bottom of the lower target (340).
        assertEquals(
            SnapLine(120.0, 0.0, 120.0, 340.0),
            snap.guides.single { it.x1 == 120.0 },
        )
    }

    @Test
    fun snapEmptyTargetsOrZeroThresholdReturnsZero() {
        val moved = SnapBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)
        val noTargets = computeSnap(moved, emptyList(), threshold = 6.0)
        assertClose(0.0, noTargets.dx)
        assertClose(0.0, noTargets.dy)
        assertTrue(noTargets.guides.isEmpty())
        val zeroThreshold = computeSnap(moved, listOf(SnapBox(x = 100.0, y = 0.0, width = 40.0, height = 40.0)), threshold = 0.0)
        assertClose(0.0, zeroThreshold.dx)
        assertTrue(zeroThreshold.guides.isEmpty())
    }

    // --- Beautiful-anchor snapping (golden ratio / proportions / equal spacing) -----------

    /** 0.618 = 1/φ, matching the engine's golden fraction. */
    private val goldenMajor = 0.6180339887498949

    /** A tall container whose Y "beautiful" lines are all far from a box placed at y=50, h=40 (sources 50/70/90). */
    private val wideFrame = SnapBox(x = 0.0, y = 0.0, width = 1000.0, height = 400.0)

    @Test
    fun anchorSnapsBoxCenterToContainerGoldenLine() {
        // Box center at 616; the container's 0.618 golden line sits at 618.03 → snaps +2.03.
        val goldenX = goldenMajor * 1000.0
        val moved = SnapBox(x = 596.0, y = 50.0, width = 40.0, height = 40.0)
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
        val frame = SnapBox(x = 0.0, y = 0.0, width = 900.0, height = 400.0)
        val moved = SnapBox(x = 277.0, y = 50.0, width = 40.0, height = 40.0)
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
        val frame = SnapBox(x = 0.0, y = 0.0, width = 800.0, height = 400.0)
        val moved = SnapBox(x = 182.0, y = 50.0, width = 40.0, height = 40.0)
        val result = computeAnchors(moved, containers = listOf(frame), siblings = emptyList(), threshold = 6.0)
        assertClose(-2.0, result.dx)
        assertEquals("1/4", result.guides.single().label)
    }

    @Test
    fun anchorContainersEmptyHasNoGoldenOrProportion() {
        // Same box that snapped to golden above; with no containers there is nothing to anchor to.
        val moved = SnapBox(x = 596.0, y = 50.0, width = 40.0, height = 40.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = emptyList(), threshold = 6.0)
        assertClose(0.0, result.dx)
        assertClose(0.0, result.dy)
        assertTrue(result.guides.isEmpty())
    }

    @Test
    fun anchorEqualSpacingCentersBoxBetweenFlankingSiblings() {
        // Two siblings leave a 300px slot (x 100..400) that overlaps the box on Y; a 60-wide box
        // whose center (254) is near the balancing center (250) snaps to equalize both gaps (120 each).
        val left = SnapBox(x = 0.0, y = 100.0, width = 100.0, height = 100.0)
        val right = SnapBox(x = 400.0, y = 100.0, width = 100.0, height = 100.0)
        val moved = SnapBox(x = 224.0, y = 160.0, width = 60.0, height = 30.0)
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
        val container = SnapBox(x = 0.0, y = 0.0, width = 500.0, height = 300.0)
        val sibling = SnapBox(x = 0.0, y = 100.0, width = 100.0, height = 100.0) // inner edge at 100
        // Box centre near 300 = midpoint of sibling edge (100) and right wall (500).
        val moved = SnapBox(x = 266.0, y = 132.0, width = 60.0, height = 8.0) // centerX = 296
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
        val s1 = SnapBox(x = 0.0, y = 100.0, width = 60.0, height = 60.0)
        val s2 = SnapBox(x = 100.0, y = 100.0, width = 60.0, height = 60.0) // gap s1→s2 = 40
        val s3 = SnapBox(x = 200.0, y = 100.0, width = 60.0, height = 60.0) // gap s2→s3 = 40
        val moved = SnapBox(x = 302.0, y = 138.0, width = 60.0, height = 8.0) // wants x=300 → gap 40 to s3
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
        val left = SnapBox(x = 0.0, y = 0.0, width = 100.0, height = 50.0)
        val right = SnapBox(x = 400.0, y = 0.0, width = 100.0, height = 50.0)
        val moved = SnapBox(x = 224.0, y = 160.0, width = 60.0, height = 30.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = listOf(left, right), threshold = 6.0)
        assertClose(0.0, result.dx)
        assertTrue(result.spacing.isEmpty())
    }

    @Test
    fun anchorEqualSpacingNeedsRoomForTheBox() {
        // The slot between the siblings (30px) is narrower than the 60px box → cannot balance it.
        val left = SnapBox(x = 0.0, y = 100.0, width = 100.0, height = 100.0)
        val right = SnapBox(x = 130.0, y = 100.0, width = 100.0, height = 100.0)
        val moved = SnapBox(x = 85.0, y = 160.0, width = 60.0, height = 30.0)
        val result = computeAnchors(moved, containers = emptyList(), siblings = listOf(left, right), threshold = 6.0)
        assertClose(0.0, result.dx)
        assertTrue(result.spacing.isEmpty())
    }

    @Test
    fun anchorAlignmentWinsTieOverGolden() {
        // The box is 3px from both a sibling's left edge and the container's golden line. Real-geometry
        // alignment must win the tie, so it snaps to the sibling edge (598.03), not the golden line (618.03).
        val goldenX = goldenMajor * 1000.0
        val moved = SnapBox(x = goldenX - 23.0, y = 50.0, width = 40.0, height = 40.0) // center = goldenX - 3
        // Narrow sibling far off on Y: its left edge is 3px right of moved.x, its center nowhere near golden.
        val sibling = SnapBox(x = goldenX - 20.0, y = -500.0, width = 10.0, height = 40.0)
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
        val sibling = SnapBox(x = -500.0, y = 240.0, width = 40.0, height = 40.0) // centerY = 260
        val moved = SnapBox(x = 596.0, y = 238.0, width = 40.0, height = 40.0) // centerX=616, centerY=258
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
            SnapBox(x = goldenX - 26.0, y = 50.0, width = 40.0, height = 40.0), // center = goldenX - 6
            containers = listOf(wideFrame), siblings = emptyList(), threshold = 6.0,
        )
        assertClose(6.0, onEdge.dx)
        val justOver = computeAnchors(
            SnapBox(x = goldenX - 27.0, y = 50.0, width = 40.0, height = 40.0), // center = goldenX - 7
            containers = listOf(wideFrame), siblings = emptyList(), threshold = 6.0,
        )
        assertClose(0.0, justOver.dx)
        assertTrue(justOver.guides.isEmpty())
    }
}
