package io.aequicor.visualization.subsystems.typography

/**
 * List presentation: turns a [RichText] with a [TextListSpec] into per-paragraph
 * markers and indent geometry. Paragraphs are `\n`-separated slices of the text.
 */
object ListLayout {

    const val BULLET = "•"

    /** Indent step per level, in ems of the base font size (Figma uses ~1.5em). */
    const val INDENT_EM = 1.5

    data class Paragraph(
        val index: Int,
        /** Text offsets of the paragraph body, excluding the trailing `\n`. */
        val start: Int,
        val end: Int,
        /** Rendered marker (e.g. `•` or `3.`), empty for [ListType.None]. */
        val marker: String,
        /** Indent level >= 0; level 0 already indents one step for a list. */
        val level: Int,
    )

    fun paragraphs(text: String, list: TextListSpec): List<Paragraph> {
        val result = mutableListOf<Paragraph>()
        var start = 0
        var ordinal = 1
        var index = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline == -1) text.length else newline
            val marker = when (list.type) {
                ListType.None -> ""
                ListType.Bullet -> BULLET
                ListType.Ordered -> "${ordinal++}."
            }
            result += Paragraph(
                index = index++,
                start = start,
                end = end,
                marker = marker,
                level = if (list.type == ListType.None) 0 else list.indent.coerceAtLeast(0),
            )
            if (newline == -1) break
            start = newline + 1
        }
        return result
    }

    /** Horizontal space reserved for markers + indent, px against [fontSize]. */
    fun indentPx(list: TextListSpec, fontSize: Double): Double =
        if (list.type == ListType.None) 0.0
        else (list.indent.coerceAtLeast(0) + 1) * INDENT_EM * fontSize
}
