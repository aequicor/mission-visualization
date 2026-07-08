package io.aequicor.visualization.engine.ir.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignColorHsvTest {

    @Test
    fun pureRedMapsToHueZeroFullSaturationFullValue() {
        val hsva = DesignColor(0xFFFF0000).toHsva()
        assertEquals(0f, hsva.hue)
        assertEquals(1f, hsva.saturation)
        assertEquals(1f, hsva.value)
        assertEquals(1f, hsva.alpha)
    }

    @Test
    fun primariesMapToTheirHues() {
        assertEquals(120f, DesignColor(0xFF00FF00).toHsva().hue)
        assertEquals(240f, DesignColor(0xFF0000FF).toHsva().hue)
    }

    @Test
    fun whiteIsSaturationZeroValueOne() {
        val hsva = DesignColor(0xFFFFFFFF).toHsva()
        assertEquals(0f, hsva.saturation)
        assertEquals(1f, hsva.value)
    }

    @Test
    fun blackIsValueZero() {
        assertEquals(0f, DesignColor(0xFF000000).toHsva().value)
    }

    @Test
    fun midGreyIsSaturationZero() {
        val hsva = DesignColor(0xFF808080).toHsva()
        assertEquals(0f, hsva.saturation)
        assertTrue(abs(hsva.value - 128f / 255f) < 1e-6f)
    }

    @Test
    fun roundTripReproducesOriginalArgbExactly() {
        // Colors whose 8-bit HSV round-trips exactly (primaries, secondaries, greys, alpha != FF).
        val exact = listOf(
            0xFFFF0000, // red
            0xFF00FF00, // green
            0xFF0000FF, // blue
            0xFFFFFF00, // yellow
            0xFF00FFFF, // cyan
            0xFFFF00FF, // magenta
            0xFFFFFFFF, // white
            0xFF000000, // black
            0xFF808080, // mid grey
            0x80FF0000, // half-alpha red
            0x00112233, // fully transparent, non-zero rgb
            0xC0336699, // arbitrary with alpha
        )
        for (argb in exact) {
            val color = DesignColor(argb)
            val roundTripped = color.toHsva().toDesignColor()
            assertEquals(argb, roundTripped.argb, "round-trip changed 0x${argb.toString(16)}")
        }
    }

    @Test
    fun roundTripKeepsArbitraryChannelsWithinOne() {
        val arbitrary = listOf(
            0xFF123456,
            0xFFABCDEF,
            0xFFFF8800,
            0x7F0A9B3C,
            0xFF2E7D32,
            0xFFD81B60,
        )
        for (argb in arbitrary) {
            val original = DesignColor(argb)
            val result = original.toHsva().toDesignColor()
            assertTrue(abs(original.red - result.red) <= 1, "red drift for 0x${argb.toString(16)}")
            assertTrue(abs(original.green - result.green) <= 1, "green drift for 0x${argb.toString(16)}")
            assertTrue(abs(original.blue - result.blue) <= 1, "blue drift for 0x${argb.toString(16)}")
        }
    }

    @Test
    fun alphaIsPreservedThroughRoundTrip() {
        for (a in listOf(0x00, 0x33, 0x80, 0xCC, 0xFF)) {
            val argb = (a.toLong() shl 24) or 0x3366CC
            val result = DesignColor(argb).toHsva().toDesignColor()
            assertEquals(a, result.alpha, "alpha lost for alpha=0x${a.toString(16)}")
        }
    }
}
