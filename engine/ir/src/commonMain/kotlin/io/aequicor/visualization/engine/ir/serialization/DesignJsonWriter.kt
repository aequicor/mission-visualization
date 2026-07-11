package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.HandleMirror
import io.aequicor.visualization.engine.ir.model.HandleOffset
import io.aequicor.visualization.engine.ir.model.VectorNetwork
import io.aequicor.visualization.engine.ir.model.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignAnchors
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignAsset
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignBreakpoint
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignComponentSet
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignI18n
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignLayoutChild
import io.aequicor.visualization.engine.ir.model.DesignLibrary
import io.aequicor.visualization.engine.ir.model.DesignLogicalInsets
import io.aequicor.visualization.engine.ir.model.DesignMask
import io.aequicor.visualization.engine.ir.model.DesignMeasurement
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignNodePatch
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignRepeat
import io.aequicor.visualization.engine.ir.model.DesignScreenMeta
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignTable
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignTransition
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.DesignVariables
import io.aequicor.visualization.engine.ir.model.DevicePreset
import io.aequicor.visualization.engine.ir.model.EasingKind
import io.aequicor.visualization.engine.ir.model.ExportFormat
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutGridType
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MeasureAxis
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.OverlaySettings
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.ResponsiveVariant
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextFormat
import io.aequicor.visualization.engine.ir.model.TextFormatKind
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Canonical-compact JSON writer, the inverse of [parseDesignDocument]: fields equal to
 * their model defaults are omitted; `schemaVersion`, node `id`/`type`, and required
 * (defaultless) fields are always emitted. Field spellings match the readers exactly,
 * so `parse(write(document))` reproduces the document.
 *
 * Known asymmetries: [PropValue.Reference] re-parses as [PropValue.Text] (both are a
 * plain JSON string), and [SourceLocation.pointer] is not serialized — the parser
 * recomputes it from the JSON position.
 */
fun writeDesignDocument(document: DesignDocument): JsonObject = buildJsonObject {
    put("schemaVersion", document.schemaVersion)
    if (document.id.isNotEmpty()) put("id", document.id)
    if (document.name.isNotEmpty()) put("name", document.name)
    if (document.pages.isNotEmpty()) put("pages", JsonArray(document.pages.map(::writePage)))
    if (document.components.isNotEmpty()) {
        put(
            "components",
            buildJsonObject {
                document.components.forEach { (id, component) -> put(id, writeComponent(component)) }
            },
        )
    }
    if (document.componentSets.isNotEmpty()) {
        put(
            "componentSets",
            buildJsonObject {
                document.componentSets.forEach { (id, set) -> put(id, writeComponentSet(set)) }
            },
        )
    }
    if (document.styles.isNotEmpty()) {
        put(
            "styles",
            buildJsonObject {
                document.styles.forEach { (id, style) -> put(id, writeStyle(style)) }
            },
        )
    }
    if (document.variables != DesignVariables()) put("variables", writeVariables(document.variables))
    if (document.assets.isNotEmpty()) {
        put(
            "assets",
            buildJsonObject {
                document.assets.forEach { (id, asset) -> put(id, writeAsset(asset)) }
            },
        )
    }
    document.screen?.let { put("screen", writeScreenMeta(it)) }
    if (document.libraries.isNotEmpty()) put("libraries", JsonArray(document.libraries.map(::writeLibrary)))
    if (document.breakpoints.isNotEmpty()) put("breakpoints", JsonArray(document.breakpoints.map(::writeBreakpoint)))
    if (document.devicePresets.isNotEmpty()) {
        put("devicePresets", JsonArray(document.devicePresets.map(::writeDevicePreset)))
    }
    if (document.prototypeVariables.isNotEmpty()) {
        put(
            "prototypeVariables",
            buildJsonObject {
                document.prototypeVariables.forEach { (name, variable) ->
                    put(
                        name,
                        buildJsonObject {
                            put("type", variableTypeName(variable.type))
                            variable.default?.let { put("default", variableValueJson(it)) }
                        },
                    )
                }
            },
        )
    }
    if (document.actionSets.isNotEmpty()) {
        put(
            "actionSets",
            buildJsonObject {
                document.actionSets.forEach { (id, actions) -> put(id, JsonArray(actions.map(::writeAction))) }
            },
        )
    }
    if (document.i18n != DesignI18n()) put("i18n", writeI18n(document.i18n))
    if (document.handoff != DesignHandoff()) put("handoff", writeHandoff(document.handoff))
    if (document.motionRefs.isNotEmpty()) put("motionRefs", stringMapJson(document.motionRefs))
}

/** Serializes one node subtree; the inverse of the node reader (see [parseDesignNode]). */
fun writeDesignNode(node: DesignNode): JsonObject = buildJsonObject {
    put("id", node.id)
    put("type", nodeTypeString(node))
    if (node.name.isNotEmpty()) put("name", node.name)
    if (node.role.isNotEmpty()) put("role", node.role)
    putKindFields(node.kind)
    if (node.visible != true.bindable()) put("visible", booleanJson(node.visible))
    if (node.locked) put("locked", true)
    node.order?.let { put("order", it) }
    if (node.opacity != 1.0.bindable()) put("opacity", doubleJson(node.opacity))
    if (node.blendMode != "normal") put("blendMode", node.blendMode)
    if (node.rotation != 0.0) put("rotation", node.rotation)
    if (node.isMask) put("isMask", true)
    node.mask?.let { put("mask", writeMask(it)) }
    node.position?.let { put("position", writePoint(it)) }
    if (node.constraints != DesignConstraints()) put("constraints", writeConstraints(node.constraints))
    node.anchors?.let { put("anchors", writeAnchors(it)) }
    if (node.size != DesignSize()) put("size", writeSize(node.size))
    node.sizing?.let { put("sizing", writeSizing(it)) }
    node.minSize?.let { put("minSize", writeSize(it)) }
    node.maxSize?.let { put("maxSize", writeSize(it)) }
    if (node.layout != DesignAutoLayout()) put("layout", writeAutoLayout(node.layout))
    if (node.layoutChild != DesignLayoutChild()) put("layoutChild", writeLayoutChild(node.layoutChild))
    node.gridPlacement?.let { put("gridPlacement", writeGridPlacement(it)) }
    node.fills?.let { fills -> put("fills", JsonArray(fills.map(::writePaint))) }
    node.strokes?.let { put("strokes", writeStrokes(it)) }
    if (node.effects.isNotEmpty()) put("effects", JsonArray(node.effects.map(::writeEffect)))
    node.cornerRadius?.let { radius ->
        put("cornerRadius", cornerRadiusJson(radius))
        if (radius.smoothing != 0.0) put("cornerSmoothing", radius.smoothing)
    }
    if (node.fillStyleId.isNotEmpty()) put("fillStyleId", node.fillStyleId)
    if (node.strokeStyleId.isNotEmpty()) put("strokeStyleId", node.strokeStyleId)
    if (node.effectStyleId.isNotEmpty()) put("effectStyleId", node.effectStyleId)
    if (node.gridStyleId.isNotEmpty()) put("gridStyleId", node.gridStyleId)
    if (node.layoutGrids.isNotEmpty()) put("layoutGrids", JsonArray(node.layoutGrids.map(::writeLayoutGrid)))
    if (node.guides.isNotEmpty()) put("guides", JsonArray(node.guides.map(::writeGuide)))
    if (node.variableModes.isNotEmpty()) put("variableModes", stringMapJson(node.variableModes))
    node.condition?.let { put("condition", it.expression.raw) }
    node.repeat?.let { put("repeat", writeRepeat(it)) }
    if (node.interactions.isNotEmpty()) put("interactions", JsonArray(node.interactions.map(::writeInteraction)))
    node.motion?.let { put("motion", writeMotion(it)) }
    if (node.responsive.isNotEmpty()) put("responsive", JsonArray(node.responsive.map(::writeResponsiveVariant)))
    if (node.exportSettings.isNotEmpty()) {
        put("exportSettings", JsonArray(node.exportSettings.map(::writeExportSetting)))
    }
    if (node.scroll != DesignScroll()) put("scroll", writeScroll(node.scroll))
    node.sourceMap?.let { put("sourceMap", writeSourceLocation(it)) }
    if (node.blockSourceMaps.isNotEmpty()) {
        put(
            "blockSourceMaps",
            buildJsonObject {
                node.blockSourceMaps.forEach { (block, location) -> put(block, writeSourceLocation(location)) }
            },
        )
    }
    if (node.children.isNotEmpty()) put("children", JsonArray(node.children.map(::writeDesignNode)))
}

private val PrettyJson = Json { prettyPrint = true }

/** Renders the object as a JSON string; compact by default, indented when [pretty]. */
fun JsonObject.toJsonString(pretty: Boolean = false): String =
    if (pretty) PrettyJson.encodeToString(JsonObject.serializer(), this) else toString()

// --- Node kinds -------------------------------------------------------------

private fun nodeTypeString(node: DesignNode): String =
    (node.kind as? DesignNodeKind.Unknown)?.rawType?.takeIf { it.isNotEmpty() } ?: node.type

private fun JsonObjectBuilder.putKindFields(kind: DesignNodeKind) {
    when (kind) {
        DesignNodeKind.Frame, DesignNodeKind.Slice -> Unit
        is DesignNodeKind.Unknown -> Unit
        is DesignNodeKind.Text -> putTextKindFields(kind)
        is DesignNodeKind.Shape -> putShapeKindFields(kind)
        is DesignNodeKind.Instance -> putInstanceKindFields(kind)
        is DesignNodeKind.BooleanOperation -> put("operation", booleanOperationName(kind.operation))
        is DesignNodeKind.Media -> put("media", writeMedia(kind.media))
        is DesignNodeKind.Table -> put("table", writeTable(kind.table))
        is DesignNodeKind.Slot -> put("slot", kind.slotName)
        is DesignNodeKind.Annotation -> put("annotation", writeAnnotation(kind.annotation))
    }
}

private fun JsonObjectBuilder.putTextKindFields(kind: DesignNodeKind.Text) {
    if (kind.characters != "".bindable()) put("characters", stringJson(kind.characters))
    kind.content?.let { put("content", writeTextContent(it)) }
    kind.format?.let { put("format", writeTextFormat(it)) }
    if (kind.textStyleId.isNotEmpty()) put("textStyleId", kind.textStyleId)
    kind.textStyle?.let { put("textStyle", writeTextStyle(it)) }
    if (kind.autoResize != TextAutoResize.None) {
        put(
            "autoResize",
            when (kind.autoResize) {
                TextAutoResize.None -> "none"
                TextAutoResize.Height -> "height"
                TextAutoResize.WidthAndHeight -> "widthAndHeight"
            },
        )
    }
    kind.truncate?.let { truncate ->
        put(
            "truncate",
            buildJsonObject {
                put("maxLines", truncate.maxLines)
                if (!truncate.ellipsis) put("ellipsis", false)
            },
        )
    }
    if (kind.styleRanges.isNotEmpty()) put("styleRanges", JsonArray(kind.styleRanges.map(::writeStyleRange)))
    if (kind.links.isNotEmpty()) {
        put(
            "links",
            JsonArray(
                kind.links.map { link ->
                    buildJsonObject {
                        put("start", link.start)
                        put("end", link.end)
                        put("url", link.url)
                        if (link.nodeTarget.isNotEmpty()) put("nodeTarget", link.nodeTarget)
                    }
                },
            ),
        )
    }
    if (kind.list != TextListSettings()) {
        put(
            "list",
            buildJsonObject {
                if (kind.list.type != TextListType.None) {
                    put(
                        "type",
                        when (kind.list.type) {
                            TextListType.None -> "none"
                            TextListType.Bullet -> "bullet"
                            TextListType.Ordered -> "ordered"
                        },
                    )
                }
                if (kind.list.indent != 0) put("indent", kind.list.indent)
            },
        )
    }
}

private fun JsonObjectBuilder.putShapeKindFields(kind: DesignNodeKind.Shape) {
    kind.pointCount?.let { put("pointCount", it) }
    kind.innerRadius?.let { put("innerRadius", it) }
    if (kind.paths.isNotEmpty()) {
        put(
            "paths",
            JsonArray(
                kind.paths.map { path ->
                    buildJsonObject {
                        if (path.windingRule != "nonzero") put("windingRule", path.windingRule)
                        put("d", path.d)
                    }
                },
            ),
        )
    }
    if (kind.iconRef.isNotEmpty()) put("iconRef", kind.iconRef)
    if (kind.pathRef.isNotEmpty()) put("pathRef", kind.pathRef)
    kind.viewBox?.let { viewBox ->
        put(
            "viewBox",
            buildJsonObject {
                if (viewBox.x != 0.0) put("x", viewBox.x)
                if (viewBox.y != 0.0) put("y", viewBox.y)
                if (viewBox.width != 0.0) put("width", viewBox.width)
                if (viewBox.height != 0.0) put("height", viewBox.height)
            },
        )
    }
    kind.network?.takeIf { it.isNotEmpty() }?.let { put("network", writeVectorNetwork(it)) }
}

private fun writeVectorNetwork(network: VectorNetwork): JsonObject =
    buildJsonObject {
        put(
            "vertices",
            JsonArray(
                network.vertices.map { vertex ->
                    buildJsonObject {
                        put("x", vertex.x)
                        put("y", vertex.y)
                        vertex.inHandle?.let { put("in", writeHandleOffset(it)) }
                        vertex.outHandle?.let { put("out", writeHandleOffset(it)) }
                        if (vertex.mirror != HandleMirror.None) put("mirror", handleMirrorName(vertex.mirror))
                        if (vertex.corner) put("corner", true)
                    }
                },
            ),
        )
        put(
            "segments",
            JsonArray(
                network.segments.map { segment ->
                    buildJsonObject {
                        put("from", segment.from)
                        put("to", segment.to)
                    }
                },
            ),
        )
        if (network.regions.isNotEmpty()) {
            put(
                "regions",
                JsonArray(
                    network.regions.map { region ->
                        buildJsonObject {
                            if (region.windingRule != "nonzero") put("windingRule", region.windingRule)
                            put("loops", JsonArray(region.loops.map { loop -> JsonArray(loop.map { JsonPrimitive(it) }) }))
                        }
                    },
                ),
            )
        }
    }

private fun writeHandleOffset(handle: HandleOffset): JsonObject =
    buildJsonObject {
        put("dx", handle.dx)
        put("dy", handle.dy)
    }

private fun handleMirrorName(mirror: HandleMirror): String =
    when (mirror) {
        HandleMirror.None -> "none"
        HandleMirror.Angle -> "angle"
        HandleMirror.AngleAndLength -> "angleAndLength"
    }

private fun JsonObjectBuilder.putInstanceKindFields(kind: DesignNodeKind.Instance) {
    put("componentId", stringJson(kind.componentId))
    if (kind.libraryRef.isNotEmpty()) put("libraryRef", kind.libraryRef)
    if (kind.variant.isNotEmpty()) put("variant", stringMapJson(kind.variant))
    if (kind.props.isNotEmpty()) {
        put(
            "props",
            buildJsonObject {
                kind.props.forEach { (name, value) -> put(name, propValueJson(value)) }
            },
        )
    }
    if (kind.overrides.isNotEmpty()) put("overrides", JsonArray(kind.overrides.map(::writeOverride)))
    if (kind.detach) put("detach", true)
    if (kind.resetOverrides) put("resetOverrides", true)
}

private fun writeMedia(media: DesignMedia): JsonObject = buildJsonObject {
    put("assetId", stringJson(media.assetId))
    if (media.kind != MediaKind.Image) put("kind", "video")
    if (media.fillMode != ImageScaleMode.Fill) put("fillMode", imageScaleModeName(media.fillMode))
    media.focalPoint?.let { put("focalPoint", writePoint(it)) }
    media.alt?.let { put("alt", writeTextContent(it)) }
    if (media.replaceable) put("replaceable", true)
    if (media.opacity != 1.0.bindable()) put("opacity", doubleJson(media.opacity))
    if (media.blendMode != "normal") put("blendMode", media.blendMode)
    if (media.posterAssetId != "".bindable()) put("posterAssetId", stringJson(media.posterAssetId))
    if (media.autoplay) put("autoplay", true)
    if (media.loop) put("loop", true)
    if (!media.muted) put("muted", false)
}

private fun writeTable(table: DesignTable): JsonObject = buildJsonObject {
    if (table.columns.isNotEmpty()) put("columns", JsonArray(table.columns.map(::writeGridTrack)))
    if (table.headerRows != 1) put("headerRows", table.headerRows)
    if (table.rowGap.differsFrom(0.0)) put("rowGap", doubleJson(table.rowGap))
    if (table.columnGap.differsFrom(0.0)) put("columnGap", doubleJson(table.columnGap))
}

private fun writeAnnotation(annotation: DesignAnnotation): JsonObject = buildJsonObject {
    // id/text are always emitted: the node-annotation reader falls back to the node id.
    put("id", annotation.id)
    if (annotation.target.isNotEmpty()) put("target", annotation.target)
    put("text", annotation.text)
    if (annotation.audience.isNotEmpty()) put("audience", annotation.audience)
}

private fun writeMask(mask: DesignMask): JsonObject = buildJsonObject {
    if (mask.type != MaskType.Alpha) {
        put(
            "type",
            when (mask.type) {
                MaskType.Alpha -> "alpha"
                MaskType.Vector -> "vector"
                MaskType.Luminance -> "luminance"
            },
        )
    }
    if (mask.appliesTo.isNotEmpty()) put("appliesTo", stringArrayJson(mask.appliesTo))
    if (mask.source.isNotEmpty()) put("source", mask.source)
}

// --- Text content and styles --------------------------------------------------

private fun writeTextContent(content: TextContent): JsonObject = buildJsonObject {
    if (content.key.isNotEmpty()) put("key", content.key)
    if (content.defaultLocale.isNotEmpty()) put("defaultLocale", content.defaultLocale)
    if (content.defaultText.isNotEmpty()) put("defaultText", content.defaultText)
    if (content.params.isNotEmpty()) {
        put(
            "params",
            buildJsonObject {
                content.params.forEach { (name, value) -> put(name, stringJson(value)) }
            },
        )
    }
}

private fun writeTextFormat(format: TextFormat): JsonObject = buildJsonObject {
    put(
        "type",
        when (format.kind) {
            TextFormatKind.Date -> "date"
            TextFormatKind.Time -> "time"
            TextFormatKind.Number -> "number"
            TextFormatKind.Currency -> "currency"
            TextFormatKind.RelativeTime -> "relativeTime"
            TextFormatKind.Percent -> "percent"
        },
    )
    if (format.options.isNotEmpty()) put("options", stringMapJson(format.options))
}

private fun writeStyleRange(range: TextStyleRange): JsonObject = buildJsonObject {
    put("start", range.start)
    put("end", range.end)
    val style = buildJsonObject {
        putTextStyleFields(range.style)
        range.fills?.let { fills -> put("fills", JsonArray(fills.map(::writePaint))) }
    }
    if (style.isNotEmpty()) put("style", style)
    if (range.styleRef.isNotEmpty()) put("styleRef", range.styleRef)
}

private fun writeTextStyle(style: DesignTextStyle): JsonObject =
    buildJsonObject { putTextStyleFields(style) }

private fun JsonObjectBuilder.putTextStyleFields(style: DesignTextStyle) {
    style.fontFamily?.let { put("fontFamily", it) }
    style.fontWeight?.let { put("fontWeight", doubleJson(it)) }
    style.fontSize?.let { put("fontSize", doubleJson(it)) }
    style.lineHeight?.let { put("lineHeight", writeUnitValue(it)) }
    style.letterSpacing?.let { put("letterSpacing", writeUnitValue(it)) }
    style.paragraphSpacing?.let { put("paragraphSpacing", it) }
    style.textAlignHorizontal?.let {
        put(
            "textAlignHorizontal",
            when (it) {
                TextAlignHorizontal.Left -> "left"
                TextAlignHorizontal.Center -> "center"
                TextAlignHorizontal.Right -> "right"
                TextAlignHorizontal.Justified -> "justified"
            },
        )
    }
    style.textAlignVertical?.let {
        put(
            "textAlignVertical",
            when (it) {
                TextAlignVertical.Top -> "top"
                TextAlignVertical.Center -> "center"
                TextAlignVertical.Bottom -> "bottom"
            },
        )
    }
    style.textCase?.let {
        put(
            "textCase",
            when (it) {
                TextCase.None -> "none"
                TextCase.Upper -> "upper"
                TextCase.Lower -> "lower"
                TextCase.Title -> "title"
            },
        )
    }
    style.textDecoration?.let {
        put(
            "textDecoration",
            when (it) {
                TextDecorationKind.None -> "none"
                TextDecorationKind.Underline -> "underline"
                TextDecorationKind.Strikethrough -> "strikethrough"
            },
        )
    }
    if (style.fontFeatures.isNotEmpty()) {
        put(
            "fontFeatures",
            buildJsonObject {
                style.fontFeatures.forEach { (feature, enabled) -> put(feature, enabled) }
            },
        )
    }
    if (style.variableAxes.isNotEmpty()) {
        put(
            "variableAxes",
            buildJsonObject {
                style.variableAxes.forEach { (axis, value) -> put(axis, value) }
            },
        )
    }
}

private fun writeUnitValue(value: UnitValue): JsonObject = buildJsonObject {
    put("unit", if (value.unit == DesignUnit.Px) "px" else "percent")
    put("value", value.value)
}

// --- Layout ------------------------------------------------------------------

private fun writeAutoLayout(layout: DesignAutoLayout): JsonObject = buildJsonObject {
    if (layout.mode != LayoutMode.None) {
        put(
            "mode",
            when (layout.mode) {
                LayoutMode.None -> "none"
                LayoutMode.Horizontal -> "horizontal"
                LayoutMode.Vertical -> "vertical"
                LayoutMode.Grid -> "grid"
            },
        )
    }
    val gap = layout.gap
    when {
        layout.columnGap != null || layout.rowGap != null -> put(
            "gap",
            buildJsonObject {
                layout.columnGap?.let { put("column", doubleJson(it)) }
                layout.rowGap?.let { put("row", doubleJson(it)) }
            },
        )
        gap is DesignGap.Auto -> put("gap", "auto")
        gap is DesignGap.Fixed && gap.value.differsFrom(0.0) -> put("gap", doubleJson(gap.value))
    }
    layout.crossGap?.let { put("crossGap", doubleJson(it)) }
    if (layout.wrap) put("wrap", true)
    paddingJson(layout.padding)?.let { put("padding", it) }
    layout.paddingLogical?.let { put("paddingLogical", writeLogicalInsets(it)) }
    if (layout.alignItems != AlignItems.Start) put("alignItems", alignItemsName(layout.alignItems))
    if (layout.justifyContent != JustifyContent.Start) {
        put(
            "justifyContent",
            when (layout.justifyContent) {
                JustifyContent.Start -> "start"
                JustifyContent.Center -> "center"
                JustifyContent.End -> "end"
                JustifyContent.SpaceBetween -> "spaceBetween"
            },
        )
    }
    if (layout.baseline != BaselineAlign.First) put("baseline", "last")
    if (layout.clipsContent) put("clipsContent", true)
    if (layout.columns.isNotEmpty()) put("columns", JsonArray(layout.columns.map(::writeGridTrack)))
    if (layout.rows.isNotEmpty()) {
        put("rows", JsonArray(layout.rows.map(::writeGridTrack)))
    } else if (layout.implicitRows != null || layout.implicitRowMin != null) {
        put(
            "rows",
            buildJsonObject {
                layout.implicitRows?.let { implicit ->
                    put("auto", if (implicit == GridTrack.Hug) JsonPrimitive(true) else writeGridTrack(implicit))
                }
                layout.implicitRowMin?.let { put("min", doubleJson(it)) }
            },
        )
    }
}

private fun paddingJson(padding: DesignInsets): JsonElement? {
    if (padding == DesignInsets()) return null
    val top = padding.top
    if (top is Bindable.Value && padding.right == top && padding.bottom == top && padding.left == top) {
        return JsonPrimitive(top.value)
    }
    return writeInsets(padding)
}

private fun writeInsets(insets: DesignInsets): JsonObject = buildJsonObject {
    if (insets.top.differsFrom(0.0)) put("top", doubleJson(insets.top))
    if (insets.right.differsFrom(0.0)) put("right", doubleJson(insets.right))
    if (insets.bottom.differsFrom(0.0)) put("bottom", doubleJson(insets.bottom))
    if (insets.left.differsFrom(0.0)) put("left", doubleJson(insets.left))
}

private fun writeLogicalInsets(insets: DesignLogicalInsets): JsonObject = buildJsonObject {
    insets.blockStart?.let { put("blockStart", doubleJson(it)) }
    insets.inlineEnd?.let { put("inlineEnd", doubleJson(it)) }
    insets.blockEnd?.let { put("blockEnd", doubleJson(it)) }
    insets.inlineStart?.let { put("inlineStart", doubleJson(it)) }
}

private fun writeAnchors(anchors: DesignAnchors): JsonObject = buildJsonObject {
    anchors.inlineStart?.let { put("inlineStart", doubleJson(it)) }
    anchors.inlineEnd?.let { put("inlineEnd", doubleJson(it)) }
    anchors.blockStart?.let { put("blockStart", doubleJson(it)) }
    anchors.blockEnd?.let { put("blockEnd", doubleJson(it)) }
}

private fun writeGridTrack(track: GridTrack): JsonObject = buildJsonObject {
    when (track) {
        is GridTrack.Fixed -> {
            put("type", "fixed")
            put("value", doubleJson(track.value))
        }
        is GridTrack.Flex -> {
            put("type", "flex")
            put("value", doubleJson(track.value))
        }
        GridTrack.Hug -> put("type", "hug")
    }
}

private fun writeGridPlacement(placement: GridPlacement): JsonObject = buildJsonObject {
    if (placement.column != 0) put("column", placement.column)
    if (placement.row != 0) put("row", placement.row)
    if (placement.columnSpan != 1) put("columnSpan", placement.columnSpan)
    if (placement.rowSpan != 1) put("rowSpan", placement.rowSpan)
}

private fun writeLayoutChild(child: DesignLayoutChild): JsonObject = buildJsonObject {
    child.alignSelf?.let { put("alignSelf", alignItemsName(it)) }
    if (child.absolute) put("absolute", true)
}

private fun writeScroll(scroll: DesignScroll): JsonObject = buildJsonObject {
    if (scroll.overflow != ScrollOverflow.None) {
        put(
            "overflow",
            when (scroll.overflow) {
                ScrollOverflow.None -> "none"
                ScrollOverflow.Horizontal -> "horizontal"
                ScrollOverflow.Vertical -> "vertical"
                ScrollOverflow.Both -> "both"
            },
        )
    }
    if (scroll.sticky) put("sticky", true)
    if (scroll.overflowX != OverflowMode.Visible) put("overflowX", overflowModeName(scroll.overflowX))
    if (scroll.overflowY != OverflowMode.Visible) put("overflowY", overflowModeName(scroll.overflowY))
    if (scroll.fixedChildren.isNotEmpty()) put("fixedChildren", stringArrayJson(scroll.fixedChildren))
}

private fun writeConstraints(constraints: DesignConstraints): JsonObject = buildJsonObject {
    if (constraints.horizontal != HorizontalConstraint.Left) {
        put(
            "horizontal",
            when (constraints.horizontal) {
                HorizontalConstraint.Left -> "left"
                HorizontalConstraint.Right -> "right"
                HorizontalConstraint.Center -> "center"
                HorizontalConstraint.LeftRight -> "leftRight"
                HorizontalConstraint.Scale -> "scale"
            },
        )
    }
    if (constraints.vertical != VerticalConstraint.Top) {
        put(
            "vertical",
            when (constraints.vertical) {
                VerticalConstraint.Top -> "top"
                VerticalConstraint.Bottom -> "bottom"
                VerticalConstraint.Center -> "center"
                VerticalConstraint.TopBottom -> "topBottom"
                VerticalConstraint.Scale -> "scale"
            },
        )
    }
}

private fun writeSize(size: DesignSize): JsonObject = buildJsonObject {
    size.width?.let { put("width", it) }
    size.height?.let { put("height", it) }
}

private fun writeSizing(sizing: DesignSizing): JsonObject = buildJsonObject {
    if (sizing.horizontal != SizingMode.Fixed) put("horizontal", sizingModeName(sizing.horizontal))
    if (sizing.vertical != SizingMode.Fixed) put("vertical", sizingModeName(sizing.vertical))
}

// --- Paints, strokes, effects, corners ----------------------------------------

private fun writePaint(paint: DesignPaint): JsonObject = buildJsonObject {
    when (paint) {
        is DesignPaint.Solid -> {
            put("type", "solid")
            put("color", colorJson(paint.color))
        }
        is DesignPaint.Gradient -> {
            put(
                "type",
                when (paint.gradientType) {
                    GradientKind.Linear -> "gradientLinear"
                    GradientKind.Radial -> "gradientRadial"
                    GradientKind.Angular -> "gradientAngular"
                    GradientKind.Diamond -> "gradientDiamond"
                },
            )
            if (paint.from != DesignPoint(0.0, 0.0)) put("from", writePoint(paint.from))
            if (paint.to != DesignPoint(0.0, 1.0)) put("to", writePoint(paint.to))
            if (paint.stops.isNotEmpty()) {
                put(
                    "stops",
                    JsonArray(
                        paint.stops.map { stop ->
                            buildJsonObject {
                                put("position", stop.position)
                                put("color", colorJson(stop.color))
                            }
                        },
                    ),
                )
            }
        }
        is DesignPaint.Image -> {
            put("type", "image")
            put("assetId", paint.assetId)
            if (paint.scaleMode != ImageScaleMode.Fill) put("scaleMode", imageScaleModeName(paint.scaleMode))
            paint.focalPoint?.let { put("focalPoint", writePoint(it)) }
            if (paint.replaceable) put("replaceable", true)
        }
        is DesignPaint.Video -> {
            put("type", "video")
            put("assetId", paint.assetId)
            if (paint.scaleMode != ImageScaleMode.Fill) put("scaleMode", imageScaleModeName(paint.scaleMode))
            paint.focalPoint?.let { put("focalPoint", writePoint(it)) }
            if (paint.posterAssetId.isNotEmpty()) put("posterAssetId", paint.posterAssetId)
            if (paint.autoplay) put("autoplay", true)
            if (paint.loop) put("loop", true)
            if (!paint.muted) put("muted", false)
        }
        is DesignPaint.Unknown -> put("type", paint.rawType)
    }
    if (paint.visible != true.bindable()) put("visible", booleanJson(paint.visible))
    if (paint.opacity != 1.0.bindable()) put("opacity", doubleJson(paint.opacity))
    if (paint.blendMode != "normal") put("blendMode", paint.blendMode)
}

private fun writeStrokes(strokes: DesignStrokes): JsonObject = buildJsonObject {
    if (strokes.paints.isNotEmpty()) put("paints", JsonArray(strokes.paints.map(::writePaint)))
    if (strokes.weight != 1.0.bindable()) put("weight", doubleJson(strokes.weight))
    strokes.weightPerSide?.let { put("weightPerSide", writeInsets(it)) }
    if (strokes.align != StrokeAlign.Inside) {
        put("align", if (strokes.align == StrokeAlign.Center) "center" else "outside")
    }
    if (strokes.dashPattern.isNotEmpty()) {
        put("dashPattern", JsonArray(strokes.dashPattern.map(::JsonPrimitive)))
    }
    if (strokes.cap != "butt") put("cap", strokes.cap)
    if (strokes.join != "miter") put("join", strokes.join)
}

private fun writeEffect(effect: DesignEffect): JsonObject = buildJsonObject {
    when (effect) {
        is DesignEffect.DropShadow -> {
            put("type", "dropShadow")
            put("color", colorJson(effect.color))
            if (effect.offset != DesignPoint()) put("offset", writePoint(effect.offset))
            if (effect.blur.differsFrom(0.0)) put("blur", doubleJson(effect.blur))
            if (effect.spread.differsFrom(0.0)) put("spread", doubleJson(effect.spread))
        }
        is DesignEffect.InnerShadow -> {
            put("type", "innerShadow")
            put("color", colorJson(effect.color))
            if (effect.offset != DesignPoint()) put("offset", writePoint(effect.offset))
            if (effect.blur.differsFrom(0.0)) put("blur", doubleJson(effect.blur))
            if (effect.spread.differsFrom(0.0)) put("spread", doubleJson(effect.spread))
        }
        is DesignEffect.LayerBlur -> {
            put("type", "layerBlur")
            put("radius", doubleJson(effect.radius))
        }
        is DesignEffect.BackgroundBlur -> {
            put("type", "backgroundBlur")
            put("radius", doubleJson(effect.radius))
        }
        is DesignEffect.Unknown -> put("type", effect.rawType)
    }
    if (effect.visible != true.bindable()) put("visible", booleanJson(effect.visible))
}

/** Uniform corners collapse to the shorthand (a number or one binding object). */
private fun cornerRadiusJson(radius: DesignCornerRadius): JsonElement {
    val topLeft = radius.topLeft
    val uniform = radius.topRight == topLeft &&
        radius.bottomRight == topLeft &&
        radius.bottomLeft == topLeft
    if (uniform) return doubleJson(topLeft)
    return buildJsonObject {
        if (radius.topLeft.differsFrom(0.0)) put("topLeft", doubleJson(radius.topLeft))
        if (radius.topRight.differsFrom(0.0)) put("topRight", doubleJson(radius.topRight))
        if (radius.bottomRight.differsFrom(0.0)) put("bottomRight", doubleJson(radius.bottomRight))
        if (radius.bottomLeft.differsFrom(0.0)) put("bottomLeft", doubleJson(radius.bottomLeft))
    }
}

private fun writeLayoutGrid(grid: LayoutGridDefinition): JsonObject = buildJsonObject {
    put(
        "type",
        when (grid.type) {
            LayoutGridType.Columns -> "columns"
            LayoutGridType.Rows -> "rows"
            LayoutGridType.Grid -> "grid"
        },
    )
    grid.count?.let { put("count", intJson(it)) }
    grid.size?.let { put("size", doubleJson(it)) }
    grid.gutter?.let { put("gutter", doubleJson(it)) }
    grid.margin?.let { put("margin", doubleJson(it)) }
    if (grid.alignment != LayoutGridAlignment.Stretch) {
        put(
            "alignment",
            when (grid.alignment) {
                LayoutGridAlignment.Stretch -> "stretch"
                LayoutGridAlignment.Start -> "start"
                LayoutGridAlignment.Center -> "center"
                LayoutGridAlignment.End -> "end"
            },
        )
    }
    grid.color?.let { put("color", it.toHexString()) }
    if (!grid.visible) put("visible", false)
}

private fun writeGuide(guide: GuideLine): JsonObject = buildJsonObject {
    put("orientation", if (guide.orientation == GuideOrientation.Horizontal) "horizontal" else "vertical")
    put("position", guide.position)
}

// --- Data directives -----------------------------------------------------------

private fun writeRepeat(repeat: DesignRepeat): JsonObject = buildJsonObject {
    put("item", repeat.itemName)
    put("in", repeat.collection.raw)
    repeat.indexName?.let { put("index", it) }
    repeat.key?.let { put("key", it.raw) }
}

// --- Interactions and motion -----------------------------------------------------

private fun writeInteraction(interaction: DesignInteraction): JsonObject = buildJsonObject {
    put(
        "trigger",
        when (interaction.trigger) {
            InteractionTrigger.OnClick -> "onClick"
            InteractionTrigger.OnHover -> "onHover"
            InteractionTrigger.OnPress -> "onPress"
            InteractionTrigger.OnDrag -> "onDrag"
            InteractionTrigger.OnKey -> "onKey"
            InteractionTrigger.AfterDelay -> "afterDelay"
            InteractionTrigger.WhileHovering -> "whileHovering"
            InteractionTrigger.WhilePressed -> "whilePressed"
            InteractionTrigger.OnVariableChange -> "onVariableChange"
        },
    )
    if (interaction.key.isNotEmpty()) put("key", interaction.key)
    interaction.delayMs?.let { put("delayMs", it) }
    if (interaction.variable.isNotEmpty()) put("variable", interaction.variable)
    put("actions", JsonArray(interaction.actions.map(::writeAction)))
    interaction.sourceMap?.let { put("sourceMap", writeSourceLocation(it)) }
}

private fun writeAction(action: DesignAction): JsonObject = buildJsonObject {
    when (action) {
        is DesignAction.Navigate -> {
            put("type", "navigate")
            put("to", action.to)
            action.transition?.let { put("transition", writeTransition(it)) }
        }
        is DesignAction.OpenOverlay -> {
            put("type", "openOverlay")
            put("destination", action.destination)
            if (action.overlay != OverlaySettings()) put("overlay", writeOverlaySettings(action.overlay))
            action.transition?.let { put("transition", writeTransition(it)) }
        }
        is DesignAction.SwapOverlay -> {
            put("type", "swapOverlay")
            put("destination", action.destination)
            action.transition?.let { put("transition", writeTransition(it)) }
        }
        is DesignAction.CloseOverlay -> {
            put("type", "closeOverlay")
            action.transition?.let { put("transition", writeTransition(it)) }
        }
        DesignAction.Back -> put("type", "back")
        is DesignAction.OpenLink -> {
            put("type", "openLink")
            put("url", action.url)
        }
        is DesignAction.SetVariable -> {
            put("type", "setVariable")
            put("variable", action.variable)
            put("value", stringJson(action.value))
        }
        is DesignAction.ChangeToVariant -> {
            put("type", "changeToVariant")
            put("target", action.target)
            if (action.variant.isNotEmpty()) put("variant", stringMapJson(action.variant))
        }
        is DesignAction.ScrollTo -> {
            put("type", "scrollTo")
            put("target", action.target)
            if (!action.animated) put("animated", false)
        }
        is DesignAction.RunActionSet -> {
            put("type", "runActionSet")
            put("actionSetId", action.actionSetId)
        }
        is DesignAction.Unknown -> put("type", action.rawType)
    }
}

private fun writeTransition(transition: DesignTransition): JsonObject = buildJsonObject {
    if (transition.type != TransitionType.Instant) {
        put(
            "type",
            when (transition.type) {
                TransitionType.Instant -> "instant"
                TransitionType.Dissolve -> "dissolve"
                TransitionType.SmartAnimate -> "smartAnimate"
                TransitionType.MoveIn -> "moveIn"
                TransitionType.MoveOut -> "moveOut"
                TransitionType.Push -> "push"
                TransitionType.SlideIn -> "slideIn"
                TransitionType.SlideOut -> "slideOut"
            },
        )
    }
    if (transition.direction != TransitionDirection.Left) {
        put(
            "direction",
            when (transition.direction) {
                TransitionDirection.Left -> "left"
                TransitionDirection.Right -> "right"
                TransitionDirection.Top -> "top"
                TransitionDirection.Bottom -> "bottom"
            },
        )
    }
    if (transition.easing != DesignEasing.Named(EasingKind.EaseOut)) put("easing", easingJson(transition.easing))
    if (transition.durationMs != 300.0) put("durationMs", transition.durationMs)
}

private fun easingJson(easing: DesignEasing): JsonElement = when (easing) {
    is DesignEasing.Named -> JsonPrimitive(
        when (easing.kind) {
            EasingKind.Linear -> "linear"
            EasingKind.EaseIn -> "easeIn"
            EasingKind.EaseOut -> "easeOut"
            EasingKind.EaseInOut -> "easeInOut"
            EasingKind.EaseInBack -> "easeInBack"
            EasingKind.EaseOutBack -> "easeOutBack"
        },
    )
    is DesignEasing.CubicBezier -> buildJsonObject {
        put("type", "cubicBezier")
        put("x1", easing.x1)
        put("y1", easing.y1)
        put("x2", easing.x2)
        put("y2", easing.y2)
    }
    is DesignEasing.Spring -> buildJsonObject {
        put("type", "spring")
        if (easing.mass != 1.0) put("mass", easing.mass)
        if (easing.stiffness != 100.0) put("stiffness", easing.stiffness)
        if (easing.damping != 15.0) put("damping", easing.damping)
    }
}

private fun writeOverlaySettings(overlay: OverlaySettings): JsonObject = buildJsonObject {
    if (overlay.position != OverlayPosition.Center) {
        put(
            "position",
            when (overlay.position) {
                OverlayPosition.Center -> "center"
                OverlayPosition.TopLeft -> "topLeft"
                OverlayPosition.TopCenter -> "topCenter"
                OverlayPosition.TopRight -> "topRight"
                OverlayPosition.BottomLeft -> "bottomLeft"
                OverlayPosition.BottomCenter -> "bottomCenter"
                OverlayPosition.BottomRight -> "bottomRight"
                OverlayPosition.Manual -> "manual"
            },
        )
    }
    overlay.offset?.let { put("offset", writePoint(it)) }
    if (!overlay.closeOnOutsideClick) put("closeOnOutsideClick", false)
    overlay.background?.let { put("background", colorJson(it)) }
}

private fun writeMotion(motion: DesignMotion): JsonObject = buildJsonObject {
    if (motion.ref.isNotEmpty()) put("ref", motion.ref)
    motion.fallback?.let { fallback ->
        put(
            "fallback",
            buildJsonObject {
                put("durationMs", fallback.durationMs)
                if (fallback.loop) put("loop", true)
                put(
                    "frames",
                    JsonArray(
                        fallback.frames.map { frame ->
                            buildJsonObject {
                                put("at", frame.at)
                                put(
                                    "properties",
                                    buildJsonObject {
                                        frame.properties.forEach { (property, value) -> put(property, value) }
                                    },
                                )
                            }
                        },
                    ),
                )
            },
        )
    }
}

// --- Responsive -----------------------------------------------------------------

private fun writeResponsiveVariant(variant: ResponsiveVariant): JsonObject = buildJsonObject {
    put(
        "when",
        buildJsonObject {
            variant.selectors.forEach { (dimension, value) -> put(dimensionName(dimension), value) }
        },
    )
    val patch = writeNodePatch(variant.patch)
    if (patch.isNotEmpty()) put("set", patch)
    variant.sourceMap?.let { put("sourceMap", writeSourceLocation(it)) }
}

private fun dimensionName(dimension: ResponsiveDimension): String = when (dimension) {
    ResponsiveDimension.Breakpoint -> "breakpoint"
    ResponsiveDimension.DevicePreset -> "devicePreset"
    ResponsiveDimension.Platform -> "platform"
    ResponsiveDimension.Theme -> "theme"
    ResponsiveDimension.Density -> "density"
    ResponsiveDimension.Locale -> "locale"
    ResponsiveDimension.Direction -> "direction"
    ResponsiveDimension.Brand -> "brand"
    ResponsiveDimension.State -> "state"
}

private fun writeNodePatch(patch: DesignNodePatch): JsonObject = buildJsonObject {
    patch.visible?.let { put("visible", booleanJson(it)) }
    patch.opacity?.let { put("opacity", doubleJson(it)) }
    patch.layout?.let { put("layout", writeAutoLayout(it)) }
    patch.layoutChild?.let { put("layoutChild", writeLayoutChild(it)) }
    patch.gridPlacement?.let { put("gridPlacement", writeGridPlacement(it)) }
    patch.sizing?.let { put("sizing", writeSizing(it)) }
    patch.size?.let { put("size", writeSize(it)) }
    patch.minSize?.let { put("minSize", writeSize(it)) }
    patch.maxSize?.let { put("maxSize", writeSize(it)) }
    patch.fills?.let { fills -> put("fills", JsonArray(fills.map(::writePaint))) }
    patch.strokes?.let { put("strokes", writeStrokes(it)) }
    patch.effects?.let { effects -> put("effects", JsonArray(effects.map(::writeEffect))) }
    patch.cornerRadius?.let { put("cornerRadius", cornerRadiusJson(it)) }
    patch.textStyle?.let { put("textStyle", writeTextStyle(it)) }
    patch.scroll?.let { put("scroll", writeScroll(it)) }
}

// --- Components, styles, variables ------------------------------------------------

private fun writeComponent(component: DesignComponent): JsonObject = buildJsonObject {
    if (component.name.isNotEmpty()) put("name", component.name)
    if (component.libraryId.isNotEmpty()) put("libraryId", component.libraryId)
    if (component.properties.isNotEmpty()) {
        put(
            "properties",
            buildJsonObject {
                component.properties.forEach { (name, definition) ->
                    put(name, writePropertyDefinition(definition))
                }
            },
        )
    }
    if (component.exposedInstances.isNotEmpty()) {
        put("exposedInstances", JsonArray(component.exposedInstances.map(::stringArrayJson)))
    }
    put("root", writeDesignNode(component.root))
}

private fun writePropertyDefinition(definition: ComponentPropertyDefinition): JsonObject = buildJsonObject {
    put(
        "type",
        when (definition.type) {
            ComponentPropertyType.Text -> "text"
            ComponentPropertyType.Boolean -> "boolean"
            ComponentPropertyType.InstanceSwap -> "instanceSwap"
            ComponentPropertyType.Variant -> "variant"
            ComponentPropertyType.Slot -> "slot"
            ComponentPropertyType.Number -> "number"
            ComponentPropertyType.RawString -> "rawString"
            ComponentPropertyType.DataBinding -> "dataBinding"
        },
    )
    definition.default?.let { put("default", propValueJson(it)) }
    if (definition.preferredValues.isNotEmpty()) {
        put("preferredValues", stringArrayJson(definition.preferredValues))
    }
    definition.minItems?.let { put("minItems", it) }
    definition.maxItems?.let { put("maxItems", it) }
    if (definition.allowedContent.isNotEmpty()) put("allowedContent", stringArrayJson(definition.allowedContent))
}

private fun writeComponentSet(set: DesignComponentSet): JsonObject = buildJsonObject {
    if (set.name.isNotEmpty()) put("name", set.name)
    if (set.axes.isNotEmpty()) {
        put(
            "axes",
            buildJsonObject {
                set.axes.forEach { (axis, values) -> put(axis, stringArrayJson(values)) }
            },
        )
    }
    if (set.variants.isNotEmpty()) put("variants", stringMapJson(set.variants))
}

private fun propValueJson(value: PropValue): JsonElement = when (value) {
    is PropValue.Text -> JsonPrimitive(value.value)
    is PropValue.Bool -> JsonPrimitive(value.value)
    is PropValue.Number -> JsonPrimitive(value.value)
    // A reference is a plain string in JSON; it re-parses as PropValue.Text.
    is PropValue.Reference -> JsonPrimitive(value.value)
    is PropValue.Content -> buildJsonObject { put("content", writeTextContent(value.content)) }
    is PropValue.Data -> buildJsonObject { put("\$data", value.expression.raw) }
    is PropValue.SlotContent -> JsonArray(value.nodes.map(::writeDesignNode))
}

private fun writeOverride(override: InstanceOverride): JsonObject = buildJsonObject {
    put("target", stringArrayJson(override.target))
    val set = buildJsonObject {
        override.fills?.let { fills -> put("fills", JsonArray(fills.map(::writePaint))) }
        override.strokes?.let { put("strokes", writeStrokes(it)) }
        override.opacity?.let { put("opacity", doubleJson(it)) }
        override.visible?.let { put("visible", booleanJson(it)) }
        override.characters?.let { put("characters", stringJson(it)) }
        override.textStyle?.let { put("textStyle", writeTextStyle(it)) }
        // Note: override corner smoothing is not representable in JSON (the reader forces 0.0).
        override.cornerRadius?.let { put("cornerRadius", cornerRadiusJson(it)) }
        override.variant?.let { put("variant", stringMapJson(it)) }
        override.props?.let { props ->
            put(
                "props",
                buildJsonObject {
                    props.forEach { (name, propValue) -> put(name, propValueJson(propValue)) }
                },
            )
        }
        override.slotContent?.let { nodes -> put("slotContent", JsonArray(nodes.map(::writeDesignNode))) }
    }
    if (set.isNotEmpty()) put("set", set)
}

private fun writeStyle(style: DesignStyle): JsonObject = when (style) {
    is DesignStyle.Text -> buildJsonObject {
        put("type", "text")
        put("value", writeTextStyle(style.value))
    }
    is DesignStyle.Paint -> buildJsonObject {
        put("type", "paint")
        put("value", JsonArray(style.value.map(::writePaint)))
    }
    is DesignStyle.Effect -> buildJsonObject {
        put("type", "effect")
        put("value", JsonArray(style.value.map(::writeEffect)))
    }
    is DesignStyle.Grid -> buildJsonObject {
        put("type", "grid")
        put("value", JsonArray(style.value.map(::writeLayoutGrid)))
    }
}

private fun writeVariables(variables: DesignVariables): JsonObject = buildJsonObject {
    put(
        "collections",
        buildJsonObject {
            variables.collections.forEach { (id, collection) -> put(id, writeVariableCollection(collection)) }
        },
    )
}

private fun writeVariableCollection(collection: VariableCollection): JsonObject = buildJsonObject {
    if (collection.name.isNotEmpty()) put("name", collection.name)
    if (collection.modes.isNotEmpty()) put("modes", stringArrayJson(collection.modes))
    if (collection.defaultMode != collection.modes.firstOrNull().orEmpty()) {
        put("defaultMode", collection.defaultMode)
    }
    if (collection.vars.isNotEmpty()) {
        put(
            "vars",
            buildJsonObject {
                collection.vars.forEach { (id, variable) -> put(id, writeVariable(variable)) }
            },
        )
    }
}

private fun writeVariable(variable: DesignVariable): JsonObject = buildJsonObject {
    put("type", variableTypeName(variable.type))
    if (variable.values.isNotEmpty()) {
        put(
            "values",
            buildJsonObject {
                variable.values.forEach { (mode, value) -> put(mode, variableValueJson(value)) }
            },
        )
    }
}

private fun variableTypeName(type: VariableType): String = when (type) {
    VariableType.Color -> "color"
    VariableType.Number -> "number"
    VariableType.Text -> "string"
    VariableType.Bool -> "boolean"
}

private fun variableValueJson(value: VariableValue): JsonElement = when (value) {
    is VariableValue.Alias -> buildJsonObject { put("\$var", value.varId) }
    is VariableValue.ColorValue -> JsonPrimitive(value.value.toHexString())
    is VariableValue.NumberValue -> JsonPrimitive(value.value)
    is VariableValue.TextValue -> JsonPrimitive(value.value)
    is VariableValue.BoolValue -> JsonPrimitive(value.value)
}

private fun writeAsset(asset: DesignAsset): JsonObject = buildJsonObject {
    if (asset.type != "image") put("type", asset.type)
    if (asset.hash.isNotEmpty()) put("hash", asset.hash)
    if (asset.url.isNotEmpty()) put("url", asset.url)
    asset.width?.let { put("width", it) }
    asset.height?.let { put("height", it) }
}

// --- Document meta -----------------------------------------------------------------

private fun writePage(page: DesignPage): JsonObject = buildJsonObject {
    if (page.id.isNotEmpty()) put("id", page.id)
    if (page.name.isNotEmpty()) put("name", page.name)
    if (page.children.isNotEmpty()) put("children", JsonArray(page.children.map(::writeDesignNode)))
}

private fun writeScreenMeta(screen: DesignScreenMeta): JsonObject = buildJsonObject {
    if (screen.id.isNotEmpty()) put("id", screen.id)
    if (screen.name.isNotEmpty()) put("name", screen.name)
    if (screen.page.isNotEmpty()) put("page", screen.page)
    if (screen.modes.isNotEmpty()) put("modes", stringMapJson(screen.modes))
    screen.frame?.let { frame ->
        put(
            "frame",
            buildJsonObject {
                if (frame.preset.isNotEmpty()) put("preset", frame.preset)
                frame.width?.let { put("width", it) }
                frame.height?.let { put("height", it) }
            },
        )
    }
    screen.canvas?.let { canvas ->
        put(
            "canvas",
            buildJsonObject {
                if (canvas.section.isNotEmpty()) put("section", canvas.section)
                if (canvas.x != 0.0) put("x", canvas.x)
                if (canvas.y != 0.0) put("y", canvas.y)
            },
        )
    }
    screen.flow?.let { flow ->
        put(
            "flow",
            buildJsonObject {
                if (flow.id.isNotEmpty()) put("id", flow.id)
                if (flow.node.isNotEmpty()) put("node", flow.node)
                if (flow.next.isNotEmpty()) put("next", stringArrayJson(flow.next))
            },
        )
    }
}

private fun writeLibrary(library: DesignLibrary): JsonObject = buildJsonObject {
    put("id", library.id)
    if (library.source.isNotEmpty()) put("source", library.source)
}

private fun writeBreakpoint(breakpoint: DesignBreakpoint): JsonObject = buildJsonObject {
    put("id", breakpoint.id)
    breakpoint.minWidth?.let { put("minWidth", it) }
    breakpoint.maxWidth?.let { put("maxWidth", it) }
}

private fun writeDevicePreset(preset: DevicePreset): JsonObject = buildJsonObject {
    put("id", preset.id)
    put("width", preset.width)
    put("height", preset.height)
    if (preset.platform.isNotEmpty()) put("platform", preset.platform)
}

private fun writeI18n(i18n: DesignI18n): JsonObject = buildJsonObject {
    if (i18n.sourceLocale.isNotEmpty()) put("sourceLocale", i18n.sourceLocale)
    if (i18n.targetLocales.isNotEmpty()) put("targetLocales", stringArrayJson(i18n.targetLocales))
    if (i18n.resources.isNotEmpty()) {
        put(
            "resources",
            buildJsonObject {
                i18n.resources.forEach { (locale, bundle) -> put(locale, stringMapJson(bundle)) }
            },
        )
    }
}

private fun writeHandoff(handoff: DesignHandoff): JsonObject = buildJsonObject {
    if (handoff.annotations.isNotEmpty()) {
        put("annotations", JsonArray(handoff.annotations.map(::writeAnnotation)))
    }
    if (handoff.measurements.isNotEmpty()) {
        put("measurements", JsonArray(handoff.measurements.map(::writeMeasurement)))
    }
    handoff.code?.let { code ->
        put(
            "code",
            buildJsonObject {
                if (code.framework.isNotEmpty()) put("framework", code.framework)
                if (code.componentHint.isNotEmpty()) put("componentHint", code.componentHint)
            },
        )
    }
}

private fun writeMeasurement(measurement: DesignMeasurement): JsonObject = buildJsonObject {
    put("from", measurement.from)
    put("to", measurement.to)
    put("axis", if (measurement.axis == MeasureAxis.Inline) "inline" else "block")
    measurement.value?.let { put("value", doubleJson(it)) }
}

private fun writeExportSetting(setting: ExportSetting): JsonObject = buildJsonObject {
    put(
        "format",
        when (setting.format) {
            ExportFormat.Png -> "png"
            ExportFormat.Jpg -> "jpg"
            ExportFormat.Svg -> "svg"
            ExportFormat.Pdf -> "pdf"
        },
    )
    if (setting.scale != 1.0) put("scale", setting.scale)
    if (setting.suffix.isNotEmpty()) put("suffix", setting.suffix)
}

private fun writeSourceLocation(location: SourceLocation): JsonObject = buildJsonObject {
    if (location.file.isNotEmpty()) put("file", location.file)
    if (location.line != 0) put("line", location.line)
}

// --- Scalar helpers -------------------------------------------------------------

private fun <T> Bindable<T>.toJson(literal: (T) -> JsonPrimitive): JsonElement = when (this) {
    is Bindable.Value -> literal(value)
    is Bindable.VarRef -> buildJsonObject { put("\$var", id) }
    is Bindable.PropRef -> buildJsonObject { put("\$prop", name) }
    is Bindable.DataRef -> buildJsonObject { put("\$data", expression.raw) }
}

private fun doubleJson(value: Bindable<Double>): JsonElement = value.toJson(::JsonPrimitive)

private fun intJson(value: Bindable<Int>): JsonElement = value.toJson(::JsonPrimitive)

private fun stringJson(value: Bindable<String>): JsonElement = value.toJson(::JsonPrimitive)

private fun booleanJson(value: Bindable<Boolean>): JsonElement = value.toJson(::JsonPrimitive)

private fun colorJson(value: Bindable<DesignColor>): JsonElement =
    value.toJson { JsonPrimitive(it.toHexString()) }

private fun DesignColor.toHexString(): String {
    val rgb = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
    return if (alpha == 0xFF) {
        "#$rgb"
    } else {
        "#$rgb${alpha.toString(16).uppercase().padStart(2, '0')}"
    }
}

/**
 * IEEE-consistent default omission for a [Bindable] Double: a literal equal to [default] — treating
 * `-0.0` as `0.0`, unlike boxed-Double total order — is omitted, while any ref always serializes.
 * Guards must use this instead of `!= default.bindable()`, whose data-class equality writes `-0.0`.
 */
private fun Bindable<Double>.differsFrom(default: Double): Boolean {
    val literal = literalOrNull() ?: return true
    return literal != default
}

private fun writePoint(point: DesignPoint): JsonObject = buildJsonObject {
    if (point.x.differsFrom(0.0)) put("x", doubleJson(point.x))
    if (point.y.differsFrom(0.0)) put("y", doubleJson(point.y))
}

private fun stringMapJson(map: Map<String, String>): JsonObject = buildJsonObject {
    map.forEach { (key, value) -> put(key, value) }
}

private fun stringArrayJson(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

private fun alignItemsName(align: AlignItems): String = when (align) {
    AlignItems.Start -> "start"
    AlignItems.Center -> "center"
    AlignItems.End -> "end"
    AlignItems.Baseline -> "baseline"
    AlignItems.Stretch -> "stretch"
}

private fun sizingModeName(mode: SizingMode): String = when (mode) {
    SizingMode.Fixed -> "fixed"
    SizingMode.Hug -> "hug"
    SizingMode.Fill -> "fill"
}

private fun imageScaleModeName(mode: ImageScaleMode): String = when (mode) {
    ImageScaleMode.Fill -> "fill"
    ImageScaleMode.Fit -> "fit"
    ImageScaleMode.Crop -> "crop"
    ImageScaleMode.Tile -> "tile"
    ImageScaleMode.Stretch -> "stretch"
}

private fun overflowModeName(mode: OverflowMode): String = when (mode) {
    OverflowMode.Visible -> "visible"
    OverflowMode.Hidden -> "hidden"
    OverflowMode.Auto -> "auto"
}

private fun booleanOperationName(operation: BooleanOperationKind): String = when (operation) {
    BooleanOperationKind.Union -> "union"
    BooleanOperationKind.Subtract -> "subtract"
    BooleanOperationKind.Intersect -> "intersect"
    BooleanOperationKind.Exclude -> "exclude"
}
