package io.aequicor.visualization.designdoc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import io.aequicor.visualization.engine.ir.layout.DesignTextMeasurer
import io.aequicor.visualization.engine.ir.layout.MeasuredText
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import io.aequicor.visualization.engine.ir.resolve.ResolvedText
import io.aequicor.visualization.engine.ir.resolve.ResolvedTextStyle
import kotlin.math.ceil

/**
 * Bridges the pure layout engine to Compose text measurement. Document px map 1:1
 * to draw px (the artboard canvas applies the zoom transform), so font sizes are
 * converted with `toSp()` against the current density.
 */
class ComposeDesignTextMeasurer(
    private val textMeasurer: TextMeasurer,
    private val density: Density,
) : DesignTextMeasurer {
    override fun measure(text: ResolvedText, maxWidth: Double?): MeasuredText {
        val result = measureResolvedText(
            measurer = textMeasurer,
            density = density,
            text = text,
            color = Color.Black,
            maxWidth = maxWidth,
        )
        return MeasuredText(result.size.width.toDouble(), result.size.height.toDouble())
    }
}

internal fun measureResolvedText(
    measurer: TextMeasurer,
    density: Density,
    text: ResolvedText,
    color: Color,
    maxWidth: Double? = null,
    exactWidth: Boolean = false,
): TextLayoutResult {
    val truncate = text.truncate
    val constraints = when {
        maxWidth == null -> Constraints()
        exactWidth -> {
            val width = ceil(maxWidth).toInt().coerceAtLeast(0)
            Constraints(minWidth = width, maxWidth = width)
        }
        else -> Constraints(maxWidth = ceil(maxWidth).toInt().coerceAtLeast(0))
    }
    return measurer.measure(
        text = text.toAnnotatedString(density, color),
        style = text.style.toTextStyle(density, color),
        overflow = if (truncate?.ellipsis == true) TextOverflow.Ellipsis else TextOverflow.Clip,
        softWrap = maxWidth != null,
        maxLines = truncate?.maxLines ?: Int.MAX_VALUE,
        constraints = constraints,
    )
}

internal fun ResolvedText.toAnnotatedString(density: Density, baseColor: Color): AnnotatedString {
    val transformed = style.textCase.apply(characters)
    return buildAnnotatedString {
        append(transformed)
        ranges.forEach { range ->
            val start = range.start.coerceIn(0, transformed.length)
            val end = range.end.coerceIn(start, transformed.length)
            if (end > start) {
                addStyle(range.style.toSpanStyle(density, range.fills.firstSolidColor() ?: baseColor), start, end)
            }
        }
    }
}

internal fun List<ResolvedPaint>?.firstSolidColor(): Color? =
    this?.filterIsInstance<ResolvedPaint.Solid>()?.firstOrNull()?.let { solid ->
        solid.color.toComposeColor().copy(
            alpha = (solid.color.alpha / 255f) * solid.opacity.toFloat(),
        )
    }

internal fun DesignColor.toComposeColor(): Color =
    Color(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
        alpha = alpha / 255f,
    )

internal fun ResolvedTextStyle.toTextStyle(density: Density, color: Color): TextStyle =
    TextStyle(
        color = color,
        fontSize = fontSize.toSp(density),
        fontWeight = FontWeight(fontWeight.coerceIn(1, 1000)),
        letterSpacing = if (letterSpacing != 0.0) letterSpacing.toSp(density) else TextUnit.Unspecified,
        lineHeight = if (lineHeight > 0.0) lineHeight.toSp(density) else TextUnit.Unspecified,
        textAlign = when (textAlignHorizontal) {
            TextAlignHorizontal.Left -> TextAlign.Left
            TextAlignHorizontal.Center -> TextAlign.Center
            TextAlignHorizontal.Right -> TextAlign.Right
            TextAlignHorizontal.Justified -> TextAlign.Justify
        },
        textDecoration = when (textDecoration) {
            TextDecorationKind.None -> TextDecoration.None
            TextDecorationKind.Underline -> TextDecoration.Underline
            TextDecorationKind.Strikethrough -> TextDecoration.LineThrough
        },
    )

private fun ResolvedTextStyle.toSpanStyle(density: Density, color: Color): SpanStyle =
    SpanStyle(
        color = color,
        fontSize = fontSize.toSp(density),
        fontWeight = FontWeight(fontWeight.coerceIn(1, 1000)),
        letterSpacing = if (letterSpacing != 0.0) letterSpacing.toSp(density) else TextUnit.Unspecified,
        textDecoration = when (textDecoration) {
            TextDecorationKind.None -> null
            TextDecorationKind.Underline -> TextDecoration.Underline
            TextDecorationKind.Strikethrough -> TextDecoration.LineThrough
        },
    )

private fun Double.toSp(density: Density): TextUnit =
    with(density) { toFloat().toSp() }

private fun TextCase.apply(value: String): String =
    when (this) {
        TextCase.None -> value
        TextCase.Upper -> value.uppercase()
        TextCase.Lower -> value.lowercase()
        TextCase.Title -> value.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
