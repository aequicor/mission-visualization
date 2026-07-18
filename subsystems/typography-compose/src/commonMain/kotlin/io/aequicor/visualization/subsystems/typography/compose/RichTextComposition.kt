package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import io.aequicor.visualization.subsystems.typography.AlignHorizontal
import io.aequicor.visualization.subsystems.typography.CaseTransform
import io.aequicor.visualization.subsystems.typography.DecorationColor
import io.aequicor.visualization.subsystems.typography.DecorationKind
import io.aequicor.visualization.subsystems.typography.DecorationSpec
import io.aequicor.visualization.subsystems.typography.DecorationStyle
import io.aequicor.visualization.subsystems.typography.GradientStop
import io.aequicor.visualization.subsystems.typography.LeadingTrim
import io.aequicor.visualization.subsystems.typography.LineHeight
import io.aequicor.visualization.subsystems.typography.ListLayout
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.Rgba
import io.aequicor.visualization.subsystems.typography.SpanAlgebra
import io.aequicor.visualization.subsystems.typography.TextCasing
import io.aequicor.visualization.subsystems.typography.TextFill
import io.aequicor.visualization.subsystems.typography.TextPosition
import io.aequicor.visualization.subsystems.typography.TypographyDefaults
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import io.aequicor.visualization.subsystems.typography.firstSolid
import io.aequicor.visualization.subsystems.typography.fontSizeOrDefault
import io.aequicor.visualization.subsystems.typography.lineHeightPx
import io.aequicor.visualization.subsystems.typography.fontWeightOrDefault
import io.aequicor.visualization.subsystems.typography.letterSpacingPx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The base glyph fill supplied by the caller (usually the node's fill list).
 * Span fills override it per range.
 */
data class RichTextFill(
    val color: Color = Color.Black,
    val brush: Brush? = null,
)

/** Styling applied to [io.aequicor.visualization.subsystems.typography.LinkSpan] ranges. */
val DefaultLinkSpanStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline)

/** Base glyph fill from a subsystem [TextFill] (solid color, or a color + brush for gradients). */
fun TextFill.toRichTextFill(): RichTextFill = when (this) {
    is TextFill.Solid -> RichTextFill(color = color.toColor())
    is TextFill.LinearGradient ->
        RichTextFill(color = stops.firstOrNull()?.color?.toColor() ?: Color.Black, brush = toBrush())
    is TextFill.RadialGradient ->
        RichTextFill(color = stops.firstOrNull()?.color?.toColor() ?: Color.Black, brush = toBrush())
}

/** One rendered paragraph of a [RichText], ready for Compose measurement. */
internal data class ParagraphSpec(
    /** Rendered-string offsets of the paragraph body (newline excluded). */
    val textStart: Int,
    val textEnd: Int,
    val annotated: AnnotatedString,
    val style: TextStyle,
    /** List marker to paint in the indent gutter, empty when not a list. */
    val marker: String,
    /** Horizontal shift of the paragraph (list indent), document px. */
    val indent: Double,
    /** Extra negative shift for a hanging first punctuation character. */
    val hangFirstChar: Boolean,
    /** Decorations that Compose cannot draw natively; offsets local to the paragraph. */
    val customDecorations: List<CustomDecoration>,
)

internal data class CustomDecoration(
    val start: Int,
    val end: Int,
    val spec: DecorationSpec,
    /** Resolved decoration color (Auto already replaced by the glyph color). */
    val color: Color,
    /** Font size of the decorated run, for thickness/offset resolution. */
    val fontSize: Double,
)

internal data class ComposedRichText(
    val rich: RichText,
    val transformed: CaseTransform.Transformed,
    val paragraphs: List<ParagraphSpec>,
    val fill: RichTextFill,
)

/**
 * Lowers a [RichText] into per-paragraph [AnnotatedString]s. Paragraphs are measured
 * independently (see `ComposeTypographyMeasurer`) so paragraph spacing, list markers
 * and hanging punctuation can be applied — Compose has no native support for either.
 */
internal fun composeRichText(
    rich: RichText,
    density: Density,
    fontProvider: FontProvider,
    fill: RichTextFill,
    linkSpanStyle: SpanStyle = DefaultLinkSpanStyle,
): ComposedRichText {
    val casing = rich.base.case ?: TextCasing.None
    val transformed = CaseTransform.apply(rich.text, casing)
    val text = transformed.text
    val spans = CaseTransform.projectSpans(
        SpanAlgebra.normalize(rich.spans, rich.text.length),
        transformed,
    )
    val links = CaseTransform.projectLinks(rich.links, transformed)
    val runs = SpanAlgebra.runsCovering(spans, text.length)

    val baseStyle = rich.base
    val baseSize = baseStyle.fontSizeOrDefault()
    val listIndent = ListLayout.indentPx(rich.list, baseSize)
    val paragraphs = ListLayout.paragraphs(text, rich.list).map { paragraph ->
        val decorations = mutableListOf<CustomDecoration>()
        val annotated = buildAnnotatedString {
            append(text.substring(paragraph.start, paragraph.end))
            runs.forEach { run ->
                val start = maxOf(run.start, paragraph.start) - paragraph.start
                val end = minOf(run.end, paragraph.end) - paragraph.start
                if (end <= start) return@forEach
                val merged = baseStyle.mergedWith(run.style)
                val runColor = run.fills?.firstSolid()?.toColor() ?: fill.color
                merged.decoration?.let { spec ->
                    if (spec.kind != DecorationKind.None && !spec.isNativelyDrawable) {
                        decorations += CustomDecoration(
                            start = start,
                            end = end,
                            spec = spec,
                            color = when (val c = spec.color) {
                                DecorationColor.Auto -> runColor
                                is DecorationColor.Custom -> c.color.toColor()
                            },
                            fontSize = merged.fontSizeOrDefault(),
                        )
                    }
                }
                val spanStyle = run.style.toSpanStyle(
                    merged = merged,
                    density = density,
                    fontProvider = fontProvider,
                    fills = run.fills,
                )
                if (spanStyle != SpanStyle()) addStyle(spanStyle, start, end)
            }
            links.forEach { link ->
                val start = maxOf(link.start, paragraph.start) - paragraph.start
                val end = minOf(link.end, paragraph.end) - paragraph.start
                if (end > start) addStyle(linkSpanStyle, start, end)
            }
        }
        // Paragraph-granular per-range line height: Compose can't vary line height per span
        // within a line, so the paragraph adopts the tallest effective line height among the
        // runs overlapping it (a paragraph fully inside one styled range gets that range's
        // line height; an intra-line size/line-height mix falls back to the tallest — documented).
        val overlappingRuns = runs.filter { maxOf(it.start, paragraph.start) < minOf(it.end, paragraph.end) }
        val paragraphLineHeight = (overlappingRuns.map { baseStyle.mergedWith(it.style) } + baseStyle)
            .maxByOrNull { it.lineHeightPx() }?.lineHeight ?: baseStyle.lineHeight
        val paragraphBase = baseStyle.copy(lineHeight = paragraphLineHeight)
        ParagraphSpec(
            textStart = paragraph.start,
            textEnd = paragraph.end,
            annotated = annotated,
            style = paragraphBase.toTextStyle(density, fontProvider, fill),
            marker = paragraph.marker,
            indent = listIndent,
            hangFirstChar = baseStyle.hangingPunctuation == true &&
                paragraph.end > paragraph.start &&
                text[paragraph.start].isHangablePunctuation(),
            customDecorations = decorations,
        )
    }
    return ComposedRichText(rich, transformed, paragraphs, fill)
}

/** Whether Compose's built-in [TextDecoration] can draw this spec faithfully. */
internal val DecorationSpec.isNativelyDrawable: Boolean
    get() = style == DecorationStyle.Solid &&
        color == DecorationColor.Auto &&
        thickness is io.aequicor.visualization.subsystems.typography.DecorationThickness.Auto &&
        !skipInk

private fun Char.isHangablePunctuation(): Boolean =
    this in "\"'«»„“”‘’‹›([{–—-•"

internal fun Rgba.toColor(): Color =
    Color(red.toFloat(), green.toFloat(), blue.toFloat(), alpha.toFloat())

/** Base (whole-paragraph) text style; span overrides layer on top. */
internal fun TypographyStyle.toTextStyle(
    density: Density,
    fontProvider: FontProvider,
    fill: RichTextFill,
): TextStyle {
    val lineHeight = lineHeightUnit(density)
    val base = TextStyle(
        color = if (fill.brush == null) fill.color else Color.Unspecified,
        fontSize = fontSizeOrDefault().toSp(density),
        fontWeight = FontWeight(fontWeightOrDefault().coerceIn(1, 1000)),
        fontStyle = if (italic == true) FontStyle.Italic else FontStyle.Normal,
        fontFamily = fontProvider.resolve(fontDescriptor()),
        letterSpacing = letterSpacingPx().takeIf { it != 0.0 }?.toSp(density) ?: TextUnit.Unspecified,
        lineHeight = lineHeight,
        lineHeightStyle = if (lineHeight != TextUnit.Unspecified) {
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = when (leadingTrim) {
                    LeadingTrim.CapHeight -> LineHeightStyle.Trim.Both
                    else -> LineHeightStyle.Trim.None
                },
            )
        } else null,
        textAlign = when (alignHorizontal) {
            AlignHorizontal.Center -> TextAlign.Center
            AlignHorizontal.Right -> TextAlign.Right
            AlignHorizontal.Justified -> TextAlign.Justify
            AlignHorizontal.Left, null -> TextAlign.Left
        },
        fontFeatureSettings = featureSettings(this),
        textIndent = paragraphIndent?.takeIf { it != 0.0 }?.let {
            TextIndent(firstLine = it.toSp(density))
        } ?: TextIndent.None,
        // Decorations are handled per-run (built-in span or custom painter segment),
        // never at the paragraph level, to avoid double drawing.
        textDecoration = TextDecoration.None,
    )
    return if (fill.brush != null) base.copy(brush = fill.brush) else base
}

/**
 * Span override: only fields the range actually overrides are set, so everything else
 * inherits from the paragraph style. [merged] (base + override) supplies values for
 * size-dependent conversions.
 */
internal fun TypographyStyle.toSpanStyle(
    merged: TypographyStyle,
    density: Density,
    fontProvider: FontProvider,
    fills: List<TextFill>?,
): SpanStyle {
    val position = merged.position ?: TextPosition.None
    val positionScale = if (position == TextPosition.None) 1.0 else TypographyDefaults.SUPERSCRIPT_SCALE
    val sizeOverridden = fontSize != null || position != TextPosition.None
    val familyOverridden = fontFamily != null || italic != null || axes.isNotEmpty()
    val featuresOverridden = features.isNotEmpty() || case != null || position != TextPosition.None

    val fillBrush = fills?.firstOrNull()?.toBrush()
    val fillColor = if (fillBrush == null) fills?.firstSolid()?.toColor() else null

    var span = SpanStyle(
        color = fillColor ?: Color.Unspecified,
        fontSize = if (sizeOverridden) (merged.fontSizeOrDefault() * positionScale).toSp(density) else TextUnit.Unspecified,
        fontWeight = fontWeight?.let { FontWeight(it.coerceIn(1, 1000)) },
        fontStyle = italic?.let { if (it) FontStyle.Italic else FontStyle.Normal },
        fontFamily = if (familyOverridden) fontProvider.resolve(merged.fontDescriptor()) else null,
        letterSpacing = if (letterSpacing != null || (sizeOverridden && merged.letterSpacing != null)) {
            merged.letterSpacingPx().toSp(density)
        } else TextUnit.Unspecified,
        baselineShift = when (position) {
            TextPosition.Superscript -> BaselineShift.Superscript
            TextPosition.Subscript -> BaselineShift.Subscript
            TextPosition.None -> null
        },
        fontFeatureSettings = if (featuresOverridden) featureSettings(merged) else null,
        textDecoration = merged.decoration?.let { spec ->
            when {
                spec.kind == DecorationKind.None -> if (decoration != null) TextDecoration.None else null
                !spec.isNativelyDrawable -> if (decoration != null) TextDecoration.None else null
                spec.kind == DecorationKind.Underline -> TextDecoration.Underline
                else -> TextDecoration.LineThrough
            }
        },
    )
    if (fillBrush != null) span = span.copy(brush = fillBrush)
    return span
}

/**
 * CSS `font-feature-settings` for the merged style. Superscript/subscript are
 * synthesized via [BaselineShift] + size scale (not `sups`/`subs`) so behavior does
 * not depend on font support.
 */
internal fun featureSettings(merged: TypographyStyle): String? {
    val tags = linkedMapOf<String, Int>()
    when (merged.case) {
        TextCasing.SmallCaps -> tags["smcp"] = 1
        TextCasing.SmallCapsForced -> {
            tags["smcp"] = 1
            tags["c2sc"] = 1
        }
        else -> {}
    }
    merged.features.forEach { (tag, on) -> tags[tag] = if (on) 1 else 0 }
    if (tags.isEmpty()) return null
    return tags.entries.joinToString(", ") { "'${it.key}' ${it.value}" }
}

private fun TypographyStyle.lineHeightUnit(density: Density): TextUnit = when (val lh = lineHeight) {
    null, LineHeight.Auto -> TextUnit.Unspecified
    is LineHeight.Px -> lh.value.toSp(density)
    is LineHeight.Percent -> (fontSizeOrDefault() * lh.value / 100.0).toSp(density)
}

internal fun TextFill.toBrush(): Brush? = when (this) {
    is TextFill.Solid -> null // plain color path is cheaper and equivalent
    is TextFill.LinearGradient -> AngledLinearGradientBrush(stops, angleDeg)
    is TextFill.RadialGradient -> CenteredRadialGradientBrush(stops)
}

/** Linear gradient across the shaded bounds at an arbitrary angle. */
internal data class AngledLinearGradientBrush(
    private val stops: List<GradientStop>,
    private val angleDeg: Double,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = angleDeg * PI / 180.0
        val dx = cos(rad)
        val dy = sin(rad)
        val cx = size.width / 2.0
        val cy = size.height / 2.0
        val extent = abs(cx * dx) + abs(cy * dy)
        return LinearGradientShader(
            from = Offset((cx - dx * extent).toFloat(), (cy - dy * extent).toFloat()),
            to = Offset((cx + dx * extent).toFloat(), (cy + dy * extent).toFloat()),
            colors = stops.map { it.color.toColor() },
            colorStops = stops.map { it.offset.toFloat() },
        )
    }
}

internal data class CenteredRadialGradientBrush(
    private val stops: List<GradientStop>,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader = RadialGradientShader(
        center = Offset(size.width / 2f, size.height / 2f),
        // A zero-size draw target must not produce a zero radius (invalid for the shader).
        radius = (maxOf(size.width, size.height) / 2f).coerceAtLeast(0.5f),
        colors = stops.map { it.color.toColor() },
        colorStops = stops.map { it.offset.toFloat() },
    )
}

internal fun Double.toSp(density: Density): TextUnit =
    with(density) { toFloat().toSp() }
