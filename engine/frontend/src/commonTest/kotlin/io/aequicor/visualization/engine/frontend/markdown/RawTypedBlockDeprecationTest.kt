package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase-3 guard: raw YAML typed blocks are no longer an authoring surface. A leading
 * ex-reserved `word:` line compiles to prose with one deprecation warning — the patch
 * is never applied, the document still compiles.
 */
class RawTypedBlockDeprecationTest {

    private fun findNode(root: DesignNode, id: String): DesignNode? {
        if (root.id == id) return root
        return root.children.firstNotNullOfOrNull { findNode(it, id) }
    }

    @Test
    fun rawLayoutBlockIsNotAppliedAndWarns() {
        val source = """
            ---
            screen: guard
            ---

            # Guard

            ## Frame: id card name «Card»

            layout:
              mode: column
              gap: 40
        """.trimIndent()
        val result = compileSlm(source)
        val document = assertNotNull(result.document, "document must still compile")
        val card = assertNotNull(findNode(document.pages.single().children.single(), "card"))
        assertEquals(
            LayoutMode.None,
            card.layout.mode,
            "the raw `layout:` block must not patch the node — defaults kept",
        )
        assertTrue(
            result.diagnostics.any {
                it.severity == DesignSeverity.Warning &&
                    "Raw YAML typed blocks are no longer supported" in it.message &&
                    "`layout:`" in it.message
            },
            "expected one deprecation warning, got: ${result.diagnostics}",
        )
        assertTrue(result.diagnostics.none { it.severity == DesignSeverity.Error })
    }

    @Test
    fun consecutiveRawBlocksWarnPerBlockKey() {
        val source = """
            ---
            screen: guard
            ---

            # Guard

            ## Frame: id card name «Card»

            node:
              visible: false
            style:
              radius: 8
        """.trimIndent()
        val result = compileSlm(source)
        val document = assertNotNull(result.document)
        val card = assertNotNull(findNode(document.pages.single().children.single(), "card"))
        assertEquals(true.bindable(), card.visible, "the raw `node:` block must not patch visibility")
        val warnings = result.diagnostics.filter {
            "Raw YAML typed blocks are no longer supported" in it.message
        }
        assertEquals(2, warnings.size, "one warning per ex-reserved key line: $warnings")
    }
}
