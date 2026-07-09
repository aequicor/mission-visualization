package io.aequicor.visualization.editor.presentation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewportMathTest {

    @Test
    fun roundTripScreenAndDocumentCoordinates() {
        val viewport = EditorViewport(zoom = 2f, panOffsetXDp = 10f, panOffsetYDp = -4f)
        val density = 1.5f

        val sx = viewport.toScreenX(120.0, density)
        val sy = viewport.toScreenY(80.0, density)

        assertClose(120.0, viewport.toDocumentX(sx, density))
        assertClose(80.0, viewport.toDocumentY(sy, density))
    }

    @Test
    fun panByScreenDeltaChangesOnlyPanOffsets() {
        val viewport = EditorViewport(zoom = 1.25f, panOffsetXDp = 8f, panOffsetYDp = 10f)
        val next = viewport.panByScreenDelta(deltaXpx = 30f, deltaYpx = -15f, density = 2f)

        assertEquals(1.25f, next.zoom)
        assertClose(23.0, next.panOffsetXDp.toDouble())
        assertClose(2.5, next.panOffsetYDp.toDouble())
    }

    @Test
    fun panToDocumentStartPlacesDocumentCoordinateOnViewportEdge() {
        val viewport = EditorViewport(zoom = 1.25f, panOffsetXDp = 8f, panOffsetYDp = 10f)
            .panToDocumentStartX(240.0)
            .panToDocumentStartY(120.0)
        val density = 2f

        assertClose(240.0, viewport.toDocumentX(0f, density))
        assertClose(120.0, viewport.toDocumentY(0f, density))
    }

    @Test
    fun zoomAroundKeepsFocusedDocumentPointStable() {
        val viewport = EditorViewport(zoom = 1f, panOffsetXDp = 20f, panOffsetYDp = 30f)
        val density = 2f
        val focusX = 200f
        val focusY = 140f
        val beforeX = viewport.toDocumentX(focusX, density)
        val beforeY = viewport.toDocumentY(focusY, density)

        val next = viewport.zoomAround(focusX, focusY, factor = 1.5f, density = density)

        assertEquals(1.5f, next.zoom)
        assertClose(beforeX, next.toDocumentX(focusX, density))
        assertClose(beforeY, next.toDocumentY(focusY, density))
    }

    @Test
    fun scrollZoomFactorRespectsDirectionAndNeutral() {
        assertEquals(1f, zoomFactorForScroll(0f))
        assertTrue(zoomFactorForScroll(1f) > 1f, "scrolling in must magnify")
        assertTrue(zoomFactorForScroll(-1f) < 1f, "scrolling out must shrink")
    }

    @Test
    fun scrollZoomFactorScalesWithMagnitude() {
        // The core smoothness property the old sign-only step lacked: a gentler scroll
        // produces a factor closer to 1 than a firmer one in the same direction.
        val small = zoomFactorForScroll(0.2f)
        val large = zoomFactorForScroll(2f)
        assertTrue(large > small, "a larger scroll must zoom more per event")
        assertTrue(small - 1f < large - 1f)
        // A tiny trackpad delta barely moves the zoom — no more full-notch jumps.
        assertTrue(abs(zoomFactorForScroll(0.05f) - 1f) < 0.01f)
    }

    @Test
    fun mouseWheelNotchZoomsGradually() {
        val notch = zoomFactorForScroll(4f)

        assertTrue(notch in 1.08f..1.12f, "one wheel notch should zoom by about 10%, not 50%")
    }

    @Test
    fun scrollZoomFactorIsSymmetricAndReversible() {
        // exp(a·k)·exp(-a·k) == 1, so an in/out pair round-trips to the same zoom.
        assertClose(1.0, (zoomFactorForScroll(1.3f) * zoomFactorForScroll(-1.3f)).toDouble())
    }

    @Test
    fun scrollZoomFactorClampsOutlierDeltas() {
        val max = WorkspaceLimits.MaxZoomScrollStep
        assertEquals(zoomFactorForScroll(max), zoomFactorForScroll(max * 10f))
        assertEquals(zoomFactorForScroll(-max), zoomFactorForScroll(-max * 10f))
    }

    @Test
    fun canvasScrollbarsAreHiddenWhenContentFitsViewport() {
        val metrics = canvasScrollbarsFor(
            viewport = EditorViewport(zoom = 1f),
            contentBounds = BoundsBox(0.0, 0.0, 300.0, 200.0),
            viewportWidthPx = 600f,
            viewportHeightPx = 400f,
            density = 1f,
            scrollbarThicknessPx = 12f,
            minThumbLengthPx = 32f,
        )

        assertTrue(!metrics.horizontal.visible)
        assertTrue(!metrics.vertical.visible)
    }

    @Test
    fun canvasScrollbarsExposeOnlyOverflowingAxesWithFigmaMargin() {
        val metrics = canvasScrollbarsFor(
            viewport = EditorViewport(zoom = 2f),
            contentBounds = BoundsBox(0.0, 0.0, 600.0, 100.0),
            viewportWidthPx = 800f,
            viewportHeightPx = 400f,
            density = 1f,
            scrollbarThicknessPx = 12f,
            minThumbLengthPx = 32f,
        )

        assertTrue(metrics.horizontal.visible)
        assertTrue(!metrics.vertical.visible)
        assertClose(320.0, metrics.horizontal.thumbLengthPx.toDouble(), epsilon = 0.001)
        assertClose(160.0, metrics.horizontal.thumbOffsetPx.toDouble(), epsilon = 0.001)
    }

    @Test
    fun canvasScrollbarThumbTracksPanOffset() {
        val viewport = EditorViewport(zoom = 1f).panToDocumentStartX(300.0)
        val metrics = canvasScrollbarsFor(
            viewport = viewport,
            contentBounds = BoundsBox(0.0, 0.0, 1000.0, 500.0),
            viewportWidthPx = 400f,
            viewportHeightPx = 500f,
            density = 1f,
            scrollbarThicknessPx = 12f,
            minThumbLengthPx = 32f,
        )

        assertTrue(metrics.horizontal.visible)
        assertClose(142.8571, metrics.horizontal.thumbOffsetPx.toDouble(), epsilon = 0.001)
        assertClose(300.0, metrics.horizontal.documentStartForThumbOffset(metrics.horizontal.thumbOffsetPx))
        assertClose(800.0, metrics.horizontal.documentStartForThumbOffset(metrics.horizontal.maxThumbOffsetPx))
    }

    @Test
    fun canvasScrollbarRangeExpandsToCurrentViewportWhenPannedPastContent() {
        val viewport = EditorViewport(zoom = 1f).panToDocumentStartX(700.0)
        val metrics = canvasScrollbarsFor(
            viewport = viewport,
            contentBounds = BoundsBox(0.0, 0.0, 300.0, 200.0),
            viewportWidthPx = 400f,
            viewportHeightPx = 400f,
            density = 1f,
            scrollbarThicknessPx = 12f,
            minThumbLengthPx = 32f,
        )

        assertTrue(metrics.horizontal.visible)
        assertClose(700.0, metrics.horizontal.documentStartForThumbOffset(metrics.horizontal.maxThumbOffsetPx))
    }

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }
}
