package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MVP vector-anchor parsing and translation over absolute SVG path commands. */
class VectorPathEditingTest {

    @Test
    fun parsesMoveAndLineAnchors() {
        val anchors = vectorAnchors("M 0 0 L 10 0 L 10 10 Z")
        assertEquals(3, anchors.size)
        assertEquals(0.0 to 0.0, anchors[0].x to anchors[0].y)
        assertEquals(10.0 to 0.0, anchors[1].x to anchors[1].y)
        assertEquals(10.0 to 10.0, anchors[2].x to anchors[2].y)
    }

    @Test
    fun translatesSelectedAnchorOnly() {
        val moved = translateSvgPoint("M 0 0 L 10 0 L 10 10 Z", anchorIndex = 1, dx = 5.0, dy = -3.0)
        assertTrue(moved != null)
        val anchors = vectorAnchors(moved!!)
        assertEquals(0.0 to 0.0, anchors[0].x to anchors[0].y, "first anchor unchanged")
        assertEquals(15.0 to (-3.0), anchors[1].x to anchors[1].y, "second anchor translated")
        assertEquals(10.0 to 10.0, anchors[2].x to anchors[2].y, "third anchor unchanged")
    }

    @Test
    fun anchorForCubicCurveIsTheEndpoint() {
        // C control1 control2 end -> only the endpoint (30,0) is a movable anchor.
        val anchors = vectorAnchors("M 0 0 C 10 10 20 10 30 0")
        assertEquals(2, anchors.size)
        assertEquals(30.0 to 0.0, anchors[1].x to anchors[1].y)
    }

    @Test
    fun outOfRangeAnchorReturnsNull() {
        assertNull(translateSvgPoint("M 0 0 L 10 0", anchorIndex = 9, dx = 1.0, dy = 1.0))
    }

    @Test
    fun handlesScientificNotationCoordinates() {
        val anchors = vectorAnchors("M 1e2 2E1 L 3 4")
        assertEquals(2, anchors.size)
        assertEquals(100.0 to 20.0, anchors[0].x to anchors[0].y, "exponent not split as a command")
    }

    @Test
    fun handlesPackedDecimalCoordinates() {
        val anchors = vectorAnchors("M .5.5 L 1.5.25")
        assertEquals(2, anchors.size)
        assertEquals(0.5 to 0.5, anchors[0].x to anchors[0].y)
        assertEquals(1.5 to 0.25, anchors[1].x to anchors[1].y, "packed decimals split into two numbers")
    }
}
