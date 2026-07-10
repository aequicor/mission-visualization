package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Whole-list rich-text write-back ([SetTextSpans]): the working node's [TextStyleRange]s and
 * [TextLink]s serialize into a `spans:` block (inline `typography:` / `fills:` / `link:`), the
 * recompiled node carries back exactly the same ranges and links, and bytes outside the applied
 * range are untouched.
 */
class TextSpansWriteBackTest {

    private val range1 = TextStyleRange(
        start = 0,
        end = 5,
        style = DesignTextStyle(fontWeight = 700.0.bindable(), italic = true),
    )
    private val range2 = TextStyleRange(
        start = 6,
        end = 11,
        fills = listOf(DesignPaint.Solid(DesignColor.fromHex("#00ff00")!!.bindable())),
    )
    private val link = TextLink(start = 12, end = 19, url = "https://example.com")

    @Test
    fun writesSpansBlockAndRoundTrips() {
        val edit = SetTextSpans("label", styleRanges = listOf(range1, range2), links = listOf(link))

        val compiled = compileForEdit(DOC)
        val result = applySlmEdit(DOC, edit, compiled)
        val new = result.requireNewSource()

        // The `spans:` block carries range / typography / fills / link.
        listOf(
            "spans:",
            "range:",
            "typography:",
            "fontWeight: 700",
            "italic: true",
            "fills:",
            "#00ff00",
            "link:",
            "href:",
            "https://example.com",
        ).forEach { fragment -> assertTrue(fragment in new, "expected `$fragment` in:\n$new") }
        assertLosslessOutside(DOC, new, assertNotNull(result.appliedRange))

        val kind = assertIs<DesignNodeKind.Text>(
            compileForEdit(new).requireDocument().requireNode("label").kind,
        )
        // Range 2 authored only fills, so its style collapses to the empty default on read-back.
        assertEquals(
            listOf(range1, range2.copy(style = DesignTextStyle())),
            kind.styleRanges,
        )
        assertEquals(listOf(link), kind.links)
    }

    /** A style range and a link over the same offsets collapse into a single span entry. */
    @Test
    fun sharedRangeCollapsesStyleAndLink() {
        val sharedRange = TextStyleRange(0, 5, style = DesignTextStyle(italic = true))
        val sharedLink = TextLink(0, 5, url = "https://example.com/a")
        val edit = SetTextSpans("label", styleRanges = listOf(sharedRange), links = listOf(sharedLink))

        val new = applySlmEdit(DOC, edit, compileForEdit(DOC)).requireNewSource()
        // Exactly one span entry (one `- range:` bullet) covers both style and link.
        assertEquals(1, Regex("""^\s*-\s*range:""", RegexOption.MULTILINE).findAll(new).count(), new)

        val kind = assertIs<DesignNodeKind.Text>(
            compileForEdit(new).requireDocument().requireNode("label").kind,
        )
        assertEquals(listOf(sharedRange), kind.styleRanges)
        assertEquals(listOf(sharedLink), kind.links)
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
            style:
              fills:
                - color: "#101010"
        """.trimIndent() + "\n"
    }
}
