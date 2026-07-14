package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.blocks.CnlContainerExtension
import io.aequicor.visualization.engine.frontend.blocks.CnlContainerLine
import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.blocks.aggregateErased
import io.aequicor.visualization.engine.frontend.blocks.parseSentenceErased
import io.aequicor.visualization.engine.frontend.cnl.CnlParser
import io.aequicor.visualization.engine.frontend.cnl.CnlPropertyKind
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.LayoutMode

/**
 * Container spacing/flow properties whose presence on a semantic heading implies
 * Auto Layout (`gap`/`padding`/`distribute`/`wrap`/`row`/`column`/`grid`). Mirrors the
 * flow keywords that the container-kind validator rejects on a plain Frame.
 */
private val FLOW_SETTING_KINDS = setOf(
    CnlPropertyKind.Direction,
    CnlPropertyKind.Gap,
    CnlPropertyKind.Padding,
    CnlPropertyKind.Distribute,
    CnlPropertyKind.Wrap,
)

/**
 * Hand-rolled block-level SLM markdown parser: frontmatter split, ATX headings 1–6,
 * paragraphs, nested lists, blockquotes, standalone images, GFM tables, fenced code
 * blocks, HTML comments, and CNL element sentences. CNL is the **only** authoring
 * surface for typed content: heading property suffixes and container-extension bodies
 * desugar into synthetic [TypedAttributeBlock]s carrying [DirectPatchEntry]s. Raw YAML
 * typed blocks (`node:` / `layout:` / …) are no longer parsed — a top-level line that
 * still spells an ex-reserved key (or a registered [SlmExtensionRegistry] extension key,
 * e.g. `diagram:`) gets a deprecation warning and stays prose. YAML survives only in the
 * frontmatter fence.
 */
class SlmMarkdownParser(
    private val diagnostics: DiagnosticCollector,
    private val extensions: SlmExtensionRegistry = SlmExtensionRegistry.Empty,
) {
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

    // --- container-extension scopes (`## Diagram: …` bodies) ---

    /**
     * An open CNL container scope: body lines down to the next same-or-higher heading are
     * offered to the extension's scoped sentence grammar first; collected elements aggregate
     * into one [DirectPatchEntry] carrying the extension payload (typed end to end, no YAML)
     * inserted right after the heading so it binds to the container's anchor.
     */
    private class ContainerScope(
        val extension: CnlContainerExtension<*, *>,
        val level: Int,
        val insertIndex: Int,
        val headingLine: Int,
    ) {
        val elements = mutableListOf<Any>()
        var firstLine: Int = headingLine
        var lastLine: Int = headingLine
        var sawSentence: Boolean = false

        fun noteSentenceLine(line: Int) {
            if (!sawSentence) firstLine = line
            sawSentence = true
            lastLine = line
        }
    }

    /** The registered container extension for a heading line's `Noun:` prefix, or null. */
    private fun containerExtensionOf(line: Line): CnlContainerExtension<*, *>? {
        val (_, contentStart) = headingMatch(line.text) ?: return null
        val content = line.text.substring(contentStart).trimEnd()
        val colon = content.indexOf(':')
        if (colon <= 0) return null
        val prefix = content.take(colon).trim()
        if (prefix.isEmpty() || prefix.any(Char::isWhitespace)) return null
        return extensions.containerFor(prefix)
    }

    // --- block loop ---

    private fun parseBlocks(lines: List<Line>): List<SlmBlock> {
        val blocks = mutableListOf<SlmBlock>()
        val scopes = ArrayDeque<ContainerScope>()

        fun close(scope: ContainerScope) {
            val span = SlmSourceSpan(scope.firstLine, scope.lastLine)
            val patch = scope.extension.aggregateErased(scope.elements, span, diagnostics)
            blocks.add(
                scope.insertIndex,
                TypedAttributeBlock(
                    entries = listOf(DirectPatchEntry(scope.extension.kind, patch, span)),
                    span = span,
                ),
            )
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank) {
                i++
                continue
            }
            val headingLevel = headingMatch(line.text)?.first
            if (headingLevel != null) {
                while (scopes.isNotEmpty() && headingLevel <= scopes.last().level) {
                    close(scopes.removeLast())
                }
                val (heading, attrs) = parseHeading(line)
                blocks += heading
                attrs?.let { blocks += it }
                containerExtensionOf(line)?.let { extension ->
                    scopes.addLast(ContainerScope(extension, headingLevel, blocks.size, line.number))
                }
                i++
                continue
            }
            val scope = scopes.lastOrNull()
            if (scope != null) {
                when (val result = scope.extension.parseSentenceErased(line.text, line.number, diagnostics)) {
                    is CnlContainerLine.Sentence -> {
                        scope.elements += result.element
                        scope.noteSentenceLine(line.number)
                        i++
                        continue
                    }
                    CnlContainerLine.Invalid -> {
                        scope.noteSentenceLine(line.number)
                        i++
                        continue
                    }
                    CnlContainerLine.Prose -> {
                        i = parseScopedProse(lines, i, blocks, scope)
                        continue
                    }
                }
            }
            i = parseGeneralBlock(lines, i, blocks)
        }
        while (scopes.isNotEmpty()) close(scopes.removeLast())
        return blocks
    }

    /**
     * A non-sentence line inside a container scope. Structural markdown (fences, quotes,
     * lists, tables, images, comments, typed blocks) parses normally; anything else becomes
     * a **single-line** paragraph with a typo-guard warning — multi-line grouping is off so
     * a following sentence line is never swallowed into the paragraph, and global CNL
     * element nouns are inactive per the container-scope contract.
     */
    private fun parseScopedProse(
        lines: List<Line>,
        start: Int,
        blocks: MutableList<SlmBlock>,
        scope: ContainerScope,
    ): Int {
        val line = lines[start]
        if (isStructuralStart(lines, start)) return parseGeneralBlock(lines, start, blocks)
        diagnostics.warning(
            "Line inside the `${scope.extension.containerNoun}` container is not a " +
                "${scope.extension.containerNoun} sentence; kept as prose",
            line.number,
        )
        blocks += ParagraphBlock(
            inlines = inlineParser.parseLine(line.text.trimEnd(), line.number, line.columnOffset),
            span = SlmSourceSpan(line.number, line.number),
        )
        return start + 1
    }

    /** Whether the line opens a non-paragraph structural block (scope-independent kinds). */
    private fun isStructuralStart(lines: List<Line>, i: Int): Boolean {
        val text = lines[i].text
        return text.startsWith("```") ||
            text.trimStart().startsWith(">") ||
            standaloneImageMatch(text) != null ||
            listMarkerOf(text) != null ||
            isTableStart(lines, i) ||
            isCommentStart(text) ||
            exReservedKeyOf(lines[i]) != null
    }

    /** One ordinary (scope-free) block dispatch starting at [start]; returns the next index. */
    private fun parseGeneralBlock(lines: List<Line>, start: Int, blocks: MutableList<SlmBlock>): Int {
        var i = start
        val line = lines[i]
        when {
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

            exReservedKeyOf(line) != null -> {
                diagnostics.warning(
                    "Raw YAML typed blocks are no longer supported; author CNL instead " +
                        "(`${exReservedKeyOf(line)}:` and its indented lines are kept as prose)",
                    line.number,
                )
                val (block, next) = parseParagraph(lines, i)
                blocks += block
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
        return i
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
            cnlElement = split?.element,
            span = SlmSourceSpan(line.number, line.number),
        )
        val typed = split?.let {
            val entries = CnlParser.desugar(it.element, line.number, diagnostics).toMutableList()
            val hasFlowDirection = it.element.properties.any { property ->
                property.kind == CnlPropertyKind.Direction
            }
            val hasFlowSettings = it.element.properties.any { property ->
                property.kind in FLOW_SETTING_KINDS
            }
            val explicitContainerKind = it.name.substringBefore(':', missingDelimiterValue = "")
                .trim()
                .lowercase()
            if (hasFlowSettings && explicitContainerKind !in setOf("frame", "autolayout")) {
                // Before Frame and AutoLayout became distinct authoring kinds, semantic
                // headings expressed flow directly (`## Card row gap 8`). Preserve that
                // syntax as an inferred AutoLayout while keeping `Frame: ... row` invalid.
                val nodeIndex = entries.indexOfFirst { entry -> entry.kind == TypedBlockKind.Node }
                if (nodeIndex >= 0) {
                    val entry = entries[nodeIndex]
                    entries[nodeIndex] = entry.copy(
                        patch = (entry.patch as NodePatch).copy(containerKind = ContainerKind.AutoLayout),
                    )
                } else {
                    entries += DirectPatchEntry(
                        key = TypedBlockKind.Node.key,
                        patch = NodePatch(containerKind = ContainerKind.AutoLayout),
                        span = SlmSourceSpan(line.number, line.number),
                    )
                }
                // Auto Layout requires a flow direction; a semantic container that sets only
                // spacing (`gap`/`padding`/`distribute`/`wrap`) without `row`/`column`/`grid`
                // defaults to a vertical stack (Figma's default) so it stays valid.
                if (!hasFlowDirection) {
                    val layoutIndex = entries.indexOfFirst { entry -> entry.kind == TypedBlockKind.Layout }
                    if (layoutIndex >= 0) {
                        val entry = entries[layoutIndex]
                        val patch = entry.patch as LayoutPatch
                        if (patch.mode == null) {
                            entries[layoutIndex] = entry.copy(patch = patch.copy(mode = LayoutMode.Vertical))
                        }
                    } else {
                        entries += DirectPatchEntry(
                            key = TypedBlockKind.Layout.key,
                            patch = LayoutPatch(mode = LayoutMode.Vertical),
                            span = SlmSourceSpan(line.number, line.number),
                        )
                    }
                }
            }
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

        diagnostics.warning("Unsupported fenced code block `$info` is ignored", open.number)
        return FencedCodeBlock(info, contentText, contentStartLine, span) to next
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

    // --- paragraphs ---

    private fun parseParagraph(lines: List<Line>, start: Int): Pair<ParagraphBlock, Int> {
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
            isCommentStart(text) ||
            exReservedKeyOf(lines[i]) != null
    }

    // --- ex-reserved typed-block keys (deprecation guard, not an authoring surface) ---
    //
    // Raw YAML typed blocks were removed as an authoring surface: a leading built-in
    // ex-reserved key (`node:`/`style:`/…) or a registered extension key (e.g. `diagram:`)
    // no longer opens anything. The pattern is still detected so authors get one clear
    // deprecation warning and the lines fall through as prose.

    /** Leading `word:` (colon followed by space or line end) at column 0 of the window. */
    private fun leadingWordColon(text: String): String? {
        if (text.isEmpty() || !text[0].isLetter()) return null
        var i = 0
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
        if (i >= text.length || text[i] != ':') return null
        val after = text.getOrNull(i + 1)
        if (after != null && after != ' ') return null
        return text.take(i)
    }

    /** An ex-reserved built-in key or a registered extension key at column 0, or null. */
    private fun exReservedKeyOf(line: Line): String? {
        val word = leadingWordColon(line.text) ?: return null
        return word.takeIf { TypedBlockKind.fromKey(it) != null || it in extensions.kinds }
    }
}
