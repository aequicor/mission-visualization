package io.aequicor.visualization.subsystems.figures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgDocumentTest {

    @Test
    fun parsesViewBoxAndSinglePath() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" width="24" height="24">""" +
            """<path d="M480-80 200-360 480-640 760-360Z"/></svg>"""
        val graphic = assertNotNull(parseSvgDocument(svg))
        assertEquals(DesignViewBox(0.0, -960.0, 960.0, 960.0), graphic.viewBox)
        assertEquals(1, graphic.paths.size)
        assertEquals("nonzero", graphic.paths[0].windingRule)
        assertEquals("M480-80 200-360 480-640 760-360Z", graphic.paths[0].d)
    }

    @Test
    fun concatenatesMultiplePathsAndReadsFillRule() {
        val svg = """<svg viewBox="0 0 100 100">""" +
            """<path d="M0 0 H100 V100 Z"/>""" +
            """<path fill-rule="evenodd" d="M25 25 H75 V75 Z"/></svg>"""
        val graphic = assertNotNull(parseSvgDocument(svg))
        assertEquals(2, graphic.paths.size)
        assertEquals("nonzero", graphic.paths[0].windingRule)
        assertEquals("evenodd", graphic.paths[1].windingRule)
    }

    @Test
    fun fallsBackToWidthHeightWhenNoViewBox() {
        val svg = """<svg width="48" height="32"><path d="M0 0 H48 V32 Z"/></svg>"""
        val graphic = assertNotNull(parseSvgDocument(svg))
        assertEquals(DesignViewBox(0.0, 0.0, 48.0, 32.0), graphic.viewBox)
    }

    @Test
    fun returnsNullWhenNoPath() {
        assertNull(parseSvgDocument("""<svg viewBox="0 0 10 10"><rect x="0" y="0"/></svg>"""))
    }

    @Test
    fun graphicGeometryLowersParsedSvgToDrawableCommands() {
        val svg = """<svg viewBox="0 0 100 100"><path d="M0 0 H100 V100 H0 Z"/></svg>"""
        val graphic = assertNotNull(parseSvgDocument(svg))
        val geometry = assertNotNull(graphicGeometry(graphic))
        assertTrue(geometry.commands.isNotEmpty())
        assertEquals(RectD(0.0, 0.0, 100.0, 100.0), geometry.sourceViewBox)
        assertEquals(PathFillRule.NonZero, geometry.fillRule)
    }
}
