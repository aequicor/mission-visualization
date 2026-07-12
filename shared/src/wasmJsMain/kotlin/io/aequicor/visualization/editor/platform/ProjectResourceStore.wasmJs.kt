package io.aequicor.visualization.editor.platform

import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * IndexedDB-backed resource store for wasmJs. Bytes cross the wasm↔JS boundary as base64 strings
 * (the only value type the raw `js(...)` shim marshals reliably), decoded to [ByteArray] on the
 * Kotlin side. IndexedDB (not localStorage) so multi-MB images do not exhaust the ~5MB draft
 * quota and survive reloads without re-prompting for a folder.
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalWasmJsInterop::class)
private class IndexedDbProjectResourceStore : ProjectResourceStore {

    override suspend fun put(path: String, bytes: ByteArray) {
        ensureResStoreInstalled()
        val base64 = Base64.encode(bytes)
        suspendCancellableCoroutine { cont ->
            resStorePut(path, base64) { cont.resume(Unit) }
        }
    }

    override suspend fun read(path: String): ByteArray? {
        ensureResStoreInstalled()
        val base64 = suspendCancellableCoroutine { cont ->
            resStoreRead(path) { value -> cont.resume(value) }
        }
        return if (base64.isEmpty()) null else runCatching { Base64.decode(base64) }.getOrNull()
    }

    override suspend fun list(): List<String> {
        ensureResStoreInstalled()
        val joined = suspendCancellableCoroutine { cont ->
            resStoreList { value -> cont.resume(value) }
        }
        return if (joined.isEmpty()) emptyList() else joined.split("\n")
    }

    override suspend fun replaceAll(resources: List<Pair<String, ByteArray>>) {
        ensureResStoreInstalled()
        // Two parallel newline-joined strings; base64 and paths never contain a newline.
        val paths = resources.joinToString("\n") { it.first }
        val payloads = resources.joinToString("\n") { Base64.encode(it.second) }
        suspendCancellableCoroutine { cont ->
            resStoreReplaceAll(paths, payloads) { cont.resume(Unit) }
        }
    }
}

actual fun createProjectResourceStore(): ProjectResourceStore = IndexedDbProjectResourceStore()

/** Installs the `window.__mvResStore` IndexedDB shim; safe to call repeatedly (no-op if present). */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun ensureResStoreInstalled() {
    js(
        """
        (function () {
          if (window.__mvResStore) return;
          var DB_NAME = "mvResources";
          var STORE = "res";
          var dbPromise = null;

          function openDb() {
            if (dbPromise) return dbPromise;
            dbPromise = new Promise(function (resolve, reject) {
              var req = indexedDB.open(DB_NAME, 1);
              req.onupgradeneeded = function () {
                var db = req.result;
                if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
              };
              req.onsuccess = function () { resolve(req.result); };
              req.onerror = function () { dbPromise = null; reject(req.error); };
            });
            return dbPromise;
          }

          function guard(promise, fallback) {
            promise.catch(function (error) { console.error(error); fallback(); });
          }

          window.__mvResStore = {
            put: function (path, base64, onDone) {
              guard(openDb().then(function (db) {
                return new Promise(function (resolve, reject) {
                  var t = db.transaction(STORE, "readwrite");
                  t.objectStore(STORE).put(base64, path);
                  t.oncomplete = function () { resolve(); };
                  t.onerror = function () { reject(t.error); };
                  t.onabort = function () { reject(t.error); };
                });
              }).then(function () { onDone(); }), function () { onDone(); });
            },
            read: function (path, onResult) {
              guard(openDb().then(function (db) {
                return new Promise(function (resolve, reject) {
                  var r = db.transaction(STORE, "readonly").objectStore(STORE).get(path);
                  r.onsuccess = function () { resolve(typeof r.result === "string" ? r.result : ""); };
                  r.onerror = function () { reject(r.error); };
                });
              }).then(function (value) { onResult(value); }), function () { onResult(""); });
            },
            list: function (onResult) {
              guard(openDb().then(function (db) {
                return new Promise(function (resolve, reject) {
                  var r = db.transaction(STORE, "readonly").objectStore(STORE).getAllKeys();
                  r.onsuccess = function () { resolve((r.result || []).join("\n")); };
                  r.onerror = function () { reject(r.error); };
                });
              }).then(function (value) { onResult(value); }), function () { onResult(""); });
            },
            replaceAll: function (pathsJoined, payloadsJoined, onDone) {
              var paths = pathsJoined ? pathsJoined.split("\n") : [];
              var payloads = payloadsJoined ? payloadsJoined.split("\n") : [];
              guard(openDb().then(function (db) {
                return new Promise(function (resolve, reject) {
                  var t = db.transaction(STORE, "readwrite");
                  var store = t.objectStore(STORE);
                  store.clear();
                  for (var i = 0; i < paths.length; i++) {
                    if (paths[i]) store.put(payloads[i] || "", paths[i]);
                  }
                  t.oncomplete = function () { resolve(); };
                  t.onerror = function () { reject(t.error); };
                  t.onabort = function () { reject(t.error); };
                });
              }).then(function () { onDone(); }), function () { onDone(); });
            }
          };
        })();
        """
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun resStorePut(path: String, base64: String, onDone: () -> Unit): Unit =
    js("window.__mvResStore.put(path, base64, onDone)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun resStoreRead(path: String, onResult: (String) -> Unit): Unit =
    js("window.__mvResStore.read(path, onResult)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun resStoreList(onResult: (String) -> Unit): Unit =
    js("window.__mvResStore.list(onResult)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun resStoreReplaceAll(paths: String, payloads: String, onDone: () -> Unit): Unit =
    js("window.__mvResStore.replaceAll(paths, payloads, onDone)")
