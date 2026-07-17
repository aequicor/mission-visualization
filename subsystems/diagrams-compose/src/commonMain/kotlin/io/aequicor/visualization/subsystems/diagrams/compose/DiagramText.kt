package io.aequicor.visualization.subsystems.diagrams.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.path.DiagramRect
import io.aequicor.visualization.subsystems.typography.AlignHorizontal
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.StyleSpan
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import io.aequicor.visualization.subsystems.typography.compose.ComposeTypographyMeasurer
import io.aequicor.visualization.subsystems.typography.compose.RichTextFill
import io.aequicor.visualization.subsystems.typography.compose.drawRichText

/** Default label font size, document px. Public so inline editors can match the render. */
const val DIAGRAM_LABEL_FONT_SIZE = 13.0

/** Smaller font for member rows / attributes / lane titles / table cells. */
const val DIAGRAM_DETAIL_FONT_SIZE = 12.0

/**
 * Lowers a diagram label into typography [RichText]. When [DiagramLabel.markdown] is set,
 * a minimal inline dialect is interpreted: `**bold**`, `*italic*` / `_italic_` and
 * `` `code` `` become style spans (markers stripped); everything else is literal text.
 */
fun DiagramLabel.toRichText(base: TypographyStyle = TypographyStyle()): RichText =
    if (markdown) {
        val parsed = parseInlineMarkdown(text)
        RichText(text = parsed.text, base = base, spans = parsed.spans)
    } else {
        RichText(text = text, base = base)
    }

internal data class ParsedMarkdown(
    val text: String,
    val spans: List<StyleSpan>,
)

/** Single-pass scanner for the `**` / `*` / `_` / `` ` `` inline markers. */
internal fun parseInlineMarkdown(source: String): ParsedMarkdown {
    val out = StringBuilder()
    val spans = mutableListOf<StyleSpan>()
    var index = 0

    fun scanTo(marker: String, from: Int): Int = source.indexOf(marker, from)

    while (index < source.length) {
        val remaining = source.length - index
        when {
            remaining >= 2 && source.startsWith("**", index) -> {
                val close = scanTo("**", index + 2)
                if (close > index + 2 - 1 && close != -1) {
                    val start = out.length
                    out.append(source, index + 2, close)
                    spans += StyleSpan(start, out.length, TypographyStyle(fontWeight = 700))
                    index = close + 2
                } else {
                    out.append(source[index]); index++
                }
            }

            source[index] == '*' || source[index] == '_' -> {
                val marker = source[index].toString()
                val close = scanTo(marker, index + 1)
                if (close != -1 && close > index + 1) {
                    val start = out.length
                    out.append(source, index + 1, close)
                    spans += StyleSpan(start, out.length, TypographyStyle(italic = true))
                    index = close + 1
                } else {
                    out.append(source[index]); index++
                }
            }

            source[index] == '`' -> {
                val close = scanTo("`", index + 1)
                if (close != -1 && close > index + 1) {
                    val start = out.length
                    out.append(source, index + 1, close)
                    spans += StyleSpan(
                        start,
                        out.length,
                        TypographyStyle(fontFamily = "JetBrains Mono"),
                    )
                    index = close + 1
                } else {
                    out.append(source[index]); index++
                }
            }

            else -> {
                out.append(source[index]); index++
            }
        }
    }
    return ParsedMarkdown(out.toString(), spans)
}

/** Vertical placement of a label inside its box. */
internal enum class LabelVerticalAlign { TOP, CENTER, BOTTOM }

/**
 * Measures and draws a [DiagramLabel] inside [box]: wraps at the box width,
 * horizontal alignment via typography, vertical via [verticalAlign].
 */
internal fun DrawScope.drawDiagramLabel(
    measurer: ComposeTypographyMeasurer,
    label: DiagramLabel,
    box: DiagramRect,
    color: Color,
    fontSize: Double = DIAGRAM_LABEL_FONT_SIZE,
    align: AlignHorizontal = AlignHorizontal.Center,
    verticalAlign: LabelVerticalAlign = LabelVerticalAlign.CENTER,
    fontWeight: Int? = null,
    italic: Boolean? = null,
) {
    if (label.text.isEmpty() || box.width <= 1.0) return
    val base = TypographyStyle(
        fontSize = fontSize,
        alignHorizontal = align,
        fontWeight = fontWeight,
        italic = italic,
    )
    val laidOut = measurer.layout(
        rich = label.toRichText(base),
        maxWidth = box.width,
        fill = RichTextFill(color),
        exactWidth = true,
    )
    val y = when (verticalAlign) {
        LabelVerticalAlign.TOP -> box.top
        LabelVerticalAlign.CENTER -> box.top + (box.height - laidOut.measured.height) / 2.0
        LabelVerticalAlign.BOTTOM -> box.bottom - laidOut.measured.height
    }
    // Clip to the label box so overflowing text never bleeds into neighboring shapes.
    clipRect(
        left = box.left.toFloat(),
        top = box.top.toFloat(),
        right = box.right.toFloat(),
        bottom = box.bottom.toFloat(),
    ) {
        drawRichText(laidOut, topLeft = Offset(box.x.toFloat(), y.toFloat()))
    }
}

/**
 * Draws a free-standing label centered on a point (edge labels), with an optional
 * readability plate behind the text.
 */
internal fun DrawScope.drawCenteredLabel(
    measurer: ComposeTypographyMeasurer,
    label: DiagramLabel,
    centerX: Double,
    centerY: Double,
    color: Color,
    plateColor: Color?,
    fontSize: Double = DIAGRAM_DETAIL_FONT_SIZE,
) {
    if (label.text.isEmpty()) return
    val base = TypographyStyle(fontSize = fontSize, alignHorizontal = AlignHorizontal.Center)
    val laidOut = measurer.layout(
        rich = label.toRichText(base),
        maxWidth = null,
        fill = RichTextFill(color),
    )
    val width = laidOut.measured.width
    val height = laidOut.measured.height
    val topLeft = Offset((centerX - width / 2.0).toFloat(), (centerY - height / 2.0).toFloat())
    if (plateColor != null) {
        drawRect(
            color = plateColor,
            topLeft = Offset(topLeft.x - 3f, topLeft.y - 1.5f),
            size = androidx.compose.ui.geometry.Size(width.toFloat() + 6f, height.toFloat() + 3f),
        )
    }
    drawRichText(laidOut, topLeft = topLeft)
}
