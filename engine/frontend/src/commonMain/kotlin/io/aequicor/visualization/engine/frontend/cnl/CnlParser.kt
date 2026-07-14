package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.BooleanOpPatch
import io.aequicor.visualization.engine.frontend.blocks.ComponentPatch
import io.aequicor.visualization.engine.frontend.blocks.ExportPatch
import io.aequicor.visualization.engine.frontend.blocks.HandoffPatch
import io.aequicor.visualization.engine.frontend.blocks.InteractionPatch
import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.MaskPatch
import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.frontend.blocks.MotionPatch
import io.aequicor.visualization.engine.frontend.blocks.NestedInstancePatch
import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.NodePositionMode
import io.aequicor.visualization.engine.frontend.blocks.OverridesPatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsivePatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsiveVariantPatch
import io.aequicor.visualization.engine.frontend.blocks.SetOverridePatch
import io.aequicor.visualization.engine.frontend.blocks.ShapePatch
import io.aequicor.visualization.engine.frontend.blocks.SizingPatch
import io.aequicor.visualization.engine.frontend.blocks.SlotOverridePatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TextSpanPatch
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.blocks.VectorPatch
import io.aequicor.visualization.engine.frontend.blocks.readers.ReaderEnums
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.DirectPatchEntry
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.CodeHints
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignMeasurement
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.MotionFrame
import io.aequicor.visualization.engine.ir.model.MotionKeyframes
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorPath
import io.aequicor.visualization.subsystems.figures.VectorRegion
import io.aequicor.visualization.subsystems.figures.VectorSegment
import io.aequicor.visualization.subsystems.figures.VectorVertex

/**
 * The CNL clause parser: a single sentence of English `noun property…` phrases → a
 * [CnlElement] with per-value source spans, plus the desugaring of that element **directly**
 * into typed patches (`node`/`shape`/`layout`/`style`/`text` [DirectPatchEntry]s via
 * [CnlDirectDesugar] — no YAML anywhere). One parse serves both compile (desugar) and
 * write-back (spans).
 */
internal object CnlParser {
    private val numberRegex = Regex("""-?\d+(\.\d+)?""")

    private data class Token(val text: String, val span: CnlSpan, val isText: Boolean, val terminated: Boolean)

    private fun isNumber(text: String): Boolean = numberRegex.matches(text)

    private fun isNumberLike(text: String): Boolean =
        isNumber(text) || text.startsWith("$") || isBinding(text)

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
        val allowedKinds = headingPrefixAllowedKinds(tokens.firstOrNull()?.text)
        // An untyped semantic name can contain a component/instance property keyword. For
        // example, `Media Library Header id media_header row` must keep "Library Header"
        // in the visible name instead of parsing `library Header` as instance metadata.
        // Use `id` as the boundary only for that ambiguous case; ordinary layout/style
        // properties before `id` remain valid and retain the earliest-boundary rule.
        val idBoundary = if (tokens.firstOrNull()?.text?.endsWith(":") != true) {
            tokens.indexOfFirst { it.text.equals("id", ignoreCase = true) }
                .takeIf { it >= 1 }
        } else {
            null
        }

        fun candidateAt(split: Int): Triple<Int, HeadingSplit, List<DirectPatchEntry>>? {
            val probe = DiagnosticCollector(diagnostics.fileName)
            val element = parseFrom(tokens, startIndex = split, noun = null, lineNumber = lineNumber, diagnostics = probe)
            val entries = if (element.properties.isNotEmpty() && probe.diagnostics.isEmpty()) {
                desugar(element, lineNumber, probe)
            } else {
                emptyList()
            }
            if (element.properties.isNotEmpty() && probe.diagnostics.isEmpty() && entriesAllowed(entries, allowedKinds)) {
                val name = content.substring(0, tokens[split].span.startColumn - baseColumn).trimEnd()
                return Triple(split, HeadingSplit(name, element), entries)
            }
            return null
        }

        // Name must be non-empty (split >= 1); the property suffix must be non-empty and fully clean.
        val ordinary = (1 until tokens.size).firstNotNullOfOrNull(::candidateAt) ?: return null
        val instanceMetadataKinds = setOf(TypedBlockKind.Component, TypedBlockKind.Props, TypedBlockKind.Overrides)
        val shouldPreferId = idBoundary != null && ordinary.first < idBoundary &&
            ordinary.third.any { entry -> entry.kind in instanceMetadataKinds }
        return if (shouldPreferId) candidateAt(idBoundary)?.second ?: ordinary.second else ordinary.second
    }

    private fun entriesAllowed(entries: List<DirectPatchEntry>, allowedKinds: Set<TypedBlockKind>?): Boolean =
        allowedKinds == null || entries.all { it.kind in allowedKinds }

    private val structuralHeadingKinds = setOf(
        TypedBlockKind.Node,
        TypedBlockKind.Layout,
        TypedBlockKind.Style,
        TypedBlockKind.Interaction,
        TypedBlockKind.Action,
        TypedBlockKind.Motion,
        TypedBlockKind.Responsive,
        TypedBlockKind.Export,
        TypedBlockKind.Handoff,
        // `mask` is not kind-gated in the emitter — any container can carry one — so a masked
        // frame/group heading suffix must be accepted rather than swallowed into the layer name.
        TypedBlockKind.Mask,
    )
    private val shapeHeadingKinds = structuralHeadingKinds + TypedBlockKind.Shape
    private val vectorHeadingKinds = structuralHeadingKinds + TypedBlockKind.Vector
    private val mediaHeadingKinds = structuralHeadingKinds + setOf(TypedBlockKind.Media)
    private val textHeadingKinds = structuralHeadingKinds + setOf(TypedBlockKind.Text)
    private val componentHeadingKinds = structuralHeadingKinds + setOf(TypedBlockKind.Component)
    private val instanceHeadingKinds =
        structuralHeadingKinds + setOf(TypedBlockKind.Component, TypedBlockKind.Props, TypedBlockKind.Overrides)

    private fun headingPrefixAllowedKinds(firstToken: String?): Set<TypedBlockKind>? {
        val prefix = firstToken
            ?.takeIf { it.endsWith(":") }
            ?.dropLast(1)
            ?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) }
            ?.lowercase()
            ?: return null
        if (prefix == "component") return componentHeadingKinds
        if (prefix == "shape") return shapeHeadingKinds
        val noun = CnlVocabulary.nouns[prefix] ?: return null
        return when (noun.nodeType) {
            "shape" -> shapeHeadingKinds
            "vector" -> vectorHeadingKinds
            "media" -> mediaHeadingKinds
            "text" -> textHeadingKinds
            "instance" -> instanceHeadingKinds
            else -> structuralHeadingKinds
        }
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
                val scan = CnlGrammar.scanTextLiteral(line, i + 1, close)
                val j = scan.closeIndex
                // Span covers the inner content, so SetText write-back replaces just the text
                // (writers re-escape via CnlGrammar.escapeText before writing into the span).
                tokens += Token(
                    text = scan.text,
                    span = CnlSpan(lineNumber, baseColumn + i + 1, baseColumn + j),
                    isText = true,
                    terminated = scan.terminated,
                )
                i = if (scan.terminated) j + 1 else j
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
            CnlPropertyKind.Visible -> consumeBindableBoolean(CnlPropertyKind.Visible, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Locked -> consumeBoolean(CnlPropertyKind.Locked, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.VariableModes -> consumePairMap(kind, tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.FontFamily, CnlPropertyKind.LineHeight, CnlPropertyKind.Tracking,
            CnlPropertyKind.TextAlign, CnlPropertyKind.TextValign, CnlPropertyKind.TextCase,
            CnlPropertyKind.TextDecoration, CnlPropertyKind.TextKey, CnlPropertyKind.TextStyleRef,
            CnlPropertyKind.AutoSize, CnlPropertyKind.Characters,
            -> consumeToken(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.Features, CnlPropertyKind.Axes ->
                consumeTypographyMap(kind, tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.ListSettings ->
                consumeListSettings(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Link -> consumeLink(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Span -> consumeSpan(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
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
            CnlPropertyKind.Wrap, CnlPropertyKind.Clip, CnlPropertyKind.Absolute, CnlPropertyKind.AutoLayout,
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
            CnlPropertyKind.SetOverride -> consumeSetOverride(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.NestedOverride -> consumeNested(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.ComponentName ->
                consumeToken(CnlPropertyKind.ComponentName, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.ComponentSet ->
                consumeToken(CnlPropertyKind.ComponentSet, tokens, valueStart, ::add, lineNumber, diagnostics)
            CnlPropertyKind.ComponentAxis ->
                consumeComponentAxis(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.ComponentPropDefinition ->
                consumeComponentPropDefinition(tokens, valueStart, keywordSpan, properties, lineNumber, diagnostics)
            CnlPropertyKind.Smoothing, CnlPropertyKind.ParagraphSpacing,
            CnlPropertyKind.MaxLines, CnlPropertyKind.Truncate, CnlPropertyKind.FontWeight,
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
            CnlPropertyKind.Id, CnlPropertyKind.NodeName ->
                consumeToken(kind, tokens, valueStart, ::add, lineNumber, diagnostics)
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
            else -> valueStart // Direction never reaches here (no keyword+value).
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

    private fun consumeBoolean(
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
        val flag = CnlVocabulary.booleans[value.text.lowercase()]
        if (flag == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.UnknownKeyword, lineNumber, "\"${value.text}\" is not a boolean")
            return start
        }
        add(kind, listOf(CnlValue(flag.toString(), value.span)), start)
        return start + 1
    }

    private fun consumeBindableBoolean(
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
        val raw = CnlVocabulary.booleans[value.text.lowercase()]?.toString()
            ?: value.text.takeIf { it.startsWith("$") || isBinding(it) }
        if (raw == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.UnknownKeyword, lineNumber, "\"${value.text}\" is not a boolean or binding")
            return start
        }
        add(kind, listOf(CnlValue(raw, value.span)), start)
        return start + 1
    }

    private fun consumePairMap(
        kind: CnlPropertyKind,
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Expected a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val leaves = group.children.mapNotNull { it.leafText() }
        val parts = mutableListOf<String>()
        val pairs = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i + 1 < leaves.size) {
            parts += "${leaves[i]}: ${leaves[i + 1]}"
            pairs += leaves[i] to leaves[i + 1]
            i += 2
        }
        if (i < leaves.size) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"${leaves[i]}\" has no value in the ( … ) pair list")
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            kind,
            listOf(CnlValue("{ ${parts.joinToString(", ")} }", span)),
            keywordSpan,
            span,
            payload = CnlPairsPayload(pairs),
        )
        return after
    }

    /**
     * A fill phrase led by `color`/`fill` (bare solid or `( … )` solid-with-props) or by
     * `gradient`/`image`/`video` (always a `( … )` group). Bare solids keep their color token +
     * span for surgical write-back ([CnlPropertyKind.Fill]); group forms carry the typed paint
     * as a [CnlPaintPayload] ([CnlPropertyKind.FillComplex]).
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

        fun addComplex(paint: DesignPaint?, lastIdx: Int) {
            val span = joinSpan(keywordSpan, tokens[lastIdx].span)
            properties += CnlProperty(
                CnlPropertyKind.FillComplex,
                listOf(CnlValue(tokens[keywordStart].text, span)),
                keywordSpan,
                span,
                payload = CnlPaintPayload(paint),
            )
        }

        if (keyword == "gradient" || keyword == "image" || keyword == "video") {
            if (next?.text != "(") {
                CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"$keyword\" needs a ( … ) group")
                return valueStart
            }
            val (group, after) = parseGroup(tokens, valueStart)
            val paint = when (keyword) {
                "gradient" -> gradientPaintOf(group)
                "image" -> mediaPaintOf(group, video = false)
                else -> mediaPaintOf(group, video = true)
            }
            addComplex(paint, after - 1)
            return after
        }

        // `color`/`fill` with a `( … )` group → solid-with-props.
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            addComplex(solidPaintOf(group), after - 1)
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

    private val fillModeWords = setOf("fill", "fit", "crop", "tile", "stretch")

    // --- typed paint constructors (direct desugar; the semantic source of truth) ---

    private val gradientKindEnums = mapOf(
        "linear" to GradientKind.Linear, "radial" to GradientKind.Radial,
        "angular" to GradientKind.Angular, "diamond" to GradientKind.Diamond,
    )

    /** Shared opacity/blend/visible paint props, coerced like `StyleBlockReader.readPaint`. */
    private class PaintPropsTyped {
        var opacity: Bindable<Double>? = null
        var blendMode: String? = null
        var visible: Bindable<Boolean>? = null

        fun apply(sub: String, value: String?): Boolean = when (sub) {
            "opacity" -> { opacity = value?.let { CnlScalars.bindableDoubleOf(it) }; true }
            "blend" -> { blendMode = value?.let { CnlScalars.stringOf(it) }; true }
            "visible" -> { visible = value?.let { CnlScalars.bindableBooleanOf(it) }; true }
            else -> false
        }

        val opacityOrDefault: Bindable<Double> get() = opacity ?: 1.0.bindable()
        val blendOrDefault: String get() = blendMode ?: "normal"
        val visibleOrDefault: Bindable<Boolean> get() = visible ?: true.bindable()
    }

    /** A `color`/`fill` ( … ) group (mirrors the reader's map-paint branch); null when the color is missing/invalid. */
    private fun solidPaintOf(group: GGroup): DesignPaint? {
        val children = group.children
        var color: String? = null
        val props = PaintPropsTyped()
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
        val bound = color?.let { CnlScalars.colorOf(it) } ?: return null
        return DesignPaint.Solid(bound, props.visibleOrDefault, props.opacityOrDefault, props.blendOrDefault)
    }

    /** A `gradient ( … )` group (mirrors the reader's gradient branch). */
    private fun gradientPaintOf(group: GGroup): DesignPaint {
        val children = group.children
        var kind = GradientKind.Linear
        var from: DesignPoint? = null
        var to: DesignPoint? = null
        val stops = mutableListOf<GradientStop>()
        val props = PaintPropsTyped()
        var i = 0
        children.getOrNull(0)?.leafText()?.lowercase()?.let { word ->
            gradientKindEnums[word]?.let { kind = it; i = 1 }
        }
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "from" -> { from = literalPointOf(children.getOrNull(i + 1)); i += 2 }
                text == "to" -> { to = literalPointOf(children.getOrNull(i + 1)); i += 2 }
                text == "stops" -> {
                    i += 1
                    while (i < children.size) {
                        val stop = children[i].asGroup() ?: break
                        stopOf(stop)?.let { stops += it }
                        i += 1
                    }
                }
                text != null && props.apply(text, children.getOrNull(i + 1)?.leafText()) -> i += 2
                else -> i += 1
            }
        }
        return DesignPaint.Gradient(
            gradientType = kind,
            from = from ?: DesignPoint(0.0, 0.0),
            to = to ?: DesignPoint(0.0, 1.0),
            stops = stops,
            visible = props.visibleOrDefault,
            opacity = props.opacityOrDefault,
            blendMode = props.blendOrDefault,
        )
    }

    /** An `image`/`video` ( … ) fill group (mirrors the reader's image/video branches). */
    private fun mediaPaintOf(group: GGroup, video: Boolean): DesignPaint {
        val children = group.children
        var asset: String? = null
        var poster: String? = null
        var fillMode: String? = null
        var focal: GNode? = null
        var autoplay = false
        var loop = false
        var replaceable = false
        var muted: Boolean? = null
        val props = PaintPropsTyped()
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "asset" -> { asset = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "poster" -> { poster = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "focus" || text == "focal" -> { focal = children.getOrNull(i + 1); i += 2 }
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
        val scaleMode = fillMode?.let { ReaderEnums.fillMode[it] } ?: ImageScaleMode.Fill
        val focalPoint = bindablePointOf(focal)
        return if (video) {
            DesignPaint.Video(
                assetId = asset.orEmpty(),
                scaleMode = scaleMode,
                focalPoint = focalPoint,
                posterAssetId = poster.orEmpty(),
                autoplay = autoplay,
                loop = loop,
                muted = muted ?: true,
                visible = props.visibleOrDefault,
                opacity = props.opacityOrDefault,
                blendMode = props.blendOrDefault,
            )
        } else {
            DesignPaint.Image(
                assetId = asset.orEmpty(),
                scaleMode = scaleMode,
                focalPoint = focalPoint,
                replaceable = replaceable,
                visible = props.visibleOrDefault,
                opacity = props.opacityOrDefault,
                blendMode = props.blendOrDefault,
            )
        }
    }

    /** A `(x y)` point read as literals (gradient from/to; the reader drops refs to `0.0` there). */
    private fun literalPointOf(node: GNode?): DesignPoint? {
        val nums = node?.asGroup()?.children?.mapNotNull { it.leafText() } ?: return null
        if (nums.size < 2) return null
        return DesignPoint(CnlScalars.doubleOf(nums[0]) ?: 0.0, CnlScalars.doubleOf(nums[1]) ?: 0.0)
    }

    /** A `(x y)` focal point kept bindable (mirrors `readFocalPoint`'s map branch). */
    private fun bindablePointOf(node: GNode?): DesignPoint? {
        val nums = node?.asGroup()?.children?.mapNotNull { it.leafText() } ?: return null
        if (nums.size < 2) return null
        return DesignPoint(
            x = CnlScalars.bindableDoubleOf(nums[0]) ?: 0.0.bindable(),
            y = CnlScalars.bindableDoubleOf(nums[1]) ?: 0.0.bindable(),
        )
    }

    /** A gradient stop group (mirrors `readStops`: position/color required, otherwise dropped). */
    private fun stopOf(group: GGroup): GradientStop? {
        val leaves = group.children.mapNotNull { it.leafText() }
        val colorToken = leaves.firstOrNull() ?: "#000000"
        val positionToken = leaves.lastOrNull { it != "at" } ?: "0"
        val position = CnlScalars.doubleOf(positionToken) ?: return null
        val color = CnlScalars.colorOf(colorToken) ?: return null
        return GradientStop(position, color)
    }

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
            val nums = group.children.mapNotNull { it.leafText() }.filter { isNumberLike(it) }
            if (nums.size < 4) {
                CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Per-corner radius needs 4 numbers (tl tr br bl)")
                return after
            }
            val fragment = "{ topLeft: ${nums[0]}, topRight: ${nums[1]}, bottomRight: ${nums[2]}, bottomLeft: ${nums[3]} }"
            val span = joinSpan(tokens[valueStart].span, tokens[after - 1].span)
            properties += CnlProperty(
                CnlPropertyKind.Radius,
                listOf(CnlValue(fragment, span)),
                keywordSpan,
                joinSpan(keywordSpan, span),
                payload = CnlRadiusPayload(
                    DesignCornerRadius(
                        topLeft = CnlScalars.bindableDoubleOf(nums[0]) ?: 0.0.bindable(),
                        topRight = CnlScalars.bindableDoubleOf(nums[1]) ?: 0.0.bindable(),
                        bottomRight = CnlScalars.bindableDoubleOf(nums[2]) ?: 0.0.bindable(),
                        bottomLeft = CnlScalars.bindableDoubleOf(nums[3]) ?: 0.0.bindable(),
                    ),
                ),
            )
            return after
        }
        if (next == null || !isNumberLike(next.text)) {
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
        val pairs = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < children.size) {
            val readerKey = styleRefKeys[children[i].leafText()?.lowercase()]
            val value = children.getOrNull(i + 1)?.leafText()
            if (readerKey != null && value != null) {
                refs += CnlValue("$readerKey: $value", keywordSpan)
                pairs += readerKey to value
                i += 2
            } else {
                i += 1
            }
        }
        if (refs.isNotEmpty()) {
            properties += CnlProperty(
                CnlPropertyKind.StyleRefs,
                refs,
                keywordSpan,
                joinSpan(keywordSpan, tokens[after - 1].span),
                payload = CnlPairsPayload(pairs),
            )
        }
        return after
    }

    private val styleRefKeys = mapOf(
        "fill" to "fillStyle", "stroke" to "strokeStyle", "text" to "textStyle",
        "effect" to "effectStyle", "grid" to "gridStyle",
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
        val pairs = mutableListOf<Pair<String, String>>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val leaves = group.children.mapNotNull { it.leafText() }
            val key = leaves.getOrNull(0)
            val raw = leaves.getOrNull(1)
            if (key != null && raw != null) {
                // Features: the boolean-word TABLE lookup (`on`/`off`/`yes`…) → "true"/"false".
                val value = if (kind == CnlPropertyKind.Features) {
                    CnlVocabulary.booleans[raw.lowercase()]?.toString() ?: raw
                } else {
                    raw
                }
                entries += "$key: $value"
                pairs += key to value
            }
            i = after
        }
        if (entries.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Expected ( key value ) groups")
            return valueStart
        }
        val payload = if (kind == CnlPropertyKind.Features) {
            // Mirrors TextBlockReader.booleanEntries: only boolean-typed values survive.
            CnlFeaturesPayload(
                pairs.mapNotNull { (key, value) ->
                    when (value) {
                        "true" -> key to true
                        "false" -> key to false
                        else -> null
                    }
                }.toMap(),
            )
        } else {
            // Mirrors TextBlockReader.axisEntries: number values only, friendly names → axis tags.
            CnlAxesPayload(
                pairs.mapNotNull { (key, value) ->
                    CnlScalars.doubleOf(value)?.let { number -> (variableAxisTags[key] ?: key) to number }
                }.toMap(),
            )
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(kind, listOf(CnlValue("{ ${entries.joinToString(", ")} }", span)), keywordSpan, span, payload)
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
            payload = CnlListSettingsPayload(
                TextListSettings(
                    type = ReaderEnums.textListType[type] ?: TextListType.None,
                    indent = CnlScalars.intOf(indent) ?: 0,
                ),
            ),
        )
        return after
    }

    /** Friendly variable-font axis names → registered tags (mirrors TextBlockReader.axisEntries). */
    private val variableAxisTags = mapOf(
        "weight" to "wght", "opticalSize" to "opsz", "width" to "wdth", "slant" to "slnt", "italic" to "ital",
    )

    /** Mirrors LayoutBlockReader's private `guideOrientations` table. */
    private val guideOrientations = mapOf(
        "horizontal" to GuideOrientation.Horizontal,
        "vertical" to GuideOrientation.Vertical,
    )

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
        fun add(value: String, sizing: SizingPatch, span: CnlSpan, end: Int): Int {
            properties += CnlProperty(
                kind,
                listOf(CnlValue(value, span)),
                keywordSpan,
                joinSpan(keywordSpan, span),
                payload = CnlSizingPayload(sizing),
            )
            return end
        }
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            return add(
                group.children.firstOrNull()?.leafText() ?: "fixed",
                sizingRecordOf(group),
                joinSpan(tokens[valueStart].span, tokens[after - 1].span),
                after,
            )
        }
        if (next != null && next.text.lowercase() in sizingModeWords) {
            val mode = next.text.lowercase()
            return add(mode, SizingPatch(mode = ReaderEnums.sizingMode[mode]), next.span, valueStart + 1)
        }
        if (next == null || !isNumber(next.text)) {
            CnlDiagnostics.warn(diagnostics, CnlRule.BadNumber, lineNumber, "Size axis needs a number, `fill`, `hug` or a ( … ) record")
            return valueStart
        }
        return add(
            "{ type: fixed, value: ${next.text} }",
            SizingPatch(mode = SizingMode.Fixed, value = CnlScalars.doubleOf(next.text)),
            next.span,
            valueStart + 1,
        )
    }

    /** A `( <mode> [N] [min N] [max N] )` sizing record (mirrors `readSizingAxis`'s map branch). */
    private fun sizingRecordOf(group: GGroup): SizingPatch {
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
        return SizingPatch(
            mode = ReaderEnums.sizingMode[mode],
            value = value?.let { CnlScalars.doubleOf(it) },
            min = min?.let { CnlScalars.doubleOf(it) },
            max = max?.let { CnlScalars.doubleOf(it) },
        )
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
        fun add(value: String, span: CnlSpan, end: Int, payload: CnlPayload? = null): Int {
            properties += CnlProperty(
                CnlPropertyKind.Gap,
                listOf(CnlValue(value, span)),
                keywordSpan,
                joinSpan(keywordSpan, span),
                payload = payload,
            )
            return end
        }
        if (next?.text?.lowercase() == "auto") return add("auto", next.span, valueStart + 1)
        if (next?.text == "(") {
            val (group, after) = parseGroup(tokens, valueStart)
            val leaves = group.children.mapNotNull { it.leafText() }
            var rowToken: String? = null
            var columnToken: String? = null
            val entries = buildList {
                val row = leaves.indexOf("row")
                if (row >= 0) leaves.getOrNull(row + 1)?.let { add("row: $it"); rowToken = it }
                val col = leaves.indexOf("column")
                if (col >= 0) leaves.getOrNull(col + 1)?.let { add("column: $it"); columnToken = it }
            }
            return add(
                "{ ${entries.joinToString(", ")} }",
                joinSpan(tokens[valueStart].span, tokens[after - 1].span),
                after,
                payload = CnlGapPayload(
                    row = rowToken?.let { CnlScalars.bindableDoubleOf(it) },
                    column = columnToken?.let { CnlScalars.bindableDoubleOf(it) },
                ),
            )
        }
        if (next == null || !isNumberLike(next.text)) {
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
        val pairs = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < leaves.size - 1) {
            if (leaves[i] in anchorSides) {
                entries += "${leaves[i]}: ${leaves[i + 1]}"
                pairs += leaves[i] to leaves[i + 1]
                i += 2
            } else {
                i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Anchor,
            entries.map { CnlValue(it, span) },
            keywordSpan,
            span,
            payload = CnlPairsPayload(pairs),
        )
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
        val pairs = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < leaves.size - 1) {
            when (leaves[i].lowercase()) {
                "horizontal" -> { entries += "horizontal: ${leaves[i + 1]}"; pairs += "horizontal" to leaves[i + 1]; i += 2 }
                "vertical" -> { entries += "vertical: ${leaves[i + 1]}"; pairs += "vertical" to leaves[i + 1]; i += 2 }
                else -> i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Constraints,
            entries.map { CnlValue(it, span) },
            keywordSpan,
            span,
            payload = CnlPairsPayload(pairs),
        )
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
        val pairs = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < leaves.size - 1) {
            when (leaves[i].lowercase()) {
                "inline" -> { entries += "inline: ${leaves[i + 1]}"; pairs += "inline" to leaves[i + 1]; i += 2 }
                "block" -> { entries += "block: ${leaves[i + 1]}"; pairs += "block" to leaves[i + 1]; i += 2 }
                "baseline" -> { entries += "baseline: ${leaves[i + 1]}"; pairs += "baseline" to leaves[i + 1]; i += 2 }
                else -> i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        val fragment = "{ ${entries.joinToString(", ")} }"
        properties += CnlProperty(
            CnlPropertyKind.ContainerAlign,
            listOf(CnlValue(fragment, span)),
            keywordSpan,
            span,
            payload = CnlPairsPayload(pairs),
        )
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
        val typed = linkSpanOf(group)
        if (typed == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"link\" needs range (s e) and url «href» or to <id>")
            return after
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Link,
            listOf(CnlValue("link", span)),
            keywordSpan,
            span,
            payload = CnlSpanPayload(typed),
        )
        return after
    }

    /** A `link ( … )` span (mirrors `TextBlockReader.readSpan`'s link branch). */
    private fun linkSpanOf(group: GGroup): TextSpanPatch? {
        val children = group.children
        var range: Pair<Int, Int>? = null
        var linkUrl: String? = null
        var linkNodeTarget: String? = null
        var hasLink = false
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "range" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children
                        ?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
                    if (nums != null && nums.size >= 2) range = nums[0].toDouble().toInt() to nums[1].toDouble().toInt()
                    i += 2
                }
                "url" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { linkUrl = it; linkNodeTarget = null; hasLink = true }
                    i += 2
                }
                "to" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { linkNodeTarget = CnlScalars.stringOf(it); linkUrl = null; hasLink = true }
                    i += 2
                }
                else -> i += 1
            }
        }
        val bounds = range ?: return null
        if (!hasLink) return null
        return TextSpanPatch(start = bounds.first, end = bounds.second, linkUrl = linkUrl, linkNodeTarget = linkNodeTarget)
    }

    /**
     * A rich-text range. Besides the legacy `style <ref>` form, editor-authored spans may carry
     * inline `font` / `weight` / `italic` / `size` and a `fills ( … )` paint list. Keeping these
     * values in CNL lets range formatting survive the reducer's source round-trip contract.
     */
    private fun consumeSpan(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        if (tokens.getOrNull(valueStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"span\" needs a ( … ) group")
            return valueStart
        }
        val (group, after) = parseGroup(tokens, valueStart)
        val typed = styleSpanOf(group)
        if (typed == null) {
            CnlDiagnostics.warn(
                diagnostics,
                CnlRule.MissingValue,
                lineNumber,
                "\"span\" needs range (s e) and a style, inline typography or fills",
            )
            return after
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Span,
            listOf(CnlValue("span", span)),
            keywordSpan,
            span,
            payload = CnlSpanPayload(typed),
        )
        return after
    }

    /** Parses a `span ( … )` rich-text range, including editor-authored inline overrides. */
    private fun styleSpanOf(group: GGroup): TextSpanPatch? {
        val children = group.children
        var range: Pair<Int, Int>? = null
        var styleRef: String? = null
        var fontFamily: String? = null
        var fontWeight: Bindable<Double>? = null
        var italic: Boolean? = null
        var fontSize: Bindable<Double>? = null
        var fills: List<DesignPaint>? = null
        var hasPayload = false
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "range" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children
                        ?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
                    if (nums != null && nums.size >= 2) range = nums[0].toDouble().toInt() to nums[1].toDouble().toInt()
                    i += 2
                }
                "style" -> {
                    children.getOrNull(i + 1)?.leafText()?.let {
                        styleRef = CnlScalars.stringOf(it)
                        hasPayload = true
                    }
                    i += 2
                }
                "font" -> {
                    children.getOrNull(i + 1)?.leafText()?.let {
                        fontFamily = it
                        hasPayload = true
                    }
                    i += 2
                }
                "weight" -> {
                    children.getOrNull(i + 1)?.leafText()?.let {
                        fontWeight = CnlScalars.bindableDoubleOf(it)
                        hasPayload = fontWeight != null || hasPayload
                    }
                    i += 2
                }
                "italic" -> {
                    children.getOrNull(i + 1)?.leafText()?.let {
                        italic = CnlVocabulary.booleans[it.lowercase()]
                        hasPayload = italic != null || hasPayload
                    }
                    i += 2
                }
                "size" -> {
                    children.getOrNull(i + 1)?.leafText()?.let {
                        fontSize = CnlScalars.bindableDoubleOf(it)
                        hasPayload = fontSize != null || hasPayload
                    }
                    i += 2
                }
                "fills" -> {
                    val value = children.getOrNull(i + 1)
                    fills = when {
                        value?.asGroup() != null -> spanFillsOf(value.asGroup()!!)
                        value?.leafText()?.lowercase() == "none" -> emptyList()
                        else -> null
                    }
                    hasPayload = fills != null || hasPayload
                    i += 2
                }
                else -> i += 1
            }
        }
        val bounds = range ?: return null
        if (!hasPayload) return null
        val inlineStyle = DesignTextStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            italic = italic,
            fontSize = fontSize,
        ).takeIf { it != DesignTextStyle() }
        return TextSpanPatch(
            start = bounds.first,
            end = bounds.second,
            styleRef = styleRef,
            style = inlineStyle,
            fills = fills,
        )
    }

    /** Paint phrases inside `fills ( … )`; syntax matches the node-level fill grammar. */
    private fun spanFillsOf(group: GGroup): List<DesignPaint> {
        val children = group.children
        val paints = mutableListOf<DesignPaint>()
        var i = 0
        while (i < children.size) {
            val word = children[i].leafText()?.lowercase()
            val value = children.getOrNull(i + 1)
            val paintGroup = value?.asGroup()
            when {
                (word == "color" || word == "fill") && paintGroup != null -> {
                    solidPaintOf(paintGroup)?.let { paints += it }
                    i += 2
                }
                word == "color" || word == "fill" -> {
                    value?.leafText()?.let { token -> solidPaintOfToken(token)?.let { paints += it } }
                    i += 2
                }
                word == "gradient" && paintGroup != null -> {
                    paints += gradientPaintOf(paintGroup)
                    i += 2
                }
                word == "image" && paintGroup != null -> {
                    paints += mediaPaintOf(paintGroup, video = false)
                    i += 2
                }
                word == "video" && paintGroup != null -> {
                    paints += mediaPaintOf(paintGroup, video = true)
                    i += 2
                }
                else -> i += 1
            }
        }
        return paints
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
        val pairs = mutableListOf<Pair<String, String>>()
        val entries = buildList {
            val x = leaves.indexOf("x"); if (x >= 0) leaves.getOrNull(x + 1)?.let { add("x: $it"); pairs += "x" to it }
            val y = leaves.indexOf("y"); if (y >= 0) leaves.getOrNull(y + 1)?.let { add("y: $it"); pairs += "y" to it }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Overflow,
            listOf(CnlValue("{ ${entries.joinToString(", ")} }", span)),
            keywordSpan,
            span,
            payload = CnlPairsPayload(pairs),
        )
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
        var fixedIds: List<String>? = null
        var sticky = false
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "direction" -> { direction = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "fixedchildren" -> {
                    val ids = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    fixed = ids?.joinToString(", ")?.let { "[ $it ]" }
                    fixedIds = ids
                    i += 2
                }
                "sticky" -> { sticky = true; i += 1 }
                else -> i += 1
            }
        }
        val parts = buildList {
            direction?.let { add("direction: $it") }
            fixed?.let { add("fixedChildren: $it") }
            if (sticky) add("sticky: true")
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Scroll,
            listOf(CnlValue("{ ${parts.joinToString(", ")} }", span)),
            keywordSpan,
            span,
            payload = CnlScrollPayload(
                direction = direction?.let { ReaderEnums.scrollDirection[it] },
                sticky = sticky,
                fixedChildren = fixedIds?.mapNotNull { CnlScalars.plainStringOf(it) },
            ),
        )
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
        properties += CnlProperty(
            kind,
            listOf(CnlValue(keyword, span)),
            keywordSpan,
            span,
            payload = trackAxisOf(group),
        )
        return after
    }

    /** A `columns`/`rows` ( … ) group (mirrors `LayoutBlockReader.readTrackAxis`). */
    private fun trackAxisOf(group: GGroup): CnlTrackAxisPayload {
        val children = group.children
        var countToken: String? = null
        var trackToken: String? = null
        var tracksTokens: List<String>? = null
        var gapToken: String? = null
        var minToken: String? = null
        var auto = false
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "count" -> { countToken = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "track" -> { trackToken = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "tracks" -> {
                    tracksTokens = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    i += 2
                }
                "gap" -> { gapToken = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "min" -> { minToken = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "auto" -> { auto = true; i += 1 }
                else -> i += 1
            }
        }
        val count = countToken?.let { CnlScalars.intOf(it) }
        val track = trackToken?.let { CnlScalars.gridTrackOf(it) }
        val trackList = tracksTokens?.mapNotNull { CnlScalars.gridTrackOf(it) }
        val tracks = when {
            trackList != null -> trackList
            count != null && count > 0 -> List(count) { track ?: GridTrack.Flex(1.0.bindable()) }
            track != null && !auto -> listOf(track)
            else -> null
        }
        return CnlTrackAxisPayload(
            tracks = tracks,
            gap = gapToken?.let { CnlScalars.bindableDoubleOf(it) },
            implicitTrack = if (auto) track ?: GridTrack.Flex(1.0.bindable()) else null,
            min = minToken?.let { CnlScalars.bindableDoubleOf(it) },
        )
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
        var column: String? = null
        var row: String? = null
        var columnSpan: String? = null
        var rowSpan: String? = null
        var i = 0
        while (i < children.size - 1) {
            when (children[i].leafText()?.lowercase()) {
                "column" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "column: $it"; column = it }; i += 2 }
                "row" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "row: $it"; row = it }; i += 2 }
                "columnspan" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "columnSpan: $it"; columnSpan = it }; i += 2 }
                "rowspan" -> { children.getOrNull(i + 1)?.leafText()?.let { entries += "rowSpan: $it"; rowSpan = it }; i += 2 }
                else -> i += 1
            }
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Place,
            listOf(CnlValue("{ ${entries.joinToString(", ")} }", span)),
            keywordSpan,
            span,
            payload = CnlPlacePayload(
                column = column?.let { CnlScalars.intOf(it) },
                row = row?.let { CnlScalars.intOf(it) },
                columnSpan = columnSpan?.let { CnlScalars.intOf(it) },
                rowSpan = rowSpan?.let { CnlScalars.intOf(it) },
            ),
        )
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
        val guides = mutableListOf<GuideLine>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val leaves = group.children.mapNotNull { it.leafText() }
            val orientation = leaves.getOrNull(0)
            val position = leaves.getOrNull(1)
            if (orientation != null && position != null) {
                items += "{ orientation: $orientation, position: $position }"
                val typedOrientation = guideOrientations[orientation]
                val typedPosition = CnlScalars.doubleOf(position)
                if (typedOrientation != null && typedPosition != null) {
                    guides += GuideLine(typedOrientation, typedPosition)
                }
            }
            i = after
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Guides,
            listOf(CnlValue("[ ${items.joinToString(", ")} ]", span)),
            keywordSpan,
            span,
            payload = CnlGuidesPayload(guides),
        )
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
        val grids = mutableListOf<LayoutGridDefinition>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            gridDefinitionOf(group)?.let { grids += it }
            i = after
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Grids,
            listOf(CnlValue("grids", span)),
            keywordSpan,
            span,
            payload = CnlGridsPayload(grids),
        )
        return i
    }

    /** A `( <type> [count N] … )` grid overlay item (mirrors `LayoutBlockReader.readGrids`; typeless items dropped). */
    private fun gridDefinitionOf(group: GGroup): LayoutGridDefinition? {
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
        val gridType = ReaderEnums.gridType[type] ?: return null
        return LayoutGridDefinition(
            type = gridType,
            count = count?.let { CnlScalars.bindableIntOf(it) },
            size = size?.let { CnlScalars.bindableDoubleOf(it) },
            gutter = gutter?.let { CnlScalars.bindableDoubleOf(it) },
            margin = margin?.let { CnlScalars.bindableDoubleOf(it) },
            alignment = alignment?.let { ReaderEnums.gridAlignment[it] } ?: LayoutGridAlignment.Stretch,
            color = color?.let { CnlScalars.colorOf(it) }?.let { (it as? Bindable.Value)?.value },
            visible = visible?.let { it == "true" } ?: true,
        )
    }

    // ============ P6 consumers — media node / shape params / vector paths·networks / boolean / mask.

    /** `media ( asset <id> [video|image] [fill|fit|crop|tile|stretch] [focus center|(x y)] [alt «…»]
     *  [opacity n|$ref] [blend <mode>] [poster <id>] [autoplay] [loop] [replaceable] [unmuted] )`
     *  → a typed [MediaPatch] carried as a [CnlMediaPayload]. */
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
        properties += CnlProperty(
            CnlPropertyKind.Media,
            listOf(CnlValue("media", span)),
            keywordSpan,
            span,
            payload = CnlMediaPayload(mediaPatchOf(group)),
        )
        return after
    }

    /** Typed twin of the retired YAML `media:` reader semantics. */
    private fun mediaPatchOf(group: GGroup): MediaPatch {
        val children = group.children
        var asset: String? = null
        var kind: String? = null      // only "video" survives; "image" is the reader default
        var fillMode: String? = null
        var focal: DesignPoint? = null
        var alt: String? = null
        var opacity: String? = null
        var blend: String? = null
        var poster: String? = null
        var autoplay = false
        var loop = false
        var replaceable = false
        var muted: Boolean? = null
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            when {
                text == "asset" -> { asset = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "poster" -> { poster = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "video" -> { kind = "video"; i += 1 }
                text == "image" -> { kind = "image"; i += 1 }
                text in fillModeWords -> { fillMode = text; i += 1 }
                text == "focus" || text == "focal" -> { focal = focalPointOf(children.getOrNull(i + 1)); i += 2 }
                text == "alt" -> { alt = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "opacity" -> { opacity = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "blend" -> { blend = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "autoplay" -> { autoplay = true; i += 1 }
                text == "loop" -> { loop = true; i += 1 }
                text == "replaceable" -> { replaceable = true; i += 1 }
                text == "unmuted" -> { muted = false; i += 1 }
                text == "muted" -> {
                    val flag = children.getOrNull(i + 1)?.leafText()?.let { CnlVocabulary.booleans[it.lowercase()] }
                    if (flag != null) { muted = flag; i += 2 } else { muted = true; i += 1 }
                }
                else -> i += 1
            }
        }
        return MediaPatch(
            asset = asset?.let { CnlScalars.bindableStringOf(it) },
            kind = kind?.let { ReaderEnums.mediaKind[it] },
            fillMode = fillMode?.let { ReaderEnums.fillMode[it] },
            focalPoint = focal,
            alt = alt?.let { TextContent(defaultText = it) },
            replaceable = if (replaceable) true else null,
            opacity = opacity?.let { CnlScalars.bindableDoubleOf(it) },
            blendMode = blend?.let { CnlScalars.stringOf(it) },
            poster = poster?.let { CnlScalars.bindableStringOf(it) },
            autoplay = if (autoplay) true else null,
            loop = if (loop) true else null,
            muted = muted,
        )
    }

    /** `focus center` → (0.5, 0.5); `focus (x y)` → a bindable point (mirrors `readFocalPoint`). */
    private fun focalPointOf(node: GNode?): DesignPoint? = when {
        node == null -> null
        node.leafText()?.lowercase() == "center" -> DesignPoint(0.5, 0.5)
        else -> bindablePointOf(node)
    }

    /** `viewbox (x y w h)` → a typed [DesignViewBox]. */
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
            listOf(CnlValue("viewbox", span)),
            keywordSpan, span,
            payload = CnlViewBoxPayload(
                DesignViewBox(nums[0].toDouble(), nums[1].toDouble(), nums[2].toDouble(), nums[3].toDouble()),
            ),
        )
        return after
    }

    /** One or more `path «d» [evenodd|nonzero]` → the typed `vector.paths` list.
     *  Greedily consumes consecutive `path` phrases so they compose into one list. */
    private fun consumeVectorPaths(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val items = mutableListOf<VectorPath>()
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
            items += VectorPath(windingRule = winding, d = dTok.text)
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
            listOf(CnlValue("paths", span)),
            keywordSpan, span,
            payload = CnlVectorPathsPayload(items),
        )
        return i
    }

    /** `network ( vertex (…) … segment (…) … region [evenodd] loops (…) … )` → a typed network. */
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
        properties += CnlProperty(
            CnlPropertyKind.VectorNetwork,
            listOf(CnlValue("network", span)),
            keywordSpan,
            span,
            payload = networkOf(group),
        )
        return after
    }

    /** Typed twin of the old network fragment + `ShapeVectorMaskReader.readNetwork`/`readRegionFills`. */
    private fun networkOf(group: GGroup): CnlNetworkPayload {
        val children = group.children
        val vertices = mutableListOf<VectorVertex>()
        val segments = mutableListOf<VectorSegment>()
        val regions = mutableListOf<VectorRegion>()
        val regionFills = LinkedHashMap<Int, List<DesignPaint>>()
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "vertex" -> {
                    children.getOrNull(i + 1)?.asGroup()?.let { g -> vertexOf(g)?.let { vertices += it } }
                    i += 2
                }
                "segment" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children
                        ?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
                    if (nums != null && nums.size >= 2) {
                        segments += VectorSegment(nums[0].toDouble().toInt(), nums[1].toDouble().toInt())
                    }
                    i += 2
                }
                "region" -> {
                    i += 1
                    var winding = "nonzero"
                    children.getOrNull(i)?.leafText()?.lowercase()?.let { w ->
                        if (w == "evenodd" || w == "nonzero") { winding = w; i += 1 }
                    }
                    if (children.getOrNull(i)?.leafText()?.lowercase() == "loops") i += 1
                    val loops = mutableListOf<List<Int>>()
                    while (i < children.size) {
                        val g = children[i].asGroup() ?: break
                        loops += g.children.mapNotNull { it.leafText() }
                            .filter { isNumber(it) }
                            .map { it.toDouble().toInt() }
                        i += 1
                    }
                    // Per-region solid/token fills: `fill #hex` / `fill $ref` / `fill token:ref`.
                    val fills = mutableListOf<DesignPaint>()
                    while (children.getOrNull(i)?.leafText()?.lowercase() == "fill") {
                        children.getOrNull(i + 1)?.leafText()?.let { token ->
                            regionPaintOf(token)?.let { fills += it }
                        }
                        i += 2
                    }
                    regions += VectorRegion(windingRule = winding, loops = loops)
                    if (fills.isNotEmpty()) regionFills[regions.size - 1] = fills
                }
                else -> i += 1
            }
        }
        val network = if (vertices.isEmpty()) null else VectorNetwork(vertices, segments, regions)
        return CnlNetworkPayload(network, regionFills)
    }

    /** A `vertex (x y …)` group (mirrors `readVertices`; a non-numeric coordinate drops the vertex). */
    private fun vertexOf(group: GGroup): VectorVertex? {
        val children = group.children
        val x = CnlScalars.doubleOf(children.getOrNull(0)?.leafText() ?: "0") ?: return null
        val y = CnlScalars.doubleOf(children.getOrNull(1)?.leafText() ?: "0") ?: return null
        var inH: HandleOffset? = null
        var outH: HandleOffset? = null
        var mirror: String? = null
        var corner = false
        var radius: String? = null
        var i = 2
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "in" -> { inH = offsetOf(children.getOrNull(i + 1)); i += 2 }
                "out" -> { outH = offsetOf(children.getOrNull(i + 1)); i += 2 }
                "mirror" -> { mirror = children.getOrNull(i + 1)?.leafText(); i += 2 }
                "corner" -> { corner = true; i += 1 }
                "radius" -> { radius = children.getOrNull(i + 1)?.leafText()?.takeIf { isNumber(it) }; i += 2 }
                else -> i += 1
            }
        }
        return VectorVertex(
            x = x,
            y = y,
            inHandle = inH,
            outHandle = outH,
            mirror = mirror?.takeIf { it.lowercase() != "none" }
                ?.let { ReaderEnums.handleMirror[it] } ?: HandleMirror.None,
            corner = corner,
            cornerRadius = radius?.toDouble() ?: 0.0,
        )
    }

    /** `(dx dy)` → a handle offset. */
    private fun offsetOf(node: GNode?): HandleOffset? {
        val nums = node?.asGroup()?.children?.mapNotNull { it.leafText() }?.filter { isNumber(it) }
        if (nums == null || nums.size < 2) return null
        return HandleOffset(nums[0].toDouble(), nums[1].toDouble())
    }

    /** A region-fill token → a solid paint: `#hex`, `token:ref`, `$ref` or `{{expr}}`; else dropped. */
    private fun regionPaintOf(token: String): DesignPaint? = when {
        token.startsWith("token:") -> DesignPaint.Solid(Bindable.VarRef(token.removePrefix("token:")))
        else -> CnlScalars.colorOf(token)?.let { DesignPaint.Solid(it) }
    }

    /** `mask <type> [clips ( id id )] [from <id>]` → a typed [MaskPatch]. */
    private fun consumeMask(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        var i = valueStart
        var type: String? = null
        var appliesTo: List<String>? = null
        var source: String? = null
        val typeTok = tokens.getOrNull(i)?.text?.lowercase()
        if (typeTok != null && typeTok in maskTypeWords) { type = typeTok; i += 1 }
        if (tokens.getOrNull(i)?.text?.lowercase() == "clips" && tokens.getOrNull(i + 1)?.text == "(") {
            val (group, after) = parseGroup(tokens, i + 1)
            val ids = group.children.mapNotNull { it.leafText() }
            if (ids.isNotEmpty()) appliesTo = ids
            i = after
        }
        if (tokens.getOrNull(i)?.text?.lowercase() == "from") {
            tokens.getOrNull(i + 1)?.let { source = it.text }
            i += 2
        }
        if (type == null && appliesTo == null && source == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"mask\" needs a type (alpha, vector or luminance)")
            return i
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Mask,
            listOf(CnlValue("mask", span)),
            keywordSpan,
            span,
            payload = CnlMaskPayload(
                MaskPatch(
                    type = type?.let { ReaderEnums.maskType[it] },
                    source = source,
                    // Bare-written ids: only tokens YAML types as strings survive (`stringList`).
                    appliesTo = appliesTo?.mapNotNull { CnlScalars.plainStringOf(it) },
                ),
            ),
        )
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
            listOf(CnlValue("variant", span)),
            keywordSpan,
            span,
            payload = CnlVariantSelectionPayload(variantSelectionOf(group)),
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
            listOf(CnlValue("props", span)),
            keywordSpan,
            span,
            payload = CnlPropsPayload(propsRecordOf(group)),
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
        var groupCount = 0
        val fills = mutableListOf<SlotOverridePatch>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            groupCount += 1
            slotFillOf(group)?.let { fills += it }
            i = after
        }
        if (groupCount == 0) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Slot \"${nameToken.text}\" needs ( … ) fills")
            return i
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.SlotOverride,
            listOf(CnlValue(nameToken.text, span)),
            keywordSpan,
            span,
            payload = CnlSlotPayload(nameToken.text, fills),
        )
        return i
    }

    /**
     * `override <id/path> ( <appearance phrases> )` → one `overrides.sets` entry. The inner
     * phrases are the ordinary node phrases (color/opacity/radius/stroke/visible/characters/
     * typography); they are re-parsed with the shared phrase loop into a nested [BlockBuilder]
     * whose typed `style`/`text`/`node` sub-patches form the [SetOverridePatch].
     */
    private fun consumeSetOverride(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val target = tokens.getOrNull(valueStart)
        if (target == null || target.text == "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"override\" needs a target and a ( … ) group")
            return valueStart
        }
        val groupStart = valueStart + 1
        if (tokens.getOrNull(groupStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Override \"${target.text}\" needs a ( … ) group")
            return groupStart
        }
        // The tokens strictly inside the ( … ) group, re-parsed as a standalone phrase sequence.
        val innerStart = groupStart + 1
        val closeIdx = matchingParen(tokens, groupStart)
        val inner = tokens.subList(innerStart, closeIdx)
        val builder = BlockBuilder()
        if (inner.isNotEmpty()) {
            val element = parseFrom(inner, startIndex = 0, noun = null, lineNumber = lineNumber, diagnostics = diagnostics)
            element.properties.forEach { applyProperty(builder, it) }
        }
        val targetSegments = target.text.split("/").filter { it.isNotEmpty() }
        val after = closeIdx + 1
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.SetOverride,
            listOf(CnlValue(target.text, span)),
            keywordSpan,
            span,
            payload = CnlSetOverridePayload(builder.setOverrideTyped(targetSegments)),
        )
        return after
    }

    /** Index of the `)` matching the `(` at [openIdx]; the last index when unbalanced. */
    private fun matchingParen(tokens: List<Token>, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < tokens.size) {
            when (tokens[i].text) {
                "(" -> depth++
                ")" -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return tokens.size - 1
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
        val record = instanceOverrideRecordOf(group)
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.NestedOverride,
            listOf(CnlValue(target.text, span)),
            keywordSpan,
            span,
            payload = CnlNestedPayload(
                target.text,
                NestedInstancePatch(variant = record.variant, props = record.props),
            ),
        )
        return after
    }

    /** `axis <name> (<value> …)` → one component definition variant axis. */
    private fun consumeComponentAxis(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val axis = tokens.getOrNull(valueStart)
        if (axis == null || axis.text == "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"axis\" needs a name and a ( … ) values group")
            return valueStart
        }
        val groupStart = valueStart + 1
        if (tokens.getOrNull(groupStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Axis \"${axis.text}\" needs a ( … ) values group")
            return groupStart
        }
        val (group, after) = parseGroup(tokens, groupStart)
        val values = group.children.mapNotNull { it.leafText() }
        if (values.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Axis \"${axis.text}\" needs at least one value")
            return after
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.ComponentAxis,
            listOf(CnlValue(axis.text, span)),
            keywordSpan,
            span,
            payload = CnlComponentAxisPayload(axis.text, values.mapNotNull { recordListItemOf(it) }),
        )
        return after
    }

    /** `prop <name> (<type> default … min N max N preferred (…) allow (…))` → one property definition. */
    private fun consumeComponentPropDefinition(
        tokens: List<Token>,
        valueStart: Int,
        keywordSpan: CnlSpan,
        properties: MutableList<CnlProperty>,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): Int {
        val name = tokens.getOrNull(valueStart)
        if (name == null || name.text == "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"prop\" needs a name and a ( … ) definition group")
            return valueStart
        }
        val groupStart = valueStart + 1
        if (tokens.getOrNull(groupStart)?.text != "(") {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "Prop \"${name.text}\" needs a ( … ) definition group")
            return groupStart
        }
        val (group, after) = parseGroup(tokens, groupStart)
        val definition = componentPropDefinitionOf(name.text, group, lineNumber, diagnostics) ?: return after
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.ComponentPropDefinition,
            listOf(CnlValue(name.text, span)),
            keywordSpan,
            span,
            payload = CnlComponentPropPayload(name.text, definition),
        )
        return after
    }

    /** Typed twin of the old prop-definition fragment + `ComponentBlockReader.readPropertyDefinitions`. */
    private fun componentPropDefinitionOf(
        name: String,
        group: GGroup,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): ComponentPropertyDefinition? {
        val children = group.children
        val typeWord = children.firstOrNull()?.leafText()?.lowercase()
        val typeKey = componentPropertyType(typeWord)
        if (typeKey == null) {
            CnlDiagnostics.warn(diagnostics, CnlRule.UnknownKeyword, lineNumber, "Prop \"$name\" needs a component property type")
            return null
        }
        var default: PropValue? = null
        var preferred: List<String> = emptyList()
        var minItems: Int? = null
        var maxItems: Int? = null
        var allowed: List<String> = emptyList()
        var i = 1
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "default" -> {
                    children.getOrNull(i + 1)?.let { value -> default = componentDefaultOf(typeKey, value) }
                    i += 2
                }
                "preferred", "preferredvalues" -> {
                    val values = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }.orEmpty()
                    if (values.isNotEmpty()) preferred = values.mapNotNull { recordListItemOf(it) }
                    i += 2
                }
                "min", "minitems" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { minItems = CnlScalars.intOf(it) }
                    i += 2
                }
                "max", "maxitems" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { maxItems = CnlScalars.intOf(it) }
                    i += 2
                }
                "allow", "allowed", "allowedcontent" -> {
                    val values = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }.orEmpty()
                    if (values.isNotEmpty()) allowed = values.mapNotNull { recordListItemOf(it) }
                    i += 2
                }
                else -> i += 1
            }
        }
        return ComponentPropertyDefinition(
            type = ReaderEnums.componentPropertyType.getValue(typeKey),
            default = default,
            preferredValues = preferred,
            minItems = minItems,
            maxItems = maxItems,
            allowedContent = allowed,
        )
    }

    private fun componentPropertyType(word: String?): String? = when (word) {
        "text" -> "text"
        "boolean", "bool" -> "boolean"
        "instance", "swap", "instanceswap" -> "instanceSwap"
        "variant" -> "variant"
        "slot" -> "slot"
        "number" -> "number"
        "string" -> "string"
        "data", "databinding" -> "dataBinding"
        else -> null
    }

    /** The declared default of a component property (mirrors the old fragment + `readPropValue`). */
    private fun componentDefaultOf(typeKey: String, value: GNode): PropValue {
        val text = value.leaf()?.token?.text.orEmpty()
        return when (typeKey) {
            "boolean" -> PropValue.Bool(CnlVocabulary.booleans[text.lowercase()] ?: false)
            "number" -> PropValue.Number(if (isNumber(text)) text.toDouble() else 0.0)
            "instanceSwap", "variant" -> PropValue.Reference(referenceValueOf(text))
            "dataBinding" -> PropValue.Data(DesignExpression(CnlScalars.expressionBodyOf(text) ?: text))
            else -> textPropOf(text)
        }
    }

    /** A slot fill group `( <instanceRef> [variant (…)] [props (…)] )`; null = no instance ref. */
    private fun slotFillOf(group: GGroup): SlotOverridePatch? {
        val children = group.children
        val instance = children.firstOrNull()?.leafText() ?: return null
        // plainScalar-written ref: a bare `null` word dies in the reader; numbers keep their text.
        val ref = instance.takeUnless { it == "null" } ?: return null
        val record = instanceOverrideRecordOf(GGroup(children.drop(1)))
        return SlotOverridePatch(
            instanceRef = ref,
            props = record.props ?: emptyMap(),
            variant = record.variant ?: emptyMap(),
        )
    }

    private class InstanceOverrideRecord(
        val variant: Map<String, String>?,
        val props: Map<String, PropValue>?,
    )

    /** The `variant`/`props` sub-records shared by nested overrides and slot fills. */
    private fun instanceOverrideRecordOf(group: GGroup): InstanceOverrideRecord {
        val children = group.children
        var variant: Map<String, String>? = null
        var props: Map<String, PropValue>? = null
        var i = 0
        while (i < children.size) {
            val kw = children[i].leafText()?.lowercase()
            val record = children.getOrNull(i + 1)?.asGroup()
            when {
                kw == "variant" && record != null -> { variant = variantSelectionOf(record); i += 2 }
                kw == "props" && record != null -> { props = propsRecordOf(record); i += 2 }
                else -> i += 1
            }
        }
        return InstanceOverrideRecord(variant, props)
    }

    /**
     * `( axis value … )` → axis→value selection map (mirrors the old plainScalar record +
     * `stringEntries`: a bare `null` value dies; numbers/booleans keep their source text).
     */
    private fun variantSelectionOf(group: GGroup): Map<String, String> {
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = LinkedHashMap<String, String>()
        var i = 0
        while (i + 1 < leaves.size) {
            leaves[i + 1].takeUnless { it == "null" }?.let { entries[leaves[i]] = it }
            i += 2
        }
        return entries
    }

    /** `( name value … )` → typed prop values; a value may be a scalar or a `( … )` record. */
    private fun propsRecordOf(group: GGroup): Map<String, PropValue> {
        val children = group.children
        val entries = LinkedHashMap<String, PropValue>()
        var i = 0
        while (i < children.size) {
            val name = children[i].leafText()
            val valueNode = children.getOrNull(i + 1)
            if (name == null || valueNode == null) {
                i += 1
                continue
            }
            val record = valueNode.asGroup()
            val value = if (record != null) propValueRecordOf(record) else propScalarOf(valueNode.leaf())
            value?.let { entries[name] = it }
            i += 2
        }
        return entries
    }

    /** A bare prop scalar: quoted text, number, boolean, `{{expr}}` binding, or a bare word (→ text). */
    private fun propScalarOf(leaf: GLeaf?): PropValue {
        val token = leaf?.token ?: return PropValue.Text("")
        val text = token.text
        return when {
            token.isText -> textPropOf(text)
            isNumber(text) -> PropValue.Number(text.toDouble())
            text.lowercase() in CnlVocabulary.booleans -> PropValue.Bool(CnlVocabulary.booleans.getValue(text.lowercase()))
            else -> textPropOf(text)
        }
    }

    /** A string prop value: `{{expr}}` → data binding, else literal text (mirrors `readPropValue`). */
    private fun textPropOf(text: String): PropValue =
        CnlScalars.expressionBodyOf(text)?.let { PropValue.Data(DesignExpression(it)) } ?: PropValue.Text(text)

    /** A prop value record: `(swap ref)`, `(text «…» [key k])`, or `(data expr)`; unknown → dropped. */
    private fun propValueRecordOf(group: GGroup): PropValue? {
        val children = group.children
        return when (children.firstOrNull()?.leafText()?.lowercase()) {
            "swap" -> PropValue.Reference(referenceValueOf(children.getOrNull(1)?.leafText().orEmpty()))
            "data" -> {
                val expr = children.getOrNull(1)?.leafText().orEmpty()
                PropValue.Data(DesignExpression(CnlScalars.expressionBodyOf(expr) ?: expr))
            }
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
                // plainScalar-written key: a bare `null` word reads back as no key.
                val i18nKey = key?.takeUnless { it == "null" }
                if (i18nKey != null) {
                    PropValue.Content(TextContent(key = i18nKey, defaultText = value))
                } else {
                    textPropOf(value)
                }
            }
            else -> null
        }
    }

    /** Is [value] safe as a bare YAML plain scalar (the old `plainScalar` bare set)? */
    private fun isPlainWord(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == '/' }

    /**
     * A plainScalar-written value read back through `stringOrNull().orEmpty()`: bare tokens the
     * YAML would type as non-strings (null/booleans/numbers) collapse to "".
     */
    private fun referenceValueOf(token: String): String =
        if (isPlainWord(token) &&
            (token == "null" || token == "true" || token == "false" || CnlScalars.doubleOf(token) != null)
        ) {
            ""
        } else {
            token
        }

    /**
     * A plainScalar-written list item read back through `stringList`: bare tokens the YAML types
     * as non-strings (null/booleans/numbers) are dropped.
     */
    private fun recordListItemOf(token: String): String? =
        if (isPlainWord(token) &&
            (token == "null" || token == "true" || token == "false" || CnlScalars.doubleOf(token) != null)
        ) {
            null
        } else {
            token
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
            val span = joinSpan(keywordSpan, tokens[after - 1].span)
            properties += CnlProperty(
                CnlPropertyKind.StrokeComplex,
                listOf(CnlValue("stroke", span)),
                keywordSpan,
                span,
                payload = strokesPayloadOf(group),
            )
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

    /** A bare stroke color token → solid paint (mirrors `readPaint` on `token:`/`color:` entries). */
    private fun strokeColorPaintOf(token: String): DesignPaint? =
        if (token.startsWith("$")) {
            DesignPaint.Solid(Bindable.VarRef(token.removePrefix("$")))
        } else {
            CnlScalars.colorOf(token)?.let { DesignPaint.Solid(it) }
        }

    /** `stroke ( … )` record (mirrors `StyleBlockReader.readStrokes` attribute hoisting). */
    private fun strokesPayloadOf(group: GGroup): CnlStrokesPayload {
        val children = group.children
        val paints = mutableListOf<DesignPaint>()
        var weight: String? = null
        var align: String? = null
        var dash: List<String>? = null
        var cap: String? = null
        var join: String? = null
        var perSide: List<String>? = null
        var i = 0
        while (i < children.size) {
            val text = children[i].leafText()?.lowercase()
            val paintGroup = children.getOrNull(i + 1)?.asGroup()
            when {
                text == "color" && paintGroup != null -> { solidPaintOf(paintGroup)?.let { paints += it }; i += 2 }
                text == "color" -> {
                    children.getOrNull(i + 1)?.leafText()?.let { token -> strokeColorPaintOf(token)?.let { paints += it } }
                    i += 2
                }
                text == "gradient" && paintGroup != null -> { paints += gradientPaintOf(paintGroup); i += 2 }
                text == "image" && paintGroup != null -> { paints += mediaPaintOf(paintGroup, video = false); i += 2 }
                text == "video" && paintGroup != null -> { paints += mediaPaintOf(paintGroup, video = true); i += 2 }
                text == "weight" -> { weight = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "weight-per-side" -> {
                    val nums = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    perSide = nums?.takeIf { it.size == 4 }
                    i += 2
                }
                text == "align" -> { align = children.getOrNull(i + 1)?.leafText()?.lowercase(); i += 2 }
                text == "dash" -> {
                    dash = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    i += 2
                }
                text == "cap" -> { cap = children.getOrNull(i + 1)?.leafText(); i += 2 }
                text == "join" -> { join = children.getOrNull(i + 1)?.leafText(); i += 2 }
                else -> i += 1
            }
        }
        return CnlStrokesPayload(
            paints = paints,
            weight = weight?.let { CnlScalars.bindableDoubleOf(it) },
            align = align?.let { ReaderEnums.strokeAlign[it] },
            dash = dash?.mapNotNull { CnlScalars.doubleOf(it) },
            cap = cap?.let { CnlScalars.stringOf(it) },
            join = join?.let { CnlScalars.stringOf(it) },
            weightPerSide = perSide?.let { sides ->
                DesignInsets(
                    top = CnlScalars.bindableDoubleOf(sides[0]) ?: 0.0.bindable(),
                    right = CnlScalars.bindableDoubleOf(sides[1]) ?: 0.0.bindable(),
                    bottom = CnlScalars.bindableDoubleOf(sides[2]) ?: 0.0.bindable(),
                    left = CnlScalars.bindableDoubleOf(sides[3]) ?: 0.0.bindable(),
                )
            },
        )
    }

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
        properties += CnlProperty(
            CnlPropertyKind.Effect,
            listOf(CnlValue("effect", span)),
            keywordSpan,
            span,
            payload = CnlEffectPayload(effectOf(group)),
        )
        return after
    }

    /** `effect ( … )` group (mirrors `StyleBlockReader.readEffect`; colorless shadows dropped). */
    private fun effectOf(group: GGroup): DesignEffect? {
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
                blur == null && text != null && isNumberLike(text) -> { blur = node.leafText(); i += 1 }
                else -> i += 1
            }
        }
        val boundColor = color?.let { token ->
            if (token.startsWith("$")) Bindable.VarRef(token.removePrefix("$")) else CnlScalars.colorOf(token)
        }
        val offset = DesignPoint(
            x = x?.let { CnlScalars.bindableDoubleOf(it) } ?: 0.0.bindable(),
            y = y?.let { CnlScalars.bindableDoubleOf(it) } ?: 0.0.bindable(),
        )
        val blurBound = blur?.let { CnlScalars.bindableDoubleOf(it) } ?: 0.0.bindable()
        val spreadBound = spread?.let { CnlScalars.bindableDoubleOf(it) } ?: 0.0.bindable()
        return when (typeKey) {
            "dropShadow" -> boundColor?.let { DesignEffect.DropShadow(it, offset, blurBound, spreadBound) }
            "innerShadow" -> boundColor?.let { DesignEffect.InnerShadow(it, offset, blurBound, spreadBound) }
            "layerBlur" -> DesignEffect.LayerBlur(radius = blurBound)
            "backgroundBlur" -> DesignEffect.BackgroundBlur(radius = blurBound)
            else -> DesignEffect.Unknown(typeKey)
        }
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
            val token = tokens.getOrNull(next)?.takeIf { isNumberLike(it.text) } ?: break
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
        var key: String? = null
        var delayMs: Double? = null
        var variable: String? = null
        var i = valueStart
        if (triggerWord in triggerArgKinds && tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val arg = group.children.firstOrNull()?.leafText()
            if (arg != null) when (triggerWord) {
                "onkey" -> key = arg
                "afterdelay" -> delayMs = CnlScalars.doubleOf(arg)
                "onvariablechange" -> variable = arg
            }
            i = after
        }
        val actions = mutableListOf<DesignAction>()
        while (i < tokens.size) {
            val word = tokens[i].text.lowercase()
            if (word in triggerTypes) break // next interaction — re-dispatched by parseFrom
            val type = actionTypes[word] ?: break // not an action → hand back to parseFrom
            val (action, next) = consumeAction(type, tokens, i + 1)
            actions += action
            i = next
        }
        if (actions.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"$triggerWord\" needs an action")
            return i
        }
        val span = joinSpan(keywordSpan, tokens[(i - 1).coerceIn(0, tokens.size - 1)].span)
        properties += CnlProperty(
            CnlPropertyKind.Interactions,
            listOf(CnlValue(tokens[keywordStart].text, span)),
            keywordSpan,
            span,
            payload = CnlInteractionPayload(
                InteractionPatch(
                    trigger = ReaderEnums.trigger[triggerTypes[triggerWord] ?: triggerWord],
                    key = key,
                    delayMs = delayMs,
                    variable = variable,
                    actions = actions,
                ),
            ),
        )
        return i
    }

    /**
     * One action `<verb> [ (positional) ] [ modifier… ]` → a typed [DesignAction] plus the index
     * after the last token consumed. Modifiers (`animate`/`overlay`/`to`/`variant`/`animated`) are
     * resolved group-locally by action type, never through the top-level keyword table.
     */
    private fun consumeAction(
        type: String,
        tokens: List<Token>,
        start: Int,
    ): Pair<DesignAction, Int> {
        var i = start

        fun positional(): String? {
            if (tokens.getOrNull(i)?.text != "(") return null
            val (group, after) = parseGroup(tokens, i)
            i = after
            return group.children.firstOrNull()?.leafText()
        }

        val positionalValue: String? = when (type) {
            "navigate", "openOverlay", "swapOverlay", "openLink",
            "setVariable", "changeToVariant", "scrollTo", "runActionSet",
            -> positional()
            else -> null // back, closeOverlay carry no positional argument
        }

        var transition: DesignTransition? = null
        var overlay: OverlaySettings? = null
        var setValue: Bindable<String>? = null
        var variantSelection: Map<String, String>? = null
        var animated = true

        modifiers@ while (i < tokens.size) {
            when (tokens[i].text.lowercase()) {
                "animate" -> {
                    if (type !in transitionActions) break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    transition = transitionOf(group.first)
                    i = group.second
                }
                "overlay" -> {
                    if (type != "openOverlay") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    overlay = overlaySettingsOf(group.first)
                    i = group.second
                }
                "to" -> {
                    if (type != "setVariable") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    group.first.children.firstOrNull()?.leafText()?.let { setValue = CnlScalars.bindableStringOf(it) }
                    i = group.second
                }
                "variant" -> {
                    if (type != "changeToVariant") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    variantSelection = bareVariantSelectionOf(group.first)
                    i = group.second
                }
                "animated" -> {
                    if (type != "scrollTo") break@modifiers
                    val group = groupAfter(tokens, i) ?: break@modifiers
                    val flag = group.first.children.firstOrNull()?.leafText()?.lowercase()
                    if (flag != null && CnlVocabulary.booleans[flag] == false) animated = false
                    i = group.second
                }
                else -> break@modifiers
            }
        }
        val action = when (type) {
            "navigate" -> DesignAction.Navigate(to = positionalValue ?: "", transition = transition)
            "openOverlay" -> DesignAction.OpenOverlay(
                destination = positionalValue ?: "",
                overlay = overlay ?: OverlaySettings(),
                transition = transition,
            )
            "swapOverlay" -> DesignAction.SwapOverlay(destination = positionalValue ?: "", transition = transition)
            "closeOverlay" -> DesignAction.CloseOverlay(transition)
            "back" -> DesignAction.Back
            "openLink" -> DesignAction.OpenLink(url = positionalValue ?: "")
            "setVariable" -> DesignAction.SetVariable(
                variable = positionalValue ?: "",
                value = setValue ?: "".bindable(),
            )
            "changeToVariant" -> DesignAction.ChangeToVariant(
                target = positionalValue ?: "",
                variant = variantSelection ?: emptyMap(),
            )
            "scrollTo" -> DesignAction.ScrollTo(target = positionalValue ?: "", animated = animated)
            "runActionSet" -> DesignAction.RunActionSet(actionSetId = positionalValue ?: "")
            else -> DesignAction.Unknown(type) // unreachable: type comes from actionTypes
        }
        return action to i
    }

    /** The `( … )` group starting right after the keyword at [keywordIdx], or null when absent. */
    private fun groupAfter(tokens: List<Token>, keywordIdx: Int): Pair<GGroup, Int>? {
        if (tokens.getOrNull(keywordIdx + 1)?.text != "(") return null
        return parseGroup(tokens, keywordIdx + 1)
    }

    /** Mirrors the reader's private `transitionDirections` table. */
    private val transitionDirections = mapOf(
        "left" to TransitionDirection.Left,
        "right" to TransitionDirection.Right,
        "top" to TransitionDirection.Top,
        "bottom" to TransitionDirection.Bottom,
    )

    /** `animate (type … easing … duration N direction …)` (mirrors `readTransition`). */
    private fun transitionOf(group: GGroup): DesignTransition {
        val children = group.children
        val raw = LinkedHashMap<String, String>()
        var i = 0
        while (i < children.size) {
            val key = transitionSubKeys[children[i].leafText()?.lowercase()]
            val value = children.getOrNull(i + 1)?.leafText()
            if (key != null && value != null) {
                raw[key] = value
                i += 2
            } else {
                i += 1
            }
        }
        val easing = when (val name = raw["easing"]) {
            null -> DesignEasing.Named(EasingKind.EaseOut)
            "spring" -> DesignEasing.Spring(
                mass = raw["mass"]?.let { CnlScalars.doubleOf(it) } ?: 1.0,
                stiffness = raw["stiffness"]?.let { CnlScalars.doubleOf(it) } ?: 100.0,
                damping = raw["damping"]?.let { CnlScalars.doubleOf(it) } ?: 15.0,
            )
            else -> DesignEasing.Named(ReaderEnums.easing[name] ?: EasingKind.EaseOut)
        }
        return DesignTransition(
            type = raw["type"]?.let { ReaderEnums.transitionType[it] } ?: TransitionType.Instant,
            direction = raw["direction"]?.let { transitionDirections[it] } ?: TransitionDirection.Left,
            easing = easing,
            durationMs = raw["durationMs"]?.let { CnlScalars.doubleOf(it) } ?: 300.0,
        )
    }

    /** `overlay (position … offset (x y) closeOnOutside (false) background …)` (mirrors `readOverlay`). */
    private fun overlaySettingsOf(group: GGroup): OverlaySettings {
        val children = group.children
        fun valueAt(index: Int): String? {
            val node = children.getOrNull(index) ?: return null
            return node.leafText() ?: node.asGroup()?.children?.firstOrNull()?.leafText()
        }
        var positionRaw: String? = null
        var offset: DesignPoint? = null
        var closeRaw: String? = null
        var backgroundRaw: String? = null
        var i = 0
        while (i < children.size) {
            when (children[i].leafText()?.lowercase()) {
                "position" -> { valueAt(i + 1)?.let { positionRaw = it }; i += 2 }
                "offset" -> {
                    val pt = children.getOrNull(i + 1)?.asGroup()?.children?.mapNotNull { it.leafText() }
                    if (pt != null && pt.size >= 2) {
                        offset = DesignPoint(
                            CnlScalars.doubleOf(pt[0]) ?: 0.0,
                            CnlScalars.doubleOf(pt[1]) ?: 0.0,
                        )
                    }
                    i += 2
                }
                "closeonoutside" -> { valueAt(i + 1)?.let { closeRaw = it }; i += 2 }
                "background" -> { valueAt(i + 1)?.let { backgroundRaw = it }; i += 2 }
                else -> i += 1
            }
        }
        return OverlaySettings(
            position = positionRaw?.let { ReaderEnums.overlayPosition[it] } ?: OverlayPosition.Center,
            offset = offset,
            closeOnOutsideClick = closeRaw?.let { CnlVocabulary.booleans[it.lowercase()] } ?: true,
            background = backgroundRaw?.let { CnlScalars.colorOf(it) },
        )
    }

    /**
     * `variant (state hover size md)` for `changeToVariant` — values were written BARE, so YAML
     * `null`/`~` words die; everything else keeps its source text (`stringEntries`).
     */
    private fun bareVariantSelectionOf(group: GGroup): Map<String, String> {
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = LinkedHashMap<String, String>()
        var i = 0
        while (i + 1 < leaves.size) {
            leaves[i + 1].takeUnless { it == "null" || it == "~" }?.let { entries[leaves[i]] = it }
            i += 2
        }
        return entries
    }

    /** One keyframe group `(at [prop N]…)` (mirrors `readKeyframes`; a non-numeric `at` drops it). */
    private fun motionFrameOf(group: GGroup): MotionFrame? {
        val leaves = group.children.mapNotNull { it.leafText() }
        val at = CnlScalars.doubleOf(leaves.firstOrNull() ?: "0") ?: return null
        val frameProperties = LinkedHashMap<String, Double>()
        var i = 1
        while (i < leaves.size) {
            val key = leaves[i].lowercase()
            val value = leaves.getOrNull(i + 1)
            if (key in motionFrameKeys && value != null && isNumber(value)) {
                frameProperties[key] = value.toDouble()
                i += 2
            } else {
                i += 1
            }
        }
        return MotionFrame(at, frameProperties)
    }

    /** `motion (ref) [duration N] [loop] [frames (at …)…]` → a typed `motion:` block. */
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
        var duration: String? = null
        var loop = false
        val frames = mutableListOf<MotionFrame>()
        var framesAuthored = false
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
                        framesAuthored = true
                        motionFrameOf(group)?.let { frames += it }
                        i = after
                    }
                }
                else -> break@fallback
            }
        }
        val hasFallback = duration != null || loop || framesAuthored
        if (ref.isEmpty() && !hasFallback) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"motion\" needs a (ref) group or fallback")
            return i
        }
        val fallback = if (hasFallback) {
            MotionKeyframes(
                durationMs = duration?.toDouble() ?: 0.0,
                loop = loop,
                frames = frames,
            )
        } else {
            null
        }
        val span = joinSpan(keywordSpan, tokens[(i - 1).coerceIn(0, tokens.size - 1)].span)
        properties += CnlProperty(
            CnlPropertyKind.Motion,
            listOf(CnlValue("motion", span)),
            keywordSpan,
            span,
            payload = CnlMotionPayload(MotionPatch(DesignMotion(ref = ref, fallback = fallback))),
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
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Responsive,
            listOf(CnlValue(selectors.raw, span)),
            keywordSpan,
            span,
            payload = CnlVariantPayload(builder.variantTyped(selectors.typed)),
        )
        return i
    }

    /** The selector group typed as reader dimensions plus a compact raw spelling for [CnlValue]. */
    private class ResponsiveSelectors(val typed: Map<ResponsiveDimension, String>, val raw: String)

    /** `( breakpoint sm platform ios … )` -> AND-ed dimension selectors, or null when empty. */
    private fun responsiveSelectors(group: GGroup): ResponsiveSelectors? {
        val leaves = group.children.mapNotNull { it.leafText() }
        val entries = mutableListOf<String>()
        val typed = linkedMapOf<ResponsiveDimension, String>()
        var i = 0
        while (i < leaves.size - 1) {
            val readerKey = responsiveDimensionKeys[leaves[i].lowercase()]
            if (readerKey != null) {
                entries += "$readerKey: ${leaves[i + 1]}"
                ReaderEnums.responsiveDimension[readerKey]?.let { typed[it] = leaves[i + 1] }
                i += 2
            } else {
                i += 1
            }
        }
        return if (entries.isEmpty()) null else ResponsiveSelectors(typed, "{ ${entries.joinToString(", ")} }")
    }

    // --- export: `export ( fmt [at N] [«suffix»] )… ` | `export off` --------

    private val exportFormatWords = setOf("png", "jpg", "jpeg", "svg", "pdf")

    /**
     * `export (png at 2 «@2x») (svg)` → typed export settings (one value per setting group so
     * repeated `export` clauses on one line merge into a single export block); `export off` →
     * `enabled: false`.
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
            properties += CnlProperty(
                CnlPropertyKind.Export,
                listOf(CnlValue("off", span)),
                keywordSpan,
                span,
                payload = CnlExportPayload(disabled = true, settings = emptyList()),
            )
            return valueStart + 1
        }
        var i = valueStart
        val values = mutableListOf<CnlValue>()
        val settings = mutableListOf<ExportSetting>()
        while (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            values += CnlValue("export", joinSpan(tokens[i].span, tokens[after - 1].span))
            exportSettingOf(group)?.let { settings += it }
            i = after
        }
        if (values.isEmpty()) {
            CnlDiagnostics.warn(diagnostics, CnlRule.MissingValue, lineNumber, "\"export\" needs ( … ) settings or `off`")
            return valueStart
        }
        properties += CnlProperty(
            CnlPropertyKind.Export,
            values,
            keywordSpan,
            joinSpan(keywordSpan, tokens[i - 1].span),
            payload = CnlExportPayload(disabled = false, settings = settings),
        )
        return i
    }

    /** `( png at 2 «@2x» )` → a typed export setting (format-less groups dropped like the reader). */
    private fun exportSettingOf(group: GGroup): ExportSetting? {
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
        val exportFormat = format?.let { ReaderEnums.exportFormat[it] } ?: return null
        return ExportSetting(
            format = exportFormat,
            scale = scale?.let { CnlScalars.doubleOf(it) } ?: 1.0,
            suffix = suffix ?: "",
        )
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
        var id: String? = null
        var target: String? = null
        var audience: String? = null
        var i = valueStart + 1
        if (tokens.getOrNull(i)?.text == "(") {
            val (group, after) = parseGroup(tokens, i)
            val leaves = group.children.mapNotNull { it.leafText() }
            var j = 0
            while (j < leaves.size - 1) {
                when (leaves[j].lowercase()) {
                    "id" -> { id = leaves[j + 1]; j += 2 }
                    "target" -> { target = leaves[j + 1]; j += 2 }
                    "audience" -> { audience = leaves[j + 1]; j += 2 }
                    else -> j += 1
                }
            }
            i = after
        }
        val span = joinSpan(keywordSpan, tokens[i - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Annotation,
            listOf(CnlValue("note", span)),
            keywordSpan,
            span,
            payload = CnlAnnotationPayload(
                DesignAnnotation(
                    id = id.orEmpty(),
                    target = target.orEmpty(),
                    text = textTok.text,
                    audience = audience.orEmpty(),
                ),
            ),
        )
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
        // The reader drops a measurement missing from/to/axis but the handoff block still exists.
        val measurement = if (from != null && to != null && axis != null) {
            DesignMeasurement(
                from = from,
                to = to,
                axis = ReaderEnums.measureAxis.getValue(axis),
                value = value?.let { CnlScalars.bindableDoubleOf(it) },
            )
        } else {
            null
        }
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.Measurement,
            listOf(CnlValue("measure", span)),
            keywordSpan,
            span,
            payload = CnlMeasurementPayload(measurement),
        )
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
        val span = joinSpan(keywordSpan, tokens[after - 1].span)
        properties += CnlProperty(
            CnlPropertyKind.CodeHint,
            listOf(CnlValue("code", span)),
            keywordSpan,
            span,
            payload = CnlCodeHintPayload(
                CodeHints(framework = framework.orEmpty(), componentHint = component.orEmpty()),
            ),
        )
        return after
    }

    // --- desugar: CnlElement -> typed patch entries (direct construction; no YAML round trip) ---

    /** Desugars [element] to typed [DirectPatchEntry] blocks at [line]. */
    @Suppress("UNUSED_PARAMETER")
    fun desugar(element: CnlElement, line: Int, diagnostics: DiagnosticCollector): List<DirectPatchEntry> {
        val builder = BlockBuilder()
        element.noun?.let { noun ->
            builder.nodeTyped { it.copy(type = noun.nodeType) }
            noun.containerKind?.let { containerKind -> builder.nodeTyped { it.copy(containerKind = containerKind) } }
            noun.role?.let { role -> builder.nodeTyped { it.copy(role = role) } }
            noun.shapeKind?.let { kind -> builder.shapeTyped { it.copy(kind = ReaderEnums.shapeKind[kind]) } }
        }
        element.properties.forEach { property -> applyProperty(builder, property) }
        return builder.toEntries(line)
    }

    /** `size W by H` axis (mirrors the old `sizeAxis` fragment + `readSizingAxis`). */
    private fun sizingAxisOfToken(value: String): SizingPatch =
        if (isNumber(value)) {
            SizingPatch(mode = SizingMode.Fixed, value = CnlScalars.doubleOf(value))
        } else {
            SizingPatch(mode = ReaderEnums.sizingMode[value])
        }

    /** A bare fill/stroke color token → solid paint (mirrors `readPaint` on a color scalar). */
    private fun solidPaintOfToken(token: String): DesignPaint? =
        CnlScalars.colorOf(token)?.let { DesignPaint.Solid(it) }

    private fun applyProperty(builder: BlockBuilder, property: CnlProperty) {
        val values = property.values.map { it.raw }
        when (property.kind) {
            CnlPropertyKind.Size ->
                builder.sizingTyped(width = sizingAxisOfToken(values[0]), height = sizingAxisOfToken(values[1]))
            CnlPropertyKind.Width -> (property.payload as? CnlSizingPayload)?.let { builder.sizingTyped(width = it.sizing) }
            CnlPropertyKind.Height -> (property.payload as? CnlSizingPayload)?.let { builder.sizingTyped(height = it.sizing) }
            CnlPropertyKind.Fill -> builder.addFillTyped(solidPaintOfToken(values[0]))
            CnlPropertyKind.FillComplex -> builder.addFillTyped((property.payload as? CnlPaintPayload)?.paint)
            CnlPropertyKind.Stroke -> builder.strokeFlatTyped(
                paint = solidPaintOfToken(values[0]),
                weight = values.getOrNull(1)?.takeIf { isNumber(it) }?.let { CnlScalars.bindableDoubleOf(it) },
                align = values.getOrNull(2)?.let { ReaderEnums.strokeAlign[it.lowercase()] },
            )
            CnlPropertyKind.StrokeComplex -> builder.strokeComplexTyped(property.payload as? CnlStrokesPayload)
            CnlPropertyKind.Effect -> builder.addEffectTyped((property.payload as? CnlEffectPayload)?.effect)
            CnlPropertyKind.Radius -> {
                val corners = (property.payload as? CnlRadiusPayload)?.radius
                    ?: CnlScalars.bindableDoubleOf(values[0])?.let { DesignCornerRadius.all(it) }
                builder.radiusTyped(corners)
            }
            CnlPropertyKind.Smoothing -> builder.smoothingTyped(CnlScalars.doubleOf(values[0]))
            CnlPropertyKind.Blend -> builder.styleTyped { it.copy(blendMode = CnlScalars.stringOf(values[0])) }
            CnlPropertyKind.StyleRefs ->
                (property.payload as? CnlPairsPayload)?.pairs?.forEach { (key, value) ->
                    val ref = CnlScalars.stringOf(value)
                    builder.styleTyped {
                        when (key) {
                            "fillStyle" -> it.copy(fillStyle = ref)
                            "strokeStyle" -> it.copy(strokeStyle = ref)
                            "textStyle" -> it.copy(textStyle = ref)
                            "effectStyle" -> it.copy(effectStyle = ref)
                            else -> it.copy(gridStyle = ref)
                        }
                    }
                }
            CnlPropertyKind.Opacity -> builder.styleTyped { it.copy(opacity = CnlScalars.bindableDoubleOf(values[0])) }
            CnlPropertyKind.Visible -> builder.nodeTyped { it.copy(visible = CnlScalars.bindableBooleanOf(values[0])) }
            CnlPropertyKind.Locked -> builder.nodeTyped { it.copy(locked = values[0] == "true") }
            CnlPropertyKind.VariableModes -> {
                val modes = (property.payload as? CnlPairsPayload)?.pairs.orEmpty()
                    .mapNotNull { (key, value) -> CnlScalars.stringOf(value)?.let { key to it } }
                    .toMap()
                builder.nodeTyped { it.copy(variableModes = modes) }
            }
            CnlPropertyKind.Rotation -> builder.positionTyped { it.copy(rotation = CnlScalars.doubleOf(values[0])) }
            CnlPropertyKind.Position -> builder.positionTyped {
                it.copy(x = CnlScalars.doubleOf(values[0]), y = CnlScalars.doubleOf(values[1]))
            }
            CnlPropertyKind.Padding -> {
                val sides = values.map { CnlScalars.bindableDoubleOf(it) }
                val (blockStart, inlineEnd, blockEnd, inlineStart) = when (values.size) {
                    2 -> listOf(sides[0], sides[1], sides[0], sides[1])
                    4 -> listOf(sides[0], sides[1], sides[2], sides[3])
                    else -> listOf(sides[0], sides[0], sides[0], sides[0])
                }
                builder.layoutTyped {
                    it.copy(
                        paddingBlockStart = blockStart,
                        paddingInlineEnd = inlineEnd,
                        paddingBlockEnd = blockEnd,
                        paddingInlineStart = inlineStart,
                    )
                }
            }
            CnlPropertyKind.Gap -> when (val payload = property.payload) {
                is CnlGapPayload -> builder.gapTyped(main = null, row = payload.row, column = payload.column)
                else -> if (values[0] == "auto") {
                    builder.gapTyped(main = DesignGap.Auto, row = null, column = null)
                } else {
                    builder.gapTyped(
                        main = CnlScalars.bindableDoubleOf(values[0])?.let { DesignGap.Fixed(it) },
                        row = null,
                        column = null,
                    )
                }
            }
            CnlPropertyKind.Direction -> builder.layoutTyped { it.copy(mode = ReaderEnums.layoutMode[values[0]]) }
            CnlPropertyKind.AlignParent -> CnlVocabulary.alignDirections[values[0].lowercase()]?.let { (axis, value) ->
                when (axis) {
                    "both" -> builder.constraintTyped(horizontal = value, vertical = value)
                    "horizontal" -> builder.constraintTyped(horizontal = value)
                    else -> builder.constraintTyped(vertical = value)
                }
            }
            CnlPropertyKind.FontSize -> builder.typographyTyped { it.copy(fontSize = CnlScalars.bindableDoubleOf(values[0])) }
            CnlPropertyKind.FontWeight -> builder.typographyTyped { it.copy(fontWeight = CnlScalars.bindableDoubleOf(values[0])) }
            CnlPropertyKind.Id -> builder.nodeTyped { it.copy(id = CnlScalars.stringOf(values[0])) }
            CnlPropertyKind.NodeName -> builder.nodeTyped { it.copy(name = values[0]) }
            CnlPropertyKind.FontFamily -> builder.typographyTyped { it.copy(fontFamily = values[0]) }
            CnlPropertyKind.LineHeight -> builder.typographyTyped { it.copy(lineHeight = CnlScalars.unitValueOf(values[0])) }
            CnlPropertyKind.Tracking -> builder.typographyTyped { it.copy(letterSpacing = CnlScalars.unitValueOf(values[0])) }
            CnlPropertyKind.ParagraphSpacing -> builder.typographyTyped { it.copy(paragraphSpacing = CnlScalars.doubleOf(values[0])) }
            CnlPropertyKind.TextAlign -> builder.typographyTyped { it.copy(textAlignHorizontal = ReaderEnums.textAlignHorizontal[values[0]]) }
            CnlPropertyKind.TextValign -> builder.typographyTyped { it.copy(textAlignVertical = ReaderEnums.textAlignVertical[values[0]]) }
            CnlPropertyKind.TextCase -> builder.typographyTyped { it.copy(textCase = ReaderEnums.textCase[values[0]]) }
            CnlPropertyKind.TextDecoration -> builder.typographyTyped { it.copy(textDecoration = ReaderEnums.textDecoration[values[0]]) }
            CnlPropertyKind.Features -> builder.typographyTyped {
                it.copy(fontFeatures = (property.payload as? CnlFeaturesPayload)?.features ?: emptyMap())
            }
            CnlPropertyKind.Axes -> builder.typographyTyped {
                it.copy(variableAxes = (property.payload as? CnlAxesPayload)?.axes ?: emptyMap())
            }
            CnlPropertyKind.TextKey -> builder.textTyped { it.copy(key = values[0]) }
            CnlPropertyKind.TextStyleRef ->
                builder.textTyped { it.copy(styleRef = CnlScalars.stringOf(values[0].removePrefix("$"))) }
            CnlPropertyKind.Characters -> {
                if (isStringBinding(values[0])) {
                    builder.textTyped { it.copy(characters = CnlScalars.bindableStringOf(values[0])) }
                } else {
                    builder.textTyped { it.copy(defaultText = values[0]) }
                }
            }
            CnlPropertyKind.MaxLines -> builder.maxLinesTyped(CnlScalars.intOf(values[0]))
            CnlPropertyKind.ListSettings ->
                builder.textTyped { it.copy(list = (property.payload as? CnlListSettingsPayload)?.settings) }
            CnlPropertyKind.AutoSize -> {
                if (values[0].lowercase() == "both") {
                    builder.textTyped { it.copy(resizingWidth = SizingMode.Hug, resizingHeight = SizingMode.Hug) }
                } else {
                    builder.textTyped { it.copy(resizingHeight = SizingMode.Hug) }
                }
            }
            CnlPropertyKind.Truncate -> builder.truncateTyped(CnlScalars.intOf(values[0]))
            CnlPropertyKind.Wrap -> builder.layoutTyped { it.copy(wrap = true) }
            CnlPropertyKind.Clip -> builder.layoutTyped { it.copy(clipContent = true) }
            CnlPropertyKind.Absolute -> builder.layoutPositionTyped { it.copy(positionMode = NodePositionMode.Absolute) }
            CnlPropertyKind.AutoLayout -> builder.nodeTyped { it.copy(containerKind = ContainerKind.AutoLayout) }
            CnlPropertyKind.Distribute -> builder.layoutTyped { it.copy(distribution = ReaderEnums.distribution[values[0]]) }
            CnlPropertyKind.Anchor -> (property.payload as? CnlPairsPayload)?.pairs?.forEach { (side, token) ->
                val bound = CnlScalars.bindableDoubleOf(token)
                builder.layoutPositionTyped {
                    when (side) {
                        "inlineStart" -> it.copy(anchorInlineStart = bound)
                        "inlineEnd" -> it.copy(anchorInlineEnd = bound)
                        "blockStart" -> it.copy(anchorBlockStart = bound)
                        else -> it.copy(anchorBlockEnd = bound)
                    }
                }
            }
            CnlPropertyKind.Constraints -> (property.payload as? CnlPairsPayload)?.pairs?.forEach { (axis, value) ->
                if (axis == "horizontal") builder.constraintTyped(horizontal = value) else builder.constraintTyped(vertical = value)
            }
            CnlPropertyKind.ContainerAlign -> {
                val pairs = (property.payload as? CnlPairsPayload)?.pairs.orEmpty().toMap()
                builder.layoutTyped {
                    it.copy(
                        alignInline = pairs["inline"]?.let { value -> ReaderEnums.align[value] },
                        alignBlock = pairs["block"]?.let { value -> ReaderEnums.align[value] },
                        baseline = pairs["baseline"]?.let { value -> ReaderEnums.baseline[value] },
                    )
                }
            }
            CnlPropertyKind.Overflow -> {
                val pairs = (property.payload as? CnlPairsPayload)?.pairs.orEmpty().toMap()
                builder.layoutTyped {
                    it.copy(
                        overflowX = pairs["x"]?.let { value -> ReaderEnums.overflow[value] },
                        overflowY = pairs["y"]?.let { value -> ReaderEnums.overflow[value] },
                    )
                }
            }
            CnlPropertyKind.Scroll -> {
                val payload = property.payload as? CnlScrollPayload
                builder.layoutTyped {
                    it.copy(
                        scrollDirection = payload?.direction,
                        scrollSticky = if (payload?.sticky == true) true else null,
                        scrollFixedChildren = payload?.fixedChildren,
                    )
                }
            }
            CnlPropertyKind.Columns -> builder.columnsTyped(property.payload as? CnlTrackAxisPayload)
            CnlPropertyKind.Rows -> builder.rowsTyped(property.payload as? CnlTrackAxisPayload)
            CnlPropertyKind.Place -> {
                val payload = property.payload as? CnlPlacePayload
                builder.layoutTyped {
                    it.copy(
                        placement = GridPlacement(
                            column = payload?.column ?: 0,
                            row = payload?.row ?: 0,
                            columnSpan = payload?.columnSpan ?: 1,
                            rowSpan = payload?.rowSpan ?: 1,
                        ),
                    )
                }
            }
            CnlPropertyKind.Guides -> builder.layoutTyped {
                it.copy(guides = (property.payload as? CnlGuidesPayload)?.guides.orEmpty())
            }
            CnlPropertyKind.Grids -> builder.layoutTyped {
                it.copy(grids = (property.payload as? CnlGridsPayload)?.grids.orEmpty())
            }
            CnlPropertyKind.Link -> builder.spanTyped((property.payload as? CnlSpanPayload)?.span)
            CnlPropertyKind.Span -> builder.spanTyped((property.payload as? CnlSpanPayload)?.span)
            CnlPropertyKind.ComponentRef -> builder.componentTyped { it.copy(ref = CnlScalars.stringOf(values[0])) }
            CnlPropertyKind.LibraryRef -> builder.componentTyped { it.copy(libraryRef = CnlScalars.stringOf(values[0])) }
            CnlPropertyKind.Variant -> (property.payload as? CnlVariantSelectionPayload)?.let { payload ->
                builder.componentTyped { it.copy(variant = payload.variant) }
            }
            CnlPropertyKind.Props -> (property.payload as? CnlPropsPayload)?.let { payload ->
                builder.componentTyped { it.copy(props = payload.props) }
            }
            CnlPropertyKind.Detach -> builder.componentTyped { it.copy(detach = true) }
            CnlPropertyKind.ResetOverrides -> builder.componentTyped { it.copy(resetOverrides = true) }
            CnlPropertyKind.SlotOverride ->
                (property.payload as? CnlSlotPayload)?.let { builder.slotOverrideTyped(it.name, it.fills) }
            CnlPropertyKind.SetOverride ->
                (property.payload as? CnlSetOverridePayload)?.let { builder.addSetOverrideTyped(it.set) }
            CnlPropertyKind.NestedOverride ->
                (property.payload as? CnlNestedPayload)?.let { builder.nestedOverrideTyped(it.target, it.override) }
            // plainScalar-written name/set: a bare `null` word reads back as null.
            CnlPropertyKind.ComponentName ->
                builder.componentTyped { it.copy(name = values[0].takeUnless { v -> v == "null" }) }
            CnlPropertyKind.ComponentSet ->
                builder.componentTyped { it.copy(set = values[0].takeUnless { v -> v == "null" }) }
            CnlPropertyKind.ComponentAxis ->
                (property.payload as? CnlComponentAxisPayload)?.let { builder.componentAxisTyped(it.axis, it.values) }
            CnlPropertyKind.ComponentPropDefinition ->
                (property.payload as? CnlComponentPropPayload)?.let { builder.componentPropertyTyped(it.name, it.definition) }
            CnlPropertyKind.Media -> (property.payload as? CnlMediaPayload)?.let { builder.mediaTyped(it.media) }
            CnlPropertyKind.ShapePoints -> builder.shapeTyped { it.copy(pointCount = CnlScalars.intOf(values[0])) }
            CnlPropertyKind.ShapeInner -> builder.shapeTyped { it.copy(innerRadius = CnlScalars.doubleOf(values[0])) }
            CnlPropertyKind.ShapeArc -> builder.shapeTyped {
                it.copy(arcStartDeg = CnlScalars.doubleOf(values[0]), arcSweepDeg = CnlScalars.doubleOf(values[1]))
            }
            CnlPropertyKind.ViewBox -> (property.payload as? CnlViewBoxPayload)?.let { payload ->
                builder.vectorTyped { it.copy(viewBox = payload.viewBox) }
            }
            CnlPropertyKind.IconRef -> builder.vectorTyped { it.copy(iconRef = values[0]) }
            CnlPropertyKind.PathRef -> builder.vectorTyped { it.copy(pathRef = values[0]) }
            CnlPropertyKind.VectorPaths -> (property.payload as? CnlVectorPathsPayload)?.let { payload ->
                builder.vectorTyped { it.copy(paths = payload.paths) }
            }
            CnlPropertyKind.VectorNetwork -> (property.payload as? CnlNetworkPayload)?.let { payload ->
                builder.vectorTyped {
                    it.copy(
                        network = payload.network,
                        regionFills = payload.regionFills.takeIf { fills -> fills.isNotEmpty() },
                    )
                }
            }
            CnlPropertyKind.BooleanOp -> builder.vectorTyped {
                it.copy(boolean = ReaderEnums.booleanOp[values[0]]?.let { op -> BooleanOpPatch(op) })
            }
            CnlPropertyKind.Mask -> (property.payload as? CnlMaskPayload)?.let { builder.maskTyped(it.mask) }
            CnlPropertyKind.Interactions ->
                (property.payload as? CnlInteractionPayload)?.let { builder.interactionTyped(it.interaction) }
            CnlPropertyKind.Motion -> (property.payload as? CnlMotionPayload)?.let { builder.motionTyped(it.motion) }
            CnlPropertyKind.Responsive ->
                (property.payload as? CnlVariantPayload)?.let { builder.responsiveVariantTyped(it.variant) }
            CnlPropertyKind.Export -> (property.payload as? CnlExportPayload)?.let {
                if (it.disabled) builder.exportDisabledTyped() else builder.exportSettingsTyped(it.settings)
            }
            CnlPropertyKind.Annotation ->
                (property.payload as? CnlAnnotationPayload)?.let { builder.handoffAnnotationTyped(it.annotation) }
            CnlPropertyKind.Measurement ->
                (property.payload as? CnlMeasurementPayload)?.let { builder.handoffMeasurementTyped(it.measurement) }
            CnlPropertyKind.CodeHint ->
                (property.payload as? CnlCodeHintPayload)?.let { builder.handoffCodeTyped(it.code) }
        }
    }

    private fun isStringBinding(value: String): Boolean =
        propName(value) != null || value.startsWith("$") || isBinding(value)

    private fun propName(value: String): String? = when {
        value.startsWith("\$prop.") -> value.removePrefix("\$prop.").takeIf { it.isNotEmpty() }
        value.startsWith("\$prop:") -> value.removePrefix("\$prop:").takeIf { it.isNotEmpty() }
        else -> null
    }

    /** A `{{expr}}` data-binding token. Single-token only (multi-word exprs need a tokenizer rule). */
    private fun isBinding(text: String): Boolean = text.startsWith("{{") && text.endsWith("}}")

    /** A flat `stroke #hex [weight] [align]` phrase (composes with a `stroke ( … )` record). */
    private data class StrokeFlat(
        val paint: DesignPaint?,
        val weight: Bindable<Double>?,
        val align: StrokeAlign?,
    )

    /**
     * Accumulates one CNL sentence's block state. Every block is built as a typed patch and
     * emitted as a [DirectPatchEntry] — no YAML round trip anywhere. "Touched" flags mirror the
     * old fragment-list `parts.isEmpty()` emission gating, so blocks that used to emit an
     * (effectively empty) map still emit an (effectively empty) patch.
     */
    private class BlockBuilder {
        // --- typed state (direct desugar). "Touched" flags mirror the old parts.isEmpty() gating. ---
        private var nodePatch = NodePatch()
        private var nodeCoreTouched = false
        private var positionTouched = false
        private var constraintTouched = false
        private var shapePatch = ShapePatch()
        private var shapeTouched = false
        private var layoutPatch = LayoutPatch()
        private var layoutTouched = false
        private var layoutPositionTouched = false
        private var sizingWidthTyped: SizingPatch? = null
        private var sizingHeightTyped: SizingPatch? = null
        private var gapRowTyped: Bindable<Double>? = null
        private var gapColumnTyped: Bindable<Double>? = null
        private var columnsAxis: CnlTrackAxisPayload? = null
        private var rowsAxis: CnlTrackAxisPayload? = null
        private var stylePatch = StylePatch()
        private var styleTouched = false
        private var radiusCorners: DesignCornerRadius? = null
        private var cornerSmoothing: Double? = null
        private val fillsTyped = mutableListOf<DesignPaint>()
        private var fillsTouched = false
        private val effectsTyped = mutableListOf<DesignEffect>()
        private var effectsTouched = false
        private var strokeFlat: StrokeFlat? = null
        private var strokeComplex: CnlStrokesPayload? = null
        private var typographyStyle = DesignTextStyle()
        private var typographyTouched = false
        private var textPatch = TextPatch()
        private var textTouched = false
        private var truncateOverflow = false
        private var maxLines: Int? = null
        private val spansTyped = mutableListOf<TextSpanPatch>()
        private val responsiveVariantsTyped = mutableListOf<ResponsiveVariantPatch>()
        private val setOverridesTyped = mutableListOf<SetOverridePatch>()
        private var mediaPatch = MediaPatch()
        private var mediaTouched = false
        private var vectorPatch = VectorPatch()
        private var vectorTouched = false
        private var maskPatch = MaskPatch()
        private var maskTouched = false
        private val interactionsTyped = mutableListOf<InteractionPatch>()
        private var motionPatch: MotionPatch? = null
        private var componentPatch = ComponentPatch()
        private var componentTouched = false
        private val componentAxes = LinkedHashMap<String, List<String>>()
        private val componentProperties = LinkedHashMap<String, ComponentPropertyDefinition>()
        private val slotOverridesTyped = LinkedHashMap<String, List<SlotOverridePatch>>()
        private val nestedOverridesTyped = LinkedHashMap<String, NestedInstancePatch>()
        private val exportSettings = mutableListOf<ExportSetting>()
        private var exportSettingsTouched = false
        private var exportDisabled = false
        private val annotationsTyped = mutableListOf<DesignAnnotation>()
        private val measurementsTyped = mutableListOf<DesignMeasurement>()
        private var codeHintTyped: CodeHints? = null
        private var handoffTouched = false

        // --- typed setters (each marks its block "touched" exactly where a string part was added) ---

        fun nodeTyped(transform: (NodePatch) -> NodePatch) {
            nodePatch = transform(nodePatch)
            nodeCoreTouched = true
        }

        fun positionTyped(transform: (NodePatch) -> NodePatch) {
            nodePatch = transform(nodePatch)
            positionTouched = true
        }

        fun constraintTyped(horizontal: String? = null, vertical: String? = null) {
            horizontal?.let {
                nodePatch = nodePatch.copy(constraintsHorizontal = ReaderEnums.horizontalConstraint[it])
                constraintTouched = true
            }
            vertical?.let {
                nodePatch = nodePatch.copy(constraintsVertical = ReaderEnums.verticalConstraint[it])
                constraintTouched = true
            }
        }

        fun shapeTyped(transform: (ShapePatch) -> ShapePatch) {
            shapePatch = transform(shapePatch)
            shapeTouched = true
        }

        fun layoutTyped(transform: (LayoutPatch) -> LayoutPatch) {
            layoutPatch = transform(layoutPatch)
            layoutTouched = true
        }

        fun layoutPositionTyped(transform: (LayoutPatch) -> LayoutPatch) {
            layoutPatch = transform(layoutPatch)
            layoutPositionTouched = true
        }

        fun sizingTyped(width: SizingPatch? = null, height: SizingPatch? = null) {
            width?.let { sizingWidthTyped = it }
            height?.let { sizingHeightTyped = it }
        }

        /** A `gap` phrase replaces the whole gap state (the old single `gap:` key: last one wins). */
        fun gapTyped(main: DesignGap?, row: Bindable<Double>?, column: Bindable<Double>?) {
            layoutTouched = true
            layoutPatch = layoutPatch.copy(gap = main)
            gapRowTyped = row
            gapColumnTyped = column
        }

        fun columnsTyped(axis: CnlTrackAxisPayload?) {
            layoutTouched = true
            axis?.let { columnsAxis = it }
        }

        fun rowsTyped(axis: CnlTrackAxisPayload?) {
            layoutTouched = true
            axis?.let { rowsAxis = it }
        }

        fun styleTyped(transform: (StylePatch) -> StylePatch) {
            stylePatch = transform(stylePatch)
            styleTouched = true
        }

        fun radiusTyped(corners: DesignCornerRadius?) {
            styleTouched = true
            radiusCorners = corners
        }

        fun smoothingTyped(value: Double?) {
            styleTouched = true
            cornerSmoothing = value
        }

        fun addFillTyped(paint: DesignPaint?) {
            fillsTouched = true
            paint?.let { fillsTyped += it }
        }

        fun addEffectTyped(effect: DesignEffect?) {
            effectsTouched = true
            effect?.let { effectsTyped += it }
        }

        fun strokeFlatTyped(paint: DesignPaint?, weight: Bindable<Double>?, align: StrokeAlign?) {
            styleTouched = true
            strokeFlat = StrokeFlat(paint, weight, align)
        }

        fun strokeComplexTyped(payload: CnlStrokesPayload?) {
            styleTouched = true
            payload?.let { strokeComplex = it }
        }

        fun typographyTyped(transform: (DesignTextStyle) -> DesignTextStyle) {
            typographyStyle = transform(typographyStyle)
            typographyTouched = true
        }

        fun textTyped(transform: (TextPatch) -> TextPatch) {
            textPatch = transform(textPatch)
            textTouched = true
        }

        fun maxLinesTyped(value: Int?) {
            textTouched = true
            maxLines = value
        }

        fun truncateTyped(value: Int?) {
            textTouched = true
            truncateOverflow = true
            maxLines = value
        }

        fun spanTyped(span: TextSpanPatch?) {
            span?.let { spansTyped += it }
        }

        fun responsiveVariantTyped(variant: ResponsiveVariantPatch) { responsiveVariantsTyped += variant }

        fun addSetOverrideTyped(set: SetOverridePatch) { setOverridesTyped += set }

        /** One `responsive.variants` record from this (sub-)builder's captured typed overrides. */
        fun variantTyped(selectors: Map<ResponsiveDimension, String>): ResponsiveVariantPatch =
            ResponsiveVariantPatch(
                selectors = selectors,
                layout = layoutTypedOrNull(),
                style = styleTypedOrNull(),
                text = textTypedOrNull(includeSpans = false),
            )

        /** One typed `overrides.sets` record from this sub-builder's captured appearance phrases. */
        fun setOverrideTyped(target: List<String>): SetOverridePatch =
            SetOverridePatch(
                target = target,
                style = styleTypedOrNull(),
                text = textTypedOrNull(includeSpans = false),
                node = overrideNodeTypedOrNull(),
            )

        /** The `media:` record; repeated phrases merge per-field (YAML duplicate-key last-wins). */
        fun mediaTyped(patch: MediaPatch) {
            mediaTouched = true
            mediaPatch = MediaPatch(
                asset = patch.asset ?: mediaPatch.asset,
                kind = patch.kind ?: mediaPatch.kind,
                fillMode = patch.fillMode ?: mediaPatch.fillMode,
                focalPoint = patch.focalPoint ?: mediaPatch.focalPoint,
                alt = patch.alt ?: mediaPatch.alt,
                replaceable = patch.replaceable ?: mediaPatch.replaceable,
                opacity = patch.opacity ?: mediaPatch.opacity,
                blendMode = patch.blendMode ?: mediaPatch.blendMode,
                poster = patch.poster ?: mediaPatch.poster,
                autoplay = patch.autoplay ?: mediaPatch.autoplay,
                loop = patch.loop ?: mediaPatch.loop,
                muted = patch.muted ?: mediaPatch.muted,
            )
        }

        /** A `vector:` aspect (viewBox/iconRef/pathRef/paths/network/boolean), merged into ONE patch. */
        fun vectorTyped(transform: (VectorPatch) -> VectorPatch) {
            vectorTouched = true
            vectorPatch = transform(vectorPatch)
        }

        /** The `mask:` block; repeated phrases merge per-field (YAML duplicate-key last-wins). */
        fun maskTyped(patch: MaskPatch) {
            maskTouched = true
            maskPatch = MaskPatch(
                type = patch.type ?: maskPatch.type,
                source = patch.source ?: maskPatch.source,
                appliesTo = patch.appliesTo ?: maskPatch.appliesTo,
            )
        }

        /** Each trigger phrase appends one interaction; they emit as separate `interaction:` blocks. */
        fun interactionTyped(patch: InteractionPatch) { interactionsTyped += patch }

        /** The node's single `motion:` block (last phrase wins). */
        fun motionTyped(patch: MotionPatch) { motionPatch = patch }

        /** A `component:` block aspect (ref/libraryRef/variant/props/detach/resetOverrides/name/set). */
        fun componentTyped(transform: (ComponentPatch) -> ComponentPatch) {
            componentTouched = true
            componentPatch = transform(componentPatch)
        }

        /** One definition-side `component.variants` axis entry. */
        fun componentAxisTyped(axis: String, values: List<String>) {
            componentTouched = true
            componentAxes[axis] = values
        }

        /** One definition-side `component.properties` entry. */
        fun componentPropertyTyped(name: String, definition: ComponentPropertyDefinition) {
            componentTouched = true
            componentProperties[name] = definition
        }

        /** One `overrides.slots` entry (duplicate slot names: last wins, like YAML map keys). */
        fun slotOverrideTyped(name: String, fills: List<SlotOverridePatch>) { slotOverridesTyped[name] = fills }

        /** One `overrides.nestedInstances` entry (duplicate targets: last wins). */
        fun nestedOverrideTyped(target: String, override: NestedInstancePatch) {
            nestedOverridesTyped[target] = override
        }

        /** Export `( … )` settings; repeated `export` clauses merge into one list. */
        fun exportSettingsTyped(settings: List<ExportSetting>) {
            exportSettingsTouched = true
            exportSettings += settings
        }

        /** `export off` → `enabled: false` (only emitted when no settings were authored). */
        fun exportDisabledTyped() { exportDisabled = true }

        fun handoffAnnotationTyped(annotation: DesignAnnotation) {
            handoffTouched = true
            annotationsTyped += annotation
        }

        /** A `measure` phrase marks handoff authored even when the record is invalid (dropped). */
        fun handoffMeasurementTyped(measurement: DesignMeasurement?) {
            handoffTouched = true
            measurement?.let { measurementsTyped += it }
        }

        fun handoffCodeTyped(code: CodeHints) {
            handoffTouched = true
            codeHintTyped = code
        }

        // --- typed emission (mirrors the block readers on the old fragments) ---

        private fun nodeTypedOrNull(): NodePatch? =
            if (nodeCoreTouched || positionTouched || constraintTouched) nodePatch else null

        /** The override-set `node` group carries only the core fields (the old map ignored position/constraints). */
        private fun overrideNodeTypedOrNull(): NodePatch? =
            if (!nodeCoreTouched) {
                null
            } else {
                nodePatch.copy(
                    positionMode = null,
                    x = null,
                    y = null,
                    rotation = null,
                    constraintsHorizontal = null,
                    constraintsVertical = null,
                )
            }

        private fun shapeTypedOrNull(): ShapePatch? = if (shapeTouched) shapePatch else null

        private fun layoutTypedOrNull(): LayoutPatch? {
            if (!layoutTouched && !layoutPositionTouched && sizingWidthTyped == null && sizingHeightTyped == null) {
                return null
            }
            return layoutPatch.copy(
                sizingWidth = sizingWidthTyped,
                sizingHeight = sizingHeightTyped,
                rowGap = gapRowTyped ?: rowsAxis?.gap,
                columnGap = gapColumnTyped ?: columnsAxis?.gap,
                gridColumns = columnsAxis?.tracks,
                gridRows = rowsAxis?.tracks,
                implicitRows = rowsAxis?.implicitTrack,
                implicitRowMin = rowsAxis?.min,
            )
        }

        private fun styleTypedOrNull(): StylePatch? {
            if (!styleTouched && !fillsTouched && !effectsTouched) return null
            return stylePatch.copy(
                radius = combinedRadius(),
                fills = if (fillsTouched) fillsTyped.toList() else null,
                strokes = combinedStrokes(),
                effects = if (effectsTouched) effectsTyped.toList() else null,
            )
        }

        private fun combinedRadius(): DesignCornerRadius? {
            if (radiusCorners == null && cornerSmoothing == null) return null
            return (radiusCorners ?: DesignCornerRadius()).copy(smoothing = cornerSmoothing ?: 0.0)
        }

        /** Composes the flat + record stroke phrases like `readStrokes` hoisted attributes. */
        private fun combinedStrokes(): DesignStrokes? {
            val flat = strokeFlat
            val complex = strokeComplex
            if (flat == null && complex == null) return null
            return DesignStrokes(
                paints = listOfNotNull(flat?.paint) + (complex?.paints ?: emptyList()),
                weight = complex?.weight ?: flat?.weight ?: 1.0.bindable(),
                align = complex?.align ?: flat?.align ?: StrokeAlign.Inside,
                dashPattern = complex?.dash ?: emptyList(),
                cap = complex?.cap ?: "butt",
                join = complex?.join ?: "miter",
                weightPerSide = complex?.weightPerSide,
            )
        }

        private fun textTypedOrNull(includeSpans: Boolean): TextPatch? {
            val hasSpans = includeSpans && spansTyped.isNotEmpty()
            if (!typographyTouched && !textTouched && !hasSpans) return null
            val lines = maxLines
            return textPatch.copy(
                typography = if (typographyTouched) typographyStyle else null,
                truncate = when {
                    truncateOverflow -> TextTruncate(lines ?: 1)
                    lines != null -> TextTruncate(lines, ellipsis = false)
                    else -> null
                },
                spans = if (hasSpans) spansTyped.toList() else null,
            )
        }

        private fun mediaTypedOrNull(): MediaPatch? = if (mediaTouched) mediaPatch else null

        private fun vectorTypedOrNull(): VectorPatch? = if (vectorTouched) vectorPatch else null

        private fun maskTypedOrNull(): MaskPatch? = if (maskTouched) maskPatch else null

        private fun componentTypedOrNull(): ComponentPatch? =
            if (!componentTouched) {
                null
            } else {
                componentPatch.copy(
                    variantsAxes = componentAxes.toMap().takeIf { it.isNotEmpty() },
                    properties = componentProperties.toMap().takeIf { it.isNotEmpty() },
                )
            }

        private fun overridesTypedOrNull(): OverridesPatch? {
            if (slotOverridesTyped.isEmpty() && setOverridesTyped.isEmpty() && nestedOverridesTyped.isEmpty()) {
                return null
            }
            return OverridesPatch(
                slots = slotOverridesTyped.toMap().takeIf { it.isNotEmpty() },
                sets = setOverridesTyped.toList().takeIf { it.isNotEmpty() },
                nestedInstances = nestedOverridesTyped.toMap().takeIf { it.isNotEmpty() },
            )
        }

        /** `settings` win over `enabled: false` when both were authored (old emission order). */
        private fun exportTypedOrNull(): ExportPatch? = when {
            exportSettingsTouched -> ExportPatch(enabled = null, settings = exportSettings.toList())
            exportDisabled -> ExportPatch(enabled = false)
            else -> null
        }

        private fun handoffTypedOrNull(): HandoffPatch? =
            if (!handoffTouched) {
                null
            } else {
                HandoffPatch(
                    DesignHandoff(
                        annotations = annotationsTyped.toList(),
                        measurements = measurementsTyped.toList(),
                        code = codeHintTyped,
                    ),
                )
            }

        fun toEntries(line: Int): List<DirectPatchEntry> {
            val span = SlmSourceSpan(line, line)
            return buildList {
                nodeTypedOrNull()?.let { add(DirectPatchEntry("node", it, span)) }
                shapeTypedOrNull()?.let { add(DirectPatchEntry("shape", it, span)) }
                mediaTypedOrNull()?.let { add(DirectPatchEntry("media", it, span)) }
                vectorTypedOrNull()?.let { add(DirectPatchEntry("vector", it, span)) }
                maskTypedOrNull()?.let { add(DirectPatchEntry("mask", it, span)) }
                layoutTypedOrNull()?.let { add(DirectPatchEntry("layout", it, span)) }
                styleTypedOrNull()?.let { add(DirectPatchEntry("style", it, span)) }
                textTypedOrNull(includeSpans = true)?.let { add(DirectPatchEntry("text", it, span)) }
                if (responsiveVariantsTyped.isNotEmpty()) {
                    add(DirectPatchEntry("responsive", ResponsivePatch(responsiveVariantsTyped.toList()), span))
                }
                exportTypedOrNull()?.let { add(DirectPatchEntry("export", it, span)) }
                handoffTypedOrNull()?.let { add(DirectPatchEntry("handoff", it, span)) }
                interactionsTyped.forEach { add(DirectPatchEntry("interaction", it, span)) }
                motionPatch?.let { add(DirectPatchEntry("motion", it, span)) }
                componentTypedOrNull()?.let { add(DirectPatchEntry("component", it, span)) }
                overridesTypedOrNull()?.let { add(DirectPatchEntry("overrides", it, span)) }
            }
        }
    }
}
