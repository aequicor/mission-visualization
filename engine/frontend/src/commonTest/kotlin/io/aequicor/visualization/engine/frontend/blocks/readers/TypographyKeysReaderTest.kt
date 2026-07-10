package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TextSpanPatch
import io.aequicor.visualization.engine.frontend.edit.compileForEdit
import io.aequicor.visualization.engine.frontend.edit.requireDocument
import io.aequicor.visualization.engine.frontend.edit.requireNode
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Authoring round-trip for the typography subsystem's new `text: typography:` keys and the
 * new inline span form. Covers the reader ([readTypographyMap] / [readSpan]) at the patch
 * level and the full compile down to a text node's [TextStyleRange]s.
 */
class TypographyKeysReaderTest {

    /** The full grid of new scalar/enum typography keys reads into a partial [DesignTextStyle]. */
    @Test
    fun readsNewTypographyKeys() {
        val (patch, collector) = readSingle(
            """
            text:
              key: screen.title
              defaultText: Mission Control
              typography:
                fontFamily: Inter
                fontWeight: 600
                italic: true
                fontSize: 16
                lineHeight: 24
                letterSpacing: 0
                paragraphSpacing: 4
                paragraphIndent: 12
                horizontalAlign: start
                verticalAlign: top
                case: smallCaps
                decoration: underline
                decorationStyle: dashed
                decorationColor: "#3366FF"
                decorationThickness: 2
                decorationSkipInk: false
                position: superscript
                leadingTrim: capHeight
                hangingPunctuation: true
                hangingList: true
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        val text = assertIs<TextPatch>(patch)
        assertEquals(
            DesignTextStyle(
                fontFamily = "Inter",
                fontWeight = 600.0.bindable(),
                italic = true,
                fontSize = 16.0.bindable(),
                lineHeight = UnitValue(DesignUnit.Px, 24.0),
                letterSpacing = UnitValue(DesignUnit.Px, 0.0),
                paragraphSpacing = 4.0,
                paragraphIndent = 12.0,
                textAlignHorizontal = TextAlignHorizontal.Left,
                textAlignVertical = TextAlignVertical.Top,
                textCase = TextCase.SmallCaps,
                textDecoration = TextDecorationKind.Underline,
                decorationStyle = TextDecorationStyle.Dashed,
                decorationColor = DesignColor.fromHex("#3366FF"),
                decorationThickness = UnitValue(DesignUnit.Px, 2.0),
                decorationSkipInk = false,
                textPosition = TextScriptPosition.Superscript,
                leadingTrim = LeadingTrim.CapHeight,
                hangingPunctuation = true,
                hangingList = true,
            ),
            text.typography,
        )
    }

    /** `case: smallCapsForced` and a percent `decorationThickness` map both read faithfully. */
    @Test
    fun readsSmallCapsForcedAndPercentDecorationThickness() {
        val (patch, collector) = readSingle(
            """
            text:
              typography:
                case: smallCapsForced
                decorationThickness: { unit: percent, value: 8 }
                position: subscript
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        val text = assertIs<TextPatch>(patch)
        assertEquals(TextCase.SmallCapsForced, text.typography?.textCase)
        assertEquals(UnitValue(DesignUnit.Percent, 8.0), text.typography?.decorationThickness)
        assertEquals(TextScriptPosition.Subscript, text.typography?.textPosition)
    }

    /** A span with inline `typography:` + `fills:` (no `style:` ref) carries both at patch level. */
    @Test
    fun readsInlineSpanTypographyAndFills() {
        val (patch, collector) = readSingle(
            """
            text:
              defaultText: "Hello world"
              spans:
                - range: [0, 5]
                  typography:
                    fontWeight: 700
                    italic: true
                  fills:
                    - color: "#00ff00"
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        val text = assertIs<TextPatch>(patch)
        assertEquals(
            listOf(
                TextSpanPatch(
                    start = 0,
                    end = 5,
                    style = DesignTextStyle(fontWeight = 700.0.bindable(), italic = true),
                    fills = listOf(DesignPaint.Solid(DesignColor.fromHex("#00ff00")!!.bindable())),
                ),
            ),
            text.spans,
        )
    }

    /** End to end: the inline span compiles into the text node's non-empty [TextStyleRange]. */
    @Test
    fun compilesInlineSpanIntoNodeStyleRanges() {
        val compiled = compileForEdit(
            """
            ---
            screen: s
            sourceLocale: en-US
            ---

            # Screen

            ## Text: Label
            node:
              type: text
              id: label
              name: Label
            text:
              key: screen.label
              defaultText: "Hello world"
              spans:
                - range: [0, 5]
                  typography:
                    fontWeight: 700
                    italic: true
                  fills:
                    - color: "#00ff00"
            """.trimIndent() + "\n",
        )
        assertTrue(compiled.isSuccess, compiled.diagnostics.joinToString { it.message })
        val kind = assertIs<DesignNodeKind.Text>(compiled.requireDocument().requireNode("label").kind)
        assertEquals(
            listOf(
                TextStyleRange(
                    start = 0,
                    end = 5,
                    style = DesignTextStyle(fontWeight = 700.0.bindable(), italic = true),
                    fills = listOf(DesignPaint.Solid(DesignColor.fromHex("#00ff00")!!.bindable())),
                ),
            ),
            kind.styleRanges,
        )
        assertTrue(kind.links.isEmpty(), "no links authored")
    }
}
