package io.aequicor.visualization.subsystems.typography

/**
 * String case transforms with a bidirectional offset map, so span and selection offsets
 * (kept against the source string) can be projected onto the transformed string and
 * back. Case mapping may change lengths (e.g. `ß` -> `SS`), so a plain index copy is
 * not enough.
 *
 * [TextCasing.SmallCaps]/[TextCasing.SmallCapsForced] are rendering features
 * (OpenType `smcp`/`c2sc`), not string transforms — they map as identity here.
 */
object CaseTransform {

    data class Transformed(
        val text: String,
        /**
         * `sourceToTransformed[i]` is the transformed offset of source offset `i`;
         * size is `source.length + 1` (the last entry maps the end-of-string offset).
         */
        val sourceToTransformed: IntArray,
    ) {
        fun toTransformed(sourceOffset: Int): Int =
            sourceToTransformed[sourceOffset.coerceIn(0, sourceToTransformed.size - 1)]

        /** Inverse projection; offsets inside an expansion snap to its source character. */
        fun toSource(transformedOffset: Int): Int {
            if (sourceToTransformed.isEmpty()) return 0
            val clamped = transformedOffset.coerceIn(0, sourceToTransformed.last())
            // sourceToTransformed is monotonically non-decreasing: binary search the
            // last source offset whose transformed position is <= clamped.
            var low = 0
            var high = sourceToTransformed.size - 1
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (sourceToTransformed[mid] <= clamped) low = mid else high = mid - 1
            }
            return low
        }

        override fun equals(other: Any?): Boolean =
            other is Transformed && text == other.text &&
                sourceToTransformed.contentEquals(other.sourceToTransformed)

        override fun hashCode(): Int = 31 * text.hashCode() + sourceToTransformed.contentHashCode()
    }

    fun apply(text: String, casing: TextCasing): Transformed = when (casing) {
        TextCasing.None, TextCasing.SmallCaps, TextCasing.SmallCapsForced -> identity(text)
        TextCasing.Upper -> mapPerChar(text) { ch, _ -> ch.uppercase() }
        TextCasing.Lower -> mapPerChar(text) { ch, _ -> ch.lowercase() }
        TextCasing.Title -> {
            var startOfWord = true
            mapPerChar(text) { ch, _ ->
                val mapped = if (startOfWord && ch.isLetter()) ch.titlecaseChar().toString() else ch.toString()
                startOfWord = !ch.isLetter() && !ch.isDigit()
                mapped
            }
        }
    }

    /** Projects spans onto the transformed string (for rendering against it). */
    fun projectSpans(spans: List<StyleSpan>, transformed: Transformed): List<StyleSpan> =
        spans.mapNotNull { span ->
            val start = transformed.toTransformed(span.start)
            val end = transformed.toTransformed(span.end)
            if (end <= start) null else span.copy(start = start, end = end)
        }

    /** Projects links onto the transformed string. */
    fun projectLinks(links: List<LinkSpan>, transformed: Transformed): List<LinkSpan> =
        links.mapNotNull { link ->
            val start = transformed.toTransformed(link.start)
            val end = transformed.toTransformed(link.end)
            if (end <= start) null else link.copy(start = start, end = end)
        }

    private fun identity(text: String): Transformed =
        Transformed(text, IntArray(text.length + 1) { it })

    private inline fun mapPerChar(text: String, transform: (Char, Int) -> String): Transformed {
        val out = StringBuilder(text.length)
        val map = IntArray(text.length + 1)
        text.forEachIndexed { index, ch ->
            map[index] = out.length
            out.append(transform(ch, index))
        }
        map[text.length] = out.length
        return Transformed(out.toString(), map)
    }
}
