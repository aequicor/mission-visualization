package io.aequicor.visualization.subsystems.typography

/**
 * Full Figma-parity text style. Every field is optional so the same type doubles as a
 * partial override: base style <- span style are merged field by field ([mergedWith]).
 *
 * The subsystem deliberately owns its types and does not import engine or app models
 * (mirrors the anchoring subsystem's `SnapBox` precedent); adapters map at the boundary.
 */
data class TypographyStyle(
    val fontFamily: String? = null,
    /** 1..1000, CSS-style numeric weight. */
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    /** Document px. */
    val fontSize: Double? = null,
    val lineHeight: LineHeight? = null,
    val letterSpacing: LetterSpacing? = null,
    /** Extra px between paragraphs (after each `\n`). */
    val paragraphSpacing: Double? = null,
    /** First-line indent of each paragraph, px. */
    val paragraphIndent: Double? = null,
    val alignHorizontal: AlignHorizontal? = null,
    val alignVertical: AlignVertical? = null,
    val case: TextCasing? = null,
    val decoration: DecorationSpec? = null,
    /** OpenType features by tag, e.g. "liga" -> true, "tnum" -> true. */
    val features: Map<String, Boolean> = emptyMap(),
    /** Variable font axes, e.g. "opsz" -> 24.0. */
    val axes: Map<String, Double> = emptyMap(),
    /** Figma "position": superscript / subscript. */
    val position: TextPosition? = null,
    val leadingTrim: LeadingTrim? = null,
    val hangingPunctuation: Boolean? = null,
    /** Hang list markers outside the text edge. */
    val hangingLists: Boolean? = null,
) {
    /** Returns this style with [override]'s non-null fields taking precedence. */
    fun mergedWith(override: TypographyStyle?): TypographyStyle {
        if (override == null) return this
        return TypographyStyle(
            fontFamily = override.fontFamily ?: fontFamily,
            fontWeight = override.fontWeight ?: fontWeight,
            italic = override.italic ?: italic,
            fontSize = override.fontSize ?: fontSize,
            lineHeight = override.lineHeight ?: lineHeight,
            letterSpacing = override.letterSpacing ?: letterSpacing,
            paragraphSpacing = override.paragraphSpacing ?: paragraphSpacing,
            paragraphIndent = override.paragraphIndent ?: paragraphIndent,
            alignHorizontal = override.alignHorizontal ?: alignHorizontal,
            alignVertical = override.alignVertical ?: alignVertical,
            case = override.case ?: case,
            decoration = override.decoration ?: decoration,
            features = features + override.features,
            axes = axes + override.axes,
            position = override.position ?: position,
            leadingTrim = override.leadingTrim ?: leadingTrim,
            hangingPunctuation = override.hangingPunctuation ?: hangingPunctuation,
            hangingLists = override.hangingLists ?: hangingLists,
        )
    }

    val isEmpty: Boolean
        get() = this == EMPTY

    companion object {
        val EMPTY = TypographyStyle()
    }
}

/** Concrete defaults substituted where a fully-resolved value is required. */
object TypographyDefaults {
    const val FONT_SIZE: Double = 14.0
    const val FONT_WEIGHT: Int = 400
    const val LINE_HEIGHT_FACTOR: Double = 1.25
    const val ASCENT_FACTOR: Double = 0.8
    const val SUPERSCRIPT_SCALE: Double = 0.65
}

fun TypographyStyle.fontSizeOrDefault(): Double = fontSize ?: TypographyDefaults.FONT_SIZE

fun TypographyStyle.fontWeightOrDefault(): Int = fontWeight ?: TypographyDefaults.FONT_WEIGHT

/** Resolved line height in px for this style (Auto -> [TypographyDefaults.LINE_HEIGHT_FACTOR] x size). */
fun TypographyStyle.lineHeightPx(): Double = when (val lh = lineHeight) {
    null, LineHeight.Auto -> fontSizeOrDefault() * TypographyDefaults.LINE_HEIGHT_FACTOR
    is LineHeight.Px -> lh.value
    is LineHeight.Percent -> fontSizeOrDefault() * lh.value / 100.0
}

/** Resolved letter spacing in px for this style. */
fun TypographyStyle.letterSpacingPx(): Double = when (val ls = letterSpacing) {
    null -> 0.0
    is LetterSpacing.Px -> ls.value
    is LetterSpacing.Percent -> fontSizeOrDefault() * ls.value / 100.0
}

sealed interface LineHeight {
    /** Native font metrics. */
    data object Auto : LineHeight

    data class Px(val value: Double) : LineHeight

    /** Percent of font size (Figma default unit). */
    data class Percent(val value: Double) : LineHeight
}

sealed interface LetterSpacing {
    data class Px(val value: Double) : LetterSpacing

    /** Percent of font size (Figma default unit). */
    data class Percent(val value: Double) : LetterSpacing
}

enum class AlignHorizontal { Left, Center, Right, Justified }

enum class AlignVertical { Top, Center, Bottom }

enum class TextCasing { None, Upper, Lower, Title, SmallCaps, SmallCapsForced }

enum class TextPosition { None, Superscript, Subscript }

/** Figma "leading trim": cap height to baseline vertical trimming. */
enum class LeadingTrim { None, CapHeight }

enum class DecorationKind { None, Underline, Strikethrough }

enum class DecorationStyle { Solid, Dashed, Dotted, Wavy }

/** RGBA color, channels 0..1. */
data class Rgba(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double = 1.0,
)

sealed interface DecorationColor {
    /** Follow the glyph fill. */
    data object Auto : DecorationColor

    data class Custom(val color: Rgba) : DecorationColor
}

sealed interface DecorationThickness {
    data object Auto : DecorationThickness

    data class Px(val value: Double) : DecorationThickness

    /** Percent of font size. */
    data class Percent(val value: Double) : DecorationThickness
}

data class DecorationSpec(
    val kind: DecorationKind = DecorationKind.None,
    val style: DecorationStyle = DecorationStyle.Solid,
    val color: DecorationColor = DecorationColor.Auto,
    val thickness: DecorationThickness = DecorationThickness.Auto,
    /** Break the line around descenders. Renderers implement this best-effort. */
    val skipInk: Boolean = false,
)

/** Resolved decoration thickness in px against [fontSize]. */
fun DecorationThickness.resolvePx(fontSize: Double): Double = when (this) {
    DecorationThickness.Auto -> (fontSize / 14.0).coerceAtLeast(1.0)
    is DecorationThickness.Px -> value
    is DecorationThickness.Percent -> fontSize * value / 100.0
}
