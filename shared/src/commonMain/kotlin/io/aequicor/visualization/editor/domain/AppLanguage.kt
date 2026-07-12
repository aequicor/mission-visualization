package io.aequicor.visualization.editor.domain

/**
 * The languages the app chrome is translated into. Pure domain enum (no Compose): the
 * UI maps it to a [io.aequicor.visualization.editor.ui.strings.Strings] catalog and the
 * data layer persists the [code]. [nativeName] is how the language names itself, shown in
 * the language picker regardless of the currently active language.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    English("en", "English"),
    Russian("ru", "Русский"),
    ;

    companion object {
        val Default: AppLanguage = English

        /** Resolves a persisted [code] back to a language, falling back to [Default]. */
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: Default
    }
}
