package io.aequicor.visualization.subsystems.typography

/** Axis-aligned rectangle in document px. */
data class RectD(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
}

/** Metrics of one laid-out line. Offsets index the *rendered* string. */
data class LineMetrics(
    /** First text offset on the line. */
    val start: Int,
    /** Offset after the last visible character on the line. */
    val end: Int,
    val top: Double,
    val height: Double,
    /** Alphabetic baseline, measured from the text-box top. */
    val baseline: Double,
    val left: Double,
    val width: Double,
)

data class MeasuredRichText(
    val width: Double,
    val height: Double,
    val lines: List<LineMetrics>,
) {
    val lineCount: Int get() = lines.size
    val firstBaseline: Double? get() = lines.firstOrNull()?.baseline
    val lastBaseline: Double? get() = lines.lastOrNull()?.baseline
}

/**
 * Platform text measurement behind a pure contract, so layout logic stays free of UI
 * frameworks. The Compose implementation lives in `:subsystems:typography-compose`;
 * [HeuristicTypographyMeasurer] is the deterministic fallback for tests.
 */
interface TypographyMeasurer {
    fun measure(rich: RichText, maxWidth: Double? = null): MeasuredRichText
}

/**
 * Caret/selection geometry over a laid-out text, for editor UIs. Offsets index the
 * rendered string; implementations must be BiDi-aware where the platform is.
 */
interface TextSelectionGeometry {
    /** Caret rectangle (zero width not required; callers draw their own bar). */
    fun caretRect(offset: Int): RectD

    /** Highlight rectangles covering `[start, end)`; one per line segment. */
    fun selectionRects(start: Int, end: Int): List<RectD>

    /** Text offset nearest to a point in text-box coordinates. */
    fun offsetAt(x: Double, y: Double): Int

    /** Word range around [offset] (double-click selection). */
    fun wordBoundaryAt(offset: Int): IntRange

    /** Line (paragraph-visual) range containing [offset] (triple-click selection). */
    fun lineBoundaryAt(offset: Int): IntRange
}

/**
 * Deterministic span-aware approximation: per-run advance = `fontSize * 0.6 +
 * letterSpacing`, greedy character wrapping, per-line height = max run line-height,
 * baseline = `0.8 * lineHeight`. Honors `\n` paragraphs, paragraph spacing, list
 * indents and truncation. Used by tests and as a pure fallback.
 */
class HeuristicTypographyMeasurer(
    private val averageCharWidthFactor: Double = 0.6,
) : TypographyMeasurer {

    override fun measure(rich: RichText, maxWidth: Double?): MeasuredRichText {
        val casing = rich.base.case ?: TextCasing.None
        val transformed = CaseTransform.apply(rich.text, casing)
        val text = transformed.text
        val spans = CaseTransform.projectSpans(SpanAlgebra.normalize(rich.spans, rich.text.length), transformed)
        val runs = SpanAlgebra.runsCovering(spans, text.length)
            .ifEmpty { listOf(SpanAlgebra.Run(0, 0, TypographyStyle.EMPTY, null)) }

        val baseSize = rich.base.fontSizeOrDefault()
        val listIndent = ListLayout.indentPx(rich.list, baseSize)
        val paragraphSpacing = rich.base.paragraphSpacing ?: 0.0
        val paragraphIndent = rich.base.paragraphIndent ?: 0.0
        val contentMax = maxWidth?.let { (it - listIndent).coerceAtLeast(1.0) }

        val lines = mutableListOf<LineMetrics>()
        var top = 0.0
        var truncated = false

        val paragraphs = ListLayout.paragraphs(text, rich.list)
        paragraphs.forEachIndexed { pIndex, paragraph ->
            if (truncated) return@forEachIndexed
            if (pIndex > 0) top += paragraphSpacing

            var lineStart = paragraph.start
            var lineWidth = if (paragraph.start == paragraph.end) 0.0 else paragraphIndent
            var lineMaxHeight = 0.0
            var lineMaxSize = baseSize
            var offset = paragraph.start

            fun runAt(o: Int): SpanAlgebra.Run =
                runs.firstOrNull { o >= it.start && o < it.end } ?: runs.last()

            fun effective(o: Int): TypographyStyle = rich.base.mergedWith(runAt(o).style)

            fun flushLine(endOffset: Int) {
                val style = effective(maxOf(lineStart, paragraph.start))
                val height = if (lineMaxHeight > 0.0) lineMaxHeight else style.lineHeightPx()
                lines += LineMetrics(
                    start = lineStart,
                    end = endOffset,
                    top = top,
                    height = height,
                    baseline = top + height * TypographyDefaults.ASCENT_FACTOR,
                    left = listIndent,
                    width = lineWidth,
                )
                top += height
                lineStart = endOffset
                lineWidth = 0.0
                lineMaxHeight = 0.0
                lineMaxSize = baseSize
                rich.truncate?.let { truncate ->
                    if (lines.size >= truncate.maxLines) truncated = true
                }
            }

            if (paragraph.start == paragraph.end) {
                // Empty paragraph still occupies one line.
                lineMaxHeight = effective(paragraph.start.coerceAtMost(text.length - 1).coerceAtLeast(0))
                    .lineHeightPx()
                flushLine(paragraph.end)
                return@forEachIndexed
            }

            while (offset < paragraph.end && !truncated) {
                val style = effective(offset)
                val advance = style.fontSizeOrDefault() * averageCharWidthFactor + style.letterSpacingPx()
                val runLineHeight = style.lineHeightPx()
                if (contentMax != null && lineWidth + advance > contentMax && offset > lineStart) {
                    flushLine(offset)
                    if (truncated) break
                    continue
                }
                lineWidth += advance
                if (runLineHeight > lineMaxHeight) lineMaxHeight = runLineHeight
                if (style.fontSizeOrDefault() > lineMaxSize) lineMaxSize = style.fontSizeOrDefault()
                offset++
            }
            if (!truncated && offset >= paragraph.end && lineStart < paragraph.end) {
                flushLine(paragraph.end)
            }
        }

        if (lines.isEmpty()) {
            val height = rich.base.lineHeightPx()
            lines += LineMetrics(
                start = 0, end = 0, top = 0.0, height = height,
                baseline = height * TypographyDefaults.ASCENT_FACTOR,
                left = listIndent, width = 0.0,
            )
        }

        val width = (lines.maxOf { it.left + it.width }).coerceAtLeast(0.0)
        val height = lines.last().let { it.top + it.height }
        return MeasuredRichText(
            width = if (maxWidth != null && width > maxWidth) maxWidth else width,
            height = height,
            lines = lines,
        )
    }
}
