package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.parseSlmYaml

/**
 * The CNL clause parser: a single sentence of `noun property…` phrases (RU/EN) → a
 * [CnlElement] with per-value source spans, plus the desugaring of that element into the
 * standard typed patches (`node`/`shape`/`layout`/`style`/`text`) reused by the block
 * readers. One parse serves both compile (desugar) and write-back (spans).
 */
internal object CnlParser {
    private val numberRegex = Regex("""-?\d+(\.\d+)?""")

    private data class Token(val text: String, val span: CnlSpan, val isText: Boolean, val terminated: Boolean)

    private fun isNumber(text: String): Boolean = numberRegex.matches(text)

    private fun isColor(text: String): Boolean =
        text.startsWith("#") || text.startsWith("$")

    // --- public entry points ---

    /**
     * Parses [line] (source [lineNumber], starting at 1-based [baseColumn]) as a CNL element
     * sentence. Returns null when the leading word is not a known element noun — the caller
     * then treats the line as ordinary prose.
     */
    fun parseElement(
        line: String,
        lineNumber: Int,
        baseColumn: Int,
        diagnostics: DiagnosticCollector,
    ): CnlElement? {
        val tokens = tokenize(line, lineNumber, baseColumn)
        if (tokens.isEmpty()) return null
        val noun = CnlVocabulary.nouns[tokens[0].text.lowercase()] ?: return null
        // Only commit to CNL (and emit its diagnostics) when the line carries a real
        // property/text signal; otherwise a noun-led prose line stays prose.
        if (!looksQualified(tokens)) return null
        return parseFrom(tokens, startIndex = 1, noun = noun, lineNumber = lineNumber, diagnostics = diagnostics)
    }

    /** A noun-led line is a CNL element only if it has a number, a quoted text, or a known word. */
    private fun looksQualified(tokens: List<Token>): Boolean =
        tokens.drop(1).any { token ->
            val lower = token.text.lowercase()
            token.isText || isNumber(token.text) ||
                lower in CnlVocabulary.propertyKeywords || lower in CnlVocabulary.directions ||
                lower in CnlVocabulary.fontWeights || lower in CnlVocabulary.italicWords
        }

    /** A container heading split into its display [name] and the CNL [element] of its property suffix. */
    data class HeadingSplit(val name: String, val element: CnlElement)

    /**
     * Splits a container heading `Панель миссий колонка отступ 16` into name (`Панель миссий`)
     * and a property suffix. The boundary is the earliest token from which the whole remainder
     * parses cleanly as CNL properties; when there is none, the heading has no properties (null).
     */
    fun parseHeading(
        content: String,
        lineNumber: Int,
        baseColumn: Int,
        diagnostics: DiagnosticCollector,
    ): HeadingSplit? {
        val tokens = tokenize(content, lineNumber, baseColumn)
        // Name must be non-empty (s >= 1); the property suffix must be non-empty and fully clean.
        for (split in 1 until tokens.size) {
            val probe = DiagnosticCollector(diagnostics.fileName)
            val element = parseFrom(tokens, startIndex = split, noun = null, lineNumber = lineNumber, diagnostics = probe)
            if (element.properties.isNotEmpty() && probe.diagnostics.isEmpty()) {
                val name = content.substring(0, tokens[split].span.startColumn - baseColumn).trimEnd()
                return HeadingSplit(name, element)
            }
        }
        return null
    }

    // --- tokenizer ---

    private fun tokenize(line: String, lineNumber: Int, baseColumn: Int): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == ' ' || c == '\t') {
                i++
                continue
            }
            if (c == '«' || c == '"') {
                val close = if (c == '«') '»' else '"'
                var j = i + 1
                while (j < line.length && line[j] != close) j++
                val terminated = j < line.length
                // Span covers the inner content, so SetText write-back replaces just the text.
                val inner = line.substring(i + 1, j)
                tokens += Token(
                    text = inner,
                    span = CnlSpan(lineNumber, baseColumn + i + 1, baseColumn + j),
                    isText = true,
                    terminated = terminated,
                )
                i = if (terminated) j + 1 else j
            } else {
                var j = i
                while (j < line.length && line[j] != ' ' && line[j] != '\t') j++
                tokens += Token(
                    text = line.substring(i, j),
                    span = CnlSpan(lineNumber, baseColumn + i, baseColumn + j),
                    isText = false,
                    terminated = true,
                )
                i = j
            }
        }
        return tokens
    }

    // --- parse ---

    private fun parseFrom(
        tokens: List<Token>,
        startIndex: Int,
        noun: CnlNoun?,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): CnlElement {
        var idx = startIndex
        var text: CnlValue? = null
        val properties = mutableListOf<CnlProperty>()
        while (idx < tokens.size) {
            val token = tokens[idx]
            idx = when {
                token.isText -> {
                    if (!token.terminated) {
                        CnlDiagnostics.warn(diagnostics, CnlRule.UnterminatedText, lineNumber, "Незакрытый текст в кавычках")
                    }
                    if (text == null) text = CnlValue(token.text, token.span)
                    idx + 1
                }
                isNumber(token.text) -> parseLeadingNumber(tokens, idx, properties, lineNumber, diagnostics)
                else -> parseWord(tokens, idx, properties, lineNumber, diagnostics)
            }
        }
        val span = CnlSpan(
            lineNumber,
            tokens.first().span.startColumn,
            tokens.last().span.endColumn,
        )
        return CnlElement(noun, text, properties, span)
    }

    /** A number that opens a phrase: `W на H` (size), `N градусов` (rotation), else stray. */
    private fun parseLeadingNumber(
        tokens: List<Token>,
        idx: Int,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val connector = tokens.getOrNull(idx + 1)
        val second = tokens.getOrNull(idx + 2)
        if (connector != null && connector.text.lowercase() in CnlVocabulary.sizeConnectors &&
            second != null && isNumber(second.text)
        ) {
            properties += CnlProperty(
                kind = CnlPropertyKind.Size,
                values = listOf(CnlValue(tokens[idx].text, tokens[idx].span), CnlValue(second.text, second.span)),
                keywordSpan = null,
                phraseSpan = joinSpan(tokens[idx].span, second.span),
            )
            return idx + 3
        }
        if (connector != null && isDegreeWord(connector.text)) {
            properties += CnlProperty(
                kind = CnlPropertyKind.Rotation,
                values = listOf(CnlValue(tokens[idx].text, tokens[idx].span)),
                keywordSpan = null,
                phraseSpan = joinSpan(tokens[idx].span, connector.span),
            )
            return idx + 2
        }
        CnlDiagnostics.warn(diagnostics, CnlRule.StrayNumber, lineNumber, "Число «${tokens[idx].text}» не относится к свойству")
        return idx + 1
    }

    /** A word: a standalone enum (direction/weight), a keyword phrase + values, or unknown. */
    private fun parseWord(
        tokens: List<Token>,
        idx: Int,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val token = tokens[idx]
        val lower = token.text.lowercase()

        CnlVocabulary.directions[lower]?.let { mode ->
            properties += singleWord(CnlPropertyKind.Direction, mode, token)
            return idx + 1
        }
        CnlVocabulary.fontWeights[lower]?.let { weight ->
            properties += singleWord(CnlPropertyKind.FontWeight, weight.toString(), token)
            return idx + 1
        }
        if (lower in CnlVocabulary.italicWords) {
            properties += singleWord(CnlPropertyKind.FontStyle, "italic", token)
            return idx + 1
        }
        // A degree word standing on its own is a harmless unit marker (e.g. after `радиус 15 градусов`).
        if (isDegreeWord(token.text)) return idx + 1

        val keyword = matchKeyword(tokens, idx)
        if (keyword != null) {
            return consumeValues(keyword.kind, tokens, idx, keyword.endIndex, keyword.keywordSpan, properties, lineNumber, diagnostics)
        }

        CnlDiagnostics.warn(
            diagnostics,
            CnlRule.UnknownKeyword,
            lineNumber,
            "Неизвестное слово «${token.text}»",
            suggestion = nearestKeyword(lower),
        )
        return idx + 1
    }

    private data class KeywordMatch(val kind: CnlPropertyKind, val endIndex: Int, val keywordSpan: CnlSpan)

    /** Greedy longest-phrase keyword match (up to [CnlVocabulary.maxKeywordWords] words). */
    private fun matchKeyword(tokens: List<Token>, idx: Int): KeywordMatch? {
        val maxWords = minOf(CnlVocabulary.maxKeywordWords, tokens.size - idx)
        for (words in maxWords downTo 1) {
            val slice = tokens.subList(idx, idx + words)
            val phrase = slice.joinToString(" ") { it.text.lowercase() }
            CnlVocabulary.propertyKeywords[phrase]?.let { kind ->
                return KeywordMatch(kind, idx + words, joinSpan(slice.first().span, slice.last().span))
            }
        }
        return null
    }

    private fun consumeValues(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        keywordStart: Int,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        fun add(finalKind: CnlPropertyKind, values: List<CnlValue>, lastIdx: Int) {
            properties += CnlProperty(
                kind = finalKind,
                values = values,
                keywordSpan = keywordSpan,
                phraseSpan = joinSpan(keywordSpan, tokens[lastIdx].span),
            )
        }

        return when (kind) {
            CnlPropertyKind.Fill -> consumeColor(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Stroke -> consumeStroke(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Radius, CnlPropertyKind.Gap, CnlPropertyKind.Opacity,
            CnlPropertyKind.Width, CnlPropertyKind.Height,
            -> consumeNumber(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Rotation -> consumeRotation(tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Padding -> consumePadding(tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Position -> consumeTwoNumbers(CnlPropertyKind.Position, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.AlignParent -> consumeDirection(tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Size -> consumeSize(tokens, valueStart, ::add, lineNumber, diagnostics)
            else -> valueStart // Direction/FontWeight/FontStyle never reach here (no keyword+value).
        }.let { consumed -> if (consumed < 0) keywordStart + 1 else consumed }
    }

    private fun consumeColor(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val value = tokens.getOrNull(start)
        if (value == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Не указан цвет")
            return start
        }
        if (!isColor(value.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadColor, lineNumber, "«${value.text}» не похоже на цвет")
            return start
        }
        add(kind, listOf(CnlValue(value.text, value.span)), start)
        return start + 1
    }

    private fun consumeStroke(
        tokens: List<Token>,
        start: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val color = tokens.getOrNull(start)
        if (color == null || !isColor(color.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadColor, lineNumber, "Обводка без цвета")
            return start
        }
        val values = mutableListOf(CnlValue(color.text, color.span))
        var next = start + 1
        tokens.getOrNull(next)?.takeIf { isNumber(it.text) }?.let {
            values += CnlValue(it.text, it.span)
            next++
        }
        tokens.getOrNull(next)?.takeIf { it.text.lowercase() in CnlVocabulary.strokeAligns }?.let {
            values += CnlValue(it.text, it.span)
            next++
        }
        properties += CnlProperty(CnlPropertyKind.Stroke, values, keywordSpan, joinSpan(keywordSpan, tokens[next - 1].span))
        return next
    }

    private fun consumeNumber(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val value = tokens.getOrNull(start)
        if (value == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Не указано значение")
            return start
        }
        if (!isNumber(value.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "«${value.text}» не число")
            return start
        }
        add(kind, listOf(CnlValue(value.text, value.span)), start)
        return start + 1
    }

    private fun consumeRotation(
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val value = tokens.getOrNull(start)
        if (value == null || !isNumber(value.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Поворот без числа")
            return start
        }
        var last = start
        tokens.getOrNull(start + 1)?.takeIf { isDegreeWord(it.text) }?.let { last = start + 1 }
        add(CnlPropertyKind.Rotation, listOf(CnlValue(value.text, value.span)), last)
        return last + 1
    }

    private fun consumePadding(
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val values = mutableListOf<CnlValue>()
        var next = start
        while (values.size < 4) {
            val token = tokens.getOrNull(next)?.takeIf { isNumber(it.text) } ?: break
            values += CnlValue(token.text, token.span)
            next++
        }
        if (values.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Паддинги без чисел")
            return start
        }
        add(CnlPropertyKind.Padding, values, next - 1)
        return next
    }

    private fun consumeTwoNumbers(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val a = tokens.getOrNull(start)
        val b = tokens.getOrNull(start + 1)
        if (a == null || b == null || !isNumber(a.text) || !isNumber(b.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Позиция требует два числа")
            return start
        }
        add(kind, listOf(CnlValue(a.text, a.span), CnlValue(b.text, b.span)), start + 1)
        return start + 2
    }

    private fun consumeDirection(
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val value = tokens.getOrNull(start)
        if (value == null || value.text.lowercase() !in CnlVocabulary.alignDirections) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadDirection, lineNumber, "Не указано направление")
            return start
        }
        add(CnlPropertyKind.AlignParent, listOf(CnlValue(value.text, value.span)), start)
        return start + 1
    }

    /** `размер W на H` (size) or `размер N` (font size on a text element). */
    private fun consumeSize(
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val a = tokens.getOrNull(start)
        val connector = tokens.getOrNull(start + 1)
        val b = tokens.getOrNull(start + 2)
        if (a != null && isNumber(a.text) && connector != null &&
            connector.text.lowercase() in CnlVocabulary.sizeConnectors && b != null && isNumber(b.text)
        ) {
            add(CnlPropertyKind.Size, listOf(CnlValue(a.text, a.span), CnlValue(b.text, b.span)), start + 2)
            return start + 3
        }
        if (a != null && isNumber(a.text)) {
            add(CnlPropertyKind.FontSize, listOf(CnlValue(a.text, a.span)), start)
            return start + 1
        }
        CnlDiagnostics.warn(diagnostics, CnlRule.IncompleteSize, lineNumber, "Размер задан неполно")
        return start
    }

    private fun singleWord(kind: CnlPropertyKind, value: String, token: Token): CnlProperty =
        CnlProperty(kind, listOf(CnlValue(value, token.span)), token.span, token.span)

    private fun isDegreeWord(text: String): Boolean {
        val word = text.lowercase().trimEnd('.', ',')
        return word in CnlVocabulary.degreeWords || word.startsWith("градус")
    }

    private fun joinSpan(a: CnlSpan, b: CnlSpan): CnlSpan =
        CnlSpan(a.line, minOf(a.startColumn, b.startColumn), maxOf(a.endColumn, b.endColumn))

    private fun nearestKeyword(word: String): String? {
        val candidates = CnlVocabulary.propertyKeywords.keys.filter { ' ' !in it } +
            CnlVocabulary.directions.keys
        return candidates.minByOrNull { osaDistance(word, it) }
            ?.takeIf { osaDistance(word, it) in 1..2 }
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

    // --- desugar: CnlElement -> typed entries (via the standard YAML + block readers) ---

    /** Desugars [element] to `node`/`shape`/`layout`/`style`/`text` typed entries at [line]. */
    fun desugar(element: CnlElement, line: Int, diagnostics: DiagnosticCollector): List<TypedEntry> {
        val builder = BlockBuilder()
        element.noun?.let { noun ->
            builder.node("type" to noun.nodeType)
            noun.role?.let { builder.node("role" to "\"$it\"") }
            noun.shapeKind?.let { builder.shape("kind" to it) }
        }
        element.properties.forEach { property -> applyProperty(builder, property) }
        return builder.toEntries(line, diagnostics)
    }

    private fun applyProperty(builder: BlockBuilder, property: CnlProperty) {
        val values = property.values.map { it.raw }
        when (property.kind) {
            CnlPropertyKind.Size -> builder.sizing(sizeAxis(values[0]), sizeAxis(values[1]))
            CnlPropertyKind.Width -> builder.sizing(width = sizeAxis(values[0]))
            CnlPropertyKind.Height -> builder.sizing(height = sizeAxis(values[0]))
            CnlPropertyKind.Fill -> builder.style("fill" to colorLiteral(values[0]))
            CnlPropertyKind.Stroke -> builder.style("stroke" to strokeLiteral(values))
            CnlPropertyKind.Radius -> builder.style("radius" to values[0])
            CnlPropertyKind.Opacity -> builder.style("opacity" to values[0])
            CnlPropertyKind.Rotation -> builder.position("rotation" to values[0])
            CnlPropertyKind.Position -> {
                builder.position("x" to values[0])
                builder.position("y" to values[1])
            }
            CnlPropertyKind.Padding -> builder.layout("padding" to paddingLiteral(values))
            CnlPropertyKind.Gap -> builder.layout("gap" to values[0])
            CnlPropertyKind.Direction -> builder.layout("mode" to values[0])
            CnlPropertyKind.AlignParent -> CnlVocabulary.alignDirections[values[0].lowercase()]?.let { (axis, value) ->
                if (axis == "both") {
                    builder.constraint("horizontal" to value)
                    builder.constraint("vertical" to value)
                } else {
                    builder.constraint(axis to value)
                }
            }
            CnlPropertyKind.FontSize -> builder.typography("fontSize" to values[0])
            CnlPropertyKind.FontWeight -> builder.typography("fontWeight" to values[0])
            CnlPropertyKind.FontStyle -> builder.typography("fontStyle" to values[0])
        }
    }

    private fun sizeAxis(value: String): String =
        if (isNumber(value)) "{ type: fixed, value: $value }" else value

    private fun colorLiteral(color: String): String =
        if (color.startsWith("#")) "\"$color\"" else color

    private fun strokeLiteral(values: List<String>): String {
        val parts = buildList {
            add("color: ${colorLiteral(values[0])}")
            values.getOrNull(1)?.takeIf { isNumber(it) }?.let { add("weight: $it") }
            values.getOrNull(2)?.let { add("position: ${it.lowercase()}") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    private fun paddingLiteral(values: List<String>): String = when (values.size) {
        1 -> values[0]
        2 -> "{ block: ${values[0]}, inline: ${values[1]} }"
        4 -> "{ blockStart: ${values[0]}, inlineEnd: ${values[1]}, blockEnd: ${values[2]}, inlineStart: ${values[3]} }"
        else -> values.first()
    }

    /** Accumulates per-block key fragments, emitting one flow map per block key. */
    private class BlockBuilder {
        private val nodeParts = mutableListOf<String>()
        private val shapeParts = mutableListOf<String>()
        private val layoutParts = mutableListOf<String>()
        private val styleParts = mutableListOf<String>()
        private val textParts = mutableListOf<String>()
        private val positionParts = mutableListOf<String>()
        private val constraintParts = mutableListOf<String>()
        private var sizeWidth: String? = null
        private var sizeHeight: String? = null

        fun node(pair: Pair<String, String>) { nodeParts += "${pair.first}: ${pair.second}" }
        fun shape(pair: Pair<String, String>) { shapeParts += "${pair.first}: ${pair.second}" }
        fun layout(pair: Pair<String, String>) { layoutParts += "${pair.first}: ${pair.second}" }
        fun style(pair: Pair<String, String>) { styleParts += "${pair.first}: ${pair.second}" }
        fun typography(pair: Pair<String, String>) { textParts += "${pair.first}: ${pair.second}" }
        fun position(pair: Pair<String, String>) { positionParts += "${pair.first}: ${pair.second}" }
        fun constraint(pair: Pair<String, String>) { constraintParts += "${pair.first}: ${pair.second}" }

        fun sizing(width: String? = null, height: String? = null) {
            width?.let { sizeWidth = it }
            height?.let { sizeHeight = it }
        }

        fun toEntries(line: Int, diagnostics: DiagnosticCollector): List<TypedEntry> {
            val node = (nodeParts + positionMap() + constraintMap()).filter { it.isNotEmpty() }
            val layout = (layoutParts + sizingMap()).filter { it.isNotEmpty() }
            val style = styleParts
            val text = if (textParts.isEmpty()) emptyList() else listOf("typography: { ${textParts.joinToString(", ")} }")
            return buildList {
                entry("node", node, line, diagnostics)?.let { add(it) }
                entry("shape", shapeParts, line, diagnostics)?.let { add(it) }
                entry("layout", layout, line, diagnostics)?.let { add(it) }
                entry("style", style, line, diagnostics)?.let { add(it) }
                entry("text", text, line, diagnostics)?.let { add(it) }
            }
        }

        private fun positionMap(): String =
            if (positionParts.isEmpty()) "" else "position: { ${positionParts.joinToString(", ")} }"

        private fun constraintMap(): String =
            if (constraintParts.isEmpty()) "" else "constraints: { ${constraintParts.joinToString(", ")} }"

        private fun sizingMap(): String {
            if (sizeWidth == null && sizeHeight == null) return ""
            val parts = buildList {
                sizeWidth?.let { add("width: $it") }
                sizeHeight?.let { add("height: $it") }
            }
            return "sizing: { ${parts.joinToString(", ")} }"
        }

        private fun entry(
            key: String,
            parts: List<String>,
            line: Int,
            diagnostics: DiagnosticCollector,
        ): TypedEntry? {
            if (parts.isEmpty()) return null
            val parsed = parseSlmYaml("$key: { ${parts.joinToString(", ")} }", diagnostics, startLine = line)
            val value = (parsed as? YamlMap)?.entries?.get(key) ?: return null
            val kind = TypedBlockKind.fromKey(key) ?: return null
            return TypedEntry(kind, value, SlmSourceSpan(line, line))
        }
    }
}
