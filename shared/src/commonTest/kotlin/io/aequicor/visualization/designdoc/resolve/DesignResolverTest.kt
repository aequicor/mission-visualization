package io.aequicor.visualization.designdoc.resolve

import io.aequicor.visualization.designdoc.domain.model.DesignColor
import io.aequicor.visualization.designdoc.domain.parser.DesignParseResult
import io.aequicor.visualization.designdoc.domain.parser.parseDesignDocument
import io.aequicor.visualization.designdoc.domain.resolve.DesignResolver
import io.aequicor.visualization.designdoc.domain.resolve.ResolvedNode
import io.aequicor.visualization.designdoc.domain.resolve.ResolvedPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignResolverTest {

    private fun resolveFirstFrame(json: String): Pair<ResolvedNode, DesignResolver> {
        val result = parseDesignDocument(json)
        val success = assertIs<DesignParseResult.Success>(result)
        val resolver = DesignResolver(success.document)
        val root = assertNotNull(resolver.resolvePage(success.document.pages.first()).firstOrNull())
        return root to resolver
    }

    private fun ResolvedNode.findBySourceId(id: String): ResolvedNode? {
        if (sourceId == id) return this
        return children.firstNotNullOfOrNull { it.findBySourceId(id) }
    }

    private fun ResolvedNode.solidFill(): DesignColor =
        (fills.first() as ResolvedPaint.Solid).color

    @Test
    fun resolvesVariablesWithModesAndAliases() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "variables": { "collections": { "col": {
                "modes": ["light", "dark"], "defaultMode": "light",
                "vars": {
                  "var_brand": { "type": "color", "values": { "light": "#112233", "dark": "#445566" } },
                  "var_accent": { "type": "color", "values": { "light": { "${'$'}var": "var_brand" }, "dark": "#778899" } }
                }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "var_accent" } } ],
                  "children": [
                    { "id": "darkChild", "type": "frame",
                      "variableModes": { "col": "dark" },
                      "fills": [ { "type": "solid", "color": { "${'$'}var": "var_accent" } } ] }
                  ] }
              ] } ]
            }
            """.trimIndent(),
        )
        assertEquals(DesignColor.fromHex("#112233"), root.solidFill(), "alias resolves through to brand in default mode")
        val darkChild = assertNotNull(root.findBySourceId("darkChild"))
        assertEquals(DesignColor.fromHex("#778899"), darkChild.solidFill(), "variableModes switches the subtree to dark")
    }

    @Test
    fun aliasCycleProducesDiagnosticInsteadOfHanging() {
        val (_, resolver) = resolveFirstFrame(
            """
            {
              "variables": { "collections": { "col": {
                "modes": ["m"], "defaultMode": "m",
                "vars": {
                  "var_a": { "type": "color", "values": { "m": { "${'$'}var": "var_b" } } },
                  "var_b": { "type": "color", "values": { "m": { "${'$'}var": "var_a" } } }
                }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "fills": [ { "type": "solid", "color": { "${'$'}var": "var_a" } } ] }
              ] } ]
            }
            """.trimIndent(),
        )
        assertTrue(resolver.diagnostics.any { "cycle" in it.message })
    }

    @Test
    fun expandsInstanceWithPropsDefaultsAndOverrides() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_btn": {
                  "name": "Button",
                  "properties": {
                    "label": { "type": "text", "default": "Button" },
                    "iconOn": { "type": "boolean", "default": false }
                  },
                  "root": {
                    "id": "btn_root", "type": "frame",
                    "layout": { "mode": "horizontal", "gap": 8 },
                    "children": [
                      { "id": "btn_icon", "type": "rectangle", "visible": { "${'$'}prop": "iconOn" },
                        "size": { "width": 16, "height": 16 } },
                      { "id": "btn_label", "type": "text", "characters": { "${'$'}prop": "label" } }
                    ]
                  }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "inst_default", "type": "instance", "componentId": "cmp_btn" },
                  { "id": "inst_custom", "type": "instance", "componentId": "cmp_btn",
                    "props": { "label": "Buy now", "iconOn": true },
                    "overrides": [
                      { "target": ["btn_label"],
                        "set": { "fills": [ { "type": "solid", "color": "#FF0000" } ] } }
                    ] }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val defaultInstance = assertNotNull(root.findBySourceId("inst_default"))
        assertNull(defaultInstance.findBySourceId("btn_icon"), "iconOn=false hides the icon")
        val defaultLabel = assertNotNull(defaultInstance.findBySourceId("btn_label"))
        assertEquals("Button", defaultLabel.text?.characters)

        val custom = assertNotNull(root.findBySourceId("inst_custom"))
        assertNotNull(custom.findBySourceId("btn_icon"), "iconOn=true shows the icon")
        val customLabel = assertNotNull(custom.findBySourceId("btn_label"))
        assertEquals("Buy now", customLabel.text?.characters)
        assertEquals(DesignColor.fromHex("#FF0000"), customLabel.solidFill(), "override recolors by id path")
        assertEquals("inst_custom/btn_label", customLabel.id, "instance internals get prefixed ids")
    }

    @Test
    fun resolvesVariantFromComponentSet() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_a": { "root": { "id": "a_root", "type": "frame", "fills": [ { "type": "solid", "color": "#0000FF" } ] } },
                "cmp_b": { "root": { "id": "b_root", "type": "frame", "fills": [ { "type": "solid", "color": "#00FF00" } ] } }
              },
              "componentSets": {
                "set_x": {
                  "axes": { "kind": ["a", "b"] },
                  "variants": { "kind=a": "cmp_a", "kind=b": "cmp_b" }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "inst_b", "type": "instance", "componentId": "set_x", "variant": { "kind": "b" } }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val instance = assertNotNull(root.findBySourceId("inst_b"))
        assertEquals(DesignColor.fromHex("#00FF00"), instance.solidFill())
    }

    @Test
    fun missingComponentYieldsPlaceholderAndDiagnostic() {
        val (root, resolver) = resolveFirstFrame(
            """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "ghost", "type": "instance", "componentId": "cmp_missing" }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val ghost = assertNotNull(root.findBySourceId("ghost"))
        assertIs<ResolvedPaint.Unknown>(ghost.fills.first())
        assertTrue(resolver.diagnostics.any { "cmp_missing" in it.message })
    }

    @Test
    fun componentCycleIsTruncatedWithDiagnostic() {
        val (_, resolver) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_loop": {
                  "root": { "id": "loop_root", "type": "frame", "children": [
                    { "id": "inner", "type": "instance", "componentId": "cmp_loop" }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "start", "type": "instance", "componentId": "cmp_loop" }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        assertTrue(resolver.diagnostics.any { "cycle" in it.message.lowercase() })
    }

    @Test
    fun consumerOverrideBeatsTemplateBakedOverrideAtNestedBoundaries() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_label": {
                  "root": { "id": "label_root", "type": "text", "characters": "Default" }
                },
                "cmp_card": {
                  "root": { "id": "card_root", "type": "frame", "children": [
                    { "id": "btn", "type": "instance", "componentId": "cmp_label",
                      "overrides": [
                        { "target": ["label_root"], "set": { "characters": "OK" } }
                      ] }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "card", "type": "instance", "componentId": "cmp_card",
                    "overrides": [
                      { "target": ["btn", "label_root"], "set": { "characters": "Cancel" } }
                    ] }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val label = assertNotNull(root.findBySourceId("btn"), "nested instance root carries the instance sourceId")
        assertEquals("Cancel", label.text?.characters, "consumer override wins over template-baked override")
    }

    @Test
    fun overridesTargetingAnInstanceApplyToItsRoot() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_chip": {
                  "root": { "id": "chip_root", "type": "frame",
                    "cornerRadius": 4,
                    "fills": [ { "type": "solid", "color": "#111111" } ] }
                },
                "cmp_holder": {
                  "root": { "id": "holder_root", "type": "frame", "children": [
                    { "id": "chip", "type": "instance", "componentId": "cmp_chip" }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "holder", "type": "instance", "componentId": "cmp_holder",
                    "overrides": [
                      { "target": ["chip"],
                        "set": { "cornerRadius": 12, "fills": [ { "type": "solid", "color": "#ABCDEF" } ] } }
                    ] }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val chip = assertNotNull(root.findBySourceId("chip"))
        assertEquals(12.0, chip.cornerRadius.topLeft, "cornerRadius override on the instance reaches its root")
        assertEquals(DesignColor.fromHex("#ABCDEF"), chip.solidFill())
    }

    @Test
    fun instanceAuthoredVisualsOverrideComponentRoot() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_tile": {
                  "root": { "id": "tile_root", "type": "frame",
                    "sizing": { "horizontal": "fill", "vertical": "fixed" },
                    "size": { "width": 100, "height": 50 },
                    "cornerRadius": 4,
                    "fills": [ { "type": "solid", "color": "#111111" } ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "t", "type": "instance", "componentId": "cmp_tile",
                    "sizing": { "horizontal": "fixed", "vertical": "fixed" },
                    "cornerRadius": 16,
                    "fills": [ { "type": "solid", "color": "#FF8800" } ] }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val tile = assertNotNull(root.findBySourceId("t"))
        assertEquals(DesignColor.fromHex("#FF8800"), tile.solidFill(), "instance-authored fills win")
        assertEquals(16.0, tile.cornerRadius.topLeft, "instance-authored radius wins")
        assertEquals(
            io.aequicor.visualization.designdoc.domain.model.SizingMode.Fixed,
            tile.sizing.horizontal,
            "instance-authored sizing wins over the component root",
        )
    }

    @Test
    fun instanceInternalsMapSelectionToOutermostInstance() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "components": {
                "cmp_inner": { "root": { "id": "inner_root", "type": "frame", "children": [
                  { "id": "deep", "type": "rectangle", "size": { "width": 10, "height": 10 } }
                ] } },
                "cmp_outer": { "root": { "id": "outer_root", "type": "frame", "children": [
                  { "id": "nested", "type": "instance", "componentId": "cmp_inner" }
                ] } }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "widget", "type": "instance", "componentId": "cmp_outer" }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val deep = assertNotNull(root.findBySourceId("deep"))
        assertEquals("widget", deep.selectableId)
        assertEquals("root", root.selectableId)
    }

    @Test
    fun textStyleMergesSharedStyleAndLocalOverride() {
        val (root, _) = resolveFirstFrame(
            """
            {
              "styles": {
                "sty_h": { "type": "text", "value": { "fontFamily": "Inter", "fontSize": 17, "fontWeight": 600, "lineHeight": { "unit": "percent", "value": 100 } } }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "t", "type": "text", "characters": "Hi",
                    "textStyleId": "sty_h",
                    "textStyle": { "fontSize": 20 } }
                ] }
              ] } ]
            }
            """.trimIndent(),
        )
        val text = assertNotNull(assertNotNull(root.findBySourceId("t")).text)
        assertEquals(20.0, text.style.fontSize, "local field overrides shared style")
        assertEquals(600, text.style.fontWeight, "unset local fields inherit shared style")
        assertEquals(20.0, text.style.lineHeight, "percent line height resolves against final font size")
    }
}
