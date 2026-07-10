package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutGridType
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.UnitValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Shared styles, paints (incl. video), effects, corner radii, layout grids, guides. */

internal fun DesignDocumentReader.readStyle(element: JsonElement, pointer: String): DesignStyle? {
    val obj = element as? JsonObject ?: return null
    return when (val type = obj.stringOrDefault("type")) {
        "text" -> DesignStyle.Text(
            (obj["value"] as? JsonObject)?.let { readTextStyle(it, "$pointer/value") } ?: DesignTextStyle(),
        )
        "paint" -> DesignStyle.Paint(
            obj["value"].asArray("$pointer/value").mapIndexedNotNull { index, paint ->
                readPaint(paint, "$pointer/value/$index")
            },
        )
        "effect" -> DesignStyle.Effect(
            obj["value"].asArray("$pointer/value").mapIndexedNotNull { index, effect ->
                readEffect(effect, "$pointer/value/$index")
            },
        )
        "grid" -> DesignStyle.Grid(readLayoutGrids(obj["value"], "$pointer/value"))
        else -> {
            warn(pointer, "Unknown style type '$type' ignored")
            null
        }
    }
}

internal fun DesignDocumentReader.readTextStyle(obj: JsonObject, pointer: String): DesignTextStyle =
    DesignTextStyle(
        fontFamily = (obj["fontFamily"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        fontWeight = obj["fontWeight"]?.let { readBindableDouble(it, 400.0) },
        italic = (obj["italic"] as? JsonPrimitive)?.booleanOrNull,
        fontSize = obj["fontSize"]?.let { readBindableDouble(it, 14.0) },
        lineHeight = readUnitValue(obj["lineHeight"], "$pointer/lineHeight"),
        letterSpacing = readUnitValue(obj["letterSpacing"], "$pointer/letterSpacing"),
        paragraphSpacing = (obj["paragraphSpacing"] as? JsonPrimitive)?.doubleOrNull,
        paragraphIndent = (obj["paragraphIndent"] as? JsonPrimitive)?.doubleOrNull,
        textAlignHorizontal = obj["textAlignHorizontal"]?.let {
            readEnum(
                it, "$pointer/textAlignHorizontal", TextAlignHorizontal.Left,
                mapOf(
                    "left" to TextAlignHorizontal.Left,
                    "center" to TextAlignHorizontal.Center,
                    "right" to TextAlignHorizontal.Right,
                    "justified" to TextAlignHorizontal.Justified,
                ),
            )
        },
        textAlignVertical = obj["textAlignVertical"]?.let {
            readEnum(
                it, "$pointer/textAlignVertical", TextAlignVertical.Top,
                mapOf(
                    "top" to TextAlignVertical.Top,
                    "center" to TextAlignVertical.Center,
                    "bottom" to TextAlignVertical.Bottom,
                ),
            )
        },
        textCase = obj["textCase"]?.let {
            readEnum(
                it, "$pointer/textCase", TextCase.None,
                mapOf(
                    "none" to TextCase.None,
                    "upper" to TextCase.Upper,
                    "lower" to TextCase.Lower,
                    "title" to TextCase.Title,
                    "smallCaps" to TextCase.SmallCaps,
                    "smallCapsForced" to TextCase.SmallCapsForced,
                ),
            )
        },
        textDecoration = obj["textDecoration"]?.let {
            readEnum(
                it, "$pointer/textDecoration", TextDecorationKind.None,
                mapOf(
                    "none" to TextDecorationKind.None,
                    "underline" to TextDecorationKind.Underline,
                    "strikethrough" to TextDecorationKind.Strikethrough,
                ),
            )
        },
        decorationStyle = obj["decorationStyle"]?.let {
            readEnum(
                it, "$pointer/decorationStyle", TextDecorationStyle.Solid,
                mapOf(
                    "solid" to TextDecorationStyle.Solid,
                    "dashed" to TextDecorationStyle.Dashed,
                    "dotted" to TextDecorationStyle.Dotted,
                    "wavy" to TextDecorationStyle.Wavy,
                ),
            )
        },
        decorationColor = (obj["decorationColor"] as? JsonPrimitive)?.takeIf { it.isString }?.let { primitive ->
            DesignColor.fromHex(primitive.content)
                ?: run {
                    warn("$pointer/decorationColor", "Invalid color value")
                    null
                }
        },
        decorationThickness = readUnitValue(obj["decorationThickness"], "$pointer/decorationThickness"),
        decorationSkipInk = (obj["decorationSkipInk"] as? JsonPrimitive)?.booleanOrNull,
        textPosition = obj["textPosition"]?.let {
            readEnum(
                it, "$pointer/textPosition", TextScriptPosition.None,
                mapOf(
                    "none" to TextScriptPosition.None,
                    "superscript" to TextScriptPosition.Superscript,
                    "subscript" to TextScriptPosition.Subscript,
                ),
            )
        },
        leadingTrim = obj["leadingTrim"]?.let {
            readEnum(
                it, "$pointer/leadingTrim", LeadingTrim.None,
                mapOf(
                    "none" to LeadingTrim.None,
                    "capHeight" to LeadingTrim.CapHeight,
                ),
            )
        },
        hangingPunctuation = (obj["hangingPunctuation"] as? JsonPrimitive)?.booleanOrNull,
        hangingList = (obj["hangingList"] as? JsonPrimitive)?.booleanOrNull,
        fontFeatures = obj["fontFeatures"].asObject().mapValues { (_, enabled) ->
            (enabled as? JsonPrimitive)?.booleanOrNull ?: false
        },
        variableAxes = obj["variableAxes"].asObject().mapNotNull { (axis, value) ->
            (value as? JsonPrimitive)?.doubleOrNull?.let { axis to it }
        }.toMap(),
    )

private fun DesignDocumentReader.readUnitValue(element: JsonElement?, pointer: String): UnitValue? {
    val obj = element as? JsonObject ?: return null
    val unit = readEnum(
        obj["unit"], "$pointer/unit", DesignUnit.Px,
        mapOf("px" to DesignUnit.Px, "percent" to DesignUnit.Percent),
    )
    return UnitValue(unit, obj.doubleOrDefault("value", 0.0))
}

internal fun DesignDocumentReader.readPaint(element: JsonElement, pointer: String): DesignPaint? {
    val obj = element as? JsonObject ?: return null
    val visible = readBindableBoolean(obj["visible"], true)
    val opacity = readBindableDouble(obj["opacity"], 1.0)
    val blendMode = obj.stringOrDefault("blendMode", "normal")
    return when (val type = obj.stringOrDefault("type", "solid")) {
        "solid" -> DesignPaint.Solid(
            color = readBindableColor(obj["color"], "$pointer/color"),
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
        )
        "gradientLinear", "gradientRadial", "gradientAngular", "gradientDiamond" -> DesignPaint.Gradient(
            gradientType = when (type) {
                "gradientRadial" -> GradientKind.Radial
                "gradientAngular" -> GradientKind.Angular
                "gradientDiamond" -> GradientKind.Diamond
                else -> GradientKind.Linear
            },
            from = (obj["from"] as? JsonObject)?.let { readPoint(it, "$pointer/from") } ?: DesignPoint(0.0, 0.0),
            to = (obj["to"] as? JsonObject)?.let { readPoint(it, "$pointer/to") } ?: DesignPoint(0.0, 1.0),
            stops = obj["stops"].asArray("$pointer/stops").mapIndexed { index, stop ->
                val stopObj = stop.asObject()
                GradientStop(
                    position = stopObj.plainDouble("position", "$pointer/stops/$index", 0.0),
                    color = readBindableColor(stopObj["color"], "$pointer/stops/$index/color"),
                )
            },
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
        )
        "image" -> DesignPaint.Image(
            assetId = obj.stringOrDefault("assetId"),
            scaleMode = readImageScaleMode(obj["scaleMode"], "$pointer/scaleMode"),
            focalPoint = (obj["focalPoint"] as? JsonObject)?.let { readPoint(it, "$pointer/focalPoint") },
            replaceable = obj.booleanOrDefault("replaceable", false),
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
        )
        "video" -> DesignPaint.Video(
            assetId = obj.stringOrDefault("assetId"),
            scaleMode = readImageScaleMode(obj["scaleMode"], "$pointer/scaleMode"),
            focalPoint = (obj["focalPoint"] as? JsonObject)?.let { readPoint(it, "$pointer/focalPoint") },
            posterAssetId = obj.stringOrDefault("posterAssetId"),
            autoplay = obj.booleanOrDefault("autoplay", false),
            loop = obj.booleanOrDefault("loop", false),
            muted = obj.booleanOrDefault("muted", true),
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
        )
        else -> {
            warn(pointer, "Unknown paint type '$type' rendered as fallback")
            DesignPaint.Unknown(type, visible, opacity, blendMode)
        }
    }
}

internal fun DesignDocumentReader.readImageScaleMode(element: JsonElement?, pointer: String): ImageScaleMode =
    readEnum(
        element, pointer, ImageScaleMode.Fill,
        mapOf(
            "fill" to ImageScaleMode.Fill,
            "fit" to ImageScaleMode.Fit,
            "crop" to ImageScaleMode.Crop,
            "tile" to ImageScaleMode.Tile,
            "stretch" to ImageScaleMode.Stretch,
        ),
    )

internal fun DesignDocumentReader.readStrokes(obj: JsonObject, pointer: String): DesignStrokes =
    DesignStrokes(
        paints = obj["paints"].asArray("$pointer/paints").mapIndexedNotNull { index, paint ->
            readPaint(paint, "$pointer/paints/$index")
        },
        weight = readBindableDouble(obj["weight"], 1.0),
        weightPerSide = (obj["weightPerSide"] as? JsonObject)?.let { readInsets(it) },
        align = readEnum(
            obj["align"], "$pointer/align", StrokeAlign.Inside,
            mapOf(
                "inside" to StrokeAlign.Inside,
                "center" to StrokeAlign.Center,
                "outside" to StrokeAlign.Outside,
            ),
        ),
        dashPattern = obj["dashPattern"].asArray("$pointer/dashPattern").mapNotNull {
            (it as? JsonPrimitive)?.doubleOrNull
        },
        cap = obj.stringOrDefault("cap", "butt"),
        join = obj.stringOrDefault("join", "miter"),
    )

internal fun DesignDocumentReader.readEffect(element: JsonElement, pointer: String): DesignEffect? {
    val obj = element as? JsonObject ?: return null
    val visible = readBindableBoolean(obj["visible"], true)
    return when (val type = obj.stringOrDefault("type")) {
        "dropShadow" -> DesignEffect.DropShadow(
            color = readBindableColor(obj["color"], "$pointer/color"),
            offset = (obj["offset"] as? JsonObject)?.let { readPoint(it, "$pointer/offset") } ?: DesignPoint(),
            blur = obj.plainDouble("blur", pointer, 0.0),
            spread = obj.plainDouble("spread", pointer, 0.0),
            visible = visible,
        )
        "innerShadow" -> DesignEffect.InnerShadow(
            color = readBindableColor(obj["color"], "$pointer/color"),
            offset = (obj["offset"] as? JsonObject)?.let { readPoint(it, "$pointer/offset") } ?: DesignPoint(),
            blur = obj.plainDouble("blur", pointer, 0.0),
            spread = obj.plainDouble("spread", pointer, 0.0),
            visible = visible,
        )
        "layerBlur" -> DesignEffect.LayerBlur(obj.plainDouble("radius", pointer, 0.0), visible)
        "backgroundBlur" -> DesignEffect.BackgroundBlur(obj.plainDouble("radius", pointer, 0.0), visible)
        else -> {
            warn(pointer, "Unknown effect type '$type' ignored")
            DesignEffect.Unknown(type, visible)
        }
    }
}

internal fun DesignDocumentReader.readCornerRadius(element: JsonElement?, smoothing: Double): DesignCornerRadius? =
    when (element) {
        null -> null
        is JsonPrimitive -> DesignCornerRadius.all(readBindableDouble(element, 0.0)).copy(smoothing = smoothing)
        is JsonObject -> if (element.isBindingRef()) {
            DesignCornerRadius.all(readBindableDouble(element, 0.0)).copy(smoothing = smoothing)
        } else {
            DesignCornerRadius(
                topLeft = readBindableDouble(element["topLeft"], 0.0),
                topRight = readBindableDouble(element["topRight"], 0.0),
                bottomRight = readBindableDouble(element["bottomRight"], 0.0),
                bottomLeft = readBindableDouble(element["bottomLeft"], 0.0),
                smoothing = smoothing,
            )
        }
        else -> null
    }

internal fun DesignDocumentReader.readLayoutGrids(
    element: JsonElement?,
    pointer: String,
): List<LayoutGridDefinition> =
    element.asArray(pointer).mapIndexedNotNull { index, grid ->
        val obj = grid as? JsonObject ?: return@mapIndexedNotNull null
        LayoutGridDefinition(
            type = readEnum(
                obj["type"], "$pointer/$index/type", LayoutGridType.Columns,
                mapOf(
                    "columns" to LayoutGridType.Columns,
                    "rows" to LayoutGridType.Rows,
                    "grid" to LayoutGridType.Grid,
                ),
            ),
            count = (obj["count"] as? JsonPrimitive)?.intOrNull,
            size = (obj["size"] as? JsonPrimitive)?.doubleOrNull,
            gutter = obj.doubleOrDefault("gutter", 0.0),
            margin = obj.doubleOrDefault("margin", 0.0),
            alignment = readEnum(
                obj["alignment"], "$pointer/$index/alignment", LayoutGridAlignment.Stretch,
                mapOf(
                    "stretch" to LayoutGridAlignment.Stretch,
                    "start" to LayoutGridAlignment.Start,
                    "center" to LayoutGridAlignment.Center,
                    "end" to LayoutGridAlignment.End,
                ),
            ),
            color = (obj["color"] as? JsonPrimitive)?.takeIf { it.isString }
                ?.let { DesignColor.fromHex(it.content) },
            visible = obj.booleanOrDefault("visible", true),
        )
    }

internal fun DesignDocumentReader.readGuides(element: JsonElement?, pointer: String): List<GuideLine> =
    element.asArray(pointer).mapIndexedNotNull { index, guide ->
        val obj = guide as? JsonObject ?: return@mapIndexedNotNull null
        GuideLine(
            orientation = readEnum(
                obj["orientation"], "$pointer/$index/orientation", GuideOrientation.Horizontal,
                mapOf(
                    "horizontal" to GuideOrientation.Horizontal,
                    "vertical" to GuideOrientation.Vertical,
                ),
            ),
            position = obj.doubleOrDefault("position", 0.0),
        )
    }
