package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.BooleanOpPatch
import io.aequicor.visualization.engine.frontend.blocks.MaskPatch
import io.aequicor.visualization.engine.frontend.blocks.ShapePatch
import io.aequicor.visualization.engine.frontend.blocks.VectorPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignViewBox
import io.aequicor.visualization.engine.ir.model.VectorPath

/** `shape:` block — parametric primitive geometry. */
internal fun readShapeBlock(value: YamlValue, reading: BlockReading): ShapePatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`shape` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("kind", "width", "height", "pointCount", "innerRadius"), reading)
    return ShapePatch(
        kind = map.enum("kind", ReaderEnums.shapeKind, reading),
        width = map.double("width", reading),
        height = map.double("height", reading),
        pointCount = map.int("pointCount", reading),
        innerRadius = map.double("innerRadius", reading),
    )
}

/** `vector:` block — icon/path refs, inline paths, boolean operation. */
internal fun readVectorBlock(value: YamlValue, reading: BlockReading): VectorPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`vector` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("iconRef", "pathRef", "viewBox", "paths", "boolean"), reading)
    return VectorPatch(
        iconRef = map.string("iconRef", reading),
        pathRef = map.string("pathRef", reading),
        viewBox = readViewBox(map.value("viewBox"), reading),
        paths = readVectorPaths(map, reading),
        boolean = readBooleanOp(map, reading),
    )
}

/** `viewBox: [x, y, w, h]`. */
private fun readViewBox(value: YamlValue?, reading: BlockReading): DesignViewBox? {
    if (value == null) return null
    val list = value as? YamlList ?: run {
        reading.warning("`viewBox` must be [x, y, width, height]", value)
        return null
    }
    val numbers = list.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
    if (numbers.size != 4) {
        reading.warning("`viewBox` must be [x, y, width, height]", value)
        return null
    }
    return DesignViewBox(numbers[0], numbers[1], numbers[2], numbers[3])
}

private fun readVectorPaths(map: YamlMap, reading: BlockReading): List<VectorPath>? {
    val list = map.listValue("paths", reading) ?: return null
    return list.items.mapNotNull { item ->
        val path = item as? YamlMap ?: run {
            reading.warning("`paths` items must be maps", item)
            return@mapNotNull null
        }
        path.warnUnknownKeys(setOf("d", "windingRule"), reading)
        val d = path.string("d", reading) ?: run {
            reading.warning("Path entry needs `d`", path)
            return@mapNotNull null
        }
        VectorPath(
            windingRule = path.string("windingRule", reading) ?: "nonzero",
            d = d,
        )
    }
}

private fun readBooleanOp(map: YamlMap, reading: BlockReading): BooleanOpPatch? {
    val boolean = map.mapValue("boolean", reading) ?: return null
    boolean.warnUnknownKeys(setOf("op", "children"), reading)
    val op = boolean.enum("op", ReaderEnums.booleanOp, reading) ?: return null
    return BooleanOpPatch(
        op = op,
        children = boolean.stringList("children", reading) ?: emptyList(),
    )
}

/** `mask:` block. */
internal fun readMaskBlock(value: YamlValue, reading: BlockReading): MaskPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`mask` must be a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("type", "source", "appliesTo"), reading)
    return MaskPatch(
        type = map.enum("type", ReaderEnums.maskType, reading),
        source = map.string("source", reading),
        appliesTo = map.stringList("appliesTo", reading),
    )
}
