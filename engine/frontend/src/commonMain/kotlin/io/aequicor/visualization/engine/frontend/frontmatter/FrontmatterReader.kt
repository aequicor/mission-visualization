package io.aequicor.visualization.engine.frontend.frontmatter

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.RawYamlBlock
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.frontend.yaml.parseSlmYaml

private const val BLOCK_PATH = "frontmatter"

private val knownKeys = setOf(
    "screen", "page", "sourceLocale", "targetLocales",
    "density", "platform", "theme",
    "frame", "canvas", "flow", "breakpoints", "libraries",
)

/** Parses the raw frontmatter YAML and maps it to [SlmFrontmatter]. */
fun readFrontmatter(raw: RawYamlBlock?, diagnostics: DiagnosticCollector): SlmFrontmatter {
    if (raw == null) {
        diagnostics.error("Missing frontmatter: `screen` must be declared", 1, blockPath = BLOCK_PATH)
        return SlmFrontmatter()
    }
    val value = parseSlmYaml(raw.text, diagnostics, startLine = raw.startLine)
    return readFrontmatter(value, diagnostics, line = raw.startLine)
}

/** Maps an already-parsed frontmatter YAML value to [SlmFrontmatter]. */
fun readFrontmatter(value: YamlValue?, diagnostics: DiagnosticCollector, line: Int = 1): SlmFrontmatter {
    val map = value as? YamlMap
    if (map == null) {
        diagnostics.error("Frontmatter must be a YAML map declaring `screen`", line, blockPath = BLOCK_PATH)
        return SlmFrontmatter()
    }
    map.entries.keys
        .filterNot { it in knownKeys }
        .forEach { key ->
            diagnostics.warning(
                "Unknown frontmatter key \"$key\"",
                map.entries.getValue(key).line,
                blockPath = BLOCK_PATH,
            )
        }

    val screen = map.string("screen", diagnostics).orEmpty()
    if (screen.isBlank()) {
        diagnostics.error("Frontmatter must declare `screen`", map.line, blockPath = BLOCK_PATH)
    }
    val modes = buildMap {
        listOf("density", "platform", "theme").forEach { dimension ->
            map.string(dimension, diagnostics)?.let { put(dimension, it) }
        }
    }
    return SlmFrontmatter(
        screen = screen,
        page = map.string("page", diagnostics).orEmpty(),
        sourceLocale = map.string("sourceLocale", diagnostics)?.let(::SlmLocale),
        targetLocales = map.stringList("targetLocales", diagnostics).map(::SlmLocale),
        modes = modes,
        frame = (map.entries["frame"] as? YamlMap)?.let { frame ->
            SlmFrame(
                preset = frame.string("preset", diagnostics).orEmpty(),
                width = frame.number("width", diagnostics),
                height = frame.number("height", diagnostics),
            )
        },
        canvas = (map.entries["canvas"] as? YamlMap)?.let { canvas ->
            val position = canvas.entries["position"] as? YamlMap
            SlmCanvas(
                section = canvas.string("section", diagnostics).orEmpty(),
                x = position?.number("x", diagnostics) ?: canvas.number("x", diagnostics),
                y = position?.number("y", diagnostics) ?: canvas.number("y", diagnostics),
            )
        },
        flow = (map.entries["flow"] as? YamlMap)?.let { flow ->
            SlmFlow(
                id = flow.string("id", diagnostics).orEmpty(),
                node = flow.string("node", diagnostics).orEmpty(),
                next = flow.stringList("next", diagnostics),
            )
        },
        breakpoints = map.mapList("breakpoints", diagnostics) { item ->
            val id = item.string("id", diagnostics)
            if (id.isNullOrBlank()) {
                diagnostics.warning("Breakpoint without `id` is ignored", item.line, blockPath = BLOCK_PATH)
                null
            } else {
                SlmBreakpoint(
                    id = id,
                    minWidth = item.number("minWidth", diagnostics),
                    maxWidth = item.number("maxWidth", diagnostics),
                )
            }
        },
        libraries = map.mapList("libraries", diagnostics) { item ->
            val id = item.string("id", diagnostics)
            val source = item.string("source", diagnostics)
            if (id.isNullOrBlank() || source.isNullOrBlank()) {
                diagnostics.warning("Library requires `id` and `source`", item.line, blockPath = BLOCK_PATH)
                null
            } else {
                SlmLibrary(id = id, source = source)
            }
        },
    )
}

// --- typed accessors with wrong-type warnings ---

private fun YamlMap.string(key: String, diagnostics: DiagnosticCollector): String? {
    val value = entries[key] ?: return null
    val scalar = value as? YamlScalar
    val result = scalar?.value?.let { raw ->
        when (raw) {
            is String -> raw
            is Double -> scalar.raw
            is Boolean -> scalar.raw
            else -> null
        }
    }
    if (result == null) {
        diagnostics.warning("Frontmatter `$key` must be a string", value.line, blockPath = BLOCK_PATH)
    }
    return result
}

private fun YamlMap.number(key: String, diagnostics: DiagnosticCollector): Double? {
    val value = entries[key] ?: return null
    val result = (value as? YamlScalar)?.value as? Double
    if (result == null) {
        diagnostics.warning("Frontmatter `$key` must be a number", value.line, blockPath = BLOCK_PATH)
    }
    return result
}

private fun YamlMap.stringList(key: String, diagnostics: DiagnosticCollector): List<String> {
    val value = entries[key] ?: return emptyList()
    val items = when (value) {
        is YamlList -> value.items
        is YamlScalar -> listOf(value)
        else -> {
            diagnostics.warning("Frontmatter `$key` must be a list", value.line, blockPath = BLOCK_PATH)
            return emptyList()
        }
    }
    return items.mapNotNull { item ->
        val text = (item as? YamlScalar)?.value as? String
        if (text == null) {
            diagnostics.warning("Frontmatter `$key` items must be strings", item.line, blockPath = BLOCK_PATH)
        }
        text
    }
}

private fun <T> YamlMap.mapList(
    key: String,
    diagnostics: DiagnosticCollector,
    transform: (YamlMap) -> T?,
): List<T> {
    val value = entries[key] ?: return emptyList()
    val list = value as? YamlList
    if (list == null) {
        diagnostics.warning("Frontmatter `$key` must be a list", value.line, blockPath = BLOCK_PATH)
        return emptyList()
    }
    return list.items.mapNotNull { item ->
        val map = item as? YamlMap
        if (map == null) {
            diagnostics.warning("Frontmatter `$key` items must be maps", item.line, blockPath = BLOCK_PATH)
            null
        } else {
            transform(map)
        }
    }
}
