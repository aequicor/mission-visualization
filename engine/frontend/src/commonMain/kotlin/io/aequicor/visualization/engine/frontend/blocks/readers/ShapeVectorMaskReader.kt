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
import io.aequicor.visualization.engine.ir.model.HandleMirror
import io.aequicor.visualization.engine.ir.model.HandleOffset
import io.aequicor.visualization.engine.ir.model.VectorNetwork
import io.aequicor.visualization.engine.ir.model.VectorPath
import io.aequicor.visualization.engine.ir.model.VectorRegion
import io.aequicor.visualization.engine.ir.model.VectorSegment
import io.aequicor.visualization.engine.ir.model.VectorVertex

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
    map.warnUnknownKeys(setOf("iconRef", "pathRef", "viewBox", "paths", "network", "boolean"), reading)
    return VectorPatch(
        iconRef = map.string("iconRef", reading),
        pathRef = map.string("pathRef", reading),
        viewBox = readViewBox(map.value("viewBox"), reading),
        paths = readVectorPaths(map, reading),
        network = readNetwork(map.mapValue("network", reading), reading),
        boolean = readBooleanOp(map, reading),
    )
}

/** `network:` sub-block — structural vector geometry (vertices with bezier handles, segments, regions). */
private fun readNetwork(map: YamlMap?, reading: BlockReading): VectorNetwork? {
    if (map == null) return null
    map.warnUnknownKeys(setOf("vertices", "segments", "regions"), reading)
    val vertices = readVertices(map, reading)
    if (vertices.isEmpty()) {
        reading.warning("`network` needs at least one vertex", map)
        return null
    }
    return VectorNetwork(vertices, readSegments(map, reading), readRegions(map, reading))
}

private fun readVertices(map: YamlMap, reading: BlockReading): List<VectorVertex> {
    val list = map.listValue("vertices", reading) ?: return emptyList()
    return list.items.mapNotNull { item ->
        val vertex = item as? YamlMap ?: run {
            reading.warning("`vertices` items must be maps", item)
            return@mapNotNull null
        }
        vertex.warnUnknownKeys(setOf("x", "y", "in", "out", "mirror", "corner"), reading)
        val x = vertex.double("x", reading) ?: run {
            reading.warning("Vertex needs `x`", vertex)
            return@mapNotNull null
        }
        val y = vertex.double("y", reading) ?: run {
            reading.warning("Vertex needs `y`", vertex)
            return@mapNotNull null
        }
        VectorVertex(
            x = x,
            y = y,
            inHandle = readOffset(vertex.value("in"), reading),
            outHandle = readOffset(vertex.value("out"), reading),
            mirror = vertex.enum("mirror", ReaderEnums.handleMirror, reading) ?: HandleMirror.None,
            corner = vertex.boolean("corner", reading) ?: false,
        )
    }
}

/** A handle offset `[dx, dy]`. */
private fun readOffset(value: YamlValue?, reading: BlockReading): HandleOffset? {
    if (value == null) return null
    val numbers = numberList(value) ?: run {
        reading.warning("Handle must be [dx, dy]", value)
        return null
    }
    if (numbers.size != 2) {
        reading.warning("Handle must be [dx, dy]", value)
        return null
    }
    return HandleOffset(numbers[0], numbers[1])
}

private fun readSegments(map: YamlMap, reading: BlockReading): List<VectorSegment> {
    val list = map.listValue("segments", reading) ?: return emptyList()
    return list.items.mapNotNull { item ->
        val numbers = numberList(item)
        if (numbers == null || numbers.size != 2) {
            reading.warning("Segment must be [from, to]", item)
            return@mapNotNull null
        }
        VectorSegment(numbers[0].toInt(), numbers[1].toInt())
    }
}

private fun readRegions(map: YamlMap, reading: BlockReading): List<VectorRegion> {
    val list = map.listValue("regions", reading) ?: return emptyList()
    return list.items.mapNotNull { item ->
        val region = item as? YamlMap ?: run {
            reading.warning("`regions` items must be maps", item)
            return@mapNotNull null
        }
        region.warnUnknownKeys(setOf("windingRule", "loops"), reading)
        val loops = (region.listValue("loops", reading)?.items ?: emptyList()).mapNotNull { loopItem ->
            numberList(loopItem)?.map { it.toInt() } ?: run {
                reading.warning("Loop must be a list of segment indices", loopItem)
                null
            }
        }
        VectorRegion(
            windingRule = region.string("windingRule", reading) ?: "nonzero",
            loops = loops,
        )
    }
}

private fun numberList(value: YamlValue?): List<Double>? {
    val list = value as? YamlList ?: return null
    return list.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
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
