package io.aequicor.visualization.editor.ui.strings

import androidx.compose.runtime.staticCompositionLocalOf
import io.aequicor.visualization.editor.domain.AppLanguage

/**
 * Root catalog of every user-facing string in the app chrome, grouped by UI area. This is
 * the localization counterpart of `EditorColors`/`LocalEditorColors`: composables read copy
 * through [LocalStrings] instead of hardcoding literals, and the whole tree re-renders in the
 * new language when the provided [Strings] changes.
 *
 * Each area is its own interface with an `…En` / `…Ru` object, so a translation can be added
 * or a new area migrated without touching the others. Only chrome is localized — document
 * content and SLM sources stay language-neutral (the domain layer never depends on this).
 */
interface Strings {
    val common: CommonStrings
    val menu: MenuStrings
    val labels: LabelStrings
    val source: SourceStrings
    val inspector: InspectorStrings
    val prototype: PrototypeStrings
    val diagram: DiagramStrings
    val canvas: CanvasStrings
    val colorPicker: ColorPickerStrings
    val landing: LandingStrings
}

/** English catalog (also the default / fallback). */
object StringsEn : Strings {
    override val common = CommonStringsEn
    override val menu = MenuStringsEn
    override val labels = LabelStringsEn
    override val source = SourceStringsEn
    override val inspector = InspectorStringsEn
    override val prototype = PrototypeStringsEn
    override val diagram = DiagramStringsEn
    override val canvas = CanvasStringsEn
    override val colorPicker = ColorPickerStringsEn
    override val landing = LandingStringsEn
}

/** Russian catalog. */
object StringsRu : Strings {
    override val common = CommonStringsRu
    override val menu = MenuStringsRu
    override val labels = LabelStringsRu
    override val source = SourceStringsRu
    override val inspector = InspectorStringsRu
    override val prototype = PrototypeStringsRu
    override val diagram = DiagramStringsRu
    override val canvas = CanvasStringsRu
    override val colorPicker = ColorPickerStringsRu
    override val landing = LandingStringsRu
}

/** Maps the chosen [AppLanguage] to its catalog. */
fun appStringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.English -> StringsEn
    AppLanguage.Russian -> StringsRu
}

/** Active string catalog for the current composition; provided by the editor shell. */
val LocalStrings = staticCompositionLocalOf<Strings> { StringsEn }
