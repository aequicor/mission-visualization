package io.aequicor.visualization.subsystems.typography.compose

import io.aequicor.visualization.subsystems.typography.FontDescriptor
import io.aequicor.visualization.subsystems.typography.TypographyStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontProviderTest {

    @Test
    fun normalizesSourceSerifAliases() {
        assertEquals("sourceserif4", BundledFontProvider.normalizeFamilyName("Source Serif Pro"))
        assertEquals("sourceserif4", BundledFontProvider.normalizeFamilyName("Source Serif"))
        assertEquals("sourceserif4", BundledFontProvider.normalizeFamilyName("Source Serif 4"))
    }

    @Test
    fun normalizationIsCaseAndSpaceInsensitive() {
        assertEquals("sourceserif4", BundledFontProvider.normalizeFamilyName("SOURCE   SERIF PRO"))
        assertEquals("sourceserif4", BundledFontProvider.normalizeFamilyName("source serif pro"))
        assertEquals("inter", BundledFontProvider.normalizeFamilyName(" In Ter "))
        assertEquals("jetbrainsmono", BundledFontProvider.normalizeFamilyName("JetBrains Mono"))
    }

    @Test
    fun normalizesJetBrainsMonoNlAlias() {
        assertEquals("jetbrainsmono", BundledFontProvider.normalizeFamilyName("JetBrains Mono NL"))
    }

    @Test
    fun unknownFamiliesNormalizeToPlainKey() {
        assertEquals("comicsansms", BundledFontProvider.normalizeFamilyName("Comic Sans MS"))
    }

    @Test
    fun noFontsResolvesNull() {
        assertNull(NoFonts.resolve(FontDescriptor("Inter")))
        assertNull(NoFonts.resolve(FontDescriptor("")))
        assertEquals(0, NoFonts.generation)
        assertTrue(NoFonts.families.isEmpty())
    }

    @Test
    fun fontDescriptorReflectsStyleFields() {
        val style = TypographyStyle(
            fontFamily = "Inter",
            fontWeight = 600,
            italic = true,
            axes = mapOf("opsz" to 24.0),
        )
        assertEquals(
            FontDescriptor(family = "Inter", weight = 600, italic = true, axes = mapOf("opsz" to 24.0)),
            style.fontDescriptor(),
        )
        assertEquals(
            FontDescriptor(family = "", weight = 400, italic = false),
            TypographyStyle().fontDescriptor(),
        )
    }
}
