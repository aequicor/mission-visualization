package io.aequicor.visualization.subsystems.typography.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import io.aequicor.visualization.subsystems.typography.DecorationColor
import io.aequicor.visualization.subsystems.typography.DecorationKind
import io.aequicor.visualization.subsystems.typography.DecorationSpec
import io.aequicor.visualization.subsystems.typography.DecorationStyle
import io.aequicor.visualization.subsystems.typography.LinkSpan
import io.aequicor.visualization.subsystems.typography.RichText
import io.aequicor.visualization.subsystems.typography.Rgba
import io.aequicor.visualization.subsystems.typography.StyleSpan
import io.aequicor.visualization.subsystems.typography.TextCasing
import io.aequicor.visualization.subsystems.typography.TextPosition
import io.aequicor.visualization.subsystems.typography.TypographyDefaults
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichTextCompositionTest {

    private val density = Density(1f)

    private fun compose(rich: RichText, fill: RichTextFill = RichTextFill()): ComposedRichText =
        composeRichText(rich, density, NoFonts, fill)

    // -- paragraph splitting --

    @Test
    fun splitsParagraphsAtNewlinesWithCorrectOffsets() {
        val composed = compose(RichText("a\nb"))

        assertEquals(2, composed.paragraphs.size)
        val (first, second) = composed.paragraphs
        assertEquals(0, first.textStart)
        assertEquals(1, first.textEnd)
        assertEquals("a", first.annotated.text)
        assertEquals(2, second.textStart)
        assertEquals(3, second.textEnd)
        assertEquals("b", second.annotated.text)
    }

    // -- case transform --

    @Test
    fun upperCaseTransformProjectsSpanOffsets() {
        // "aß b" -> "ASS B": the ß -> SS expansion shifts everything after it.
        val rich = RichText(
            text = "aß b",
            base = TypographyStyle(case = TextCasing.Upper),
            spans = listOf(StyleSpan(0, 2, TypographyStyle(fontWeight = 700))),
        )
        val composed = compose(rich)

        assertEquals("ASS B", composed.transformed.text)
        val paragraph = composed.paragraphs.single()
        assertEquals("ASS B", paragraph.annotated.text)
        assertEquals(0, paragraph.textStart)
        assertEquals(5, paragraph.textEnd)

        val span = paragraph.annotated.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(3, span.end)
        assertEquals(FontWeight(700), span.item.fontWeight)
    }

    // -- span overrides --

    @Test
    fun spanOverrideProducesSpanStyleAtRightRange() {
        val rich = RichText(
            text = "hello world",
            spans = listOf(StyleSpan(6, 11, TypographyStyle(fontWeight = 700))),
        )
        val paragraph = compose(rich).paragraphs.single()

        val span = paragraph.annotated.spanStyles.single()
        assertEquals(6, span.start)
        assertEquals(11, span.end)
        assertEquals(FontWeight(700), span.item.fontWeight)
    }

    @Test
    fun linkRangeGetsUnderlineSpan() {
        val rich = RichText(
            text = "hello world",
            links = listOf(LinkSpan(0, 5, url = "https://example.org")),
        )
        val paragraph = compose(rich).paragraphs.single()

        val span = paragraph.annotated.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
        assertEquals(TextDecoration.Underline, span.item.textDecoration)
    }

    // -- decorations --

    @Test
    fun dashedDecorationBecomesCustomDecorationWithoutNativeSpan() {
        val spec = DecorationSpec(kind = DecorationKind.Underline, style = DecorationStyle.Dashed)
        val rich = RichText(
            text = "hello world",
            spans = listOf(StyleSpan(0, 5, TypographyStyle(decoration = spec))),
        )
        val paragraph = compose(rich).paragraphs.single()

        val decoration = paragraph.customDecorations.single()
        assertEquals(0, decoration.start)
        assertEquals(5, decoration.end)
        assertEquals(DecorationStyle.Dashed, decoration.spec.style)
        // Auto color resolves to the glyph fill color.
        assertEquals(Color.Black, decoration.color)
        assertEquals(TypographyDefaults.FONT_SIZE, decoration.fontSize)

        // The native span must not draw the same underline again.
        assertTrue(
            paragraph.annotated.spanStyles.none {
                it.item.textDecoration == TextDecoration.Underline ||
                    it.item.textDecoration == TextDecoration.LineThrough
            },
        )
    }

    @Test
    fun customDecorationColorResolvesCustomColor() {
        val spec = DecorationSpec(
            kind = DecorationKind.Strikethrough,
            style = DecorationStyle.Wavy,
            color = DecorationColor.Custom(Rgba(1.0, 0.0, 0.0)),
        )
        val rich = RichText(
            text = "abc",
            spans = listOf(StyleSpan(0, 3, TypographyStyle(decoration = spec))),
        )
        val decoration = compose(rich).paragraphs.single().customDecorations.single()

        assertEquals(Color(1f, 0f, 0f, 1f), decoration.color)
    }

    @Test
    fun defaultDecorationSpecUsesNativeUnderline() {
        val spec = DecorationSpec(kind = DecorationKind.Underline)
        val rich = RichText(
            text = "hello world",
            spans = listOf(StyleSpan(0, 5, TypographyStyle(decoration = spec))),
        )
        val paragraph = compose(rich).paragraphs.single()

        assertTrue(paragraph.customDecorations.isEmpty())
        val span = paragraph.annotated.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(5, span.end)
        assertEquals(TextDecoration.Underline, span.item.textDecoration)
    }

    // -- featureSettings --

    @Test
    fun featureSettingsForSmallCaps() {
        assertEquals("'smcp' 1", featureSettings(TypographyStyle(case = TextCasing.SmallCaps)))
    }

    @Test
    fun featureSettingsForForcedSmallCaps() {
        assertEquals(
            "'smcp' 1, 'c2sc' 1",
            featureSettings(TypographyStyle(case = TextCasing.SmallCapsForced)),
        )
    }

    @Test
    fun featureSettingsMergesExplicitFeatures() {
        val style = TypographyStyle(
            case = TextCasing.SmallCaps,
            features = linkedMapOf("liga" to false, "tnum" to true),
        )
        assertEquals("'smcp' 1, 'liga' 0, 'tnum' 1", featureSettings(style))
    }

    @Test
    fun explicitFeatureOverridesCaseDerivedTag() {
        val style = TypographyStyle(
            case = TextCasing.SmallCaps,
            features = mapOf("smcp" to false),
        )
        assertEquals("'smcp' 0", featureSettings(style))
    }

    @Test
    fun featureSettingsNullWhenEmpty() {
        assertNull(featureSettings(TypographyStyle()))
        assertNull(featureSettings(TypographyStyle(case = TextCasing.Upper)))
    }

    // -- toSpanStyle --

    @Test
    fun toSpanStyleOnlySetsOverriddenFields() {
        val base = TypographyStyle(fontSize = 20.0)
        val override = TypographyStyle(fontWeight = 700)
        val span = override.toSpanStyle(
            merged = base.mergedWith(override),
            density = density,
            fontProvider = NoFonts,
            fills = null,
        )

        assertEquals(FontWeight(700), span.fontWeight)
        assertEquals(TextUnit.Unspecified, span.fontSize)
        assertEquals(Color.Unspecified, span.color)
        assertNull(span.fontStyle)
        assertNull(span.fontFamily)
        assertEquals(TextUnit.Unspecified, span.letterSpacing)
        assertNull(span.baselineShift)
        assertNull(span.fontFeatureSettings)
        assertNull(span.textDecoration)
        assertNull(span.brush)
    }

    @Test
    fun superscriptScalesFontSizeAndShiftsBaseline() {
        val base = TypographyStyle(fontSize = 20.0)
        val override = TypographyStyle(position = TextPosition.Superscript)
        val span = override.toSpanStyle(
            merged = base.mergedWith(override),
            density = density,
            fontProvider = NoFonts,
            fills = null,
        )

        assertNotEquals(TextUnit.Unspecified, span.fontSize)
        val expected = (20.0 * TypographyDefaults.SUPERSCRIPT_SCALE).toFloat()
        assertEquals(expected, span.fontSize.value, 0.001f)
        assertEquals(BaselineShift.Superscript, span.baselineShift)
    }
}
