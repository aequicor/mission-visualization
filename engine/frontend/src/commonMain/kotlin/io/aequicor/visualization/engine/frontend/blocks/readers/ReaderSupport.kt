package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.bindable

/**
 * Shared reading context of one typed-block entry: diagnostics are reported at
 * `file:line#<kind>` — the block path is the reserved key of the entry.
 */
internal class BlockReading(
    val diagnostics: DiagnosticCollector,
    val blockPath: String,
) {
    fun error(message: String, at: YamlValue) =
        diagnostics.error(message, at.line, blockPath = blockPath)

    fun warning(message: String, at: YamlValue) =
        diagnostics.warning(message, at.line, blockPath = blockPath)

    fun info(message: String, at: YamlValue) =
        diagnostics.info(message, at.line, blockPath = blockPath)
}

// --- basic accessors ---

internal fun YamlMap.value(key: String): YamlValue? = entries[key]

internal fun YamlMap.mapValue(key: String, reading: BlockReading): YamlMap? {
    val value = entries[key] ?: return null
    val map = value as? YamlMap
    if (map == null) reading.warning("`$key` must be a map", value)
    return map
}

internal fun YamlMap.listValue(key: String, reading: BlockReading): YamlList? {
    val value = entries[key] ?: return null
    val list = value as? YamlList
    if (list == null) reading.warning("`$key` must be a list", value)
    return list
}

internal fun YamlValue.stringOrNull(): String? = (this as? YamlScalar)?.value as? String

internal fun YamlMap.string(key: String, reading: BlockReading): String? {
    val value = entries[key] ?: return null
    val scalar = value as? YamlScalar ?: run {
        reading.warning("`$key` must be a string", value)
        return null
    }
    return when (val raw = scalar.value) {
        is String -> raw
        null -> null
        // Numbers/booleans used where strings are expected keep their source text.
        else -> scalar.raw
    }
}

internal fun YamlMap.double(key: String, reading: BlockReading): Double? {
    val value = entries[key] ?: return null
    val result = (value as? YamlScalar)?.value as? Double
    if (result == null) reading.warning("`$key` must be a number", value)
    return result
}

internal fun YamlMap.int(key: String, reading: BlockReading): Int? =
    double(key, reading)?.toInt()

internal fun YamlMap.boolean(key: String, reading: BlockReading): Boolean? {
    val value = entries[key] ?: return null
    val result = (value as? YamlScalar)?.value as? Boolean
    if (result == null) reading.warning("`$key` must be true or false", value)
    return result
}

internal fun YamlMap.stringList(key: String, reading: BlockReading): List<String>? {
    val value = entries[key] ?: return null
    val items = when (value) {
        is YamlList -> value.items
        is YamlScalar -> listOf(value)
        else -> {
            reading.warning("`$key` must be a list", value)
            return null
        }
    }
    return items.mapNotNull { item ->
        val text = item.stringOrNull()
        if (text == null) reading.warning("`$key` items must be strings", item)
        text
    }
}

/** Warns about keys the reader does not understand; typo safety net. */
internal fun YamlMap.warnUnknownKeys(known: Set<String>, reading: BlockReading) {
    entries.keys.filterNot { it in known }.forEach { key ->
        reading.warning("Unknown key \"$key\"", entries.getValue(key))
    }
}

// --- enum mapping: unknown value -> warning + null ---

internal fun <T : Any> YamlMap.enum(
    key: String,
    mapping: Map<String, T>,
    reading: BlockReading,
): T? {
    val value = entries[key] ?: return null
    return enumOf(value, key, mapping, reading)
}

internal fun <T : Any> enumOf(
    value: YamlValue,
    key: String,
    mapping: Map<String, T>,
    reading: BlockReading,
): T? {
    val text = value.stringOrNull() ?: run {
        reading.warning("`$key` must be one of ${mapping.keys}", value)
        return null
    }
    val mapped = mapping[text]
    if (mapped == null) {
        reading.warning("Unknown `$key` value \"$text\"; expected one of ${mapping.keys}", value)
    }
    return mapped
}

// --- bindable conversions ---

/** `{{expr}}` wrapper check; returns the inner expression text or null. */
internal fun expressionBody(text: String): String? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{{") || !trimmed.endsWith("}}")) return null
    return trimmed.substring(2, trimmed.length - 2).trim()
}

/** `$token.path` -> `token.path`; also `{ token: x }` / `{ variable: x }` maps. */
internal fun varRefOf(value: YamlValue): String? = when (value) {
    is YamlScalar -> (value.value as? String)?.takeIf { it.startsWith("$") }?.drop(1)
    is YamlMap -> value.value("variable")?.stringOrNull() ?: value.value("token")?.stringOrNull()
    else -> null
}

/**
 * A number-valued [Bindable]: literal number, `$token` / `{variable:|token:}` ref,
 * or `{{expr}}` data binding.
 */
internal fun bindableDouble(value: YamlValue, key: String, reading: BlockReading): Bindable<Double>? {
    varRefOf(value)?.let { return Bindable.VarRef(it) }
    val scalar = value as? YamlScalar
    when (val raw = scalar?.value) {
        is Double -> return raw.bindable()
        is String -> expressionBody(raw)?.let { return Bindable.DataRef(DesignExpression(it)) }
        else -> {}
    }
    reading.warning("`$key` must be a number, \$token ref or {{expr}}", value)
    return null
}

internal fun YamlMap.bindableDouble(key: String, reading: BlockReading): Bindable<Double>? {
    val value = entries[key] ?: return null
    return bindableDouble(value, key, reading)
}

internal fun YamlMap.bindableBoolean(key: String, reading: BlockReading): Bindable<Boolean>? {
    val value = entries[key] ?: return null
    varRefOf(value)?.let { return Bindable.VarRef(it) }
    val scalar = value as? YamlScalar
    when (val raw = scalar?.value) {
        is Boolean -> return raw.bindable()
        is String -> expressionBody(raw)?.let { return Bindable.DataRef(DesignExpression(it)) }
        else -> {}
    }
    reading.warning("`$key` must be true/false, \$token ref or {{expr}}", value)
    return null
}

private val rgbaRegex =
    Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([0-9.]+)\s*)?\)""")

/** `#hex`, `rgba(r,g,b,a)`, `$token`, `{token:|variable:}` or `{{expr}}` color. */
internal fun bindableColor(value: YamlValue, key: String, reading: BlockReading): Bindable<DesignColor>? {
    varRefOf(value)?.let { return Bindable.VarRef(it) }
    val text = value.stringOrNull()
    if (text != null) {
        expressionBody(text)?.let { return Bindable.DataRef(DesignExpression(it)) }
        if (text.startsWith("#")) {
            DesignColor.fromHex(text)?.let { return it.bindable() }
        }
        rgbaRegex.matchEntire(text.trim())?.let { match ->
            val (r, g, b) = match.destructured
            val alpha = match.groupValues[4].toDoubleOrNull() ?: 1.0
            val argb = ((alpha * 255).toLong().coerceIn(0, 255) shl 24) or
                (r.toLong().coerceIn(0, 255) shl 16) or
                (g.toLong().coerceIn(0, 255) shl 8) or
                b.toLong().coerceIn(0, 255)
            return DesignColor(argb).bindable()
        }
    }
    reading.warning("`$key` must be a #hex color, rgba(), token or variable ref", value)
    return null
}

internal fun YamlMap.bindableColor(key: String, reading: BlockReading): Bindable<DesignColor>? {
    val value = entries[key] ?: return null
    return bindableColor(value, key, reading)
}

/** A string-valued [Bindable]; `{{expr}}` becomes a data ref. */
internal fun bindableString(value: YamlValue, key: String, reading: BlockReading): Bindable<String>? {
    varRefOf(value)?.let { return Bindable.VarRef(it) }
    val scalar = value as? YamlScalar ?: run {
        reading.warning("`$key` must be a string", value)
        return null
    }
    val text = scalar.value?.let { if (it is String) it else scalar.raw } ?: return "".bindable()
    expressionBody(text)?.let { return Bindable.DataRef(DesignExpression(it)) }
    return text.bindable()
}
