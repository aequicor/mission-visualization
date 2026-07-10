package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Full-loop stability for the typography subsystem: author SLM carrying the new typography
 * keys plus rich-text spans, compile, apply a [SetTextStyle] write-back, recompile, and assert
 * that the applied field changed while every other authored typography field, style range and
 * link survived untouched — and that bytes outside the edit are identical.
 */
class TypographyRoundTripTest {

    @Test
    fun authoredTypographyAndSpansSurviveAWriteBackEdit() {
        val compiled = compileForEdit(DOC)
        assertTrue(compiled.isSuccess, compiled.diagnostics.joinToString { it.message })

        // Baseline: what the authored document compiles to.
        val before = assertIs<DesignNodeKind.Text>(compiled.requireDocument().requireNode("label").kind)
        assertEquals(TextCase.SmallCaps, before.textStyle?.textCase)
        assertEquals(TextScriptPosition.Superscript, before.textStyle?.textPosition)
        val authoredRanges = listOf(TextStyleRange(0, 5, DesignTextStyle(fontWeight = 700.0.bindable())))
        val authoredLinks = listOf(TextLink(6, 11, url = "https://example.com"))
        assertEquals(authoredRanges, before.styleRanges)
        assertEquals(authoredLinks, before.links)

        // Write-back: flip `italic` in place and append `letterSpacing`.
        val edit = SetTextStyle(
            "label",
            DesignTextStyle(italic = false, letterSpacing = UnitValue(DesignUnit.Px, 1.0)),
        )
        val result = applySlmEdit(DOC, edit, compiled)
        val new = result.requireNewSource()
        assertLosslessOutside(DOC, new, assertNotNull(result.appliedRange))

        val after = assertIs<DesignNodeKind.Text>(
            compileForEdit(new).requireDocument().requireNode("label").kind,
        )
        // The edit applied.
        assertEquals(false, after.textStyle?.italic)
        assertEquals(UnitValue(DesignUnit.Px, 1.0), after.textStyle?.letterSpacing)
        // Everything else authored survived.
        assertEquals(16.0.bindable(), after.textStyle?.fontSize)
        assertEquals(TextCase.SmallCaps, after.textStyle?.textCase)
        assertEquals(TextDecorationStyle.Dashed, after.textStyle?.decorationStyle)
        assertEquals(DesignColor.fromHex("#3366FF"), after.textStyle?.decorationColor)
        assertEquals(TextScriptPosition.Superscript, after.textStyle?.textPosition)
        assertEquals(authoredRanges, after.styleRanges)
        assertEquals(authoredLinks, after.links)
    }

    private companion object {
        private val DOC = """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Text: Notice
            node:
              type: text
              id: label
              name: Notice
            text:
              key: screen.notice
              defaultText: "Hello world example"
              typography:
                fontSize: 16
                italic: true
                case: smallCaps
                decorationStyle: dashed
                decorationColor: "#3366FF"
                position: superscript
              spans:
                - range: [0, 5]
                  typography:
                    fontWeight: 700
                - range: [6, 11]
                  link:
                    type: url
                    href: https://example.com
        """.trimIndent() + "\n"
    }
}
