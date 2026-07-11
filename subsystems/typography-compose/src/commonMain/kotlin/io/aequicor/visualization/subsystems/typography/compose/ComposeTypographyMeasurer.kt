package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.aequicor.visualization.subsystems.typography.CaseTransform
import io.aequicor.visualization.subsystems.typography.LineMetrics
import io.aequicor.visualization.subsystems.typography.MeasuredRichText
import io.aequicor.visualization.subsystems.typography.RectD
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.TextSelectionGeometry
import io.aequicor.visualization.subsystems.typography.TypographyMeasurer
import io.aequicor.visualization.subsystems.typography.fontSizeOrDefault
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Compose-backed [TypographyMeasurer]. Lays out each paragraph as its own
 * [TextLayoutResult] and stacks them vertically — that is what makes paragraph
 * spacing, list markers and hanging punctuation expressible (Compose paragraphs
 * support none of those natively).
 *
 * Results are cached: text nodes are measured several times per layout pass
 * (natural width, natural height, placement, baseline) and again at draw time.
 */
class ComposeTypographyMeasurer(
    private val textMeasurer: TextMeasurer,
    private val density: Density,
    private val fontProvider: FontProvider = NoFonts,
    private val cacheSize: Int = 128,
) : TypographyMeasurer {

    private val cache = LinkedHashMap<CacheKey, LaidOutRichText>()

    override fun measure(rich: RichText, maxWidth: Double?): MeasuredRichText =
        layout(rich, maxWidth).measured

    /**
     * Full layout with paint/selection geometry; cached.
     *
     * [exactWidth] forces every paragraph to exactly [maxWidth] (min = max constraint)
     * so center/right/justify alignment positions lines inside the node box — use it
     * when laying out for painting into a known box.
     */
    fun layout(
        rich: RichText,
        maxWidth: Double? = null,
        fill: RichTextFill = RichTextFill(),
        linkSpanStyle: SpanStyle = DefaultLinkSpanStyle,
        exactWidth: Boolean = false,
    ): LaidOutRichText {
        val key = CacheKey(
            rich = rich,
            // Tenth-of-a-px quantization keeps float jitter from defeating the cache.
            maxWidthTenths = maxWidth?.let { (it * 10).roundToInt() },
            exactWidth = exactWidth,
            fill = fill,
            fontGeneration = fontProvider.generation,
            density = density.density,
            fontScale = density.fontScale,
        )
        cache[key]?.let { hit ->
            // LinkedHashMap has no LRU mode in common Kotlin; refresh insertion order.
            cache.remove(key)
            cache[key] = hit
            return hit
        }
        val laidOut = doLayout(rich, maxWidth, fill, linkSpanStyle, exactWidth)
        cache[key] = laidOut
        while (cache.size > cacheSize) cache.remove(cache.keys.first())
        return laidOut
    }

    private fun doLayout(
        rich: RichText,
        maxWidth: Double?,
        fill: RichTextFill,
        linkSpanStyle: SpanStyle,
        exactWidth: Boolean,
    ): LaidOutRichText {
        val composed = composeRichText(rich, density, fontProvider, fill, linkSpanStyle)
        val paragraphSpacing = rich.base.paragraphSpacing ?: 0.0
        val truncate = rich.truncate

        val paragraphs = mutableListOf<ParagraphLayout>()
        var y = 0.0
        var linesUsed = 0
        var truncated = false

        composed.paragraphs.forEachIndexed { index, spec ->
            if (truncated) return@forEachIndexed
            val remainingLines = truncate?.let { it.maxLines - linesUsed } ?: Int.MAX_VALUE
            if (remainingLines <= 0) {
                truncated = true
                return@forEachIndexed
            }
            if (index > 0) y += paragraphSpacing

            val contentMax = maxWidth?.let { (it - spec.indent).coerceAtLeast(1.0) }
            val constraints = when {
                contentMax == null -> Constraints()
                exactWidth -> {
                    val width = ceil(contentMax).toInt().coerceAtLeast(0)
                    Constraints(minWidth = width, maxWidth = width)
                }
                else -> Constraints(maxWidth = ceil(contentMax).toInt().coerceAtLeast(0))
            }
            val result = textMeasurer.measure(
                text = spec.annotated,
                style = spec.style,
                overflow = if (truncate?.ellipsis == true) TextOverflow.Ellipsis else TextOverflow.Clip,
                softWrap = maxWidth != null,
                maxLines = remainingLines,
                constraints = constraints,
            )
            val hangShift = if (spec.hangFirstChar && result.layoutInput.text.isNotEmpty()) {
                -result.getBoundingBox(0).width.toDouble()
            } else 0.0
            val marker = if (spec.marker.isEmpty()) null else textMeasurer.measure(
                text = spec.marker,
                style = spec.style.copy(
                    textAlign = androidx.compose.ui.text.style.TextAlign.Left,
                    textIndent = androidx.compose.ui.text.style.TextIndent.None,
                ),
                softWrap = false,
            )
            paragraphs += ParagraphLayout(
                spec = spec,
                result = result,
                markerLayout = marker,
                x = spec.indent + hangShift,
                y = y,
            )
            y += result.size.height.toDouble()
            linesUsed += result.lineCount
            if (truncate != null && linesUsed >= truncate.maxLines) truncated = true
        }

        val lines = buildList {
            paragraphs.forEach { paragraph ->
                val result = paragraph.result
                for (line in 0 until result.lineCount) {
                    val top = result.getLineTop(line).toDouble()
                    val bottom = result.getLineBottom(line).toDouble()
                    val left = result.getLineLeft(line).toDouble()
                    val right = result.getLineRight(line).toDouble()
                    add(
                        LineMetrics(
                            start = paragraph.spec.textStart + result.getLineStart(line),
                            end = paragraph.spec.textStart + result.getLineEnd(line, visibleEnd = true),
                            top = paragraph.y + top,
                            height = bottom - top,
                            baseline = paragraph.y + result.getLineBaseline(line).toDouble(),
                            left = paragraph.x + left,
                            width = right - left,
                        ),
                    )
                }
            }
        }

        val width = paragraphs.maxOfOrNull { it.x + it.result.size.width.toDouble() } ?: 0.0
        val measured = MeasuredRichText(
            width = width.coerceAtLeast(0.0),
            height = y.coerceAtLeast(rich.base.let { style ->
                if (paragraphs.isEmpty()) style.fontSizeOrDefault() else 0.0
            }),
            lines = lines,
        )
        return LaidOutRichText(
            rich = rich,
            transformed = composed.transformed,
            fill = composed.fill,
            paragraphs = paragraphs,
            measured = measured,
        )
    }

    private data class CacheKey(
        val rich: RichText,
        val maxWidthTenths: Int?,
        val exactWidth: Boolean,
        val fill: RichTextFill,
        val fontGeneration: Int,
        val density: Float,
        val fontScale: Float,
    )
}

internal data class ParagraphLayout(
    val spec: ParagraphSpec,
    val result: TextLayoutResult,
    val markerLayout: TextLayoutResult?,
    /** Paragraph origin inside the text box, document px. */
    val x: Double,
    val y: Double,
)

/**
 * A laid-out [RichText]: aggregate metrics plus caret/selection geometry.
 *
 * All offsets are in the *rendered* string space (after case transform); use
 * [transformed] to map to and from source offsets. `IntRange` results follow the
 * `first until last+1` convention: `range.first` is the start offset and
 * `range.last + 1` the exclusive end.
 */
class LaidOutRichText internal constructor(
    val rich: RichText,
    val transformed: CaseTransform.Transformed,
    val fill: RichTextFill,
    internal val paragraphs: List<ParagraphLayout>,
    val measured: MeasuredRichText,
) : TextSelectionGeometry {

    /** Rendered text length (case transform may change lengths). */
    val renderedLength: Int get() = transformed.text.length

    /**
     * Hit-rectangles for every hyperlink, in text-box coordinates. Link offsets (source
     * space) are projected through the case transform, then mapped through the layout with
     * the same per-line segmentation as [selectionRects]. Empty-rect links are dropped.
     */
    fun linkRects(): List<Pair<io.aequicor.visualization.subsystems.typography.LinkSpan, List<RectD>>> =
        rich.links.mapNotNull { link ->
            val start = transformed.toTransformed(link.start)
            val end = transformed.toTransformed(link.end)
            val rects = selectionRects(start, end)
            if (rects.isEmpty()) null else link to rects
        }

    /** The link whose hit-rects contain the text-box point, or null. */
    fun linkAt(x: Double, y: Double): io.aequicor.visualization.subsystems.typography.LinkSpan? =
        linkRects().firstOrNull { (_, rects) ->
            rects.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }
        }?.first

    override fun caretRect(offset: Int): RectD {
        val paragraph = paragraphAt(offset) ?: return RectD(0.0, 0.0, 0.0, rich.base.fontSizeOrDefault())
        val local = (offset - paragraph.spec.textStart)
            .coerceIn(0, paragraph.result.layoutInput.text.length)
        val rect = paragraph.result.getCursorRect(local)
        return RectD(
            left = paragraph.x + rect.left.toDouble(),
            top = paragraph.y + rect.top.toDouble(),
            width = rect.width.toDouble(),
            height = rect.height.toDouble(),
        )
    }

    override fun selectionRects(start: Int, end: Int): List<RectD> {
        if (end <= start) return emptyList()
        val rects = mutableListOf<RectD>()
        paragraphs.forEach { paragraph ->
            val length = paragraph.result.layoutInput.text.length
            val from = (start - paragraph.spec.textStart).coerceIn(0, length)
            val to = (end - paragraph.spec.textStart).coerceIn(0, length)
            if (to <= from && !(start <= paragraph.spec.textEnd && end > paragraph.spec.textEnd)) return@forEach
            if (to > from) {
                val result = paragraph.result
                val firstLine = result.getLineForOffset(from)
                val lastLine = result.getLineForOffset((to - 1).coerceAtLeast(from))
                for (line in firstLine..lastLine) {
                    val segStart = maxOf(from, result.getLineStart(line))
                    val segEnd = minOf(to, result.getLineEnd(line, visibleEnd = true))
                    if (segEnd <= segStart) continue
                    val a = result.getHorizontalPosition(segStart, usePrimaryDirection = true).toDouble()
                    val b = result.getHorizontalPosition(segEnd, usePrimaryDirection = true).toDouble()
                    val top = result.getLineTop(line).toDouble()
                    val bottom = result.getLineBottom(line).toDouble()
                    rects += RectD(
                        left = paragraph.x + minOf(a, b),
                        top = paragraph.y + top,
                        width = maxOf(a, b) - minOf(a, b),
                        height = bottom - top,
                    )
                }
            }
        }
        return rects
    }

    /** Precise (BiDi-correct) selection outline for painting, in text-box coordinates. */
    fun selectionPath(start: Int, end: Int): Path {
        val path = Path()
        if (end <= start) return path
        paragraphs.forEach { paragraph ->
            val length = paragraph.result.layoutInput.text.length
            val from = (start - paragraph.spec.textStart).coerceIn(0, length)
            val to = (end - paragraph.spec.textStart).coerceIn(0, length)
            if (to <= from) return@forEach
            val sub = paragraph.result.getPathForRange(from, to)
            sub.translate(Offset(paragraph.x.toFloat(), paragraph.y.toFloat()))
            path.addPath(sub)
        }
        return path
    }

    override fun offsetAt(x: Double, y: Double): Int {
        if (paragraphs.isEmpty()) return 0
        val paragraph = paragraphs.lastOrNull { y >= it.y } ?: paragraphs.first()
        val height = paragraph.result.size.height.toDouble()
        val clampedY = (y - paragraph.y).coerceIn(0.0, height - 0.01)
        val local = paragraph.result.getOffsetForPosition(
            Offset((x - paragraph.x).toFloat(), clampedY.toFloat()),
        )
        return paragraph.spec.textStart + local
    }

    override fun wordBoundaryAt(offset: Int): IntRange {
        val paragraph = paragraphAt(offset) ?: return IntRange.EMPTY
        val length = paragraph.result.layoutInput.text.length
        val local = (offset - paragraph.spec.textStart).coerceIn(0, length)
        val word = paragraph.result.getWordBoundary(local)
        return (paragraph.spec.textStart + word.start) until (paragraph.spec.textStart + word.end)
    }

    override fun lineBoundaryAt(offset: Int): IntRange {
        val paragraph = paragraphAt(offset) ?: return IntRange.EMPTY
        val length = paragraph.result.layoutInput.text.length
        val local = (offset - paragraph.spec.textStart).coerceIn(0, length)
        val line = paragraph.result.getLineForOffset(local)
        val start = paragraph.result.getLineStart(line)
        val end = paragraph.result.getLineEnd(line, visibleEnd = false)
        return (paragraph.spec.textStart + start) until (paragraph.spec.textStart + end)
    }

    private fun paragraphAt(offset: Int): ParagraphLayout? =
        paragraphs.firstOrNull { offset <= it.spec.textEnd } ?: paragraphs.lastOrNull()
}
