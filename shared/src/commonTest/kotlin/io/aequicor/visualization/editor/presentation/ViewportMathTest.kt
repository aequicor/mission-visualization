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

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.0001) {
        assertTrue(abs(expected - actual) <= epsilon, "expected <$expected>, actual <$actual>")
    }
}
