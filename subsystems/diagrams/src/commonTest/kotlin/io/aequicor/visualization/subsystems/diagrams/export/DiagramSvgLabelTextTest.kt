package io.aequicor.visualization.subsystems.diagrams.export

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.text.ApproximateDiagramTextMeasurer
import io.aequicor.visualization.subsystems.diagrams.text.DiagramTextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Node captions in the exported SVG. `<text>` does not wrap on its own, so the exporter has to
 * break lines itself — until it did, the audit SVG could not show a label defect at all.
 */
class DiagramSvgLabelTextTest {

    private val longLabel = "Вести контакты собственников и совета дома"

    private fun graphWith(node: DiagramNode) = DiagramGraph().withNode(node)

    private fun tspans(svg: String): List<String> =
        Regex("<tspan[^>]*>([^<]*)</tspan>").findAll(svg).map { it.groupValues[1] }.toList()

    @Test
    fun aCaptionTooWideForItsNodeIsWrappedIntoSeveralTspans() {
        val svg = diagramToSvg(
            graphWith(
                DiagramNode(
                    id = DiagramNodeId("uc"),
                    x = 0.0, y = 0.0, width = 160.0, height = 70.0,
                    payload = UmlUseCaseNode(longLabel),
                ),
            ),
        )

        val lines = tspans(svg)
        assertTrue(lines.size > 1, "a 295px caption in a 160px ellipse must wrap, got: $lines")
        assertEquals(longLabel, lines.joinToString(" ").trim(), "wrapping must not lose words")
    }

    @Test
    fun noExportedLineIsWiderThanItsLabelBox() {
        val node = DiagramNode(
            id = DiagramNodeId("uc"),
            x = 0.0, y = 0.0, width = 300.0, height = 160.0,
            payload = UmlUseCaseNode(longLabel),
        )
        val svg = diagramToSvg(graphWith(node))

        // The ellipse's inscribed box, minus the caption padding, is what lines must fit into.
        val boxWidth = 300.0 / 1.4142135623730951 - 16.0
        val measurer = ApproximateDiagramTextMeasurer()
        tspans(svg).forEach { line ->
            val width = measurer.measure(line, DiagramTextStyle(fontSize = 12.0)).width
            assertTrue(width <= boxWidth + 0.001, "line '$line' is ${width}px, box is ${boxWidth}px")
        }
    }

    @Test
    fun aShortCaptionStaysOnOneLine() {
        val svg = diagramToSvg(
            graphWith(
                DiagramNode(
                    id = DiagramNodeId("uc"),
                    x = 0.0, y = 0.0, width = 200.0, height = 120.0,
                    payload = UmlUseCaseNode("Submit"),
                ),
            ),
        )

        assertEquals(listOf("Submit"), tspans(svg))
    }

    @Test
    fun typedPayloadCaptionsComeFromThePayloadNotAnOrphanedLabel() {
        // The exporter used to prefer node.labels, disagreeing with the canvas, which draws the
        // payload's own name for a use-case.
        val svg = diagramToSvg(
            graphWith(
                DiagramNode(
                    id = DiagramNodeId("uc"),
                    x = 0.0, y = 0.0, width = 240.0, height = 120.0,
                    payload = UmlUseCaseNode("Payload name"),
                    labels = listOf(DiagramLabel("orphan")),
                ),
            ),
        )

        assertTrue("Payload name" in svg, "the canvas draws payload.name; the export must agree")
        assertTrue("orphan" !in svg, "an orphaned node label must not be exported")
    }

    @Test
    fun basicShapeCaptionsStillComeFromTheNodeLabel() {
        val svg = diagramToSvg(
            graphWith(
                DiagramNode(
                    id = DiagramNodeId("r"),
                    x = 0.0, y = 0.0, width = 200.0, height = 80.0,
                    payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE),
                    labels = listOf(DiagramLabel("Shape caption")),
                ),
            ),
        )

        assertEquals(listOf("Shape caption"), tspans(svg))
    }
}
