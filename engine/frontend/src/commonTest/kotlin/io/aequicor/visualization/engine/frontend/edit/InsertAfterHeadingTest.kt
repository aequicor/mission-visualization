package io.aequicor.visualization.engine.frontend.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Case 3: brand-new typed blocks after heading anchors and the screen root. */
class InsertAfterHeadingTest {
    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen

        ## Panel

        Some plain prose text.
    """.trimIndent() + "\n"

    @Test
    fun insertsDirectlyAfterHeadingBeforeFollowingParagraph() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetLayoutProperty("panel", LayoutProp.Gap, YamlScalarValue.Num(12.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(doc.replace("## Panel\n", "## Panel\nlayout:\n  gap: 12\n"), new)
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun appendsIntoExistingGroupKeepingItContiguous() {
        val grouped = doc.replace("## Panel\n", "## Panel\nlayout:\n  mode: row\n")
        val compiled = compileForEdit(grouped)
        val result = applySlmEdit(
            grouped,
            SetStyleProperty("panel", StyleProp.Radius, YamlScalarValue.Num(8.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(
            grouped.replace(
                "layout:\n  mode: row\n",
                "layout:\n  mode: row\nstyle:\n  radius: 8\n",
            ),
            new,
        )
        assertLosslessOutside(grouped, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun rootAnchoredBlockInsertsAfterH1() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetLayoutProperty("s", LayoutProp.Gap, YamlScalarValue.Num(24.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(doc.replace("# Screen\n", "# Screen\nlayout:\n  gap: 24\n"), new)
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }
}
