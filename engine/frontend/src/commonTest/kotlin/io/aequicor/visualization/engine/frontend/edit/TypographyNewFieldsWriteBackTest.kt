package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.UnitValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Write-back of the typography subsystem's new [DesignTextStyle] fields via [SetTextStyle]:
 * [TypographyYamlWriter] appends each non-null field under an existing `typography:` block
 * with the canonical key/token, the recompiled node carries them back, and every byte
 * outside the applied range is identical.
 */
class TypographyNewFieldsWriteBackTest {

    private fun DesignNode.textStyle(): DesignTextStyle =
        assertNotNull(assertIs<DesignNodeKind.Text>(kind).textStyle, "text node carries a resolved textStyle")

    @Test
    fun appendsNewTypographyFieldsAndRoundTrips() {
        val edit = SetTextStyle(
            "label",
            DesignTextStyle(
                italic = true,
                paragraphIndent = 12.0,
                textCase = TextCase.SmallCaps,
                decorationStyle = TextDecorationStyle.Dashed,
                decorationColor = DesignColor.fromHex("#3366FF"),
                decorationThickness = UnitValue(DesignUnit.Px, 2.0),
                decorationSkipInk = false,
                textPosition = TextScriptPosition.Superscript,
                leadingTrim = LeadingTrim.CapHeight,
                hangingPunctuation = true,
                hangingList = true,
            ),
        )

        val compiled = compileForEdit(DOC)
        val result = applySlmEdit(DOC, edit, compiled)
        val new = result.requireNewSource()

        // Each new field lands under its canonical authoring key; the sibling fontSize survives.
        assertTrue("fontSize: 12" in new, "untouched sibling key survives: $new")
        listOf(
            "italic: true",
            "paragraphIndent: 12",
            "case: smallCaps",
            "decorationStyle: dashed",
            "decorationColor: \"#3366FF\"",
            "decorationThickness: 2",
            "decorationSkipInk: false",
            "position: superscript",
            "leadingTrim: capHeight",
            "hangingPunctuation: true",
            "hangingList: true",
        ).forEach { line -> assertTrue(line in new, "expected `$line` in:\n$new") }
        assertLosslessOutside(DOC, new, assertNotNull(result.appliedRange))

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals(true, style.italic)
        assertEquals(12.0, style.paragraphIndent)
        assertEquals(TextCase.SmallCaps, style.textCase)
        assertEquals(TextDecorationStyle.Dashed, style.decorationStyle)
        assertEquals(DesignColor.fromHex("#3366FF"), style.decorationColor)
        assertEquals(UnitValue(DesignUnit.Px, 2.0), style.decorationThickness)
        assertEquals(false, style.decorationSkipInk)
        assertEquals(TextScriptPosition.Superscript, style.textPosition)
        assertEquals(LeadingTrim.CapHeight, style.leadingTrim)
        assertEquals(true, style.hangingPunctuation)
        assertEquals(true, style.hangingList)
    }

    /** A percent `decorationThickness` renders as an explicit `{ unit: percent, value }` map. */
    @Test
    fun percentDecorationThicknessRendersAsMap() {
        val edit = SetTextStyle(
            "label",
            DesignTextStyle(decorationThickness = UnitValue(DesignUnit.Percent, 8.0)),
        )
        val new = applySlmEdit(DOC, edit, compileForEdit(DOC)).requireNewSource()
        assertTrue("unit: percent" in new, "percent thickness is a map: $new")

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals(UnitValue(DesignUnit.Percent, 8.0), style.decorationThickness)
    }

    /** `TextCase.SmallCapsForced` round-trips through the canonical token. */
    @Test
    fun smallCapsForcedRoundTrips() {
        val edit = SetTextStyle("label", DesignTextStyle(textCase = TextCase.SmallCapsForced))
        val new = applySlmEdit(DOC, edit, compileForEdit(DOC)).requireNewSource()
        assertTrue("case: smallCapsForced" in new, "canonical token written: $new")

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals(TextCase.SmallCapsForced, style.textCase)
        // Sanity: the new decoration kind still round-trips alongside case.
        assertEquals(TextDecorationKind.None, style.textDecoration ?: TextDecorationKind.None)
    }

    private companion object {
        private val DOC = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Text: Title
            node:
              type: text
              id: label
              name: Title
            text:
              key: screen.title
              defaultText: Hello
              typography:
                fontSize: 12
            style:
              fills:
                - color: "#101010"
        """.trimIndent() + "\n"
    }
}
