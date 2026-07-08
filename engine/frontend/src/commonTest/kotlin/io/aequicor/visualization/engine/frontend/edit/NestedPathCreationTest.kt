package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Case 2: missing suffix paths grow after the deepest existing prefix. */
class NestedPathCreationTest {
    @Test
    fun deepPathGrowsInsideLayoutWithOnlyMode() {
        val doc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Panel
            layout:
              mode: row
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetSizing("panel", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(
            doc.replace(
                "layout:\n  mode: row\n",
                "layout:\n  mode: row\n  sizing:\n    width:\n      type: fixed\n      value: 320\n",
            ),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun deepPathGrowsInsideEmptyLayout() {
        val doc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Panel
            layout:

            Some plain prose text.
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetSizing("panel", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(
            doc.replace(
                "layout:\n",
                "layout:\n  sizing:\n    width:\n      type: fixed\n      value: 320\n",
            ),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }
}
