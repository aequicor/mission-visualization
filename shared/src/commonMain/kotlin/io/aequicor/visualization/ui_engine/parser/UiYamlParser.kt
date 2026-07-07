package io.aequicor.visualization.ui_engine.parser

import io.aequicor.visualization.ui_engine.ui_document_ir.SourceSpan
import io.aequicor.visualization.ui_engine.ui_document_ir.UiAction
import io.aequicor.visualization.ui_engine.ui_document_ir.UiComment
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnostic
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnosticSeverity
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument
import io.aequicor.visualization.ui_engine.ui_document_ir.UiLayout
import io.aequicor.visualization.ui_engine.ui_document_ir.UiNode
import io.aequicor.visualization.ui_engine.ui_document_ir.UiPrompt
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScenario
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScenarioStep
import io.aequicor.visualization.ui_engine.ui_document_ir.UiScreen
import io.aequicor.visualization.ui_engine.ui_document_ir.UiStyle
import io.aequicor.visualization.ui_engine.ui_document_ir.UiTheme
import io.aequicor.visualization.ui_engine.ui_document_ir.UiValue
import io.aequicor.visualization.ui_engine.ui_document_ir.stableUiId

fun parseUiDocumentYaml(source: String): UiParseResult =
    try {
        val root = ConstrainedUiYamlParser(source).parse()
        val diagnostics = mutableListOf<UiDiagnostic>()
        val document = UiDocumentReader(diagnostics).read(root)
        UiParseResult.Success(document, diagnostics)
    } catch (error: UiYamlException) {
        UiParseResult.Failure(
            listOf(
                UiDiagnostic(
                    severity = UiDiagnosticSeverity.Error,
                    message = error.reason,
                    source = SourceSpan(error.line, error.column),
                ),
            ),
        )
    }

private class UiYamlException(
    val line: Int,
    val column: Int,
    val reason: String,
) : RuntimeException(reason)

private sealed interface YamlNode {
    val line: Int
    val column: Int
}

private data class YamlMap(
    val entries: Map<String, YamlNode>,
    override val line: Int,
    override val column: Int,
) : YamlNode

private data class YamlList(
    val items: List<YamlNode>,
    override val line: Int,
    override val column: Int,
) : YamlNode

private data class YamlScalar(
    val value: Any?,
    override val line: Int,
    override val column: Int,
) : YamlNode

private data class ParsedNode(
    val node: YamlNode,
    val nextIndex: Int,
)

private class ConstrainedUiYamlParser(
    source: String,
) {
    private val lines = source.normalizedLines()

    fun parse(): YamlNode {
        val start = nextContentIndex(0)
            ?: fail(1, 1, "UI document is empty.")
        val parsed = parseBlock(start, indentOf(start))
        val trailing = nextContentIndex(parsed.nextIndex)
        if (trailing != null) {
            fail(lineNumber(trailing), columnNumber(trailing), "Unexpected trailing content.")
        }
        return parsed.node
    }

    private fun parseBlock(startIndex: Int, indent: Int): ParsedNode {
        val currentIndent = indentOf(startIndex)
        if (currentIndent < indent) {
            fail(lineNumber(startIndex), columnNumber(startIndex), "Expected indentation of at least $indent spaces.")
        }
        val trimmed = contentAt(startIndex)
        return if (trimmed.startsWith("-")) {
            parseList(startIndex, currentIndent)
        } else {
            parseMap(startIndex, currentIndent)
        }
    }

    private fun parseMap(startIndex: Int, indent: Int): ParsedNode {
        val entries = linkedMapOf<String, YamlNode>()
        var index = startIndex
        while (index < lines.size) {
            val contentIndex = nextContentIndex(index) ?: return ParsedNode(YamlMap(entries, lineNumber(startIndex), columnNumber(startIndex)), lines.size)
            val currentIndent = indentOf(contentIndex)
            if (currentIndent < indent) return ParsedNode(YamlMap(entries, lineNumber(startIndex), columnNumber(startIndex)), contentIndex)
            if (currentIndent > indent) fail(lineNumber(contentIndex), columnNumber(contentIndex), "Unexpected indentation.")

            val content = contentAt(contentIndex)
            if (content.startsWith("-")) {
                fail(lineNumber(contentIndex), columnNumber(contentIndex), "Unexpected list item inside a map.")
            }

            val entry = parseMapEntry(content, lineNumber(contentIndex), columnNumber(contentIndex))
            if (entries.containsKey(entry.key)) {
                fail(lineNumber(contentIndex), columnNumber(contentIndex), "Duplicate key '${entry.key}'.")
            }
            val value = parseEntryValue(entry, contentIndex, currentIndent)
            entries[entry.key] = value.node
            index = value.nextIndex
        }
        return ParsedNode(YamlMap(entries, lineNumber(startIndex), columnNumber(startIndex)), index)
    }

    private fun parseList(startIndex: Int, indent: Int): ParsedNode {
        val items = mutableListOf<YamlNode>()
        var index = startIndex
        while (index < lines.size) {
            val contentIndex = nextContentIndex(index) ?: return ParsedNode(YamlList(items, lineNumber(startIndex), columnNumber(startIndex)), lines.size)
            val currentIndent = indentOf(contentIndex)
            if (currentIndent < indent) return ParsedNode(YamlList(items, lineNumber(startIndex), columnNumber(startIndex)), contentIndex)
            if (currentIndent > indent) fail(lineNumber(contentIndex), columnNumber(contentIndex), "Unexpected indentation inside a list.")

            val content = contentAt(contentIndex)
            if (!content.startsWith("-")) {
                return ParsedNode(YamlList(items, lineNumber(startIndex), columnNumber(startIndex)), contentIndex)
            }

            val itemContent = content.drop(1).trimStart()
            if (itemContent.isEmpty()) {
                val childIndex = nextContentIndex(contentIndex + 1)
                    ?: fail(lineNumber(contentIndex), columnNumber(contentIndex), "List item needs a value.")
                if (indentOf(childIndex) <= currentIndent) {
                    fail(lineNumber(childIndex), columnNumber(childIndex), "Nested list item content must be indented.")
                }
                val nested = parseBlock(childIndex, indentOf(childIndex))
                items += nested.node
                index = nested.nextIndex
                continue
            }

            if (looksLikeMapEntry(itemContent)) {
                val parsedMapItem = parseListMapItem(itemContent, contentIndex, currentIndent)
                items += parsedMapItem.node
                index = parsedMapItem.nextIndex
            } else {
                items += parseScalarOrInline(itemContent, lineNumber(contentIndex), columnNumber(contentIndex) + 2)
                val nextIndex = nextContentIndex(contentIndex + 1)
                if (nextIndex != null && indentOf(nextIndex) > currentIndent) {
                    fail(lineNumber(nextIndex), columnNumber(nextIndex), "Scalar list items cannot have nested content.")
                }
                index = contentIndex + 1
            }
        }
        return ParsedNode(YamlList(items, lineNumber(startIndex), columnNumber(startIndex)), index)
    }

    private fun parseListMapItem(itemContent: String, itemIndex: Int, listIndent: Int): ParsedNode {
        val entries = linkedMapOf<String, YamlNode>()
        val entry = parseMapEntry(itemContent, lineNumber(itemIndex), columnNumber(itemIndex) + 2)
        val firstValue = parseEntryValue(entry, itemIndex, listIndent)
        entries[entry.key] = firstValue.node

        var index = firstValue.nextIndex
        while (true) {
            val continuationIndex = nextContentIndex(index) ?: break
            val continuationIndent = indentOf(continuationIndex)
            if (continuationIndent <= listIndent) break

            val continuation = parseBlock(continuationIndex, continuationIndent)
            val continuationMap = continuation.node as? YamlMap
                ?: fail(lineNumber(continuationIndex), columnNumber(continuationIndex), "List map item continuation must be a map.")
            for ((key, value) in continuationMap.entries) {
                if (entries.containsKey(key)) {
                    fail(value.line, value.column, "Duplicate key '$key'.")
                }
                entries[key] = value
            }
            index = continuation.nextIndex
        }

        return ParsedNode(YamlMap(entries, lineNumber(itemIndex), columnNumber(itemIndex)), index)
    }

    private fun parseEntryValue(entry: MapEntry, index: Int, indent: Int): ParsedNode {
        if (entry.valueText.isNotEmpty()) {
            return ParsedNode(parseScalarOrInline(entry.valueText, lineNumber(index), entry.valueColumn), index + 1)
        }

        val childIndex = nextContentIndex(index + 1)
        if (childIndex == null || indentOf(childIndex) <= indent) {
            return ParsedNode(YamlScalar(null, lineNumber(index), entry.valueColumn), index + 1)
        }
        return parseBlock(childIndex, indentOf(childIndex))
    }

    private fun parseScalarOrInline(text: String, line: Int, column: Int): YamlNode {
        if (text == "|" || text == ">") {
            fail(line, column, "Multiline YAML scalars are not supported in the UI DSL subset.")
        }
        if (text.startsWith("&") || text.startsWith("*") || text.startsWith("!")) {
            fail(line, column, "YAML anchors, aliases, and tags are not supported in the UI DSL subset.")
        }
        if (text == "[]") return YamlList(emptyList(), line, column)
        if (text == "{}") return YamlMap(emptyMap(), line, column)
        if (text.startsWith("[") || text.startsWith("{")) {
            if (!text.endsWith(if (text.startsWith("[")) "]" else "}")) {
                fail(line, column, "Inline YAML values must be closed on one line.")
            }
            return if (text.startsWith("[")) parseInlineList(text, line, column) else parseInlineMap(text, line, column)
        }

        val scalarValue: Any? = when {
            text == "null" || text == "~" -> null
            text == "true" -> true
            text == "false" -> false
            text.startsWith("\"") || text.startsWith("'") -> parseQuoted(text, line, column)
            text.toIntOrNull() != null -> text.toInt()
            text.contains(".") && text.toDoubleOrNull() != null -> text.toDouble()
            else -> text
        }
        return YamlScalar(scalarValue, line, column)
    }

    private fun parseInlineList(text: String, line: Int, column: Int): YamlList {
        val inner = text.removePrefix("[").removeSuffix("]").trim()
        if (inner.isEmpty()) return YamlList(emptyList(), line, column)
        val items = splitInline(inner).map { parseScalarOrInline(it.trim(), line, column) }
        return YamlList(items, line, column)
    }

    private fun parseInlineMap(text: String, line: Int, column: Int): YamlMap {
        val inner = text.removePrefix("{").removeSuffix("}").trim()
        if (inner.isEmpty()) return YamlMap(emptyMap(), line, column)
        val entries = linkedMapOf<String, YamlNode>()
        for (part in splitInline(inner)) {
            val entry = parseMapEntry(part.trim(), line, column)
            entries[entry.key] = parseScalarOrInline(entry.valueText, line, entry.valueColumn)
        }
        return YamlMap(entries, line, column)
    }

    private fun splitInline(text: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var depth = 0
        for (char in text) {
            when {
                quote != null -> {
                    current.append(char)
                    if (char == quote) quote = null
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    current.append(char)
                }
                char == '[' || char == '{' -> {
                    depth++
                    current.append(char)
                }
                char == ']' || char == '}' -> {
                    depth--
                    current.append(char)
                }
                char == ',' && depth == 0 -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }

    private fun parseQuoted(text: String, line: Int, column: Int): String {
        val quote = text.first()
        if (text.length < 2 || text.last() != quote) {
            fail(line, column, "Quoted strings must close on the same line.")
        }
        val raw = text.substring(1, text.lastIndex)
        if (quote == '\'') return raw

        val result = StringBuilder()
        var escaping = false
        for (char in raw) {
            if (escaping) {
                result.append(
                    when (char) {
                        'n' -> '\n'
                        '"' -> '"'
                        '\\' -> '\\'
                        else -> char
                    },
                )
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else {
                result.append(char)
            }
        }
        if (escaping) result.append('\\')
        return result.toString()
    }

    private fun parseMapEntry(content: String, line: Int, column: Int): MapEntry {
        val separatorIndex = content.indexOf(':')
        if (separatorIndex <= 0) fail(line, column, "Expected a 'key: value' entry.")
        val key = content.substring(0, separatorIndex).trim()
        if (key.isEmpty()) fail(line, column, "Map keys cannot be empty.")
        if (key.startsWith("!") || key.startsWith("&") || key.startsWith("*")) {
            fail(line, column, "YAML anchors, aliases, and tags are not supported in keys.")
        }
        val value = content.substring(separatorIndex + 1).trimStart()
        val valueColumn = column + separatorIndex + 2 + content.substring(separatorIndex + 1).takeWhile { it == ' ' }.length
        return MapEntry(key, value, valueColumn)
    }

    private fun looksLikeMapEntry(content: String): Boolean {
        val separatorIndex = content.indexOf(':')
        return separatorIndex > 0 && !content.take(separatorIndex).any { it.isWhitespace() }
    }

    private fun nextContentIndex(startIndex: Int): Int? {
        var index = startIndex
        while (index < lines.size) {
            val raw = lines[index]
            if (raw.indexOf('\t') >= 0 && raw.takeWhile { it == ' ' || it == '\t' }.contains('\t')) {
                fail(lineNumber(index), columnNumber(index), "Tabs are not supported for indentation.")
            }
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) return index
            index++
        }
        return null
    }

    private fun contentAt(index: Int): String =
        lines[index].trimEnd().drop(indentOf(index))

    private fun indentOf(index: Int): Int =
        lines[index].takeWhile { it == ' ' }.length

    private fun lineNumber(index: Int): Int =
        index + 1

    private fun columnNumber(index: Int): Int =
        indentOf(index) + 1

    private fun fail(line: Int, column: Int, message: String): Nothing =
        throw UiYamlException(line, column, message)

    private data class MapEntry(
        val key: String,
        val valueText: String,
        val valueColumn: Int,
    )
}

private class UiDocumentReader(
    private val diagnostics: MutableList<UiDiagnostic>,
) {
    fun read(root: YamlNode): UiDocument {
        val map = root.asMap("Root UI document must be a map.")
        warnUnknown(map, setOf("version", "title", "description", "metadata", "theme", "screens", "scenarios", "comments", "prompts"))
        return UiDocument(
            version = map.int("version") ?: 1,
            title = map.string("title") ?: "Untitled UI document",
            description = map.string("description") ?: "",
            metadata = map.map("metadata")?.stringMap().orEmpty(),
            theme = map.map("theme")?.let(::readTheme) ?: UiTheme(),
            screens = map.list("screens")?.items?.map(::readScreen).orEmpty(),
            scenarios = map.list("scenarios")?.items?.map(::readScenario).orEmpty(),
            comments = map.list("comments")?.items?.map(::readComment).orEmpty(),
            prompts = map.list("prompts")?.items?.map(::readPrompt).orEmpty(),
            source = root.sourceSpan(),
        )
    }

    private fun readTheme(node: YamlMap): UiTheme {
        warnUnknown(node, setOf("name", "primary", "accent", "surface", "mood", "tokens"))
        return UiTheme(
            name = node.string("name") ?: "Lazurite",
            primary = node.string("primary") ?: "#1F5FA8",
            accent = node.string("accent") ?: "#2BB8A8",
            surface = node.string("surface") ?: "#F6FAFF",
            mood = node.string("mood") ?: "precise, calm, product-focused",
            tokens = node.map("tokens")?.stringMap().orEmpty(),
        )
    }

    private fun readScreen(node: YamlNode): UiScreen {
        val map = node.asMap("Screen entries must be maps.")
        warnUnknown(map, setOf("id", "title", "description", "layout", "children"))
        val id = map.string("id") ?: ""
        return UiScreen(
            id = id,
            title = map.string("title") ?: id,
            description = map.string("description") ?: "",
            layout = map.map("layout")?.let(::readLayout) ?: UiLayout(),
            children = map.list("children")?.items?.map(::readNode).orEmpty(),
            source = node.sourceSpan(),
        )
    }

    private fun readNode(node: YamlNode): UiNode {
        val map = node.asMap("Node entries must be maps.")
        warnUnknown(
            map,
            NodeAllowedKeys + NodeShorthandPropKeys,
        )
        val props = linkedMapOf<String, UiValue>()
        map.map("props")?.entries?.forEach { (key, value) -> props[key] = value.toUiValue() }
        for (key in NodeShorthandPropKeys) {
            val value = map.entries[key] ?: continue
            if (key !in props) props[key] = value.toUiValue()
        }
        return UiNode(
            id = map.string("id") ?: "",
            type = map.string("type") ?: "",
            props = props,
            layout = map.map("layout")?.let(::readLayout) ?: UiLayout(),
            style = map.map("style")?.let(::readStyle) ?: UiStyle(),
            actions = readActions(map),
            children = map.list("children")?.items?.map(::readNode).orEmpty(),
            source = node.sourceSpan(),
        )
    }

    private fun readLayout(map: YamlMap): UiLayout {
        warnUnknown(map, setOf("type", "padding", "gap", "width", "height", "weight", "align", "columns"))
        return UiLayout(
            type = map.string("type") ?: "column",
            padding = map.string("padding") ?: "",
            gap = map.string("gap") ?: "",
            width = map.string("width") ?: "",
            height = map.string("height") ?: "",
            weight = map.float("weight"),
            align = map.string("align") ?: "",
            columns = map.int("columns"),
        )
    }

    private fun readStyle(map: YamlMap): UiStyle {
        warnUnknown(map, setOf("tone", "variant", "size", "emphasis", "color", "background"))
        return UiStyle(
            tone = map.string("tone") ?: "",
            variant = map.string("variant") ?: "",
            size = map.string("size") ?: "",
            emphasis = map.string("emphasis") ?: "",
            color = map.string("color") ?: "",
            background = map.string("background") ?: "",
        )
    }

    private fun readActions(map: YamlMap): List<UiAction> {
        val actions = mutableListOf<UiAction>()
        map.list("actions")?.items?.mapTo(actions) { readAction(it) }
        map.map("action")?.let { actions += readAction(it) }
        return actions
    }

    private fun readAction(node: YamlNode): UiAction {
        val map = node.asMap("Action entries must be maps.")
        warnUnknown(map, setOf("type", "target", "value"))
        return UiAction(
            type = map.string("type") ?: "none",
            target = map.string("target") ?: "",
            value = map.string("value") ?: "",
            source = node.sourceSpan(),
        )
    }

    private fun readScenario(node: YamlNode): UiScenario {
        val map = node.asMap("Scenario entries must be maps.")
        warnUnknown(map, setOf("id", "title", "summary", "steps"))
        val id = map.string("id") ?: ""
        return UiScenario(
            id = id,
            title = map.string("title") ?: id,
            summary = map.string("summary") ?: "",
            steps = map.list("steps")?.items?.map(::readStep).orEmpty(),
            source = node.sourceSpan(),
        )
    }

    private fun readStep(node: YamlNode): UiScenarioStep {
        val map = node.asMap("Scenario step entries must be maps.")
        warnUnknown(map, setOf("id", "screenId", "nodeId", "componentId", "action", "expectation", "note"))
        return UiScenarioStep(
            id = map.string("id") ?: "",
            screenId = map.string("screenId") ?: "",
            nodeId = map.string("nodeId") ?: map.string("componentId") ?: "",
            action = map.string("action") ?: "",
            expectation = map.string("expectation") ?: "",
            note = map.string("note") ?: "",
            source = node.sourceSpan(),
        )
    }

    private fun readComment(node: YamlNode): UiComment {
        val map = node.asMap("Comment entries must be maps.")
        warnUnknown(map, setOf("id", "targetId", "author", "body", "createdAt"))
        val targetId = map.string("targetId") ?: ""
        val body = map.string("body") ?: ""
        return UiComment(
            id = map.string("id") ?: stableUiId("comment", targetId, body),
            targetId = targetId,
            author = map.string("author") ?: "agent",
            body = body,
            createdAt = map.string("createdAt") ?: "",
            source = node.sourceSpan(),
        )
    }

    private fun readPrompt(node: YamlNode): UiPrompt {
        val map = node.asMap("Prompt entries must be maps.")
        warnUnknown(map, setOf("id", "targetId", "title", "body", "createdAt"))
        val targetId = map.string("targetId") ?: ""
        val body = map.string("body") ?: ""
        return UiPrompt(
            id = map.string("id") ?: stableUiId("prompt", targetId, body),
            targetId = targetId,
            title = map.string("title") ?: "Design prompt",
            body = body,
            createdAt = map.string("createdAt") ?: "",
            source = node.sourceSpan(),
        )
    }

    private fun warnUnknown(map: YamlMap, allowedKeys: Set<String>) {
        for ((key, node) in map.entries) {
            if (key !in allowedKeys) {
                diagnostics += UiDiagnostic(
                    severity = UiDiagnosticSeverity.Warning,
                    message = "Unknown field '$key' was ignored.",
                    source = node.sourceSpan(),
                )
            }
        }
    }
}

private val NodeAllowedKeys = setOf("id", "type", "props", "layout", "style", "action", "actions", "children")

private val NodeShorthandPropKeys = setOf(
    "title",
    "text",
    "body",
    "placeholder",
    "items",
    "headers",
    "rows",
    "label",
    "value",
    "icon",
)

private fun String.normalizedLines(): List<String> =
    replace("\r\n", "\n").replace('\r', '\n').split('\n')

private fun YamlNode.sourceSpan(): SourceSpan =
    SourceSpan(line, column)

private fun YamlNode.asMap(error: String): YamlMap =
    this as? YamlMap ?: throw UiYamlException(line, column, error)

private fun YamlNode.scalarString(error: String): String {
    val scalar = this as? YamlScalar ?: throw UiYamlException(line, column, error)
    return when (val value = scalar.value) {
        null -> ""
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> throw UiYamlException(line, column, error)
    }
}

private fun YamlNode.toUiValue(): UiValue =
    when (this) {
        is YamlScalar -> when (val scalar = value) {
            null -> UiValue.NullValue
            is String -> UiValue.StringValue(scalar)
            is Int -> UiValue.NumberValue(scalar.toDouble())
            is Double -> UiValue.NumberValue(scalar)
            is Boolean -> UiValue.BooleanValue(scalar)
            is Number -> UiValue.NumberValue(scalar.toDouble())
            else -> UiValue.StringValue(scalar.toString())
        }
        is YamlList -> UiValue.ListValue(items.map { it.toUiValue() })
        is YamlMap -> UiValue.ObjectValue(entries.mapValues { it.value.toUiValue() })
    }

private fun YamlMap.string(key: String): String? =
    entries[key]?.scalarString("Field '$key' must be a scalar string.")

private fun YamlMap.int(key: String): Int? {
    val node = entries[key] ?: return null
    val scalar = node as? YamlScalar ?: throw UiYamlException(node.line, node.column, "Field '$key' must be an integer.")
    return when (val value = scalar.value) {
        is Int -> value
        is String -> value.toIntOrNull() ?: throw UiYamlException(node.line, node.column, "Field '$key' must be an integer.")
        else -> throw UiYamlException(node.line, node.column, "Field '$key' must be an integer.")
    }
}

private fun YamlMap.float(key: String): Float? {
    val node = entries[key] ?: return null
    val scalar = node as? YamlScalar ?: throw UiYamlException(node.line, node.column, "Field '$key' must be a number.")
    return when (val value = scalar.value) {
        is Int -> value.toFloat()
        is Double -> value.toFloat()
        is String -> value.toFloatOrNull() ?: throw UiYamlException(node.line, node.column, "Field '$key' must be a number.")
        else -> throw UiYamlException(node.line, node.column, "Field '$key' must be a number.")
    }
}

private fun YamlMap.map(key: String): YamlMap? {
    val node = entries[key] ?: return null
    return node as? YamlMap ?: throw UiYamlException(node.line, node.column, "Field '$key' must be a map.")
}

private fun YamlMap.list(key: String): YamlList? {
    val node = entries[key] ?: return null
    return node as? YamlList ?: throw UiYamlException(node.line, node.column, "Field '$key' must be a list.")
}

private fun YamlMap.stringMap(): Map<String, String> =
    entries.mapValues { (key, value) -> value.scalarString("Field '$key' must be a scalar string.") }
