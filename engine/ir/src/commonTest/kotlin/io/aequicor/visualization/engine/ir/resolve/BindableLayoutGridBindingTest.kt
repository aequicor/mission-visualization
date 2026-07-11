package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.ir.serialization.toJsonString
import io.aequicor.visualization.engine.ir.serialization.writeDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * S28 final sub-step: a `LayoutGridDefinition` overlay is bindable — `count` via the new
 * `Bindable<Int>` lane, and `size`/`gutter`/`margin` via `Bindable<Double>`. Because the overlay
 * has no layout effect it is otherwise never resolved, so [DesignResolver.resolveLayoutGrid] is the
 * boundary that makes those bindings LIVE: a `$var`/`{{expr}}` in an overlay slot evaluates to a
 * concrete literal rather than surviving as a dead ref.
 */
class BindableLayoutGridBindingTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun roundTrip(document: DesignDocument): DesignDocument =
        parse(writeDesignDocument(document).toJsonString())

    private fun DesignDocument.node(id: String): DesignNode =
        assertNotNull(pages.first().children.firstNotNullOfOrNull { find(it, id) }, "node $id")

    private fun find(node: DesignNode, id: String): DesignNode? =
        if (node.id == id) node else node.children.firstNotNullOfOrNull { find(it, id) }

    private fun resolvedGrid(document: DesignDocument, id: String): LayoutGridDefinition {
        val resolver = DesignResolver(document, ResolveContext())
        val roots = resolver.resolvePage(document.pages.first())
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
        val node = assertNotNull(roots.firstNotNullOfOrNull { findResolved(it, id) }, "resolved node $id")
        return node.layoutGrids.single()
    }

    private fun findResolved(node: ResolvedNode, sourceId: String): ResolvedNode? =
        if (node.sourceId == sourceId) node else node.children.firstNotNullOfOrNull { findResolved(it, sourceId) }

    /** (a) The Int lane resolves a `$var(=3)` count to the literal 3; a literal count still works. */
    @Test
    fun countVarResolvesToLiteralInt() {
        val json = """
            {
              "variables": { "collections": { "g": {
                "modes": ["m"], "defaultMode": "m",
                "vars": { "columns": { "type": "number", "values": { "m": 3 } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "bound", "type": "frame",
                  "layoutGrids": [ { "type": "columns", "count": { "${'$'}var": "columns" } } ] },
                { "id": "plain", "type": "frame",
                  "layoutGrids": [ { "type": "columns", "count": 12 } ] }
              ] } ]
            }
        """.trimIndent()
        val document = parse(json)

        val bound = resolvedGrid(document, "bound")
        assertIs<Bindable.Value<Int>>(bound.count, "overlay \$var count must not stay a dead ref")
        assertEquals(3, bound.count?.literalOrNull(), "\$var(=3) count resolves to the literal 3")

        val plain = resolvedGrid(document, "plain")
        assertEquals(12, plain.count?.literalOrNull(), "a literal count still resolves unchanged")
    }

    /** (b) The overlay binding is LIVE: `$var` count AND size resolve to their values, not refs. */
    @Test
    fun overlayCountAndSizeBindingsAreLive() {
        val json = """
            {
              "variables": { "collections": { "g": {
                "modes": ["m"], "defaultMode": "m",
                "vars": {
                  "columns": { "type": "number", "values": { "m": 8 } },
                  "cell": { "type": "number", "values": { "m": 64 } }
                }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "board", "type": "frame",
                  "layoutGrids": [ { "type": "columns",
                    "count": { "${'$'}var": "columns" },
                    "size": { "${'$'}var": "cell" } } ] }
              ] } ]
            }
        """.trimIndent()
        val grid = resolvedGrid(parse(json), "board")

        // Proving the binding is not dead: the resolved overlay carries the LITERAL, never the ref.
        assertIs<Bindable.Value<Int>>(grid.count)
        assertIs<Bindable.Value<Double>>(grid.size)
        assertEquals(8, grid.count?.literalOrNull())
        assertEquals(64.0, grid.size?.literalOrNull())
    }

    /** (c) `$var` and `{{expr}}` in count, size, gutter and margin all survive the JSON round-trip. */
    @Test
    fun allOverlaySlotBindingsSurviveJsonRoundTrip() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "board", "type": "frame",
                  "layoutGrids": [ { "type": "columns",
                    "count": { "${'$'}var": "gcolumns" },
                    "size": { "${'$'}data": "{{data.cell}}" },
                    "gutter": { "${'$'}var": "g.gutter" },
                    "margin": { "${'$'}data": "{{data.margin}}" } } ] }
              ] } ]
            }
        """.trimIndent()
        val grid = roundTrip(parse(json)).node("board").layoutGrids.single()

        assertEquals(Bindable.VarRef("gcolumns"), grid.count)
        assertEquals(Bindable.DataRef(DesignExpression("data.cell")), grid.size)
        assertEquals(Bindable.VarRef("g.gutter"), grid.gutter)
        assertEquals(Bindable.DataRef(DesignExpression("data.margin")), grid.margin)
    }
}
