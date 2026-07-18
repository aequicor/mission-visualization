package io.aequicor.visualization.subsystems.diagrams.text

/** The typography a diagram label is measured and drawn with. */
public data class DiagramTextStyle(
    val fontSize: Double = DIAGRAM_LABEL_TEXT_SIZE,
    val fontWeight: Int? = null,
    val italic: Boolean = false,
)

/** Default label size in document px; mirrors the compose renderer's canonical label font. */
public const val DIAGRAM_LABEL_TEXT_SIZE: Double = 13.0

/**
 * The result of laying a label out: its natural extent plus the lines it broke into.
 *
 * [width] is the widest line, never the box width — callers sizing a shape to its text depend
 * on that (see [DiagramTextMeasurer.measure]).
 */
public data class MeasuredDiagramText(
    val width: Double,
    val height: Double,
    val lines: List<String>,
)

/**
 * Platform text measurement injected into the pure diagram core, mirroring
 * `DesignTextMeasurer` in `:engine:ir`: the core stays free of Compose, the UI layer supplies
 * the real implementation, and tests use a deterministic fake.
 */
public interface DiagramTextMeasurer {

    /**
     * Lays [text] out in [style], wrapping at [maxWidth] when it is non-null.
     *
     * With `maxWidth = null` the text is measured **unwrapped**, and [MeasuredDiagramText.width]
     * is its natural width — the only input from which a hug size may be derived.
     */
    public fun measure(
        text: String,
        style: DiagramTextStyle = DiagramTextStyle(),
        maxWidth: Double? = null,
    ): MeasuredDiagramText
}

/**
 * Deterministic approximation: the default for the pure core, for SVG export and for lint.
 *
 * Two deliberate differences from the older approximations in this repo:
 * - **Factor 0.54**, not 0.6. Measured Inter advances average 0.540 of the font size for
 *   Cyrillic and 0.521 for Latin; 0.6 overestimates a 42-character Cyrillic label by ~11%.
 *   `:engine:ir`'s 0.6 is deliberately left alone — changing it would move layout goldens.
 * - **Wraps on word boundaries**, like Compose. The per-character wrapping used by
 *   `ApproximateTextMeasurer` and the lint helpers undercounts lines, because it packs a
 *   fragment of the next word onto the current line where Compose would break.
 *
 * A word longer than the box still breaks mid-word, as Compose does when a word cannot fit.
 */
public class ApproximateDiagramTextMeasurer(
    private val averageCharWidthFactor: Double = 0.54,
    private val lineHeightFactor: Double = 1.25,
) : DiagramTextMeasurer {

    override fun measure(
        text: String,
        style: DiagramTextStyle,
        maxWidth: Double?,
    ): MeasuredDiagramText {
        val advance = style.fontSize * averageCharWidthFactor
        val lineHeight = style.fontSize * lineHeightFactor
        if (text.isEmpty()) return MeasuredDiagramText(0.0, lineHeight, listOf(""))

        val contentMax = maxWidth?.takeIf { it > 0.0 }
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (contentMax == null) {
                lines += paragraph
                return@forEach
            }
            lines += wrapParagraph(paragraph, advance, contentMax)
        }
        val width = lines.maxOf { it.length * advance }
        return MeasuredDiagramText(
            width = width,
            height = lines.size * lineHeight,
            lines = lines,
        )
    }

    private fun wrapParagraph(paragraph: String, advance: Double, contentMax: Double): List<String> {
        if (paragraph.isEmpty()) return listOf("")
        val maxChars = maxOf(1, (contentMax / advance).toInt())
        val out = mutableListOf<String>()
        var current = ""

        fun flush() {
            out += current
            current = ""
        }

        paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
            var rest = word
            // A word wider than the whole box breaks mid-word, as Compose does.
            while (rest.length > maxChars) {
                if (current.isNotEmpty()) flush()
                out += rest.take(maxChars)
                rest = rest.drop(maxChars)
            }
            if (rest.isEmpty()) return@forEach
            val candidate = if (current.isEmpty()) rest else "$current $rest"
            if (candidate.length <= maxChars) {
                current = candidate
            } else {
                flush()
                current = rest
            }
        }
        if (current.isNotEmpty()) flush()
        return out.ifEmpty { listOf("") }
    }
}
