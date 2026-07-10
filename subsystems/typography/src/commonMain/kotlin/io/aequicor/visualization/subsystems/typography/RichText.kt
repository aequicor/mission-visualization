package io.aequicor.visualization.subsystems.typography

/**
 * A styled text document: one string plus a base style and character-range overrides.
 * Offsets are UTF-16 indices into [text]; ranges are half-open `[start, end)`.
 */
data class RichText(
    val text: String,
    /** Node-level style; span styles override it per range. */
    val base: TypographyStyle = TypographyStyle(),
    val spans: List<StyleSpan> = emptyList(),
    val links: List<LinkSpan> = emptyList(),
    val list: TextListSpec = TextListSpec(),
    val truncate: Truncation? = null,
    val autoResize: AutoResizeMode = AutoResizeMode.None,
)

/** Mixed formatting inside one text without splitting it into multiple nodes. */
data class StyleSpan(
    val start: Int,
    val end: Int,
    val style: TypographyStyle = TypographyStyle(),
    /** Glyph fills for the range; null = inherit the base fill. */
    val fills: List<TextFill>? = null,
)

/** Exactly one of [url] (external) or [nodeTarget] (internal navigation) is set. */
data class LinkSpan(
    val start: Int,
    val end: Int,
    val url: String = "",
    val nodeTarget: String = "",
)

enum class ListType { None, Bullet, Ordered }

data class TextListSpec(
    val type: ListType = ListType.None,
    val indent: Int = 0,
)

data class Truncation(
    val maxLines: Int,
    val ellipsis: Boolean = true,
)

enum class AutoResizeMode { None, Height, WidthAndHeight }

/**
 * Glyph fill. Deliberately simpler than a full design-paint model: solid and gradient
 * cover Figma's text-fill surface; image fills on glyphs stay at the node adapter level.
 */
sealed interface TextFill {
    data class Solid(val color: Rgba) : TextFill

    data class LinearGradient(
        val stops: List<GradientStop>,
        /** Degrees, 0 = left-to-right, growing clockwise. */
        val angleDeg: Double = 0.0,
    ) : TextFill

    data class RadialGradient(val stops: List<GradientStop>) : TextFill
}

data class GradientStop(
    /** 0..1 position along the gradient. */
    val offset: Double,
    val color: Rgba,
)

/** First solid color of the fill list, if any (fallback color for painters). */
fun List<TextFill>.firstSolid(): Rgba? =
    firstOrNull { it is TextFill.Solid }?.let { (it as TextFill.Solid).color }
