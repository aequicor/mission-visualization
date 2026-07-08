package io.aequicor.visualization.editor.data

import java.io.File

/** File-backed store: one file per key under a base [directory]. */
private class FileKeyValueStore(private val directory: File) : KeyValueStore {

    private fun fileFor(key: String): File =
        File(directory, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    override fun getString(key: String): String? =
        fileFor(key).takeIf { it.isFile }?.readText()

    override fun putString(key: String, value: String) {
        directory.mkdirs()
        fileFor(key).writeText(value)
    }

    override fun remove(key: String) {
        fileFor(key).delete()
    }
}

actual fun createKeyValueStore(): KeyValueStore {
    val home = System.getProperty("user.home") ?: "."
    return FileKeyValueStore(File(home, ".mission-visualization"))
}
