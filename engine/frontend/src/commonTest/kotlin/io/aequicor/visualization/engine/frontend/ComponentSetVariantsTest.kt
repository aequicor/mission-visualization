package io.aequicor.visualization.engine.frontend

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end: multiple `## Component:` definitions sharing one `component.name`
 * compile into a single variant-bearing `DesignComponentSet`, and an instance
 * selecting a non-default combination expands the matching definition root.
 */
class ComponentSetVariantsTest {
    private val source = slm(
        """
        ---
        screen: wireBoard
        page: Demo
        sourceLocale: en-US
        frame:
          preset: desktop-1440
          width: 1440
          height: 1024
        ---

        # Wire Board

        ## Component: Wire Tile id cmpWireTile component-name WireTile set cmpWireTileSet axis kind (default highlight) variant (kind default) color #FFFFFF

        ## Component: Wire Tile id cmpWireTileHighlight component-name WireTile set cmpWireTileSet axis kind (default highlight) variant (kind highlight) color #E97155

        ## Board id board

        Instance id activeTile of WireTile variant (kind highlight)
        """,
    ) + "\n"

    private fun findResolved(nodes: List<ResolvedNode>, id: String): ResolvedNode? =
        nodes.firstNotNullOfOrNull { node ->
            if (node.id == id) node else findResolved(node.children, id)
        }

    @Test
    fun twoDefinitionsBuildOneVariantBearingSet() {
        val result = compileSlm(source)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        val document = assertNotNull(result.document)
        assertEquals(setOf("cmpWireTile", "cmpWireTileHighlight"), document.components.keys)
        val set = document.componentSets.getValue("cmpWireTileSet")
        assertEquals("WireTile", set.name)
        assertEquals(mapOf("kind" to listOf("default", "highlight")), set.axes)
        assertEquals(
            mapOf(
                "kind=default" to "cmpWireTile",
                "kind=highlight" to "cmpWireTileHighlight",
            ),
            set.variants,
        )

        // The name ref rewrote to the SET id, so the resolver selects per variant.
        val instanceNode = assertNotNull(document.nodeById("activeTile"))
        val kind = assertIs<DesignNodeKind.Instance>(instanceNode.kind)
        assertEquals(Bindable.Value("cmpWireTileSet"), kind.componentId)
        assertEquals(mapOf("kind" to "highlight"), kind.variant)
    }

    @Test
    fun instanceSelectingHighlightExpandsTheHighlightRoot() {
        val document = assertNotNull(compileSlm(source).document)
        val resolver = DesignResolver(document)
        val resolved = resolver.resolvePage(document.pages.single())

        val tile = assertNotNull(findResolved(resolved, "activeTile"))
        val fill = assertIs<ResolvedPaint.Solid>(tile.fills.single())
        assertEquals(DesignColor.fromHex("#E97155"), fill.color)
        assertTrue(
            resolver.diagnostics.isEmpty(),
            resolver.diagnostics.joinToString { it.message },
        )
    }

    private val soloSource = slm(
        """
        ---
        screen: soloBoard
        page: Demo
        sourceLocale: en-US
        frame:
          preset: desktop-1440
          width: 1440
          height: 1024
        ---

        # Solo Board

        ## Component: Solo Tile id cmpSoloTile component-name SoloTile set cmpSoloTileSet axis kind (default highlight) color #143A66

        ## Board id board

        Instance id defaultTile of cmpSoloTileSet variant (kind default)
        """,
    ) + "\n"

    @Test
    fun soloDefinitionWithAxesCompilesWithInfoAndResolvesDefaultCombination() {
        val result = compileSlm(soloSource)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.joinToString { it.message },
        )
        // The info diagnostic (surfaced as a warning) flags the default-only set.
        assertTrue(result.diagnostics.any { "defines only the default variant" in it.message })

        val document = assertNotNull(result.document)
        assertEquals(
            mapOf("kind=default" to "cmpSoloTile"),
            document.componentSets.getValue("cmpSoloTileSet").variants,
        )

        val resolver = DesignResolver(document)
        val resolved = resolver.resolvePage(document.pages.single())
        val tile = assertNotNull(findResolved(resolved, "defaultTile"))
        val fill = assertIs<ResolvedPaint.Solid>(tile.fills.single())
        assertEquals(DesignColor.fromHex("#143A66"), fill.color)
        assertTrue(
            resolver.diagnostics.isEmpty(),
            resolver.diagnostics.joinToString { it.message },
        )
    }
}
