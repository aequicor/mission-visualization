package io.aequicor.visualization.engine.ir.resolve

import kotlin.math.abs
import kotlin.math.floor

/**
 * Pure-Kotlin approximation of ICU MessageFormat used for i18n text resolution.
 *
 * Supported subset:
 * - literal text;
 * - `{param}` substitution (a missing param keeps the `{param}` placeholder verbatim);
 * - `{param, plural, one {...} few {...} many {...} other {...}}` with `#` substituted by the
 *   numeric param value and exact `=N` selectors matched before categories;
 * - `{param, select, key {...} other {...}}`;
 * - nested braces and nested arguments inside plural/select branches.
 *
 * Plural rules cover CLDR cardinal rules for `en` (n == 1 -> one) and `ru`
 * (mod10 == 1 && mod100 != 11 -> one; mod10 in 2..4 && mod100 !in 12..14 -> few; other
 * integers -> many; fractions -> other); any other language falls back to the English rule.
 * Numeric values are formatted without a decimal point when they are whole.
 *
 * NOT supported (documented approximation of full ICU): `''` quoting/escaping, `offset:`,
 * ordinal plurals, argument skeletons (number/date/time formats). A malformed message is
 * returned verbatim and reported through [format]'s diagnostics callback.
 */
object IcuLiteFormatter {

    fun format(
        message: String,
        params: Map<String, String>,
        locale: String,
        onDiagnostic: (String) -> Unit = {},
    ): String =
        try {
            formatPart(message, params, languageOf(locale), onDiagnostic, numberText = null)
        } catch (failure: IcuSyntaxException) {
            onDiagnostic("Malformed ICU message '${message.take(120)}': ${failure.message}")
            message
        }

    private fun languageOf(locale: String): String =
        locale.substringBefore('-').substringBefore('_').trim().lowercase()

    /**
     * Parse-only inspection for validators: walks the whole message — every argument and
     * every plural/select branch, not just the branch formatting would pick — and reports
     * the first syntax error plus all arguments seen so far.
     */
    fun inspect(message: String): IcuInspection {
        val arguments = mutableListOf<IcuArgument>()
        return try {
            inspectPart(message, arguments)
            IcuInspection(syntaxError = null, arguments = arguments)
        } catch (failure: IcuSyntaxException) {
            IcuInspection(syntaxError = failure.message, arguments = arguments)
        }
    }

    private fun inspectPart(part: String, sink: MutableList<IcuArgument>) {
        var index = 0
        while (index < part.length) {
            when (part[index]) {
                '{' -> {
                    val end = matchingBrace(part, index)
                    inspectArgument(part.substring(index + 1, end), sink)
                    index = end + 1
                }
                '}' -> throw IcuSyntaxException("unexpected '}'")
                else -> index++
            }
        }
    }

    private fun inspectArgument(body: String, sink: MutableList<IcuArgument>) {
        val firstComma = topLevelComma(body)
        if (firstComma < 0) {
            val name = body.trim()
            if (name.isEmpty()) throw IcuSyntaxException("empty argument name")
            sink += IcuArgument(name = name, type = "", selectors = emptyList())
            return
        }
        val name = body.take(firstComma).trim()
        if (name.isEmpty()) throw IcuSyntaxException("empty argument name")
        val rest = body.substring(firstComma + 1)
        val secondComma = topLevelComma(rest)
        if (secondComma < 0) throw IcuSyntaxException("argument '$name' is missing branches")
        val keyword = rest.take(secondComma).trim()
        if (keyword != "plural" && keyword != "select") {
            throw IcuSyntaxException("unsupported argument type '$keyword'")
        }
        val branches = parseBranches(rest.substring(secondComma + 1), name)
        sink += IcuArgument(name = name, type = keyword, selectors = branches.map { it.first })
        branches.forEach { (_, content) -> inspectPart(content, sink) }
    }

    private fun formatPart(
        part: String,
        params: Map<String, String>,
        language: String,
        onDiagnostic: (String) -> Unit,
        /** Formatted value substituted for `#`; null outside plural branches. */
        numberText: String?,
    ): String {
        val out = StringBuilder()
        var index = 0
        while (index < part.length) {
            when (val ch = part[index]) {
                '#' -> {
                    out.append(numberText ?: "#")
                    index++
                }
                '{' -> {
                    val end = matchingBrace(part, index)
                    out.append(formatArgument(part.substring(index + 1, end), params, language, onDiagnostic, numberText))
                    index = end + 1
                }
                '}' -> throw IcuSyntaxException("unexpected '}'")
                else -> {
                    out.append(ch)
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun formatArgument(
        body: String,
        params: Map<String, String>,
        language: String,
        onDiagnostic: (String) -> Unit,
        numberText: String?,
    ): String {
        val firstComma = topLevelComma(body)
        if (firstComma < 0) {
            val name = body.trim()
            if (name.isEmpty()) throw IcuSyntaxException("empty argument name")
            // A missing param keeps the placeholder verbatim (e.g. unevaluated data bindings).
            return params[name] ?: "{$name}"
        }
        val name = body.take(firstComma).trim()
        if (name.isEmpty()) throw IcuSyntaxException("empty argument name")
        val rest = body.substring(firstComma + 1)
        val secondComma = topLevelComma(rest)
        if (secondComma < 0) throw IcuSyntaxException("argument '$name' is missing branches")
        val keyword = rest.take(secondComma).trim()
        if (keyword != "plural" && keyword != "select") {
            throw IcuSyntaxException("unsupported argument type '$keyword'")
        }
        val branches = parseBranches(rest.substring(secondComma + 1), name)
        return if (keyword == "plural") {
            formatPlural(name, branches, params, language, onDiagnostic)
        } else {
            formatSelect(name, branches, params, language, onDiagnostic, numberText)
        }
    }

    private fun formatPlural(
        name: String,
        branches: List<Pair<String, String>>,
        params: Map<String, String>,
        language: String,
        onDiagnostic: (String) -> Unit,
    ): String {
        val raw = params[name]
        val number = raw?.trim()?.toDoubleOrNull()
        if (number == null) {
            onDiagnostic("Plural parameter '$name' is missing or not a number; using 'other'")
        }
        // A non-numeric param substitutes # verbatim; a missing param keeps the # marker.
        val numberText = number?.let(::formatNumber) ?: raw
        val branch = number?.let { value ->
            branches.firstOrNull { (selector, _) ->
                selector.startsWith("=") && selector.drop(1).toDoubleOrNull() == value
            }
        }
            ?: number?.let { value -> branches.firstOrNull { it.first == pluralCategory(language, value) } }
            ?: branches.firstOrNull { it.first == "other" }
            ?: throw IcuSyntaxException("plural argument '$name' has no matching branch and no 'other'")
        return formatPart(branch.second, params, language, onDiagnostic, numberText)
    }

    private fun formatSelect(
        name: String,
        branches: List<Pair<String, String>>,
        params: Map<String, String>,
        language: String,
        onDiagnostic: (String) -> Unit,
        numberText: String?,
    ): String {
        val value = params[name]
        if (value == null) {
            onDiagnostic("Select parameter '$name' is missing; using 'other'")
        }
        val branch = value?.let { selected -> branches.firstOrNull { it.first == selected } }
            ?: branches.firstOrNull { it.first == "other" }
            ?: throw IcuSyntaxException("select argument '$name' has no matching branch and no 'other'")
        return formatPart(branch.second, params, language, onDiagnostic, numberText)
    }

    /** `selector {content}` pairs; content may contain nested braces. */
    private fun parseBranches(source: String, argumentName: String): List<Pair<String, String>> {
        val branches = mutableListOf<Pair<String, String>>()
        var index = 0
        while (index < source.length) {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) break
            val selectorStart = index
            while (index < source.length && source[index] != '{' && !source[index].isWhitespace()) index++
            val selector = source.substring(selectorStart, index)
            if (selector.isEmpty()) throw IcuSyntaxException("branch of '$argumentName' is missing a selector")
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length || source[index] != '{') {
                throw IcuSyntaxException("branch '$selector' of '$argumentName' is missing '{'")
            }
            val end = matchingBrace(source, index)
            branches += selector to source.substring(index + 1, end)
            index = end + 1
        }
        if (branches.isEmpty()) throw IcuSyntaxException("argument '$argumentName' has no branches")
        return branches
    }

    /** Index of the first top-level (nesting depth 0) comma, or -1. */
    private fun topLevelComma(source: String): Int {
        var depth = 0
        source.forEachIndexed { index, ch ->
            when (ch) {
                '{' -> depth++
                '}' -> depth--
                ',' -> if (depth == 0) return index
            }
        }
        return -1
    }

    /** Index of the brace matching the `{` at [open]; throws when unbalanced. */
    private fun matchingBrace(source: String, open: Int): Int {
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        throw IcuSyntaxException("unbalanced '{'")
    }

    private fun pluralCategory(language: String, value: Double): String {
        val isWhole = value == floor(value) && !value.isInfinite()
        return when (language) {
            "ru" -> {
                if (!isWhole) return "other"
                val n = abs(value).toLong()
                val mod10 = n % 10
                val mod100 = n % 100
                when {
                    mod10 == 1L && mod100 != 11L -> "one"
                    mod10 in 2L..4L && mod100 !in 12L..14L -> "few"
                    else -> "many"
                }
            }
            else -> if (value == 1.0) "one" else "other"
        }
    }

    /** Whole numbers print without a decimal point. */
    private fun formatNumber(value: Double): String =
        if (value == floor(value) && !value.isInfinite() && abs(value) < Long.MAX_VALUE.toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private class IcuSyntaxException(message: String) : Exception(message)
}

/** Result of [IcuLiteFormatter.inspect]: first syntax error (if any) and the arguments seen. */
data class IcuInspection(
    val syntaxError: String?,
    val arguments: List<IcuArgument>,
)

/** One ICU argument occurrence: `{name}` (type "") or `{name, plural|select, ...}`. */
data class IcuArgument(
    val name: String,
    val type: String,
    val selectors: List<String>,
)
