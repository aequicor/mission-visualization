package io.aequicor.visualization.editor.platform

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One store instance is shared by the canvas and Resources pane on desktop. */
private object DesktopProjectResourceStore : ProjectResourceStore {
    private val sessionEntries = ConcurrentHashMap<String, ByteArray>()

    override suspend fun put(path: String, bytes: ByteArray) {
        val relative = normalizedResourcePath(path)
        val root = activeProjectRoot()
        if (root == null) {
            sessionEntries[relative] = bytes.copyOf()
            return
        }
        withContext(Dispatchers.IO) {
            atomicWrite(resourceTarget(root, relative), bytes)
        }
    }

    override suspend fun read(path: String): ByteArray? {
        val relative = normalizedResourcePath(path)
        val root = activeProjectRoot()
        if (root == null) return sessionEntries[relative]?.copyOf()
        return withContext(Dispatchers.IO) {
            resourceTarget(root, relative)
                .takeIf(Files::isRegularFile)
                ?.let(Files::readAllBytes)
        }
    }

    override suspend fun list(): List<String> {
        val root = activeProjectRoot() ?: return sessionEntries.keys.sorted()
        return withContext(Dispatchers.IO) {
            val resources = root.resolve(ResourceDirectory).normalize()
            if (!Files.isDirectory(resources)) return@withContext emptyList()
            Files.walk(resources).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .map { root.relativize(it).toString().replace('\\', '/') }
                    .sorted()
                    .toList()
            }
        }
    }

    override suspend fun replaceAll(resources: List<Pair<String, ByteArray>>) {
        val normalized = resources.map { (path, bytes) -> normalizedResourcePath(path) to bytes }
        val root = activeProjectRoot()
        if (root == null) {
            sessionEntries.clear()
            normalized.forEach { (path, bytes) -> sessionEntries[path] = bytes.copyOf() }
            return
        }
        withContext(Dispatchers.IO) {
            val resourceRoot = root.resolve(ResourceDirectory).normalize()
            require(resourceRoot.startsWith(root)) { "Resource directory escapes the project folder" }
            if (Files.isDirectory(resourceRoot)) {
                Files.walk(resourceRoot).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
            normalized.forEach { (path, bytes) -> atomicWrite(resourceTarget(root, path), bytes) }
        }
    }
}

actual fun createProjectResourceStore(): ProjectResourceStore = DesktopProjectResourceStore

private fun activeProjectRoot(): Path? = platformActiveFolderId()
    ?.let(Path::of)
    ?.toAbsolutePath()
    ?.normalize()
    ?.takeIf(Files::isDirectory)

private fun normalizedResourcePath(path: String): String {
    val normalized = path.replace('\\', '/').trim()
    require(normalized.startsWith("$ResourceDirectory/")) { "Resource path must be under res/: $path" }
    require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "Invalid resource path: $path"
    }
    return normalized
}

private fun resourceTarget(root: Path, relative: String): Path {
    val target = root.resolve(relative).toAbsolutePath().normalize()
    require(target.startsWith(root) && target != root) { "Resource path escapes the project folder: $relative" }
    return target
}

private fun atomicWrite(target: Path, bytes: ByteArray) {
    Files.createDirectories(target.parent)
    val temp = Files.createTempFile(target.parent, ".${target.fileName}.", ".tmp")
    try {
        Files.write(temp, bytes)
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}

private const val ResourceDirectory = "res"
