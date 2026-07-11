package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Batch semantics: a failing edit aborts the whole batch (partial diagnostics, no source). */
class SequentialBatchTest {
    @Test
    fun failingEditAbortsTheWholeBatch() {
        val compiled = compileForEdit(SpecRuDocument)
        val result = applySlmEdits(
            SpecRuDocument,
            listOf(
                SetLayoutProperty("topbar", LayoutProp.Gap, YamlScalarValue.Num(12.0)),
                // Paragraph-segment node: never addressable.
                SetText("createMission", "X"),
                SetLayoutProperty("filters", LayoutProp.Gap, YamlScalarValue.Num(8.0)),
            ),
            compiled,
            EditTestOptions,
        )
        assertFalse(result.isApplied)
        assertNull(result.newSource)
        assertNull(result.appliedRange)
        assertTrue(result.diagnostics.any { "no addressable source anchor" in it.message })
    }
}
