package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.ContainerKind
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.LayoutMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContainerKindCnlTest {
    private fun source(body: String) = """
        ---
        screen: containerKinds
        sourceLocale: en-US
        targetLocales: [en-US]
        ---

        # Container Kinds
    """.trimIndent() + "\n\n" + body

    @Test
    fun frameAndAutoLayoutRoundTripWithCanonicalHeadings() {
        val first = compileSlm(
            source(
                """
                ## Frame: Canvas id canvas 640 by 480 position 20 20

                Text id title «Free» width 120 height 24 position 32 40

                ## AutoLayout: Stack id stack column gap 12 padding 16

                Text id item «Flow» width (fill) height (hug)
                """.trimIndent(),
            ),
        )
        assertTrue(first.diagnostics.none { it.severity == DesignSeverity.Error }, first.diagnostics.joinToString { it.message })
        val document = assertNotNull(first.document)
        val canvas = assertNotNull(document.nodeById("canvas"))
        val stack = assertNotNull(document.nodeById("stack"))
        assertEquals(ContainerKind.Frame, canvas.containerKind)
        assertEquals(LayoutMode.None, canvas.layout.mode)
        assertEquals(ContainerKind.AutoLayout, stack.containerKind)
        assertEquals(LayoutMode.Vertical, stack.layout.mode)

        val emitted = CnlEmitter.emitDocument(document)
        assertTrue("## Frame:" in emitted, emitted)
        assertTrue("## AutoLayout:" in emitted, emitted)
        val second = compileSlm(emitted)
        assertTrue(second.diagnostics.none { it.severity == DesignSeverity.Error }, second.diagnostics.joinToString { it.message })
        assertEquals(ContainerKind.Frame, second.document?.nodeById("canvas")?.containerKind)
        assertEquals(ContainerKind.AutoLayout, second.document?.nodeById("stack")?.containerKind)
    }

    @Test
    fun semanticContainerRequiresAndEmitsAutoLayoutModifier() {
        val compiled = compileSlm(
            source(
                """
                ## Component: Card id card component-name Card auto-layout row gap 8

                Text id label «Card» width (hug) height (hug)
                """.trimIndent(),
            ),
        )
        assertTrue(compiled.diagnostics.none { it.severity == DesignSeverity.Error }, compiled.diagnostics.joinToString { it.message })
        val root = assertNotNull(compiled.document?.components?.get("card")?.root)
        assertEquals(ContainerKind.AutoLayout, root.containerKind)
        assertTrue("auto-layout row" in CnlEmitter.emitDocument(assertNotNull(compiled.document)))
    }

    @Test
    fun legacySemanticHeadingInfersAutoLayoutWithoutMisreadingNameKeywords() {
        val compiled = compileSlm(
            source(
                """
                ## Media Library Header id media_library_header row gap 8

                Text id label «Media» width (hug) height (hug)

                ## Component: Legacy Card id legacy_card component-name Card column gap 12

                Text id title «Card» width (hug) height (hug)
                """.trimIndent(),
            ),
        )

        assertTrue(compiled.diagnostics.none { it.severity == DesignSeverity.Error }, compiled.diagnostics.joinToString { it.message })
        val header = assertNotNull(compiled.document?.nodeById("media_library_header"))
        assertEquals("Media Library Header", header.name)
        assertTrue(header.kind is DesignNodeKind.Frame)
        assertEquals(ContainerKind.AutoLayout, header.containerKind)
        assertEquals(LayoutMode.Horizontal, header.layout.mode)
        assertEquals(ContainerKind.AutoLayout, assertNotNull(compiled.document).components["legacy_card"]?.root?.containerKind)
    }

    @Test
    fun mismatchedKindsProduceCompileErrors() {
        listOf(
            "## Frame: Legacy row gap 8",
            "## AutoLayout: Missing Direction",
            "## AutoLayout: Free free",
        ).forEach { heading ->
            val result = compileSlm(source(heading))
            assertTrue(
                result.diagnostics.any { it.severity == DesignSeverity.Error },
                "expected a container-kind error for: $heading\n${result.diagnostics.joinToString { it.message }}",
            )
        }
    }
}
