package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CnlComponentsDocumentTest {
    private val frontmatter = """
        ---
        screen: wireBoard
        page: Demo
        sourceLocale: en-US
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        ---
    """.trimIndent()

    private val components = """
        ## Component: Wire Tile id cmpWireTile component-name WireTile set cmpWireTileSet axis kind (default highlight) variant (kind default) color #FFFFFF

        ## Component: Wire Tile id cmpWireTileHighlight component-name WireTile set cmpWireTileSet axis kind (default highlight) variant (kind highlight) color #E97155

        ## Component: Log Row id cmpLogRow component-name «Log Row» prop label (text default «Event») prop time (text default «00:00») row auto-layout

        Text id logLabel characters ${'$'}prop.label
        Text id logTime characters ${'$'}prop.time
    """.trimIndent()

    private val body = """
        # Wire Board

        ## Board id board

        Instance id activeTile of WireTile variant (kind highlight)
        Instance id firstLogRow of cmpLogRow props (label «Uplink window opened» time «09:52»)
    """.trimIndent()

    private fun compile(src: String): DesignDocument {
        val result = compileSlm(slm(src) + "\n")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document)
    }

    private fun findResolved(nodes: List<ResolvedNode>, id: String): ResolvedNode? =
        nodes.firstNotNullOfOrNull { node ->
            if (node.id == id) node else findResolved(node.children, id)
        }

    @Test
    fun componentDefinitionsCompileAndLiftOutOfVisibleTree() {
        val document = compile("$frontmatter\n\n$body\n\n$components")

        assertEquals(setOf("cmpWireTile", "cmpWireTileHighlight", "cmpLogRow"), document.components.keys)
        assertEquals("component", document.components.getValue("cmpWireTile").root.type)
        assertEquals(
            mapOf("kind" to listOf("default", "highlight")),
            document.componentSets.getValue("cmpWireTileSet").axes,
        )
        assertEquals(
            mapOf(
                "kind=default" to "cmpWireTile",
                "kind=highlight" to "cmpWireTileHighlight",
            ),
            document.componentSets.getValue("cmpWireTileSet").variants,
        )
        assertEquals(
            mapOf(
                "label" to ComponentPropertyDefinition(
                    type = ComponentPropertyType.Text,
                    default = PropValue.Content(
                        TextContent(
                            key = "components.logRow.label",
                            defaultLocale = "en-US",
                            defaultText = "Event",
                        ),
                    ),
                ),
                "time" to ComponentPropertyDefinition(
                    type = ComponentPropertyType.Text,
                    default = PropValue.Content(
                        TextContent(
                            key = "components.logRow.time",
                            defaultLocale = "en-US",
                            defaultText = "00:00",
                        ),
                    ),
                ),
            ),
            document.components.getValue("cmpLogRow").properties,
        )

        val visibleNames = document.pages.single().allNodes().map { it.name }
        assertFalse(visibleNames.any { it == "Wire Tile" || it == "Log Row" })

        val activeTile = assertNotNull(document.nodeById("activeTile"))
        val activeKind = assertIs<DesignNodeKind.Instance>(activeTile.kind)
        assertEquals(Bindable.Value("cmpWireTileSet"), activeKind.componentId)

        val logRow = assertNotNull(document.nodeById("firstLogRow"))
        val logKind = assertIs<DesignNodeKind.Instance>(logRow.kind)
        assertEquals(Bindable.Value("cmpLogRow"), logKind.componentId)
        assertEquals(
            mapOf(
                "label" to PropValue.Text("Uplink window opened"),
                "time" to PropValue.Text("09:52"),
            ),
            logKind.props,
        )
    }

    @Test
    fun emittedComponentsRecompileAndResolveVariant() {
        val doc1 = compile("$frontmatter\n\n$body\n\n$components")
        val emitted = CnlEmitter.emitComponents(doc1)
        val doc2 = compile("$frontmatter\n\n$body\n\n$emitted")

        assertEquals(doc1.components.keys, doc2.components.keys)
        assertEquals(doc1.componentSets, doc2.componentSets)
        assertEquals(doc1.components.mapValues { it.value.properties }, doc2.components.mapValues { it.value.properties })
        assertEquals(emitted, CnlEmitter.emitComponents(doc2))

        val resolver = DesignResolver(doc2)
        val resolved = resolver.resolvePage(doc2.pages.single())
        val tile = assertNotNull(findResolved(resolved, "activeTile"))
        val fill = assertIs<ResolvedPaint.Solid>(tile.fills.single())
        assertEquals(DesignColor.fromHex("#E97155"), fill.color)
        assertTrue(resolver.diagnostics.isEmpty(), resolver.diagnostics.joinToString { it.message })
    }
}
