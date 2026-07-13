package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CnlFullDocumentEmitTest {
    private val source = """
        ---
        screen: fullDemo
        page: Demo
        sourceLocale: en-US
        targetLocales: [en-US, fr-FR]
        theme: light
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        breakpoints:
          - id: desktop
            minWidth: 1024
        libraries:
          - id: ds
            source: "@company/design-system"
        ---

        # Collection theme (modes light dark default light)

        Color color.surface light #FFFFFF dark #101114
        Number radius.card light 8 dark 8

        # Styles

        Paint surface.card color ${'$'}color.surface
        TextStyle type.body font Inter size 16 weight 500

        # Full Demo column gap 24 padding 48 styles (fill surface.card) auto-layout

        Instance id cardOne of cmpCard props (label «Telemetry nominal»)
        Text id status «Online» text-style type.body

        ## Component: Card id cmpCard component-name Card prop label (text default «Event») color #F8FAFC radius 8

        Text id cardLabel characters ${'$'}prop.label text-style type.body
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
    fun fullDocumentEmitRecompilesAllDocumentScopesAndRefs() {
        val doc1 = compile(source)
        val emitted = CnlEmitter.emitDocument(doc1)
        val doc2 = compile(emitted)

        assertEquals(doc1.screen, doc2.screen)
        assertEquals(doc1.i18n.sourceLocale, doc2.i18n.sourceLocale)
        assertEquals(doc1.i18n.targetLocales, doc2.i18n.targetLocales)
        assertEquals(doc1.breakpoints, doc2.breakpoints)
        assertEquals(doc1.libraries, doc2.libraries)
        assertEquals(doc1.variables, doc2.variables)
        assertEquals(doc1.prototypeVariables, doc2.prototypeVariables)
        assertEquals(doc1.styles, doc2.styles)
        assertEquals(doc1.components.keys, doc2.components.keys)
        assertEquals(doc1.components.mapValues { it.value.properties }, doc2.components.mapValues { it.value.properties })

        val ids1 = doc1.pages.flatMap { it.allNodes() }.map { it.id }
        val ids2 = doc2.pages.flatMap { it.allNodes() }.map { it.id }
        assertEquals(ids1, ids2)

        val resolver = DesignResolver(doc2)
        val resolved = resolver.resolvePage(doc2.pages.single())
        val root = assertNotNull(findResolved(resolved, "fullDemo"))
        val fill = assertIs<ResolvedPaint.Solid>(root.fills.single())
        assertEquals(DesignColor.fromHex("#FFFFFF"), fill.color)
        val status = assertNotNull(findResolved(resolved, "status"))
        assertEquals("Inter", assertNotNull(status.text).style.fontFamily)
        val label = assertNotNull(findResolved(resolved, "cardOne/cardLabel"))
        assertEquals("Telemetry nominal", assertNotNull(label.text).characters)
        assertTrue(resolver.diagnostics.isEmpty(), resolver.diagnostics.joinToString { it.message })
    }

    @Test
    fun fullDocumentEmitIsIdempotent() {
        val doc1 = compile(source)
        val once = CnlEmitter.emitDocument(doc1)
        val doc2 = compile(once)
        assertEquals(once, CnlEmitter.emitDocument(doc2))
    }
}
