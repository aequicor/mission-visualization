package io.aequicor.visualization.designdoc.domain.model

/**
 * Text style; every field is optional so the same type doubles as a partial override
 * (shared style <- node style <- range style are merged field by field).
 */
data class DesignTextStyle(
    val fontFamily: String? = null,
    val fontWeight: Bindable<Double>? = null,
    val fontSize: Bindable<Double>? = null,
    val lineHeight: UnitValue? = null,
    val letterSpacing: UnitValue? = null,
    val paragraphSpacing: Double? = null,
    val textAlignHorizontal: TextAlignHorizontal? = null,
    val textAlignVertical: TextAlignVertical? = null,
    val textCase: TextCase? = null,
    val textDecoration: TextDecorationKind? = null,
    val fontFeatures: Map<String, Boolean> = emptyMap(),
) {
    /** Returns this style with [override]'s non-null fields taking precedence. */
    fun mergedWith(override: DesignTextStyle?): DesignTextStyle {
        if (override == null) return this
        return DesignTextStyle(
            fontFamily = override.fontFamily ?: fontFamily,
            fontWeight = override.fontWeight ?: fontWeight,
            fontSize = override.fontSize ?: fontSize,
            lineHeight = override.lineHeight ?: lineHeight,
            letterSpacing = override.letterSpacing ?: letterSpacing,
            paragraphSpacing = override.paragraphSpacing ?: paragraphSpacing,
            textAlignHorizontal = override.textAlignHorizontal ?: textAlignHorizontal,
            textAlignVertical = override.textAlignVertical ?: textAlignVertical,
            textCase = override.textCase ?: textCase,
            textDecoration = override.textDecoration ?: textDecoration,
            fontFeatures = fontFeatures + override.fontFeatures,
        )
    }
}

enum class TextAlignHorizontal { Left, Center, Right, Justified }

enum class TextAlignVertical { Top, Center, Bottom }

enum class TextCase { None, Upper, Lower, Title }

enum class TextDecorationKind { None, Underline, Strikethrough }

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
)

data class TextLink(
    val start: Int,
    val end: Int,
    val url: String,
)
