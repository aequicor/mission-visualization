package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Whole-body regeneration: compile a multi-section CNL screen into IR, emit the CNL body
 * back with [CnlEmitter.emitDocumentBody], recompile, and assert the node tree (ids, types, and the
 * authored per-node fields) survives. This is the end-to-end "generate" capability the demo
 * migration (PM) is built on — the same emitter, but over a full heading tree instead of one node.
 */
class CnlDocumentRoundTripTest {
    private val frontmatter = """
        ---
        screen: demo
        sourceLocale: en-US
        targetLocales: [en-US]
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        ---
    """.trimIndent()

    private val body = """
        # Mission Overview column gap 24 padding 48 color #0B1220 auto-layout

        ## AutoLayout: Header row gap 12 padding 16 color #111827 radius 12

        Text «Active missions» size 20 bold color #F8FAFC
        Text «12 in progress» size 14 color #94A3B8
        Button «Create mission» size 16 bold color #22C55E

        ## AutoLayout: Metrics grid columns (count 3 track 1fr) gap (row 16 column 16) padding 24

        Rectangle 200 by 80 color #1E293B radius 8 stroke (color #334155 weight 1)
        Rectangle 200 by 80 gradient (linear from (0 0) to (0 1) stops (#4F46E5 at 0) (#9333EA at 1)) radius 8
        Ellipse 40 by 40 color #2563EB effect (dropShadow color #00000040 offset (0 2) blur 8)
    """.trimIndent()

    private fun compile(src: String): DesignDocument {
        val result = compileSlm(slm(src) + "\n")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document)
    }

    @Test
    fun wholeScreenRegeneratesFaithfully() {
        val doc1 = compile("$frontmatter\n\n$body")
        val emittedBody = CnlEmitter.emitDocumentBody(doc1)
        val doc2 = compile("$frontmatter\n\n$emittedBody")

        val nodes1 = doc1.pages.flatMap { it.allNodes() }
        val nodes2 = doc2.pages.flatMap { it.allNodes() }
        assertEquals(nodes1.map { it.id }, nodes2.map { it.id }, "node id set / order")
        assertEquals(nodes1.size, nodes2.size, "node count")

        nodes1.zip(nodes2).forEach { (a, b) ->
            assertEquals(a.type, b.type, "type of ${a.id}")
            assertEquals(a.name, b.name, "name of ${a.id}")
            assertEquals(a.fills, b.fills, "fills of ${a.id}")
            assertEquals(a.strokes, b.strokes, "strokes of ${a.id}")
            assertEquals(a.effects, b.effects, "effects of ${a.id}")
            assertEquals(a.cornerRadius, b.cornerRadius, "radius of ${a.id}")
            assertEquals(a.size, b.size, "size of ${a.id}")
            assertEquals(a.layout.mode, b.layout.mode, "mode of ${a.id}")
            assertEquals(a.layout.gap, b.layout.gap, "gap of ${a.id}")
            assertEquals(a.layout.columns, b.layout.columns, "columns of ${a.id}")
        }
    }

    @Test
    fun emitDocumentIsIdempotent() {
        val doc1 = compile("$frontmatter\n\n$body")
        val once = CnlEmitter.emitDocumentBody(doc1)
        val doc2 = compile("$frontmatter\n\n$once")
        assertEquals(once, CnlEmitter.emitDocumentBody(doc2), "emitDocumentBody is a fixed point")
    }

    @Test
    fun mixedContainerAndLeafSiblingsRegenerateFaithfully() {
        val mixedBody = """
            # Mission Overview

            ## Frame: Parent id parent

            ### Frame: Child Frame id child

            ### Text: After Child id after characters «After child»
        """.trimIndent()
        val doc1 = compile("$frontmatter\n\n$mixedBody")
        val doc2 = compile("$frontmatter\n\n${CnlEmitter.emitDocumentBody(doc1)}")

        val parent1 = assertNotNull(doc1.pages.single().allNodes().firstOrNull { it.id == "parent" })
        val parent2 = assertNotNull(doc2.pages.single().allNodes().firstOrNull { it.id == "parent" })
        assertEquals(listOf("child", "after"), parent1.children.map { it.id })
        assertEquals(parent1.children.map { it.id }, parent2.children.map { it.id })
    }
}
