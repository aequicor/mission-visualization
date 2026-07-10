package io.aequicor.visualization.engine.ir.model

/**
 * Text style; every field is optional so the same type doubles as a partial override
 * (shared style <- node style <- range style are merged field by field).
 */
data class DesignTextStyle(
    val fontFamily: String? = null,
    val fontWeight: Bindable<Double>? = null,
    val italic: Boolean? = null,
    val fontSize: Bindable<Double>? = null,
    val lineHeight: UnitValue? = null,
    val letterSpacing: UnitValue? = null,
    val paragraphSpacing: Double? = null,
    /** First-line indent of each paragraph, px. */
    val paragraphIndent: Double? = null,
    val textAlignHorizontal: TextAlignHorizontal? = null,
    val textAlignVertical: TextAlignVertical? = null,
    val textCase: TextCase? = null,
    val textDecoration: TextDecorationKind? = null,
    val decorationStyle: TextDecorationStyle? = null,
    /** null = decoration follows the glyph color. */
    val decorationColor: DesignColor? = null,
    /** null = automatic thickness; percent resolves against font size. */
    val decorationThickness: UnitValue? = null,
    val decorationSkipInk: Boolean? = null,
    /** Figma "position": superscript / subscript. */
    val textPosition: TextScriptPosition? = null,
    val leadingTrim: LeadingTrim? = null,
    val hangingPunctuation: Boolean? = null,
    /** Hang list markers outside the text edge. */
    val hangingList: Boolean? = null,
    val fontFeatures: Map<String, Boolean> = emptyMap(),
    /** Variable font axes, e.g. "opsz" -> 24.0. */
    val variableAxes: Map<String, Double> = emptyMap(),
) {
    /** Returns this style with [override]'s non-null fields taking precedence. */
    fun mergedWith(override: DesignTextStyle?): DesignTextStyle {
        if (override == null) return this
        return DesignTextStyle(
            fontFamily = override.fontFamily ?: fontFamily,
            fontWeight = override.fontWeight ?: fontWeight,
            italic = override.italic ?: italic,
            fontSize = override.fontSize ?: fontSize,
            lineHeight = override.lineHeight ?: lineHeight,
            letterSpacing = override.letterSpacing ?: letterSpacing,
            paragraphSpacing = override.paragraphSpacing ?: paragraphSpacing,
            paragraphIndent = override.paragraphIndent ?: paragraphIndent,
            textAlignHorizontal = override.textAlignHorizontal ?: textAlignHorizontal,
            textAlignVertical = override.textAlignVertical ?: textAlignVertical,
            textCase = override.textCase ?: textCase,
            textDecoration = override.textDecoration ?: textDecoration,
            decorationStyle = override.decorationStyle ?: decorationStyle,
            decorationColor = override.decorationColor ?: decorationColor,
            decorationThickness = override.decorationThickness ?: decorationThickness,
            decorationSkipInk = override.decorationSkipInk ?: decorationSkipInk,
            textPosition = override.textPosition ?: textPosition,
            leadingTrim = override.leadingTrim ?: leadingTrim,
            hangingPunctuation = override.hangingPunctuation ?: hangingPunctuation,
            hangingList = override.hangingList ?: hangingList,
            fontFeatures = fontFeatures + override.fontFeatures,
            variableAxes = variableAxes + override.variableAxes,
        )
    }
}

enum class TextAlignHorizontal { Left, Center, Right, Justified }

enum class TextAlignVertical { Top, Center, Bottom }

enum class TextCase { None, Upper, Lower, Title, SmallCaps, SmallCapsForced }

enum class TextDecorationKind { None, Underline, Strikethrough }

enum class TextDecorationStyle { Solid, Dashed, Dotted, Wavy }

enum class TextScriptPosition { None, Superscript, Subscript }

/** Figma "leading trim": cap height to baseline vertical trimming. */
enum class LeadingTrim { None, CapHeight }

enum class TextAutoResize { None, Height, WidthAndHeight }

data class TextTruncate(
    val maxLines: Int,
    val ellipsis: Boolean = true,
)

/** Mixed formatting inside one text node without splitting it into multiple nodes. */
data class TextStyleRange(
    val start: Int,
    val end: Int,
    val style: DesignTextStyle = DesignTextStyle(),
    val fills: List<DesignPaint>? = null,
    /** Shared text style id merged under the node base and over by [style] (base < ref < inline). */
    val styleRef: String = "",
)

/** Exactly one of [url] (external) or [nodeTarget] (internal navigation) is set. */
data class TextLink(
    val start: Int,
    val end: Int,
    val url: String,
    val nodeTarget: String = "",
)
