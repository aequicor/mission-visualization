package io.aequicor.visualization.editor.data

/**
 * Minimal string key/value persistence. Platform actuals back it with the OS-local
 * store: browser `localStorage` on web, a file on JVM, SharedPreferences on Android,
 * NSUserDefaults on iOS.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** Fallback store used where local persistence is unavailable; keeps callers total. */
object NoopKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = null
    override fun putString(key: String, value: String) = Unit
    override fun remove(key: String) = Unit
}

/** The platform local key/value store. */
expect fun createKeyValueStore(): KeyValueStore
