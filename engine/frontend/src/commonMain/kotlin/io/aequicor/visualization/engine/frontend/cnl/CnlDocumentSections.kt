package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.CnlElementBlock
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
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.frontend.yaml.parseSlmYaml

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
    ): TypedEntry? {
        val text = headingText.trim()
        if (stylesHeading.matches(text)) return parseStylesPatch(heading, body, diagnostics)
        return parseVariablesPatch(headingText, heading, body, diagnostics)
    }

    fun parseVariablesPatch(
        headingText: String,
        heading: HeadingBlock,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): TypedEntry? {
        val text = headingText.trim()
        val yaml = when {
            collectionHeading.matches(text) -> collectionYaml(text, body, diagnostics)
            prototypeHeading.matches(text) -> prototypeYaml(body, diagnostics)
            else -> return null
        } ?: return null
        val parsed = parseSlmYaml(yaml, diagnostics, heading.span.startLine)
        val value = (parsed as? YamlMap)?.entries?.get("variables") ?: return null
        val endLine = body.lastOrNull()?.span?.endLine ?: heading.span.endLine
        return TypedEntry(TypedBlockKind.Variables, value, SlmSourceSpan(heading.span.startLine, endLine))
    }

    private fun collectionYaml(
        headingText: String,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): String? {
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
        return buildString {
            appendLine("variables:")
            appendLine("  collections:")
            appendLine("    - id: ${yamlPlain(collectionId)}")
            collectionName?.let { appendLine("      name: ${yamlString(it)}") }
            if (options.modes.isNotEmpty()) {
                appendLine("      modes: [${options.modes.joinToString(", ") { yamlPlain(it) }}]")
                appendLine("      defaultMode: ${yamlPlain(options.defaultMode ?: options.modes.first())}")
            }
            appendLine("      variables:")
            variables.forEach { variable ->
                appendLine("        ${yamlPlain(variable.name)}:")
                appendLine("          type: ${variable.type.yamlType}")
                appendLine("          values:")
                variable.values.forEach { value ->
                    appendLine("            ${yamlPlain(value.mode)}: ${yamlValue(variable.type, value.value)}")
                }
            }
        }
    }

    private fun prototypeYaml(
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): String? {
        val variables = logicalLines(body, diagnostics, "document CNL variable section") {
            "Document CNL variable rows must not be UI element sentences; use `String`, not `Text`, for text variables"
        }.mapNotNull { line ->
            parsePrototypeVariable(line, diagnostics)
        }
        if (variables.isEmpty()) {
            diagnostics.warning("Prototype Variables section has no variable rows", body.firstOrNull()?.span ?: SlmSourceSpan(1, 1))
        }
        return buildString {
            appendLine("variables:")
            appendLine("  prototype:")
            variables.forEach { variable ->
                appendLine("    ${yamlPlain(variable.name)}:")
                appendLine("      type: ${variable.type.yamlType}")
                variable.default?.let { appendLine("      default: ${yamlValue(variable.type, it)}") }
            }
        }
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
            .toSortedMap()
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

    private data class SharedStyleRow(val id: String, val value: YamlValue)

    private fun parseStylesPatch(
        heading: HeadingBlock,
        body: List<SlmBlock>,
        diagnostics: DiagnosticCollector,
    ): TypedEntry? {
        val styles = linkedMapOf<String, YamlValue>()
        logicalLines(body, diagnostics, "document CNL styles section") {
            "Document CNL style rows must use `Paint`, `TextStyle`, `Effect` or `Grid`, not UI element sentences"
        }.mapNotNull { line ->
            parseSharedStyle(line, diagnostics)
        }.forEach { row ->
            if (styles.containsKey(row.id)) {
                diagnostics.warning("Shared style \"${row.id}\" is defined more than once; last definition wins", row.value.line, blockPath = "styles")
            }
            styles[row.id] = row.value
        }
        if (styles.isEmpty()) {
            diagnostics.warning("Styles section has no style rows", body.firstOrNull()?.span ?: heading.span)
        }
        val endLine = body.lastOrNull()?.span?.endLine ?: heading.span.endLine
        return TypedEntry(
            TypedBlockKind.Styles,
            yamlMap(styles, heading.span.startLine),
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
        return SharedStyleRow(id, styleSpec(kind, lowered, line.line))
    }

    private fun lowerStyleRow(
        kind: String,
        rest: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): TypedEntry? {
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
        return entry
    }

    private fun styleSpec(kind: String, lowered: TypedEntry, line: Int): YamlMap {
        val type = when (kind) {
            "paint" -> "paint"
            "textstyle" -> "text"
            "effect" -> "effect"
            "grid" -> "grid"
            else -> kind
        }
        val bodyKey = when (kind) {
            "textstyle" -> "text"
            "grid" -> "layout"
            else -> "style"
        }
        return yamlMap(
            linkedMapOf(
                "type" to yamlScalar(type, line),
                bodyKey to lowered.value,
            ),
            line,
        )
    }

    private fun yamlScalar(value: String, line: Int): YamlScalar =
        YamlScalar(value, value, line, 1)

    private fun yamlMap(entries: Map<String, YamlValue>, line: Int): YamlMap =
        YamlMap(
            entries = entries,
            line = line,
            column = 1,
            endLine = entries.values.maxOfOrNull { it.endLine } ?: line,
            endColumn = entries.values.maxOfOrNull { it.endColumn } ?: 1,
        )

    private data class VariableRow(val type: CnlVariableType, val name: String, val values: List<ModeValue>)
    private data class PrototypeRow(val type: CnlVariableType, val name: String, val default: String?)
    private data class ModeValue(val mode: String, val value: String)

    private enum class CnlVariableType(val yamlType: String, vararg val words: String) {
        Color("color", "color"),
        Number("number", "number"),
        String("string", "string", "text"),
        Boolean("boolean", "boolean", "bool"),
        ;

        companion object {
            fun of(word: String): CnlVariableType? =
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

    private fun yamlValue(type: CnlVariableType, value: String): String {
        if (value.startsWith("$")) return value
        return when (type) {
            CnlVariableType.Color -> yamlString(value)
            CnlVariableType.Number -> if (numberRegex.matches(value)) value else yamlString(value)
            CnlVariableType.String -> yamlString(value)
            CnlVariableType.Boolean -> when (value.lowercase()) {
                "yes", "true" -> "true"
                "no", "false" -> "false"
                else -> yamlString(value)
            }
        }
    }

    private val plainScalarRegex = Regex("""[A-Za-z0-9_.-]+""")

    private fun yamlPlain(value: String): String =
        if (value.matches(plainScalarRegex)) value else yamlString(value)

    private fun yamlString(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
}
