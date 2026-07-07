package io.aequicor.visualization.designdoc.domain.layout

import io.aequicor.visualization.designdoc.domain.resolve.ResolvedText

/**
 * Platform text measurement injected into the layout engine so the engine itself
 * stays pure Kotlin. The Compose UI layer provides the real implementation; tests
 * use a deterministic fake.
 */
interface DesignTextMeasurer {
    fun measure(text: ResolvedText, maxWidth: Double? = null): MeasuredText
}

data class MeasuredText(
    val width: Double,
    val height: Double,
)

/** Deterministic approximation used as a fallback and in unit tests. */
class ApproximateTextMeasurer(
    private val averageCharWidthFactor: Double = 0.6,
) : DesignTextMeasurer {
    override fun measure(text: ResolvedText, maxWidth: Double?): MeasuredText {
        val fontSize = text.style.fontSize
        val lineHeight = if (text.style.lineHeight > 0.0) text.style.lineHeight else fontSize * 1.25
        val charWidth = fontSize * averageCharWidthFactor + text.style.letterSpacing
        val naturalWidth = text.characters.length * charWidth
        if (maxWidth == null || naturalWidth <= maxWidth || maxWidth <= 0.0) {
            return MeasuredText(width = naturalWidth, height = lineHeight)
        }
        val charsPerLine = (maxWidth / charWidth).toInt().coerceAtLeast(1)
        var lines = (text.characters.length + charsPerLine - 1) / charsPerLine
        text.truncate?.let { truncate -> lines = lines.coerceAtMost(truncate.maxLines) }
        return MeasuredText(width = maxWidth, height = lines * lineHeight)
    }
}
