package io.aequicor.visualization.editor.data

import platform.Foundation.NSUserDefaults

/** NSUserDefaults-backed store (fine for the small draft envelope). */
private class NsUserDefaultsKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String) = defaults.setObject(value, key)
    override fun remove(key: String) = defaults.removeObjectForKey(key)
}

actual fun createKeyValueStore(): KeyValueStore = NsUserDefaultsKeyValueStore()
