package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesignJsonParserTest {

    @Test
    fun parsesDocumentStructureWithBindingsAndAutoLayout() {
        val result = parseDesignDocument(
            """
            {
              "schemaVersion": "1.0.0",
              "id": "doc_test",
              "name": "Test",
              "variables": {
                "collections": {
                  "col": {
                    "modes": ["light", "dark"],
                    "defaultMode": "light",
                    "vars": {
                      "var_surface": { "type": "color", "values": { "light": "#FFFFFF", "dark": "#111827" } },
                      "var_gap": { "type": "number", "values": { "light": 12, "dark": 12 } }
                    }
                  }
                }
              },
              "pages": [
                {
                  "id": "page_1",
                  "name": "Page",
                  "children": [
                    {
                      "id": "frame_root", "type": "frame",
                      "position": { "x": 72, "y": 72 },
                      "size": { "width": 400, "height": 300 },
                      "sizing": { "horizontal": "fixed", "vertical": "hug" },
                      "layout": { "mode": "vertical", "gap": { "${'$'}var": "var_gap" },
                                  "padding": { "top": 16, "right": 16, "bottom": 16, "left": 16 },
                                  "alignItems": "stretch", "clipsContent": true },
                      "fills": [ { "type": "solid", "color": { "${'$'}var": "var_surface" } } ],
                      "cornerRadius": 8,
                      "children": [
                        { "id": "row", "type": "frame",
                          "layout": { "mode": "horizontal", "gap": "auto" },
                          "sizing": { "horizontal": "fill", "vertical": "hug" } },
                        { "id": "label", "type": "text",
                          "characters": "Hello",
                          "textStyle": { "fontSize": 15, "fontWeight": 600, "lineHeight": { "unit": "percent", "value": 140 } },
                          "autoResize": "widthAndHeight" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val success = assertIs<DesignParseResult.Success>(result)
        assertEquals(0, success.diagnostics.size, "expected no diagnostics: ${success.diagnostics}")
        val document = success.document
        assertEquals("doc_test", document.id)
        val root = assertNotNull(document.nodeById("frame_root"))
        assertEquals(LayoutMode.Vertical, root.layout.mode)
        assertEquals(SizingMode.Hug, root.sizing?.vertical)
        assertIs<Bindable.VarRef>((root.layout.gap as DesignGap.Fixed).value)
        assertEquals(true, root.layout.clipsContent)
        val fill = assertIs<DesignPaint.Solid>(root.fills.orEmpty().first())
        assertIs<Bindable.VarRef>(fill.color)
        assertEquals(8.0, (root.cornerRadius?.topLeft as Bindable.Value<Double>).value)

        val row = assertNotNull(document.nodeById("row"))
        assertIs<DesignGap.Auto>(row.layout.gap)
        assertEquals(SizingMode.Fill, row.sizing?.horizontal)

        val label = assertNotNull(document.nodeById("label"))
        val text = assertIs<DesignNodeKind.Text>(label.kind)
        assertEquals("Hello", (text.characters as Bindable.Value<String>).value)
        assertEquals(600.0, (text.textStyle?.fontWeight as Bindable.Value<Double>).value)
    }

    @Test
    fun unknownNodeTypesAndEnumValuesFallBackWithWarnings() {
        val result = parseDesignDocument(
            """
            {
              "pages": [
                { "id": "p", "children": [
                    { "id": "weird", "type": "hologram",
                      "layout": { "mode": "orbital" } }
                ] }
              ]
            }
            """.trimIndent(),
        )
        val success = assertIs<DesignParseResult.Success>(result)
        val node = assertNotNull(success.document.nodeById("weird"))
        assertIs<DesignNodeKind.Unknown>(node.kind)
        assertEquals(LayoutMode.None, node.layout.mode)
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "hologram" in it.message })
        assertTrue(success.diagnostics.any { it.severity == DesignSeverity.Warning && "orbital" in it.message })
    }

    @Test
    fun malformedJsonFails() {
        val result = parseDesignDocument("{ not json ")
        val failure = assertIs<DesignParseResult.Failure>(result)
        assertTrue(failure.diagnostics.any { it.severity == DesignSeverity.Error })
    }

    @Test
    fun nonObjectRootFails() {
        val result = parseDesignDocument("[1, 2, 3]")
        assertIs<DesignParseResult.Failure>(result)
    }

    @Test
    fun parsesInstanceWithPropsVariantAndOverrides() {
        val result = parseDesignDocument(
            """
            {
              "pages": [
                { "id": "p", "children": [
                  { "id": "inst", "type": "instance",
                    "componentId": "set_button",
                    "variant": { "kind": "primary" },
                    "props": { "label": "Buy", "iconOn": true, "count": 3 },
                    "overrides": [
                      { "target": ["btn_label"],
                        "set": { "characters": "Add", "fills": [ { "type": "solid", "color": "#FF0000" } ] } }
                    ] }
                ] }
              ]
            }
            """.trimIndent(),
        )
        val success = assertIs<DesignParseResult.Success>(result)
        val node = assertNotNull(success.document.nodeById("inst"))
        val instance = assertIs<DesignNodeKind.Instance>(node.kind)
        assertEquals("set_button", (instance.componentId as Bindable.Value<String>).value)
        assertEquals(mapOf("kind" to "primary"), instance.variant)
        assertEquals(3, instance.props.size)
        val override = instance.overrides.single()
        assertEquals(listOf("btn_label"), override.target)
        assertEquals("Add", (override.characters as Bindable.Value<String>).value)
        assertEquals(1, override.fills?.size)
    }

    @Test
    fun parsesGridLayoutTracksAndPlacement() {
        val result = parseDesignDocument(
            """
            {
              "pages": [
                { "id": "p", "children": [
                  { "id": "grid", "type": "frame",
                    "layout": { "mode": "grid",
                                "columns": [ { "type": "fixed", "value": 56 }, { "type": "flex", "value": 1 }, { "type": "hug" } ],
                                "rows": [ { "type": "hug" } ],
                                "gap": { "column": 12, "row": 8 } },
                    "children": [
                      { "id": "cell", "type": "rectangle",
                        "gridPlacement": { "column": 2, "row": 1, "columnSpan": 1, "rowSpan": 2 } }
                    ] }
                ] }
              ]
            }
            """.trimIndent(),
        )
        val success = assertIs<DesignParseResult.Success>(result)
        val grid = assertNotNull(success.document.nodeById("grid"))
        assertEquals(LayoutMode.Grid, grid.layout.mode)
        assertEquals(3, grid.layout.columns.size)
        assertEquals(12.0, (grid.layout.columnGap as Bindable.Value<Double>).value)
        assertEquals(8.0, (grid.layout.rowGap as Bindable.Value<Double>).value)
        val cell = assertNotNull(success.document.nodeById("cell"))
        assertEquals(2, cell.gridPlacement?.column)
        assertEquals(2, cell.gridPlacement?.rowSpan)
    }
}
