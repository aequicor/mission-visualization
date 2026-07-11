package io.aequicor.visualization.engine.frontend.yaml

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector

/**
 * Hand-rolled YAML-subset parser for SLM **frontmatter only** — YAML's sole remaining
 * surface in SLM. Typed content is authored as CNL and never round-trips through YAML.
 *
 * Supported: indent-nested maps (insertion-ordered), `- ` block lists (including map
 * items with sibling entries and continuations), scalars (`null`/`~`, booleans, numbers
 * as [Double], double-/single-quoted and plain strings — `$token` refs and `{{expr}}`
 * stay plain strings), single-line inline `[..]`/`{..}`, full-line and trailing ` #`
 * comments.
 *
 * Unsupported constructs are reported as diagnostics and the failing entry is skipped
 * while parsing continues: tabs in indentation, duplicate keys, anchors/aliases/tags
 * (`&` `*` `!`), multiline block scalars (`|` `>`), unclosed inline collections.
 *
 * [startLine] is the absolute 1-based line of the first line of [text], so all reported
 * positions are absolute in the enclosing SLM file.
 */
fun parseSlmYaml(
    text: String,
    diagnostics: DiagnosticCollector,
    startLine: Int = 1,
): YamlValue? = YamlSubsetParser(text, diagnostics, startLine).parse()

private class YamlSubsetParser(
    text: String,
    private val diagnostics: DiagnosticCollector,
    startLine: Int,
) {
    private class Row(val absLine: Int, val indent: Int, val content: String)

    private val rows: List<Row> = buildRows(text, startLine)

    fun parse(): YamlValue? {
        if (rows.isEmpty()) return null
        val (value, next) = parseBlockValue(0)
        if (next < rows.size) {
            diagnostics.error("Unexpected content after YAML document", rows[next].absLine)
        }
        return value
    }

    private fun buildRows(text: String, startLine: Int): List<Row> {
        val result = mutableListOf<Row>()
        text.split('\n').forEachIndexed { index, rawLine ->
            val absLine = startLine + index
            val line = rawLine.removeSuffix("\r")
            val leading = line.takeWhile { it == ' ' || it == '\t' }
            if ('\t' in leading) {
                diagnostics.error("Tab character in YAML indentation", absLine)
                return@forEachIndexed
            }
            val content = stripTrailingComment(line).trim()
            if (content.isEmpty()) return@forEachIndexed
            result += Row(absLine, leading.length, content)
        }
        return result
    }

    /**
     * Cuts a ` # comment` (whitespace-preceded, outside quotes) and full-line comments.
     * An unquoted `#RGB`/`#RRGGBB`/`#RRGGBBAA` hex color is NOT a comment: weak models
     * routinely write `color: #1E293B` without quotes, so we keep it as a value token.
     */
    private fun stripTrailingComment(line: String): String {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            when (line[i]) {
                '\\' -> if (inDouble) i++
                '"' -> if (!inSingle) inDouble = !inDouble
                '\'' -> if (!inDouble) inSingle = !inSingle
                '#' -> if (!inSingle && !inDouble) {
                    val prev = line.getOrNull(i - 1)
                    if ((prev == null || prev == ' ' || prev == '\t') && !isHexColorToken(line, i)) {
                        return line.take(i)
                    }
                }
            }
            i++
        }
        return line
    }

    /** `#` at [at] followed by 3/4/6/8 hex digits and a value boundary — a color, not a comment. */
    private fun isHexColorToken(line: String, at: Int): Boolean {
        var j = at + 1
        while (j < line.length && line[j].digitToIntOrNull(16) != null) j++
        val length = j - (at + 1)
        if (length != 3 && length != 4 && length != 6 && length != 8) return false
        val after = line.getOrNull(j)
        return after == null || after == ' ' || after == '\t' ||
            after == ',' || after == '}' || after == ']'
    }

    private fun isListItemRow(content: String): Boolean =
        content == "-" || content.startsWith("- ")

    private fun isBlockScalarIndicator(rest: String): Boolean =
        rest.isNotEmpty() && (rest[0] == '|' || rest[0] == '>') &&
            rest.drop(1).all { it == '+' || it == '-' || it.isDigit() }

    /** First `:` followed by space/end, outside quotes → (key, trimmed rest). */
    private fun splitKey(content: String): Pair<String, String>? {
        var inSingle = false
        var inDouble = false
        for (i in content.indices) {
            when (content[i]) {
                '"' -> if (!inSingle) inDouble = !inDouble
                '\'' -> if (!inDouble) inSingle = !inSingle
                ':' -> if (!inSingle && !inDouble &&
                    (i == content.lastIndex || content[i + 1] == ' ')
                ) {
                    val key = content.take(i).trim()
                    if (key.isEmpty()) return null
                    return key to content.drop(i + 1).trim()
                }
            }
        }
        return null
    }

    private fun parseBlockValue(pos: Int): Pair<YamlValue?, Int> {
        val row = rows[pos]
        return when {
            isListItemRow(row.content) -> parseList(pos, row.indent)
            splitKey(row.content) != null -> parseMap(pos, row.indent)
            else -> parseFlowOrScalar(row.content, row.absLine, row.indent + 1) to pos + 1
        }
    }

    private fun parseMap(pos: Int, indent: Int): Pair<YamlValue?, Int> {
        val first = rows[pos]
        val entries = LinkedHashMap<String, YamlValue>()
        var endLine = first.absLine
        var endColumn = first.indent + 1
        var i = pos
        while (i < rows.size) {
            val row = rows[i]
            if (row.indent > indent) {
                diagnostics.error("Unexpected indentation", row.absLine)
                i++
                continue
            }
            if (row.indent < indent || isListItemRow(row.content)) break
            val kv = splitKey(row.content)
            if (kv == null) {
                diagnostics.error("Expected `key: value`", row.absLine)
                i++
                continue
            }
            val (key, rest) = kv
            val (value, next) = parseEntryValue(rest, row.content, row.indent, row.absLine, indent, i + 1)
            i = next
            if (value == null) continue
            if (entries.containsKey(key)) {
                diagnostics.error("Duplicate key \"$key\"", row.absLine)
                continue
            }
            entries[key] = value
            if (value.endLine > endLine || (value.endLine == endLine && value.endColumn > endColumn)) {
                endLine = value.endLine
                endColumn = value.endColumn
            }
        }
        return YamlMap(entries, first.absLine, indent + 1, endLine, endColumn) to i
    }

    private fun parseList(pos: Int, indent: Int): Pair<YamlValue?, Int> {
        val first = rows[pos]
        val items = mutableListOf<YamlValue>()
        var endLine = first.absLine
        var endColumn = first.indent + 1
        var i = pos
        while (i < rows.size) {
            val row = rows[i]
            if (row.indent > indent) {
                diagnostics.error("Unexpected indentation", row.absLine)
                i++
                continue
            }
            if (row.indent < indent || !isListItemRow(row.content)) break
            val afterDash = row.content.drop(1)
            val leadingSpaces = afterDash.takeWhile { it == ' ' }.length
            val itemText = afterDash.trimStart()
            val itemIndent = row.indent + 1 + leadingSpaces
            i++
            val value: YamlValue?
            if (itemText.isEmpty()) {
                value = if (i < rows.size && rows[i].indent > row.indent) {
                    val (nested, next) = parseBlockValue(i)
                    i = next
                    nested
                } else {
                    YamlScalar(null, "", row.absLine, row.indent + 3)
                }
            } else if (isBlockScalarIndicator(itemText)) {
                diagnostics.error("Multiline block scalars (| and >) are not supported", row.absLine)
                while (i < rows.size && rows[i].indent > row.indent) i++
                value = null
            } else if (splitKey(itemText) != null) {
                val (mapItem, next) = parseDashMap(itemText, row, itemIndent, i)
                i = next
                value = mapItem
            } else {
                value = parseFlowOrScalar(itemText, row.absLine, itemIndent + 1)
            }
            if (value == null) continue
            items += value
            if (value.endLine > endLine || (value.endLine == endLine && value.endColumn > endColumn)) {
                endLine = value.endLine
                endColumn = value.endColumn
            }
        }
        return YamlList(items, first.absLine, indent + 1, endLine, endColumn) to i
    }

    /** Map list item: first entry on the dash line, sibling entries below at [itemIndent]. */
    private fun parseDashMap(
        firstContent: String,
        dashRow: Row,
        itemIndent: Int,
        nextPos: Int,
    ): Pair<YamlValue?, Int> {
        val entries = LinkedHashMap<String, YamlValue>()
        var endLine = dashRow.absLine
        var endColumn = itemIndent + 1
        var i = nextPos

        fun store(key: String, value: YamlValue?, atLine: Int) {
            if (value == null) return
            if (entries.containsKey(key)) {
                diagnostics.error("Duplicate key \"$key\"", atLine)
                return
            }
            entries[key] = value
            if (value.endLine > endLine || (value.endLine == endLine && value.endColumn > endColumn)) {
                endLine = value.endLine
                endColumn = value.endColumn
            }
        }

        val (firstKey, firstRest) = splitKey(firstContent) ?: return null to nextPos
        val (firstValue, afterFirst) =
            parseEntryValue(firstRest, firstContent, itemIndent, dashRow.absLine, itemIndent, i)
        i = afterFirst
        store(firstKey, firstValue, dashRow.absLine)

        while (i < rows.size) {
            val row = rows[i]
            if (row.indent > itemIndent) {
                diagnostics.error("Unexpected indentation", row.absLine)
                i++
                continue
            }
            if (row.indent < itemIndent || isListItemRow(row.content)) break
            val kv = splitKey(row.content)
            if (kv == null) {
                diagnostics.error("Expected `key: value`", row.absLine)
                i++
                continue
            }
            val (value, next) = parseEntryValue(kv.second, row.content, row.indent, row.absLine, itemIndent, i + 1)
            i = next
            store(kv.first, value, row.absLine)
        }
        return YamlMap(entries, dashRow.absLine, itemIndent + 1, endLine, endColumn) to i
    }

    /**
     * Value of one `key: …` entry. [entryContent] is the trimmed entry text starting at
     * 0-based column [contentIndent]; [keyIndent] governs which following rows are the
     * nested block of an empty-rest entry.
     */
    private fun parseEntryValue(
        rest: String,
        entryContent: String,
        contentIndent: Int,
        absLine: Int,
        keyIndent: Int,
        nextPos: Int,
    ): Pair<YamlValue?, Int> {
        if (rest.isNotEmpty()) {
            if (isBlockScalarIndicator(rest)) {
                diagnostics.error("Multiline block scalars (| and >) are not supported", absLine)
                var j = nextPos
                while (j < rows.size && rows[j].indent > keyIndent) j++
                return null to j
            }
            val valueColumn = contentIndent + (entryContent.length - rest.length) + 1
            return parseFlowOrScalar(rest, absLine, valueColumn) to nextPos
        }
        if (nextPos < rows.size) {
            val next = rows[nextPos]
            if (next.indent > keyIndent || (next.indent == keyIndent && isListItemRow(next.content))) {
                return parseBlockValue(nextPos)
            }
        }
        return YamlScalar(null, "", absLine, contentIndent + entryContent.length + 2) to nextPos
    }

    private fun parseFlowOrScalar(text: String, absLine: Int, column: Int): YamlValue? =
        when {
            text.startsWith("{{") -> parseScalarToken(text, absLine, column)
            else -> parseFlowOrScalarDispatch(text, absLine, column)
        }

    private fun parseFlowOrScalarDispatch(text: String, absLine: Int, column: Int): YamlValue? =
        when (text.first()) {
            '[', '{' -> parseFlow(text, absLine, column)
            '&', '*' -> {
                diagnostics.error("YAML anchors and aliases are not supported", absLine)
                null
            }
            '!' -> {
                diagnostics.error("YAML tags are not supported", absLine)
                null
            }
            else -> parseScalarToken(text, absLine, column)
        }

    // --- inline (flow) collections, single-line only ---

    private class FlowCursor(val text: String) {
        var pos: Int = 0

        val atEnd: Boolean get() = pos >= text.length

        fun peek(): Char? = text.getOrNull(pos)

        fun skipWs() {
            while (pos < text.length && text[pos] == ' ') pos++
        }
    }

    private fun parseFlow(text: String, absLine: Int, column: Int): YamlValue? {
        val cursor = FlowCursor(text)
        val value = readFlowValue(cursor, absLine, column) ?: return null
        cursor.skipWs()
        if (!cursor.atEnd) {
            diagnostics.error("Unexpected content after inline collection", absLine)
        }
        return value
    }

    private fun readFlowValue(cursor: FlowCursor, absLine: Int, baseColumn: Int): YamlValue? {
        cursor.skipWs()
        return when {
            cursor.atEnd -> {
                diagnostics.error("Unclosed inline collection", absLine)
                null
            }
            cursor.text.startsWith("{{", cursor.pos) -> readFlowScalarValue(cursor, absLine, baseColumn)
            cursor.peek() == '[' -> readFlowList(cursor, absLine, baseColumn)
            cursor.peek() == '{' -> readFlowMap(cursor, absLine, baseColumn)
            else -> readFlowScalarValue(cursor, absLine, baseColumn)
        }
    }

    private fun readFlowScalarValue(cursor: FlowCursor, absLine: Int, baseColumn: Int): YamlValue? {
        val start = cursor.pos
        val token = readFlowScalarToken(cursor) ?: run {
            diagnostics.error("Unclosed quoted string in inline collection", absLine)
            return null
        }
        return if (token.isEmpty()) {
            YamlScalar(null, "", absLine, baseColumn + start)
        } else {
            parseScalarToken(token, absLine, baseColumn + start)
        }
    }

    private fun readFlowList(cursor: FlowCursor, absLine: Int, baseColumn: Int): YamlValue? {
        val startColumn = baseColumn + cursor.pos
        cursor.pos++ // consume '['
        val items = mutableListOf<YamlValue>()
        while (true) {
            cursor.skipWs()
            when (cursor.peek()) {
                null -> {
                    diagnostics.error("Unclosed inline list", absLine)
                    return null
                }
                ']' -> {
                    cursor.pos++
                    return YamlList(items, absLine, startColumn, absLine, baseColumn + cursor.pos)
                }
                ',' -> cursor.pos++
                else -> items += readFlowValue(cursor, absLine, baseColumn) ?: return null
            }
        }
    }

    private fun readFlowMap(cursor: FlowCursor, absLine: Int, baseColumn: Int): YamlValue? {
        val startColumn = baseColumn + cursor.pos
        cursor.pos++ // consume '{'
        val entries = LinkedHashMap<String, YamlValue>()
        while (true) {
            cursor.skipWs()
            when (cursor.peek()) {
                null -> {
                    diagnostics.error("Unclosed inline map", absLine)
                    return null
                }
                '}' -> {
                    cursor.pos++
                    return YamlMap(entries, absLine, startColumn, absLine, baseColumn + cursor.pos)
                }
                ',' -> cursor.pos++
                else -> {
                    val keyStart = cursor.pos
                    while (!cursor.atEnd && cursor.peek() != ':' && cursor.peek() != '}') cursor.pos++
                    if (cursor.peek() != ':') {
                        diagnostics.error("Expected `key: value` in inline map", absLine)
                        return null
                    }
                    val key = cursor.text.substring(keyStart, cursor.pos).trim().removeSurrounding("\"")
                    cursor.pos++ // consume ':'
                    val value = readFlowValue(cursor, absLine, baseColumn) ?: return null
                    if (entries.containsKey(key)) {
                        diagnostics.error("Duplicate key \"$key\"", absLine)
                    } else {
                        entries[key] = value
                    }
                }
            }
        }
    }

    /** Consumes one scalar token inside a flow collection; null on unclosed quote. */
    private fun readFlowScalarToken(cursor: FlowCursor): String? {
        val text = cursor.text
        val start = cursor.pos
        val quote = text[start]
        if (quote == '"' || quote == '\'') {
            var i = start + 1
            while (i < text.length) {
                val c = text[i]
                if (quote == '"' && c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) {
                    cursor.pos = i + 1
                    return text.substring(start, i + 1)
                }
                i++
            }
            return null
        }
        var i = start
        if (text.startsWith("{{", start)) {
            val close = text.indexOf("}}", start + 2)
            i = if (close >= 0) close + 2 else start + 2
        }
        while (i < text.length && text[i] != ',' && text[i] != ']' && text[i] != '}') i++
        cursor.pos = i
        return text.substring(start, i).trim()
    }

    // --- scalars ---

    private val numberRegex = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

    private fun parseScalarToken(token: String, absLine: Int, column: Int): YamlScalar? =
        when {
            token == "null" || token == "~" -> YamlScalar(null, token, absLine, column)
            token == "true" -> YamlScalar(true, token, absLine, column)
            token == "false" -> YamlScalar(false, token, absLine, column)
            token.startsWith("\"") -> parseQuoted(token, '"', absLine, column)
            token.startsWith("'") -> parseQuoted(token, '\'', absLine, column)
            numberRegex.matches(token) -> YamlScalar(token.toDouble(), token, absLine, column)
            token.startsWith("&") || token.startsWith("*") -> {
                diagnostics.error("YAML anchors and aliases are not supported", absLine)
                null
            }
            token.startsWith("!") -> {
                diagnostics.error("YAML tags are not supported", absLine)
                null
            }
            else -> YamlScalar(token, token, absLine, column)
        }

    private fun parseQuoted(token: String, quote: Char, absLine: Int, column: Int): YamlScalar? {
        val builder = StringBuilder()
        var i = 1
        while (i < token.length) {
            val c = token[i]
            when {
                quote == '"' && c == '\\' && i + 1 < token.length -> {
                    builder.append(
                        when (val esc = token[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> esc
                        },
                    )
                    i += 2
                }
                quote == '\'' && c == '\'' && token.getOrNull(i + 1) == '\'' -> {
                    builder.append('\'')
                    i += 2
                }
                c == quote -> {
                    val raw = token.take(i + 1)
                    if (token.drop(i + 1).isNotBlank()) {
                        diagnostics.error("Unexpected content after quoted scalar", absLine)
                    }
                    return YamlScalar(builder.toString(), raw, absLine, column)
                }
                else -> {
                    builder.append(c)
                    i++
                }
            }
        }
        diagnostics.error("Unclosed quoted string", absLine)
        return null
    }
}
