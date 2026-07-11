package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.blocks.StylesPatch
import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.blocks.VariablesPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.CnlElementBlock
import io.aequicor.visualization.engine.frontend.markdown.DirectPatchEntry
import io.aequicor.visualization.engine.frontend.markdown.ExpressionRun
import io.aequicor.visualization.engine.frontend.markdown.HeadingBlock
import io.aequicor.visualization.engine.frontend.markdown.HtmlCommentBlock
import io.aequicor.visualization.engine.frontend.markdown.LinkRun
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.ParagraphBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmInline
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TextRun
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue

/** Document-scoped CNL sections: shared styles, variable collections and prototype variables. */
internal object CnlDocumentSections {
    private val collectionHeading = Regex("""^Collection\s+(\S+)(?:\s+«([^»]*)»)?(?:\s+\((.*)\))?\s*$""", RegexOption.IGNORE_CASE)
    private val prototypeHeading = Regex("""^Prototype\s+Variables\s*$""", RegexOption.IGNORE_CASE)
    private val stylesHeading = Regex("""^Styles\s*$""", RegexOption.IGNORE_CASE)
    private val styleRow = Regex("""^(Paint|TextStyle|Effect|Grid)\s+(\S+)(?:\s+(.*))?\s*$""", RegexOption.IGNORE_CASE)
    private val numberRegex = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")
    private val whitespaceRegex = Regex("""\s+""")

    fun isDocumentHeading(text: String): Boolean =
        collectionHeading.matches(text.trim()) ||
            prototypeHeading.matches(text.trim()) ||
            stylesHeading.matches(text.trim())

    fun parseDocumentPatch(
        headingText: String,
        heading: HeadingBlock,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): DirectPatchEntry? {
        val text = headingText.trim()
        if (stylesHeading.matches(text)) return parseStylesPatch(heading, body, diagnostics)
        return parseVariablesPatch(headingText, heading, body, diagnostics)
    }

    fun parseVariablesPatch(
        headingText: String,
        heading: HeadingBlock,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): DirectPatchEntry? {
        val text = headingText.trim()
        val patch = when {
            collectionHeading.matches(text) -> collectionPatch(text, body, diagnostics)
            prototypeHeading.matches(text) -> prototypePatch(body, diagnostics)
            else -> return null
        } ?: return null
        val endLine = body.lastOrNull()?.span?.endLine ?: heading.span.endLine
        return DirectPatchEntry(TypedBlockKind.Variables.key, patch, SlmSourceSpan(heading.span.startLine, endLine))
    }

    private fun collectionPatch(
        headingText: String,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): VariablesPatch? {
        val match = collectionHeading.matchEntire(headingText) ?: return null
        val collectionId = match.groupValues[1]
        val collectionName = match.groupValues.getOrNull(2).orEmpty().takeIf { it.isNotBlank() }
        val options = parseCollectionOptions(match.groupValues.getOrNull(3).orEmpty())
        val variables = logicalLines(body, diagnostics, "document CNL variable section") {
            "Document CNL variable rows must not be UI element sentences; use `String`, not `Text`, for text variables"
        }.mapNotNull { line ->
            parseCollectionVariable(line, diagnostics)
        }
        if (variables.isEmpty()) {
            diagnostics.warning("Collection \"$collectionId\" has no variable rows", body.firstOrNull()?.span ?: SlmSourceSpan(1, 1))
        }
        val collections = LinkedHashMap<String, VariableCollection>()
        // The old `id:` scalar was plain-written: a bare `null` word read back as no id and the
        // reader dropped the whole collection (the `variables:` entry itself still existed).
        if (collectionId != "null") {
            // The old bare-written modes went through `stringList`: non-string-typed tokens die.
            val modes = options.modes.mapNotNull { plainListItem(it) }
            val writtenDefault = if (options.modes.isNotEmpty()) options.defaultMode ?: options.modes.first() else null
            val defaultMode = writtenDefault?.takeUnless { it == "null" } ?: modes.firstOrNull().orEmpty()
            val vars = LinkedHashMap<String, DesignVariable>()
            variables.forEach { variable ->
                vars[variable.name] = DesignVariable(
                    type = variable.type.irType,
                    values = variable.values.mapNotNull { value ->
                        variableValueOf(variable.type, value.value)?.let { value.mode to it }
                    }.toMap(),
                )
            }
            collections[collectionId] = VariableCollection(
                name = collectionName ?: collectionId,
                modes = modes,
                defaultMode = defaultMode,
                vars = vars,
            )
        }
        return VariablesPatch(collections = collections)
    }

    private fun prototypePatch(
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): VariablesPatch {
        val variables = logicalLines(body, diagnostics, "document CNL variable section") {
            "Document CNL variable rows must not be UI element sentences; use `String`, not `Text`, for text variables"
        }.mapNotNull { line ->
            parsePrototypeVariable(line, diagnostics)
        }
        if (variables.isEmpty()) {
            diagnostics.warning("Prototype Variables section has no variable rows", body.firstOrNull()?.span ?: SlmSourceSpan(1, 1))
        }
        val prototype = LinkedHashMap<String, PrototypeVariable>()
        variables.forEach { variable ->
            prototype[variable.name] = PrototypeVariable(
                type = variable.type.irType,
                default = variable.default?.let { variableValueOf(variable.type, it) },
            )
        }
        // An empty section mirrors the old empty `prototype:` scalar → no prototype map.
        return VariablesPatch(prototype = prototype.takeIf { it.isNotEmpty() })
    }

    /**
     * One typed per-mode value (mirrors the old `yamlValue` writer + `readVariableValue`):
     * `$ref` → alias for any type; otherwise the type decides — invalid values are dropped.
     */
    private fun variableValueOf(type: CnlVariableType, value: String): VariableValue? {
        if (value.startsWith("$")) return VariableValue.Alias(value.drop(1))
        return when (type) {
            CnlVariableType.Color -> DesignColor.fromHex(value)?.let { VariableValue.ColorValue(it) }
            CnlVariableType.Number ->
                if (numberRegex.matches(value)) VariableValue.NumberValue(value.toDouble()) else null
            CnlVariableType.String -> VariableValue.TextValue(value)
            CnlVariableType.Boolean -> when (value.lowercase()) {
                "yes", "true" -> VariableValue.BoolValue(true)
                "no", "false" -> VariableValue.BoolValue(false)
                else -> null
            }
        }
    }

    /** A plain-written list item read back through `stringList`: non-string-typed bare tokens die. */
    private fun plainListItem(token: String): String? =
        if (token.matches(plainScalarRegex) &&
            (token == "null" || token == "true" || token == "false" || numberRegex.matches(token))
        ) {
            null
        } else {
            token
        }

    private data class CollectionOptions(val modes: List<String>, val defaultMode: String?)

    private fun parseCollectionOptions(raw: String): CollectionOptions {
        if (raw.isBlank()) return CollectionOptions(emptyList(), null)
        val tokens = raw.trim().split(whitespaceRegex).filter { it.isNotEmpty() }
        val modes = mutableListOf<String>()
        var defaultMode: String? = null
        var index = 0
        while (index < tokens.size) {
            when (tokens[index].lowercase()) {
                "mode", "modes" -> {
                    index++
                    while (index < tokens.size && tokens[index].lowercase() != "default") {
                        modes += tokens[index]
                        index++
                    }
                }
                "default" -> {
                    defaultMode = tokens.getOrNull(index + 1)
                    index += 2
                }
                else -> index++
            }
        }
        return CollectionOptions(modes, defaultMode)
    }

    private data class LogicalLine(val text: String, val line: Int)

    private fun logicalLines(
        blocks: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
        sectionName: String,
        cnlElementMessage: () -> String,
    ): List<LogicalLine> =
        blocks.flatMap { block ->
            when (block) {
                is ParagraphBlock -> paragraphLines(block)
                is HtmlCommentBlock -> emptyList()
                is CnlElementBlock -> {
                    diagnostics.warning(cnlElementMessage(), block.span)
                    emptyList()
                }
                is HeadingBlock -> emptyList()
                is ListBlock, is BlockquoteBlock -> {
                    diagnostics.warning("$sectionName uses one row per line", block.span)
                    emptyList()
                }
                else -> {
                    diagnostics.warning("Unsupported block inside $sectionName", block.span)
                    emptyList()
                }
            }
        }

    private fun paragraphLines(block: ParagraphBlock): List<LogicalLine> =
        block.inlines
            .groupBy { it.line }
            .entries
            .sortedBy { it.key }
            .mapNotNull { (line, inlines) ->
                val text = inlines.sortedBy { it.column }.joinToString("") { renderInline(it) }.trim()
                text.takeIf { it.isNotEmpty() }?.let { LogicalLine(it, line) }
            }

    private fun renderInline(inline: SlmInline): String = when (inline) {
        is TextRun -> inline.text
        is ExpressionRun -> "{{${inline.raw}}}"
        is LinkRun -> "[${inline.label.joinToString("") { renderInline(it) }}](${inline.target})"
        else -> ""
    }

    private data class SharedStyleRow(val id: String, val style: DesignStyle?, val line: Int)

    private fun parseStylesPatch(
        heading: HeadingBlock,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): DirectPatchEntry {
        // The row map mirrors the old YAML-map semantics: last definition per id wins (a later
        // INVALID row still overwrites an earlier valid one), positions keep first occurrence.
        val rows = linkedMapOf<String, DesignStyle?>()
        logicalLines(body, diagnostics, "document CNL styles section") {
            "Document CNL style rows must use `Paint`, `TextStyle`, `Effect` or `Grid`, not UI element sentences"
        }.mapNotNull { line ->
            parseSharedStyle(line, diagnostics)
        }.forEach { row ->
            if (rows.containsKey(row.id)) {
                diagnostics.warning("Shared style \"${row.id}\" is defined more than once; last definition wins", row.line, blockPath = "styles")
            }
            rows[row.id] = row.style
        }
        if (rows.isEmpty()) {
            diagnostics.warning("Styles section has no style rows", body.firstOrNull()?.span ?: heading.span)
        }
        val styles = linkedMapOf<String, DesignStyle>()
        rows.forEach { (id, style) -> style?.let { styles[id] = it } }
        val endLine = body.lastOrNull()?.span?.endLine ?: heading.span.endLine
        return DirectPatchEntry(
            TypedBlockKind.Styles.key,
            StylesPatch(styles),
            SlmSourceSpan(heading.span.startLine, endLine),
        )
    }

    private fun parseSharedStyle(line: LogicalLine, diagnostics: DiagnosticCollector): SharedStyleRow? {
        val match = styleRow.matchEntire(line.text) ?: run {
            diagnostics.warning("Unknown shared style row \"${line.text}\"", line.line, blockPath = "styles")
            return null
        }
        val kind = match.groupValues[1].lowercase()
        val id = match.groupValues[2]
        val rest = match.groupValues.getOrNull(3).orEmpty().trim()
        if (rest.isBlank()) {
            diagnostics.warning("Shared style \"$id\" needs CNL properties", line.line, blockPath = "styles")
            return null
        }
        val lowered = lowerStyleRow(kind, rest, line.line, diagnostics) ?: return null
        // Mirrors StylesBlockReader: a row whose lowered patch lacks the required body is kept
        // for duplicate detection but contributes no style (warning texts match the reader's).
        val style = when (kind) {
            "paint" -> (lowered as? StylePatch)?.fills?.let { DesignStyle.Paint(it) } ?: run {
                diagnostics.warning("Paint style \"$id\" needs fill/color CNL", line.line, blockPath = "styles")
                null
            }
            "textstyle" -> (lowered as? TextPatch)?.typography?.let { DesignStyle.Text(it) } ?: run {
                diagnostics.warning("Text style \"$id\" needs typography CNL", line.line, blockPath = "styles")
                null
            }
            "effect" -> (lowered as? StylePatch)?.effects?.let { DesignStyle.Effect(it) } ?: run {
                diagnostics.warning("Effect style \"$id\" needs effect CNL", line.line, blockPath = "styles")
                null
            }
            else -> (lowered as? LayoutPatch)?.grids?.let { DesignStyle.Grid(it) } ?: run {
                diagnostics.warning("Grid style \"$id\" needs grids CNL", line.line, blockPath = "styles")
                null
            }
        }
        return SharedStyleRow(id, style, line.line)
    }

    /** Lowers one shared-style row's CNL property suffix to its typed block patch. */
    private fun lowerStyleRow(
        kind: String,
        rest: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): TypedPatch? {
        val entries = when (kind) {
            "textstyle" -> {
                val element = CnlParser.parseElement("Text «style» $rest", line, 1, diagnostics) ?: run {
                    diagnostics.warning("TextStyle row could not be parsed as CNL", line, blockPath = "styles")
                    return null
                }
                CnlParser.desugar(element, line, diagnostics)
            }
            else -> {
                val element = CnlParser.parseHeading("Style $rest", line, 1, diagnostics)?.element ?: run {
                    diagnostics.warning("Shared style row could not be parsed as CNL", line, blockPath = "styles")
                    return null
                }
                CnlParser.desugar(element, line, diagnostics)
            }
        }
        val blockKind = when (kind) {
            "paint", "effect" -> TypedBlockKind.Style
            "textstyle" -> TypedBlockKind.Text
            "grid" -> TypedBlockKind.Layout
            else -> null
        }
        val entry = entries.firstOrNull { it.kind == blockKind }
        if (entry == null) diagnostics.warning("Shared style row has no ${blockKind?.key ?: "known"} CNL properties", line, blockPath = "styles")
        return entry?.patch
    }

    private data class VariableRow(val type: CnlVariableType, val name: String, val values: List<ModeValue>)
    private data class PrototypeRow(val type: CnlVariableType, val name: String, val default: String?)
    private data class ModeValue(val mode: String, val value: String)

    private enum class CnlVariableType(val irType: VariableType, vararg val words: String) {
        Color(VariableType.Color, "color"),
        Number(VariableType.Number, "number"),
        String(VariableType.Text, "string", "text"),
        Boolean(VariableType.Bool, "boolean", "bool"),
        ;

        companion object {
            fun of(word: kotlin.String): CnlVariableType? =
                entries.firstOrNull { type -> word.lowercase() in type.words }
        }
    }

    private fun parseCollectionVariable(line: LogicalLine, diagnostics: DiagnosticCollector): VariableRow? {
        val tokens = tokenize(line.text, line.line, diagnostics)
        if (tokens.isEmpty()) return null
        val type = CnlVariableType.of(tokens[0]) ?: run {
            diagnostics.warning("Unknown variable type \"${tokens[0]}\"", line.line, blockPath = "variables")
            return null
        }
        val name = tokens.getOrNull(1) ?: run {
            diagnostics.warning("Variable row needs a name", line.line, blockPath = "variables")
            return null
        }
        val values = mutableListOf<ModeValue>()
        var index = 2
        while (index < tokens.size) {
            val mode = tokens[index]
            val value = tokens.getOrNull(index + 1) ?: run {
                diagnostics.warning("Variable \"$name\" mode \"$mode\" needs a value", line.line, blockPath = "variables")
                return@run null
            } ?: break
            values += ModeValue(mode, value)
            index += 2
        }
        if (values.isEmpty()) {
            diagnostics.warning("Variable \"$name\" needs at least one mode value", line.line, blockPath = "variables")
            return null
        }
        return VariableRow(type, name, values)
    }

    private fun parsePrototypeVariable(line: LogicalLine, diagnostics: DiagnosticCollector): PrototypeRow? {
        val tokens = tokenize(line.text, line.line, diagnostics)
        if (tokens.isEmpty()) return null
        val type = CnlVariableType.of(tokens[0]) ?: run {
            diagnostics.warning("Unknown prototype variable type \"${tokens[0]}\"", line.line, blockPath = "variables")
            return null
        }
        val name = tokens.getOrNull(1) ?: run {
            diagnostics.warning("Prototype variable row needs a name", line.line, blockPath = "variables")
            return null
        }
        val valueIndex = if (tokens.getOrNull(2)?.equals("default", ignoreCase = true) == true) 3 else 2
        return PrototypeRow(type, name, tokens.getOrNull(valueIndex))
    }

    private fun tokenize(text: String, line: Int, diagnostics: DiagnosticCollector): List<String> {
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length) break
            val char = text[index]
            when (char) {
                '«' -> {
                    val scan = CnlGrammar.scanTextLiteral(text, index + 1, '»')
                    if (!scan.terminated) {
                        diagnostics.warning("Unterminated text literal in variable row", line, blockPath = "variables")
                    }
                    tokens += scan.text
                    index = if (scan.terminated) scan.closeIndex + 1 else text.length
                }
                '"', '\'' -> {
                    val scan = CnlGrammar.scanTextLiteral(text, index + 1, char)
                    if (!scan.terminated) {
                        diagnostics.warning("Unterminated quoted value in variable row", line, blockPath = "variables")
                    }
                    tokens += scan.text
                    index = if (scan.terminated) scan.closeIndex + 1 else text.length
                }
                else -> {
                    val start = index
                    while (index < text.length && !text[index].isWhitespace()) index++
                    tokens += text.substring(start, index)
                }
            }
        }
        return tokens
    }

    /** The old yamlPlain writer's bare-safe set — used to mirror its `stringList` read-back. */
    private val plainScalarRegex = Regex("""[A-Za-z0-9_.-]+""")
}
