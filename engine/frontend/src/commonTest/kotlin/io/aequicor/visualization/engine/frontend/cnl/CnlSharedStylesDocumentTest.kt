package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CnlSharedStylesDocumentTest {
    private val frontmatter = """
        ---
        screen: styleDemo
        page: Demo
        sourceLocale: en-US
        ---
    """.trimIndent()

    private val styles = """
        # Styles

        Paint color.surface color #FFFFFF
        TextStyle type.body font Inter size 16 weight 500 line-height 24
        Effect shadow.card effect (dropShadow color #000000 opacity 0.25 offset (0 4) blur 16)
        Grid layout.desktop grids (columns count 12 gutter 16 margin 80)
    """.trimIndent()

    private val body = """
        # Style Demo

        Rectangle id panel 100 by 40 styles (fill color.surface effect shadow.card grid layout.desktop)
        Text id label «Hello» text-style type.body
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
    fun sharedStylesCompileAsDocumentScopeAndResolve() {
        val document = compile("$frontmatter\n\n$styles\n\n$body")

        assertEquals(setOf("color.surface", "type.body", "shadow.card", "layout.desktop"), document.styles.keys)
        assertIs<DesignStyle.Paint>(document.styles.getValue("color.surface"))
        assertIs<DesignStyle.Text>(document.styles.getValue("type.body"))
        assertIs<DesignStyle.Effect>(document.styles.getValue("shadow.card"))
        assertIs<DesignStyle.Grid>(document.styles.getValue("layout.desktop"))

        val visibleNames = document.pages.single().allNodes().map { it.name }
        assertFalse("Styles" in visibleNames)

        val resolver = DesignResolver(document)
        val resolved = resolver.resolvePage(document.pages.single())
        val panel = assertNotNull(findResolved(resolved, "panel"))
        val fill = assertIs<ResolvedPaint.Solid>(panel.fills.single())
        assertEquals(DesignColor.fromHex("#FFFFFF"), fill.color)
        assertEquals(1, panel.effects.size)
        assertEquals(1, panel.layoutGrids.size)
        assertEquals(12, panel.layoutGrids.single().count)

        val label = assertNotNull(findResolved(resolved, "label"))
        val text = assertNotNull(label.text)
        assertEquals("Inter", text.style.fontFamily)
        assertEquals(16.0, text.style.fontSize)
        assertEquals(500, text.style.fontWeight)
        assertTrue(resolver.diagnostics.isEmpty(), resolver.diagnostics.joinToString { it.message })
    }

    @Test
    fun emittedSharedStylesRecompile() {
        val doc1 = compile("$frontmatter\n\n$styles\n\n$body")
        val emitted = CnlEmitter.emitStyles(doc1)
        val doc2 = compile("$frontmatter\n\n$emitted\n\n$body")

        assertEquals(doc1.styles, doc2.styles)
        assertEquals(emitted, CnlEmitter.emitStyles(doc2))
        assertTrue(emitted.contains("# Styles"))
        assertTrue(emitted.contains("Paint color.surface color #FFFFFF"))
        assertTrue(emitted.contains("TextStyle type.body"))
    }
}
