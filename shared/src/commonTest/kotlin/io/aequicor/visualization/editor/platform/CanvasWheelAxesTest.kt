package io.aequicor.visualization.editor.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasWheelAxesTest {

    @Test
    fun composeDesktopShiftHorizontalDeltaKeepsItsXAxis() {
        assertEquals(
            CanvasWheelAxes(x = 0.4f, y = 0f),
            canvasWheelPanAxes(deltaX = 0.4f, deltaY = 0f, shiftPressed = true),
        )
    }

    @Test
    fun shiftWheelYAxisFallsBackToHorizontal() {
        assertEquals(
            CanvasWheelAxes(x = -1f, y = 0f),
            canvasWheelPanAxes(deltaX = 0f, deltaY = -1f, shiftPressed = true),
        )
    }

    @Test
    fun unmodifiedTrackpadKeepsBothAxes() {
        assertEquals(
            CanvasWheelAxes(x = 0.25f, y = -0.5f),
            canvasWheelPanAxes(deltaX = 0.25f, deltaY = -0.5f, shiftPressed = false),
        )
    }

    @Test
    fun zoomUsesTheNonZeroDominantAxis() {
        assertEquals(-0.6f, canvasWheelZoomAxis(deltaX = 0f, deltaY = -0.6f))
        assertEquals(0.3f, canvasWheelZoomAxis(deltaX = 0.3f, deltaY = 0f))
    }

    @Test
    fun nativeMagnificationConvertsToRelativeFactorAndClampsOutliers() {
        assertEquals(1.1f, canvasMagnificationFactor(0.1f))
        assertEquals(0.5f, canvasMagnificationFactor(-4f))
        assertEquals(2f, canvasMagnificationFactor(4f))
    }
}
