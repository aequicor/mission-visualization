package io.aequicor.visualization.editor.data

import android.content.Context

/**
 * Holds the application [Context] for local persistence. Set once from the app's
 * `Application.onCreate`; when unset (Compose previews, host tests) the store degrades
 * to [NoopKeyValueStore] instead of crashing.
 */
object AndroidPersistence {
    var appContext: Context? = null
}

private class SharedPrefsKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("mission_visualization_drafts", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

actual fun createKeyValueStore(): KeyValueStore =
    AndroidPersistence.appContext?.let(::SharedPrefsKeyValueStore) ?: NoopKeyValueStore
