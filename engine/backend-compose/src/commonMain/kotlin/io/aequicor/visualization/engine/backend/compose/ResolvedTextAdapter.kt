package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.graphics.Color
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import io.aequicor.visualization.engine.ir.resolve.ResolvedText
import io.aequicor.visualization.engine.ir.resolve.ResolvedTextStyle
import io.aequicor.visualization.subsystems.typography.AlignHorizontal
import io.aequicor.visualization.subsystems.typography.AlignVertical
import io.aequicor.visualization.subsystems.typography.AutoResizeMode
import io.aequicor.visualization.subsystems.typography.DecorationColor
import io.aequicor.visualization.subsystems.typography.DecorationKind
import io.aequicor.visualization.subsystems.typography.DecorationSpec
import io.aequicor.visualization.subsystems.typography.DecorationStyle
import io.aequicor.visualization.subsystems.typography.DecorationThickness
import io.aequicor.visualization.subsystems.typography.GradientStop
import io.aequicor.visualization.subsystems.typography.LetterSpacing
import io.aequicor.visualization.subsystems.typography.LineHeight
import io.aequicor.visualization.subsystems.typography.LinkSpan
import io.aequicor.visualization.subsystems.typography.ListType
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.Rgba
import io.aequicor.visualization.subsystems.typography.StyleSpan
import io.aequicor.visualization.subsystems.typography.TextCasing
import io.aequicor.visualization.subsystems.typography.TextFill
import io.aequicor.visualization.subsystems.typography.TextListSpec
import io.aequicor.visualization.subsystems.typography.TextPosition
import io.aequicor.visualization.subsystems.typography.Truncation
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import io.aequicor.visualization.subsystems.typography.compose.RichTextFill
import io.aequicor.visualization.subsystems.typography.compose.toRichTextFill
import io.aequicor.visualization.subsystems.typography.prunedAgainst
import kotlin.math.PI
import kotlin.math.atan2
import io.aequicor.visualization.engine.ir.model.LeadingTrim as IrLeadingTrim
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle as IrDecorationStyle
import io.aequicor.visualization.subsystems.typography.LeadingTrim as TypoLeadingTrim

/**
 * Boundary adapter: `ResolvedText` (engine IR) -> `RichText` (typography subsystem).
 * Range styles arrive fully merged from the resolver; [prunedAgainst] reduces them
 * back to minimal per-range overrides.
 */
internal fun ResolvedText.toRichText(): RichText {
    val base = style.toTypographyStyle()
    return RichText(
        text = characters,
        base = base,
        spans = ranges.mapNotNull { range ->
            val override = range.style.toTypographyStyle().prunedAgainst(base)
            val fills = range.fills?.toTextFills()
            if (override == TypographyStyle.EMPTY && fills == null) null
            else StyleSpan(start = range.start, end = range.end, style = override, fills = fills)
        },
        links = links.map { LinkSpan(it.start, it.end, it.url, it.nodeTarget) },
        list = TextListSpec(
            type = when (list.type) {
                TextListType.None -> ListType.None
                TextListType.Bullet -> ListType.Bullet
                TextListType.Ordered -> ListType.Ordered
            },
            indent = list.indent,
        ),
        truncate = truncate?.let { Truncation(it.maxLines, it.ellipsis) },
        autoResize = when (autoResize) {
            TextAutoResize.None -> AutoResizeMode.None
            TextAutoResize.Height -> AutoResizeMode.Height
            TextAutoResize.WidthAndHeight -> AutoResizeMode.WidthAndHeight
        },
    )
}

internal fun ResolvedTextStyle.toTypographyStyle(): TypographyStyle = TypographyStyle(
    fontFamily = fontFamily.takeIf { it.isNotBlank() },
    fontWeight = fontWeight,
    italic = italic,
    fontSize = fontSize,
    lineHeight = if (lineHeight > 0.0) LineHeight.Px(lineHeight) else LineHeight.Auto,
    letterSpacing = if (letterSpacing != 0.0) LetterSpacing.Px(letterSpacing) else null,
    paragraphSpacing = paragraphSpacing,
    paragraphIndent = paragraphIndent,
    alignHorizontal = when (textAlignHorizontal) {
        TextAlignHorizontal.Left -> AlignHorizontal.Left
        TextAlignHorizontal.Center -> AlignHorizontal.Center
        TextAlignHorizontal.Right -> AlignHorizontal.Right
        TextAlignHorizontal.Justified -> AlignHorizontal.Justified
    },
    alignVertical = when (textAlignVertical) {
        TextAlignVertical.Top -> AlignVertical.Top
        TextAlignVertical.Center -> AlignVertical.Center
        TextAlignVertical.Bottom -> AlignVertical.Bottom
    },
    case = when (textCase) {
        TextCase.None -> TextCasing.None
        TextCase.Upper -> TextCasing.Upper
        TextCase.Lower -> TextCasing.Lower
        TextCase.Title -> TextCasing.Title
        TextCase.SmallCaps -> TextCasing.SmallCaps
        TextCase.SmallCapsForced -> TextCasing.SmallCapsForced
    },
    decoration = if (textDecoration == TextDecorationKind.None) null else DecorationSpec(
        kind = when (textDecoration) {
            TextDecorationKind.None -> DecorationKind.None
            TextDecorationKind.Underline -> DecorationKind.Underline
            TextDecorationKind.Strikethrough -> DecorationKind.Strikethrough
        },
        style = when (decorationStyle) {
            IrDecorationStyle.Solid -> DecorationStyle.Solid
            IrDecorationStyle.Dashed -> DecorationStyle.Dashed
            IrDecorationStyle.Dotted -> DecorationStyle.Dotted
            IrDecorationStyle.Wavy -> DecorationStyle.Wavy
        },
        color = decorationColor?.let { DecorationColor.Custom(it.toRgba()) } ?: DecorationColor.Auto,
        thickness = decorationThickness?.let { DecorationThickness.Px(it) } ?: DecorationThickness.Auto,
        skipInk = decorationSkipInk,
    ),
    features = fontFeatures,
    axes = variableAxes,
    position = when (textPosition) {
        TextScriptPosition.None -> TextPosition.None
        TextScriptPosition.Superscript -> TextPosition.Superscript
        TextScriptPosition.Subscript -> TextPosition.Subscript
    },
    leadingTrim = when (leadingTrim) {
        IrLeadingTrim.None -> TypoLeadingTrim.None
        IrLeadingTrim.CapHeight -> TypoLeadingTrim.CapHeight
    },
    hangingPunctuation = hangingPunctuation,
    hangingLists = hangingList,
)

internal fun DesignColor.toRgba(opacity: Double = 1.0): Rgba = Rgba(
    red = red / 255.0,
    green = green / 255.0,
    blue = blue / 255.0,
    alpha = (alpha / 255.0) * opacity,
)

internal fun ResolvedPaint.toTextFill(): TextFill? = when (this) {
    is ResolvedPaint.Solid -> TextFill.Solid(color.toRgba(opacity))
    is ResolvedPaint.Gradient -> {
        val gradientStops = stops
            .sortedBy { it.position }
            .map { GradientStop(it.position, it.color.toRgba(opacity)) }
        when {
            gradientStops.isEmpty() -> null
            gradientType == GradientKind.Radial -> TextFill.RadialGradient(gradientStops)
            // Angular/diamond glyph fills approximate as a linear ramp along from->to.
            else -> TextFill.LinearGradient(
                stops = gradientStops,
                angleDeg = atan2(to.y - from.y, to.x - from.x) * 180.0 / PI,
            )
        }
    }
    else -> null
}

internal fun List<ResolvedPaint>.toTextFills(): List<TextFill>? =
    mapNotNull { it.toTextFill() }.ifEmpty { null }

/** Node fill list -> the base glyph fill (first expressible paint wins). */
internal fun List<ResolvedPaint>.toRichTextFill(): RichTextFill =
    firstNotNullOfOrNull { it.toTextFill() }?.toRichTextFill() ?: RichTextFill(color = Color.Black)
