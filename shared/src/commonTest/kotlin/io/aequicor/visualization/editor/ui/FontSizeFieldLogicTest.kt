package io.aequicor.visualization.editor.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontSizeFieldLogicTest {

    @Test
    fun figmaPresetListIsCompleteAndOrdered() {
        assertEquals(
            listOf(10, 11, 12, 13, 14, 15, 16, 20, 24, 32, 36, 40, 48, 64, 96, 128),
            FigmaFontSizePresets,
        )
    }

    @Test
    fun selectedPresetMatchesNumericFieldValue() {
        assertTrue(isFontSizePresetSelected("14", 14))
        assertTrue(isFontSizePresetSelected("14.0", 14))
        assertFalse(isFontSizePresetSelected("13", 14))
        assertFalse(isFontSizePresetSelected("Mixed", 14))
        assertFalse(isFontSizePresetSelected("", 14))
    }
}
