package io.aequicor.visualization.editor.ui

import io.aequicor.visualization.editor.presentation.BoundsBox
import io.aequicor.visualization.editor.presentation.ResizeHandle
import io.aequicor.visualization.editor.presentation.computeResize
import io.aequicor.visualization.subsystems.anchoring.ResizeSnapState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResizeSnappingTest {

    private val baseline = BoundsBox(x = 0.0, y = 0.0, width = 100.0, height = 50.0)

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }

    @Test
    fun aspectLockedCornerSnapsHorizontalEdgeAndKeepsRatio() {
        val snapped = snapResizeDeltas(
            baseline = baseline,
            handle = ResizeHandle.BottomRight,
            rawDocDx = 20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 124.0, y = 0.0, width = 40.0, height = 10.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = baseline.width,
            baseHeight = baseline.height,
            handle = ResizeHandle.BottomRight,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(124.0, result.width)
        assertClose(62.0, result.height)
        assertClose(2.0, result.width / result.height)
        assertEquals(1, output.guides.size)
        assertClose(124.0, output.guides.single().line.x1)
        assertClose(62.0, output.guides.single().line.y2)
    }

    @Test
    fun aspectLockedCornerProjectsVerticalSnapOntoWidth() {
        val snapped = snapResizeDeltas(
            baseline = baseline,
            handle = ResizeHandle.BottomRight,
            rawDocDx = 20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 500.0, y = 64.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = baseline.width,
            baseHeight = baseline.height,
            handle = ResizeHandle.BottomRight,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(128.0, result.width)
        assertClose(64.0, result.height)
        assertClose(2.0, result.width / result.height)
        assertEquals(1, output.guides.size)
        assertClose(64.0, output.guides.single().line.y1)
    }

    @Test
    fun aspectLockedCornerKeepsOnlyClosestAxisWhenBothCatch() {
        val snapped = snapResizeDeltas(
            baseline = baseline,
            handle = ResizeHandle.BottomRight,
            rawDocDx = 20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 123.0, y = 65.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = baseline.width,
            baseHeight = baseline.height,
            handle = ResizeHandle.BottomRight,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(123.0, result.width)
        assertClose(61.5, result.height)
        assertEquals(1, output.guides.size)
        assertTrue(output.guides.single().line.x1 == output.guides.single().line.x2)
        assertClose(123.0, assertNotNull(output.state.latchX))
        assertNull(output.state.latchY)
    }

    @Test
    fun aspectLockedTopLeftProjectsVerticalSnapWithCorrectSigns() {
        val positionedBaseline = BoundsBox(x = 100.0, y = 100.0, width = 100.0, height = 50.0)
        val snapped = snapResizeDeltas(
            baseline = positionedBaseline,
            handle = ResizeHandle.TopLeft,
            rawDocDx = -20f,
            rawDocDy = -10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 500.0, y = 46.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = positionedBaseline.width,
            baseHeight = positionedBaseline.height,
            handle = ResizeHandle.TopLeft,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(128.0, result.width)
        assertClose(64.0, result.height)
        assertClose(72.0, positionedBaseline.x + result.dx)
        assertClose(86.0, positionedBaseline.y + result.dy)
        assertEquals(1, output.guides.size)
        assertClose(86.0, output.guides.single().line.y1)
    }

    @Test
    fun aspectLockedBottomLeftProjectsVerticalSnapWithOppositeAxisSigns() {
        val positionedBaseline = BoundsBox(x = 100.0, y = 100.0, width = 100.0, height = 50.0)
        val snapped = snapResizeDeltas(
            baseline = positionedBaseline,
            handle = ResizeHandle.BottomLeft,
            rawDocDx = -20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 500.0, y = 164.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = positionedBaseline.width,
            baseHeight = positionedBaseline.height,
            handle = ResizeHandle.BottomLeft,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(128.0, result.width)
        assertClose(64.0, result.height)
        assertClose(72.0, positionedBaseline.x + result.dx)
        assertClose(164.0, positionedBaseline.y + result.dy + result.height)
        assertClose(164.0, output.guides.single().line.y1)
    }

    @Test
    fun aspectLockedBottomLeftSnapsItsLeftEdge() {
        val positionedBaseline = BoundsBox(x = 100.0, y = 100.0, width = 100.0, height = 50.0)
        val snapped = snapResizeDeltas(
            baseline = positionedBaseline,
            handle = ResizeHandle.BottomLeft,
            rawDocDx = -20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 76.0, y = 500.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = positionedBaseline.width,
            baseHeight = positionedBaseline.height,
            handle = ResizeHandle.BottomLeft,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(124.0, result.width)
        assertClose(62.0, result.height)
        assertClose(76.0, positionedBaseline.x + result.dx)
        assertClose(76.0, output.guides.single().line.x1)
    }

    @Test
    fun expiredPriorLatchDoesNotOverrideCloserOtherAxis() {
        val snapped = snapResizeDeltas(
            baseline = baseline,
            handle = ResizeHandle.BottomRight,
            rawDocDx = 20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 125.0, y = 61.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState(latchX = 1_000.0),
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = baseline.width,
            baseHeight = baseline.height,
            handle = ResizeHandle.BottomRight,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            lockRatio = true,
        )

        assertClose(122.0, result.width)
        assertClose(61.0, result.height)
        assertNull(output.state.latchX)
        assertClose(61.0, assertNotNull(output.state.latchY))
        assertTrue(output.guides.single().line.y1 == output.guides.single().line.y2)
    }

    @Test
    fun infeasibleClosestAxisFallsBackToFeasibleOtherAxis() {
        val snapped = snapResizeDeltas(
            baseline = baseline,
            handle = ResizeHandle.BottomRight,
            rawDocDx = 20f,
            rawDocDy = 10f,
            lockRatio = true,
            context = SnapContext(
                containers = emptyList(),
                siblings = listOf(BoundsBox(x = 122.0, y = 57.0, width = 40.0, height = 40.0)),
            ),
            catchPx = 7.0,
            releasePx = 16.0,
            prior = ResizeSnapState.None,
            maxWidth = 120.0,
        )

        val output = assertNotNull(snapped.output)
        val result = computeResize(
            baseWidth = baseline.width,
            baseHeight = baseline.height,
            handle = ResizeHandle.BottomRight,
            docDx = snapped.docDx.toDouble(),
            docDy = snapped.docDy.toDouble(),
            maxWidth = 120.0,
            lockRatio = true,
        )

        assertClose(114.0, result.width)
        assertClose(57.0, result.height)
        assertNull(output.state.latchX)
        assertClose(57.0, assertNotNull(output.state.latchY))
    }
}
