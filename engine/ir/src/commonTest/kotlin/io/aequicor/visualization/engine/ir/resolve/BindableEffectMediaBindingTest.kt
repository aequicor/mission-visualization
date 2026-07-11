package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import io.aequicor.visualization.engine.ir.serialization.toJsonString
import io.aequicor.visualization.engine.ir.serialization.writeDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * S28a/S28c: effect blur/spread/radius and media assetId/posterAssetId are `Bindable`, so `$var`
 * and `{{expr}}` bindings survive the JSON round-trip and resolve to concrete values at runtime.
 */
class BindableEffectMediaBindingTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun roundTrip(document: DesignDocument): DesignDocument =
        parse(writeDesignDocument(document).toJsonString())

    private fun DesignDocument.node(id: String) =
        assertNotNull(pages.first().children.firstNotNullOfOrNull { find(it, id) }, "node $id")

    private fun find(node: io.aequicor.visualization.engine.ir.model.DesignNode, id: String): io.aequicor.visualization.engine.ir.model.DesignNode? =
        if (node.id == id) node else node.children.firstNotNullOfOrNull { find(it, id) }

    private val bindingJson = """
        {
          "assets": { "hero_img": { "type": "image", "url": "https://cdn/hero.png" } },
          "pages": [ { "id": "p", "children": [
            { "id": "card", "type": "frame",
              "effects": [
                { "type": "dropShadow", "color": "#00000040",
                  "blur": { "${'$'}var": "var_blur" },
                  "spread": { "${'$'}data": "{{data.spread}}" } },
                { "type": "layerBlur", "radius": { "${'$'}var": "var_blur" } }
              ] },
            { "id": "hero", "type": "media",
              "media": {
                "assetId": { "${'$'}var": "var_asset" },
                "kind": "video",
                "posterAssetId": { "${'$'}data": "{{data.thumb}}" }
              } }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun bindingsSurviveJsonRoundTrip() {
        val document = roundTrip(parse(bindingJson))

        val effects = document.node("card").effects
        val shadow = assertIs<DesignEffect.DropShadow>(effects[0])
        assertEquals(Bindable.VarRef("var_blur"), shadow.blur)
        assertEquals(Bindable.DataRef(DesignExpression("data.spread")), shadow.spread)
        val layerBlur = assertIs<DesignEffect.LayerBlur>(effects[1])
        assertEquals(Bindable.VarRef("var_blur"), layerBlur.radius)

        val media = assertIs<DesignNodeKind.Media>(document.node("hero").kind).media
        assertEquals(Bindable.VarRef("var_asset"), media.assetId)
        assertEquals(Bindable.DataRef(DesignExpression("data.thumb")), media.posterAssetId)
    }

    @Test
    fun tokenBackedBlurAndAssetResolveToConcreteValues() {
        val json = """
            {
              "variables": { "collections": { "col": {
                "modes": ["m"], "defaultMode": "m",
                "vars": {
                  "var_blur": { "type": "number", "values": { "m": 24 } },
                  "var_asset": { "type": "string", "values": { "m": "hero_img" } }
                }
              } } },
              "assets": { "hero_img": { "type": "image", "url": "https://cdn/hero.png" } },
              "pages": [ { "id": "p", "children": [
                { "id": "card", "type": "frame",
                  "effects": [ { "type": "layerBlur", "radius": { "${'$'}var": "var_blur" } } ] },
                { "id": "hero", "type": "media",
                  "media": { "assetId": { "${'$'}var": "var_asset" } } }
              ] } ]
            }
        """.trimIndent()
        val document = parse(json)
        val resolver = DesignResolver(document, ResolveContext())
        val roots = resolver.resolvePage(document.pages.first())

        val card = assertNotNull(roots.firstNotNullOfOrNull { findResolved(it, "card") })
        val layerBlur = assertIs<ResolvedEffect.LayerBlur>(card.effects.single())
        assertEquals(24.0, layerBlur.radius, "blur \$var resolves to a concrete px value")

        val hero = assertNotNull(roots.firstNotNullOfOrNull { findResolved(it, "hero") })
        val media = assertNotNull(hero.media)
        assertEquals("hero_img", media.assetId, "media assetId \$var resolves to the asset key")
        assertEquals("https://cdn/hero.png", media.url, "the resolved key looks up the asset table")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    private fun findResolved(node: ResolvedNode, sourceId: String): ResolvedNode? =
        if (node.sourceId == sourceId) node else node.children.firstNotNullOfOrNull { findResolved(it, sourceId) }

    @Test
    fun pointBindingSurvivesJsonRoundTrip() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "dot", "type": "frame",
                  "position": { "x": { "${'$'}var": "px" }, "y": 5 } }
              ] } ]
            }
        """.trimIndent()
        val position = assertNotNull(roundTrip(parse(json)).node("dot").position)
        assertEquals(Bindable.VarRef("px"), position.x)
        assertEquals(Bindable.Value(5.0), position.y)
    }
}
