package io.aequicor.visualization.engine.frontend.edit

import kotlin.math.abs
import kotlin.math.floor

/**
 * Canonical scalar rendering for surgical YAML writes (design J.B item 5):
 * integers without a decimal point, minimal doubles, `true`/`false`, `$token`
 * refs verbatim, plain strings when they match `^[A-Za-z0-9_$./-]+$` (and cannot
 * be re-read as a bool/number/null), double-quoted with escapes otherwise. When
 * replacing an existing quoted scalar, the original quote style is preserved.
 */
internal object ScalarFormatter {
    private val plainRegex = Regex("""^[A-Za-z0-9_$./-]+$""")
    private val numberRegex = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")
    private val reservedWords = setOf("true", "false", "null", "~")

    /** Renders [value]; [existingRaw] is the replaced source token, when replacing. */
    fun format(value: YamlScalarValue, existingRaw: String? = null): String = when (value) {
        is YamlScalarValue.Num -> formatNumber(value.value)
        is YamlScalarValue.Bool -> if (value.value) "true" else "false"
        is YamlScalarValue.TokenRef ->
            if (value.token.startsWith("$")) value.token else "$" + value.token
        is YamlScalarValue.Str -> formatString(value.value, existingRaw)
    }

    private fun formatNumber(value: Double): String =
        if (value == floor(value) && !value.isInfinite() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private fun formatString(text: String, existingRaw: String?): String = when {
        existingRaw?.startsWith("\"") == true -> doubleQuoted(text)
        existingRaw?.startsWith("'") == true -> singleQuoted(text)
        isPlain(text) -> text
        else -> doubleQuoted(text)
    }

    private fun isPlain(text: String): Boolean =
        plainRegex.matches(text) && text !in reservedWords && !numberRegex.matches(text)

    private fun doubleQuoted(text: String): String = buildString {
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

    private fun singleQuoted(text: String): String = "'" + text.replace("'", "''") + "'"
}
