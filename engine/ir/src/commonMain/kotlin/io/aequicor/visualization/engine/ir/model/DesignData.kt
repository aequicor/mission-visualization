package io.aequicor.visualization.engine.ir.model

/**
 * Opaque expression from `{{...}}`. Grammar: dotted path segments, `[index]`, `.length`,
 * `==`/`!=`/`<`/`<=`/`>`/`>=` against a string/number/bool literal, `!expr`. Parsed lazily
 * by the resolver's evaluator; invalid syntax surfaces as a diagnostic there.
 */
data class DesignExpression(val raw: String)

data class DesignCondition(val expression: DesignExpression)

/** Repeat directive, `{{mission in missions}}`: one clone of the node per collection item. */
data class DesignRepeat(
    val itemName: String,
    val collection: DesignExpression,
    val indexName: String? = null,
    /** Stable per-item id expression; falls back to the item index. */
    val key: DesignExpression? = null,
)

/** Runtime data value bound into the document through expressions. */
sealed interface DataValue {
    data class Str(val value: String) : DataValue

    data class Num(val value: Double) : DataValue

    data class Bool(val value: Boolean) : DataValue

    data class ListValue(val items: List<DataValue>) : DataValue

    data class MapValue(val entries: Map<String, DataValue>) : DataValue

    data object Null : DataValue
}
