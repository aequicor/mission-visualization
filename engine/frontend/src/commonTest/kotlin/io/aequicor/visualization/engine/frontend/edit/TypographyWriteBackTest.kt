package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Typography write-back ([SetTextStyle]): a [DesignTextStyle] serializes into the node's
 * `text.typography` block and round-trips faithfully — existing keys are replaced in place,
 * new keys appended, a scalar `lineHeight` upgrades to a percent map, and a px value onto
 * an authored percent map fails cleanly so the caller can fall back in-memory. Every write
 * leaves the bytes outside the edit range identical.
 */
class TypographyWriteBackTest {

    /** A text node with a `typography:` block; [typographyBody] is its 4-space-indented fields. */
    private fun docWith(typographyBody: String): String =
        HEAD + typographyBody.trimEnd('\n') + "\n" + TAIL

    private fun recompiledLabel(source: String, edit: SlmEdit): DesignNode {
        val compiled = compileForEdit(source)
        val newSource = applySlmEdit(source, edit, compiled).requireNewSource()
        val recompiled = compileForEdit(newSource)
        assertTrue(recompiled.isSuccess, recompiled.diagnostics.joinToString { it.message })
        return recompiled.requireDocument().requireNode("label")
    }

    private fun DesignNode.textStyle(): DesignTextStyle =
        assertNotNull(assertIs<DesignNodeKind.Text>(kind).textStyle, "text node carries a resolved textStyle")

    @Test
    fun replacesExistingScalarsInPlace() {
        val doc = docWith(
            """
            |    fontFamily: Inter
            |    fontSize: 12
            |    fontWeight: 700
            """.trimMargin(),
        )
        val edit = SetTextStyle("label", DesignTextStyle(fontSize = 18.0.bindable(), fontWeight = 600.0.bindable()))

        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, edit, compiled)
        val new = result.requireNewSource()
        assertEquals(
            doc.replace("fontSize: 12", "fontSize: 18").replace("fontWeight: 700", "fontWeight: 600"),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals("Inter", style.fontFamily, "untouched fontFamily survives")
        assertEquals(18.0.bindable(), style.fontSize)
        assertEquals(600.0.bindable(), style.fontWeight)
    }

    @Test
    fun writesTokenFontWeightAsRef() {
        val doc = docWith(
            """
            |    fontSize: 12
            |    fontWeight: 700
            """.trimMargin(),
        )
        val edit = SetTextStyle("label", DesignTextStyle(fontWeight = Bindable.VarRef("weight.bold")))
        val style = recompiledLabel(doc, edit).textStyle()
        assertEquals(Bindable.VarRef("weight.bold"), style.fontWeight)
    }

    @Test
    fun appendsPxLetterSpacingAsBareNumber() {
        val doc = docWith("    fontSize: 12")
        val edit = SetTextStyle("label", DesignTextStyle(letterSpacing = UnitValue(DesignUnit.Px, 2.0)))

        val compiled = compileForEdit(doc)
        val new = applySlmEdit(doc, edit, compiled).requireNewSource()
        assertTrue("letterSpacing: 2" in new, "px letterSpacing renders bare, not as a map: $new")
        assertFalse("unit:" in new, "no percent map emitted for a px value")

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals(UnitValue(DesignUnit.Px, 2.0), style.letterSpacing)
        assertEquals(12.0.bindable(), style.fontSize, "sibling typography key survives")
    }

    @Test
    fun writesPercentLineHeightAsMap() {
        val doc = docWith("    fontSize: 12")
        val edit = SetTextStyle("label", DesignTextStyle(lineHeight = UnitValue(DesignUnit.Percent, 135.0)))
        val style = recompiledLabel(doc, edit).textStyle()
        assertEquals(UnitValue(DesignUnit.Percent, 135.0), style.lineHeight)
    }

    @Test
    fun upgradesScalarLineHeightToPercentMapInPlace() {
        val doc = docWith(
            """
            |    fontSize: 12
            |    lineHeight: 32
            """.trimMargin(),
        )
        val edit = SetTextStyle("label", DesignTextStyle(lineHeight = UnitValue(DesignUnit.Percent, 135.0)))

        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, edit, compiled)
        val new = result.requireNewSource()
        assertEquals(
            doc.replace("lineHeight: 32", "lineHeight: { unit: percent, value: 135 }"),
            new,
        )
        assertLosslessOutside(doc, new, assertNotNull(result.appliedRange))

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals(UnitValue(DesignUnit.Percent, 135.0), style.lineHeight)
    }

    @Test
    fun pxLineHeightOntoAuthoredPercentMapFallsBack() {
        val doc = docWith(
            """
            |    fontSize: 12
            |    lineHeight:
            |      unit: percent
            |      value: 135
            """.trimMargin(),
        )
        val edit = SetTextStyle("label", DesignTextStyle(lineHeight = UnitValue(DesignUnit.Px, 20.0)))

        val compiled = compileForEdit(doc)
        val result = applySlmEdit(doc, edit, compiled)
        assertFalse(result.isApplied, "a px scalar cannot replace an authored percent map in place")
        assertNull(result.newSource)
        assertTrue(
            result.diagnostics.any { "scalar" in it.message.lowercase() || "map" in it.message.lowercase() },
            "surfaces the merge-conflict diagnostic: ${result.diagnostics.map { it.message }}",
        )
    }

    @Test
    fun canonicalizesHorizontalAlignToken() {
        // Authored `start` reads to Left; writing Left back emits the canonical `left`.
        val doc = docWith(
            """
            |    fontSize: 12
            |    horizontalAlign: start
            """.trimMargin(),
        )
        val edit = SetTextStyle("label", DesignTextStyle(textAlignHorizontal = TextAlignHorizontal.Left))
        val new = applySlmEdit(doc, edit, compileForEdit(doc)).requireNewSource()
        assertTrue("horizontalAlign: left" in new, "canonical token written: $new")

        val style = compileForEdit(new).requireDocument().requireNode("label").textStyle()
        assertEquals(TextAlignHorizontal.Left, style.textAlignHorizontal)
    }

    @Test
    fun createsTypographyBlockWhenAbsent() {
        val doc = HEAD_NO_TYPOGRAPHY + TAIL
        val edit = SetTextStyle("label", DesignTextStyle(fontSize = 14.0.bindable()))
        val style = recompiledLabel(doc, edit).textStyle()
        assertEquals(14.0.bindable(), style.fontSize)
    }

    private companion object {
        private val HEAD = """
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
        """.trimIndent() + "\n"

        private val HEAD_NO_TYPOGRAPHY = """
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
        """.trimIndent() + "\n"

        private val TAIL = """
            style:
              fills:
                - color: "#101010"
        """.trimIndent() + "\n"
    }
}
