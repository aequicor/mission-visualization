package io.aequicor.visualization.editor.data

import web.storage.localStorage

/** Browser `localStorage`, via the kotlin-wrappers `web.storage` API. */
private class LocalStorageKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = localStorage.getItem(key)
    override fun putString(key: String, value: String) = localStorage.setItem(key, value)
    override fun remove(key: String) = localStorage.removeItem(key)
}

actual fun createKeyValueStore(): KeyValueStore = LocalStorageKeyValueStore()
