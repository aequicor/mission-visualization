package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.SizingPatch
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.SizingMode

private val knownKeys = setOf(
    "mode", "padding", "gap", "align", "distribution", "wrap", "sizing", "size", "clipContent",
    "overflow", "scroll", "ignoreAutoLayout", "position", "columns", "rows", "placement",
    "guides", "grids",
)

/** `layout:` block — auto layout, grid, absolute positioning, scroll, guides, grids. */
internal fun readLayoutBlock(value: YamlValue, reading: BlockReading): LayoutPatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`layout` must be a map", value)
        return null
    }
    map.warnUnknownKeys(knownKeys, reading)

    val padding = readPadding(map, reading)
    val gap = readGap(map, reading)
    val align = map.mapValue("align", reading)
    val sizing = map.mapValue("sizing", reading)
    val size = readSize(map, reading)
    val overflow = map.mapValue("overflow", reading)
    val scroll = map.mapValue("scroll", reading)
    val position = readPosition(map, reading)
    val columns = readTrackAxis(map, "columns", reading)
    val rows = readTrackAxis(map, "rows", reading)

    return LayoutPatch(
        mode = map.enum("mode", ReaderEnums.layoutMode, reading),
        paddingBlockStart = padding.blockStart,
        paddingInlineEnd = padding.inlineEnd,
        paddingBlockEnd = padding.blockEnd,
        paddingInlineStart = padding.inlineStart,
        gap = gap.main,
        rowGap = gap.row ?: rows?.gap,
        columnGap = gap.column ?: columns?.gap,
        alignInline = align?.enum("inline", ReaderEnums.align, reading),
        alignBlock = align?.enum("block", ReaderEnums.align, reading),
        baseline = align?.enum("baseline", ReaderEnums.baseline, reading),
        distribution = map.enum("distribution", ReaderEnums.distribution, reading),
        wrap = map.boolean("wrap", reading),
        sizingWidth = sizing?.value("width")?.let { readSizingAxis(it, "width", reading) } ?: size?.first,
        sizingHeight = sizing?.value("height")?.let { readSizingAxis(it, "height", reading) } ?: size?.second,
        clipContent = map.boolean("clipContent", reading),
        overflowX = overflow?.enum("x", ReaderEnums.overflow, reading),
        overflowY = overflow?.enum("y", ReaderEnums.overflow, reading),
        scrollDirection = scroll?.enum("direction", ReaderEnums.scrollDirection, reading),
        scrollSticky = scroll?.boolean("sticky", reading),
        scrollFixedChildren = scroll?.stringList("fixedChildren", reading),
        ignoreAutoLayout = map.boolean("ignoreAutoLayout", reading),
        positionMode = position?.map?.enum("mode", positionModes, reading),
        anchorInlineStart = position?.map?.anchor("inlineStart", "left", reading),
        anchorInlineEnd = position?.map?.anchor("inlineEnd", "right", reading),
        anchorBlockStart = position?.map?.anchor("blockStart", "top", reading),
        anchorBlockEnd = position?.map?.anchor("blockEnd", "bottom", reading),
        positionX = position?.x,
        positionY = position?.y,
        gridColumns = columns?.tracks,
        gridRows = rows?.tracks,
        implicitRows = rows?.implicitTrack,
        implicitRowMin = rows?.min,
        placement = readPlacement(map, reading),
        guides = readGuides(map, reading),
        grids = readGrids(map, reading),
    )
}

// --- position: map (mode/anchors/x/y) or `[x, y]` shorthand pair ---

private class PositionSpec(val map: YamlMap?, val x: Double?, val y: Double?)

private fun readPosition(map: YamlMap, reading: BlockReading): PositionSpec? {
    val value = map.value("position") ?: return null
    return when (value) {
        is YamlMap -> PositionSpec(value, value.double("x", reading), value.double("y", reading))
        is YamlList -> {
            if (value.items.size != 2) {
                reading.warning("`position` list must be `[x, y]`", value)
                PositionSpec(null, null, null)
            } else {
                PositionSpec(null, value.numberAt(0), value.numberAt(1))
            }
        }
        else -> {
            reading.warning("`position` must be a map or a `[x, y]` pair", value)
            null
        }
    }
}

// --- sizing ---

/** `size: [<w>, <h>]` shorthand where each axis is `fill`/`hug`/`fixed`/a number or a map. */
private fun readSize(map: YamlMap, reading: BlockReading): Pair<SizingPatch?, SizingPatch?>? {
    val value = map.value("size") ?: return null
    val list = value as? YamlList
    if (list == null || list.items.size != 2) {
        reading.warning("`size` must be a `[width, height]` pair", value)
        return null
    }
    return readSizeAxis(list.items[0], "width", reading) to readSizeAxis(list.items[1], "height", reading)
}

private fun readSizeAxis(value: YamlValue, axis: String, reading: BlockReading): SizingPatch? {
    when (val raw = (value as? YamlScalar)?.value) {
        is Double -> return SizingPatch(mode = SizingMode.Fixed, value = raw)
        is String -> {
            ReaderEnums.sizingMode[raw]?.let { return SizingPatch(mode = it) }
            reading.warning("`size` $axis must be a number, `fill`, `hug` or `fixed`", value)
            return null
        }
        else -> {}
    }
    if (value is YamlMap) return readSizingAxis(value, axis, reading)
    reading.warning("`size` $axis must be a number, mode name or map", value)
    return null
}

/** `width: fill` shorthand or `{ type, value, min, max }` map. */
internal fun readSizingAxis(value: YamlValue, axis: String, reading: BlockReading): SizingPatch? {
    if (value is YamlScalar) {
        val mode = enumOf(value, axis, ReaderEnums.sizingMode, reading) ?: return null
        return SizingPatch(mode = mode)
    }
    val map = value as? YamlMap ?: run {
        reading.warning("`$axis` sizing must be a mode name or a map", value)
        return null
    }
    map.warnUnknownKeys(setOf("type", "value", "min", "max"), reading)
    return SizingPatch(
        mode = map.enum("type", ReaderEnums.sizingMode, reading),
        value = map.double("value", reading),
        min = map.double("min", reading),
        max = map.double("max", reading),
    )
}

// --- padding: logical + physical import compatibility ---

private class PaddingSides(
    val blockStart: Bindable<Double>?,
    val inlineEnd: Bindable<Double>?,
    val blockEnd: Bindable<Double>?,
    val inlineStart: Bindable<Double>?,
)

private fun readPadding(map: YamlMap, reading: BlockReading): PaddingSides {
    val value = map.value("padding") ?: return PaddingSides(null, null, null, null)
    if (value is YamlScalar || varRefOf(value) != null) {
        val all = bindableDouble(value, "padding", reading)
        return PaddingSides(all, all, all, all)
    }
    val sides = value as? YamlMap ?: run {
        reading.warning("`padding` must be a number or a per-side map", value)
        return PaddingSides(null, null, null, null)
    }
    sides.warnUnknownKeys(
        setOf(
            "top", "right", "bottom", "left",
            "inline", "block", "inlineStart", "inlineEnd", "blockStart", "blockEnd",
        ),
        reading,
    )
    val inline = sides.bindableDouble("inline", reading)
    val block = sides.bindableDouble("block", reading)
    return PaddingSides(
        blockStart = sides.bindableDouble("blockStart", reading)
            ?: sides.bindableDouble("top", reading) ?: block,
        inlineEnd = sides.physical("inlineEnd", "right", reading) ?: inline,
        blockEnd = sides.bindableDouble("blockEnd", reading)
            ?: sides.bindableDouble("bottom", reading) ?: block,
        inlineStart = sides.physical("inlineStart", "left", reading) ?: inline,
    )
}

/** Logical key wins; physical `left`/`right` is accepted with an import-compat hint. */
private fun YamlMap.physical(
    logicalKey: String,
    physicalKey: String,
    reading: BlockReading,
): Bindable<Double>? {
    bindableDouble(logicalKey, reading)?.let { return it }
    val physical = value(physicalKey) ?: return null
    reading.info(
        "Physical `$physicalKey` is normalized to logical `$logicalKey`",
        physical,
    )
    return bindableDouble(physical, physicalKey, reading)
}

private fun YamlMap.anchor(
    logicalKey: String,
    physicalKey: String,
    reading: BlockReading,
): Bindable<Double>? = physical(logicalKey, physicalKey, reading)

// --- gap ---

private class GapSpec(
    val main: DesignGap?,
    val row: Bindable<Double>?,
    val column: Bindable<Double>?,
)

private fun readGap(map: YamlMap, reading: BlockReading): GapSpec {
    val value = map.value("gap") ?: return GapSpec(null, null, null)
    if ((value as? YamlScalar)?.value == "auto") return GapSpec(DesignGap.Auto, null, null)
    if (value is YamlMap && (value.entries.containsKey("row") || value.entries.containsKey("column"))) {
        return GapSpec(
            main = null,
            row = value.bindableDouble("row", reading),
            column = value.bindableDouble("column", reading),
        )
    }
    val main = bindableDouble(value, "gap", reading) ?: return GapSpec(null, null, null)
    return GapSpec(DesignGap.Fixed(main), null, null)
}

// --- grid tracks ---

private class TrackAxis(
    val tracks: List<GridTrack>?,
    val gap: Bindable<Double>?,
    val implicitTrack: GridTrack?,
    val min: Bindable<Double>?,
)

private fun readTrackAxis(map: YamlMap, key: String, reading: BlockReading): TrackAxis? {
    val axis = map.mapValue(key, reading) ?: return null
    axis.warnUnknownKeys(setOf("count", "track", "tracks", "gap", "auto", "min"), reading)
    val count = axis.int("count", reading)
    val track = axis.value("track")?.let { readTrack(it, reading) }
    val trackList = axis.listValue("tracks", reading)?.items?.mapNotNull { readTrack(it, reading) }
    val auto = axis.boolean("auto", reading) ?: false
    val tracks = when {
        trackList != null -> trackList
        count != null && count > 0 -> List(count) { track ?: GridTrack.Flex(1.0.bindable()) }
        track != null && !auto -> listOf(track)
        else -> null
    }
    return TrackAxis(
        tracks = tracks,
        gap = axis.bindableDouble("gap", reading),
        implicitTrack = if (auto) track ?: GridTrack.Flex(1.0.bindable()) else null,
        min = axis.bindableDouble("min", reading),
    )
}

/**
 * `hug` -> Hug. A trailing `fr` marks a Flex track, but only when its body is self-delimiting so
 * the `fr` cannot be mistaken for the tail of a ref id: a literal weight (`1fr`), a `{{expr}}`
 * binding (`{{w}}fr`), or a **braced** token ref (`${weight}fr` / `${prop.w}fr`). A bare `$id` /
 * `$prop.x` ref is always a Fixed track even when its id ends in `fr` (`$railfr` = Fixed ref to
 * `railfr`) — braceless refs are never Flex. Everything else is a Fixed literal/ref. The emitter
 * ([io.aequicor.visualization.engine.frontend.cnl.CnlGrammar]'s `flexWord`) mirrors this surface.
 */
private fun readTrack(value: YamlValue, reading: BlockReading): GridTrack? {
    val raw = (value as? YamlScalar)?.value
    if (raw is String) {
        if (raw == "hug") return GridTrack.Hug
        flexTrackBody(raw)?.let { body -> bindableDoubleFromText(body)?.let { return GridTrack.Flex(it) } }
    }
    bindableDouble(value, "track", reading)?.let { return GridTrack.Fixed(it) }
    return null
}

/**
 * The weight body of a `…fr` Flex token, or null when [raw] is not a Flex token. A braced ref
 * `${id}fr` / `${prop.x}fr` unwraps to the bare `$id` / `$prop.x` ref; a `{{expr}}fr` binding keeps
 * its delimiters; a literal `1fr` yields `1`. A braceless `$id`fr / `$prop.x`fr is NOT a Flex token
 * (the `fr` belongs to the ref id) -> null, so the caller reads the whole scalar as a Fixed ref.
 */
private fun flexTrackBody(raw: String): String? {
    if (!raw.endsWith("fr")) return null
    val body = raw.removeSuffix("fr")
    return when {
        body.startsWith("\${") && body.endsWith("}") -> "$" + body.substring(2, body.length - 1)
        body.startsWith("{{") && body.endsWith("}}") -> body
        body.startsWith("$") -> null
        else -> body
    }
}

/** Parses a bare `fr`-body: literal, `$token`/`$prop` ref or `{{expr}}` binding. */
private fun bindableDoubleFromText(text: String): Bindable<Double>? {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("\$prop.") -> trimmed.removePrefix("\$prop.").takeIf { it.isNotEmpty() }?.let { Bindable.PropRef(it) }
        trimmed.startsWith("\$prop:") -> trimmed.removePrefix("\$prop:").takeIf { it.isNotEmpty() }?.let { Bindable.PropRef(it) }
        trimmed.startsWith("$") -> trimmed.drop(1).takeIf { it.isNotEmpty() }?.let { Bindable.VarRef(it) }
        else -> expressionBody(trimmed)?.let { Bindable.DataRef(DesignExpression(it)) }
            ?: trimmed.toDoubleOrNull()?.bindable()
    }
}

private fun readPlacement(map: YamlMap, reading: BlockReading): GridPlacement? {
    val placement = map.mapValue("placement", reading) ?: return null
    placement.warnUnknownKeys(setOf("column", "row", "columnSpan", "rowSpan"), reading)
    return GridPlacement(
        column = placement.int("column", reading) ?: 0,
        row = placement.int("row", reading) ?: 0,
        columnSpan = placement.int("columnSpan", reading) ?: 1,
        rowSpan = placement.int("rowSpan", reading) ?: 1,
    )
}

// --- guides + layout grid overlays ---

private val guideOrientations = mapOf(
    "horizontal" to GuideOrientation.Horizontal,
    "vertical" to GuideOrientation.Vertical,
)

private fun readGuides(map: YamlMap, reading: BlockReading): List<GuideLine>? {
    val list = map.listValue("guides", reading) ?: return null
    return list.items.mapNotNull { item ->
        val guide = item as? YamlMap ?: run {
            reading.warning("`guides` items must be maps", item)
            return@mapNotNull null
        }
        val orientation = guide.enum("orientation", guideOrientations, reading)
        val position = guide.double("position", reading)
        if (orientation == null || position == null) null else GuideLine(orientation, position)
    }
}

private fun readGrids(map: YamlMap, reading: BlockReading): List<LayoutGridDefinition>? {
    val list = map.listValue("grids", reading) ?: return null
    return list.items.mapNotNull { item ->
        val grid = item as? YamlMap ?: run {
            reading.warning("`grids` items must be maps", item)
            return@mapNotNull null
        }
        val type = grid.enum("type", ReaderEnums.gridType, reading) ?: return@mapNotNull null
        LayoutGridDefinition(
            type = type,
            count = grid.bindableInt("count", reading),
            size = grid.bindableDouble("size", reading),
            gutter = grid.bindableDouble("gutter", reading),
            margin = grid.bindableDouble("margin", reading),
            alignment = grid.enum("alignment", ReaderEnums.gridAlignment, reading)
                ?: LayoutGridAlignment.Stretch,
            color = grid.bindableColor("color", reading)
                ?.let { (it as? Bindable.Value)?.value },
            visible = grid.boolean("visible", reading) ?: true,
        )
    }
}
