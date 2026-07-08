package io.aequicor.visualization.editor.data

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Browser `localStorage` on wasmJs. There is no kotlin-browser wrapper on this target,
 * so access goes through raw `js(...)` interop shims (String-only, which is all the
 * draft envelope needs).
 */
private class LocalStorageKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = localStorageGetItem(key)
    override fun putString(key: String, value: String) = localStorageSetItem(key, value)
    override fun remove(key: String) = localStorageRemoveItem(key)
}

actual fun createKeyValueStore(): KeyValueStore = LocalStorageKeyValueStore()

@OptIn(ExperimentalWasmJsInterop::class)
private fun localStorageGetItem(key: String): String? =
    js("window.localStorage.getItem(key)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun localStorageSetItem(key: String, value: String): Unit =
    js("window.localStorage.setItem(key, value)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun localStorageRemoveItem(key: String): Unit =
    js("window.localStorage.removeItem(key)")
