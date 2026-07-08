package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.semantics.SpecRuDocument
import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** One resize on the spec's full RU document: everything else is byte-identical. */
class LosslessRuDocumentTest {
    @Test
    fun singleResizeKeepsEveryByteOutsideAppliedRange() {
        val compiled = compileForEdit(SpecRuDocument)
        val result = applySlmEdit(
            SpecRuDocument,
            SetSizing("topbar", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
            compiled,
        )
        val new = result.requireNewSource()
        val range = assertNotNull(result.appliedRange)

        // Frontmatter, prose, conditions and the card list survive untouched.
        assertLosslessOutside(SpecRuDocument, new, range)
        // Paragraph anchor: a separating blank line, then the nested block form.
        assertEquals(
            "\nlayout:\n  sizing:\n    width:\n      type: fixed\n      value: 320\n",
            new.substring(range.startOffset, range.endOffset),
        )

        // The patched document still compiles to the same tree shape.
        val recompiled = compileForEdit(new)
        assertEquals(
            listOf("topbar", "filters", "emptyState", "missions"),
            recompiled.requireDocument().pages.single().children.single().children.map { it.id },
        )
    }
}
