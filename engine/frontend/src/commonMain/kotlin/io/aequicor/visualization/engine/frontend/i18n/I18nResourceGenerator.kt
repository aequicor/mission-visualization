package io.aequicor.visualization.engine.frontend.i18n

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.ast.KeyHint
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.semantics.SemanticLexicon
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextContent

/** Result of the i18n stage: locale bundles plus the per-entry key assignments. */
data class I18nGeneration(
    val resources: Map<SlmLocale, Map<String, String>>,
    val assignments: List<KeyAssignment>,
)

data class KeyAssignment(val entry: TextEntry, val key: String)

/**
 * i18n resource generation (design section F). Explicit keys (text.key, i18nKey
 * prop, `<!-- i18n:key=... -->`) always win; duplicate explicit keys with
 * different default text are an error. Generated keys dedupe identical
 * (key, text) pairs; a key reused with different text gets a `.2` suffix and a
 * warning. The source-locale bundle carries every default text; target locales
 * get empty bundles plus one summary warning each. ICU plural messages pass
 * through verbatim with brace/category sanity checks.
 */
fun generateI18nResources(
    entries: List<TextEntry>,
    screenId: String,
    sourceLocale: SlmLocale,
    targetLocales: List<SlmLocale>,
    lexicon: SemanticLexicon,
    diagnostics: DiagnosticCollector,
): I18nGeneration {
    val generator = I18nKeyGenerator(screenId, lexicon)
    val bundle = LinkedHashMap<String, String>()
    val assignments = mutableListOf<KeyAssignment>()

    // Explicit keys claim first, in document order.
    entries.filter { it.explicitKey != null }.forEach { entry ->
        val key = checkNotNull(entry.explicitKey)
        val existing = bundle[key]
        if (existing != null && existing != entry.defaultText) {
            diagnostics.error(
                "Duplicate explicit i18n key \"$key\" with different default text",
                entry.span,
            )
        } else {
            bundle[key] = entry.defaultText
        }
        assignments += KeyAssignment(entry, key)
    }

    // `text{N}` numbering: N appears only when a base has more than one distinct text.
    val generated = entries.filter { it.explicitKey == null }
    val numberedTexts = LinkedHashMap<String, MutableList<String>>()
    generated.filter { generator.isNumberedText(it) }.forEach { entry ->
        val texts = numberedTexts.getOrPut(generator.baseKeyFor(entry)) { mutableListOf() }
        if (entry.defaultText !in texts) texts += entry.defaultText
    }

    generated.forEach { entry ->
        val base = generator.baseKeyFor(entry)
        var key = if (generator.isNumberedText(entry)) {
            val texts = numberedTexts.getValue(base)
            if (texts.size <= 1) base else "$base${texts.indexOf(entry.defaultText) + 1}"
        } else {
            base
        }
        val existing = bundle[key]
        when {
            existing == null -> bundle[key] = entry.defaultText
            existing == entry.defaultText -> {} // dedupe identical (key, text)
            else -> {
                var suffix = 2
                while (bundle["$key.$suffix"] != null && bundle["$key.$suffix"] != entry.defaultText) suffix++
                val candidate = "$key.$suffix"
                diagnostics.warning(
                    "i18n key \"$key\" is already used with different text; using \"$candidate\"",
                    entry.span,
                )
                key = candidate
                bundle[key] = entry.defaultText
            }
        }
        assignments += KeyAssignment(entry, key)
    }

    bundle.forEach { (key, message) -> checkIcuMessage(key, message, sourceLocale, diagnostics) }

    val resources = LinkedHashMap<SlmLocale, Map<String, String>>()
    resources[sourceLocale] = bundle.toMap()
    targetLocales
        .filter { it.tag != sourceLocale.tag }
        .distinctBy { it.tag }
        .forEach { locale ->
            resources[locale] = emptyMap()
            if (bundle.isNotEmpty()) {
                diagnostics.warning(
                    "Locale \"${locale.tag}\" has ${bundle.size} untranslated key(s); " +
                        "first: ${bundle.keys.take(3).joinToString(", ")}",
                    line = 1,
                )
            }
        }
    return I18nGeneration(resources = resources, assignments = assignments)
}

/** Writes assigned keys back into node text content / instance labels / media alt. */
fun applyGeneratedKeys(
    document: DesignDocument,
    assignments: List<KeyAssignment>,
    sourceLocale: SlmLocale,
): DesignDocument {
    var result = document
    assignments.forEach { (entry, key) ->
        when (val hint = entry.keyHint) {
            is KeyHint.ComponentProp -> result = result.copy(
                components = result.components.mapValues { (componentId, component) ->
                    if (componentId != hint.componentId) {
                        component
                    } else {
                        component.copy(
                            properties = component.properties.mapValues { (propName, definition) ->
                                if (propName != hint.prop) {
                                    definition
                                } else {
                                    definition.copy(
                                        default = contentDefault(definition.default, key, entry, sourceLocale),
                                    )
                                }
                            },
                        )
                    }
                },
            )
            else -> result = result.updateNode(entry.nodeId) { node -> wireNodeKey(node, key) }
        }
    }
    return result
}

private fun wireNodeKey(node: DesignNode, key: String): DesignNode = when (val kind = node.kind) {
    is DesignNodeKind.Text -> {
        val content = kind.content
        if (content == null) node else node.copy(kind = kind.copy(content = content.copy(key = key)))
    }
    is DesignNodeKind.Instance -> {
        val label = kind.props["label"]
        if (label is PropValue.Content) {
            node.copy(
                kind = kind.copy(
                    props = kind.props + ("label" to PropValue.Content(label.content.copy(key = key))),
                ),
            )
        } else {
            node
        }
    }
    is DesignNodeKind.Media -> {
        val alt = kind.media.alt
        if (alt == null) node else node.copy(kind = kind.copy(media = kind.media.copy(alt = alt.copy(key = key))))
    }
    else -> node
}

private fun contentDefault(
    default: PropValue?,
    key: String,
    entry: TextEntry,
    sourceLocale: SlmLocale,
): PropValue = when (default) {
    is PropValue.Content -> PropValue.Content(
        default.content.copy(key = key, defaultLocale = sourceLocale.tag),
    )
    is PropValue.Text -> PropValue.Content(
        TextContent(key = key, defaultLocale = sourceLocale.tag, defaultText = default.value),
    )
    else -> default ?: PropValue.Content(
        TextContent(key = key, defaultLocale = sourceLocale.tag, defaultText = entry.defaultText),
    )
}

// --- ICU sanity checks ---

private val pluralMarker = Regex("""\{\s*[A-Za-z0-9_]+\s*,\s*plural\s*,""")

internal fun checkIcuMessage(
    key: String,
    message: String,
    sourceLocale: SlmLocale,
    diagnostics: DiagnosticCollector,
) {
    var depth = 0
    var balanced = true
    message.forEach { char ->
        when (char) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth < 0) balanced = false
            }
        }
    }
    if (depth != 0 || !balanced) {
        diagnostics.warning("Unbalanced braces in ICU message for key \"$key\"")
        return
    }
    if (!pluralMarker.containsMatchIn(message)) return
    val required = when (sourceLocale.language) {
        "ru" -> setOf("one", "few", "many", "other")
        "en" -> setOf("one", "other")
        else -> setOf("other")
    }
    val missing = required - pluralCategories(message)
    if (missing.isNotEmpty()) {
        diagnostics.warning(
            "ICU plural for key \"$key\" is missing ${sourceLocale.language} " +
                "categories: ${missing.sorted().joinToString(", ")}",
        )
    }
}

/** Category names (`one`, `few`, `=0`, ...) at the top level of the plural body. */
private fun pluralCategories(message: String): Set<String> {
    val marker = pluralMarker.find(message) ?: return emptySet()
    val categories = mutableSetOf<String>()
    val token = StringBuilder()
    var depth = 1
    var index = marker.range.last + 1
    while (index < message.length && depth > 0) {
        when (val char = message[index]) {
            '{' -> {
                if (depth == 1 && token.isNotBlank()) categories += token.toString().trim()
                token.clear()
                depth++
            }
            '}' -> {
                token.clear()
                depth--
            }
            else -> if (depth == 1) token.append(char)
        }
        index++
    }
    return categories
}
