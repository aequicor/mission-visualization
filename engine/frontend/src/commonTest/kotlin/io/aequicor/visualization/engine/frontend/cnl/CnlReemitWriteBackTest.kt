package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.edit.SetNodeRotation
import io.aequicor.visualization.engine.frontend.edit.SetText
import io.aequicor.visualization.engine.frontend.edit.SetTextStyle
import io.aequicor.visualization.engine.frontend.edit.applySlmEdit
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip coverage for CNL write-backs that Block C left running solely through
 * [io.aequicor.visualization.engine.frontend.edit.CnlWriter] (the legacy YAML writers are
 * gone): [SetNodeRotation] and OpenType `features`/variable `axes` regenerate the whole CNL
 * sentence from the patched node (tier-3), and [SetText] surgically rewrites the `«…»` literal
 * and regenerates the i18n bundle under the node's unchanged key. Each test drives the public
 * [applySlmEdit] path — passing the fully patched node exactly as the reducer's write-back does
 * — and asserts the edit persists to source and survives recompile.
 */
class CnlReemitWriteBackTest {
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

    private fun compiled(source: String): SlmCompileResult = compileSlm(source)

    private fun SlmCompileResult.node(id: String): DesignNode =
        assertNotNull(document).pages.single().children.single().allDescendants().first { it.id == id }

    private fun SlmCompileResult.textNodeId(): String =
        assertNotNull(document).pages.single().children.single()
            .allDescendants().first { it.kind is DesignNodeKind.Text }.id

    private fun SlmCompileResult.assertClean() =
        assertTrue(diagnostics.none { it.severity == DesignSeverity.Error }, diagnostics.joinToString { it.message })

    // --- SetNodeRotation: rotation N phrase round-trips via tier-3 re-emit ---

    @Test
    fun rotationRoundTripsAndPreservesSiblingPhrases() {
        val source = screen("## Frame: Card id card column gap 12 color #FFFFFF")
        val result = compiled(source)
        val patched = result.node("card").copy(rotation = 45.0)
        val next = assertNotNull(applySlmEdit(source, SetNodeRotation("card", 45.0), result, patched).newSource)

        assertTrue("rotation 45" in next, next)
        // The whole-sentence re-emit keeps every other authored phrase.
        assertTrue("id card" in next, next)
        assertTrue("name «Card»" in next, next)
        assertTrue("gap 12" in next, next)
        assertTrue("color #FFFFFF" in next, next)
        // No YAML typed-block fallback leaked in.
        assertFalse(Regex("""(?m)^\s*position:\s*$""").containsMatchIn(next), next)

        val recompiled = compiled(next)
        recompiled.assertClean()
        assertEquals(45.0, recompiled.node("card").rotation)
    }

    @Test
    fun rotationWithoutPatchedNodeStaysInMemory() {
        // SetNodeRotation is not surgically expressible in a CNL sentence; without a patched node
        // tier-3 is unavailable, so it must fail cleanly (→ in-memory fallback), never a YAML splice.
        val source = screen("## Frame: Card id card column gap 12 color #FFFFFF")
        assertNull(applySlmEdit(source, SetNodeRotation("card", 45.0), compiled(source)).newSource)
    }

    // --- SetTextStyle: OpenType features + variable axes round-trip via tier-3 re-emit ---

    private fun textNode(result: SlmCompileResult, id: String): DesignNodeKind.Text =
        result.node(id).kind as DesignNodeKind.Text

    @Test
    fun openTypeFeaturesAndVariableAxesRoundTrip() {
        val source = screen(
            "## Text: id label characters «Hello» name «Hello» width hug height hug size 12 key demo.label bold font «Inter»",
        )
        val result = compiled(source)
        val text = textNode(result, "label")
        val style = (text.textStyle ?: DesignTextStyle()).copy(
            fontFeatures = mapOf("liga" to true),
            variableAxes = mapOf("wght" to 620.0),
        )
        val patched = result.node("label").copy(kind = text.copy(textStyle = style))
        val next = assertNotNull(applySlmEdit(source, SetTextStyle("label", style), result, patched).newSource)

        assertTrue("features (liga on)" in next, next)
        assertTrue("axes (wght 620)" in next, next)
        // Existing typography survives the re-emit.
        assertTrue("size 12" in next, next)
        assertTrue("font «Inter»" in next, next)

        val recompiled = compiled(next)
        recompiled.assertClean()
        val roundTripped = assertNotNull(textNode(recompiled, "label").textStyle)
        assertEquals(true, roundTripped.fontFeatures["liga"])
        assertEquals(620.0, roundTripped.variableAxes["wght"])
        assertEquals(12.0, roundTripped.fontSize?.literalOrNull())
        assertEquals("Inter", roundTripped.fontFamily)
    }

    @Test
    fun changingAnAuthoredFeatureReplacesTheWholeGroup() {
        // A feature toggled off in the source must flip on — tier-3 re-emit rewrites the whole
        // `features (…)` group from the patched node rather than leaving the stale token behind.
        val source = screen(
            "## Text: id label characters «Hello» name «Hello» width hug height hug size 12 features (liga off)",
        )
        val result = compiled(source)
        assertEquals(false, assertNotNull(textNode(result, "label").textStyle).fontFeatures["liga"])
        val text = textNode(result, "label")
        val style = (text.textStyle ?: DesignTextStyle()).copy(fontFeatures = mapOf("liga" to true))
        val patched = result.node("label").copy(kind = text.copy(textStyle = style))
        val next = assertNotNull(applySlmEdit(source, SetTextStyle("label", style), result, patched).newSource)

        assertTrue("features (liga on)" in next, next)
        assertFalse("liga off" in next, next)
        val recompiled = compiled(next)
        recompiled.assertClean()
        assertEquals(true, assertNotNull(textNode(recompiled, "label").textStyle).fontFeatures["liga"])
    }

    @Test
    fun featuresWithoutPatchedNodeStayInMemory() {
        // No patched node → tier-3 unavailable; the features edit fails cleanly instead of
        // silently dropping the axes/features (which the surgical tiers cannot express).
        val source = screen(
            "## Text: id label characters «Hello» name «Hello» width hug height hug size 12",
        )
        val edit = SetTextStyle("label", DesignTextStyle(fontFeatures = mapOf("liga" to true)))
        assertNull(applySlmEdit(source, edit, compiled(source)).newSource)
    }

    // --- SetText: i18n bundle regenerates under the unchanged key ---

    @Test
    fun setTextRewritesLiteralAndRegeneratesBundleUnderSameKey() {
        val source = screen("Text «Hello» key greeting.title size 20")
        val result = compiled(source)
        val textId = result.textNodeId()
        val before = textNode(result, textId)
        assertEquals("greeting.title", before.content?.key)
        assertEquals("Hello", before.content?.defaultText)
        assertEquals("Hello", assertNotNull(result.resources[SlmLocale("en-US")])["greeting.title"])

        val next = assertNotNull(applySlmEdit(source, SetText(textId, "Bye"), result).newSource)
        assertTrue("Text «Bye» key greeting.title size 20" in next, next)
        assertFalse("Hello" in next, next)

        val recompiled = compiled(next)
        recompiled.assertClean()
        // Re-find by kind: this inline node has no explicit id, so its content-derived id shifts
        // with the text — the invariant under test is the stable i18n key, not the node id.
        val after = textNode(recompiled, recompiled.textNodeId())
        // Only the default text changed; the authored key is untouched and the bundle regenerates.
        assertEquals("greeting.title", after.content?.key)
        assertEquals("Bye", after.content?.defaultText)
        assertEquals("Bye", assertNotNull(recompiled.resources[SlmLocale("en-US")])["greeting.title"])
    }
}
