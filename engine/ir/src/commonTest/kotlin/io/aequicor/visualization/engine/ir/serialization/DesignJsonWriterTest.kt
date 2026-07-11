package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignI18n
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextListType
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 5.2 writer: canonical-compact output, `parse(write(doc)) == doc` round trips. */
class DesignJsonWriterTest {

    @Test
    fun fullFeaturedDocumentRoundTrips() {
        val firstPass = assertIs<DesignParseResult.Success>(parseDesignDocument(FullFeaturedDocument))
        assertEquals(0, firstPass.diagnostics.size, "fixture must parse cleanly: ${firstPass.diagnostics}")
        val document = firstPass.document

        // Guard the fixture against silently dropped areas before asserting the round trip.
        assertEquals(2, document.components.size)
        assertEquals(1, document.componentSets.size)
        assertEquals(4, document.styles.size)
        assertEquals(3, document.assets.size)
        assertEquals(2, document.breakpoints.size)
        assertEquals(1, document.devicePresets.size)
        assertEquals(2, document.prototypeVariables.size)
        assertEquals(1, document.actionSets.size)
        assertEquals(2, document.i18n.resources.size)
        assertEquals(1, document.handoff.annotations.size)
        assertEquals(1, document.handoff.measurements.size)
        assertNotNull(document.handoff.code)
        assertEquals(1, document.motionRefs.size)
        assertNotNull(document.screen?.flow)
        assertEquals(
            5,
            document.variables.collections.getValue("col").vars.size,
        )
        val hero = assertNotNull(document.nodeById("hero"))
        assertEquals(3, hero.effects.size)
        assertNotNull(document.nodeById("title")?.sourceMap)
        assertIs<DesignNodeKind.Media>(assertNotNull(document.nodeById("hero_clip")).kind)
        assertEquals(3, assertIs<DesignNodeKind.Table>(assertNotNull(document.nodeById("tbl")).kind).table.columns.size)
        val instance = assertIs<DesignNodeKind.Instance>(assertNotNull(document.nodeById("inst")).kind)
        assertEquals(1, instance.overrides.size)
        assertEquals(6, instance.props.size)
        assertNotNull(document.nodeById("mask_shape")?.mask)
        assertNotNull(document.nodeById("abs")?.anchors)
        val cta = assertNotNull(document.nodeById("cta"))
        assertEquals(4, cta.interactions.size)
        assertEquals(2, cta.responsive.size)
        assertEquals(2, cta.exportSettings.size)
        assertNotNull(cta.motion)
        assertNotNull(cta.condition)
        assertNotNull(cta.repeat)
        val board = assertNotNull(document.nodeById("board"))
        assertEquals(1, board.layoutGrids.size)
        assertEquals(2, board.guides.size)
        assertNotNull(board.layout.implicitRows)

        val secondPass = parseDesignDocument(writeDesignDocument(document).toJsonString())
        val reparsed = assertIs<DesignParseResult.Success>(secondPass)
        assertEquals(0, reparsed.diagnostics.size, "written JSON must re-parse cleanly: ${reparsed.diagnostics}")
        assertEquals(document, reparsed.document)
    }

    @Test
    fun handBuiltDocumentRoundTrips() {
        val document = DesignDocument(
            id = "doc_manual",
            name = "Manual",
            pages = listOf(
                DesignPage(
                    id = "p",
                    name = "Main",
                    children = listOf(
                        DesignNode(
                            id = "title",
                            type = "text",
                            kind = DesignNodeKind.Text(
                                content = TextContent(
                                    key = "title.key",
                                    defaultLocale = "en-US",
                                    defaultText = "Hello, {name}!",
                                    params = mapOf("name" to "Мир".bindable()),
                                ),
                                list = TextListSettings(TextListType.Ordered, indent = 2),
                            ),
                            opacity = 0.8.bindable(),
                            size = DesignSize(width = 240.0),
                        ),
                        DesignNode(
                            id = "spacer",
                            type = "frame",
                            kind = DesignNodeKind.Frame,
                            children = listOf(
                                DesignNode(
                                    id = "inner",
                                    type = "rectangle",
                                    kind = DesignNodeKind.Shape(ShapeType.Rectangle),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            i18n = DesignI18n(
                sourceLocale = "en-US",
                targetLocales = listOf("ru-RU"),
                resources = mapOf("ru-RU" to mapOf("title.key" to "Привет, {name}!")),
            ),
            motionRefs = mapOf("pulse" to "asset_pulse"),
        )

        val result = parseDesignDocument(writeDesignDocument(document).toJsonString())
        assertEquals(document, assertIs<DesignParseResult.Success>(result).document)
    }

    @Test
    fun writerOmitsDefaultFields() {
        val minimalFrame = writeDesignNode(DesignNode(id = "n", type = "frame", kind = DesignNodeKind.Frame))
        assertEquals(setOf("id", "type"), minimalFrame.keys)
        assertEquals("""{"id":"n","type":"frame"}""", minimalFrame.toJsonString())

        val minimalText = writeDesignNode(DesignNode(id = "t", type = "text", kind = DesignNodeKind.Text()))
        assertEquals(setOf("id", "type"), minimalText.keys)

        val minimalDocument = writeDesignDocument(DesignDocument())
        assertEquals(setOf("schemaVersion"), minimalDocument.keys)
    }

    @Test
    fun writerNormalizesNegativeZeroCoordinates() {
        // A -0.0 coordinate must be omitted like 0.0 (IEEE), not written via boxed-Double total
        // order — otherwise widening DesignPoint.x/y to Bindable<Double> silently changes output.
        val node = DesignNode(
            id = "n",
            type = "frame",
            kind = DesignNodeKind.Frame,
            effects = listOf(
                DesignEffect.DropShadow(
                    color = DesignColor(0xFF000000L).bindable(),
                    offset = DesignPoint(x = -0.0, y = 0.0),
                ),
            ),
        )
        val serialized = writeDesignNode(node).toJsonString()
        assertTrue("-0.0" !in serialized, "negative-zero coordinate must not be serialized: $serialized")

        val reparsed = assertIs<DesignNodeParseResult.Success>(parseDesignNode(serialized)).node
        val offset = (reparsed.effects.single() as DesignEffect.DropShadow).offset
        assertEquals(0.0, offset.x.literalOrNull())
        assertEquals(0.0, offset.y.literalOrNull())
    }

    @Test
    fun shadowOffsetAndFocalPointRefsRoundTripThroughJson() {
        // A `$var` axis and a `{{expr}}` axis on both point-valued slots must survive JSON serde.
        val shadowNode = DesignNode(
            id = "s",
            type = "frame",
            kind = DesignNodeKind.Frame,
            effects = listOf(
                DesignEffect.DropShadow(
                    color = DesignColor(0xFF000000L).bindable(),
                    offset = DesignPoint(
                        x = Bindable.VarRef("shadow.x"),
                        y = Bindable.DataRef(DesignExpression("data.dy")),
                    ),
                ),
            ),
        )
        val reparsedShadow = assertIs<DesignNodeParseResult.Success>(
            parseDesignNode(writeDesignNode(shadowNode).toJsonString()),
        ).node
        val offset = (reparsedShadow.effects.single() as DesignEffect.DropShadow).offset
        assertEquals(Bindable.VarRef("shadow.x"), offset.x)
        assertEquals(Bindable.DataRef(DesignExpression("data.dy")), offset.y)

        val mediaNode = DesignNode(
            id = "m",
            type = "media",
            kind = DesignNodeKind.Media(
                DesignMedia(
                    assetId = "media/hero".bindable(),
                    focalPoint = DesignPoint(
                        x = Bindable.VarRef("crop.x"),
                        y = Bindable.DataRef(DesignExpression("data.fy")),
                    ),
                ),
            ),
        )
        val reparsedMedia = assertIs<DesignNodeParseResult.Success>(
            parseDesignNode(writeDesignNode(mediaNode).toJsonString()),
        ).node
        val focal = assertNotNull((reparsedMedia.kind as DesignNodeKind.Media).media.focalPoint)
        assertEquals(Bindable.VarRef("crop.x"), focal.x)
        assertEquals(Bindable.DataRef(DesignExpression("data.fy")), focal.y)
    }

    @Test
    fun unknownKindWritesRawType() {
        val node = DesignNode(id = "x", type = "hologram", kind = DesignNodeKind.Unknown("hologram"))
        val json = writeDesignNode(node)
        assertEquals("hologram", (json["type"] as JsonPrimitive).content)

        val result = parseDesignNode(json.toJsonString())
        val success = assertIs<DesignNodeParseResult.Success>(result)
        assertEquals(node, success.node)
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "hologram" in it.message })
    }

    @Test
    fun toJsonStringPrettyPrintsOnDemand() {
        val json = writeDesignNode(DesignNode(id = "n", type = "frame", kind = DesignNodeKind.Frame))
        assertTrue('\n' !in json.toJsonString())
        val pretty = json.toJsonString(pretty = true)
        assertTrue('\n' in pretty)
        assertEquals(json, assertIs<DesignNodeParseResult.Success>(parseDesignNode(pretty)).node.let(::writeDesignNode))
    }
}

/** Exercises every model area (see the guard assertions in [DesignJsonWriterTest]). */
private val FullFeaturedDocument = """
{
  "schemaVersion": "slm-ir/1.0",
  "id": "doc_full",
  "name": "Full Coverage",
  "screen": {
    "id": "missionDashboard",
    "name": "Mission Dashboard",
    "page": "p_main",
    "modes": { "theme": "light", "density": "compact" },
    "frame": { "preset": "desktop-1440", "width": 1440, "height": 1024 },
    "canvas": { "section": "Operations", "x": 100, "y": 200 },
    "flow": { "id": "flow1", "node": "hero", "next": ["screen2"] }
  },
  "libraries": [ { "id": "ds", "source": "@company/design-system" } ],
  "breakpoints": [ { "id": "compact", "maxWidth": 599 }, { "id": "expanded", "minWidth": 600 } ],
  "devicePresets": [ { "id": "phone", "width": 390, "height": 844, "platform": "ios" } ],
  "variables": { "collections": { "col": {
    "name": "Theme",
    "modes": ["light", "dark"],
    "defaultMode": "light",
    "vars": {
      "var_brand": { "type": "color", "values": { "light": "#1F5FA8", "dark": "#143A66" } },
      "var_accent": { "type": "color", "values": { "light": { "${'$'}var": "var_brand" }, "dark": "#2BB8A8" } },
      "var_space": { "type": "number", "values": { "light": 8, "dark": 12 } },
      "var_label": { "type": "string", "values": { "light": "Light", "dark": "Dark" } },
      "var_on": { "type": "boolean", "values": { "light": true, "dark": false } }
    }
  } } },
  "styles": {
    "style_text": { "type": "text", "value": {
      "fontFamily": "Inter", "fontWeight": 600, "fontSize": 16,
      "lineHeight": { "unit": "percent", "value": 140 },
      "letterSpacing": { "unit": "px", "value": 0.4 },
      "paragraphSpacing": 8,
      "textAlignHorizontal": "center", "textAlignVertical": "center",
      "textCase": "upper", "textDecoration": "underline",
      "fontFeatures": { "tnum": true },
      "variableAxes": { "opsz": 24 }
    } },
    "style_fill": { "type": "paint", "value": [ { "type": "solid", "color": { "${'$'}var": "var_brand" } } ] },
    "style_effect": { "type": "effect", "value": [ { "type": "layerBlur", "radius": 4 } ] },
    "style_grid": { "type": "grid", "value": [
      { "type": "columns", "count": 12, "gutter": 16, "margin": 24, "alignment": "center", "color": "#1F5FA833", "visible": false }
    ] }
  },
  "assets": {
    "asset_img": { "type": "image", "hash": "abc", "url": "https://cdn/img.png", "width": 640, "height": 480 },
    "asset_video": { "type": "video", "url": "https://cdn/clip.mp4" },
    "asset_pulse": { "type": "motion" }
  },
  "componentSets": { "set_btn": {
    "name": "Button",
    "axes": { "kind": ["primary", "ghost"] },
    "variants": { "kind=primary": "cmp_btn", "kind=ghost": "cmp_btn_ghost" }
  } },
  "components": {
    "cmp_btn": {
      "name": "Button",
      "libraryId": "ds",
      "properties": {
        "label": { "type": "text", "default": "Button" },
        "icon": { "type": "instanceSwap", "preferredValues": ["cmp_icon_a", "cmp_icon_b"] },
        "body": { "type": "slot", "minItems": 0, "maxItems": 2, "allowedContent": ["cmp_row"] }
      },
      "exposedInstances": [ ["btn_root", "nested_icon"] ],
      "root": { "id": "btn_root", "type": "frame",
        "layout": { "mode": "horizontal", "gap": 8, "padding": { "top": 4, "right": 8, "bottom": 4, "left": 8 } },
        "children": [
          { "id": "btn_label", "type": "text", "characters": { "${'$'}prop": "label" } },
          { "id": "btn_slot", "type": "slot", "slot": "body" }
        ] }
    },
    "cmp_btn_ghost": { "name": "Ghost", "root": { "id": "ghost_root", "type": "frame" } }
  },
  "prototypeVariables": {
    "dialogOpen": { "type": "boolean", "default": false },
    "selectedTab": { "type": "string", "default": "overview" }
  },
  "actionSets": { "openDialog": [
    { "type": "setVariable", "variable": "dialogOpen", "value": "true" },
    { "type": "openOverlay", "destination": "dlg", "overlay": { "position": "topRight", "offset": { "x": 8, "y": 8 } } }
  ] },
  "i18n": {
    "sourceLocale": "en-US",
    "targetLocales": ["en-US", "ru-RU"],
    "resources": {
      "en-US": { "title": "Missions", "count": "{count, plural, one {# mission} other {# missions}}" },
      "ru-RU": { "title": "Миссии", "count": "{count, plural, one {# миссия} few {# миссии} many {# миссий} other {# миссии}}" }
    }
  },
  "handoff": {
    "annotations": [ { "id": "a1", "target": "hero", "text": "Primary entry point", "audience": "dev" } ],
    "measurements": [ { "from": "hero", "to": "board", "axis": "inline", "value": 24 } ],
    "code": { "framework": "compose", "componentHint": "MissionDashboard" }
  },
  "motionRefs": { "pulse": "asset_pulse" },
  "pages": [ { "id": "p_main", "name": "Main", "children": [
    { "id": "hero", "type": "section", "name": "Hero", "role": "header", "order": 2, "blendMode": "screen",
      "layout": {
        "mode": "horizontal", "gap": "auto", "crossGap": 4, "wrap": true,
        "padding": 12, "paddingLogical": { "inlineStart": 16, "inlineEnd": 16 },
        "alignItems": "center", "justifyContent": "spaceBetween", "baseline": "last", "clipsContent": true
      },
      "fillStyleId": "style_fill", "strokeStyleId": "style_fill", "effectStyleId": "style_effect",
      "effects": [
        { "type": "dropShadow", "color": "#00000040", "offset": { "y": 2 }, "blur": 8, "spread": 1 },
        { "type": "innerShadow", "color": "#FFFFFF20", "blur": 4, "visible": { "${'$'}var": "var_on" } },
        { "type": "backgroundBlur", "radius": 6 }
      ],
      "cornerRadius": { "topLeft": 8, "topRight": 8 }, "cornerSmoothing": 0.6,
      "children": [
        { "id": "title", "type": "text", "name": "Title",
          "textStyleId": "style_text",
          "characters": "MISSIONS",
          "content": {
            "key": "title", "defaultLocale": "en-US", "defaultText": "Missions",
            "params": { "count": "{{missions.length}}", "unit": "шт" }
          },
          "format": { "type": "currency", "options": { "currency": "USD" } },
          "textStyle": { "fontSize": 24, "fontWeight": { "${'$'}var": "var_space" } },
          "autoResize": "height",
          "truncate": { "maxLines": 2, "ellipsis": false },
          "styleRanges": [ { "start": 0, "end": 4, "styleRef": "sty_h", "style": {
            "fontWeight": 700, "textDecoration": "underline",
            "fills": [ { "type": "solid", "color": "#E97155" } ]
          } } ],
          "links": [
            { "start": 0, "end": 4, "url": "https://aequicor.io" },
            { "start": 5, "end": 8, "nodeTarget": "board" }
          ],
          "list": { "type": "bullet", "indent": 1 },
          "sourceMap": { "file": "dash.slm.md", "line": 12 },
          "blockSourceMaps": { "layout": { "file": "dash.slm.md", "line": 14 } } },
        { "id": "hero_clip", "type": "media", "size": { "width": 320, "height": 180 },
          "media": {
            "assetId": "asset_video", "kind": "video", "fillMode": "crop",
            "focalPoint": { "x": 0.3, "y": 0.7 },
            "alt": { "key": "hero.alt", "defaultText": "Intro clip" },
            "replaceable": true, "opacity": 0.9, "blendMode": "screen",
            "posterAssetId": "asset_img", "autoplay": true, "loop": true, "muted": false
          } },
        { "id": "mask_shape", "type": "ellipse", "isMask": true,
          "mask": { "type": "luminance", "appliesTo": ["photo1"] } },
        { "id": "photo1", "type": "rectangle",
          "fills": [ { "type": "image", "assetId": "asset_img", "scaleMode": "tile",
                       "focalPoint": { "x": 0.5, "y": 0.5 }, "replaceable": true, "opacity": 0.8 } ] },
        { "id": "vid_fill", "type": "rectangle",
          "fills": [ { "type": "video", "assetId": "asset_video", "scaleMode": "stretch",
                       "posterAssetId": "asset_img", "autoplay": true, "loop": true, "muted": false,
                       "visible": { "${'$'}var": "var_on" }, "blendMode": "screen" } ] }
      ] },
    { "id": "board", "type": "frame",
      "layout": {
        "mode": "grid",
        "columns": [ { "type": "fixed", "value": 120 }, { "type": "flex", "value": 1 } ],
        "rows": { "auto": true, "min": 32 },
        "gap": { "column": 8, "row": 4 }
      },
      "gridStyleId": "style_grid",
      "layoutGrids": [ { "type": "grid", "size": 8, "color": "#2BB8A833" } ],
      "guides": [
        { "orientation": "vertical", "position": 320 },
        { "orientation": "horizontal", "position": 96 }
      ],
      "children": [
        { "id": "cellA", "type": "frame", "gridPlacement": { "column": 1, "columnSpan": 2 } },
        { "id": "tbl", "type": "table",
          "table": {
            "columns": [ { "type": "fixed", "value": 120 }, { "type": "flex", "value": 2 }, { "type": "hug" } ],
            "headerRows": 2, "rowGap": 4, "columnGap": { "${'$'}var": "var_space" }
          },
          "children": [ { "id": "row1", "type": "frame", "children": [
            { "id": "cell11", "type": "text", "characters": "A" }
          ] } ] }
      ] },
    { "id": "inst", "type": "instance", "componentId": "set_btn", "libraryRef": "ds",
      "variant": { "kind": "primary" },
      "props": {
        "label": "Open", "count": 3, "enabled": true,
        "bound": { "${'$'}data": "mission.name" },
        "caption": { "content": { "key": "title", "defaultText": "Missions" } },
        "body": [ { "id": "row_extra", "type": "frame" } ]
      },
      "overrides": [ { "target": ["btn_root", "btn_label"], "set": {
        "characters": "Go",
        "fills": [ { "type": "solid", "color": "#FFFFFF" } ],
        "opacity": 0.9, "visible": true,
        "textStyle": { "fontSize": 12 },
        "cornerRadius": 4,
        "variant": { "kind": "ghost" },
        "props": { "label": "Nested" },
        "slotContent": [ { "id": "extra", "type": "text", "characters": "X" } ]
      } } ],
      "detach": true, "resetOverrides": true },
    { "id": "abs", "type": "rectangle",
      "visible": { "${'$'}var": "var_on" }, "locked": true, "order": 3, "role": "primaryAction",
      "opacity": 0.5, "blendMode": "multiply", "rotation": 15,
      "position": { "x": 10, "y": 20 },
      "constraints": { "horizontal": "leftRight", "vertical": "bottom" },
      "anchors": { "inlineStart": 24, "inlineEnd": 24, "blockStart": 16, "blockEnd": { "${'$'}var": "var_space" } },
      "size": { "width": 200, "height": 100 },
      "sizing": { "horizontal": "fill", "vertical": "hug" },
      "minSize": { "width": 100 }, "maxSize": { "height": 400 },
      "layoutChild": { "alignSelf": "end", "absolute": true },
      "variableModes": { "col": "dark" },
      "cornerRadius": { "${'$'}var": "var_space" },
      "strokes": {
        "paints": [ { "type": "solid", "color": "#143A66" } ],
        "weight": 2, "weightPerSide": { "top": 1, "bottom": 2 },
        "align": "center", "dashPattern": [4, 2], "cap": "round", "join": "bevel"
      },
      "fills": [ { "type": "gradientLinear", "to": { "x": 1, "y": 1 }, "stops": [
        { "position": 0, "color": "#1F5FA8" },
        { "position": 1, "color": { "${'$'}var": "var_accent" } }
      ] } ] },
    { "id": "star1", "type": "star", "pointCount": 5, "innerRadius": 0.5 },
    { "id": "vec1", "type": "vector",
      "paths": [ { "windingRule": "evenodd", "d": "M0 0L10 10Z" } ],
      "iconRef": "ds/Icon/Alert", "pathRef": "asset_img",
      "viewBox": { "width": 24, "height": 24 } },
    { "id": "bool1", "type": "booleanOperation", "operation": "subtract", "children": [
      { "id": "b_a", "type": "rectangle" }, { "id": "b_b", "type": "ellipse" }
    ] },
    { "id": "cut", "type": "slice" },
    { "id": "note1", "type": "annotation",
      "annotation": { "id": "note1", "target": "hero", "text": "Sticky header", "audience": "design" } },
    { "id": "cta", "type": "frame",
      "condition": "{{missions.length > 0}}",
      "repeat": { "item": "mission", "in": "missions", "index": "i", "key": "{{mission.id}}" },
      "interactions": [
        { "trigger": "onClick", "actions": [
            { "type": "openOverlay", "destination": "dlg",
              "overlay": { "position": "bottomCenter", "closeOnOutsideClick": false, "background": "#00000080" },
              "transition": { "type": "slideIn", "direction": "bottom", "durationMs": 240,
                              "easing": { "type": "spring", "stiffness": 220, "damping": 24 } } },
            { "type": "setVariable", "variable": "dialogOpen", "value": "true" }
          ],
          "sourceMap": { "file": "dash.slm.md", "line": 40 } },
        { "trigger": "afterDelay", "delayMs": 1500, "actions": [ { "type": "back" } ] },
        { "trigger": "onKey", "key": "Escape", "actions": [
            { "type": "closeOverlay", "transition": { "type": "dissolve",
              "easing": { "type": "cubicBezier", "x1": 0.4, "y1": 0, "x2": 0.2, "y2": 1 } } }
          ] },
        { "trigger": "onVariableChange", "variable": "selectedTab", "actions": [
            { "type": "scrollTo", "target": "board", "animated": false },
            { "type": "changeToVariant", "target": "inst", "variant": { "kind": "ghost" } },
            { "type": "runActionSet", "actionSetId": "openDialog" },
            { "type": "navigate", "to": "screen2", "transition": { "type": "push", "direction": "right" } },
            { "type": "openLink", "url": "https://aequicor.io" },
            { "type": "swapOverlay", "destination": "dlg2" }
          ] }
      ],
      "motion": { "ref": "pulse", "fallback": { "durationMs": 600, "loop": true, "frames": [
        { "at": 0, "properties": { "opacity": 0.2 } },
        { "at": 1, "properties": { "opacity": 1, "scale": 1.1 } }
      ] } },
      "responsive": [
        { "when": { "breakpoint": "compact", "theme": "dark" },
          "set": {
            "visible": false, "opacity": 0.5,
            "layout": { "mode": "grid", "rows": [ { "type": "fixed", "value": 40 } ] },
            "layoutChild": { "absolute": true },
            "gridPlacement": { "column": 1 },
            "sizing": { "horizontal": "fill" },
            "size": { "width": 100 }, "minSize": { "width": 50 }, "maxSize": { "width": 200 },
            "fills": [ { "type": "solid", "color": "#143A66" } ],
            "strokes": { "weight": 1 },
            "effects": [ { "type": "layerBlur", "radius": 2 } ],
            "cornerRadius": 8,
            "textStyle": { "fontSize": 12 },
            "scroll": { "overflow": "vertical" }
          },
          "sourceMap": { "file": "dash.slm.md", "line": 50 } },
        { "when": { "locale": "ru-RU" }, "set": { "opacity": 1 } }
      ],
      "exportSettings": [ { "format": "png", "scale": 2, "suffix": "@2x" }, { "format": "svg" } ],
      "scroll": { "overflow": "vertical", "sticky": true, "overflowX": "hidden", "overflowY": "auto",
                  "fixedChildren": ["cta_header"] },
      "children": [ { "id": "cta_header", "type": "frame" } ] }
  ] } ]
}
"""
