package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.ir.serialization.toJsonString
import io.aequicor.visualization.engine.ir.serialization.writeDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * S28 sub-step 4: grid track sizes (`GridTrack.Fixed`/`Flex.value`) and `implicitRowMin` are
 * `Bindable<Double>`, so `$var`/`{{expr}}` bindings survive the JSON round-trip and resolve to
 * concrete literals at the resolver boundary — the pure, scope-less layout engine only ever sees
 * resolved numbers, never a ref.
 */
class BindableGridTrackBindingTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun roundTrip(document: DesignDocument): DesignDocument =
        parse(writeDesignDocument(document).toJsonString())

    private fun DesignDocument.node(id: String) =
        assertNotNull(pages.first().children.firstNotNullOfOrNull { find(it, id) }, "node $id")

    private fun find(
        node: io.aequicor.visualization.engine.ir.model.DesignNode,
        id: String,
    ): io.aequicor.visualization.engine.ir.model.DesignNode? =
        if (node.id == id) node else node.children.firstNotNullOfOrNull { find(it, id) }

    @Test
    fun trackAndImplicitRowMinBindingsSurviveJsonRoundTrip() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "grid", "type": "frame",
                  "layout": {
                    "mode": "grid",
                    "columns": [
                      { "type": "fixed", "value": { "${'$'}var": "col.width" } },
                      { "type": "flex", "value": { "${'$'}var": "col.weight" } }
                    ],
                    "rows": {
                      "auto": { "type": "fixed", "value": 40 },
                      "min": { "${'$'}data": "{{data.rowMin}}" }
                    }
                  } }
              ] } ]
            }
        """.trimIndent()
        val layout = roundTrip(parse(json)).node("grid").layout

        assertEquals(GridTrack.Fixed(Bindable.VarRef("col.width")), layout.columns[0])
        assertEquals(GridTrack.Flex(Bindable.VarRef("col.weight")), layout.columns[1])
        assertEquals(Bindable.DataRef(DesignExpression("data.rowMin")), layout.implicitRowMin)
    }

    @Test
    fun fixedAndFlexTrackTokensResolveIntoLayoutGeometry() {
        val json = """
            {
              "variables": { "collections": { "col": {
                "modes": ["m"], "defaultMode": "m",
                "vars": {
                  "sidebar": { "type": "number", "values": { "m": 200 } },
                  "weight": { "type": "number", "values": { "m": 1 } }
                }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "grid", "type": "frame",
                  "size": { "width": 500, "height": 100 },
                  "layout": {
                    "mode": "grid",
                    "columns": [
                      { "type": "fixed", "value": { "${'$'}var": "sidebar" } },
                      { "type": "flex", "value": { "${'$'}var": "weight" } }
                    ],
                    "gap": { "column": 0, "row": 0 }
                  },
                  "children": [
                    { "id": "a", "type": "rectangle", "sizing": { "horizontal": "fill", "vertical": "fill" } },
                    { "id": "b", "type": "rectangle", "sizing": { "horizontal": "fill", "vertical": "fill" } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val root = layoutFirst(json)

        val a = root.box("a")
        val b = root.box("b")
        assertEquals(0.0, a.x, "fixed \$var track starts at the container origin")
        assertEquals(200.0, a.width, "fixed \$var(=200) resolves to a 200px column, not 0/1")
        assertEquals(200.0, b.x, "flex column begins after the resolved 200px fixed track")
        assertEquals(300.0, b.width, "flex \$var(=1) shares the remaining 300px")
    }

    @Test
    fun implicitRowMinTokenClampsResolvedRowHeight() {
        val json = """
            {
              "variables": { "collections": { "col": {
                "modes": ["m"], "defaultMode": "m",
                "vars": { "rowMin": { "type": "number", "values": { "m": 50 } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "grid", "type": "frame",
                  "sizing": { "horizontal": "hug", "vertical": "hug" },
                  "layout": {
                    "mode": "grid",
                    "columns": [ { "type": "fixed", "value": 100 }, { "type": "fixed", "value": 100 } ],
                    "rows": { "auto": { "type": "fixed", "value": 40 }, "min": { "${'$'}var": "rowMin" } },
                    "gap": { "column": 0, "row": 0 }
                  },
                  "children": [
                    { "id": "a", "type": "rectangle", "size": { "width": 90, "height": 30 } },
                    { "id": "b", "type": "rectangle", "size": { "width": 90, "height": 30 } },
                    { "id": "c", "type": "rectangle", "size": { "width": 90, "height": 30 } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val root = layoutFirst(json)

        assertEquals(0.0, root.box("a").y)
        assertEquals(50.0, root.box("c").y, "min \$var(=50) resolves and clamps the first row before the second starts")
        assertEquals(100.0, root.height, "two implicit rows, each Fixed(40) clamped to the resolved min 50")
    }

    private fun layoutFirst(json: String): LayoutBox {
        val document = parse(json)
        val resolver = DesignResolver(document, ResolveContext())
        val root = assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
        return DesignLayoutEngine().layout(root)
    }

    private fun LayoutBox.box(sourceId: String): LayoutBox =
        assertNotNull(findBySourceId(sourceId), "missing box '$sourceId'")
}
