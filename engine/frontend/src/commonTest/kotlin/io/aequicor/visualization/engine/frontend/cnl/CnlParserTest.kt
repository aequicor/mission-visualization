package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.ShapePatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.subsystems.figures.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CnlParserTest {
    private data class Parsed(
        val element: CnlElement,
        val patches: List<TypedPatch>,
        val diagnostics: DiagnosticCollector,
    ) {
        inline fun <reified T> patch(): T? = patches.filterIsInstance<T>().firstOrNull()
    }

    private fun parse(line: String): Parsed {
        val diagnostics = DiagnosticCollector("test.layout.md")
        val element = CnlParser.parseElement(line, lineNumber = 1, baseColumn = 1, diagnostics)
            ?: error("\"$line\" was not recognized as a CNL element")
        val patches = CnlParser.desugar(element, line = 1, diagnostics).map { it.patch }
        return Parsed(element, patches, diagnostics)
    }

    @Test
    fun rectangleSentenceCompilesToPatches() {
        val parsed = parse("Rectangle 120 by 15 color #00B843 radius 15 padding 10 gap 16")
        assertTrue(
            parsed.diagnostics.diagnostics.isEmpty(),
            parsed.diagnostics.diagnostics.joinToString { it.message },
        )
        assertEquals("shape", parsed.patch<NodePatch>()?.type)
        assertEquals(ShapeType.Rectangle, parsed.patch<ShapePatch>()?.kind)
        val layout = assertNotNull(parsed.patch<LayoutPatch>())
        assertEquals(SizingMode.Fixed, layout.sizingWidth?.mode)
        assertEquals(120.0, layout.sizingWidth?.value)
        assertEquals(15.0, layout.sizingHeight?.value)
        assertEquals(DesignGap.Fixed(16.0.bindable()), layout.gap)
        assertEquals(10.0.bindable(), layout.paddingBlockStart)
        assertEquals(10.0.bindable(), layout.paddingInlineEnd)
        val style = assertNotNull(parsed.patch<StylePatch>())
        assertEquals(listOf(DesignPaint.Solid(DesignColor(0xFF00B843).bindable())), style.fills)
        assertEquals(15.0.bindable(), style.radius?.topLeft)
    }

    @Test
    fun sizeConnectorVariantsAreEquivalent() {
        val parsed = parse("rectangle 120 x 15 color #00B843 gap 16")
        assertEquals("shape", parsed.patch<NodePatch>()?.type)
        assertEquals(ShapeType.Rectangle, parsed.patch<ShapePatch>()?.kind)
        assertEquals(120.0, parsed.patch<LayoutPatch>()?.sizingWidth?.value)
        assertEquals(DesignGap.Fixed(16.0.bindable()), parsed.patch<LayoutPatch>()?.gap)
        assertEquals(
            listOf(DesignPaint.Solid(DesignColor(0xFF00B843).bindable())),
            parsed.patch<StylePatch>()?.fills,
        )
    }

    @Test
    fun rotationInDegrees() {
        val parsed = parse("Rectangle 10 by 10 rotation 30 degrees")
        assertEquals(30.0, parsed.patch<NodePatch>()?.rotation)
    }

    @Test
    fun textElementCarriesLiteralAndTypography() {
        val parsed = parse("Text «Active missions» size 24 bold")
        assertEquals("Active missions", parsed.element.textLiteral?.raw)
        assertEquals("text", parsed.patch<NodePatch>()?.type)
        assertNotNull(parsed.patch<TextPatch>()?.typography)
        assertEquals(24.0.bindable(), parsed.patch<TextPatch>()?.typography?.fontSize)
    }

    @Test
    fun explicitIdIsCarriedToNodePatch() {
        val parsed = parse("Rectangle id hero_card 10 by 10 color #1E293B")
        assertEquals("hero_card", parsed.patch<NodePatch>()?.id)
    }

    @Test
    fun unquotedHexColorIsAccepted() {
        val parsed = parse("Rectangle 10 by 10 color #1E293B")
        assertEquals(
            listOf(DesignPaint.Solid(DesignColor(0xFF1E293B).bindable())),
            parsed.patch<StylePatch>()?.fills,
        )
    }

    @Test
    fun unknownWordReportsSelfExplainingRule() {
        val parsed = parse("Rectangle 10 by 20 blabla")
        val message = parsed.diagnostics.diagnostics.singleOrNull { "unknown-keyword" in it.message }
        assertNotNull(message, "expected an unknown-keyword diagnostic")
        assertTrue("How to fix" in message.message, message.message)
    }

    @Test
    fun badColorReportsRule() {
        val parsed = parse("Rectangle 10 by 10 color blue")
        assertTrue(parsed.diagnostics.diagnostics.any { "bad-color" in it.message })
    }

    @Test
    fun nounLedProseIsNotHijacked() {
        val diagnostics = DiagnosticCollector("test.layout.md")
        // No number, no quoted text, no keyword → this stays prose, not a CNL element.
        assertNull(CnlParser.parseElement("Text updated yesterday", 1, 1, diagnostics))
        assertTrue(diagnostics.diagnostics.isEmpty())
    }
}
