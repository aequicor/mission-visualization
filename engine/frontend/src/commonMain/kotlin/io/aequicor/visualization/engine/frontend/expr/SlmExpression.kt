package io.aequicor.visualization.engine.frontend.expr

/**
 * Parsed `{{...}}` expression. Grammar (design section D):
 *
 * ```
 * expr       := repeat | comparison | path | literal
 * repeat     := IDENT 'in' path
 * comparison := operand ('=='|'!='|'<'|'<='|'>'|'>=') operand
 * operand    := path | literal
 * path       := IDENT ('.' IDENT)*
 * literal    := number | '\''…'\'' | 'true' | 'false'
 * ```
 *
 * Anything unparseable becomes [Raw]; the caller decides whether to warn.
 */
sealed interface SlmExpression {
    /** Dotted data path, e.g. `mission.status` → `["mission", "status"]`. */
    data class Path(val segments: List<String>) : SlmExpression

    /** `mission in missions`. */
    data class Repeat(val itemName: String, val collection: Path) : SlmExpression

    /** `missions.length == 0`; operands are [Path] or [Literal]. */
    data class Comparison(
        val left: SlmExpression,
        val op: ComparisonOp,
        val right: SlmExpression,
    ) : SlmExpression

    sealed interface Literal : SlmExpression {
        data class Num(val value: Double) : Literal

        data class Str(val value: String) : Literal

        data class Bool(val value: Boolean) : Literal
    }

    /** Unparseable expression text, kept verbatim. */
    data class Raw(val text: String) : SlmExpression
}

enum class ComparisonOp(val symbol: String) {
    Eq("=="),
    Neq("!="),
    Lt("<"),
    Lte("<="),
    Gt(">"),
    Gte(">="),
    ;

    companion object {
        fun fromSymbol(symbol: String): ComparisonOp? = entries.firstOrNull { it.symbol == symbol }
    }
}
