package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignCondition
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignMask
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignRepeat
import io.aequicor.visualization.engine.ir.model.DesignTable
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.subsystems.figures.DesignViewBox
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextFormat
import io.aequicor.visualization.engine.ir.model.TextFormatKind
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorPath
import io.aequicor.visualization.subsystems.figures.VectorRegion
import io.aequicor.visualization.subsystems.figures.VectorSegment
import io.aequicor.visualization.subsystems.figures.VectorVertex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Node core, node kinds (incl. media/table/slot/annotation), mask, and source maps. */

internal fun DesignDocumentReader.readNode(element: JsonElement, pointer: String): DesignNode? {
    val obj = element as? JsonObject ?: run {
        error(pointer, "Node must be an object")
        return null
    }
    val type = obj.stringOrDefault("type", "frame")
    val kind = readKind(type, obj, pointer)
    val layoutObj = obj["layout"] as? JsonObject
    val layout = readAutoLayout(layoutObj, "$pointer/layout")
    val containerKind = if ("containerKind" !in obj) {
        // Field absent (legacy/foreign/partial JSON): infer from the parsed layout so a
        // non-none layout mode is not silently downgraded to Frame (IR-LAYOUT-009).
        if (layout.mode != LayoutMode.None) ContainerKind.AutoLayout else ContainerKind.Frame
    } else {
        when (val raw = obj.stringOrDefault("containerKind", "frame")) {
            "frame" -> ContainerKind.Frame
            "autoLayout" -> ContainerKind.AutoLayout
            else -> {
                error("$pointer/containerKind", "Unknown containerKind '$raw'; expected frame or autoLayout")
                ContainerKind.Frame
            }
        }
    }
    return DesignNode(
        id = obj.stringOrDefault("id"),
        type = type,
        kind = kind,
        containerKind = containerKind,
        name = obj.stringOrDefault("name"),
        role = obj.stringOrDefault("role"),
        visible = readBindableBoolean(obj["visible"], true),
        locked = obj.booleanOrDefault("locked", false),
        order = (obj["order"] as? JsonPrimitive)?.intOrNull,
        opacity = readBindableDouble(obj["opacity"], 1.0),
        blendMode = obj.stringOrDefault("blendMode", "normal"),
        rotation = obj.plainDouble("rotation", pointer, 0.0),
        isMask = obj.booleanOrDefault("isMask", false),
        mask = (obj["mask"] as? JsonObject)?.let { readMask(it, "$pointer/mask") },
        position = (obj["position"] as? JsonObject)?.let { readPoint(it, "$pointer/position") },
        constraints = readConstraints(obj["constraints"], "$pointer/constraints"),
        anchors = readAnchors(obj["anchors"]),
        size = readSize(obj["size"], "$pointer/size"),
        sizing = readSizing(obj["sizing"], "$pointer/sizing"),
        minSize = (obj["minSize"] as? JsonObject)?.let { readSize(it, "$pointer/minSize") },
        maxSize = (obj["maxSize"] as? JsonObject)?.let { readSize(it, "$pointer/maxSize") },
        layout = layout,
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
        gridStyleId = obj.stringOrDefault("gridStyleId"),
        layoutGrids = readLayoutGrids(obj["layoutGrids"], "$pointer/layoutGrids"),
        guides = readGuides(obj["guides"], "$pointer/guides"),
        variableModes = obj["variableModes"].asObject().mapValues { (_, mode) ->
            mode.asStringOrEmpty()
        },
        condition = readCondition(obj["condition"], "$pointer/condition"),
        repeat = readRepeat(obj["repeat"], "$pointer/repeat"),
        interactions = readInteractions(obj["interactions"], "$pointer/interactions"),
        motion = (obj["motion"] as? JsonObject)?.let { readMotion(it, "$pointer/motion") },
        responsive = readResponsiveVariants(obj["responsive"], "$pointer/responsive"),
        exportSettings = readExportSettings(obj["exportSettings"], "$pointer/exportSettings"),
        scroll = readScroll(obj["scroll"], "$pointer/scroll"),
        sourceMap = readSourceLocation(obj["sourceMap"], pointer),
        blockSourceMaps = readBlockSourceMaps(obj["blockSourceMaps"], pointer),
        children = readChildren(obj, pointer),
    )
}

internal fun DesignDocumentReader.readChildren(
    obj: Map<String, JsonElement>,
    pointer: String,
): List<DesignNode> =
    obj["children"].asArray("$pointer/children").mapIndexedNotNull { index, child ->
        readNode(child, "$pointer/children/$index")
    }

private fun DesignDocumentReader.readKind(type: String, obj: JsonObject, pointer: String): DesignNodeKind =
    when (type) {
        "frame", "group", "section", "component", "screen" -> DesignNodeKind.Frame
        "text" -> readTextKind(obj, pointer)
        "rectangle" -> shapeKind(ShapeType.Rectangle, obj, pointer)
        "ellipse" -> shapeKind(ShapeType.Ellipse, obj, pointer)
        "polygon" -> shapeKind(ShapeType.Polygon, obj, pointer)
        "star" -> shapeKind(ShapeType.Star, obj, pointer)
        "line" -> shapeKind(ShapeType.Line, obj, pointer)
        "arrow" -> shapeKind(ShapeType.Arrow, obj, pointer)
        "vector" -> shapeKind(ShapeType.Vector, obj, pointer)
        "diagram" -> readDiagramKind(obj, pointer)
        "instance" -> DesignNodeKind.Instance(
            componentId = readBindableString(obj["componentId"], ""),
            libraryRef = obj.stringOrDefault("libraryRef"),
            variant = obj["variant"].asObject().mapValues { (_, value) -> value.asStringOrEmpty() },
            props = obj["props"].asObject().mapNotNull { (name, value) ->
                readPropValue(value, "$pointer/props/$name")?.let { name to it }
            }.toMap(),
            overrides = obj["overrides"].asArray("$pointer/overrides").mapIndexedNotNull { index, override ->
                readOverride(override, "$pointer/overrides/$index")
            },
            detach = obj.booleanOrDefault("detach", false),
            resetOverrides = obj.booleanOrDefault("resetOverrides", false),
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
        "media" -> DesignNodeKind.Media(readMedia(obj, pointer))
        "table" -> DesignNodeKind.Table(readTable(obj, pointer))
        "slot" -> DesignNodeKind.Slot(
            slotName = obj.stringOrDefault("slot", obj.stringOrDefault("slotName", obj.stringOrDefault("name"))),
        )
        "annotation" -> DesignNodeKind.Annotation(readNodeAnnotation(obj, pointer))
        else -> {
            warn(pointer, "Unknown node type '$type' rendered as fallback")
            DesignNodeKind.Unknown(type)
        }
    }

private fun DesignDocumentReader.readTextKind(obj: JsonObject, pointer: String): DesignNodeKind.Text =
    DesignNodeKind.Text(
        characters = readBindableString(obj["characters"], ""),
        content = (obj["content"] as? JsonObject)?.let { readTextContent(it, "$pointer/content") },
        format = (obj["format"] as? JsonObject)?.let { readTextFormat(it, "$pointer/format") },
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
        links = obj["links"].asArray("$pointer/links").map { readTextLink(it) },
        list = readTextListSettings(obj["list"], "$pointer/list"),
    )

private fun DesignDocumentReader.shapeKind(
    shape: ShapeType,
    obj: JsonObject,
    pointer: String,
): DesignNodeKind.Shape =
    DesignNodeKind.Shape(
        shape = shape,
        pointCount = (obj["pointCount"] as? JsonPrimitive)?.intOrNull,
        innerRadius = (obj["innerRadius"] as? JsonPrimitive)?.doubleOrNull,
        arcStartDeg = (obj["arcStartDeg"] as? JsonPrimitive)?.doubleOrNull,
        arcSweepDeg = (obj["arcSweepDeg"] as? JsonPrimitive)?.doubleOrNull,
        paths = obj["paths"].asArray("$pointer/paths").mapNotNull { path ->
            val pathObj = path as? JsonObject ?: return@mapNotNull null
            VectorPath(
                windingRule = pathObj.stringOrDefault("windingRule", "nonzero"),
                d = pathObj.stringOrDefault("d"),
            )
        },
        iconRef = obj.stringOrDefault("iconRef"),
        pathRef = obj.stringOrDefault("pathRef"),
        viewBox = (obj["viewBox"] as? JsonObject)?.let { viewBox ->
            DesignViewBox(
                x = viewBox.plainDouble("x", "$pointer/viewBox", 0.0),
                y = viewBox.plainDouble("y", "$pointer/viewBox", 0.0),
                width = viewBox.plainDouble("width", "$pointer/viewBox", 0.0),
                height = viewBox.plainDouble("height", "$pointer/viewBox", 0.0),
            )
        },
        network = readVectorNetwork(obj["network"], "$pointer/network"),
        regionFills = readRegionFills(obj["regionFills"], "$pointer/regionFills"),
    )

private fun DesignDocumentReader.readRegionFills(element: JsonElement?, pointer: String): Map<Int, List<DesignPaint>> {
    val obj = element as? JsonObject ?: return emptyMap()
    val result = LinkedHashMap<Int, List<DesignPaint>>()
    obj.forEach { (key, value) ->
        val index = key.toIntOrNull() ?: return@forEach
        val paints = (value as? JsonArray)?.mapNotNull { readPaint(it, "$pointer/$key") } ?: return@forEach
        if (paints.isNotEmpty()) result[index] = paints
    }
    return result
}

private fun DesignDocumentReader.readVectorNetwork(element: JsonElement?, pointer: String): VectorNetwork? {
    val obj = element as? JsonObject ?: return null
    val vertices = obj["vertices"].asArray("$pointer/vertices").mapNotNull { vertex ->
        val vertexObj = vertex as? JsonObject ?: return@mapNotNull null
        VectorVertex(
            x = vertexObj.plainDouble("x", "$pointer/vertices", 0.0),
            y = vertexObj.plainDouble("y", "$pointer/vertices", 0.0),
            inHandle = readHandleOffset(vertexObj["in"]),
            outHandle = readHandleOffset(vertexObj["out"]),
            mirror = readEnum(
                vertexObj["mirror"], "$pointer/vertices/mirror", HandleMirror.None,
                mapOf(
                    "none" to HandleMirror.None,
                    "angle" to HandleMirror.Angle,
                    "angleAndLength" to HandleMirror.AngleAndLength,
                ),
            ),
            corner = vertexObj.booleanOrDefault("corner", false),
            cornerRadius = vertexObj.plainDouble("cornerRadius", "$pointer/vertices", 0.0),
        )
    }
    val segments = obj["segments"].asArray("$pointer/segments").mapNotNull { segment ->
        val segmentObj = segment as? JsonObject ?: return@mapNotNull null
        VectorSegment(segmentObj.intOrDefault("from", 0), segmentObj.intOrDefault("to", 0))
    }
    val regions = obj["regions"].asArray("$pointer/regions").mapNotNull { region ->
        val regionObj = region as? JsonObject ?: return@mapNotNull null
        VectorRegion(
            windingRule = regionObj.stringOrDefault("windingRule", "nonzero"),
            loops = regionObj["loops"].asArray("$pointer/regions/loops").map { loop ->
                loop.asArray("$pointer/regions/loops").mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            },
        )
    }
    return VectorNetwork(vertices, segments, regions).takeIf { it.isNotEmpty() }
}

private fun readHandleOffset(element: JsonElement?): HandleOffset? {
    val obj = element as? JsonObject ?: return null
    return HandleOffset(
        dx = (obj["dx"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        dy = (obj["dy"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
    )
}

/** Media settings live in a "media" block; top-level fields are accepted as shorthand. */
private fun DesignDocumentReader.readMedia(obj: JsonObject, pointer: String): DesignMedia {
    val media = obj["media"] as? JsonObject ?: obj
    return DesignMedia(
        assetId = readBindableString(media["assetId"], ""),
        kind = readEnum(
            media["kind"], "$pointer/kind", MediaKind.Image,
            mapOf("image" to MediaKind.Image, "video" to MediaKind.Video),
        ),
        fillMode = readImageScaleMode(media["fillMode"], "$pointer/fillMode"),
        focalPoint = (media["focalPoint"] as? JsonObject)?.let { readPoint(it, "$pointer/focalPoint") },
        alt = (media["alt"] as? JsonObject)?.let { readTextContent(it, "$pointer/alt") },
        replaceable = media.booleanOrDefault("replaceable", false),
        opacity = readBindableDouble(media["opacity"], 1.0),
        blendMode = media.stringOrDefault("blendMode", "normal"),
        posterAssetId = readBindableString(media["posterAssetId"], ""),
        autoplay = media.booleanOrDefault("autoplay", false),
        loop = media.booleanOrDefault("loop", false),
        muted = media.booleanOrDefault("muted", true),
    )
}

/** Table settings live in a "table" block; top-level fields are accepted as shorthand. */
private fun DesignDocumentReader.readTable(obj: JsonObject, pointer: String): DesignTable {
    val table = obj["table"] as? JsonObject ?: obj
    return DesignTable(
        columns = table["columns"].asArray("$pointer/columns").mapNotNull { readGridTrack(it) },
        headerRows = table.intOrDefault("headerRows", 1),
        rowGap = readBindableDouble(table["rowGap"], 0.0),
        columnGap = readBindableDouble(table["columnGap"], 0.0),
    )
}

private fun DesignDocumentReader.readNodeAnnotation(obj: JsonObject, pointer: String): DesignAnnotation {
    val annotation = obj["annotation"] as? JsonObject ?: obj
    return DesignAnnotation(
        id = annotation.stringOrDefault("id", obj.stringOrDefault("id")),
        target = annotation.stringOrDefault("target"),
        text = annotation.stringOrDefault("text"),
        audience = annotation.stringOrDefault("audience"),
    )
}

internal fun DesignDocumentReader.readMask(obj: JsonObject, pointer: String): DesignMask =
    DesignMask(
        type = readEnum(
            obj["type"], "$pointer/type", MaskType.Alpha,
            mapOf(
                "alpha" to MaskType.Alpha,
                "vector" to MaskType.Vector,
                "luminance" to MaskType.Luminance,
            ),
        ),
        appliesTo = obj["appliesTo"].asArray("$pointer/appliesTo").map { it.asStringOrEmpty() },
        source = obj.stringOrDefault("source"),
    )

// --- Data directives ------------------------------------------------------

internal fun DesignDocumentReader.readCondition(element: JsonElement?, pointer: String): DesignCondition? {
    if (element == null) return null
    val raw = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: run {
        warn(pointer, "Condition must be an expression string")
        return null
    }
    return DesignCondition(DesignExpression(raw.stripMustache()))
}

/** Object form `{item, in, index, key}` or string shorthand `"item in collection"`. */
internal fun DesignDocumentReader.readRepeat(element: JsonElement?, pointer: String): DesignRepeat? =
    when (element) {
        null -> null
        is JsonObject -> {
            val itemName = element.stringOrDefault("item", element.stringOrDefault("itemName"))
            val collection = element.stringOrDefault("in", element.stringOrDefault("collection"))
            if (itemName.isEmpty() || collection.isEmpty()) {
                warn(pointer, "Repeat needs 'item' and 'in'; directive ignored")
                null
            } else {
                DesignRepeat(
                    itemName = itemName,
                    collection = DesignExpression(collection.stripMustache()),
                    indexName = element.stringOrDefault("index", element.stringOrDefault("indexName"))
                        .takeIf { it.isNotEmpty() },
                    key = element.stringOrDefault("key").takeIf { it.isNotEmpty() }
                        ?.let { DesignExpression(it.stripMustache()) },
                )
            }
        }
        is JsonPrimitive -> {
            val parts = element.content.stripMustache().split(" in ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                DesignRepeat(
                    itemName = parts[0].trim(),
                    collection = DesignExpression(parts[1].trim()),
                )
            } else {
                warn(pointer, "Repeat shorthand must look like 'item in collection'; directive ignored")
                null
            }
        }
        else -> {
            warn(pointer, "Repeat must be an object or a string; directive ignored")
            null
        }
    }

// --- Text content ----------------------------------------------------------

internal fun DesignDocumentReader.readTextContent(obj: JsonObject, pointer: String): TextContent =
    TextContent(
        key = obj.stringOrDefault("key"),
        defaultLocale = obj.stringOrDefault("defaultLocale"),
        defaultText = obj.stringOrDefault("defaultText"),
        params = obj["params"].asObject().mapValues { (_, value) -> readContentParam(value) },
    )

/** A param is a bindable string; `"{{expr}}"` strings become data bindings. */
private fun DesignDocumentReader.readContentParam(element: JsonElement?): Bindable<String> {
    val raw = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (raw != null && raw.isMustache()) {
        return Bindable.DataRef(DesignExpression(raw.stripMustache()))
    }
    return readBindableString(element, "")
}

private fun DesignDocumentReader.readTextFormat(obj: JsonObject, pointer: String): TextFormat =
    TextFormat(
        kind = readEnum(
            obj["type"] ?: obj["kind"], "$pointer/type", TextFormatKind.Number,
            mapOf(
                "date" to TextFormatKind.Date,
                "time" to TextFormatKind.Time,
                "number" to TextFormatKind.Number,
                "currency" to TextFormatKind.Currency,
                "relativeTime" to TextFormatKind.RelativeTime,
                "percent" to TextFormatKind.Percent,
            ),
        ),
        options = obj["options"].asObject().mapValues { (_, value) -> value.asStringOrEmpty() },
    )

private fun DesignDocumentReader.readTextListSettings(
    element: JsonElement?,
    pointer: String,
): TextListSettings {
    val obj = element as? JsonObject ?: return TextListSettings()
    return TextListSettings(
        type = readEnum(
            obj["type"], "$pointer/type", TextListType.None,
            mapOf(
                "none" to TextListType.None,
                "bullet" to TextListType.Bullet,
                "ordered" to TextListType.Ordered,
            ),
        ),
        indent = obj.intOrDefault("indent", 0),
    )
}

internal fun DesignDocumentReader.readStyleRange(element: JsonElement, pointer: String): TextStyleRange {
    val obj: Map<String, JsonElement> = element.asObject()
    // A "style" string is a shared-style ref; a "style" object is inline typography.
    val styleObj = obj["style"] as? JsonObject
    return TextStyleRange(
        start = obj.intOrDefault("start", 0),
        end = obj.intOrDefault("end", 0),
        styleRef = obj.stringOrDefault("styleRef").ifEmpty { obj.stringOrDefault("style") },
        style = styleObj?.let { readTextStyle(it, "$pointer/style") } ?: DesignTextStyle(),
        fills = (styleObj?.get("fills") as? JsonArray)?.mapIndexedNotNull { index, paint ->
            readPaint(paint, "$pointer/style/fills/$index")
        },
    )
}

internal fun DesignDocumentReader.readTextLink(element: JsonElement): TextLink {
    val linkObj = element.asObject()
    return TextLink(
        start = linkObj.intOrDefault("start", 0),
        end = linkObj.intOrDefault("end", 0),
        url = linkObj.stringOrDefault("url"),
        nodeTarget = linkObj.stringOrDefault("nodeTarget"),
    )
}

// --- Source maps ------------------------------------------------------------

internal fun DesignDocumentReader.readBlockSourceMaps(
    element: JsonElement?,
    pointer: String,
): Map<String, SourceLocation> =
    element.asObject().mapNotNull { (block, location) ->
        readSourceLocation(location, "$pointer/blockSourceMaps/$block")?.let { block to it }
    }.toMap()
