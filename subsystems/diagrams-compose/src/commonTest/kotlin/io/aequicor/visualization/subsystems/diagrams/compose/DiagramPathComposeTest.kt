package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.ui.graphics.Color
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.diagramPath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagramPathComposeTest {

    @Test
    fun emptyDiagramPathProducesEmptyComposePath() {
        assertTrue(DiagramPath.Empty.toComposePath().isEmpty)
    }

    @Test
    fun polylineAndCurvesConvertWithMatchingBounds() {
        val path = diagramPath {
            moveTo(0.0, 0.0)
            lineTo(100.0, 0.0)
            quadTo(110.0, 25.0, 100.0, 50.0)
            cubicTo(80.0, 70.0, 20.0, 70.0, 0.0, 50.0)
            close()
        }.toComposePath()

        assertTrue(!path.isEmpty)
        val bounds = path.getBounds()
        assertTrue(abs(bounds.left - 0f) < 1f, "left=${bounds.left}")
        assertTrue(bounds.right >= 100f && bounds.right <= 111f, "right=${bounds.right}")
        assertTrue(abs(bounds.top - 0f) < 1f, "top=${bounds.top}")
        assertTrue(bounds.bottom in 50f..71f, "bottom=${bounds.bottom}")
    }

    @Test
    fun svgArcLowersToCurvesCoveringTheEllipse() {
        // Full circle of radius 50 around (50, 50) as two half-arcs.
        val path = diagramPath {
            moveTo(0.0, 50.0)
            arcTo(radiusX = 50.0, radiusY = 50.0, sweep = true, endX = 100.0, endY = 50.0)
            arcTo(radiusX = 50.0, radiusY = 50.0, sweep = true, endX = 0.0, endY = 50.0)
            close()
        }.toComposePath()

        val bounds = path.getBounds()
        assertTrue(abs(bounds.left - 0f) < 0.6f, "left=${bounds.left}")
        assertTrue(abs(bounds.right - 100f) < 0.6f, "right=${bounds.right}")
        assertTrue(abs(bounds.top - 0f) < 0.6f, "top=${bounds.top}")
        assertTrue(abs(bounds.bottom - 100f) < 0.6f, "bottom=${bounds.bottom}")
    }

    @Test
    fun degenerateArcFallsBackToLine() {
        val path = diagramPath {
            moveTo(0.0, 0.0)
            arcTo(radiusX = 0.0, radiusY = 0.0, endX = 10.0, endY = 10.0)
        }.toComposePath()
        val bounds = path.getBounds()
        assertEquals(10f, bounds.right, absoluteTolerance = 0.01f)
        assertEquals(10f, bounds.bottom, absoluteTolerance = 0.01f)
    }

    @Test
    fun diagramColorMapsArgbChannels() {
        val color = DiagramColor(0x80FF8040u).toComposeColor()
        assertEquals(Color(0x80FF8040.toInt()), color)
    }

    @Test
    fun strokePatternEffects() {
        assertNull(strokePatternEffect(DiagramStrokePattern.SOLID, 2f))
        assertNotNull(strokePatternEffect(DiagramStrokePattern.DASHED, 2f))
        assertNotNull(strokePatternEffect(DiagramStrokePattern.DOTTED, 2f))
    }

    @Test
    fun sketchJitterIsDeterministicAndBounded() {
        val outline = diagramPath {
            moveTo(0.0, 0.0)
            lineTo(50.0, 0.0)
            lineTo(50.0, 30.0)
            close()
        }
        val first = outline.sketched(sketchSeed("node-a"))
        val second = outline.sketched(sketchSeed("node-a"))
        assertEquals(first, second, "same seed must produce identical jitter")
        val other = outline.sketched(sketchSeed("node-b"))
        assertTrue(first != other, "different seeds should differ")
    }

    @Test
    fun markdownParsingBuildsSpans() {
        val parsed = parseInlineMarkdown("plain **bold** and *italic* and `code`")
        assertEquals("plain bold and italic and code", parsed.text)
        assertEquals(3, parsed.spans.size)
        assertEquals(700, parsed.spans[0].style.fontWeight)
        assertEquals(true, parsed.spans[1].style.italic)
        assertEquals("JetBrains Mono", parsed.spans[2].style.fontFamily)
    }

    @Test
    fun shortenPolylineCutsFromBothEnds() {
        val points = listOf(
            io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint(0.0, 0.0),
            io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint(100.0, 0.0),
        )
        val shortened = shortenPolyline(points, 10.0, 20.0)
        assertEquals(10.0, shortened.first().x, absoluteTolerance = 1e-9)
        assertEquals(80.0, shortened.last().x, absoluteTolerance = 1e-9)
    }
}
