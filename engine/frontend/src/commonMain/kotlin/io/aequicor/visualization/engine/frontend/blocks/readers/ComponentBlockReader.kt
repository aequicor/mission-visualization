package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ComponentPatch
import io.aequicor.visualization.engine.frontend.blocks.NestedInstancePatch
import io.aequicor.visualization.engine.frontend.blocks.OverridesPatch
import io.aequicor.visualization.engine.frontend.blocks.PropsPatch
import io.aequicor.visualization.engine.frontend.blocks.SlotOverridePatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextContent

private val componentKeys = setOf(
    "ref", "libraryRef", "name", "variant", "variants", "properties", "props",
    "detach", "resetOverrides",
)

/** `component:` block — instance reference or component definition. */
internal fun readComponentBlock(value: YamlValue, reading: BlockReading): ComponentPatch? {
    if (value is YamlScalar) {
        val ref = value.value as? String ?: run {
            reading.error("`component` shorthand must be a ref, e.g. `component: ds/Button`", value)
            return null
        }
        return ComponentPatch(ref = ref)
    }
    val map = value as? YamlMap ?: run {
        reading.error("`component` must be a map or a ref shorthand", value)
        return null
    }
    map.warnUnknownKeys(componentKeys, reading)
    return ComponentPatch(
        ref = map.string("ref", reading),
        libraryRef = map.string("libraryRef", reading),
        name = map.string("name", reading),
        variant = map.mapValue("variant", reading)?.stringEntries(reading),
        variantsAxes = readVariantAxes(map, reading),
        properties = readPropertyDefinitions(map, reading),
        props = map.mapValue("props", reading)?.let { readProps(it, reading) },
        detach = map.boolean("detach", reading),
        resetOverrides = map.boolean("resetOverrides", reading),
    )
}

/** `props:` block — bare property values on an instance anchor. */
internal fun readPropsBlock(value: YamlValue, reading: BlockReading): PropsPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`props` must be a map", value)
        return null
    }
    return PropsPatch(readProps(map, reading))
}

/** `overrides:` block — slot fills and nested-instance overrides. */
internal fun readOverridesBlock(value: YamlValue, reading: BlockReading): OverridesPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`overrides` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("slots", "nestedInstances"), reading)
    val slots = map.mapValue("slots", reading)?.entries?.mapValues { (slotName, fills) ->
        val list = fills as? io.aequicor.visualization.engine.frontend.yaml.YamlList ?: run {
            reading.warning("Slot \"$slotName\" must be a list of instances", fills)
            return@mapValues emptyList()
        }
        list.items.mapNotNull { item ->
            val fill = item as? YamlMap ?: run {
                reading.warning("Slot entries must be maps", item)
                return@mapNotNull null
            }
            val instance = fill.string("instance", reading) ?: run {
                reading.warning("Slot entry needs `instance`", fill)
                return@mapNotNull null
            }
            SlotOverridePatch(
                instanceRef = instance,
                props = fill.mapValue("props", reading)?.let { readProps(it, reading) } ?: emptyMap(),
                variant = fill.mapValue("variant", reading)?.stringEntries(reading) ?: emptyMap(),
            )
        }
    }
    val nested = map.mapValue("nestedInstances", reading)?.entries?.mapValues { (id, override) ->
        val overrideMap = override as? YamlMap ?: run {
            reading.warning("Nested instance \"$id\" override must be a map", override)
            return@mapValues NestedInstancePatch()
        }
        NestedInstancePatch(
            variant = overrideMap.mapValue("variant", reading)?.stringEntries(reading),
            props = overrideMap.mapValue("props", reading)?.let { readProps(it, reading) },
        )
    }
    return OverridesPatch(slots = slots, nestedInstances = nested)
}

// --- shared prop value reading ---

internal fun readProps(map: YamlMap, reading: BlockReading): Map<String, PropValue> =
    map.entries.mapNotNull { (name, value) ->
        readPropValue(value, name, reading)?.let { name to it }
    }.toMap()

/**
 * A prop value: scalar (`Открыть`, `42`, `true`), a `"{{expr}}"` data binding, or the
 * explicit `{type, value, i18nKey}` map form.
 */
internal fun readPropValue(value: YamlValue, name: String, reading: BlockReading): PropValue? {
    if (value is YamlScalar) {
        return when (val raw = value.value) {
            is Boolean -> PropValue.Bool(raw)
            is Double -> PropValue.Number(raw)
            is String -> expressionBody(raw)
                ?.let { PropValue.Data(DesignExpression(it)) }
                ?: PropValue.Text(raw)
            null -> PropValue.Text("")
            else -> null
        }
    }
    val map = value as? YamlMap ?: run {
        reading.warning("Prop \"$name\" must be a scalar or a {type, value} map", value)
        return null
    }
    val type = map.string("type", reading)
    val i18nKey = map.string("i18nKey", reading)
    val rawValue = map.value("value")
    return when (type) {
        "text" -> {
            val text = rawValue?.stringOrNull().orEmpty()
            if (i18nKey != null) {
                PropValue.Content(TextContent(key = i18nKey, defaultText = text))
            } else {
                expressionBody(text)?.let { PropValue.Data(DesignExpression(it)) }
                    ?: PropValue.Text(text)
            }
        }
        "boolean" -> PropValue.Bool((rawValue as? YamlScalar)?.value as? Boolean ?: false)
        "number" -> PropValue.Number((rawValue as? YamlScalar)?.value as? Double ?: 0.0)
        "instanceSwap", "variant" -> PropValue.Reference(rawValue?.stringOrNull().orEmpty())
        "string" -> PropValue.Text(rawValue?.stringOrNull().orEmpty())
        "dataBinding" -> {
            val expr = rawValue?.stringOrNull().orEmpty()
            PropValue.Data(DesignExpression(expressionBody(expr) ?: expr))
        }
        null -> {
            reading.warning("Prop \"$name\" map needs a `type`", map)
            null
        }
        else -> {
            reading.warning("Unknown prop type \"$type\" for \"$name\"", map)
            null
        }
    }
}

// --- definition side ---

private fun readVariantAxes(map: YamlMap, reading: BlockReading): Map<String, List<String>>? {
    val variants = map.mapValue("variants", reading) ?: return null
    return variants.entries.mapNotNull { (axis, spec) ->
        val axisMap = spec as? YamlMap ?: run {
            reading.warning("Variant axis \"$axis\" must be a map with `values`", spec)
            return@mapNotNull null
        }
        val values = axisMap.stringList("values", reading) ?: run {
            reading.warning("Variant axis \"$axis\" needs `values`", axisMap)
            return@mapNotNull null
        }
        axis to values
    }.toMap()
}

private fun readPropertyDefinitions(
    map: YamlMap,
    reading: BlockReading,
): Map<String, ComponentPropertyDefinition>? {
    val properties = map.mapValue("properties", reading) ?: return null
    return properties.entries.mapNotNull { (name, spec) ->
        val propMap = spec as? YamlMap ?: run {
            reading.warning("Property \"$name\" must be a map", spec)
            return@mapNotNull null
        }
        propMap.warnUnknownKeys(
            setOf("type", "default", "preferredValues", "minItems", "maxItems", "allowedContent"),
            reading,
        )
        val type = propMap.enum("type", ReaderEnums.componentPropertyType, reading)
            ?: return@mapNotNull null
        name to ComponentPropertyDefinition(
            type = type,
            default = propMap.value("default")?.let { readPropValue(it, name, reading) },
            preferredValues = propMap.stringList("preferredValues", reading) ?: emptyList(),
            minItems = propMap.int("minItems", reading),
            maxItems = propMap.int("maxItems", reading),
            allowedContent = propMap.stringList("allowedContent", reading) ?: emptyList(),
        )
    }.toMap()
}

/** A map whose values are all plain strings (variant selections). */
internal fun YamlMap.stringEntries(reading: BlockReading): Map<String, String> =
    entries.mapNotNull { (key, value) ->
        val scalar = value as? YamlScalar
        val text = when (val raw = scalar?.value) {
            is String -> raw
            is Boolean, is Double -> scalar.raw
            else -> null
        }
        if (text == null) {
            reading.warning("`$key` must be a string", value)
            null
        } else {
            key to text
        }
    }.toMap()
