package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `override <id> ( … )` authors an `overrides.set` (id-path property patch) in CNL — the last
 * ir-only feature the demos needed. Parse-in and emit must round-trip through IR (S24).
 */
class CnlOverrideSetTest {
    private val frontmatter = """
        ---
        screen: eventLog
        page: Demo
        sourceLocale: en-US
        frame: { preset: desktop-1440, width: 1440, height: 1024 }
        ---
    """.trimIndent()

    private val component = """
        ## Component: Log Row id cmpLogRow component-name «Log Row»

        Ellipse id logDot 12 by 12 color #17C46B
    """.trimIndent()

    private fun compile(src: String): DesignDocument {
        val result = compileSlm(slm(src) + "\n")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document)
    }

    private fun instanceNode(document: DesignDocument, id: String): DesignNode =
        assertNotNull(document.pages.single().allNodes().firstOrNull { it.id == id }, "instance $id")

    @Test
    fun fillOverrideParsesAndRoundTrips() {
        val body = """
            # Event Log

            Instance id row3 of cmpLogRow override logDot (color #FFB800)
        """.trimIndent()
        val document = compile("$frontmatter\n\n$component\n\n$body")

        val instance = instanceNode(document, "row3").kind as DesignNodeKind.Instance
        val override = assertNotNull(instance.overrides.singleOrNull(), "one override")
        assertEquals(listOf("logDot"), override.target)
        val fill = assertNotNull(override.fills?.singleOrNull()) as DesignPaint.Solid
        assertEquals(DesignColor.fromHex("#FFB800"), (fill.color as Bindable.Value).value)

        // emit → recompile → same override.
        val emitted = CnlEmitter.emitSentence(instanceNode(document, "row3"), includeId = true)
        assertTrue("override logDot (color #FFB800)" in emitted, emitted)
        val body2 = "# Event Log\n\n$emitted"
        val document2 = compile("$frontmatter\n\n$component\n\n$body2")
        val override2 = (instanceNode(document2, "row3").kind as DesignNodeKind.Instance).overrides.single()
        assertEquals(override, override2)
    }

    @Test
    fun multiGroupOverrideRoundTrips() {
        val body = """
            # Event Log

            Instance id row5 of cmpLogRow override logDot (color #FF1D1D opacity 0.5 visible no)
        """.trimIndent()
        val document = compile("$frontmatter\n\n$component\n\n$body")
        val override = (instanceNode(document, "row5").kind as DesignNodeKind.Instance).overrides.single()
        assertEquals(listOf("logDot"), override.target)
        assertEquals(0.5, (override.opacity as Bindable.Value).value)
        assertEquals(false, (override.visible as Bindable.Value).value)

        val emitted = CnlEmitter.emitSentence(instanceNode(document, "row5"), includeId = true)
        val document2 = compile("$frontmatter\n\n$component\n\n# Event Log\n\n$emitted")
        assertEquals(override, (instanceNode(document2, "row5").kind as DesignNodeKind.Instance).overrides.single())
    }
}
