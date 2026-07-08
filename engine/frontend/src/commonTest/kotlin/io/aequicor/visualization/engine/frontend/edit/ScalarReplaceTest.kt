package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.SizingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Case 1: in-place scalar replacement, golden full-string assertions. */
class ScalarReplaceTest {
    private val numericDoc = """
        ---
        screen: s
        sourceLocale: en-US
        ---

        # Screen

        ## Panel
        layout:
          mode: row
          gap: 12   # main gap
    """.trimIndent() + "\n"

    private val tokenDoc = numericDoc.replace("gap: 12   # main gap", "gap: ${'$'}spacing.md   # main gap")

    @Test
    fun replacesNumberPreservingTrailingCommentAndAlignment() {
        val compiled = compileForEdit(numericDoc)
        val result = applySlmEdit(
            numericDoc,
            SetLayoutProperty("panel", LayoutProp.Gap, YamlScalarValue.Num(20.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(numericDoc.replace("gap: 12   # main gap", "gap: 20   # main gap"), new)
        assertLosslessOutside(numericDoc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun replacesNumberWithTokenRef() {
        val compiled = compileForEdit(numericDoc)
        val result = applySlmEdit(
            numericDoc,
            SetLayoutProperty("panel", LayoutProp.Gap, YamlScalarValue.TokenRef("spacing.md")),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(tokenDoc, new)
        assertLosslessOutside(numericDoc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun replacesTokenRefWithNumber() {
        val compiled = compileForEdit(tokenDoc)
        val result = applySlmEdit(
            tokenDoc,
            SetLayoutProperty("panel", LayoutProp.Gap, YamlScalarValue.Num(16.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(tokenDoc.replace("gap: ${'$'}spacing.md   # main gap", "gap: 16   # main gap"), new)
        assertLosslessOutside(tokenDoc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun keepsExistingQuoteStyleForPlainReplacement() {
        val doc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Panel
            text:
              defaultText: "Hello"
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, SetText("panel", "Bye"), compiled)
        val new = result.requireNewSource()
        // "Bye" is plain-safe, but the double quotes of the original survive.
        assertEquals(doc.replace("defaultText: \"Hello\"", "defaultText: \"Bye\""), new)
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }

    @Test
    fun upgradesSizingShorthandToInlineMap() {
        val doc = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Panel
            layout:
              sizing:
                width: fill
        """.trimIndent() + "\n"
        val compiled = compileForEdit(doc)
        val result = applySlmEdit(
            doc,
            SetSizing("panel", width = SizingSpec(SizingMode.Fixed, value = 320.0)),
            compiled,
        )
        val new = result.requireNewSource()
        assertEquals(doc.replace("width: fill", "width: { type: fixed, value: 320 }"), new)
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))
    }
}
