package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignExpression

/**
 * Layered data scope for `{{...}}` expressions: child bindings (repeat items, indexes)
 * shadow the parent chain, which bottoms out at [ResolveContext.data].
 */
class EvalScope(
    private val parent: EvalScope? = null,
    private val bindings: Map<String, DataValue> = emptyMap(),
) {
    fun lookup(name: String): DataValue? = bindings[name] ?: parent?.lookup(name)

    /** True when no binding is reachable anywhere in the chain (preview mode: no data wired). */
    val isEmpty: Boolean
        get() = bindings.isEmpty() && (parent?.isEmpty ?: true)
}

/**
 * Pure-Kotlin evaluator for [DesignExpression] raw strings against an [EvalScope].
 *
 * Grammar (see [DesignExpression]):
 * - dotted path segments: `user.name`;
 * - list indexing: `missions[0].id`;
 * - `.length` on lists and strings (an explicit map entry named "length" wins);
 * - one comparison `==` / `!=` / `<` / `<=` / `>` / `>=` of a path against a
 *   string (`'...'` or `"..."`), number, or bool literal;
 * - `!expr` negates the boolean value of the whole rest of the expression.
 *
 * A parse or evaluation failure returns null and reports the reason through the
 * diagnostics callback; the caller decides the fallback.
 */
object ExpressionEvaluator {

    fun evaluate(
        expression: DesignExpression,
        scope: EvalScope,
        onDiagnostic: (String) -> Unit = {},
    ): DataValue? =
        try {
            evaluateExpression(expression.raw.trim(), scope)
        } catch (failure: EvalException) {
            onDiagnostic(failure.message ?: "expression failed")
            null
        }

    /**
     * Parse-only validation entry: checks that [expression] matches the grammar without
     * evaluating it against any data. Returns the syntax error, or null when it parses.
     */
    fun syntaxErrorOrNull(expression: DesignExpression): String? =
        try {
            checkSyntax(expression.raw.trim())
            null
        } catch (failure: EvalException) {
            failure.message ?: "expression failed to parse"
        }

    private fun checkSyntax(raw: String) {
        if (raw.isEmpty()) throw EvalException("empty expression")
        if (raw.startsWith("!") && !raw.startsWith("!=")) {
            checkSyntax(raw.drop(1).trim())
            return
        }
        val comparison = splitComparison(raw)
        if (comparison != null) {
            val (leftRaw, _, rightRaw) = comparison
            parsePath(leftRaw.trim())
            parseLiteral(rightRaw.trim())
            return
        }
        parsePath(raw)
    }

    private fun evaluateExpression(raw: String, scope: EvalScope): DataValue {
        if (raw.isEmpty()) throw EvalException("empty expression")
        if (raw.startsWith("!") && !raw.startsWith("!=")) {
            val operand = evaluateExpression(raw.drop(1).trim(), scope)
            val bool = operand as? DataValue.Bool
                ?: throw EvalException("'!' needs a boolean operand, got ${describe(operand)}")
            return DataValue.Bool(!bool.value)
        }
        val comparison = splitComparison(raw)
        if (comparison != null) {
            val (leftRaw, operator, rightRaw) = comparison
            val left = evaluatePath(leftRaw.trim(), scope)
            val right = parseLiteral(rightRaw.trim())
            return DataValue.Bool(compare(left, operator, right))
        }
        return evaluatePath(raw, scope)
    }

    /** Splits at the first comparison operator outside of quotes, or null when there is none. */
    private fun splitComparison(raw: String): Triple<String, String, String>? {
        var index = 0
        var quote: Char? = null
        while (index < raw.length) {
            val ch = raw[index]
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '\'' || ch == '"' -> quote = ch
                ch == '=' && raw.getOrNull(index + 1) == '=' ->
                    return Triple(raw.take(index), "==", raw.substring(index + 2))
                ch == '!' && raw.getOrNull(index + 1) == '=' ->
                    return Triple(raw.take(index), "!=", raw.substring(index + 2))
                ch == '<' || ch == '>' -> {
                    val twoChar = raw.getOrNull(index + 1) == '='
                    val operator = if (twoChar) "$ch=" else ch.toString()
                    return Triple(raw.take(index), operator, raw.substring(index + operator.length))
                }
            }
            index++
        }
        return null
    }

    private fun parseLiteral(raw: String): DataValue =
        when {
            raw.isEmpty() -> throw EvalException("missing literal after comparison operator")
            raw == "true" -> DataValue.Bool(true)
            raw == "false" -> DataValue.Bool(false)
            raw == "null" -> DataValue.Null
            raw.length >= 2 && (raw.first() == '\'' || raw.first() == '"') && raw.last() == raw.first() ->
                DataValue.Str(raw.substring(1, raw.length - 1))
            else -> raw.toDoubleOrNull()?.let { DataValue.Num(it) }
                ?: throw EvalException("'$raw' is not a string/number/bool literal")
        }

    // --- Path resolution -----------------------------------------------------

    private sealed interface PathSegment {
        data class Member(val name: String) : PathSegment

        data class Index(val value: Int) : PathSegment
    }

    private fun evaluatePath(raw: String, scope: EvalScope): DataValue {
        val segments = parsePath(raw)
        val root = segments.first() as PathSegment.Member
        var current = scope.lookup(root.name)
            ?: throw EvalException("unknown name '${root.name}'")
        segments.drop(1).forEach { segment ->
            current = when (segment) {
                is PathSegment.Member -> member(current, segment.name)
                is PathSegment.Index -> index(current, segment.value)
            }
        }
        return current
    }

    private fun parsePath(raw: String): List<PathSegment> {
        if (raw.isEmpty()) throw EvalException("empty path")
        val segments = mutableListOf<PathSegment>()
        var index = 0

        fun readName(): String {
            val start = index
            while (index < raw.length && (raw[index].isLetterOrDigit() || raw[index] == '_')) index++
            if (index == start) throw EvalException("expected a name at offset $start in '$raw'")
            return raw.substring(start, index)
        }

        segments += PathSegment.Member(readName())
        while (index < raw.length) {
            when (raw[index]) {
                '.' -> {
                    index++
                    segments += PathSegment.Member(readName())
                }
                '[' -> {
                    val close = raw.indexOf(']', startIndex = index)
                    if (close < 0) throw EvalException("missing ']' in '$raw'")
                    val number = raw.substring(index + 1, close).trim().toIntOrNull()
                        ?: throw EvalException("index must be an integer in '$raw'")
                    segments += PathSegment.Index(number)
                    index = close + 1
                }
                else -> throw EvalException("unexpected '${raw[index]}' at offset $index in '$raw'")
            }
        }
        return segments
    }

    private fun member(value: DataValue, name: String): DataValue =
        when {
            value is DataValue.MapValue && name in value.entries -> value.entries.getValue(name)
            name == "length" && value is DataValue.ListValue -> DataValue.Num(value.items.size.toDouble())
            name == "length" && value is DataValue.Str -> DataValue.Num(value.value.length.toDouble())
            value is DataValue.MapValue -> throw EvalException("unknown member '$name'")
            else -> throw EvalException("cannot read '$name' of ${describe(value)}")
        }

    private fun index(value: DataValue, position: Int): DataValue {
        val list = value as? DataValue.ListValue
            ?: throw EvalException("cannot index ${describe(value)}")
        return list.items.getOrNull(position)
            ?: throw EvalException("index $position out of bounds (size ${list.items.size})")
    }

    // --- Comparison ------------------------------------------------------------

    private fun compare(left: DataValue, operator: String, right: DataValue): Boolean =
        when (operator) {
            "==" -> valuesEqual(left, right)
            "!=" -> !valuesEqual(left, right)
            else -> {
                val ordering = orderingOf(left, right, operator)
                when (operator) {
                    "<" -> ordering < 0
                    "<=" -> ordering <= 0
                    ">" -> ordering > 0
                    else -> ordering >= 0
                }
            }
        }

    /** Values of mismatched types are never equal. */
    private fun valuesEqual(left: DataValue, right: DataValue): Boolean =
        when {
            left is DataValue.Num && right is DataValue.Num -> left.value == right.value
            left is DataValue.Str && right is DataValue.Str -> left.value == right.value
            left is DataValue.Bool && right is DataValue.Bool -> left.value == right.value
            else -> left is DataValue.Null && right is DataValue.Null
        }

    private fun orderingOf(left: DataValue, right: DataValue, operator: String): Int =
        when {
            left is DataValue.Num && right is DataValue.Num -> left.value.compareTo(right.value)
            left is DataValue.Str && right is DataValue.Str -> left.value.compareTo(right.value)
            else -> throw EvalException(
                "'$operator' needs two numbers or two strings, got ${describe(left)} and ${describe(right)}",
            )
        }

    private fun describe(value: DataValue?): String =
        when (value) {
            is DataValue.Str -> "a string"
            is DataValue.Num -> "a number"
            is DataValue.Bool -> "a boolean"
            is DataValue.ListValue -> "a list"
            is DataValue.MapValue -> "a map"
            DataValue.Null, null -> "null"
        }

    private class EvalException(message: String) : Exception(message)
}
