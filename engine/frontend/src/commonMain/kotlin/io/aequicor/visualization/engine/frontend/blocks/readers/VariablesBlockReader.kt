package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.VariablesPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue

/** `variables:` block — collections with modes plus prototype variables. */
internal fun readVariablesBlock(value: YamlValue, reading: BlockReading): VariablesPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`variables` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("collections", "prototype"), reading)
    return VariablesPatch(
        collections = readCollections(map, reading),
        prototype = readPrototype(map, reading),
    )
}

private fun readCollections(map: YamlMap, reading: BlockReading): Map<String, VariableCollection>? {
    val list = map.listValue("collections", reading) ?: return null
    val collections = LinkedHashMap<String, VariableCollection>()
    list.items.forEach { item ->
        val collection = item as? YamlMap ?: run {
            reading.warning("`collections` items must be maps", item)
            return@forEach
        }
        collection.warnUnknownKeys(setOf("id", "name", "modes", "defaultMode", "variables"), reading)
        val id = collection.string("id", reading) ?: run {
            reading.warning("Collection needs an `id`", collection)
            return@forEach
        }
        val modes = collection.stringList("modes", reading) ?: emptyList()
        collections[id] = VariableCollection(
            name = collection.string("name", reading) ?: id,
            modes = modes,
            defaultMode = collection.string("defaultMode", reading)
                ?: modes.firstOrNull().orEmpty(),
            vars = readVariables(collection, modes, reading),
        )
    }
    return collections
}

private fun readVariables(
    collection: YamlMap,
    modes: List<String>,
    reading: BlockReading,
): Map<String, DesignVariable> {
    val variables = collection.mapValue("variables", reading) ?: return emptyMap()
    return variables.entries.mapNotNull { (name, spec) ->
        val varMap = spec as? YamlMap ?: run {
            reading.warning("Variable \"$name\" must be a map", spec)
            return@mapNotNull null
        }
        varMap.warnUnknownKeys(setOf("type", "values"), reading)
        val type = varMap.enum("type", ReaderEnums.variableType, reading) ?: return@mapNotNull null
        val values = varMap.mapValue("values", reading)?.entries?.mapNotNull { (mode, raw) ->
            if (modes.isNotEmpty() && mode !in modes) {
                reading.warning("Variable \"$name\" value for unknown mode \"$mode\"", raw)
            }
            readVariableValue(raw, type, name, reading)?.let { mode to it }
        }?.toMap() ?: emptyMap()
        name to DesignVariable(type = type, values = values)
    }.toMap()
}

private fun readVariableValue(
    value: YamlValue,
    type: VariableType,
    name: String,
    reading: BlockReading,
): VariableValue? {
    varRefOf(value)?.let { return VariableValue.Alias(it) }
    val scalar = value as? YamlScalar ?: run {
        reading.warning("Variable \"$name\" values must be scalars", value)
        return null
    }
    return when (type) {
        VariableType.Color -> {
            val text = scalar.value as? String
            val color = text?.let { DesignColor.fromHex(it) }
            if (color == null) {
                reading.warning("Variable \"$name\" needs a #hex color value", value)
                null
            } else {
                VariableValue.ColorValue(color)
            }
        }
        VariableType.Number -> (scalar.value as? Double)?.let { VariableValue.NumberValue(it) }
            ?: run {
                reading.warning("Variable \"$name\" needs a number value", value)
                null
            }
        VariableType.Bool -> (scalar.value as? Boolean)?.let { VariableValue.BoolValue(it) }
            ?: run {
                reading.warning("Variable \"$name\" needs a boolean value", value)
                null
            }
        VariableType.Text -> VariableValue.TextValue(
            (scalar.value as? String) ?: scalar.raw,
        )
    }
}

private fun readPrototype(map: YamlMap, reading: BlockReading): Map<String, PrototypeVariable>? {
    val prototype = map.mapValue("prototype", reading) ?: return null
    return prototype.entries.mapNotNull { (name, spec) ->
        val varMap = spec as? YamlMap ?: run {
            reading.warning("Prototype variable \"$name\" must be a map", spec)
            return@mapNotNull null
        }
        varMap.warnUnknownKeys(setOf("type", "default"), reading)
        val type = varMap.enum("type", ReaderEnums.variableType, reading) ?: return@mapNotNull null
        val default = varMap.value("default")?.let { readVariableValue(it, type, name, reading) }
        name to PrototypeVariable(type = type, default = default)
    }.toMap()
}
