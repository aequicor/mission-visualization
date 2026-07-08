package io.aequicor.visualization.engine.frontend.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Case 3 for list-item anchors: typed blocks at the item content column. */
class InsertIntoListItemTest {
    private val doc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen

        ## Panel

        - First item
        - Second item
          - Nested entry
        - Third item
          layout:
            gap: 4
    """.trimIndent() + "\n"

    @Test
    fun insertsAtItemContentColumnTwo() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, SetText(compiled.textNodeId("First item"), "Hello"), compiled)
        val new = result.requireNewSource()
        assertEquals(
            doc.replace("- First item\n", "- First item\n  text:\n    defaultText: Hello\n"),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun insertsAtNestedItemContentColumnFour() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, SetText(compiled.textNodeId("Nested entry"), "Bye"), compiled)
        val new = result.requireNewSource()
        assertEquals(
            doc.replace("  - Nested entry\n", "  - Nested entry\n    text:\n      defaultText: Bye\n"),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun extendsItemBoundEntryInPlace() {
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetLayoutProperty(
                compiled.textNodeId("Third item"),
                LayoutProp.Mode,
                YamlScalarValue.Str("row"),
            ),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(
            doc.replace("  layout:\n    gap: 4\n", "  layout:\n    gap: 4\n    mode: row\n"),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }
}
