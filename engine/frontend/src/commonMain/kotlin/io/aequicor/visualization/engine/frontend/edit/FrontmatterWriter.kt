package io.aequicor.visualization.engine.frontend.edit

/** Surgical writer for the small screen-level contract that lives in SLM frontmatter. */
internal object FrontmatterWriter {
    fun setScreenFrame(source: String, lineIndex: LineIndex, edit: SetScreenFrame): WritePlan {
        if (edit.width == null && edit.height == null) return WritePlan.Ops(emptyList())
        if (lineIndex.lineText(1).trimEnd('\r') != "---") {
            return WritePlan.Failed("Screen frame write-back requires YAML frontmatter", 1)
        }
        val closingLine = (2..lineIndex.lineCount).firstOrNull { line ->
            lineIndex.lineText(line).trimEnd('\r').trim() == "---"
        } ?: return WritePlan.Failed("Screen frame write-back cannot find the closing frontmatter fence", 1)
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val frameLine = (2 until closingLine).firstOrNull { line ->
            val text = lineIndex.lineText(line).trimEnd('\r')
            text.takeWhile { it == ' ' }.isEmpty() && text.substringBefore('#').trimStart().startsWith("frame:")
        }
        if (frameLine == null) {
            val entries = buildList {
                edit.width?.let { add("  width: ${number(it)}") }
                edit.height?.let { add("  height: ${number(it)}") }
            }
            val block = (listOf("frame:") + entries).joinToString(newline, postfix = newline)
            val offset = lineIndex.lineStartOffset(closingLine)
            return WritePlan.Ops(listOf(TextOp(offset, offset, block)))
        }

        val frameText = lineIndex.lineText(frameLine).trimEnd('\r')
        val frameValue = frameText.substringAfter(':').substringBefore('#').trim()
        if (frameValue.isNotEmpty()) {
            val original = lineIndex.lineText(frameLine)
            val carriage = if (original.endsWith('\r')) "\r" else ""
            val replacement = rewriteInlineFrame(frameText, edit)
                ?: return WritePlan.Failed("Inline `frame` frontmatter is not a writable flow mapping", frameLine)
            val start = lineIndex.lineStartOffset(frameLine)
            return WritePlan.Ops(listOf(TextOp(start, start + original.length, replacement + carriage)))
        }
        val frameIndent = lineIndex.indentOf(frameLine)
        val blockEndLine = ((frameLine + 1) until closingLine).firstOrNull { line ->
            val text = lineIndex.lineText(line).trimEnd('\r')
            text.isNotBlank() && lineIndex.indentOf(line) <= frameIndent
        } ?: closingLine
        val childLines = ((frameLine + 1) until blockEndLine).filter { line ->
            val text = lineIndex.lineText(line).trimEnd('\r')
            text.isNotBlank() && !text.trimStart().startsWith('#') && lineIndex.indentOf(line) > frameIndent
        }
        val childIndent = childLines.firstOrNull()?.let(lineIndex::indentOf) ?: (frameIndent + 2)
        val ops = mutableListOf<TextOp>()
        val missing = mutableListOf<Pair<String, Double>>()

        fun set(key: String, value: Double?) {
            if (value == null) return
            val line = childLines.firstOrNull { childLine ->
                lineIndex.lineText(childLine).trimEnd('\r').trimStart().startsWith("$key:")
            }
            if (line == null) {
                missing += key to value
                return
            }
            val original = lineIndex.lineText(line)
            val carriage = if (original.endsWith('\r')) "\r" else ""
            val withoutCarriage = original.removeSuffix("\r")
            val colon = withoutCarriage.indexOf(':')
            val commentAt = withoutCarriage.indexOf('#', startIndex = colon + 1)
            val comment = if (commentAt >= 0) withoutCarriage.substring(commentAt).trimStart() else ""
            val replacement = buildString {
                append(withoutCarriage.substring(0, colon + 1))
                append(' ')
                append(number(value))
                if (comment.isNotEmpty()) append("  ").append(comment)
                append(carriage)
            }
            val start = lineIndex.lineStartOffset(line)
            ops += TextOp(start, start + original.length, replacement)
        }

        set("width", edit.width)
        set("height", edit.height)
        if (missing.isNotEmpty()) {
            val offset = lineIndex.lineStartOffset(blockEndLine)
            val text = missing.joinToString(newline, postfix = newline) { (key, value) ->
                " ".repeat(childIndent) + "$key: ${number(value)}"
            }
            ops += TextOp(offset, offset, text)
        }
        return WritePlan.Ops(ops)
    }

    /**
     * Rewrites `frame: { width: 128, height: 296 }` without normalizing the rest of the line.
     * Flow entries may contain quoted or nested values; only top-level width/height values are
     * replaced, and a missing requested axis is appended before the closing brace.
     */
    private fun rewriteInlineFrame(line: String, edit: SetScreenFrame): String? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val open = line.indexOf('{', startIndex = colon + 1)
        if (open < 0) return null
        val close = matchingBrace(line, open) ?: return null
        var rewritten = line

        fun set(key: String, value: Double?) {
            if (value == null) return
            val currentClose = matchingBrace(rewritten, open) ?: return
            val span = flowValueSpan(rewritten, open, currentClose, key)
            rewritten = if (span != null) {
                rewritten.replaceRange(span.first, span.last + 1, number(value))
            } else {
                appendFlowEntry(rewritten, open, currentClose, key, number(value))
            }
        }

        set("width", edit.width)
        set("height", edit.height)
        return rewritten
    }

    private fun appendFlowEntry(line: String, open: Int, close: Int, key: String, value: String): String {
        val body = line.substring(open + 1, close)
        if (body.isBlank()) {
            val spaced = body.isNotEmpty()
            val entry = if (spaced) " $key: $value " else "$key: $value"
            return line.replaceRange(open + 1, close, entry)
        }
        val trailingWhitespace = body.takeLastWhile(Char::isWhitespace)
        val insertAt = close - trailingWhitespace.length
        return line.substring(0, insertAt) + ", $key: $value" + line.substring(insertAt)
    }

    /** Inclusive span of a top-level flow-mapping value, excluding its surrounding whitespace. */
    private fun flowValueSpan(line: String, open: Int, close: Int, wantedKey: String): IntRange? {
        flowEntryRanges(line, open + 1, close).forEach { entry ->
            val colon = topLevelColon(line, entry.first, entry.last + 1) ?: return@forEach
            val key = line.substring(entry.first, colon).trim().removeSurrounding("\"").removeSurrounding("'")
            if (key != wantedKey) return@forEach
            val start = (colon + 1..entry.last).firstOrNull { !line[it].isWhitespace() } ?: return null
            val end = (entry.last downTo start).first { !line[it].isWhitespace() }
            return start..end
        }
        return null
    }

    private fun flowEntryRanges(line: String, start: Int, endExclusive: Int): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var entryStart = start
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in start until endExclusive) {
            val char = line[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\' && quote == '"') escaped = true
                else if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '{', '[', '(' -> depth++
                '}', ']', ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) {
                    if (entryStart < index) ranges += entryStart until index
                    entryStart = index + 1
                }
            }
        }
        if (entryStart < endExclusive) ranges += entryStart until endExclusive
        return ranges
    }

    private fun topLevelColon(line: String, start: Int, endExclusive: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in start until endExclusive) {
            val char = line[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\' && quote == '"') escaped = true
                else if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '{', '[', '(' -> depth++
                '}', ']', ')' -> if (depth > 0) depth--
                ':' -> if (depth == 0) return index
            }
        }
        return null
    }

    private fun matchingBrace(line: String, open: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in open until line.length) {
            val char = line[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\' && quote == '"') escaped = true
                else if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun number(value: Double): String = ScalarFormatter.format(YamlScalarValue.Num(value))
}
