package io.aequicor.visualization.engine.frontend.edit

/**
 * Offset index over the SLM source: maps the 1-based (line, column) positions used
 * by the markdown/YAML parsers to string offsets. Built once per apply. Lines are
 * `\n`-delimited, matching the parsers' `split('\n')` view of the source.
 */
internal class LineIndex(private val source: String) {
    /** Offset of the first character of each 1-based line. */
    private val lineStarts: IntArray = buildList {
        add(0)
        source.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }.toIntArray()

    val lineCount: Int get() = lineStarts.size

    val endsWithNewline: Boolean get() = source.isNotEmpty() && source.last() == '\n'

    /** Offset of the character at 1-based [line] / [column]. */
    fun offsetOf(line: Int, column: Int): Int = lineStartOffset(line) + (column - 1)

    /**
     * Offset of the start of 1-based [line]. Lines past the end address the end of
     * the source, so inserts can append after the last line.
     */
    fun lineStartOffset(line: Int): Int =
        if (line <= lineCount) lineStarts[line - 1] else source.length

    /** Text of 1-based [line] without its trailing newline. */
    fun lineText(line: Int): String {
        if (line !in 1..lineCount) return ""
        val start = lineStarts[line - 1]
        val end = if (line < lineCount) lineStarts[line] - 1 else source.length
        return source.substring(start, maxOf(start, end))
    }

    /** Leading-space count of 1-based [line]. */
    fun indentOf(line: Int): Int = lineText(line).takeWhile { it == ' ' }.length
}
