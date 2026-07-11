package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.edit.LayoutProp
import io.aequicor.visualization.engine.frontend.edit.RenameNode
import io.aequicor.visualization.engine.frontend.edit.SetCornerRadii
import io.aequicor.visualization.engine.frontend.edit.SetFills
import io.aequicor.visualization.engine.frontend.edit.SetLayoutProperty
import io.aequicor.visualization.engine.frontend.edit.SetSizing
import io.aequicor.visualization.engine.frontend.edit.SetText
import io.aequicor.visualization.engine.frontend.edit.SetTextStyle
import io.aequicor.visualization.engine.frontend.edit.SizingSpec
import io.aequicor.visualization.engine.frontend.edit.YamlScalarValue
import io.aequicor.visualization.engine.frontend.edit.applySlmEdit
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Editor edits on a CNL-authored node patch the sentence in place (surgical write-back). */
class CnlWriteBackTest {
    private fun screen(body: String): String = slm(
        """
        ---
        screen: demo
        sourceLocale: en-US
        targetLocales:
          - en-US
        ---

        # Demo

        $body
        """,
    ) + "\n"

    private fun compiledAndId(source: String, kind: (DesignNodeKind) -> Boolean): Pair<SlmCompileResult, String> {
        val result = compileSlm(source)
        val root = assertNotNull(result.document).pages.single().children.single()
        val node = root.allDescendants().first { kind(it.kind) }
        return result to node.id
    }

    @Test
    fun editFillReplacesColorInPlace() {
        val source = screen("Rectangle 120 by 15 color #00B843 radius 15 gap 16")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Shape }
        val result = applySlmEdit(source, SetFills(id, listOf(DesignPaint.Solid(DesignColor(0xFFFF0000).bindable()))), compiled)
        val next = assertNotNull(result.newSource)
        assertTrue("color #FF0000" in next, next)
        assertFalse("#00B843" in next)
        assertTrue("Rectangle 120 by 15 color #FF0000 radius 15 gap 16" in next, next)
    }

    @Test
    fun editRadiusAndGapReplaceInPlace() {
        val source = screen("Rectangle 120 by 15 color #00B843 radius 15 gap 16")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Shape }
        val radius = applySlmEdit(source, SetCornerRadii(id, DesignCornerRadius.all(8.0.bindable())), compiled)
        assertTrue("radius 8" in assertNotNull(radius.newSource))
        val gap = applySlmEdit(source, SetLayoutProperty(id, LayoutProp.Gap, YamlScalarValue.Num(24.0)), compiled)
        assertTrue("gap 24" in assertNotNull(gap.newSource))
    }

    @Test
    fun editResizeReplacesBothDimensions() {
        val source = screen("Rectangle 120 by 15 color #00B843")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Shape }
        val edit = SetSizing(id, SizingSpec(SizingMode.Fixed, 200.0), SizingSpec(SizingMode.Fixed, 40.0))
        val next = assertNotNull(applySlmEdit(source, edit, compiled).newSource)
        assertTrue("200 by 40" in next, next)
    }

    @Test
    fun missingPropertyIsAppendedAsPhrase() {
        val source = screen("Rectangle 120 by 15 color #00B843")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Shape }
        val next = assertNotNull(applySlmEdit(source, SetCornerRadii(id, DesignCornerRadius.all(12.0.bindable())), compiled).newSource)
        assertTrue("Rectangle 120 by 15 color #00B843 radius 12" in next, next)
    }

    @Test
    fun editTextReplacesLiteral() {
        val source = screen("Text «Hello» size 20")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Text }
        val next = assertNotNull(applySlmEdit(source, SetText(id, "Bye"), compiled).newSource)
        assertTrue("Text «Bye» size 20" in next, next)
        assertFalse("Hello" in next)
    }

    @Test
    fun editTextStyleInHeadingCnlReplacesInPlace() {
        val source = screen("## Text: id label characters «Hello» name «Hello» width hug height hug size 12 key demo.label bold font «Inter»")
        val compiled = compileSlm(source)
        val edit = SetTextStyle(
            "label",
            DesignTextStyle(
                fontFamily = "Inter",
                fontSize = 20.0.bindable(),
                fontWeight = 700.0.bindable(),
                lineHeight = UnitValue(DesignUnit.Percent, 140.0),
                letterSpacing = UnitValue(DesignUnit.Px, 1.5),
                textAlignHorizontal = TextAlignHorizontal.Center,
            ),
        )

        val next = assertNotNull(applySlmEdit(source, edit, compiled).newSource)

        assertTrue("size 20" in next, next)
        assertTrue("line-height 140%" in next, next)
        assertTrue("tracking 1.5" in next, next)
        assertTrue("text-align center" in next, next)
        assertTrue("font «Inter»" in next, next)
        assertFalse("fontSize:" in next, next)
        assertFalse("lineHeight:" in next, next)
        assertFalse("letterSpacing:" in next, next)
    }

    @Test
    fun editKeepsEverythingElseByteForByte() {
        val source = screen("Rectangle 120 by 15 color #00B843 radius 15 gap 16")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Shape }
        val next = assertNotNull(applySlmEdit(source, SetFills(id, listOf(DesignPaint.Solid(DesignColor(0xFF112233).bindable()))), compiled).newSource)
        assertEquals(source.replace("#00B843", "#112233"), next)
    }

    @Test
    fun nonSurgicalEditReemitsSentenceInsteadOfYamlFallback() {
        // RenameNode is not surgically expressible in a CNL sentence → tier-3 re-emit from the
        // patched node. It must regenerate the sentence in CNL, never append a YAML typed block.
        val source = screen("## Frame: Card id card column gap 12 color #FFFFFF")
        val compiled = compileSlm(source)
        val node = assertNotNull(compiled.document).pages.single().children.single()
            .allDescendants().first { it.id == "card" }
        val result = applySlmEdit(source, RenameNode("card", "Panel"), compiled, node.copy(name = "Panel"))
        val next = assertNotNull(result.newSource, "tier-3 re-emit produced a new source")
        assertTrue("name «Panel»" in next, next)
        // No YAML typed-block fallback leaked in.
        assertFalse(Regex("""(?m)^\s*node:\s*$""").containsMatchIn(next), next)
        assertFalse("name: Panel" in next, next)
        // Recompiles clean with the rename applied.
        val recompiled = compileSlm(next)
        assertTrue(recompiled.diagnostics.none { it.severity == io.aequicor.visualization.engine.ir.model.DesignSeverity.Error })
        assertEquals("Panel", assertNotNull(recompiled.document).pages.single().children.single()
            .allDescendants().first { it.id == "card" }.name)
    }

    @Test
    fun cnlNodeEditWithoutPatchedNodeFailsInsteadOfYaml() {
        // Without a patched node, tier-3 is unavailable; a non-surgical edit must fail (→ in-memory
        // fallback in the reducer), NOT silently append a YAML typed block to the CNL sentence.
        val source = screen("## Frame: Card id card column gap 12 color #FFFFFF")
        val compiled = compileSlm(source)
        val result = applySlmEdit(source, RenameNode("card", "Panel"), compiled)
        assertNull(result.newSource, "no patched node → no write-back, no YAML leak")
    }

    @Test
    fun editTextEscapesClosingGuillemet() {
        val source = screen("Text «Hello» size 20")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Text }
        val next = assertNotNull(applySlmEdit(source, SetText(id, "a » b"), compiled).newSource)
        assertTrue("""Text «a \» b» size 20""" in next, next)
        val reparsed = compileSlm(next)
        val node = assertNotNull(reparsed.document).pages.single().children.single()
            .allDescendants().first { it.kind is DesignNodeKind.Text }
        assertEquals("a » b", (node.kind as DesignNodeKind.Text).content?.defaultText)
    }

    @Test
    fun editFontFamilyQuotesBareTokenValue() {
        // A bare-token font value span cannot absorb a multi-word family; the writer must
        // rewrite it as a «…» literal, not splice raw words into the sentence.
        val source = screen("Text «Hello» size 20 font Inter")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Text }
        val edit = SetTextStyle(id, DesignTextStyle(fontFamily = "SF Pro"))
        val next = assertNotNull(applySlmEdit(source, edit, compiled).newSource)
        assertTrue("font «SF Pro»" in next, next)
        val reparsed = compileSlm(next)
        assertTrue(reparsed.diagnostics.none { it.severity == io.aequicor.visualization.engine.ir.model.DesignSeverity.Error })
        val node = assertNotNull(reparsed.document).pages.single().children.single()
            .allDescendants().first { it.kind is DesignNodeKind.Text }
        assertEquals("SF Pro", (node.kind as DesignNodeKind.Text).textStyle?.fontFamily)
    }

    @Test
    fun editFontFamilyReplacesInsideQuotedLiteral() {
        val source = screen("Text «Hello» size 20 font «Inter»")
        val (compiled, id) = compiledAndId(source) { it is DesignNodeKind.Text }
        val edit = SetTextStyle(id, DesignTextStyle(fontFamily = "SF Pro"))
        val next = assertNotNull(applySlmEdit(source, edit, compiled).newSource)
        assertTrue("font «SF Pro»" in next, next)
        assertFalse("««" in next, next)
    }
}
