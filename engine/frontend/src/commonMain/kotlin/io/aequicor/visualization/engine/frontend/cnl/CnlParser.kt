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
                lower in CnlVocabulary.fontWeights
        }

    /** A container heading split into its display [name] and the CNL [element] of its property suffix. */
    data class HeadingSplit(val name: String, val element: CnlElement)

    /**
     * Splits a container heading `Mission Panel column gap 16` into name (`Mission Panel`)
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
            } else if (c == '(' || c == ')') {
                // Structural group delimiters for `( … )` value groups (gradients, records, tuples).
                tokens += Token(
                    text = c.toString(),
                    span = CnlSpan(lineNumber, baseColumn + i, baseColumn + i + 1),
                    isText = false,
                    terminated = true,
                )
                i++
            } else {
                var j = i
                while (j < line.length && line[j] != ' ' && line[j] != '\t' && line[j] != '(' && line[j] != ')') j++
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

    // --- structured `( … )` groups ---

    /** A node in a parsed value group: either a bare token or a nested group. */
    private sealed interface GNode
    private data class GLeaf(val token: Token) : GNode
    private data class GGroup(val children: List<GNode>) : GNode

    private fun GNode.leafText(): String? = (this as? GLeaf)?.token?.text
    private fun GNode.leafIsText(): Boolean = (this as? GLeaf)?.token?.isText == true
    private fun GNode.leaf(): GLeaf? = this as? GLeaf
    private fun GNode.asGroup(): GGroup? = this as? GGroup

    /** Parses a `( … )` group whose `(` is at [openIdx]; returns the group and the index after `)`. */
    private fun parseGroup(tokens: List<Token>, openIdx: Int): Pair<GGroup, Int> {
        val children = mutableListOf<GNode>()
        var i = openIdx + 1
        while (i < tokens.size && tokens[i].text != ")") {
            if (tokens[i].text == "(") {
                val (group, next) = parseGroup(tokens, i)
                children += group
                i = next
            } else {
                children += GLeaf(tokens[i])
                i++
            }
        }
        return GGroup(children) to (if (i < tokens.size) i + 1 else i)
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
                        CnlDiagnostics.warn(diagnostics, CnlRule.UnterminatedText, lineNumber, "Unterminated quoted text")
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

    /** A number that opens a phrase: `W by H` (size), `N degrees` (rotation), else stray. */
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
        CnlDiagnostics.warn(diagnostics, CnlRule.StrayNumber, lineNumber, "Number \"${tokens[idx].text}\" is not attached to a property")
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
        // A degree word standing on its own is a harmless unit marker (e.g. after `radius 15 degrees`).
        if (isDegreeWord(token.text)) return idx + 1

        val keyword = matchKeyword(tokens, idx)
        if (keyword != null) {
            return consumeValues(keyword.kind, tokens, idx, keyword.endIndex, keyword.keywordSpan, properties, lineNumber, diagnostics)
        }

        CnlDiagnostics.warn(
            diagnostics,
            CnlRule.UnknownKeyword,
            lineNumber,
            "Unknown word \"${token.text}\"",
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
            CnlPropertyKind.Fill -> consumeFill(tokens, keywordStart, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Stroke -> consumeStroke(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Effect -> consumeEffect(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Radius -> consumeRadius(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.StyleRefs -> consumeStyleRefs(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Blend -> consumeToken(CnlPropertyKind.Blend, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.FontFamily, CnlPropertyKind.LineHeight, CnlPropertyKind.Tracking,
            CnlPropertyKind.TextAlign, CnlPropertyKind.TextValign, CnlPropertyKind.TextCase,
            CnlPropertyKind.TextDecoration, CnlPropertyKind.TextKey, CnlPropertyKind.TextStyleRef,
            CnlPropertyKind.AutoSize,
            -> consumeToken(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Features, CnlPropertyKind.Axes ->
                consumeTypographyMap(kind, tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.ListSettings ->
                consumeListSettings(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Link -> consumeLink(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Width, CnlPropertyKind.Height ->
                consumeSizingAxis(kind, tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Gap -> consumeGap(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Distribute -> consumeToken(CnlPropertyKind.Distribute, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Anchor -> consumeAnchor(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Constraints -> consumeConstraints(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Overflow -> consumeOverflow(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Scroll -> consumeScroll(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Columns, CnlPropertyKind.Rows ->
                consumeTrackAxis(kind, tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Place -> consumePlace(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Guides -> consumeGuides(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Grids -> consumeGrids(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Wrap, CnlPropertyKind.Clip, CnlPropertyKind.Absolute,
            CnlPropertyKind.Detach, CnlPropertyKind.ResetOverrides,
            -> {
                properties += CnlProperty(kind, emptyList(), keywordSpan, keywordSpan)
                valueStart
            }
            CnlPropertyKind.ComponentRef ->
                consumeToken(CnlPropertyKind.ComponentRef, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.LibraryRef ->
                consumeToken(CnlPropertyKind.LibraryRef, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Variant -> consumeVariant(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Props -> consumeProps(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.SlotOverride -> consumeSlot(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.NestedOverride -> consumeNested(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Smoothing, CnlPropertyKind.ParagraphSpacing,
            CnlPropertyKind.MaxLines, CnlPropertyKind.Truncate,
            -> consumeNumber(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Opacity ->
                consumeBindableNumber(CnlPropertyKind.Opacity, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Rotation -> consumeRotation(tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Padding -> consumePadding(tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Position -> consumeTwoNumbers(CnlPropertyKind.Position, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.AlignParent ->
                if (tokens.getOrNull(valueStart)?.text == "(") {
                    consumeContainerAlign(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
                } else {
                    consumeDirection(tokens, valueStart, ::add, lineNumber, diagnostics)
                }
            CnlPropertyKind.Size -> consumeSize(tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Id -> consumeToken(CnlPropertyKind.Id, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Media -> consumeMedia(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.ShapePoints, CnlPropertyKind.ShapeInner ->
                consumeNumber(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.ShapeArc -> consumeShapeArc(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.ViewBox -> consumeViewBox(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.IconRef, CnlPropertyKind.PathRef, CnlPropertyKind.BooleanOp ->
                consumeToken(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.VectorPaths -> consumeVectorPaths(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.VectorNetwork -> consumeNetwork(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Mask -> consumeMask(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Interactions ->
                consumeInteraction(tokens, keywordStart, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Motion ->
                consumeMotion(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Responsive -> consumeResponsive(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Export -> consumeExport(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Annotation -> consumeHandoffNote(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Measurement -> consumeHandoffMeasure(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.CodeHint -> consumeHandoffCode(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            else -> valueStart // Direction/FontWeight never reach here (no keyword+value).
        }.let { consumed -> if (consumed < 0) keywordStart + 1 else consumed }
    }

    /** A single opaque token value (e.g. `id overview_cards`). */
    private fun consumeToken(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val value = tokens.getOrNull(start)
        if (value == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "No value given")
            return start
        }
        add(kind, listOf(CnlValue(value.text, value.span)), start)
        return start + 1
    }

    /**
     * A fill phrase led by `color`/`fill` (bare solid or `( … )` solid-with-props) or by
     * `gradient`/`image`/`video` (always a `( … )` group). Bare solids keep their color token +
     * span for surgical write-back ([CnlPropertyKind.Fill]); group forms are pre-lowered to a
     * ready YAML fragment ([CnlPropertyKind.FillComplex]).
     */
    private fun consumeFill(
        tokens: List<Token>,
        keywordStart: Int,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val keyword = tokens[keywordStart].text.lowercase()
        val next = tokens.getOrNull(valueStart)

        fun addComplex(fragment: String, lastIdx: Int) {
            val span = joinSpan(keywordSpan, tokens[lastIdx].span)
            properties += CnlProperty(CnlPropertyKind.FillComplex, listOf(CnlValue(fragment, span)), keywordSpan, span)
        }

        if (keyword == "gradient" || keyword == "image" || keyword == "video") {
            if (next?.text != "(") {
                CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"$keyword\" needs a ( … ) group")
                return valueStart
            }
            val (group, after) = parseGroup(tokens, valueStart)
            val fragment = when (keyword) {
                "gradient" -> gradientFragment(group)
                "image" -> mediaFillFragment(group, video = false)
                else -> mediaFillFragment(group, video = true)
            }
            addComplex(fragment, after - 1)
            return after
        }

        // `color`/`fill` with a `( … )` group → solid-with-props.
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            addComplex(solidFragment(group), after - 1)
            return after
        }

        // Bare solid (P0/P1a form): store the raw color token so write-back can replace it.
        if (next == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "No color given")
            return valueStart
        }
        if (!isColor(next.text) && !isBinding(next.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadColor, lineNumber, "\"${next.text}\" is not a color")
            return valueStart
        }
        properties += CnlProperty(
            CnlPropertyKind.Fill,
            listOf(CnlValue(next.text, next.span)),
            keywordSpan,
            joinSpan(keywordSpan, next.span),
        )
        return valueStart + 1
    }

    // --- fill fragment builders (GGroup -> ready YAML fragment for a `fills:` list item) ---

    private class FillProps {
        var opacity: String? = null
        var blendMode: String? = null
        var visible: String? = null

        fun apply(sub: String, value: String?): Boolean = when (sub) {
            "opacity" -> { opacity = value; true }
            "blend" -> { blendMode = value; true }
            "visible" -> { visible = value?.let(::boolLiteral); true }
            else -> false
        }

        fun parts(): List<String> = buildList {
            opacity?.let { add("opacity: $it") }
            blendMode?.let { add("blendMode: $it") }
            visible?.let { add("visible: $it") }
        }
    }

    private fun gradientFragment(group: GGroup): String {
        val children = group.children
        var kindKey = "linearGradient"
        var from: String? = null
        var to: String? = null
        val stops = mutableListOf<String>()
        val props = FillProps()
        var i = 0
        children.getOrNull(0)?.leafText()?.lowercase()?.let { word ->
            gradientKinds[word]?.let { kindKey = it; i = 1 }
        }
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "from" -> { from = pointFragment(children.getOrNull(i + 1)); i += 2 }
                text == "to" -> { to = pointFragment(children.getOrNull(i + 1)); i += 2 }
                text == "stops" -> {
                    i += 1
                    while (i < children.size) {
                        val stop = children[i].asGroup() ?: break
                        stops += stopFragment(stop); i += 1
                    }
                }
                text != null && props.apply(text, children.getOrNull(i + 1)?.leafText()) -> i += 2
                else -> i += 1
            }
        }
        val parts = buildList {
            add("type: $kindKey")
            from?.let { add("from: $it") }
            to?.let { add("to: $it") }
            if (stops.isNotEmpty()) add("stops: [ ${stops.joinToString(", ")} ]")
            addAll(props.parts())
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    private fun mediaFillFragment(group: GGroup, video: Boolean): String {
        val children = group.children
        var asset: String? = null
        var poster: String? = null
        var fillMode: String? = null
        var focal: String? = null
        var autoplay = false
        var loop = false
        var replaceable = false
        var muted: Boolean? = null
        val props = FillProps()
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "asset" -> { asset = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "poster" -> { poster = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "focus" || text == "focal" -> { focal = pointFragment(children.getOrNull(i + 1)); i += 2 }
                text in fillModeWords -> { fillMode = text; i += 1 }
                text == "autoplay" -> { autoplay = true; i += 1 }
                text == "loop" -> { loop = true; i += 1 }
                text == "replaceable" -> { replaceable = true; i += 1 }
                text == "muted" -> {
                    val flag = children.getOrNull(i + 1)?.leafText()?.let { CnlVocabulary.booleans[it.lowercase()] }
                    if (flag != null) { muted = flag; i += 2 } else { muted = true; i += 1 }
                }
                text != null && props.apply(text, children.getOrNull(i + 1)?.leafText()) -> i += 2
                else -> i += 1
            }
        }
        val parts = buildList {
            add("type: ${if (video) "video" else "image"}")
            asset?.let { add("asset: ${yamlString(it)}") }
            fillMode?.let { add("fillMode: $it") }
            focal?.let { add("focalPoint: $it") }
            if (video) {
                poster?.let { add("poster: ${yamlString(it)}") }
                if (autoplay) add("autoplay: true")
                if (loop) add("loop: true")
                muted?.let { add("muted: $it") }
            } else if (replaceable) {
                add("replaceable: true")
            }
            addAll(props.parts())
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    private fun solidFragment(group: GGroup): String {
        val children = group.children
        var color: String? = null
        val props = FillProps()
        var i = 0
        while (i < children.size) {
            val node = children[i]
            val text = node.leafText()
            val lower = text?.lowercase()
            when {
                lower != null && props.apply(lower, children.getOrNull(i + 1)?.leafText()) -> i += 2
                text != null && (isColor(text) || isBinding(text)) -> { color = text; i += 1 }
                else -> i += 1
            }
        }
        val parts = buildList {
            color?.let { add("color: ${colorLiteral(it)}") }
            addAll(props.parts())
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    private fun pointFragment(node: GNode?): String? {
        val nums = node?.asGroup()?.children?.mapNotNull { it.leafText() } ?: return null
        if (nums.size < 2) return null
        return "{ x: ${nums[0]}, y: ${nums[1]} }"
    }

    private fun stopFragment(group: GGroup): String {
        val leaves = group.children.mapNotNull { it.leafText() }
        val color = leaves.firstOrNull() ?: "#000000"
        val position = leaves.lastOrNull { it != "at" } ?: "0"
        return "{ position: $position, color: ${colorLiteral(color)} }"
    }

    private fun boolLiteral(word: String): String =
        CnlVocabulary.booleans[word.lowercase()]?.toString() ?: word

    private fun yamlString(value: String): String = "\"$value\""

    private val gradientKinds = mapOf(
        "linear" to "linearGradient", "radial" to "radialGradient",
        "angular" to "angularGradient", "diamond" to "diamondGradient",
    )
    private val fillModeWords = setOf("fill", "fit", "crop", "tile", "stretch")

    /** `radius N` (uniform, write-back friendly) or `radius (tl tr br bl)` (per-corner). */
    private fun consumeRadius(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val next = tokens.getOrNull(valueStart)
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            val nums = group.children.mapNotNull { it.leafText() }.filter { isNumber(it) }
            if (nums.size < 4) {
                CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Per-corner radius needs 4 numbers (tl tr br bl)")
                return after
            }
            val fragment = "{ topLeft: ${nums[0]}, topRight: ${nums[1]}, bottomRight: ${nums[2]}, bottomLeft: ${nums[3]} }"
            val span = joinSpan(tokens[valueStart].span, tokens[after - 1].span)
            properties += CnlProperty(CnlPropertyKind.Radius, listOf(CnlValue(fragment, span)), keywordSpan, joinSpan(keywordSpan, span))
            return after
        }
        if (next == null || !isNumber(next.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Radius needs a number")
            return valueStart
        }
        properties += CnlProperty(CnlPropertyKind.Radius, listOf(CnlValue(next.text, next.span)), keywordSpan, joinSpan(keywordSpan, next.span))
        return valueStart + 1
    }

    /** `styles ( fill <id> text <id> effect <id> grid <id> )` — group-scoped shared-style refs. */
    private fun consumeStyleRefs(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val next = tokens.getOrNull(valueStart)
        if (next?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"styles\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val children = group.children
        val refs = mutableListOf<CnlValue>()
        var i = 0
        while (i < children.size) {
            val readerKey = styleRefKeys[children[i].leafText()?.lowercase()]
            val value = children.getOrNull(i + 1)?.leafText()
            if (readerKey != null && value != null) {
                refs += CnlValue("$readerKey: $value", keywordSpan)
                i += 2
            } else {
                i += 1
            }
        }
        if (refs.isNotEmpty()) {
            properties += CnlProperty(CnlPropertyKind.StyleRefs, refs, keywordSpan, joinSpan(keywordSpan, tokens[after - 1].span))
        }
        return after
    }

    private val styleRefKeys = mapOf(
        "fill" to "fillStyle", "text" to "textStyle", "effect" to "effectStyle", "grid" to "gridStyle",
    )

    /** `features (liga on) (tnum off)` or `axes (wght 620) (opsz 28)` → a `{ key: value, … }` map. */
    private fun consumeTypographyMap(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        var i = valueStart
        val entries = mutableListOf<String>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val leaves = group.children.mapNotNull { it.leafText() }
            val key = leaves.getOrNull(0)
            val raw = leaves.getOrNull(1)
            if (key != null && raw != null) {
                val value = if (kind == CnlPropertyKind.Features) boolLiteral(raw) else raw
                entries += "$key: $value"
            }
            i = after
        }
        if (entries.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Expected ( key value ) groups")
            return valueStart
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(kind, listOf(CnlValue("{ ${entries.joinToString(", ")} }", span)), keywordSpan, span)
        return i
    }

    /** `list (bullet indent 1)` / `list (ordered)` → `{ type: bullet, indent: 1 }`. */
    private fun consumeListSettings(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"list\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val leaves = group.children.mapNotNull { it.leafText() }
        val type = leaves.firstOrNull() ?: "none"
        val indentIdx = leaves.indexOf("indent")
        val indent = if (indentIdx >= 0) leaves.getOrNull(indentIdx + 1) ?: "0" else "0"
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.ListSettings,
            listOf(CnlValue("{ type: $type, indent: $indent }", span)),
            keywordSpan,
            span,
        )
        return after
    }

    private fun unitLiteral(value: String): String =
        if (value.endsWith("%")) "{ unit: percent, value: ${value.dropLast(1)} }" else value

    // --- layout-deep ---

    private val sizingModeWords = setOf("fill", "hug", "fixed")

    /** `width N` | `width fill|hug` | `width ( <mode> [N] [min N] [max N] )` → a sizing axis value. */
    private fun consumeSizingAxis(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val next = tokens.getOrNull(valueStart)
        fun add(value: String, span: CnlSpan, end: Int): Int {
            properties += CnlProperty(kind, listOf(CnlValue(value, span)), keywordSpan, joinSpan(keywordSpan, span))
            return end
        }
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            return add(sizingRecordFragment(group), joinSpan(tokens[valueStart].span, tokens[after - 1].span), after)
        }
        if (next != null && next.text.lowercase() in sizingModeWords) {
            return add(next.text.lowercase(), next.span, valueStart + 1)
        }
        if (next == null || !isNumber(next.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Size axis needs a number, `fill`, `hug` or a ( … ) record")
            return valueStart
        }
        return add("{ type: fixed, value: ${next.text} }", next.span, valueStart + 1)
    }

    private fun sizingRecordFragment(group: GGroup): String {
        val children = group.children
        val mode = children.getOrNull(0)?.leafText()?.lowercase() ?: "fixed"
        var value: String? = null
        var min: String? = null
        var max: String? = null
        var i = 1
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "min" -> { min = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "max" -> { max = children.getOrNull(i + 1)?.leafText(); i += 2 }
                value == null && text != null && isNumber(text) -> { value = text; i += 1 }
                else -> i += 1
            }
        }
        val parts = buildList {
            add("type: $mode")
            value?.let { add("value: $it") }
            min?.let { add("min: $it") }
            max?.let { add("max: $it") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** `gap N` | `gap auto` | `gap ( row N column N )`. */
    private fun consumeGap(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val next = tokens.getOrNull(valueStart)
        fun add(value: String, span: CnlSpan, end: Int): Int {
            properties += CnlProperty(CnlPropertyKind.Gap, listOf(CnlValue(value, span)), keywordSpan, joinSpan(keywordSpan, span))
            return end
        }
        if (next?.text?.lowercase() == "auto") return add("auto", next.span, valueStart + 1)
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            val leaves = group.children.mapNotNull { it.leafText() }
            val entries = buildList {
                val row = leaves.indexOf("row"); if (row >= 0) leaves.getOrNull(row + 1)?.let { add("row: $it") }
                val col = leaves.indexOf("column"); if (col >= 0) leaves.getOrNull(col + 1)?.let { add("column: $it") }
            }
            return add("{ ${entries.joinToString(", ")} }", joinSpan(tokens[valueStart].span, tokens[after - 1].span), after)
        }
        if (next == null || !isNumber(next.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Gap needs a number, `auto` or a ( row/column ) record")
            return valueStart
        }
        return add(next.text, next.span, valueStart + 1)
    }

    /** `anchor ( inlineStart N inlineEnd N blockStart N blockEnd N )` → `layout.position` sides. */
    private fun consumeAnchor(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"anchor\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = mutableListOf<String>()
        var i = 0
        while (i < leaves.size - 1) {
            if (leaves[i] in anchorSides) { entries += "${leaves[i]}: ${leaves[i + 1]}"; i += 2 } else i += 1
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Anchor, entries.map { CnlValue(it, span) }, keywordSpan, span)
        return after
    }

    private val anchorSides = setOf("inlineStart", "inlineEnd", "blockStart", "blockEnd")

    /** `constraints ( horizontal <hc> vertical <vc> )` → `node.constraints`. */
    private fun consumeConstraints(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"constraints\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = mutableListOf<String>()
        var i = 0
        while (i < leaves.size - 1) {
            when (leaves[i].lowercase()) {
                "horizontal" -> { entries += "horizontal: ${leaves[i + 1]}"; i += 2 }
                "vertical" -> { entries += "vertical: ${leaves[i + 1]}"; i += 2 }
                else -> i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Constraints, entries.map { CnlValue(it, span) }, keywordSpan, span)
        return after
    }

    /** `align ( inline <a> block <a> baseline first|last )` → `layout.align`. */
    private fun consumeContainerAlign(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val (group, after) = parseGroup(tokens, valueStart)
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = mutableListOf<String>()
        var i = 0
        while (i < leaves.size - 1) {
            when (leaves[i].lowercase()) {
                "inline" -> { entries += "inline: ${leaves[i + 1]}"; i += 2 }
                "block" -> { entries += "block: ${leaves[i + 1]}"; i += 2 }
                "baseline" -> { entries += "baseline: ${leaves[i + 1]}"; i += 2 }
                else -> i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        val fragment = "{ ${entries.joinToString(", ")} }"
        properties += CnlProperty(CnlPropertyKind.ContainerAlign, listOf(CnlValue(fragment, span)), keywordSpan, span)
        return after
    }

    /**
     * `link (range (s e) url «href»)` | `link (range (s e) to <nodeId>)` → one `text.spans[]` item.
     * Range form only; each `link` phrase is its own [CnlPropertyKind.Link] property.
     */
    private fun consumeLink(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"link\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val fragment = linkSpanFragment(group)
        if (fragment == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"link\" needs range (s e) and url «href» or to <id>")
            return after
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Link, listOf(CnlValue(fragment, span)), keywordSpan, span)
        return after
    }

    private fun linkSpanFragment(group: GGroup): String? {
        val children = group.children
        var range: String? = null
        var link: String? = null
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "range" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children
                        ?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
                    if (nums != null && nums.size >= 2) range = "range: [ ${nums[0]}, ${nums[1]} ]"
                    i += 2
                }
                "url" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { link = "link: { type: url, href: ${yamlString(it)} }" }
                    i += 2
                }
                "to" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { link = "link: { type: node, target: $it }" }
                    i += 2
                }
                else -> i += 1
            }
        }
        if (range == null || link == null) return null
        return "{ $range, $link }"
    }

    // --- layout-deep P4b: overflow / scroll / grid tracks / placement / guides / grids ---

    /** `overflow ( x <mode> y <mode> )` → `layout.overflow` record. */
    private fun consumeOverflow(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"overflow\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = buildList {
            val x = leaves.indexOf("x"); if (x >= 0) leaves.getOrNull(x + 1)?.let { add("x: $it") }
            val y = leaves.indexOf("y"); if (y >= 0) leaves.getOrNull(y + 1)?.let { add("y: $it") }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Overflow, listOf(CnlValue("{ ${entries.joinToString(", ")} }", span)), keywordSpan, span)
        return after
    }

    /** `scroll ( direction <d> fixedChildren ( id id ) )` → `layout.scroll` record. */
    private fun consumeScroll(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"scroll\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val children = group.children
        var direction: String? = null
        var fixed: String? = null
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "direction" -> { direction = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "fixedchildren" -> {
                    val ids = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    fixed = ids?.joinToString(", ")?.let { "[ $it ]" }
                    i += 2
                }
                else -> i += 1
            }
        }
        val parts = buildList {
            direction?.let { add("direction: $it") }
            fixed?.let { add("fixedChildren: $it") }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Scroll, listOf(CnlValue("{ ${parts.joinToString(", ")} }", span)), keywordSpan, span)
        return after
    }

    /** `columns ( count N track T [gap N] )` / `rows ( auto min N )` → `layout.columns`/`layout.rows`. */
    private fun consumeTrackAxis(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val keyword = if (kind == CnlPropertyKind.Columns) "columns" else "rows"
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"$keyword\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(kind, listOf(CnlValue(trackAxisFragment(group), span)), keywordSpan, span)
        return after
    }

    private fun trackAxisFragment(group: GGroup): String {
        val children = group.children
        var count: String? = null
        var track: String? = null
        var gap: String? = null
        var min: String? = null
        var auto = false
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "count" -> { count = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "track" -> { track = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "gap" -> { gap = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "min" -> { min = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "auto" -> { auto = true; i += 1 }
                else -> i += 1
            }
        }
        val parts = buildList {
            count?.let { add("count: $it") }
            track?.let { add("track: $it") }
            if (auto) add("auto: true")
            min?.let { add("min: $it") }
            gap?.let { add("gap: $it") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** `place ( column N row N columnSpan N rowSpan N )` → `layout.placement`. */
    private fun consumePlace(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"place\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val children = group.children
        val entries = mutableListOf<String>()
        var i = 0
        while (i < children.size - 1) {
            when (children[i].leafText()?.lowercase()) {
                "column" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "column: $it" }; i += 2 }
                "row" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "row: $it" }; i += 2 }
                "columnspan" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "columnSpan: $it" }; i += 2 }
                "rowspan" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "rowSpan: $it" }; i += 2 }
                else -> i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Place, listOf(CnlValue("{ ${entries.joinToString(", ")} }", span)), keywordSpan, span)
        return after
    }

    /** `guides ( <orient> N ) ( <orient> N )…` → `layout.guides` list of { orientation, position }. */
    private fun consumeGuides(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"guides\" needs ( … ) groups")
            return valueStart
        }
        var i = valueStart
        val items = mutableListOf<String>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val leaves = group.children.mapNotNull { it.leafText() }
            val orientation = leaves.getOrNull(0)
            val position = leaves.getOrNull(1)
            if (orientation != null && position != null) {
                items += "{ orientation: $orientation, position: $position }"
            }
            i = after
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(CnlPropertyKind.Guides, listOf(CnlValue("[ ${items.joinToString(", ")} ]", span)), keywordSpan, span)
        return i
    }

    /** `grids ( <type> [count N] [size N] [gutter N] [margin N] [alignment a] [color #hex] [visible b] )…`. */
    private fun consumeGrids(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"grids\" needs ( … ) groups")
            return valueStart
        }
        var i = valueStart
        val items = mutableListOf<String>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            items += gridFragment(group)
            i = after
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(CnlPropertyKind.Grids, listOf(CnlValue("[ ${items.joinToString(", ")} ]", span)), keywordSpan, span)
        return i
    }

    private fun gridFragment(group: GGroup): String {
        val children = group.children
        val type = children.getOrNull(0)?.leafText()?.lowercase() ?: "columns"
        var count: String? = null
        var size: String? = null
        var gutter: String? = null
        var margin: String? = null
        var alignment: String? = null
        var color: String? = null
        var visible: String? = null
        var i = 1
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "count" -> { count = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "size" -> { size = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "gutter" -> { gutter = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "margin" -> { margin = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "alignment" -> { alignment = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "color" -> { color = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "visible" -> {
                    val flag = children.getOrNull(i + 1)?.leafText()?.let { CnlVocabulary.booleans[it.lowercase()] }
                    if (flag != null) { visible = flag.toString(); i += 2 } else { visible = "true"; i += 1 }
                }
                else -> i += 1
            }
        }
        val parts = buildList {
            add("type: $type")
            count?.let { add("count: $it") }
            size?.let { add("size: $it") }
            gutter?.let { add("gutter: $it") }
            margin?.let { add("margin: $it") }
            alignment?.let { add("alignment: $it") }
            color?.let { add("color: ${colorLiteral(it)}") }
            visible?.let { add("visible: $it") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    // ============ P6 consumers — media node / shape params / vector paths·networks / boolean / mask.

    /** `media ( asset <id> [video|image] [fill|fit|crop|tile|stretch] [focus center|(x y)] [alt «…»]
     *  [opacity n|$ref] [blend <mode>] [poster <id>] [autoplay] [loop] [replaceable] [unmuted] )`
     *  → a pre-lowered `media:` record body. */
    private fun consumeMedia(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"media\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Media, listOf(CnlValue(mediaFragment(group), span)), keywordSpan, span)
        return after
    }

    private fun mediaFragment(group: GGroup): String {
        val children = group.children
        var asset: String? = null
        var kind: String? = null      // only "video" survives; "image" is the reader default
        var fillMode: String? = null
        var focal: String? = null
        var alt: String? = null
        var opacity: String? = null
        var blend: String? = null
        var poster: String? = null
        var autoplay = false
        var loop = false
        var replaceable = false
        var muted: String? = null
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "asset" -> { asset = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "poster" -> { poster = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "video" -> { kind = "video"; i += 1 }
                text == "image" -> { kind = "image"; i += 1 }
                text in fillModeWords -> { fillMode = text; i += 1 }
                text == "focus" || text == "focal" -> { focal = focalFragment(children.getOrNull(i + 1)); i += 2 }
                text == "alt" -> { alt = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "opacity" -> { opacity = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "blend" -> { blend = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "autoplay" -> { autoplay = true; i += 1 }
                text == "loop" -> { loop = true; i += 1 }
                text == "replaceable" -> { replaceable = true; i += 1 }
                text == "unmuted" -> { muted = "false"; i += 1 }
                text == "muted" -> {
                    val flag = children.getOrNull(i + 1)?.leafText()?.let { CnlVocabulary.booleans[it.lowercase()] }
                    if (flag != null) { muted = flag.toString(); i += 2 } else { muted = "true"; i += 1 }
                }
                else -> i += 1
            }
        }
        val parts = buildList {
            asset?.let { add("asset: ${yamlString(it)}") }
            kind?.let { add("kind: $it") }
            fillMode?.let { add("fillMode: $it") }
            focal?.let { add("focalPoint: $it") }
            alt?.let { add("alt: ${yamlString(it)}") }
            opacity?.let { add("opacity: $it") }
            blend?.let { add("blendMode: $it") }
            poster?.let { add("poster: ${yamlString(it)}") }
            if (autoplay) add("autoplay: true")
            if (loop) add("loop: true")
            if (replaceable) add("replaceable: true")
            muted?.let { add("muted: $it") }
        }
        return parts.joinToString(", ")
    }

    /** `focus center` → scalar `center`; `focus (x y)` → `{ x: .., y: .. }`. */
    private fun focalFragment(node: GNode?): String? = when {
        node == null -> null
        node.leafText()?.lowercase() == "center" -> "center"
        else -> pointFragment(node)
    }

    /** `viewbox (x y w h)` → `viewBox: [ x, y, w, h ]`. */
    /** `arc ( start sweep )` → shape `arcStart` + `arcSweep` (ellipse pie/donut geometry). */
    private fun consumeShapeArc(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"arc\" needs a ( start sweep ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val nums = group.children.mapNotNull { it.leaf() }.filter { isNumber(it.token.text) }
        if (nums.size < 2) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "arc needs 2 numbers (start sweep)")
            return after
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.ShapeArc,
            listOf(CnlValue(nums[0].token.text, nums[0].token.span), CnlValue(nums[1].token.text, nums[1].token.span)),
            keywordSpan, span,
        )
        return after
    }

    private fun consumeViewBox(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"viewbox\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val nums = group.children.mapNotNull { it.leafText() }.filter { isNumber(it) }
        if (nums.size < 4) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "viewbox needs 4 numbers (x y width height)")
            return after
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.ViewBox,
            listOf(CnlValue("viewBox: [ ${nums[0]}, ${nums[1]}, ${nums[2]}, ${nums[3]} ]", span)),
            keywordSpan, span,
        )
        return after
    }

    /** One or more `path «d» [evenodd|nonzero]` → `paths: [ { d: "…"[, windingRule: evenodd] }, … ]`.
     *  Greedily consumes consecutive `path` phrases so they compose into one list. */
    private fun consumeVectorPaths(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val items = mutableListOf<String>()
        var i = valueStart
        var last = valueStart
        while (true) {
            val dTok = tokens.getOrNull(i) ?: break
            if (!dTok.isText) break
            var next = i + 1
            var winding = "nonzero"
            tokens.getOrNull(next)?.text?.lowercase()?.let { flag ->
                if (flag == "evenodd" || flag == "nonzero") { winding = flag; next += 1 }
            }
            items += if (winding == "evenodd") {
                "{ d: ${yamlString(dTok.text)}, windingRule: evenodd }"
            } else {
                "{ d: ${yamlString(dTok.text)} }"
            }
            last = next - 1
            if (tokens.getOrNull(next)?.text?.lowercase() == "path") { i = next + 1 } else { i = next; break }
        }
        if (items.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"path\" needs «path data»")
            return valueStart
        }
        val span = joinSpan(keywordSpan, tokens[last].span)
        properties += CnlProperty(
            CnlPropertyKind.VectorPaths,
            listOf(CnlValue("paths: [ ${items.joinToString(", ")} ]", span)),
            keywordSpan, span,
        )
        return i
    }

    /** `network ( vertex (…) … segment (…) … region [evenodd] loops (…) … )` → `network: { … }`. */
    private fun consumeNetwork(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"network\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.VectorNetwork, listOf(CnlValue(networkFragment(group), span)), keywordSpan, span)
        return after
    }

    private fun networkFragment(group: GGroup): String {
        val children = group.children
        val vertices = mutableListOf<String>()
        val segments = mutableListOf<String>()
        val regions = mutableListOf<String>()
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "vertex" -> {
                    children.getOrNull(i + 1)?.asGroup()?.let { vertices += vertexFragment(it) }
                    i += 2
                }
                "segment" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children
                        ?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
                    if (nums != null && nums.size >= 2) segments += "[ ${nums[0]}, ${nums[1]} ]"
                    i += 2
                }
                "region" -> {
                    i += 1
                    var winding = "nonzero"
                    children.getOrNull(i)?.leafText()?.lowercase()?.let { w ->
                        if (w == "evenodd" || w == "nonzero") { winding = w; i += 1 }
                    }
                    if (children.getOrNull(i)?.leafText()?.lowercase() == "loops") i += 1
                    val loops = mutableListOf<String>()
                    while (i < children.size) {
                        val g = children[i].asGroup() ?: break
                        val nums = g.children.mapNotNull { it.leafText() }.filter { isNumber(it) }
                        loops += "[ ${nums.joinToString(", ")} ]"
                        i += 1
                    }
                    // Per-region solid/token fills: `fill #hex` / `fill $ref` / `fill token:ref`.
                    val fills = mutableListOf<String>()
                    while (children.getOrNull(i)?.leafText()?.lowercase() == "fill") {
                        children.getOrNull(i + 1)?.leafText()?.let { fills += regionFillItem(it) }
                        i += 2
                    }
                    val parts = buildList {
                        if (winding == "evenodd") add("windingRule: evenodd")
                        add("loops: [ ${loops.joinToString(", ")} ]")
                        if (fills.isNotEmpty()) add("fills: [ ${fills.joinToString(", ")} ]")
                    }
                    regions += "{ ${parts.joinToString(", ")} }"
                }
                else -> i += 1
            }
        }
        val parts = buildList {
            add("vertices: [ ${vertices.joinToString(", ")} ]")
            if (segments.isNotEmpty()) add("segments: [ ${segments.joinToString(", ")} ]")
            if (regions.isNotEmpty()) add("regions: [ ${regions.joinToString(", ")} ]")
        }
        return "network: { ${parts.joinToString(", ")} }"
    }

    private fun vertexFragment(group: GGroup): String {
        val children = group.children
        val x = children.getOrNull(0)?.leafText()
        val y = children.getOrNull(1)?.leafText()
        var inH: String? = null
        var outH: String? = null
        var mirror: String? = null
        var corner = false
        var radius: String? = null
        var i = 2
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "in" -> { inH = offsetFragment(children.getOrNull(i + 1)); i += 2 }
                "out" -> { outH = offsetFragment(children.getOrNull(i + 1)); i += 2 }
                "mirror" -> { mirror = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "corner" -> { corner = true; i += 1 }
                "radius" -> { radius = children.getOrNull(i + 1)?.leafText()?.takeIf { isNumber(it) }; i += 2 }
                else -> i += 1
            }
        }
        val parts = buildList {
            add("x: ${x ?: "0"}")
            add("y: ${y ?: "0"}")
            inH?.let { add("in: $it") }
            outH?.let { add("out: $it") }
            mirror?.let { if (it.lowercase() != "none") add("mirror: $it") }
            if (corner) add("corner: true")
            radius?.let { add("radius: $it") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** `(dx dy)` → `[ dx, dy ]`. */
    private fun offsetFragment(node: GNode?): String? {
        val nums = node?.asGroup()?.children?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
        if (nums == null || nums.size < 2) return null
        return "[ ${nums[0]}, ${nums[1]} ]"
    }

    /** `mask <type> [clips ( id id )] [from <id>]` → `mask:` block entries (type + appliesTo [+ source]). */
    private fun consumeMask(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        var i = valueStart
        val entries = mutableListOf<String>()
        val typeTok = tokens.getOrNull(i)?.text?.lowercase()
        if (typeTok != null && typeTok in maskTypeWords) { entries += "type: $typeTok"; i += 1 }
        if (tokens.getOrNull(i)?.text?.lowercase() == "clips" && tokens.getOrNull(i + 1)?.text == "(") {
            val (group, after) = parseGroup(tokens, i + 1)
            val ids = group.children.mapNotNull { it.leafText() }
            if (ids.isNotEmpty()) entries += "appliesTo: [ ${ids.joinToString(", ")} ]"
            i = after
        }
        if (tokens.getOrNull(i)?.text?.lowercase() == "from") {
            tokens.getOrNull(i + 1)?.let { entries += "source: ${yamlString(it.text)}" }
            i += 2
        }
        if (entries.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"mask\" needs a type (alpha, vector or luminance)")
            return i
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(CnlPropertyKind.Mask, entries.map { CnlValue(it, span) }, keywordSpan, span)
        return i
    }

    private val maskTypeWords = setOf("alpha", "vector", "luminance")

    // --- components (instance side) ---

    /** `variant ( axis value … )` → a `component.variant` selection record. */
    private fun consumeVariant(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"variant\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Variant,
            listOf(CnlValue("variant: ${variantRecordFragment(group)}", span)),
            keywordSpan,
            span,
        )
        return after
    }

    /** `props ( name value … )` → a `component.props` record. */
    private fun consumeProps(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"props\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Props,
            listOf(CnlValue("props: ${propsRecordFragment(group)}", span)),
            keywordSpan,
            span,
        )
        return after
    }

    /** `slot <name> (fill…) (fill…)` → one `overrides.slots` entry (a list of instance fills). */
    private fun consumeSlot(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val nameToken = tokens.getOrNull(valueStart)
        if (nameToken == null || nameToken.text == "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"slot\" needs a name and ( … ) fills")
            return valueStart
        }
        var i = valueStart + 1
        val fills = mutableListOf<String>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            fills += slotFillFragment(group)
            i = after
        }
        if (fills.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Slot \"${nameToken.text}\" needs ( … ) fills")
            return i
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.SlotOverride,
            listOf(CnlValue("${nameToken.text}: [ ${fills.joinToString(", ")} ]", span)),
            keywordSpan,
            span,
        )
        return i
    }

    /** `nested <target> ( [variant (…)] [props (…)] )` → one `overrides.nestedInstances` entry. */
    private fun consumeNested(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val target = tokens.getOrNull(valueStart)
        if (target == null || target.text == "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"nested\" needs a target and a ( … ) group")
            return valueStart
        }
        val groupStart = valueStart + 1
        if (tokens.getOrNull(groupStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Nested \"${target.text}\" needs a ( … ) group")
            return groupStart
        }
        val (group, after) = parseGroup(tokens, groupStart)
        val inner = instanceOverrideRecordParts(group)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.NestedOverride,
            listOf(CnlValue("${target.text}: { ${inner.joinToString(", ")} }", span)),
            keywordSpan,
            span,
        )
        return after
    }

    /** A slot fill group `( <instanceRef> [variant (…)] [props (…)] )` → `{ instance:…, variant:…, props:… }`. */
    private fun slotFillFragment(group: GGroup): String {
        val children = group.children
        val parts = mutableListOf<String>()
        var i = 0
        children.firstOrNull()?.leafText()?.let { parts += "instance: ${plainScalar(it)}"; i = 1 }
        parts += instanceOverrideRecordParts(GGroup(children.drop(i)))
        return "{ ${parts.joinToString(", ")} }"
    }

    /** The `variant`/`props` sub-records shared by nested overrides and slot fills. */
    private fun instanceOverrideRecordParts(group: GGroup): List<String> {
        val children = group.children
        val parts = mutableListOf<String>()
        var i = 0
        while (i < children.size) {
            val kw = children[i].leafText()?.lowercase()
            val record = children.getOrNull(i + 1)?.asGroup()
            when {
                kw == "variant" && record != null -> { parts += "variant: ${variantRecordFragment(record)}"; i += 2 }
                kw == "props" && record != null -> { parts += "props: ${propsRecordFragment(record)}"; i += 2 }
                else -> i += 1
            }
        }
        return parts
    }

    /** `( axis value … )` → `{ axis: value, … }` (variant axis→value record; iteration order preserved). */
    private fun variantRecordFragment(group: GGroup): String {
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = buildList {
            var i = 0
            while (i + 1 < leaves.size) {
                add("${leaves[i]}: ${plainScalar(leaves[i + 1])}")
                i += 2
            }
        }
        return "{ ${entries.joinToString(", ")} }"
    }

    /** `( name value … )` → `{ name: <propValue>, … }`; a value may be a scalar or a `( … )` record. */
    private fun propsRecordFragment(group: GGroup): String {
        val children = group.children
        val entries = mutableListOf<String>()
        var i = 0
        while (i < children.size) {
            val name = children[i].leafText()
            val valueNode = children.getOrNull(i + 1)
            if (name == null || valueNode == null) {
                i += 1
                continue
            }
            val record = valueNode.asGroup()
            entries += if (record != null) {
                "$name: ${propValueRecordFragment(record)}"
            } else {
                "$name: ${propScalarFragment(valueNode.leaf())}"
            }
            i += 2
        }
        return "{ ${entries.joinToString(", ")} }"
    }

    /** A bare prop scalar: quoted text, number, boolean, `{{expr}}` binding, or a bare word (→ text). */
    private fun propScalarFragment(leaf: GLeaf?): String {
        val token = leaf?.token ?: return yamlString("")
        val text = token.text
        return when {
            token.isText -> yamlString(text)
            isNumber(text) -> text
            text.lowercase() in CnlVocabulary.booleans -> CnlVocabulary.booleans.getValue(text.lowercase()).toString()
            else -> yamlString(text)
        }
    }

    /** A prop value record: `(swap ref)`, `(text «…» [key k])`, or `(data expr)`. */
    private fun propValueRecordFragment(group: GGroup): String {
        val children = group.children
        return when (children.firstOrNull()?.leafText()?.lowercase()) {
            "swap" -> "{ type: instanceSwap, value: ${plainScalar(children.getOrNull(1)?.leafText().orEmpty())} }"
            "data" -> "{ type: dataBinding, value: ${yamlString(children.getOrNull(1)?.leafText().orEmpty())} }"
            "text" -> {
                var value = ""
                var key: String? = null
                var i = 1
                while (i < children.size) {
                    val node = children[i]
                    when {
                        node.leaf()?.token?.isText == true -> { value = node.leafText().orEmpty(); i += 1 }
                        node.leafText()?.lowercase() == "key" -> { key = children.getOrNull(i + 1)?.leafText(); i += 2 }
                        else -> i += 1
                    }
                }
                val parts = buildList {
                    add("type: text")
                    add("value: ${yamlString(value)}")
                    key?.let { add("i18nKey: ${plainScalar(it)}") }
                }
                "{ ${parts.joinToString(", ")} }"
            }
            else -> "{ }"
        }
    }

    /** A YAML plain scalar when safe, otherwise a quoted string (keeps `md`, `ds/Icon/Check` bare). */
    private fun plainScalar(value: String): String =
        if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == '/' }) {
            value
        } else {
            yamlString(value)
        }

    private fun consumeStroke(
        tokens: List<Token>,
        start: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        // Record form `stroke ( … )` → a `strokes:` list (stack / dash / cap / join).
        if (tokens.getOrNull(start)?.text == "(") {
            val (group, after) = parseGroup(tokens, start)
            val fragment = strokeRecordFragment(group)
            val span = joinSpan(keywordSpan, tokens[after - 1].span)
            properties += CnlProperty(CnlPropertyKind.StrokeComplex, listOf(CnlValue(fragment, span)), keywordSpan, span)
            return after
        }
        // Flat form `stroke #hex [weight] [align]` (P0; keeps the color token span for write-back).
        val color = tokens.getOrNull(start)
        if (color == null || !isColor(color.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadColor, lineNumber, "Stroke has no color")
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

    /** `stroke ( color … [weight N] [align a] [dash (n n)] [cap w] [join w] )` → `strokes:` list. */
    private fun strokeRecordFragment(group: GGroup): String {
        val children = group.children
        val paints = mutableListOf<String>()
        var weight: String? = null
        var align: String? = null
        var dash: String? = null
        var cap: String? = null
        var join: String? = null
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "color" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { paints += paintColorEntry(it) }
                    i += 2
                }
                text == "weight" -> { weight = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "align" -> { align = children.getOrNull(i + 1)?.leafText()?.lowercase(); i += 2 }
                text == "dash" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    dash = nums?.joinToString(", ")?.let { "[ $it ]" }
                    i += 2
                }
                text == "cap" -> { cap = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "join" -> { join = children.getOrNull(i + 1)?.leafText(); i += 2 }
                else -> i += 1
            }
        }
        val items = paints.mapIndexed { index, paint ->
            if (index == 0) {
                val props = buildList {
                    add(paint)
                    weight?.let { add("weight: $it") }
                    align?.let { add("position: $it") }
                    dash?.let { add("dash: $it") }
                    cap?.let { add("caps: $it") }
                    join?.let { add("joins: $it") }
                }
                "{ ${props.joinToString(", ")} }"
            } else {
                "{ $paint }"
            }
        }
        return "strokes: [ ${items.joinToString(", ")} ]"
    }

    /** A paint color for a stroke item map: `token: id` for `$ref`, else `color: "#hex"`. */
    private fun paintColorEntry(raw: String): String =
        if (raw.startsWith("$")) "token: ${raw.removePrefix("$")}" else "color: ${colorLiteral(raw)}"

    /** `effect ( <type> … )` → one `effects:` list item. */
    private fun consumeEffect(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"effect\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Effect, listOf(CnlValue(effectFragment(group), span)), keywordSpan, span)
        return after
    }

    private fun effectFragment(group: GGroup): String {
        val children = group.children
        val typeWord = children.getOrNull(0)?.leafText()?.lowercase()
        val typeKey = effectTypes[typeWord] ?: (children.getOrNull(0)?.leafText() ?: "dropShadow")
        var color: String? = null
        var x: String? = null
        var y: String? = null
        var blur: String? = null
        var spread: String? = null
        var i = 1
        while (i < children.size) {
            val node = children[i]
            val text = node.leafText()?.lowercase()
            when {
                text == "color" -> { color = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "offset" -> {
                    val pt = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    if (pt != null && pt.size >= 2) { x = pt[0]; y = pt[1] }
                    i += 2
                }
                text == "blur" -> { blur = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "spread" -> { spread = children.getOrNull(i + 1)?.leafText(); i += 2 }
                blur == null && text != null && isNumber(text) -> { blur = text; i += 1 } // layerBlur/backgroundBlur positional radius
                else -> i += 1
            }
        }
        val parts = buildList {
            add("type: $typeKey")
            color?.let { add(paintColorEntry(it)) }
            x?.let { add("x: $it") }
            y?.let { add("y: $it") }
            blur?.let { add("blur: $it") }
            spread?.let { add("spread: $it") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    private val effectTypes = mapOf(
        "dropshadow" to "dropShadow", "innershadow" to "innerShadow",
        "layerblur" to "layerBlur", "backgroundblur" to "backgroundBlur",
    )

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
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "No value given")
            return start
        }
        if (!isNumber(value.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "\"${value.text}\" is not a number")
            return start
        }
        add(kind, listOf(CnlValue(value.text, value.span)), start)
        return start + 1
    }

    /**
     * A scalar that may be a number, a `$token` ref, or a `{{expr}}` data binding — used by `opacity`
     * so the value survives to a `Bindable.DataRef` / `Bindable.VarRef` in the style reader.
     */
    private fun consumeBindableNumber(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        start: Int,
        add: (CnlPropertyKind, List<CnlValue>, Int) -> Unit,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val value = tokens.getOrNull(start)
        if (value == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "No value given")
            return start
        }
        if (!isNumber(value.text) && !isBinding(value.text) && !value.text.startsWith("$")) {
            CnlDiagnostics.warn(
                diagnostics, CnlRule.BadNumber, lineNumber,
                "\"${value.text}\" is not a number, \$ref or {{expr}}",
            )
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
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Rotation needs a number")
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
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Padding needs numbers")
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
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Position needs two numbers")
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
            CnlDiagnostics.warn(diagnostics, CnlRule.BadDirection, lineNumber, "No direction given")
            return start
        }
        add(CnlPropertyKind.AlignParent, listOf(CnlValue(value.text, value.span)), start)
        return start + 1
    }

    /** `size W by H` (box size) or `size N` (font size on a text element). */
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
        CnlDiagnostics.warn(diagnostics, CnlRule.IncompleteSize, lineNumber, "Size is incomplete")
        return start
    }

    private fun singleWord(kind: CnlPropertyKind, value: String, token: Token): CnlProperty =
        CnlProperty(kind, listOf(CnlValue(value, token.span)), token.span, token.span)

    private fun isDegreeWord(text: String): Boolean {
        val word = text.lowercase().trimEnd('.', ',')
        return word in CnlVocabulary.degreeWords
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

    // --- interactions & motion (P7) ---
    //
    // One trigger phrase = one `interaction:` typed block; repeated triggers on a line accumulate as
    // separate interactions (each re-enters consumeInteraction from parseFrom). Every value that is a
    // `( … )` group resolves its sub-keywords in a GROUP-LOCAL vocabulary here — never through the
    // top-level propertyKeywords table — so `type`/`direction`/`position`/`duration`/`offset`/`loop`
    // etc. can safely recur without colliding with node-level P0/P1a keywords.

    /** Trigger keyword (lowercase) → reader `trigger` enum spelling. */
    private val triggerTypes = mapOf(
        "onclick" to "onClick",
        "onhover" to "onHover",
        "onpress" to "onPress",
        "ondrag" to "onDrag",
        "onkey" to "onKey",
        "afterdelay" to "afterDelay",
        "whilehovering" to "whileHovering",
        "whilepressed" to "whilePressed",
        "onvariablechange" to "onVariableChange",
    )

    /** Triggers that carry a positional argument group. */
    private val triggerArgKinds = setOf("onkey", "afterdelay", "onvariablechange")

    /** Action keyword (lowercase) → reader action `type` spelling. */
    private val actionTypes = mapOf(
        "navigate" to "navigate",
        "openoverlay" to "openOverlay",
        "swapoverlay" to "swapOverlay",
        "closeoverlay" to "closeOverlay",
        "back" to "back",
        "openlink" to "openLink",
        "setvariable" to "setVariable",
        "changetovariant" to "changeToVariant",
        "scrollto" to "scrollTo",
        "runactionset" to "runActionSet",
    )

    /** Actions that accept an `animate ( … )` transition modifier. */
    private val transitionActions = setOf("navigate", "openOverlay", "swapOverlay", "closeOverlay")

    /** `animate ( … )` sub-keyword (lowercase) → reader `animation` map key. */
    private val transitionSubKeys = mapOf(
        "type" to "type",
        "easing" to "easing",
        "duration" to "durationMs",
        "direction" to "direction",
        "mass" to "mass",
        "stiffness" to "stiffness",
        "damping" to "damping",
    )

    /** Frame property sub-keys accepted inside a motion keyframe group. */
    private val motionFrameKeys = setOf("opacity", "x", "y", "scale", "rotation")

    /**
     * A trigger-led interaction phrase `<trigger> [ (arg) ] <action>…`, up to the next trigger or a
     * non-action word. Emits one `interaction:` block; repeated triggers accumulate as separate
     * interactions (each re-enters here from [parseFrom]).
     */
    private fun consumeInteraction(
        tokens: List<Token>,
        keywordStart: Int,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val triggerWord = tokens[keywordStart].text.lowercase()
        val parts = mutableListOf("trigger: ${triggerTypes[triggerWord] ?: triggerWord}")
        var i = valueStart
        if (triggerWord in triggerArgKinds && tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val arg = group.children.firstOrNull()?.leafText()
            if (arg != null) when (triggerWord) {
                "onkey" -> parts += "key: ${yamlString(arg)}"
                "afterdelay" -> parts += "delayMs: $arg"
                "onvariablechange" -> parts += "variable: ${yamlString(arg)}"
            }
            i = after
        }
        val actions = mutableListOf<String>()
        while (i < tokens.size) {
            val word = tokens[i].text.lowercase()
            if (word in triggerTypes) break // next interaction — re-dispatched by parseFrom
            val type = actionTypes[word] ?: break // not an action → hand back to parseFrom
            val (fragment, next) = consumeAction(type, tokens, i + 1, lineNumber, diagnostics)
            actions += fragment
            i = next
        }
        if (actions.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"$triggerWord\" needs an action")
            return i
        }
        parts += "actions: [ ${actions.joinToString(", ")} ]"
        val span = joinSpan(keywordSpan, tokens[(i - 1).coerceIn(0, tokens.size - 1)].span)
        properties += CnlProperty(
            CnlPropertyKind.Interactions,
            listOf(CnlValue(parts.joinToString(", "), span)),
            keywordSpan,
            span,
        )
        return i
    }

    /**
     * One action `<verb> [ (positional) ] [ modifier… ]` → a ready `{ type: …, … }` map plus the index
     * after the last token consumed. Modifiers (`animate`/`overlay`/`to`/`variant`/`animated`) are
     * resolved group-locally by action type, never through the top-level keyword table.
     */
    private fun consumeAction(
        type: String,
        tokens: List<Token>,
        start: Int,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Pair<String, Int> {
        var i = start
        val parts = mutableListOf("type: $type")

        fun positional(): String? {
            if (tokens.getOrNull(i)?.text != "(") return null
            val (group, after) = parseGroup(tokens, i)
            i = after
            return group.children.firstOrNull()?.leafText()
        }

        when (type) {
            "navigate" -> positional()?.let { parts += "to: ${yamlString(it)}" }
            "openOverlay", "swapOverlay" -> positional()?.let { parts += "destination: ${yamlString(it)}" }
            "openLink" -> positional()?.let { parts += "url: ${yamlString(it)}" }
            "setVariable" -> positional()?.let { parts += "variable: ${yamlString(it)}" }
            "changeToVariant", "scrollTo" -> positional()?.let { parts += "target: ${yamlString(it)}" }
            "runActionSet" -> positional()?.let { parts += "actionSet: ${yamlString(it)}" }
            else -> {} // back, closeOverlay carry no positional argument
        }

        modifiers@ while (i < tokens.size) {
            when (tokens[i].text.lowercase()) {
                "animate" -> {
                    if (type !in transitionActions) break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    parts += "animation: ${transitionFragment(group.first)}"
                    i = group.second
                }
                "overlay" -> {
                    if (type != "openOverlay") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    parts += "overlay: ${overlayFragment(group.first)}"
                    i = group.second
                }
                "to" -> {
                    if (type != "setVariable") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    group.first.children.firstOrNull()?.leafText()?.let { parts += "value: ${yamlString(it)}" }
                    i = group.second
                }
                "variant" -> {
                    if (type != "changeToVariant") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    parts += "variant: ${variantFragment(group.first)}"
                    i = group.second
                }
                "animated" -> {
                    if (type != "scrollTo") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    val flag = group.first.children.firstOrNull()?.leafText()?.lowercase()
                    if (flag != null && CnlVocabulary.booleans[flag] == false) parts += "animated: false"
                    i = group.second
                }
                else -> break@modifiers
            }
        }
        return "{ ${parts.joinToString(", ")} }" to i
    }

    /** The `( … )` group starting right after the keyword at [keywordIdx], or null when absent. */
    private fun groupAfter(tokens: List<Token>, keywordIdx: Int): Pair<GGroup, Int>? {
        if (tokens.getOrNull(keywordIdx + 1)?.text != "(") return null
        return parseGroup(tokens, keywordIdx + 1)
    }

    /** `animate (type … easing … duration N direction …)` → an `animation:` map fragment. */
    private fun transitionFragment(group: GGroup): String {
        val children = group.children
        val parts = mutableListOf<String>()
        var i = 0
        while (i < children.size) {
            val key = transitionSubKeys[children[i].leafText()?.lowercase()]
            val value = children.getOrNull(i + 1)?.leafText()
            if (key != null && value != null) {
                parts += "$key: $value"
                i += 2
            } else {
                i += 1
            }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** `overlay (position … offset (x y) closeOnOutside (false) background …)` → an `overlay:` fragment. */
    private fun overlayFragment(group: GGroup): String {
        val children = group.children
        fun valueAt(index: Int): String? {
            val node = children.getOrNull(index) ?: return null
            return node.leafText() ?: node.asGroup()?.children?.firstOrNull()?.leafText()
        }
        val parts = mutableListOf<String>()
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "position" -> { valueAt(i + 1)?.let { parts += "position: $it" }; i += 2 }
                "offset" -> {
                    val pt = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    if (pt != null && pt.size >= 2) parts += "offset: { x: ${pt[0]}, y: ${pt[1]} }"
                    i += 2
                }
                "closeonoutside" -> { valueAt(i + 1)?.let { parts += "closeOnOutsideClick: ${boolLiteral(it)}" }; i += 2 }
                "background" -> { valueAt(i + 1)?.let { parts += "background: ${colorLiteral(it)}" }; i += 2 }
                else -> i += 1
            }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** `variant (state hover size md)` → a `{ state: hover, size: md }` selection map. */
    private fun variantFragment(group: GGroup): String {
        val leaves = group.children.mapNotNull { it.leafText() }
        val parts = mutableListOf<String>()
        var i = 0
        while (i + 1 < leaves.size) {
            parts += "${leaves[i]}: ${leaves[i + 1]}"
            i += 2
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** One keyframe group `(at [prop N]…)` → `{ at: N, prop: N, … }`. */
    private fun motionFrameFragment(group: GGroup): String {
        val leaves = group.children.mapNotNull { it.leafText() }
        val at = leaves.firstOrNull() ?: "0"
        val parts = mutableListOf("at: $at")
        var i = 1
        while (i < leaves.size) {
            val key = leaves[i].lowercase()
            val value = leaves.getOrNull(i + 1)
            if (key in motionFrameKeys && value != null && isNumber(value)) {
                parts += "$key: $value"
                i += 2
            } else {
                i += 1
            }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    /** `motion (ref) [duration N] [loop] [frames (at …)…]` → a `motion:` typed block. */
    private fun consumeMotion(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        var i = valueStart
        val ref: String = if (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            i = after
            group.children.firstOrNull()?.leafText() ?: ""
        } else {
            ""
        }
        if (ref.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"motion\" needs a (ref) group")
            return i
        }
        var duration: String? = null
        var loop = false
        val frames = mutableListOf<String>()
        fallback@ while (i < tokens.size) {
            when (tokens[i].text.lowercase()) {
                "duration" -> {
                    val value = tokens.getOrNull(i + 1)?.takeIf { isNumber(it.text) } ?: break@fallback
                    duration = value.text
                    i += 2
                }
                "loop" -> { loop = true; i += 1 }
                "frames" -> {
                    i += 1
                    while (tokens.getOrNull(i)?.text == "(") {
                        val (group, after) = parseGroup(tokens, i)
                        frames += motionFrameFragment(group)
                        i = after
                    }
                }
                else -> break@fallback
            }
        }
        val fallbackParts = buildList {
            duration?.let { add("durationMs: $it") }
            if (loop) add("loop: true")
            if (frames.isNotEmpty()) add("frames: [ ${frames.joinToString(", ")} ]")
        }
        val motionParts = buildList {
            add("ref: ${yamlString(ref)}")
            if (fallbackParts.isNotEmpty()) add("fallback: { ${fallbackParts.joinToString(", ")} }")
        }
        val span = joinSpan(keywordSpan, tokens[(i - 1).coerceIn(0, tokens.size - 1)].span)
        properties += CnlProperty(
            CnlPropertyKind.Motion,
            listOf(CnlValue(motionParts.joinToString(", "), span)),
            keywordSpan,
            span,
        )
        return i
    }

    // ========================================================================
    // P10 parser consumers — responsive `when (…)`, export settings, handoff note/measure/code.
    // --- responsive: `when ( <dim value>… ) <override phrase>… ` ------------

    private val responsiveTerminators = setOf("when", "export", "note", "measure", "code", "off")

    /** Group sub-keyword (lowercased token) -> reader dimension key (ReaderEnums.responsiveDimension). */
    private val responsiveDimensionKeys = mapOf(
        "breakpoint" to "breakpoint",
        "devicepreset" to "devicePreset",
        "platform" to "platform",
        "theme" to "theme",
        "density" to "density",
        "locale" to "locale",
        "direction" to "direction",
        "brand" to "brand",
        "state" to "state",
    )

    /**
     * `when ( <dim value>… ) <override>… ` → one `responsive.variants` record. The selector group
     * is AND-ed dimensions; every following property phrase (routed through a nested BlockBuilder,
     * exactly like a base node) is captured until the next scope-terminating clause keyword.
     */
    private fun consumeResponsive(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"when\" needs a ( … ) selector group")
            return valueStart
        }
        val (selectorGroup, afterSelector) = parseGroup(tokens, valueStart)
        val selectors = responsiveSelectors(selectorGroup) ?: run {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"when\" selects nothing")
            return afterSelector
        }
        val overrideProps = mutableListOf<CnlProperty>()
        var i = afterSelector
        while (i < tokens.size) {
            val tok = tokens[i]
            if (!tok.isText && tok.text.lowercase() in responsiveTerminators) break
            i = when {
                tok.isText -> i + 1 // stray text inside a variant scope: ignore
                isNumber(tok.text) -> parseLeadingNumber(tokens, i, overrideProps, lineNumber, diagnostics)
                else -> parseWord(tokens, i, overrideProps, lineNumber, diagnostics)
            }
        }
        val builder = BlockBuilder()
        overrideProps.forEach { applyProperty(builder, it) }
        val record = builder.variantRecord(selectors)
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(CnlPropertyKind.Responsive, listOf(CnlValue(record, span)), keywordSpan, span)
        return i
    }

    /** `( breakpoint sm platform ios … )` -> `{ breakpoint: "sm", platform: "ios" }` or null when empty. */
    private fun responsiveSelectors(group: GGroup): String? {
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = mutableListOf<String>()
        var i = 0
        while (i < leaves.size - 1) {
            val readerKey = responsiveDimensionKeys[leaves[i].lowercase()]
            if (readerKey != null) {
                entries += "$readerKey: ${yamlString(leaves[i + 1])}"
                i += 2
            } else {
                i += 1
            }
        }
        return if (entries.isEmpty()) null else "{ ${entries.joinToString(", ")} }"
    }

    // --- export: `export ( fmt [at N] [«suffix»] )… ` | `export off` --------

    private val exportFormatWords = setOf("png", "jpg", "jpeg", "svg", "pdf")

    /**
     * `export (png at 2 «@2x») (svg)` → `settings: [ … ]` (one value per setting so repeated
     * `export` clauses on one line merge into a single export block); `export off` → `enabled: false`.
     */
    private fun consumeExport(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val next = tokens.getOrNull(valueStart)
        if (next != null && next.text.lowercase() == "off") {
            val span = joinSpan(keywordSpan, next.span)
            properties += CnlProperty(CnlPropertyKind.Export, listOf(CnlValue("enabled: false", span)), keywordSpan, span)
            return valueStart + 1
        }
        var i = valueStart
        val settings = mutableListOf<CnlValue>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            settings += CnlValue(exportSettingFragment(group), joinSpan(tokens[i].span, tokens[after - 1].span))
            i = after
        }
        if (settings.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"export\" needs ( … ) settings or `off`")
            return valueStart
        }
        properties += CnlProperty(CnlPropertyKind.Export, settings, keywordSpan, joinSpan(keywordSpan, tokens[i - 1].span))
        return i
    }

    /** `( png at 2 «@2x» )` -> `{ format: png, scale: 2, suffix: "@2x" }`. */
    private fun exportSettingFragment(group: GGroup): String {
        val children = group.children
        var format: String? = null
        var scale: String? = null
        var suffix: String? = null
        var i = 0
        while (i < children.size) {
            val node = children[i]
            val text = node.leafText()
            when {
                text?.lowercase() == "at" -> { scale = children.getOrNull(i + 1)?.leafText(); i += 2 }
                node.leafIsText() -> { suffix = text; i += 1 }
                text != null && text.lowercase() in exportFormatWords -> { format = text.lowercase(); i += 1 }
                else -> i += 1
            }
        }
        val parts = buildList {
            format?.let { add("format: $it") }
            scale?.let { add("scale: $it") }
            suffix?.let { add("suffix: ${yamlString(it)}") }
        }
        return "{ ${parts.joinToString(", ")} }"
    }

    // --- handoff: note / measure / code (parse-only; folded into one handoff block) --

    /** `note «text» ( [id …] [target …] [audience …] )` → one `handoff.annotations` record. */
    private fun consumeHandoffNote(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val textTok = tokens.getOrNull(valueStart)
        if (textTok == null || !textTok.isText) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"note\" needs «text»")
            return valueStart
        }
        val parts = mutableListOf("text: ${yamlString(textTok.text)}")
        var i = valueStart + 1
        if (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val leaves = group.children.mapNotNull { it.leafText() }
            var j = 0
            while (j < leaves.size - 1) {
                when (leaves[j].lowercase()) {
                    "id" -> { parts += "id: ${yamlString(leaves[j + 1])}"; j += 2 }
                    "target" -> { parts += "target: ${yamlString(leaves[j + 1])}"; j += 2 }
                    "audience" -> { parts += "audience: ${yamlString(leaves[j + 1])}"; j += 2 }
                    else -> j += 1
                }
            }
            i = after
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(CnlPropertyKind.Annotation, listOf(CnlValue("{ ${parts.joinToString(", ")} }", span)), keywordSpan, span)
        return i
    }

    private val measureAxisWords = setOf("inline", "block")

    /** `measure ( from A to B <axis> [value N] )` → one `handoff.measurements` record. */
    private fun consumeHandoffMeasure(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"measure\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val children = group.children
        var from: String? = null
        var to: String? = null
        var axis: String? = null
        var value: String? = null
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "from" -> { from = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "to" -> { to = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "value" -> { value = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text != null && text in measureAxisWords -> { axis = text; i += 1 }
                else -> i += 1
            }
        }
        val parts = buildList {
            from?.let { add("from: ${yamlString(it)}") }
            to?.let { add("to: ${yamlString(it)}") }
            axis?.let { add("axis: $it") }
            value?.let { add("value: $it") }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.Measurement, listOf(CnlValue("{ ${parts.joinToString(", ")} }", span)), keywordSpan, span)
        return after
    }

    /** `code ( framework «…» component «…» )` → `handoff.code` (reader key is `componentHint`). */
    private fun consumeHandoffCode(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"code\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val children = group.children
        var framework: String? = null
        var component: String? = null
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "framework" -> { framework = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "component" -> { component = children.getOrNull(i + 1)?.leafText(); i += 2 }
                else -> i += 1
            }
        }
        val parts = buildList {
            framework?.let { add("framework: ${yamlString(it)}") }
            component?.let { add("componentHint: ${yamlString(it)}") }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(CnlPropertyKind.CodeHint, listOf(CnlValue("{ ${parts.joinToString(", ")} }", span)), keywordSpan, span)
        return after
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
            CnlPropertyKind.Width -> builder.sizing(width = values[0])
            CnlPropertyKind.Height -> builder.sizing(height = values[0])
            CnlPropertyKind.Fill -> builder.addFill(colorLiteral(values[0]))
            CnlPropertyKind.FillComplex -> builder.addFill(values[0])
            CnlPropertyKind.Stroke -> builder.style("stroke" to strokeLiteral(values))
            CnlPropertyKind.StrokeComplex -> builder.styleRaw(values[0])
            CnlPropertyKind.Effect -> builder.addEffect(values[0])
            CnlPropertyKind.Radius -> builder.style("radius" to values[0])
            CnlPropertyKind.Smoothing -> builder.style("cornerSmoothing" to values[0])
            CnlPropertyKind.Blend -> builder.style("blendMode" to values[0])
            CnlPropertyKind.StyleRefs -> values.forEach { builder.styleRaw(it) }
            CnlPropertyKind.Opacity -> builder.style("opacity" to bindingLiteral(values[0]))
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
            CnlPropertyKind.Id -> builder.node("id" to values[0])
            CnlPropertyKind.FontFamily -> builder.typography("fontFamily" to yamlString(values[0]))
            CnlPropertyKind.LineHeight -> builder.typography("lineHeight" to unitLiteral(values[0]))
            CnlPropertyKind.Tracking -> builder.typography("letterSpacing" to unitLiteral(values[0]))
            CnlPropertyKind.ParagraphSpacing -> builder.typography("paragraphSpacing" to values[0])
            CnlPropertyKind.TextAlign -> builder.typography("horizontalAlign" to values[0])
            CnlPropertyKind.TextValign -> builder.typography("verticalAlign" to values[0])
            CnlPropertyKind.TextCase -> builder.typography("case" to values[0])
            CnlPropertyKind.TextDecoration -> builder.typography("decoration" to values[0])
            CnlPropertyKind.Features -> builder.typography("openType" to values[0])
            CnlPropertyKind.Axes -> builder.typography("variableFont" to values[0])
            CnlPropertyKind.TextKey -> builder.text("key" to yamlString(values[0]))
            CnlPropertyKind.TextStyleRef -> builder.text("style" to values[0].removePrefix("$"))
            CnlPropertyKind.MaxLines -> builder.text("maxLines" to values[0])
            CnlPropertyKind.ListSettings -> builder.text("list" to values[0])
            CnlPropertyKind.AutoSize ->
                builder.text("resizing" to if (values[0].lowercase() == "both") "{ width: hug, height: hug }" else "{ height: hug }")
            CnlPropertyKind.Truncate -> {
                builder.text("overflow" to "truncate")
                builder.text("maxLines" to values[0])
            }
            CnlPropertyKind.Wrap -> builder.layout("wrap" to "true")
            CnlPropertyKind.Clip -> builder.layout("clipContent" to "true")
            CnlPropertyKind.Absolute -> builder.layoutPosition("mode: absolute")
            CnlPropertyKind.Distribute -> builder.layout("distribution" to values[0])
            CnlPropertyKind.Anchor -> values.forEach { builder.layoutPosition(it) }
            CnlPropertyKind.Constraints -> values.forEach { builder.constraintRaw(it) }
            CnlPropertyKind.ContainerAlign -> builder.layout("align" to values[0])
            CnlPropertyKind.Overflow -> builder.layout("overflow" to values[0])
            CnlPropertyKind.Scroll -> builder.layout("scroll" to values[0])
            CnlPropertyKind.Columns -> builder.layout("columns" to values[0])
            CnlPropertyKind.Rows -> builder.layout("rows" to values[0])
            CnlPropertyKind.Place -> builder.layout("placement" to values[0])
            CnlPropertyKind.Guides -> builder.layout("guides" to values[0])
            CnlPropertyKind.Grids -> builder.layout("grids" to values[0])
            CnlPropertyKind.Link -> builder.span(values[0])
            CnlPropertyKind.ComponentRef -> builder.component("ref: ${values[0]}")
            CnlPropertyKind.LibraryRef -> builder.component("libraryRef: ${values[0]}")
            CnlPropertyKind.Variant -> builder.component(values[0])
            CnlPropertyKind.Props -> builder.component(values[0])
            CnlPropertyKind.Detach -> builder.component("detach: true")
            CnlPropertyKind.ResetOverrides -> builder.component("resetOverrides: true")
            CnlPropertyKind.SlotOverride -> builder.overrideSlot(values[0])
            CnlPropertyKind.NestedOverride -> builder.overrideNested(values[0])
            CnlPropertyKind.Media -> builder.media(values[0])
            CnlPropertyKind.ShapePoints -> builder.shape("pointCount" to values[0])
            CnlPropertyKind.ShapeInner -> builder.shape("innerRadius" to values[0])
            CnlPropertyKind.ShapeArc -> {
                builder.shape("arcStart" to values[0])
                builder.shape("arcSweep" to values[1])
            }
            CnlPropertyKind.ViewBox -> builder.vector(values[0])
            CnlPropertyKind.IconRef -> builder.vector("iconRef: ${yamlString(values[0])}")
            CnlPropertyKind.PathRef -> builder.vector("pathRef: ${yamlString(values[0])}")
            CnlPropertyKind.VectorPaths -> builder.vector(values[0])
            CnlPropertyKind.VectorNetwork -> builder.vector(values[0])
            CnlPropertyKind.BooleanOp -> builder.vector("boolean: { op: ${values[0]} }")
            CnlPropertyKind.Mask -> values.forEach { builder.mask(it) }
            CnlPropertyKind.Interactions -> builder.addInteraction(values[0])
            CnlPropertyKind.Motion -> builder.motion(values[0])
            CnlPropertyKind.Responsive -> builder.responsiveVariant(values[0])
            CnlPropertyKind.Export -> values.forEach { v ->
                if (v == "enabled: false") builder.exportDisabled() else builder.exportSetting(v)
            }
            CnlPropertyKind.Annotation -> builder.handoffAnnotation(values[0])
            CnlPropertyKind.Measurement -> builder.handoffMeasurement(values[0])
            CnlPropertyKind.CodeHint -> builder.handoffCode(values[0])
        }
    }

    private fun sizeAxis(value: String): String =
        if (isNumber(value)) "{ type: fixed, value: $value }" else value

    private fun colorLiteral(color: String): String =
        if (color.startsWith("#") || color.startsWith("{{")) "\"$color\"" else color

    /** A region-fill token → a `fills:` list item: `#hex` (quoted), `token:ref` → map, else bare `$ref`. */
    private fun regionFillItem(token: String): String = when {
        token.startsWith("#") -> "\"$token\""
        token.startsWith("token:") -> "{ token: ${token.removePrefix("token:")} }"
        else -> token
    }

    /** Opacity/binding literal — quotes only `{{expr}}`; numbers and `$ref` pass through bare. */
    private fun bindingLiteral(value: String): String =
        if (value.startsWith("{{")) "\"$value\"" else value

    /** A `{{expr}}` data-binding token. Single-token only (multi-word exprs need a tokenizer rule). */
    private fun isBinding(text: String): Boolean = text.startsWith("{{") && text.endsWith("}}")

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
        private val fillParts = mutableListOf<String>()
        private val effectParts = mutableListOf<String>()
        private val typographyParts = mutableListOf<String>()
        private val textDirectParts = mutableListOf<String>()
        private val spanParts = mutableListOf<String>()
        private val positionParts = mutableListOf<String>()
        private val constraintParts = mutableListOf<String>()
        private val layoutPositionParts = mutableListOf<String>()
        private val componentParts = mutableListOf<String>()
        private val slotOverrideParts = mutableListOf<String>()
        private val nestedOverrideParts = mutableListOf<String>()
        private val mediaParts = mutableListOf<String>()
        private val vectorParts = mutableListOf<String>()
        private val maskParts = mutableListOf<String>()
        private val interactionParts = mutableListOf<String>()
        private var motionFragment: String? = null
        private var sizeWidth: String? = null
        private var sizeHeight: String? = null
        private val responsiveVariantParts = mutableListOf<String>()
        private val exportSettingParts = mutableListOf<String>()
        private var exportDisabled = false
        private val annotationParts = mutableListOf<String>()
        private val measurementParts = mutableListOf<String>()
        private var codeHintPart: String? = null

        fun node(pair: Pair<String, String>) { nodeParts += "${pair.first}: ${pair.second}" }
        fun shape(pair: Pair<String, String>) { shapeParts += "${pair.first}: ${pair.second}" }
        fun layout(pair: Pair<String, String>) { layoutParts += "${pair.first}: ${pair.second}" }
        fun style(pair: Pair<String, String>) { styleParts += "${pair.first}: ${pair.second}" }
        /** Appends a pre-formatted `key: value` fragment to the `style:` map (shared style refs). */
        fun styleRaw(fragment: String) { styleParts += fragment }
        /** Each `color`/`fill` phrase appends a paint; they compose into a `fills:` list. */
        fun addFill(value: String) { fillParts += value }
        /** Each `effect ( … )` phrase appends an effect; they compose into an `effects:` list. */
        fun addEffect(value: String) { effectParts += value }
        fun typography(pair: Pair<String, String>) { typographyParts += "${pair.first}: ${pair.second}" }
        /** A direct `text:` block key (key/style/list/resizing/overflow/maxLines), outside `typography:`. */
        fun text(pair: Pair<String, String>) { textDirectParts += "${pair.first}: ${pair.second}" }
        /** Each `link ( … )` phrase appends a `text.spans[]` item; they compose into `text: { spans: [ … ] }`. */
        fun span(fragment: String) { spanParts += fragment }
        fun position(pair: Pair<String, String>) { positionParts += "${pair.first}: ${pair.second}" }
        fun constraint(pair: Pair<String, String>) { constraintParts += "${pair.first}: ${pair.second}" }
        /** A pre-formatted `key: value` constraint fragment (per-axis constraints record). */
        fun constraintRaw(fragment: String) { constraintParts += fragment }
        /** A `layout.position` sub-map fragment (absolute mode + anchor sides, merged into one map). */
        fun layoutPosition(fragment: String) { layoutPositionParts += fragment }
        /** A `component:` block fragment (ref/libraryRef/variant/props/detach/resetOverrides). */
        fun component(fragment: String) { componentParts += fragment }
        /** One `overrides.slots` entry `name: [ … ]`. */
        fun overrideSlot(fragment: String) { slotOverrideParts += fragment }
        /** One `overrides.nestedInstances` entry `target: { … }`. */
        fun overrideNested(fragment: String) { nestedOverrideParts += fragment }
        /** The whole `media:` record body (image/video convenience layer). */
        fun media(fragment: String) { mediaParts += fragment }
        /** A `vector:` sub-entry (viewBox/iconRef/pathRef/paths/network/boolean), merged into ONE `vector:` map. */
        fun vector(fragment: String) { vectorParts += fragment }
        /** A `mask:` block `key: value` fragment. */
        fun mask(fragment: String) { maskParts += fragment }
        /** Each trigger phrase appends one interaction map; they emit as separate `interaction:` blocks. */
        fun addInteraction(fragment: String) { interactionParts += fragment }
        /** The node's single `motion:` block. */
        fun motion(fragment: String) { motionFragment = fragment }

        /** Each `when ( … ) …` clause contributes one `responsive.variants` record. */
        fun responsiveVariant(fragment: String) { responsiveVariantParts += fragment }
        /** Each export `( … )` setting group; repeated `export` clauses merge into one list. */
        fun exportSetting(fragment: String) { exportSettingParts += fragment }
        /** `export off` → `enabled: false` (only emitted when no settings were authored). */
        fun exportDisabled() { exportDisabled = true }
        fun handoffAnnotation(fragment: String) { annotationParts += fragment }
        fun handoffMeasurement(fragment: String) { measurementParts += fragment }
        fun handoffCode(fragment: String) { codeHintPart = fragment }

        /** Assembles one `responsive.variants` record from this (sub-)builder's captured overrides. */
        fun variantRecord(selectors: String): String {
            val parts = buildList {
                add("when: $selectors")
                variantLayoutMap()?.let { add("layout: $it") }
                variantStyleMap()?.let { add("style: $it") }
                variantTextMap()?.let { add("text: $it") }
            }
            return "{ ${parts.joinToString(", ")} }"
        }

        private fun variantLayoutMap(): String? {
            val parts = (layoutParts + sizingMap() + layoutPositionMap()).filter { it.isNotEmpty() }
            return if (parts.isEmpty()) null else "{ ${parts.joinToString(", ")} }"
        }

        private fun variantStyleMap(): String? {
            val parts = (listOf(fillsList(), effectsList()) + styleParts).filter { it.isNotEmpty() }
            return if (parts.isEmpty()) null else "{ ${parts.joinToString(", ")} }"
        }

        private fun variantTextMap(): String? {
            val parts = buildList {
                if (typographyParts.isNotEmpty()) add("typography: { ${typographyParts.joinToString(", ")} }")
                addAll(textDirectParts)
            }
            return if (parts.isEmpty()) null else "{ ${parts.joinToString(", ")} }"
        }

        fun sizing(width: String? = null, height: String? = null) {
            width?.let { sizeWidth = it }
            height?.let { sizeHeight = it }
        }

        fun toEntries(line: Int, diagnostics: DiagnosticCollector): List<TypedEntry> {
            val node = (nodeParts + positionMap() + constraintMap()).filter { it.isNotEmpty() }
            val layout = (layoutParts + sizingMap() + layoutPositionMap()).filter { it.isNotEmpty() }
            val style = (listOf(fillsList(), effectsList()) + styleParts).filter { it.isNotEmpty() }
            val text = buildList {
                if (typographyParts.isNotEmpty()) add("typography: { ${typographyParts.joinToString(", ")} }")
                addAll(textDirectParts)
                if (spanParts.isNotEmpty()) add("spans: [ ${spanParts.joinToString(", ")} ]")
            }
            val overrides = buildList {
                if (slotOverrideParts.isNotEmpty()) add("slots: { ${slotOverrideParts.joinToString(", ")} }")
                if (nestedOverrideParts.isNotEmpty()) add("nestedInstances: { ${nestedOverrideParts.joinToString(", ")} }")
            }
            val responsive = if (responsiveVariantParts.isEmpty()) emptyList()
                else listOf("variants: [ ${responsiveVariantParts.joinToString(", ")} ]")
            val export = buildList {
                if (exportSettingParts.isNotEmpty()) add("settings: [ ${exportSettingParts.joinToString(", ")} ]")
                else if (exportDisabled) add("enabled: false")
            }
            val handoff = buildList {
                if (annotationParts.isNotEmpty()) add("annotations: [ ${annotationParts.joinToString(", ")} ]")
                if (measurementParts.isNotEmpty()) add("measurements: [ ${measurementParts.joinToString(", ")} ]")
                codeHintPart?.let { add("code: $it") }
            }
            return buildList {
                entry("node", node, line, diagnostics)?.let { add(it) }
                entry("shape", shapeParts, line, diagnostics)?.let { add(it) }
                entry("media", mediaParts, line, diagnostics)?.let { add(it) }
                entry("vector", vectorParts, line, diagnostics)?.let { add(it) }
                entry("mask", maskParts, line, diagnostics)?.let { add(it) }
                entry("layout", layout, line, diagnostics)?.let { add(it) }
                entry("style", style, line, diagnostics)?.let { add(it) }
                entry("text", text, line, diagnostics)?.let { add(it) }
                entry("responsive", responsive, line, diagnostics)?.let { add(it) }
                entry("export", export, line, diagnostics)?.let { add(it) }
                entry("handoff", handoff, line, diagnostics)?.let { add(it) }
                interactionParts.forEach { part -> entry("interaction", listOf(part), line, diagnostics)?.let { add(it) } }
                motionFragment?.let { fragment -> entry("motion", listOf(fragment), line, diagnostics)?.let { add(it) } }
                entry("component", componentParts, line, diagnostics)?.let { add(it) }
                entry("overrides", overrides, line, diagnostics)?.let { add(it) }
            }
        }

        private fun fillsList(): String =
            if (fillParts.isEmpty()) "" else "fills: [ ${fillParts.joinToString(", ")} ]"

        private fun effectsList(): String =
            if (effectParts.isEmpty()) "" else "effects: [ ${effectParts.joinToString(", ")} ]"

        private fun positionMap(): String =
            if (positionParts.isEmpty()) "" else "position: { ${positionParts.joinToString(", ")} }"

        private fun constraintMap(): String =
            if (constraintParts.isEmpty()) "" else "constraints: { ${constraintParts.joinToString(", ")} }"

        private fun layoutPositionMap(): String =
            if (layoutPositionParts.isEmpty()) "" else "position: { ${layoutPositionParts.joinToString(", ")} }"

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
