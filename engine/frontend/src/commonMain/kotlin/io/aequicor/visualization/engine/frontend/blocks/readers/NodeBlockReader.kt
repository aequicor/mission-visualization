package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.NodePositionMode
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue

private val knownKeys = setOf(
    "type", "id", "name", "role", "visible", "locked", "order", "position", "constraints",
)

internal val positionModes: Map<String, NodePositionMode> = mapOf(
    "auto" to NodePositionMode.Auto,
    "absolute" to NodePositionMode.Absolute,
)

/**
 * `node:` block. `node: frame` scalar shorthand is normalized here to
 * `NodePatch(type = "frame")`. "screen"/"group"/"section" stay as type strings —
 * the IR maps them all to the Frame kind.
 */
internal fun readNodeBlock(value: YamlValue, reading: BlockReading): NodePatch? {
    if (value is YamlScalar) {
        val type = value.value as? String
        if (type == null) {
            reading.error("`node` shorthand must be a type name, e.g. `node: frame`", value)
            return null
        }
        return NodePatch(type = type)
    }
    val map = value as? YamlMap ?: run {
        reading.error("`node` must be a map or a type-name shorthand", value)
        return null
    }
    map.warnUnknownKeys(knownKeys, reading)
    val position = map.mapValue("position", reading)
    val constraints = map.mapValue("constraints", reading)
    return NodePatch(
        type = map.string("type", reading),
        id = map.string("id", reading),
        name = map.string("name", reading),
        role = map.string("role", reading),
        visible = map.bindableBoolean("visible", reading),
        locked = map.boolean("locked", reading),
        order = map.int("order", reading),
        positionMode = position?.enum("mode", positionModes, reading),
        x = position?.double("x", reading),
        y = position?.double("y", reading),
        rotation = position?.double("rotation", reading),
        constraintsHorizontal = constraints?.enum("horizontal", ReaderEnums.horizontalConstraint, reading),
        constraintsVertical = constraints?.enum("vertical", ReaderEnums.verticalConstraint, reading),
    )
}
