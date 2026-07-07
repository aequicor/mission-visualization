package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignEasing
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideOrientation
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.LayoutGridAlignment
import io.aequicor.visualization.engine.ir.model.LayoutGridType
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.OverlayPosition
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.TransitionDirection
import io.aequicor.visualization.engine.ir.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 5.1 parser surface: screen meta, media, interactions, responsive, slots, bindings. */
class DesignJsonParserExpansionTest {

    @Test
    fun parsesScreenMetaLibrariesBreakpointsAndI18nResources() {
        val result = parseDesignDocument(
            """
            {
              "schemaVersion": "slm-ir/1.0",
              "screen": {
                "id": "missionDashboard",
                "name": "Mission Dashboard",
                "page": "Operations",
                "sourceLocale": "ru-RU",
                "targetLocales": ["ru-RU", "en-US"],
                "modes": { "theme": "light", "density": "compact" },
                "frame": { "preset": "desktop-1440", "width": 1440, "height": 1024 },
                "canvas": { "section": "Operations", "x": 100, "y": 200 },
                "flow": { "id": "missionOperations", "node": "dashboard", "next": ["createMissionDialog"] }
              },
              "libraries": [ { "id": "ds", "source": "@company/design-system" } ],
              "breakpoints": [
                { "id": "compact", "maxWidth": 599 },
                { "id": "expanded", "minWidth": 600 }
              ],
              "devicePresets": [ { "id": "phone", "width": 390, "height": 844, "platform": "ios" } ],
              "resources": {
                "ru-RU": { "missionDashboard.title": "Панель миссий" },
                "en-US": { "missionDashboard.title": "Mission Dashboard" }
              },
              "pages": [ { "id": "p", "children": [] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        val document = success.document
        assertEquals("slm-ir/1.0", document.schemaVersion)

        val screen = assertNotNull(document.screen)
        assertEquals("missionDashboard", screen.id)
        assertEquals("Operations", screen.page)
        assertEquals(mapOf("theme" to "light", "density" to "compact"), screen.modes)
        assertEquals("desktop-1440", screen.frame?.preset)
        assertEquals(1440.0, screen.frame?.width)
        assertEquals("Operations", screen.canvas?.section)
        assertEquals(200.0, screen.canvas?.y)
        assertEquals(listOf("createMissionDialog"), screen.flow?.next)

        val library = document.libraries.single()
        assertEquals("ds", library.id)
        assertEquals("@company/design-system", library.source)

        assertEquals(2, document.breakpoints.size)
        assertEquals(599.0, document.breakpoints.first { it.id == "compact" }.maxWidth)
        assertEquals(600.0, document.breakpoints.first { it.id == "expanded" }.minWidth)

        val preset = document.devicePresets.single()
        assertEquals(390.0, preset.width)
        assertEquals("ios", preset.platform)

        assertEquals("ru-RU", document.i18n.sourceLocale)
        assertEquals(listOf("ru-RU", "en-US"), document.i18n.targetLocales)
        assertEquals("Mission Dashboard", document.i18n.resources["en-US"]?.get("missionDashboard.title"))
        assertEquals("Панель миссий", document.i18n.resources["ru-RU"]?.get("missionDashboard.title"))
    }

    @Test
    fun parsesMediaNodeWithFocalPointAndVideoSettings() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "hero", "type": "media",
                  "media": {
                    "assetId": "asset_video",
                    "kind": "video",
                    "fillMode": "crop",
                    "focalPoint": { "x": 0.3, "y": 0.7 },
                    "alt": { "key": "hero.alt", "defaultText": "Intro clip" },
                    "posterAssetId": "asset_poster",
                    "autoplay": true, "loop": true, "muted": false
                  } }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        val node = assertNotNull(success.document.nodeById("hero"))
        val media = assertIs<DesignNodeKind.Media>(node.kind).media
        assertEquals("asset_video", media.assetId)
        assertEquals(MediaKind.Video, media.kind)
        assertEquals(ImageScaleMode.Crop, media.fillMode)
        assertEquals(0.3, media.focalPoint?.x)
        assertEquals(0.7, media.focalPoint?.y)
        assertEquals("hero.alt", media.alt?.key)
        assertEquals("Intro clip", media.alt?.defaultText)
        assertEquals("asset_poster", media.posterAssetId)
        assertTrue(media.autoplay)
        assertTrue(media.loop)
        assertEquals(false, media.muted)
    }

    @Test
    fun parsesInteractionsWithOverlayTransitionAndSpringEasing() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "btn", "type": "frame",
                  "interactions": [
                    { "trigger": "onClick",
                      "action": {
                        "type": "openOverlay",
                        "destination": "dialog_create",
                        "overlay": { "position": "bottomCenter", "closeOnOutsideClick": false, "background": "#00000080" },
                        "transition": {
                          "type": "slideIn", "direction": "bottom", "durationMs": 240,
                          "easing": { "type": "spring", "stiffness": 220, "damping": 24 }
                        }
                      } },
                    { "trigger": "afterDelay", "delayMs": 1500, "actions": [ { "type": "back" } ] }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        val node = assertNotNull(success.document.nodeById("btn"))
        assertEquals(2, node.interactions.size)

        val click = node.interactions.first()
        assertEquals(InteractionTrigger.OnClick, click.trigger)
        val open = assertIs<DesignAction.OpenOverlay>(click.actions.single())
        assertEquals("dialog_create", open.destination)
        assertEquals(OverlayPosition.BottomCenter, open.overlay.position)
        assertEquals(false, open.overlay.closeOnOutsideClick)
        assertNotNull(open.overlay.background)
        val transition = assertNotNull(open.transition)
        assertEquals(TransitionType.SlideIn, transition.type)
        assertEquals(TransitionDirection.Bottom, transition.direction)
        assertEquals(240.0, transition.durationMs)
        val spring = assertIs<DesignEasing.Spring>(transition.easing)
        assertEquals(220.0, spring.stiffness)
        assertEquals(24.0, spring.damping)

        val delayed = node.interactions.last()
        assertEquals(InteractionTrigger.AfterDelay, delayed.trigger)
        assertEquals(1500.0, delayed.delayMs)
        assertIs<DesignAction.Back>(delayed.actions.single())
    }

    @Test
    fun parsesResponsiveVariantsWithDimensionSelectors() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "panel", "type": "frame",
                  "responsive": [
                    { "when": { "breakpoint": "compact", "theme": "dark" },
                      "set": { "visible": false, "opacity": 0.5, "layout": { "mode": "vertical" } } },
                    { "when": { "hologram": "on" }, "set": { "opacity": 0 } }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        val node = assertNotNull(success.document.nodeById("panel"))
        val variant = node.responsive.single()
        assertEquals(
            mapOf(
                ResponsiveDimension.Breakpoint to "compact",
                ResponsiveDimension.Theme to "dark",
            ),
            variant.selectors,
        )
        assertEquals(false, (variant.patch.visible as? Bindable.Value)?.value)
        assertEquals(0.5, (variant.patch.opacity as? Bindable.Value)?.value)
        assertEquals(LayoutMode.Vertical, variant.patch.layout?.mode)
        assertNull(variant.patch.fills)
        assertNull(variant.patch.sizing)
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "hologram" in it.message })
    }

    @Test
    fun parsesSlotPropAndNestedInstanceOverride() {
        val result = parseDesignDocument(
            """
            {
              "components": {
                "cmp_card": {
                  "name": "Card",
                  "properties": {
                    "body": { "type": "slot", "minItems": 1, "maxItems": 3, "allowedContent": ["cmp_row"] }
                  },
                  "root": { "id": "card_root", "type": "frame", "children": [
                    { "id": "body_slot", "type": "slot", "slot": "body" }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "inst", "type": "instance", "componentId": "cmp_card", "libraryRef": "ds",
                  "resetOverrides": true,
                  "props": { "body": [ { "id": "row1", "type": "frame" } ] },
                  "overrides": [
                    { "target": ["card_root", "nested_button"],
                      "set": {
                        "variant": { "kind": "primary" },
                        "props": { "label": "Open" },
                        "slotContent": [ { "id": "extra", "type": "text", "characters": "X" } ]
                      } }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")

        val component = assertNotNull(success.document.components["cmp_card"])
        val slotProperty = assertNotNull(component.properties["body"])
        assertEquals(ComponentPropertyType.Slot, slotProperty.type)
        assertEquals(1, slotProperty.minItems)
        assertEquals(3, slotProperty.maxItems)
        assertEquals(listOf("cmp_row"), slotProperty.allowedContent)
        val slotNode = assertNotNull(component.root.findById("body_slot"))
        assertEquals("body", assertIs<DesignNodeKind.Slot>(slotNode.kind).slotName)

        val instanceNode = assertNotNull(success.document.nodeById("inst"))
        val instance = assertIs<DesignNodeKind.Instance>(instanceNode.kind)
        assertEquals("ds", instance.libraryRef)
        assertTrue(instance.resetOverrides)
        val slotFill = assertIs<PropValue.SlotContent>(assertNotNull(instance.props["body"]))
        assertEquals("row1", slotFill.nodes.single().id)

        val override = instance.overrides.single()
        assertEquals(listOf("card_root", "nested_button"), override.target)
        assertEquals(mapOf("kind" to "primary"), override.variant)
        assertEquals(PropValue.Text("Open"), override.props?.get("label"))
        assertEquals("extra", override.slotContent?.single()?.id)
    }

    @Test
    fun parsesDataBindingsAsDataRefs() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "label", "type": "text",
                  "characters": { "${'$'}data": "mission.name" },
                  "visible": { "${'$'}data": "{{missions.length > 0}}" },
                  "content": {
                    "key": "missions.count",
                    "defaultText": "0 missions",
                    "params": { "count": "{{missions.length}}" }
                  },
                  "condition": "{{missions.length == 0}}",
                  "repeat": { "item": "mission", "in": "missions", "key": "{{mission.id}}" } }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        val node = assertNotNull(success.document.nodeById("label"))
        val text = assertIs<DesignNodeKind.Text>(node.kind)

        val characters = assertIs<Bindable.DataRef>(text.characters)
        assertEquals("mission.name", characters.expression.raw)
        val visible = assertIs<Bindable.DataRef>(node.visible)
        assertEquals("missions.length > 0", visible.expression.raw)

        val content = assertNotNull(text.content)
        assertEquals("missions.count", content.key)
        val countParam = assertIs<Bindable.DataRef>(assertNotNull(content.params["count"]))
        assertEquals("missions.length", countParam.expression.raw)

        assertEquals("missions.length == 0", node.condition?.expression?.raw)
        val repeat = assertNotNull(node.repeat)
        assertEquals("mission", repeat.itemName)
        assertEquals("missions", repeat.collection.raw)
        assertEquals("mission.id", repeat.key?.raw)
    }

    @Test
    fun parsesTableNodeLayoutGridsAndGuides() {
        val result = parseDesignDocument(
            """
            {
              "styles": {
                "style_grid": { "type": "grid", "value": [ { "type": "rows", "size": 8 } ] }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "board", "type": "frame",
                  "gridStyleId": "style_grid",
                  "layoutGrids": [
                    { "type": "columns", "count": 12, "gutter": 16, "margin": 24, "alignment": "center", "color": "#1F5FA833" }
                  ],
                  "guides": [ { "orientation": "vertical", "position": 320 } ],
                  "children": [
                    { "id": "tbl", "type": "table",
                      "table": {
                        "columns": [ { "type": "fixed", "value": 120 }, { "type": "flex", "value": 1 } ],
                        "headerRows": 2, "rowGap": 4, "columnGap": 8
                      } }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")

        val board = assertNotNull(success.document.nodeById("board"))
        assertEquals("style_grid", board.gridStyleId)
        val layoutGrid = board.layoutGrids.single()
        assertEquals(LayoutGridType.Columns, layoutGrid.type)
        assertEquals(12, layoutGrid.count)
        assertEquals(16.0, layoutGrid.gutter)
        assertEquals(LayoutGridAlignment.Center, layoutGrid.alignment)
        assertNotNull(layoutGrid.color)
        val guide = board.guides.single()
        assertEquals(GuideOrientation.Vertical, guide.orientation)
        assertEquals(320.0, guide.position)

        val gridStyle = assertIs<DesignStyle.Grid>(assertNotNull(success.document.styles["style_grid"]))
        assertEquals(LayoutGridType.Rows, gridStyle.value.single().type)
        assertEquals(8.0, gridStyle.value.single().size)

        val tableNode = assertNotNull(success.document.nodeById("tbl"))
        val table = assertIs<DesignNodeKind.Table>(tableNode.kind).table
        assertEquals(listOf(GridTrack.Fixed(120.0), GridTrack.Flex(1.0)), table.columns)
        assertEquals(2, table.headerRows)
        assertEquals(4.0, (table.rowGap as Bindable.Value<Double>).value)
        assertEquals(8.0, (table.columnGap as Bindable.Value<Double>).value)
    }

    @Test
    fun parsesNodeSourceMapAndBlockSourceMaps() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "n", "type": "frame",
                  "sourceMap": { "file": "mission-dashboard.layout.md", "line": 12 },
                  "blockSourceMaps": {
                    "layout": { "file": "mission-dashboard.layout.md", "line": 14 },
                    "style": { "file": "mission-dashboard.layout.md", "line": 18 }
                  } }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        val node = assertNotNull(success.document.nodeById("n"))
        val sourceMap = assertNotNull(node.sourceMap)
        assertEquals("mission-dashboard.layout.md", sourceMap.file)
        assertEquals(12, sourceMap.line)
        assertEquals(14, node.blockSourceMaps["layout"]?.line)
        assertEquals(18, node.blockSourceMaps["style"]?.line)
    }

    @Test
    fun unknownActionTypeBecomesUnknownWithWarning() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "btn", "type": "frame",
                  "interactions": [
                    { "trigger": "onClick", "action": { "type": "teleport", "to": "/mars" } }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        val node = assertNotNull(success.document.nodeById("btn"))
        val action = assertIs<DesignAction.Unknown>(node.interactions.single().actions.single())
        assertEquals("teleport", action.rawType)
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "teleport" in it.message })
    }

    @Test
    fun unknownTriggerDropsInteractionWithWarning() {
        val result = parseDesignDocument(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "btn", "type": "frame",
                  "interactions": [
                    { "trigger": "onSneeze", "action": { "type": "back" } },
                    { "trigger": "onClick", "action": { "type": "back" } }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        val node = assertNotNull(success.document.nodeById("btn"))
        assertEquals(listOf(InteractionTrigger.OnClick), node.interactions.map { it.trigger })
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "onSneeze" in it.message })
    }

    @Test
    fun legacySchemaVersionStillParses() {
        val result = parseDesignDocument(
            """
            { "schemaVersion": "1.0.0", "pages": [ { "id": "p", "children": [ { "id": "n", "type": "frame" } ] } ] }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals("1.0.0", success.document.schemaVersion)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        assertNotNull(success.document.nodeById("n"))
    }

    @Test
    fun unsupportedSchemaVersionWarnsButParses() {
        val result = parseDesignDocument(
            """
            { "schemaVersion": "slm-ir/2.0", "pages": [ { "id": "p", "children": [] } ] }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals("slm-ir/2.0", success.document.schemaVersion)
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "slm-ir/2.0" in it.message })
    }
}
