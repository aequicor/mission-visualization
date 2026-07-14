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
            flushSessionEntries(root)
            atomicWrite(resourceTarget(root, relative), bytes)
        }
    }

    override suspend fun read(path: String): ByteArray? {
        val relative = normalizedResourcePath(path)
        val root = activeProjectRoot()
        if (root == null) return sessionEntries[relative]?.copyOf()
        return withContext(Dispatchers.IO) {
            flushSessionEntries(root)
            resourceTarget(root, relative)
                .takeIf(Files::isRegularFile)
                ?.let(Files::readAllBytes)
        }
    }

    override suspend fun list(): List<String> {
        val root = activeProjectRoot() ?: return sessionEntries.keys.sorted()
        return withContext(Dispatchers.IO) {
            flushSessionEntries(root)
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
            // A full replace supersedes any pre-connect session buffer; drop it so a later lazy
            // flush cannot resurrect resources this call intended to remove.
            sessionEntries.clear()
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

    /**
     * Migrates resources buffered before a folder was connected ([sessionEntries]) onto disk under
     * the project `res/` directory, then clears the buffer. Idempotent: a no-op once flushed, so it
     * is safe to call at the top of every disk-backed operation the first time a root appears.
     */
    private fun flushSessionEntries(root: Path) {
        if (sessionEntries.isEmpty()) return
        synchronized(sessionEntries) {
            if (sessionEntries.isEmpty()) return
            sessionEntries.forEach { (relative, bytes) ->
                atomicWrite(resourceTarget(root, relative), bytes)
            }
            sessionEntries.clear()
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
    val canonicalRoot = root.toRealPath()
    val target = canonicalRoot.resolve(relative).toAbsolutePath().normalize()
    require(target.startsWith(canonicalRoot) && target != canonicalRoot) {
        "Resource path escapes the project folder: $relative"
    }
    // Lexical containment is not enough: a symlinked `res/` (or any ancestor) can point outside the
    // project. Mirror ProjectIo.safeResolve — resolve the nearest existing ancestor's real path and
    // require it to still live under the canonical root.
    val existingAncestor = generateSequence(target.parent) { it.parent }.firstOrNull { Files.exists(it) }
        ?: error("Resource target has no accessible parent")
    require(existingAncestor.toRealPath().startsWith(canonicalRoot)) {
        "Resource path escapes the connected folder through a symbolic link: $relative"
    }
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
