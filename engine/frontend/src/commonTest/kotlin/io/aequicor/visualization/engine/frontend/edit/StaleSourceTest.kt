package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fingerprint gate hard-fails on any drift between source and compile. */
class StaleSourceTest {
    @Test
    fun editedSourceIsRejectedAgainstOldCompile() {
        val compiled = compileForEdit(SpecRuDocument)
        val tampered = SpecRuDocument + "\n"
        val result = applySlmEdit(
            tampered,
            SetLayoutProperty("topbar", LayoutProp.Gap, YamlScalarValue.Num(12.0)),
            compiled,
        )
        assertFalse(result.isApplied)
        assertTrue(result.diagnostics.single().message.startsWith("Stale compile result"))
    }

    @Test
    fun batchIsRejectedBeforeAnyEditApplies() {
        val compiled = compileForEdit(SpecRuDocument)
        val tampered = "$SpecRuDocument\n"
        val result = applySlmEdits(
            tampered,
            listOf(SetLayoutProperty("topbar", LayoutProp.Gap, YamlScalarValue.Num(12.0))),
            compiled,
            EditTestOptions,
        )
        assertFalse(result.isApplied)
        assertTrue(result.diagnostics.single().message.startsWith("Stale compile result"))
    }
}
