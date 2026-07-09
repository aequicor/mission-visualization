package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.cnl.CnlParser
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.parseSlmYaml

/**
 * Hand-rolled block-level SLM markdown parser: frontmatter split, ATX headings 1–6,
 * paragraphs, nested lists, blockquotes, standalone images, GFM tables, fenced code
 * blocks, HTML comments, and typed attribute blocks per the five detection rules
 * (reserved key at block start, anchor-indent match, group merging, blank-line group
 * closing, near-miss hint with prose fallback). Unfenced `ir:` is an error.
 */
class SlmMarkdownParser(private val diagnostics: DiagnosticCollector) {
    private val inlineParser = SlmInlineParser(diagnostics)

    fun parse(source: String): SlmMarkdownDocument {
        val rawLines = source.split('\n').map { it.removeSuffix("\r") }
        var frontmatter: RawYamlBlock? = null
        var bodyStart = 0
        if (rawLines.isNotEmpty() && rawLines[0].trim() == "---") {
            val closing = (1 until rawLines.size).firstOrNull { rawLines[it].trim() == "---" }
            if (closing == null) {
                diagnostics.error("Unclosed frontmatter: missing closing ---", 1)
                bodyStart = 1
            } else {
                frontmatter = RawYamlBlock(
                    text = rawLines.subList(1, closing).joinToString("\n"),
                    startLine = 2,
                    span = SlmSourceSpan(1, closing + 1),
                )
                bodyStart = closing + 1
            }
        }
        val window = (bodyStart until rawLines.size).map { Line(it + 1, rawLines[it], 0) }
        return SlmMarkdownDocument(diagnostics.fileName, frontmatter, parseBlocks(window))
    }

    /** One source line inside a (possibly stripped) parsing window. */
    private data class Line(val number: Int, val text: String, val columnOffset: Int) {
        val isBlank: Boolean get() = text.isBlank()
        val indent: Int get() = text.takeWhile { it == ' ' }.length
    }

    // --- block loop ---

    private fun parseBlocks(lines: List<Line>): List<SlmBlock> {
        val blocks = mutableListOf<SlmBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank -> i++

                headingMatch(line.text) != null -> {
                    val (heading, attrs) = parseHeading(line)
                    blocks += heading
                    attrs?.let { blocks += it }
                    i++
                }

                line.text.startsWith("```") -> {
                    val (block, next) = parseFence(lines, i)
                    block?.let { blocks += it }
                    i = next
                }

                line.text.trimStart().startsWith(">") -> {
                    val (block, next) = parseBlockquote(lines, i)
                    blocks += block
                    i = next
                }

                standaloneImageMatch(line.text) != null -> {
                    blocks += parseStandaloneImage(line)
                    i++
                }

                listMarkerOf(line.text) != null -> {
                    val (block, next) = parseList(lines, i)
                    blocks += block
                    i = next
                }

                isTableStart(lines, i) -> {
                    val (block, next) = parseTable(lines, i)
                    blocks += block
                    i = next
                }

                isCommentStart(line.text) -> {
                    val comment = parseHtmlComment(lines, i)
                    if (comment != null) {
                        blocks += comment.first
                        i = comment.second
                    } else {
                        val (block, next) = parseParagraph(lines, i)
                        blocks += block
                        i = next
                    }
                }

                typedKeyOf(line) != null -> {
                    val (block, next) = parseTypedGroup(lines, i)
                    block?.let { blocks += it }
                    i = next
                }

                else -> {
                    val cnl = cnlElementOf(line)
                    if (cnl != null) {
                        blocks += cnl
                        i++
                    } else {
                        val (block, next) = parseParagraph(lines, i)
                        blocks += block
                        i = next
                    }
                }
            }
        }
        return blocks
    }

    // --- headings ---

    private fun headingMatch(text: String): Pair<Int, Int>? {
        if (!text.startsWith("#")) return null
        val level = text.takeWhile { it == '#' }.length
        if (level > 6) return null
        if (level >= text.length || text[level] != ' ') return null
        var contentStart = level
        while (contentStart < text.length && text[contentStart] == ' ') contentStart++
        return level to contentStart
    }

    /** A heading, plus the synthetic typed block from its trailing CNL container properties. */
    private fun parseHeading(line: Line): Pair<HeadingBlock, TypedAttributeBlock?> {
        val (level, contentStart) = headingMatch(line.text)!!
        if (level >= 4) {
            diagnostics.info("Heading level $level is treated as a nested section", line.number)
        }
        val content = line.text.substring(contentStart).trimEnd()
        val split = CnlParser.parseHeading(content, line.number, line.columnOffset + contentStart + 1, diagnostics)
        val name = split?.name ?: content
        val heading = HeadingBlock(
            level = level,
            inlines = inlineParser.parseLine(name, line.number, line.columnOffset + contentStart),
            span = SlmSourceSpan(line.number, line.number),
        )
        val typed = split?.let {
            val entries = CnlParser.desugar(it.element, line.number, diagnostics)
            entries.takeIf { list -> list.isNotEmpty() }
                ?.let { list -> TypedAttributeBlock(list, SlmSourceSpan(line.number, line.number)) }
        }
        return heading to typed
    }

    // --- fenced code blocks ---

    private fun parseFence(lines: List<Line>, start: Int): Pair<SlmBlock?, Int> {
        val open = lines[start]
        val info = open.text.removePrefix("```").trim()
        val content = mutableListOf<Line>()
        var j = start + 1
        var closed = false
        while (j < lines.size) {
            if (lines[j].text.trimEnd() == "```") {
                closed = true
                break
            }
            content += lines[j]
            j++
        }
        if (!closed) {
            diagnostics.error("Unclosed fenced code block", open.number)
        }
        val endLine = if (closed) lines[j].number else (lines.lastOrNull()?.number ?: open.number)
        val next = if (closed) j + 1 else lines.size
        val span = SlmSourceSpan(open.number, endLine)
        val contentText = content.joinToString("\n") { it.text }
        val contentStartLine = open.number + 1

        if (info == "ir") {
            return FencedCodeBlock(info, contentText, contentStartLine, span) to next
        }
        val firstContent = content.firstOrNull { !it.isBlank }
        if ((info == "yaml" || info.isEmpty()) && firstContent != null && typedKeyOf(firstContent) != null) {
            return parseFencedTypedBlock(content, span) to next
        }
        diagnostics.warning("Unsupported fenced code block `$info` is ignored", open.number)
        return FencedCodeBlock(info, contentText, contentStartLine, span) to next
    }

    /** Fenced `yaml`/bare fence whose first key is reserved: the fence delimits one group. */
    private fun parseFencedTypedBlock(content: List<Line>, span: SlmSourceSpan): SlmBlock? {
        val entries = mutableListOf<TypedEntry>()
        var i = 0
        while (i < content.size) {
            val line = content[i]
            when {
                line.isBlank -> i++
                typedKeyOf(line) != null -> {
                    val (block, next) = parseTypedGroup(content, i)
                    block?.let { entries += it.entries }
                    i = next
                }
                else -> {
                    diagnostics.warning("Unrecognized content in typed yaml block", line.number)
                    i++
                }
            }
        }
        return if (entries.isEmpty()) null else TypedAttributeBlock(entries, span)
    }

    // --- blockquotes ---

    private fun parseBlockquote(lines: List<Line>, start: Int): Pair<BlockquoteBlock, Int> {
        val inner = mutableListOf<Line>()
        var j = start
        while (j < lines.size && lines[j].text.trimStart().startsWith(">")) {
            val text = lines[j].text
            var strip = text.indexOf('>') + 1
            if (strip < text.length && text[strip] == ' ') strip++
            inner += Line(lines[j].number, text.substring(strip), lines[j].columnOffset + strip)
            j++
        }
        val span = SlmSourceSpan(lines[start].number, lines[j - 1].number)
        return BlockquoteBlock(parseBlocks(inner), span) to j
    }

    // --- images ---

    private val standaloneImageRegex =
        Regex("""^!\[([^\]]*)\]\(([^)]*)\)\s*(?:<!--(.*?)-->)?\s*$""")

    private fun standaloneImageMatch(text: String): MatchResult? =
        standaloneImageRegex.matchEntire(text)

    private fun parseStandaloneImage(line: Line): ImageBlock {
        val match = standaloneImageMatch(line.text)!!
        val comment = match.groupValues[3]
        return ImageBlock(
            alt = match.groupValues[1],
            path = match.groupValues[2],
            i18nKeyOverride = comment.takeIf { it.isNotEmpty() }?.let(::i18nKeyOf),
            span = SlmSourceSpan(line.number, line.number),
        )
    }

    // --- lists ---

    private class ListMarker(val indent: Int, val ordered: Boolean, val contentStart: Int)

    private fun listMarkerOf(text: String): ListMarker? {
        val indent = text.takeWhile { it == ' ' }.length
        val rest = text.substring(indent)
        val markerLength = when {
            rest.startsWith("- ") || rest.startsWith("* ") -> 1
            rest == "-" || rest == "*" -> return ListMarker(indent, ordered = false, contentStart = text.length)
            else -> {
                val digits = rest.takeWhile { it.isDigit() }.length
                if (digits in 1..9 && rest.getOrNull(digits) == '.' && rest.getOrNull(digits + 1) == ' ') {
                    digits + 1
                } else {
                    return null
                }
            }
        }
        var contentStart = indent + markerLength
        while (contentStart < text.length && text[contentStart] == ' ') contentStart++
        return ListMarker(indent, ordered = rest[0].isDigit(), contentStart = contentStart)
    }

    private fun parseList(lines: List<Line>, start: Int): Pair<ListBlock, Int> {
        val first = lines[start]
        val firstMarker = listMarkerOf(first.text)!!
        val baseIndent = firstMarker.indent
        val ordered = firstMarker.ordered
        val items = mutableListOf<SlmListItem>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank) {
                var k = i + 1
                while (k < lines.size && lines[k].isBlank) k++
                if (k >= lines.size) break
                val nextMarker = listMarkerOf(lines[k].text)
                val continues = nextMarker != null && nextMarker.indent == baseIndent && nextMarker.ordered == ordered
                if (!continues) break
                i = k
                continue
            }
            val marker = listMarkerOf(line.text)
            if (marker == null || marker.indent != baseIndent || marker.ordered != ordered) break
            val contentStart = marker.contentStart
            val inlines = inlineParser.parseLine(
                line.text.substring(contentStart).trimEnd(),
                line.number,
                line.columnOffset + contentStart,
            )
            val childLines = mutableListOf<Line>()
            var j = i + 1
            while (j < lines.size) {
                val child = lines[j]
                if (child.isBlank) {
                    var k = j + 1
                    while (k < lines.size && lines[k].isBlank) k++
                    if (k < lines.size && lines[k].indent >= contentStart) {
                        childLines += Line(child.number, "", child.columnOffset)
                        j++
                        continue
                    }
                    break
                }
                if (child.indent < contentStart) break
                childLines += Line(child.number, child.text.substring(contentStart), child.columnOffset + contentStart)
                j++
            }
            val children = if (childLines.isEmpty()) emptyList() else parseBlocks(childLines)
            val itemEnd = childLines.lastOrNull { !it.isBlank }?.number ?: line.number
            items += SlmListItem(inlines, children, SlmSourceSpan(line.number, itemEnd))
            i = j
        }
        val span = SlmSourceSpan(first.number, items.last().span.endLine)
        return ListBlock(ordered, items, span) to i
    }

    // --- tables ---

    private fun isTableStart(lines: List<Line>, i: Int): Boolean {
        if (!lines[i].text.contains('|')) return false
        val delimiter = lines.getOrNull(i + 1) ?: return false
        return isDelimiterRow(delimiter.text)
    }

    private fun isDelimiterRow(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.contains('-') || !trimmed.contains('|')) return false
        val cells = trimmed.removePrefix("|").removeSuffix("|").split('|')
        return cells.isNotEmpty() && cells.all { it.trim().matches(Regex("""^:?-+:?$""")) }
    }

    private fun parseTable(lines: List<Line>, start: Int): Pair<TableBlock, Int> {
        val header = parseRowCells(lines[start])
        val rows = mutableListOf<List<List<SlmInline>>>()
        var j = start + 2
        while (j < lines.size && !lines[j].isBlank && lines[j].text.contains('|')) {
            rows += parseRowCells(lines[j])
            j++
        }
        val span = SlmSourceSpan(lines[start].number, lines[j - 1].number)
        return TableBlock(header, rows, span) to j
    }

    /** Splits on unescaped `|`; `\|` becomes a literal pipe inside the cell. */
    private fun parseRowCells(line: Line): List<List<SlmInline>> {
        val text = line.text
        data class Segment(val start: Int, val content: String)

        val segments = mutableListOf<Segment>()
        val builder = StringBuilder()
        var segmentStart = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && text.getOrNull(i + 1) == '|' -> {
                    builder.append('|')
                    i += 2
                }
                c == '|' -> {
                    segments += Segment(segmentStart, builder.toString())
                    builder.clear()
                    segmentStart = i + 1
                    i++
                }
                else -> {
                    builder.append(c)
                    i++
                }
            }
        }
        segments += Segment(segmentStart, builder.toString())
        // Boundary pipes produce empty edge segments; drop them like GFM does.
        if (segments.size > 1 && segments.first().content.isBlank()) segments.removeAt(0)
        if (segments.size > 1 && segments.last().content.isBlank()) segments.removeAt(segments.lastIndex)
        return segments.map { segment ->
            val leading = segment.content.takeWhile { it == ' ' }.length
            inlineParser.parseLine(
                segment.content.trim(),
                line.number,
                line.columnOffset + segment.start + leading,
            )
        }
    }

    // --- HTML comments ---

    private fun isCommentStart(text: String): Boolean = text.startsWith("<!--")

    private fun parseHtmlComment(lines: List<Line>, start: Int): Pair<HtmlCommentBlock, Int>? {
        val first = lines[start]
        val firstText = first.text
        val sameLineClose = firstText.indexOf("-->")
        if (sameLineClose >= 0) {
            if (firstText.substring(sameLineClose + 3).isNotBlank()) return null
            val text = firstText.substring(4, sameLineClose).trim()
            return HtmlCommentBlock(text, SlmSourceSpan(first.number, first.number)) to start + 1
        }
        val body = mutableListOf(firstText.substring(4))
        var j = start + 1
        while (j < lines.size) {
            val text = lines[j].text
            val close = text.indexOf("-->")
            if (close >= 0) {
                if (text.substring(close + 3).isNotBlank()) return null
                body += text.substring(0, close)
                val comment = HtmlCommentBlock(
                    body.joinToString("\n").trim(),
                    SlmSourceSpan(first.number, lines[j].number),
                )
                return comment to j + 1
            }
            body += text
            j++
        }
        return null
    }

    // --- controlled natural language element sentences ---

    /** A CNL element line (`Прямоугольник 120 на 15 …`) → block, or null when it is prose. */
    private fun cnlElementOf(line: Line): CnlElementBlock? {
        val element = CnlParser.parseElement(line.text, line.number, line.columnOffset + 1, diagnostics)
            ?: return null
        return CnlElementBlock(element, SlmSourceSpan(line.number, line.number))
    }

    // --- typed attribute blocks ---

    /** Leading `word:` (colon followed by space or end) at column 0 of the window. */
    private fun leadingWordColon(text: String): String? {
        if (text.isEmpty() || !text[0].isLetter()) return null
        var i = 0
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
        if (i >= text.length || text[i] != ':') return null
        val after = text.getOrNull(i + 1)
        if (after != null && after != ' ') return null
        return text.take(i)
    }

    /** Reserved key opening a typed entry at this line, incl. fenced-only `ir`. */
    private fun typedKeyOf(line: Line): String? {
        val word = leadingWordColon(line.text) ?: return null
        return word.takeIf { it in TypedBlockKind.reservedKeys }
    }

    private fun parseTypedGroup(lines: List<Line>, start: Int): Pair<TypedAttributeBlock?, Int> {
        val entries = mutableListOf<TypedEntry>()
        val startLine = lines[start].number
        var endLine = startLine
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank) break
            val key = typedKeyOf(line) ?: break
            var j = i + 1
            while (j < lines.size && !lines[j].isBlank && lines[j].text.first().isWhitespace()) j++
            val sliceLines = lines.subList(i, j)
            val entrySpan = SlmSourceSpan(line.number, sliceLines.last().number)
            endLine = entrySpan.endLine
            i = j
            if (key == "ir") {
                diagnostics.error("`ir` must be a fenced code block: use ```ir", line.number, blockPath = "ir")
                continue
            }
            val kind = TypedBlockKind.fromKey(key) ?: continue
            val sliceText = sliceLines.joinToString("\n") { " ".repeat(it.columnOffset) + it.text }
            val parsed = parseSlmYaml(sliceText, diagnostics, startLine = line.number)
            val value = (parsed as? YamlMap)?.entries?.get(key)
            if (value == null) {
                if (parsed != null && parsed !is YamlMap) {
                    diagnostics.error("Malformed typed block entry `$key:`", line.number, blockPath = key)
                }
                continue
            }
            entries += TypedEntry(kind, value, entrySpan)
        }
        val block = if (entries.isEmpty()) null else TypedAttributeBlock(entries, SlmSourceSpan(startLine, endLine))
        return block to i
    }

    // --- paragraphs + near-miss hint ---

    private fun parseParagraph(lines: List<Line>, start: Int): Pair<ParagraphBlock, Int> {
        emitNearMissHint(lines[start])
        val inlines = mutableListOf<SlmInline>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank) break
            if (i > start && interruptsParagraph(lines, i)) break
            inlines += inlineParser.parseLine(line.text.trimEnd(), line.number, line.columnOffset)
            i++
        }
        val span = SlmSourceSpan(lines[start].number, lines[i - 1].number)
        return ParagraphBlock(inlines, span) to i
    }

    private fun interruptsParagraph(lines: List<Line>, i: Int): Boolean {
        val text = lines[i].text
        return headingMatch(text) != null ||
            text.startsWith("```") ||
            text.trimStart().startsWith(">") ||
            standaloneImageMatch(text) != null ||
            listMarkerOf(text) != null ||
            isTableStart(lines, i) ||
            isCommentStart(text)
    }

    /**
     * Rule 5: a paragraph-start word that is a case-insensitive or transposition-aware
     * Levenshtein-1 near miss of a reserved key gets an info hint but stays prose.
     */
    private fun emitNearMissHint(line: Line) {
        val word = leadingWordColon(line.text) ?: return
        if (word in TypedBlockKind.reservedKeys) return
        val lower = word.lowercase()
        val nearest = TypedBlockKind.entries.map { it.key }.firstOrNull { key ->
            lower == key || isNearMiss(lower, key)
        } ?: return
        diagnostics.info(
            "Did you mean typed block `$nearest:`? \"$word:\" is treated as prose.",
            line.number,
        )
    }

    private fun isNearMiss(a: String, b: String): Boolean {
        if (a.length - b.length !in -1..1) return false
        return osaDistance(a, b) == 1
    }

    /** Optimal string alignment distance (Levenshtein + adjacent transposition). */
    private fun osaDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }
}
