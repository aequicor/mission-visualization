package io.aequicor.visualization.subsystems.typography.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the pure [wavePoints] geometry of a [io.aequicor.visualization.subsystems.typography.DecorationStyle.Wavy]
 * decoration — no canvas or layout required.
 */
class WaveGeometryTest {

    @Test
    fun emptyWhenLeftNotBeforeRight() {
        assertTrue(wavePoints(left = 10f, right = 10f, baselineY = 0f, amplitude = 2f, wavelength = 6f).isEmpty())
        assertTrue(wavePoints(left = 20f, right = 5f, baselineY = 0f, amplitude = 2f, wavelength = 6f).isEmpty())
    }

    @Test
    fun endXsAreNonDecreasingAndLastReachesRight() {
        val right = 37f
        val segments = wavePoints(left = 0f, right = right, baselineY = 12f, amplitude = 2f, wavelength = 6f)

        assertTrue(segments.isNotEmpty())
        segments.zipWithNext().forEach { (a, b) ->
            assertTrue(b.endX >= a.endX, "endX must not decrease: ${a.endX} then ${b.endX}")
        }
        assertEquals(right, segments.last().endX)
    }

    @Test
    fun controlYAlternatesStartingAboveBaseline() {
        val baselineY = 12f
        val amplitude = 2f
        val segments = wavePoints(left = 0f, right = 40f, baselineY = baselineY, amplitude = amplitude, wavelength = 10f)

        assertTrue(segments.first().controlY < baselineY, "first control point must sit above the baseline")
        segments.forEachIndexed { index, segment ->
            val expected = if (index % 2 == 0) baselineY - amplitude * 2 else baselineY + amplitude * 2
            assertEquals(expected, segment.controlY, "control Y must alternate above/below at index $index")
            assertEquals(baselineY, segment.endY)
        }
    }

    @Test
    fun segmentCountMatchesWavelengthStepsForDivisibleWidth() {
        val segments = wavePoints(left = 0f, right = 40f, baselineY = 0f, amplitude = 2f, wavelength = 10f)

        assertEquals(4, segments.size)
    }
}
