package io.aequicor.visualization.engine.ir.layout

import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.resolve.ResolvedText
import io.aequicor.visualization.engine.ir.resolve.ResolvedTextStyle

/**
 * Platform text measurement injected into the layout engine so the engine itself
 * stays pure Kotlin. The Compose UI layer provides the real implementation; tests
 * use a deterministic fake.
 */
interface DesignTextMeasurer {
    fun measure(text: ResolvedText, maxWidth: Double? = null): MeasuredText

    /**
     * Distance from the text box top to the first line's alphabetic baseline.
     * Default approximation: `0.8 * fontSize` (typical ascent share); platform
     * implementations return the real measured baseline.
     */
    fun firstBaseline(text: ResolvedText, maxWidth: Double? = null): Double =
        text.style.fontSize * 0.8
}

data class MeasuredText(
    val width: Double,
    val height: Double,
    val lineCount: Int = 1,
    /** Text-box top to first-line alphabetic baseline; null = not provided. */
    val firstBaseline: Double? = null,
    /** Text-box top to last-line alphabetic baseline; null = not provided. */
    val lastBaseline: Double? = null,
)

/**
 * Deterministic approximation used as a fallback and in unit tests.
 *
 * Span-aware: per-character advance and line height come from the covering
 * [ResolvedText.ranges] style (base style elsewhere), `\n` starts a new paragraph,
 * paragraph spacing and list indents contribute to the box. For single-style text
 * without newlines the numbers are identical to the historical uniform formula
 * (`width = length * (fontSize * factor + letterSpacing)`).
 */
class ApproximateTextMeasurer(
    private val averageCharWidthFactor: Double = 0.6,
) : DesignTextMeasurer {

    override fun measure(text: ResolvedText, maxWidth: Double?): MeasuredText {
        val characters = text.characters
        val baseStyle = text.style
        val indent = listIndent(text)
        val contentMax = maxWidth?.takeIf { it > 0.0 }?.let { (it - indent).coerceAtLeast(1.0) }

        fun styleAt(offset: Int): ResolvedTextStyle =
            text.ranges.firstOrNull { offset >= it.start && offset < it.end }?.style ?: baseStyle

        fun charWidth(style: ResolvedTextStyle): Double =
            style.fontSize * averageCharWidthFactor + style.letterSpacing

        fun runLineHeight(style: ResolvedTextStyle): Double =
            if (style.lineHeight > 0.0) style.lineHeight else style.fontSize * 1.25

        val lineHeights = mutableListOf<Double>()
        var top = 0.0
        var maxLineWidth = 0.0
        var truncated = false

        val paragraphs = characters.split('\n')
        var offset = 0
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            if (truncated) {
                offset += paragraph.length + 1
                return@forEachIndexed
            }
            if (paragraphIndex > 0) top += baseStyle.paragraphSpacing

            var lineWidth = 0.0
            var lineMaxHeight = 0.0
            var lineHasContent = false

            fun flushLine(fallbackStyle: ResolvedTextStyle) {
                val height = if (lineMaxHeight > 0.0) lineMaxHeight else runLineHeight(fallbackStyle)
                lineHeights += height
                top += height
                if (lineWidth > maxLineWidth) maxLineWidth = lineWidth
                lineWidth = 0.0
                lineMaxHeight = 0.0
                lineHasContent = false
                text.truncate?.let { truncate ->
                    if (lineHeights.size >= truncate.maxLines) truncated = true
                }
            }

            if (paragraph.isEmpty()) {
                flushLine(styleAt(offset))
            } else {
                var index = 0
                while (index < paragraph.length && !truncated) {
                    val style = styleAt(offset + index)
                    val advance = charWidth(style)
                    if (contentMax != null && lineHasContent && lineWidth + advance > contentMax) {
                        flushLine(style)
                        if (truncated) break
                        continue
                    }
                    lineWidth += advance
                    lineHasContent = true
                    val height = runLineHeight(style)
                    if (height > lineMaxHeight) lineMaxHeight = height
                    index++
                }
                if (!truncated && lineHasContent) flushLine(styleAt(offset + paragraph.length - 1))
            }
            offset += paragraph.length + 1
        }

        if (lineHeights.isEmpty()) {
            lineHeights += runLineHeight(baseStyle)
            top = lineHeights.single()
        }

        val naturalWidth = indent + maxLineWidth
        val width = when {
            maxWidth == null || maxWidth <= 0.0 -> naturalWidth
            naturalWidth <= maxWidth -> naturalWidth
            else -> maxWidth
        }
        val first = firstBaseline(text, maxWidth)
        return MeasuredText(
            width = width,
            height = top,
            lineCount = lineHeights.size,
            firstBaseline = first,
            lastBaseline = first + (top - lineHeights.first()),
        )
    }

    private fun listIndent(text: ResolvedText): Double =
        if (text.list.type == TextListType.None) 0.0
        else (text.list.indent.coerceAtLeast(0) + 1) * 1.5 * text.style.fontSize
}
