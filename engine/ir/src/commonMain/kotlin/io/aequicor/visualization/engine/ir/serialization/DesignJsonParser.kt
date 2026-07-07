package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignAsset
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignComponentSet
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignLayoutChild
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.DesignVariables
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.VectorPath
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Parses a Figma-like JSON design document into the typed IR.
 *
 * Forward compatibility contract: unknown fields are ignored, unknown enum values fall
 * back to a default and emit a warning diagnostic; only malformed JSON or a non-object
 * root fails the parse.
 */
fun parseDesignDocument(source: String): DesignParseResult {
    val json = Json { ignoreUnknownKeys = true }
    val root = try {
        json.parseToJsonElement(source)
    } catch (failure: Exception) {
        return DesignParseResult.Failure(
            listOf(
                DesignDiagnostic(
                    DesignSeverity.Error,
                    "Malformed JSON: ${failure.message.orEmpty().take(200)}",
                ),
            ),
        )
    }
    val rootObject = root as? JsonObject
        ?: return DesignParseResult.Failure(
            listOf(DesignDiagnostic(DesignSeverity.Error, "Document root must be a JSON object")),
        )
    val parser = DesignDocumentReader()
    val document = parser.readDocument(rootObject)
    return DesignParseResult.Success(document, parser.diagnostics)
}

private class DesignDocumentReader {
    val diagnostics = mutableListOf<DesignDiagnostic>()

    fun readDocument(obj: JsonObject): DesignDocument =
        DesignDocument(
            schemaVersion = obj.stringOrDefault("schemaVersion", "1.0.0"),
            id = obj.stringOrDefault("id"),
            name = obj.stringOrDefault("name"),
            pages = obj["pages"].asArray("/pages").mapIndexed { index, page ->
                readPage(page, "/pages/$index")
            },
            components = obj["components"].asObject().mapNotNull { (id, value) ->
                readComponent(value, "/components/$id")?.let { id to it }
            }.toMap(),
            componentSets = obj["componentSets"].asObject().mapValues { (id, value) ->
                readComponentSet(value, "/componentSets/$id")
            },
            styles = obj["styles"].asObject().mapNotNull { (id, value) ->
                readStyle(value, "/styles/$id")?.let { id to it }
            }.toMap(),
            variables = readVariables(obj["variables"], "/variables"),
            assets = obj["assets"].asObject().mapValues { (id, value) ->
                readAsset(value)
            },
        )

    private fun readPage(element: JsonElement, pointer: String): DesignPage {
        val obj = element.asObject()
        return DesignPage(
            id = obj.stringOrDefault("id"),
            name = obj.stringOrDefault("name"),
            children = readChildren(obj, pointer),
        )
    }

    fun readNode(element: JsonElement, pointer: String): DesignNode? {
        val obj = element as? JsonObject ?: run {
            error(pointer, "Node must be an object")
            return null
        }
        val type = obj.stringOrDefault("type", "frame")
        val kind = readKind(type, obj, pointer)
        val layoutObj = obj["layout"] as? JsonObject
        return DesignNode(
            id = obj.stringOrDefault("id"),
            type = type,
            kind = kind,
            name = obj.stringOrDefault("name"),
            visible = readBindableBoolean(obj["visible"], true),
            locked = obj.booleanOrDefault("locked", false),
            opacity = readBindableDouble(obj["opacity"], 1.0),
            blendMode = obj.stringOrDefault("blendMode", "normal"),
            rotation = obj.plainDouble("rotation", pointer, 0.0),
            isMask = obj.booleanOrDefault("isMask", false),
            position = (obj["position"] as? JsonObject)?.let { readPoint(it, "$pointer/position") },
            constraints = readConstraints(obj["constraints"], "$pointer/constraints"),
            size = readSize(obj["size"], "$pointer/size"),
            sizing = readSizing(obj["sizing"], "$pointer/sizing"),
            minSize = (obj["minSize"] as? JsonObject)?.let { readSize(it, "$pointer/minSize") },
            maxSize = (obj["maxSize"] as? JsonObject)?.let { readSize(it, "$pointer/maxSize") },
            layout = readAutoLayout(layoutObj, "$pointer/layout"),
            layoutChild = readLayoutChild(obj["layoutChild"], "$pointer/layoutChild"),
            gridPlacement = (obj["gridPlacement"] as? JsonObject)?.let { readGridPlacement(it) },
            fills = (obj["fills"] as? JsonArray)?.mapIndexedNotNull { index, paint ->
                readPaint(paint, "$pointer/fills/$index")
            },
            strokes = (obj["strokes"] as? JsonObject)?.let { readStrokes(it, "$pointer/strokes") },
            effects = obj["effects"].asArray("$pointer/effects").mapIndexedNotNull { index, effect ->
                readEffect(effect, "$pointer/effects/$index")
            },
            cornerRadius = readCornerRadius(obj["cornerRadius"], obj.doubleOrDefault("cornerSmoothing", 0.0)),
            fillStyleId = obj.stringOrDefault("fillStyleId"),
            strokeStyleId = obj.stringOrDefault("strokeStyleId"),
            effectStyleId = obj.stringOrDefault("effectStyleId"),
            variableModes = obj["variableModes"].asObject().mapValues { (_, mode) ->
                mode.asStringOrEmpty()
            },
            scroll = readScroll(obj["scroll"], "$pointer/scroll"),
            children = readChildren(obj, pointer),
        )
    }

    private fun readChildren(obj: Map<String, JsonElement>, pointer: String): List<DesignNode> =
        obj["children"].asArray("$pointer/children").mapIndexedNotNull { index, child ->
            readNode(child, "$pointer/children/$index")
        }

    private fun readKind(type: String, obj: JsonObject, pointer: String): DesignNodeKind =
        when (type) {
            "frame", "group", "section", "component" -> DesignNodeKind.Frame
            "text" -> DesignNodeKind.Text(
                characters = readBindableString(obj["characters"], ""),
                textStyleId = obj.stringOrDefault("textStyleId"),
                textStyle = (obj["textStyle"] as? JsonObject)?.let { readTextStyle(it, "$pointer/textStyle") },
                autoResize = readEnum(
                    obj["autoResize"], "$pointer/autoResize", TextAutoResize.None,
                    mapOf(
                        "none" to TextAutoResize.None,
                        "height" to TextAutoResize.Height,
                        "widthAndHeight" to TextAutoResize.WidthAndHeight,
                    ),
                ),
                truncate = (obj["truncate"] as? JsonObject)?.let { truncate ->
                    TextTruncate(
                        maxLines = truncate.intOrDefault("maxLines", 1),
                        ellipsis = truncate.booleanOrDefault("ellipsis", true),
                    )
                },
                styleRanges = obj["styleRanges"].asArray("$pointer/styleRanges").mapIndexed { index, range ->
                    readStyleRange(range, "$pointer/styleRanges/$index")
                },
                links = obj["links"].asArray("$pointer/links").map { link ->
                    val linkObj = link.asObject()
                    TextLink(
                        start = linkObj.intOrDefault("start", 0),
                        end = linkObj.intOrDefault("end", 0),
                        url = linkObj.stringOrDefault("url"),
                    )
                },
            )
            "rectangle" -> shapeKind(ShapeType.Rectangle, obj, pointer)
            "ellipse" -> shapeKind(ShapeType.Ellipse, obj, pointer)
            "polygon" -> shapeKind(ShapeType.Polygon, obj, pointer)
            "star" -> shapeKind(ShapeType.Star, obj, pointer)
            "line" -> shapeKind(ShapeType.Line, obj, pointer)
            "vector" -> shapeKind(ShapeType.Vector, obj, pointer)
            "instance" -> DesignNodeKind.Instance(
                componentId = readBindableString(obj["componentId"], ""),
                variant = obj["variant"].asObject().mapValues { (_, value) -> value.asStringOrEmpty() },
                props = obj["props"].asObject().mapNotNull { (name, value) ->
                    readPropValue(value)?.let { name to it }
                }.toMap(),
                overrides = obj["overrides"].asArray("$pointer/overrides").mapIndexedNotNull { index, override ->
                    readOverride(override, "$pointer/overrides/$index")
                },
            )
            "booleanOperation" -> DesignNodeKind.BooleanOperation(
                readEnum(
                    obj["operation"], "$pointer/operation", BooleanOperationKind.Union,
                    mapOf(
                        "union" to BooleanOperationKind.Union,
                        "subtract" to BooleanOperationKind.Subtract,
                        "intersect" to BooleanOperationKind.Intersect,
                        "exclude" to BooleanOperationKind.Exclude,
                    ),
                ),
            )
            "slice" -> DesignNodeKind.Slice
            else -> {
                warn(pointer, "Unknown node type '$type' rendered as fallback")
                DesignNodeKind.Unknown(type)
            }
        }

    private fun shapeKind(shape: ShapeType, obj: JsonObject, pointer: String): DesignNodeKind.Shape =
        DesignNodeKind.Shape(
            shape = shape,
            pointCount = (obj["pointCount"] as? JsonPrimitive)?.intOrNull,
            innerRadius = (obj["innerRadius"] as? JsonPrimitive)?.doubleOrNull,
            paths = obj["paths"].asArray("$pointer/paths").mapNotNull { path ->
                val pathObj = path as? JsonObject ?: return@mapNotNull null
                VectorPath(
                    windingRule = pathObj.stringOrDefault("windingRule", "nonzero"),
                    d = pathObj.stringOrDefault("d"),
                )
            },
        )

    private fun readPropValue(element: JsonElement): PropValue? {
        val primitive = element as? JsonPrimitive ?: return null
        return when {
            primitive.isString -> PropValue.Text(primitive.content)
            primitive.booleanOrNull != null -> PropValue.Bool(primitive.booleanOrNull ?: false)
            primitive.doubleOrNull != null -> PropValue.Number(primitive.doubleOrNull ?: 0.0)
            else -> null
        }
    }

    private fun readOverride(element: JsonElement, pointer: String): InstanceOverride? {
        val obj = element as? JsonObject ?: return null
        val target = obj["target"].asArray("$pointer/target").map { it.asStringOrEmpty() }
        if (target.isEmpty()) {
            warn(pointer, "Override without target path is ignored")
            return null
        }
        val set = obj["set"] as? JsonObject ?: JsonObject(emptyMap())
        return InstanceOverride(
            target = target,
            fills = (set["fills"] as? JsonArray)?.mapIndexedNotNull { index, paint ->
                readPaint(paint, "$pointer/set/fills/$index")
            },
            strokes = (set["strokes"] as? JsonObject)?.let { readStrokes(it, "$pointer/set/strokes") },
            opacity = set["opacity"]?.let { readBindableDouble(it, 1.0) },
            visible = set["visible"]?.let { readBindableBoolean(it, true) },
            characters = set["characters"]?.let { readBindableString(it, "") },
            textStyle = (set["textStyle"] as? JsonObject)?.let { readTextStyle(it, "$pointer/set/textStyle") },
            cornerRadius = set["cornerRadius"]?.let { readCornerRadius(it, 0.0) },
        )
    }

    private fun readStyleRange(element: JsonElement, pointer: String): TextStyleRange {
        val obj: Map<String, JsonElement> = element.asObject()
        val styleObj = obj["style"] as? JsonObject
        return TextStyleRange(
            start = obj.intOrDefault("start", 0),
            end = obj.intOrDefault("end", 0),
            style = styleObj?.let { readTextStyle(it, "$pointer/style") } ?: DesignTextStyle(),
            fills = (styleObj?.get("fills") as? JsonArray)?.mapIndexedNotNull { index, paint ->
                readPaint(paint, "$pointer/style/fills/$index")
            },
        )
    }

    fun readTextStyle(obj: JsonObject, pointer: String): DesignTextStyle =
        DesignTextStyle(
            fontFamily = (obj["fontFamily"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            fontWeight = obj["fontWeight"]?.let { readBindableDouble(it, 400.0) },
            fontSize = obj["fontSize"]?.let { readBindableDouble(it, 14.0) },
            lineHeight = readUnitValue(obj["lineHeight"], "$pointer/lineHeight"),
            letterSpacing = readUnitValue(obj["letterSpacing"], "$pointer/letterSpacing"),
            paragraphSpacing = (obj["paragraphSpacing"] as? JsonPrimitive)?.doubleOrNull,
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
            fontFeatures = obj["fontFeatures"].asObject().mapValues { (_, enabled) ->
                (enabled as? JsonPrimitive)?.booleanOrNull ?: false
            },
        )

    private fun readUnitValue(element: JsonElement?, pointer: String): UnitValue? {
        val obj = element as? JsonObject ?: return null
        val unit = readEnum(
            obj["unit"], "$pointer/unit", DesignUnit.Px,
            mapOf("px" to DesignUnit.Px, "percent" to DesignUnit.Percent),
        )
        return UnitValue(unit, obj.doubleOrDefault("value", 0.0))
    }

    fun readPaint(element: JsonElement, pointer: String): DesignPaint? {
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
            "image", "video" -> DesignPaint.Image(
                assetId = obj.stringOrDefault("assetId"),
                scaleMode = readEnum(
                    obj["scaleMode"], "$pointer/scaleMode", ImageScaleMode.Fill,
                    mapOf(
                        "fill" to ImageScaleMode.Fill,
                        "fit" to ImageScaleMode.Fit,
                        "crop" to ImageScaleMode.Crop,
                        "tile" to ImageScaleMode.Tile,
                    ),
                ),
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

    fun readStrokes(obj: JsonObject, pointer: String): DesignStrokes =
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

    fun readEffect(element: JsonElement, pointer: String): DesignEffect? {
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

    fun readCornerRadius(element: JsonElement?, smoothing: Double): DesignCornerRadius? =
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

    fun readAutoLayout(obj: JsonObject?, pointer: String): DesignAutoLayout {
        if (obj == null) return DesignAutoLayout()
        val gapElement = obj["gap"]
        val gap = when {
            gapElement is JsonPrimitive && gapElement.isString && gapElement.content == "auto" -> DesignGap.Auto
            gapElement == null || gapElement is JsonObject && !gapElement.isBindingRef() -> DesignGap.Fixed(0.0.bindable())
            else -> DesignGap.Fixed(readBindableDouble(gapElement, 0.0))
        }
        val gapObject = gapElement as? JsonObject
        return DesignAutoLayout(
            mode = readEnum(
                obj["mode"], "$pointer/mode", LayoutMode.None,
                mapOf(
                    "none" to LayoutMode.None,
                    "horizontal" to LayoutMode.Horizontal,
                    "vertical" to LayoutMode.Vertical,
                    "grid" to LayoutMode.Grid,
                ),
            ),
            gap = gap,
            crossGap = obj["crossGap"]?.let { readBindableDouble(it, 0.0) },
            wrap = obj.booleanOrDefault("wrap", false),
            padding = (obj["padding"])?.let { readInsetsOrShorthand(it) } ?: DesignInsets(),
            alignItems = readEnum(
                obj["alignItems"], "$pointer/alignItems", AlignItems.Start,
                mapOf(
                    "start" to AlignItems.Start,
                    "center" to AlignItems.Center,
                    "end" to AlignItems.End,
                    "baseline" to AlignItems.Baseline,
                    "stretch" to AlignItems.Stretch,
                ),
            ),
            justifyContent = readEnum(
                obj["justifyContent"], "$pointer/justifyContent", JustifyContent.Start,
                mapOf(
                    "start" to JustifyContent.Start,
                    "center" to JustifyContent.Center,
                    "end" to JustifyContent.End,
                    "spaceBetween" to JustifyContent.SpaceBetween,
                ),
            ),
            clipsContent = obj.booleanOrDefault("clipsContent", false),
            columns = obj["columns"].asArray("$pointer/columns").mapNotNull { readGridTrack(it) },
            rows = obj["rows"].asArray("$pointer/rows").mapNotNull { readGridTrack(it) },
            columnGap = gapObject?.get("column")?.let { readBindableDouble(it, 0.0) },
            rowGap = gapObject?.get("row")?.let { readBindableDouble(it, 0.0) },
        )
    }

    private fun readGridTrack(element: JsonElement): GridTrack? {
        val obj = element as? JsonObject ?: return null
        return when (obj.stringOrDefault("type", "hug")) {
            "fixed" -> GridTrack.Fixed(obj.doubleOrDefault("value", 0.0))
            "flex" -> GridTrack.Flex(obj.doubleOrDefault("value", 1.0))
            else -> GridTrack.Hug
        }
    }

    private fun readGridPlacement(obj: JsonObject): GridPlacement =
        GridPlacement(
            column = obj.intOrDefault("column", 0),
            row = obj.intOrDefault("row", 0),
            columnSpan = obj.intOrDefault("columnSpan", 1),
            rowSpan = obj.intOrDefault("rowSpan", 1),
        )

    private fun readLayoutChild(element: JsonElement?, pointer: String): DesignLayoutChild {
        val obj = element as? JsonObject ?: return DesignLayoutChild()
        return DesignLayoutChild(
            alignSelf = obj["alignSelf"]?.let {
                readEnum(
                    it, "$pointer/alignSelf", AlignItems.Start,
                    mapOf(
                        "start" to AlignItems.Start,
                        "center" to AlignItems.Center,
                        "end" to AlignItems.End,
                        "baseline" to AlignItems.Baseline,
                        "stretch" to AlignItems.Stretch,
                    ),
                )
            },
            absolute = obj.booleanOrDefault("absolute", false),
        )
    }

    private fun readScroll(element: JsonElement?, pointer: String): DesignScroll {
        val obj = element as? JsonObject ?: return DesignScroll()
        return DesignScroll(
            overflow = readEnum(
                obj["overflow"], "$pointer/overflow", ScrollOverflow.None,
                mapOf(
                    "none" to ScrollOverflow.None,
                    "horizontal" to ScrollOverflow.Horizontal,
                    "vertical" to ScrollOverflow.Vertical,
                    "both" to ScrollOverflow.Both,
                ),
            ),
            sticky = obj.booleanOrDefault("sticky", false),
        )
    }

    private fun readConstraints(element: JsonElement?, pointer: String): DesignConstraints {
        val obj = element as? JsonObject ?: return DesignConstraints()
        return DesignConstraints(
            horizontal = readEnum(
                obj["horizontal"], "$pointer/horizontal", HorizontalConstraint.Left,
                mapOf(
                    "left" to HorizontalConstraint.Left,
                    "right" to HorizontalConstraint.Right,
                    "center" to HorizontalConstraint.Center,
                    "leftRight" to HorizontalConstraint.LeftRight,
                    "scale" to HorizontalConstraint.Scale,
                ),
            ),
            vertical = readEnum(
                obj["vertical"], "$pointer/vertical", VerticalConstraint.Top,
                mapOf(
                    "top" to VerticalConstraint.Top,
                    "bottom" to VerticalConstraint.Bottom,
                    "center" to VerticalConstraint.Center,
                    "topBottom" to VerticalConstraint.TopBottom,
                    "scale" to VerticalConstraint.Scale,
                ),
            ),
        )
    }

    private fun readSizing(element: JsonElement?, pointer: String): DesignSizing? {
        val obj = element as? JsonObject ?: return null
        val modes = mapOf(
            "fixed" to SizingMode.Fixed,
            "hug" to SizingMode.Hug,
            "fill" to SizingMode.Fill,
        )
        return DesignSizing(
            horizontal = readEnum(obj["horizontal"], "$pointer/horizontal", SizingMode.Fixed, modes),
            vertical = readEnum(obj["vertical"], "$pointer/vertical", SizingMode.Fixed, modes),
        )
    }

    private fun readSize(element: JsonElement?, pointer: String): DesignSize {
        val obj = element as? JsonObject ?: return DesignSize()
        warnIfBinding(obj["width"], "$pointer/width")
        warnIfBinding(obj["height"], "$pointer/height")
        return DesignSize(
            width = (obj["width"] as? JsonPrimitive)?.doubleOrNull,
            height = (obj["height"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    private fun readPoint(obj: JsonObject, pointer: String): DesignPoint =
        DesignPoint(
            x = obj.plainDouble("x", pointer, 0.0),
            y = obj.plainDouble("y", pointer, 0.0),
        )

    private fun readInsetsOrShorthand(element: JsonElement): DesignInsets =
        when (element) {
            is JsonObject -> readInsets(element)
            is JsonPrimitive -> {
                val all = readBindableDouble(element, 0.0)
                DesignInsets(all, all, all, all)
            }
            else -> DesignInsets()
        }

    private fun readInsets(obj: JsonObject): DesignInsets =
        DesignInsets(
            top = readBindableDouble(obj["top"], 0.0),
            right = readBindableDouble(obj["right"], 0.0),
            bottom = readBindableDouble(obj["bottom"], 0.0),
            left = readBindableDouble(obj["left"], 0.0),
        )

    fun readComponent(element: JsonElement, pointer: String): DesignComponent? {
        val obj = element as? JsonObject ?: return null
        val rootElement = obj["root"] ?: run {
            error(pointer, "Component is missing 'root' node")
            return null
        }
        val root = readNode(rootElement, "$pointer/root") ?: return null
        return DesignComponent(
            name = obj.stringOrDefault("name"),
            properties = obj["properties"].asObject().mapNotNull { (name, def) ->
                readPropertyDefinition(def, "$pointer/properties/$name")?.let { name to it }
            }.toMap(),
            root = root,
        )
    }

    private fun readPropertyDefinition(element: JsonElement, pointer: String): ComponentPropertyDefinition? {
        val obj = element as? JsonObject ?: return null
        return ComponentPropertyDefinition(
            type = readEnum(
                obj["type"], "$pointer/type", ComponentPropertyType.Text,
                mapOf(
                    "text" to ComponentPropertyType.Text,
                    "boolean" to ComponentPropertyType.Boolean,
                    "instanceSwap" to ComponentPropertyType.InstanceSwap,
                    "variant" to ComponentPropertyType.Variant,
                ),
            ),
            default = obj["default"]?.let { readPropValue(it) },
        )
    }

    private fun readComponentSet(element: JsonElement, pointer: String): DesignComponentSet {
        val obj = element.asObject()
        return DesignComponentSet(
            name = obj.stringOrDefault("name"),
            axes = obj["axes"].asObject().mapValues { (_, values) ->
                values.asArray("$pointer/axes").map { it.asStringOrEmpty() }
            },
            variants = obj["variants"].asObject().mapValues { (_, componentId) ->
                componentId.asStringOrEmpty()
            },
        )
    }

    private fun readStyle(element: JsonElement, pointer: String): DesignStyle? {
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
            else -> {
                warn(pointer, "Unknown style type '$type' ignored")
                null
            }
        }
    }

    private fun readVariables(element: JsonElement?, pointer: String): DesignVariables {
        val obj = element as? JsonObject ?: return DesignVariables()
        return DesignVariables(
            collections = obj["collections"].asObject().mapValues { (collectionId, collection) ->
                readVariableCollection(collection, "$pointer/collections/$collectionId")
            },
        )
    }

    private fun readVariableCollection(element: JsonElement, pointer: String): VariableCollection {
        val obj = element.asObject()
        val modes = obj["modes"].asArray("$pointer/modes").map { it.asStringOrEmpty() }
        return VariableCollection(
            name = obj.stringOrDefault("name"),
            modes = modes,
            defaultMode = obj.stringOrDefault("defaultMode", modes.firstOrNull().orEmpty()),
            vars = obj["vars"].asObject().mapNotNull { (varId, variable) ->
                readVariable(variable, "$pointer/vars/$varId")?.let { varId to it }
            }.toMap(),
        )
    }

    private fun readVariable(element: JsonElement, pointer: String): DesignVariable? {
        val obj = element as? JsonObject ?: return null
        val type = readEnum(
            obj["type"], "$pointer/type", VariableType.Text,
            mapOf(
                "color" to VariableType.Color,
                "number" to VariableType.Number,
                "string" to VariableType.Text,
                "boolean" to VariableType.Bool,
            ),
        )
        return DesignVariable(
            type = type,
            values = obj["values"].asObject().mapNotNull { (mode, value) ->
                readVariableValue(value, type, "$pointer/values/$mode")?.let { mode to it }
            }.toMap(),
        )
    }

    private fun readVariableValue(element: JsonElement, type: VariableType, pointer: String): VariableValue? {
        if (element is JsonObject) {
            val alias = (element["\$var"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (alias != null) return VariableValue.Alias(alias)
            warn(pointer, "Variable value object must be an alias {\"\$var\": id}")
            return null
        }
        val primitive = element as? JsonPrimitive ?: return null
        return when (type) {
            VariableType.Color -> DesignColor.fromHex(primitive.content)?.let { VariableValue.ColorValue(it) }
                ?: run {
                    warn(pointer, "Invalid color '${primitive.content}'")
                    null
                }
            VariableType.Number -> primitive.doubleOrNull?.let { VariableValue.NumberValue(it) }
            VariableType.Text -> VariableValue.TextValue(primitive.content)
            VariableType.Bool -> primitive.booleanOrNull?.let { VariableValue.BoolValue(it) }
        }
    }

    private fun readAsset(element: JsonElement): DesignAsset {
        val obj = element.asObject()
        return DesignAsset(
            type = obj.stringOrDefault("type", "image"),
            hash = obj.stringOrDefault("hash"),
            url = obj.stringOrDefault("url"),
        )
    }

    // --- Bindable scalars -------------------------------------------------

    fun readBindableDouble(element: JsonElement?, fallback: Double): Bindable<Double> =
        readBindable(element) { primitive -> primitive.doubleOrNull } ?: fallback.bindable()

    fun readBindableString(element: JsonElement?, fallback: String): Bindable<String> =
        readBindable(element) { primitive -> primitive.content.takeIf { primitive.isString } } ?: fallback.bindable()

    fun readBindableBoolean(element: JsonElement?, fallback: Boolean): Bindable<Boolean> =
        readBindable(element) { primitive -> primitive.booleanOrNull } ?: fallback.bindable()

    fun readBindableColor(element: JsonElement?, pointer: String): Bindable<DesignColor> {
        val bound = readBindable(element) { primitive ->
            DesignColor.fromHex(primitive.content)
        }
        if (bound == null && element != null) {
            warn(pointer, "Invalid color value")
        }
        return bound ?: DesignColor.Black.bindable()
    }

    private fun <T> readBindable(element: JsonElement?, convert: (JsonPrimitive) -> T?): Bindable<T>? =
        when (element) {
            is JsonObject -> {
                val varRef = (element["\$var"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val propRef = (element["\$prop"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                when {
                    varRef != null -> Bindable.VarRef(varRef)
                    propRef != null -> Bindable.PropRef(propRef)
                    else -> null
                }
            }
            is JsonPrimitive -> convert(element)?.let { Bindable.Value(it) }
            else -> null
        }

    // --- JSON helpers -----------------------------------------------------

    private fun JsonObject.isBindingRef(): Boolean =
        containsKey("\$var") || containsKey("\$prop")

    private fun JsonElement?.asObject(): Map<String, JsonElement> =
        (this as? JsonObject) ?: emptyMap()

    private fun JsonElement?.asArray(pointer: String): List<JsonElement> =
        when (this) {
            null -> emptyList()
            is JsonArray -> this
            else -> {
                warn(pointer, "Expected an array")
                emptyList()
            }
        }

    private fun JsonElement.asStringOrEmpty(): String =
        (this as? JsonPrimitive)?.content.orEmpty()

    private fun Map<String, JsonElement>.stringOrDefault(key: String, default: String = ""): String =
        ((this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: default

    private fun Map<String, JsonElement>.doubleOrDefault(key: String, default: Double): Double =
        (this[key] as? JsonPrimitive)?.doubleOrNull ?: default

    private fun Map<String, JsonElement>.intOrDefault(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: default

    private fun Map<String, JsonElement>.booleanOrDefault(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    /** Reads a scalar that does NOT support bindings, warning instead of silently dropping one. */
    private fun Map<String, JsonElement>.plainDouble(key: String, pointer: String, default: Double): Double {
        warnIfBinding(this[key], "$pointer/$key")
        return (this[key] as? JsonPrimitive)?.doubleOrNull ?: default
    }

    private fun warnIfBinding(element: JsonElement?, pointer: String) {
        if (element is JsonObject && element.isBindingRef()) {
            warn(pointer, "Bindings are not supported for this field; using default")
        }
    }

    private fun <T> readEnum(
        element: JsonElement?,
        pointer: String,
        fallback: T,
        values: Map<String, T>,
    ): T {
        val raw = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return fallback
        val match = values[raw]
        if (match == null) {
            warn(pointer, "Unknown value '$raw', using fallback")
            return fallback
        }
        return match
    }

    fun warn(pointer: String, message: String) {
        diagnostics += DesignDiagnostic(DesignSeverity.Warning, message, SourceLocation(pointer))
    }

    fun error(pointer: String, message: String) {
        diagnostics += DesignDiagnostic(DesignSeverity.Error, message, SourceLocation(pointer))
    }
}
