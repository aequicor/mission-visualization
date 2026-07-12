package io.aequicor.visualization.editor.data

import io.aequicor.visualization.editor.domain.AppLanguage

/**
 * Persists the chosen UI language in the platform key/value store (browser `localStorage`,
 * a JVM file, SharedPreferences on Android, NSUserDefaults on iOS). Kept separate from the
 * draft slice so the language choice survives «Открыть → Welcome» / project resets.
 */
class LanguagePreference(private val store: KeyValueStore) {

    /** Last chosen language, or [AppLanguage.Default] on first run / unavailable storage. */
    fun load(): AppLanguage = AppLanguage.fromCode(store.getString(KEY))

    fun save(language: AppLanguage) = store.putString(KEY, language.code)

    private companion object {
        const val KEY = "app.language"
    }
}
