package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.yaml.YamlList
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.bindable

private val knownKeys = setOf(
    "opacity", "blendMode", "radius", "cornerSmoothing", "fill", "fills", "stroke", "strokes",
    "effects", "fillStyle", "strokeStyle", "textStyle", "effectStyle", "gridStyle",
)

/** `style:` block — opacity/blend, radius, fills, strokes, effects, shared style refs. */
internal fun readStyleBlock(value: YamlValue, reading: BlockReading): StylePatch? {
    val map = value as? YamlMap ?: run {
        reading.error("`style` must be a map", value)
        return null
    }
    map.warnUnknownKeys(knownKeys, reading)
    val effects = readEffects(map, reading)
    return StylePatch(
        opacity = map.bindableDouble("opacity", reading),
        blendMode = map.string("blendMode", reading),
        radius = readRadius(map, reading),
        fills = readFills(map, reading),
        strokes = readStrokes(map, reading),
        effects = effects?.effects,
        fillStyle = map.string("fillStyle", reading),
        strokeStyle = map.string("strokeStyle", reading),
        textStyle = map.string("textStyle", reading),
        effectStyle = map.string("effectStyle", reading) ?: effects?.styleRef,
        gridStyle = map.string("gridStyle", reading),
    )
}

// --- corner radius ---

private fun readRadius(map: YamlMap, reading: BlockReading): DesignCornerRadius? {
    val smoothing = map.double("cornerSmoothing", reading)
    val value = map.value("radius")
    val radius = when {
        value == null -> null
        value is YamlScalar || varRefOf(value) != null ->
            bindableDouble(value, "radius", reading)?.let { DesignCornerRadius.all(it) }
        value is YamlMap -> {
            value.warnUnknownKeys(
                setOf("topLeft", "topRight", "bottomRight", "bottomLeft"),
                reading,
            )
            DesignCornerRadius(
                topLeft = value.bindableDouble("topLeft", reading) ?: 0.0.bindable(),
                topRight = value.bindableDouble("topRight", reading) ?: 0.0.bindable(),
                bottomRight = value.bindableDouble("bottomRight", reading) ?: 0.0.bindable(),
                bottomLeft = value.bindableDouble("bottomLeft", reading) ?: 0.0.bindable(),
            )
        }
        else -> {
            reading.warning("`radius` must be a number, token ref or per-corner map", value)
            null
        }
    }
    if (radius == null && smoothing == null) return null
    return (radius ?: DesignCornerRadius()).copy(smoothing = smoothing ?: 0.0)
}

// --- paints (fills and stroke paints) ---

/** `fills:` list plus the singular `fill:` shorthand (scalar color, `$token`, or map). */
private fun readFills(map: YamlMap, reading: BlockReading): List<DesignPaint>? {
    val list = readPaints(map, "fills", reading)
    val single = map.value("fill")?.let { readPaint(it, reading) }
    return when {
        single != null && list != null -> listOf(single) + list
        single != null -> listOf(single)
        else -> list
    }
}

internal fun readPaints(map: YamlMap, key: String, reading: BlockReading): List<DesignPaint>? {
    val list = map.listValue(key, reading) ?: return null
    return list.items.mapNotNull { item -> readPaint(item, reading) }
}

private fun readPaint(item: YamlValue, reading: BlockReading): DesignPaint? {
    // `- variable: color.surface` / `- token: color.x` / bare hex scalar -> solid paint.
    varRefOf(item)?.let { ref ->
        val map = item as? YamlMap
        if (map == null || map.entries.keys.all { it in setOf("variable", "token", "opacity", "blendMode") }) {
            return DesignPaint.Solid(
                color = Bindable.VarRef(ref),
                opacity = map?.bindableDouble("opacity", reading) ?: 1.0.bindable(),
                blendMode = map?.string("blendMode", reading) ?: "normal",
            )
        }
    }
    if (item is YamlScalar) {
        val color = bindableColor(item, "fills", reading) ?: return null
        return DesignPaint.Solid(color)
    }
    val map = item as? YamlMap ?: run {
        reading.warning("Paint entries must be maps or color scalars", item)
        return null
    }
    val opacity = map.bindableDouble("opacity", reading) ?: 1.0.bindable()
    val blendMode = map.string("blendMode", reading) ?: "normal"
    val visible = map.bindableBoolean("visible", reading) ?: true.bindable()
    return when (val type = map.string("type", reading) ?: "solid") {
        "solid" -> {
            val color = paintColor(map, reading) ?: return null
            DesignPaint.Solid(color, visible, opacity, blendMode)
        }
        "image" -> DesignPaint.Image(
            assetId = map.string("asset", reading) ?: map.string("assetId", reading).orEmpty(),
            scaleMode = map.enum("fillMode", ReaderEnums.fillMode, reading)
                ?: io.aequicor.visualization.engine.ir.model.ImageScaleMode.Fill,
            focalPoint = readFocalPoint(map.value("focalPoint"), reading),
            replaceable = map.boolean("replaceable", reading) ?: false,
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
        )
        "video" -> DesignPaint.Video(
            assetId = map.string("asset", reading) ?: map.string("assetId", reading).orEmpty(),
            scaleMode = map.enum("fillMode", ReaderEnums.fillMode, reading)
                ?: io.aequicor.visualization.engine.ir.model.ImageScaleMode.Fill,
            focalPoint = readFocalPoint(map.value("focalPoint"), reading),
            posterAssetId = map.string("poster", reading).orEmpty(),
            autoplay = map.boolean("autoplay", reading) ?: false,
            loop = map.boolean("loop", reading) ?: false,
            muted = map.boolean("muted", reading) ?: true,
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
        )
        else -> {
            val gradient = ReaderEnums.gradientKind[type]
            if (gradient == null) {
                reading.warning("Unknown fill type \"$type\"", map)
                DesignPaint.Unknown(type, visible, opacity, blendMode)
            } else {
                DesignPaint.Gradient(
                    gradientType = gradient,
                    from = readPoint(map.value("from"), reading) ?: DesignPoint(0.0, 0.0),
                    to = readPoint(map.value("to"), reading) ?: DesignPoint(0.0, 1.0),
                    stops = readStops(map, reading),
                    visible = visible,
                    opacity = opacity,
                    blendMode = blendMode,
                )
            }
        }
    }
}

/** `color: #hex` | `token: ref` | `variable: ref` on a paint or stop map. */
private fun paintColor(map: YamlMap, reading: BlockReading): Bindable<DesignColor>? {
    map.value("color")?.let { return bindableColor(it, "color", reading) }
    varRefOf(map)?.let { return Bindable.VarRef(it) }
    reading.warning("Paint needs `color`, `token` or `variable`", map)
    return null
}

private fun readStops(map: YamlMap, reading: BlockReading): List<GradientStop> {
    val list = map.listValue("stops", reading) ?: return emptyList()
    return list.items.mapNotNull { item ->
        val stop = item as? YamlMap ?: run {
            reading.warning("`stops` items must be maps", item)
            return@mapNotNull null
        }
        val position = stop.double("position", reading) ?: return@mapNotNull null
        val color = paintColor(stop, reading) ?: return@mapNotNull null
        GradientStop(position, color)
    }
}

internal fun readPoint(value: YamlValue?, reading: BlockReading): DesignPoint? {
    val map = value as? YamlMap ?: return null
    return DesignPoint(
        x = map.double("x", reading) ?: 0.0,
        y = map.double("y", reading) ?: 0.0,
    )
}

internal fun readFocalPoint(value: YamlValue?, reading: BlockReading): DesignPoint? {
    if (value == null) return null
    if ((value as? YamlScalar)?.value == "center") return DesignPoint(0.5, 0.5)
    val map = value as? YamlMap ?: return null
    return DesignPoint(
        x = map.bindableDouble("x", reading) ?: 0.0.bindable(),
        y = map.bindableDouble("y", reading) ?: 0.0.bindable(),
    )
}

// --- strokes ---

private fun readStrokes(map: YamlMap, reading: BlockReading): DesignStrokes? {
    // `strokes:` list plus the singular `stroke:` shorthand (scalar color or map).
    val items: List<YamlValue> = buildList {
        map.value("stroke")?.let { add(it) }
        map.listValue("strokes", reading)?.let { addAll(it.items) }
    }
    if (items.isEmpty()) return null
    var weight: Bindable<Double>? = null
    var align: StrokeAlign? = null
    var dash: List<Double>? = null
    var cap: String? = null
    var join: String? = null
    var perSide: DesignInsets? = null
    // A stroke item is a paint (any fill-style paint: solid/gradient/image/video with
    // opacity/blend/visible) plus, on any item, the shared stroke attributes hoisted here.
    val paints = items.mapNotNull { item ->
        (item as? YamlMap)?.let { m ->
            m.bindableDouble("weight", reading)?.let { weight = it }
            m.enum("position", ReaderEnums.strokeAlign, reading)?.let { align = it }
            (m.value("dash") as? YamlList)?.let { d ->
                dash = d.items.mapNotNull { (it as? YamlScalar)?.value as? Double }
            }
            (m.value("weightPerSide") as? YamlMap)?.let { p ->
                perSide = DesignInsets(
                    top = p.bindableDouble("top", reading) ?: 0.0.bindable(),
                    right = p.bindableDouble("right", reading) ?: 0.0.bindable(),
                    bottom = p.bindableDouble("bottom", reading) ?: 0.0.bindable(),
                    left = p.bindableDouble("left", reading) ?: 0.0.bindable(),
                )
            }
            m.string("caps", reading)?.let { cap = it }
            m.string("joins", reading)?.let { join = it }
        }
        readPaint(item, reading)
    }
    return DesignStrokes(
        paints = paints,
        weight = weight ?: 1.0.bindable(),
        align = align ?: StrokeAlign.Inside,
        dashPattern = dash ?: emptyList(),
        cap = cap ?: "butt",
        join = join ?: "miter",
        weightPerSide = perSide,
    )
}

// --- effects ---

private class EffectsResult(
    val effects: List<DesignEffect>,
    val styleRef: String?,
)

private fun readEffects(map: YamlMap, reading: BlockReading): EffectsResult? {
    val list = map.listValue("effects", reading) ?: return null
    var styleRef: String? = null
    val effects = list.items.mapNotNull { item ->
        val effect = item as? YamlMap ?: run {
            reading.warning("`effects` items must be maps", item)
            return@mapNotNull null
        }
        // `- style: shadow.card` entry contributes an effect style ref.
        if (effect.entries.keys == setOf("style")) {
            styleRef = effect.string("style", reading)
            return@mapNotNull null
        }
        readEffect(effect, reading)
    }
    return EffectsResult(effects, styleRef)
}

private fun readEffect(effect: YamlMap, reading: BlockReading): DesignEffect? {
    val visible = effect.bindableBoolean("visible", reading) ?: true.bindable()
    return when (val type = effect.string("type", reading)) {
        "dropShadow" -> DesignEffect.DropShadow(
            color = effectColor(effect, reading) ?: return null,
            offset = DesignPoint(
                x = effect.bindableDouble("x", reading) ?: 0.0.bindable(),
                y = effect.bindableDouble("y", reading) ?: 0.0.bindable(),
            ),
            blur = effect.bindableDouble("blur", reading) ?: 0.0.bindable(),
            spread = effect.bindableDouble("spread", reading) ?: 0.0.bindable(),
            visible = visible,
        )
        "innerShadow" -> DesignEffect.InnerShadow(
            color = effectColor(effect, reading) ?: return null,
            offset = DesignPoint(
                x = effect.bindableDouble("x", reading) ?: 0.0.bindable(),
                y = effect.bindableDouble("y", reading) ?: 0.0.bindable(),
            ),
            blur = effect.bindableDouble("blur", reading) ?: 0.0.bindable(),
            spread = effect.bindableDouble("spread", reading) ?: 0.0.bindable(),
            visible = visible,
        )
        "layerBlur" -> DesignEffect.LayerBlur(
            radius = effect.bindableDouble("blur", reading) ?: effect.bindableDouble("radius", reading) ?: 0.0.bindable(),
            visible = visible,
        )
        "backgroundBlur" -> DesignEffect.BackgroundBlur(
            radius = effect.bindableDouble("blur", reading) ?: effect.bindableDouble("radius", reading) ?: 0.0.bindable(),
            visible = visible,
        )
        null -> {
            reading.warning("Effect entry needs a `type`", effect)
            null
        }
        else -> {
            reading.warning("Unknown effect type \"$type\"", effect)
            DesignEffect.Unknown(type, visible)
        }
    }
}

/**
 * Effect color from `color`/`token`/`variable` plus optional `opacity`. Opacity is
 * baked into a literal color's alpha; a token color cannot carry it in the IR.
 */
private fun effectColor(effect: YamlMap, reading: BlockReading): Bindable<DesignColor>? {
    val color = effect.value("color")?.let { bindableColor(it, "color", reading) }
        ?: varRefOf(effect)?.let { Bindable.VarRef(it) }
        ?: run {
            reading.warning("Effect needs `color`, `token` or `variable`", effect)
            return null
        }
    val opacity = effect.double("opacity", reading) ?: return color
    return when (color) {
        is Bindable.Value -> {
            val argb = color.value.argb
            val alpha = ((argb shr 24 and 0xFF) * opacity).toLong().coerceIn(0, 255)
            DesignColor((alpha shl 24) or (argb and 0xFFFFFF)).bindable()
        }
        else -> {
            reading.info("Effect `opacity` is ignored for token/variable colors", effect)
            color
        }
    }
}
