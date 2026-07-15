package io.aequicor.visualization.editor.platform

import io.aequicor.visualization.editor.presentation.zoomFactorForScroll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasWheelInputTest {

    @Test
    fun desktopWheelTickKeepsWindowsDistanceAndSoftensMacTrackpadMotion() {
        assertEquals(128f, desktopCanvasWheelPanPixels(scrollUnits = 1f, density = 2f, macOs = false))
        assertEquals(-32f, desktopCanvasWheelPanPixels(scrollUnits = -0.25f, density = 2f, macOs = false))
        assertEquals(40f, desktopCanvasWheelPanPixels(scrollUnits = 1f, density = 2f, macOs = true))
        assertEquals(-10f, desktopCanvasWheelPanPixels(scrollUnits = -0.25f, density = 2f, macOs = true))
    }

    @Test
    fun desktopWheelTickProducesResponsiveZoomStep() {
        val factor = zoomFactorForScroll(platformCanvasWheelZoomUnits(1f))

        assertTrue(factor in 1.15f..1.17f, "one desktop wheel tick should zoom by about 16%")
    }
}
