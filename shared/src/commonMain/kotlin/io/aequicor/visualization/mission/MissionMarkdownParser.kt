package io.aequicor.visualization.mission

private const val MissionFence = "```mission-visualization"
private const val ClosingFence = "```"

fun parseMissionMarkdown(markdown: String): MissionParseResult {
    return try {
        val document = extractMissionDocument(markdown)
            ?: return MissionParseResult.Failure(
                listOf(MissionParseMessage(1, "Expected exactly one fenced ```mission-visualization block.")),
            )

        val duplicate = countMissionFences(markdown) > 1
        if (duplicate) {
            return MissionParseResult.Failure(
                listOf(MissionParseMessage(document.fenceStartLine, "Expected exactly one mission-visualization block, found more than one.")),
            )
        }

        val root = ConstrainedYamlParser(document.yaml, document.fenceStartLine + 1).parse()
        val warnings = mutableListOf<MissionParseMessage>()
        val spec = MissionSpecReader(warnings).read(root)
        MissionParseResult.Success(spec, document, warnings)
    } catch (error: MissionYamlException) {
        MissionParseResult.Failure(listOf(MissionParseMessage(error.line, error.reason)))
    }
}

private fun countMissionFences(markdown: String): Int =
    markdown.normalizedLines().count { it.trim() == MissionFence }

private fun extractMissionDocument(markdown: String): MissionMarkdownDocument? {
    val lines = markdown.normalizedLines()
    val startIndex = lines.indexOfFirst { it.trim() == MissionFence }
    if (startIndex < 0) return null

    val endIndex = lines.drop(startIndex + 1).indexOfFirst { it.trim() == ClosingFence }
    if (endIndex < 0) {
        throw MissionYamlException(startIndex + 1, "Missing closing ``` fence for mission-visualization block.")
    }

    val absoluteEndIndex = startIndex + 1 + endIndex
    val yaml = lines.subList(startIndex + 1, absoluteEndIndex).joinToString("\n")
    return MissionMarkdownDocument(
        originalMarkdown = markdown,
        yaml = yaml,
        fenceStartLine = startIndex + 1,
        fenceEndLine = absoluteEndIndex + 1,
    )
}

private fun String.normalizedLines(): List<String> =
    replace("\r\n", "\n").replace('\r', '\n').split('\n')

private class MissionYamlException(
    val line: Int,
    val reason: String,
) : RuntimeException(reason)

private sealed interface YamlNode {
    val line: Int
}

private data class YamlMap(
    val entries: Map<String, YamlNode>,
    override val line: Int,
) : YamlNode

private data class YamlList(
    val items: List<YamlNode>,
    override val line: Int,
) : YamlNode

private data class YamlScalar(
    val value: Any?,
    override val line: Int,
) : YamlNode

private data class ParsedNode(
    val node: YamlNode,
    val nextIndex: Int,
)

private class ConstrainedYamlParser(
    yaml: String,
    private val firstLineNumber: Int,
) {
    private val lines = yaml.normalizedLines()

    fun parse(): YamlNode {
        val start = nextContentIndex(0)
            ?: fail(firstLineNumber, "mission-visualization block is empty.")
        val parsed = parseBlock(start, indentOf(start))
        val trailing = nextContentIndex(parsed.nextIndex)
        if (trailing != null) {
            fail(lineNumber(trailing), "Unexpected trailing content.")
        }
        return parsed.node
    }

    private fun parseBlock(startIndex: Int, indent: Int): ParsedNode {
        val currentIndent = indentOf(startIndex)
        if (currentIndent < indent) {
            fail(lineNumber(startIndex), "Expected indentation of at least $indent spaces.")
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
            val contentIndex = nextContentIndex(index) ?: return ParsedNode(YamlMap(entries, lineNumber(startIndex)), lines.size)
            val currentIndent = indentOf(contentIndex)
            if (currentIndent < indent) return ParsedNode(YamlMap(entries, lineNumber(startIndex)), contentIndex)
            if (currentIndent > indent) fail(lineNumber(contentIndex), "Unexpected indentation.")

            val content = contentAt(contentIndex)
            if (content.startsWith("-")) {
                fail(lineNumber(contentIndex), "Unexpected list item inside a map.")
            }

            val entry = parseMapEntry(content, lineNumber(contentIndex))
            if (entries.containsKey(entry.key)) {
                fail(lineNumber(contentIndex), "Duplicate key '${entry.key}'.")
            }
            val value = parseEntryValue(entry, contentIndex, currentIndent)
            entries[entry.key] = value.node
            index = value.nextIndex
        }
        return ParsedNode(YamlMap(entries, lineNumber(startIndex)), index)
    }

    private fun parseList(startIndex: Int, indent: Int): ParsedNode {
        val items = mutableListOf<YamlNode>()
        var index = startIndex
        while (index < lines.size) {
            val contentIndex = nextContentIndex(index) ?: return ParsedNode(YamlList(items, lineNumber(startIndex)), lines.size)
            val currentIndent = indentOf(contentIndex)
            if (currentIndent < indent) return ParsedNode(YamlList(items, lineNumber(startIndex)), contentIndex)
            if (currentIndent > indent) fail(lineNumber(contentIndex), "Unexpected indentation inside a list.")

            val content = contentAt(contentIndex)
            if (!content.startsWith("-")) {
                return ParsedNode(YamlList(items, lineNumber(startIndex)), contentIndex)
            }

            val itemContent = content.drop(1).trimStart()
            if (itemContent.isEmpty()) {
                val childIndex = nextContentIndex(contentIndex + 1)
                    ?: fail(lineNumber(contentIndex), "List item needs a value.")
                if (indentOf(childIndex) <= currentIndent) {
                    fail(lineNumber(childIndex), "Nested list item content must be indented.")
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
                items += parseScalarOrInline(itemContent, lineNumber(contentIndex))
                val nextIndex = nextContentIndex(contentIndex + 1)
                if (nextIndex != null && indentOf(nextIndex) > currentIndent) {
                    fail(lineNumber(nextIndex), "Scalar list items cannot have nested content.")
                }
                index = contentIndex + 1
            }
        }
        return ParsedNode(YamlList(items, lineNumber(startIndex)), index)
    }

    private fun parseListMapItem(itemContent: String, itemIndex: Int, listIndent: Int): ParsedNode {
        val entries = linkedMapOf<String, YamlNode>()
        val entry = parseMapEntry(itemContent, lineNumber(itemIndex))
        val firstValue = parseEntryValue(entry, itemIndex, listIndent)
        entries[entry.key] = firstValue.node

        var index = firstValue.nextIndex
        while (true) {
            val continuationIndex = nextContentIndex(index) ?: break
            val continuationIndent = indentOf(continuationIndex)
            if (continuationIndent <= listIndent) break

            val continuation = parseBlock(continuationIndex, continuationIndent)
            val continuationMap = continuation.node as? YamlMap
                ?: fail(lineNumber(continuationIndex), "List map item continuation must be a map.")
            for ((key, value) in continuationMap.entries) {
                if (entries.containsKey(key)) {
                    fail(value.line, "Duplicate key '$key'.")
                }
                entries[key] = value
            }
            index = continuation.nextIndex
        }

        return ParsedNode(YamlMap(entries, lineNumber(itemIndex)), index)
    }

    private fun parseEntryValue(entry: MapEntry, index: Int, indent: Int): ParsedNode {
        if (entry.valueText.isNotEmpty()) {
            return ParsedNode(parseScalarOrInline(entry.valueText, lineNumber(index)), index + 1)
        }

        val childIndex = nextContentIndex(index + 1)
        if (childIndex == null || indentOf(childIndex) <= indent) {
            return ParsedNode(YamlScalar(null, lineNumber(index)), index + 1)
        }
        return parseBlock(childIndex, indentOf(childIndex))
    }

    private fun parseScalarOrInline(text: String, line: Int): YamlNode {
        if (text == "|" || text == ">") {
            fail(line, "Multiline YAML scalars are not supported in the MVP subset.")
        }
        if (text.startsWith("&") || text.startsWith("*") || text.startsWith("!")) {
            fail(line, "YAML anchors, aliases, and tags are not supported in the MVP subset.")
        }
        if (text == "[]") return YamlList(emptyList(), line)
        if (text == "{}") return YamlMap(emptyMap(), line)
        if (text.startsWith("[") || text.startsWith("{")) {
            if (!text.endsWith(if (text.startsWith("[")) "]" else "}")) {
                fail(line, "Inline YAML values must be closed on one line.")
            }
            return if (text.startsWith("[")) parseInlineList(text, line) else parseInlineMap(text, line)
        }

        val scalarValue: Any? = when {
            text == "null" || text == "~" -> null
            text == "true" -> true
            text == "false" -> false
            text.startsWith("\"") || text.startsWith("'") -> parseQuoted(text, line)
            text.toIntOrNull() != null -> text.toInt()
            text.contains(".") && text.toDoubleOrNull() != null -> text.toDouble()
            else -> text
        }
        return YamlScalar(scalarValue, line)
    }

    private fun parseInlineList(text: String, line: Int): YamlList {
        val inner = text.removePrefix("[").removeSuffix("]").trim()
        if (inner.isEmpty()) return YamlList(emptyList(), line)
        val items = splitInline(inner).map { parseScalarOrInline(it.trim(), line) }
        return YamlList(items, line)
    }

    private fun parseInlineMap(text: String, line: Int): YamlMap {
        val inner = text.removePrefix("{").removeSuffix("}").trim()
        if (inner.isEmpty()) return YamlMap(emptyMap(), line)
        val entries = linkedMapOf<String, YamlNode>()
        for (part in splitInline(inner)) {
            val entry = parseMapEntry(part.trim(), line)
            entries[entry.key] = parseScalarOrInline(entry.valueText, line)
        }
        return YamlMap(entries, line)
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

    private fun parseQuoted(text: String, line: Int): String {
        val quote = text.first()
        if (text.length < 2 || text.last() != quote) {
            fail(line, "Quoted strings must close on the same line.")
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

    private fun parseMapEntry(content: String, line: Int): MapEntry {
        val separatorIndex = content.indexOf(':')
        if (separatorIndex <= 0) fail(line, "Expected a 'key: value' entry.")
        val key = content.substring(0, separatorIndex).trim()
        if (key.isEmpty()) fail(line, "Map keys cannot be empty.")
        if (key.startsWith("!") || key.startsWith("&") || key.startsWith("*")) {
            fail(line, "YAML anchors, aliases, and tags are not supported in keys.")
        }
        val value = content.substring(separatorIndex + 1).trimStart()
        return MapEntry(key, value)
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
                fail(lineNumber(index), "Tabs are not supported for indentation.")
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
        firstLineNumber + index

    private fun fail(line: Int, message: String): Nothing =
        throw MissionYamlException(line, message)

    private data class MapEntry(
        val key: String,
        val valueText: String,
    )
}

private class MissionSpecReader(
    private val warnings: MutableList<MissionParseMessage>,
) {
    fun read(root: YamlNode): MissionSpec {
        val map = root.asMap("Root mission spec must be a map.")
        warnUnknown(map, setOf("version", "title", "description", "theme", "screens", "scenarios", "comments", "prompts"))
        return MissionSpec(
            version = map.int("version") ?: 1,
            title = map.string("title") ?: "Untitled mission",
            description = map.string("description") ?: "",
            theme = map.map("theme")?.let(::readTheme) ?: MissionTheme(),
            screens = map.list("screens")?.items?.map(::readScreen).orEmpty(),
            scenarios = map.list("scenarios")?.items?.map(::readScenario).orEmpty(),
            comments = map.list("comments")?.items?.map(::readComment).orEmpty(),
            prompts = map.list("prompts")?.items?.map(::readPrompt).orEmpty(),
        )
    }

    private fun readTheme(node: YamlMap): MissionTheme {
        warnUnknown(node, setOf("name", "primary", "accent", "surface", "mood"))
        return MissionTheme(
            name = node.string("name") ?: "Lazurite",
            primary = node.string("primary") ?: "#1F5FA8",
            accent = node.string("accent") ?: "#2BB8A8",
            surface = node.string("surface") ?: "#F6FAFF",
            mood = node.string("mood") ?: "precise, calm, product-focused",
        )
    }

    private fun readScreen(node: YamlNode): MissionScreen {
        val map = node.asMap("Screen entries must be maps.")
        warnUnknown(map, setOf("id", "title", "description", "components"))
        val id = map.requiredString("id", "Screen id is required.")
        return MissionScreen(
            id = id,
            title = map.string("title") ?: id,
            description = map.string("description") ?: "",
            components = map.list("components")?.items?.map(::readComponent).orEmpty(),
        )
    }

    private fun readComponent(node: YamlNode): MissionComponent {
        val map = node.asMap("Component entries must be maps.")
        warnUnknown(
            map,
            setOf("id", "type", "title", "text", "placeholder", "description", "variant", "items", "children", "properties"),
        )
        val id = map.requiredString("id", "Component id is required.")
        val type = map.requiredString("type", "Component type is required.")
        return MissionComponent(
            id = id,
            type = type,
            title = map.string("title") ?: "",
            text = map.string("text") ?: "",
            placeholder = map.string("placeholder") ?: "",
            description = map.string("description") ?: "",
            variant = map.string("variant") ?: "",
            items = map.list("items")?.items?.map { it.scalarString("Component item must be a scalar string.") }.orEmpty(),
            children = map.list("children")?.items?.map(::readComponent).orEmpty(),
            properties = map.map("properties")?.entries?.mapValues { it.value.scalarString("Property values must be scalar strings.") }.orEmpty(),
        )
    }

    private fun readScenario(node: YamlNode): MissionScenario {
        val map = node.asMap("Scenario entries must be maps.")
        warnUnknown(map, setOf("id", "title", "summary", "steps"))
        val id = map.requiredString("id", "Scenario id is required.")
        return MissionScenario(
            id = id,
            title = map.string("title") ?: id,
            summary = map.string("summary") ?: "",
            steps = map.list("steps")?.items?.map(::readStep).orEmpty(),
        )
    }

    private fun readStep(node: YamlNode): ScenarioStep {
        val map = node.asMap("Scenario step entries must be maps.")
        warnUnknown(map, setOf("id", "screenId", "componentId", "action", "expectation", "note"))
        return ScenarioStep(
            id = map.string("id") ?: "",
            screenId = map.requiredString("screenId", "Scenario step screenId is required."),
            componentId = map.string("componentId") ?: "",
            action = map.requiredString("action", "Scenario step action is required."),
            expectation = map.string("expectation") ?: "",
            note = map.string("note") ?: "",
        )
    }

    private fun readComment(node: YamlNode): ComponentComment {
        val map = node.asMap("Comment entries must be maps.")
        warnUnknown(map, setOf("id", "targetId", "author", "body", "createdAt"))
        val targetId = map.requiredString("targetId", "Comment targetId is required.")
        return ComponentComment(
            id = map.string("id") ?: stableId("comment", targetId, map.string("body") ?: ""),
            targetId = targetId,
            author = map.string("author") ?: "agent",
            body = map.requiredString("body", "Comment body is required."),
            createdAt = map.string("createdAt") ?: "",
        )
    }

    private fun readPrompt(node: YamlNode): DesignPrompt {
        val map = node.asMap("Prompt entries must be maps.")
        warnUnknown(map, setOf("id", "targetId", "title", "body", "createdAt"))
        val targetId = map.requiredString("targetId", "Prompt targetId is required.")
        return DesignPrompt(
            id = map.string("id") ?: stableId("prompt", targetId, map.string("body") ?: ""),
            targetId = targetId,
            title = map.string("title") ?: "Design prompt",
            body = map.requiredString("body", "Prompt body is required."),
            createdAt = map.string("createdAt") ?: "",
        )
    }

    private fun warnUnknown(map: YamlMap, allowedKeys: Set<String>) {
        for (key in map.entries.keys) {
            if (key !in allowedKeys) {
                warnings += MissionParseMessage(map.entries.getValue(key).line, "Unknown field '$key' was ignored.")
            }
        }
    }
}

private fun YamlNode.asMap(error: String): YamlMap =
    this as? YamlMap ?: throw MissionYamlException(line, error)

private fun YamlNode.scalarString(error: String): String {
    val scalar = this as? YamlScalar ?: throw MissionYamlException(line, error)
    return when (val value = scalar.value) {
        null -> ""
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> throw MissionYamlException(line, error)
    }
}

private fun YamlMap.string(key: String): String? =
    entries[key]?.scalarString("Field '$key' must be a scalar string.")

private fun YamlMap.requiredString(key: String, error: String): String {
    val node = entries[key] ?: throw MissionYamlException(line, error)
    val value = node.scalarString("Field '$key' must be a scalar string.")
    if (value.isBlank()) throw MissionYamlException(node.line, error)
    return value
}

private fun YamlMap.int(key: String): Int? {
    val node = entries[key] ?: return null
    val scalar = node as? YamlScalar ?: throw MissionYamlException(node.line, "Field '$key' must be an integer.")
    return when (val value = scalar.value) {
        is Int -> value
        is String -> value.toIntOrNull() ?: throw MissionYamlException(node.line, "Field '$key' must be an integer.")
        else -> throw MissionYamlException(node.line, "Field '$key' must be an integer.")
    }
}

private fun YamlMap.map(key: String): YamlMap? {
    val node = entries[key] ?: return null
    return node as? YamlMap ?: throw MissionYamlException(node.line, "Field '$key' must be a map.")
}

private fun YamlMap.list(key: String): YamlList? {
    val node = entries[key] ?: return null
    return node as? YamlList ?: throw MissionYamlException(node.line, "Field '$key' must be a list.")
}
