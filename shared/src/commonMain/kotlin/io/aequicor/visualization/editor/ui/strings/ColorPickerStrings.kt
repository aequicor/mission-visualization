package io.aequicor.visualization.editor.ui.strings

/**
 * ColorPicker area catalog: the compact swatch row + expanded HSV popup
 * ([io.aequicor.visualization.editor.ui.ColorPickerField]). Only user-facing copy lives here —
 * the RGB channel letters and the HEX label read the same in Russian, but stay in the catalog
 * so the whole control is localized in one place.
 */
interface ColorPickerStrings {
    // Swatch toggle (icon contentDescription)
    val openColorSelector: String
    val closeColorSelector: String

    // RGB channel field labels
    val channelR: String
    val channelG: String
    val channelB: String

    // Hex field label
    val hex: String
}

object ColorPickerStringsEn : ColorPickerStrings {
    override val openColorSelector = "Open color selector"
    override val closeColorSelector = "Close color selector"

    override val channelR = "R"
    override val channelG = "G"
    override val channelB = "B"

    override val hex = "HEX"
}

object ColorPickerStringsRu : ColorPickerStrings {
    override val openColorSelector = "Открыть выбор цвета"
    override val closeColorSelector = "Закрыть выбор цвета"

    override val channelR = "R"
    override val channelG = "G"
    override val channelB = "B"

    override val hex = "HEX"
}
