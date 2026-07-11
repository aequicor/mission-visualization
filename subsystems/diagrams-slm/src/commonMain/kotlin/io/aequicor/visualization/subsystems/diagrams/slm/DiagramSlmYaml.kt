package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import kotlin.math.abs
import kotlin.math.floor

/**
 * Minimal YAML tree used by [DiagramYamlWriter] to emit the canonical `diagram:` block.
 * Rendering rules mirror the frontend's `SlmBlockRenderer` (which is internal to
 * `:engine:frontend`): block mappings nest two spaces, sequence map items hoist their
 * first entry onto the `- ` line, inline sequences render as `[a, b]`.
 */
internal sealed interface SlmYaml {
    /** Pre-formatted scalar token (already quoted/escaped as needed). */
    data class Scalar(val text: String) : SlmYaml

    data class Mapping(val entries: List<Pair<String, SlmYaml>>) : SlmYaml

    data class Sequence(val items: List<SlmYaml>, val inline: Boolean = false) : SlmYaml
}

internal object SlmYamlRender {

    /** `key: value` / `key:` + nested lines, indented [indent] spaces. */
    fun entryLines(key: String, value: SlmYaml, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        return when (value) {
            is SlmYaml.Scalar -> listOf("$pad$key: ${value.text}")
            is SlmYaml.Mapping ->
                if (value.entries.isEmpty()) {
                    listOf("$pad$key:")
                } else {
                    listOf("$pad$key:") + mappingLines(value, indent + 2)
                }
            is SlmYaml.Sequence ->
                if (value.inline) {
                    listOf("$pad$key: ${inline(value)}")
                } else {
                    listOf("$pad$key:") + sequenceLines(value, indent + 2)
                }
        }
    }

    fun mappingLines(mapping: SlmYaml.Mapping, indent: Int): List<String> =
        mapping.entries.flatMap { (key, child) -> entryLines(key, child, indent) }

    fun sequenceLines(sequence: SlmYaml.Sequence, indent: Int): List<String> =
        sequence.items.flatMap { sequenceItem(it, indent) }

    /**
     * One block-sequence item. Map items hoist their first entry onto the `- ` line and
     * align the rest two columns in — a flow map after a `- ` dash is not accepted by
     * the SLM parser in block context.
     */
    private fun sequenceItem(item: SlmYaml, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        return when (item) {
            is SlmYaml.Scalar -> listOf("$pad- ${item.text}")
            is SlmYaml.Sequence -> listOf("$pad- ${inline(item)}")
            is SlmYaml.Mapping -> {
                if (item.entries.isEmpty()) return listOf("$pad- {}")
                val lines = mappingLines(item, indent + 2)
                listOf("$pad- ${lines.first().trimStart()}") + lines.drop(1)
            }
        }
    }

    private fun inline(value: SlmYaml): String = when (value) {
        is SlmYaml.Scalar -> value.text
        is SlmYaml.Sequence -> "[" + value.items.joinToString(", ") { inline(it) } + "]"
        is SlmYaml.Mapping ->
            "{ " + value.entries.joinToString(", ") { (k, v) -> "$k: ${inline(v)}" } + " }"
    }
}

// --- scalar formatting (mirrors the frontend's internal ScalarFormatter rules) ---

private val plainStringRegex = Regex("""^[A-Za-z0-9_$./-]+$""")
private val numberLikeRegex = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")
private val reservedYamlWords = setOf("true", "false", "null", "~")

/** Integers without a decimal point, minimal doubles, never scientific for integers. */
internal fun yamlNumber(value: Double): String =
    if (value == floor(value) && !value.isInfinite() && abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        value.toString()
    }

/** Plain when safe (`^[A-Za-z0-9_$./-]+$`, not bool/number/null), double-quoted otherwise. */
internal fun yamlString(text: String): String = when {
    plainStringRegex.matches(text) &&
        text !in reservedYamlWords &&
        !numberLikeRegex.matches(text) -> text
    else -> buildString {
        append('"')
        text.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

internal fun scalar(text: String): SlmYaml = SlmYaml.Scalar(yamlString(text))

internal fun scalar(value: Double): SlmYaml = SlmYaml.Scalar(yamlNumber(value))

internal fun scalar(value: Int): SlmYaml = SlmYaml.Scalar(value.toString())

internal fun scalar(value: Boolean): SlmYaml = SlmYaml.Scalar(if (value) "true" else "false")

internal fun inlinePair(first: Double, second: Double): SlmYaml =
    SlmYaml.Sequence(listOf(scalar(first), scalar(second)), inline = true)

// --- enum tokens ---

/** Canonical SLM token of an enum constant: lowercase snake of the Kotlin name. */
internal fun Enum<*>.slmToken(): String = name.lowercase()

/** Parses [token] case-insensitively, accepting `-` for `_`; null on unknown values. */
internal inline fun <reified E : Enum<E>> enumFromToken(token: String): E? {
    val normalized = token.trim().lowercase().replace('-', '_')
    return enumValues<E>().firstOrNull { it.name.lowercase() == normalized }
}

// --- colors ---

/** Canonical `#AARRGGBB` uppercase hex. */
internal fun formatDiagramColor(color: DiagramColor): String =
    "#" + (color.argb and 0xFFFFFFFFu).toString(16).uppercase().padStart(8, '0')

/** Accepts `#RRGGBB` (alpha `FF`) and `#AARRGGBB`; null when malformed. */
internal fun parseDiagramColor(text: String): DiagramColor? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toULongOrNull(16) ?: return null
    return DiagramColor(if (hex.length == 6) value or 0xFF000000u else value)
}
