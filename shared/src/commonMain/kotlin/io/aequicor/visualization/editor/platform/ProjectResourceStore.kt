package io.aequicor.visualization.editor.platform

/**
 * Binary project resources (images the user drops/pastes) addressed by their project-relative
 * path, e.g. `res/logo.png`. The design engine holds no image bytes — it references resources by
 * path — so this store owns the bytes and the render provider ([ImageAssetProvider] wiring) reads
 * them back to decode.
 *
 * On web this is backed by IndexedDB, so bytes survive reloads without re-prompting for a folder;
 * on other platforms it is an in-memory session store ([InMemoryProjectResourceStore]) until
 * native project folders land. All operations are suspend: the web backend is asynchronous.
 */
interface ProjectResourceStore {
    /** Stores [bytes] under [path], overwriting any existing resource at that path. */
    suspend fun put(path: String, bytes: ByteArray)

    /** Bytes stored at [path], or null when absent. */
    suspend fun read(path: String): ByteArray?

    /** Project-relative paths of every stored resource. */
    suspend fun list(): List<String>

    /** Replaces the entire store with [resources] (used on project open/import). */
    suspend fun replaceAll(resources: List<Pair<String, ByteArray>>)
}

/**
 * In-memory [ProjectResourceStore] for platforms without a persistent binary store yet
 * (desktop/Android/iOS in v1) and for tests. Session-scoped: contents are lost on restart.
 */
class InMemoryProjectResourceStore : ProjectResourceStore {
    private val entries = mutableMapOf<String, ByteArray>()

    override suspend fun put(path: String, bytes: ByteArray) {
        entries[path] = bytes
    }

    override suspend fun read(path: String): ByteArray? = entries[path]

    override suspend fun list(): List<String> = entries.keys.toList()

    override suspend fun replaceAll(resources: List<Pair<String, ByteArray>>) {
        entries.clear()
        resources.forEach { (path, bytes) -> entries[path] = bytes }
    }
}

/** The platform resource store: IndexedDB on web, in-memory elsewhere. */
expect fun createProjectResourceStore(): ProjectResourceStore
