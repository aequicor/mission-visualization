package io.aequicor.visualization.subsystems.diagrams.compose

import io.aequicor.visualization.subsystems.diagrams.text.DiagramTextMeasurer
import io.aequicor.visualization.subsystems.diagrams.text.DiagramTextStyle
import io.aequicor.visualization.subsystems.diagrams.text.MeasuredDiagramText
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer

/**
 * [DiagramTextMeasurer] backed by real font metrics, adapting the typography measurer the
 * canvas already draws with — so a shape hugged to its text matches what gets rendered.
 *
 * The seam mirrors `SceneRenderer`'s: the pure core declares the contract, the Compose layer
 * supplies the implementation.
 */
class ComposeDiagramTextMeasurer(
    private val measurer: ComposeTypographyMeasurer,
) : DiagramTextMeasurer {

    override fun measure(
        text: String,
        style: DiagramTextStyle,
        maxWidth: Double?,
    ): MeasuredDiagramText {
        val rich = RichText(
            text = text,
            base = TypographyStyle(
                fontSize = style.fontSize,
                fontWeight = style.fontWeight,
                italic = style.italic,
            ),
        )
        // NEVER pass exactWidth here. It pins the paragraph to min == max constraints, so the
        // reported width becomes the box width rather than the text's own — a hug computed from
        // that would be a fixed point on garbage (drawDiagramLabel paints with exactWidth for
        // alignment; measuring is the opposite need).
        val measured = measurer.measure(rich, maxWidth)
        return MeasuredDiagramText(
            width = measured.width,
            height = measured.height,
            lines = measured.lines.map { line ->
                text.substring(
                    line.start.coerceIn(0, text.length),
                    line.end.coerceIn(line.start.coerceIn(0, text.length), text.length),
                )
            },
        )
    }
}
