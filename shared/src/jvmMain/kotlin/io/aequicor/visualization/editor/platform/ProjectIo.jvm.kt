package io.aequicor.visualization.editor.platform

import io.aequicor.visualization.editor.data.decodeProjectSnapshot
import io.aequicor.visualization.editor.data.encodeProjectSourcesJson
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import java.nio.charset.StandardCharsets
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFileChooser

internal actual val platformSupportsProjectDiskIo: Boolean = false

internal actual fun platformOpenProjectZipArchive() = Unit

internal actual fun platformOpenProjectFolder() = Unit

internal actual fun platformSaveProjectFolder(sourcesJson: String, onSaved: () -> Unit) = Unit

internal actual fun platformDownloadProjectZip(sourcesJson: String, onSaved: () -> Unit) = Unit

internal actual fun platformExportCanvasPng(fileName: String, crop: CanvasExportCrop?) = Unit

internal actual fun platformBeginPdfExport() = Unit

internal actual fun platformAppendCanvasPdfPage(title: String, crop: CanvasExportCrop?) = Unit

internal actual fun platformFinishPdfExport(fileName: String) = Unit

internal actual fun platformToggleFullscreen() = Unit

internal actual fun platformOpenUrl(url: String) {
    runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
            desktop.browse(java.net.URI(url))
        }
    }
}

internal actual fun platformSetActiveProjectId(id: String) = Unit

internal actual val platformSupportsFolderSync: Boolean = true

internal actual fun platformInitFolderSync() = JvmFolderSync.init()

internal actual fun platformConnectFolderLive() {
    JvmFolderSync.chooseFolder()?.let(JvmFolderSync::connect)
}

internal actual fun platformCreateFolderProject(sourcesJson: String) {
    val snapshot = decodeProjectSnapshot(sourcesJson) ?: return
    val folder = JvmFolderSync.chooseFolder() ?: return
    runCatching {
        snapshot.sources.forEach { source -> JvmFolderSync.writeInitial(folder, source) }
    }.onSuccess {
        JvmFolderSync.connect(folder)
    }.onFailure {
        JvmFolderSync.fail(it.message ?: "Unable to create project folder")
    }
}

internal actual fun platformReconnectSavedFolder() = JvmFolderSync.reconnect()

internal actual fun platformDisconnectFolder() = JvmFolderSync.disconnect(forget = true)

internal actual fun platformSavedFolderName(): String? = JvmFolderSync.savedFolderName()

internal actual fun folderSyncRevision(): Int = JvmFolderSync.revision.get()

internal actual fun folderSyncSnapshotJson(): String? = JvmFolderSync.snapshotJson

internal actual fun folderSyncStatus(): String? = JvmFolderSync.status

internal actual fun platformWriteFolderFile(fileName: String, content: String) =
    JvmFolderSync.writeFromEditor(fileName, content)

internal actual fun platformEpochMillis(): Long = System.currentTimeMillis()

internal actual fun platformActiveFolderId(): String? = JvmFolderSync.activeFolderId

internal actual fun platformForgetFolder(id: String) = JvmFolderSync.forget(id)

internal actual val platformSupportsLanding: Boolean = false

internal actual fun platformInstallLanding(configJson: String) = Unit

internal actual fun platformHideLanding() = Unit

internal actual fun platformLandingPendingActionJson(): String? = null

/** JVM implementation of the common live-folder contract. */
private object JvmFolderSync {
    private val stateLock = Any()
    private val settingsDir: Path = Path.of(System.getProperty("user.home") ?: ".", ".mission-visualization")
    private val savedRootFile: Path = settingsDir.resolve("folder-sync-root.txt")
    private val expectedWrites = ConcurrentHashMap<Path, Int>()
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null
    private var activeRoot: Path? = null
    private var savedRoot: Path? = null
    private val watchKeys = ConcurrentHashMap<WatchKey, Path>()

    val revision = AtomicInteger(0)
    @Volatile var snapshotJson: String? = null
    @Volatile var status: String = "idle"
    @Volatile var activeFolderId: String? = null

    fun init() {
        if (activeRoot != null) return
        savedRoot = runCatching {
            savedRootFile.takeIf(Files::isRegularFile)?.let { Path.of(Files.readString(it)).toRealPath() }
        }.getOrNull()?.takeIf(Files::isDirectory)
        status = if (savedRoot == null) "idle" else "reconnect-needed"
    }

    fun chooseFolder(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a folder with .layout.md files"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            (activeRoot ?: savedRoot)?.toFile()?.let { currentDirectory = it }
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else null
    }

    fun reconnect() {
        val root = savedRoot
        if (root == null || !Files.isDirectory(root)) {
            status = "error"
            return
        }
        connect(root)
    }

    fun connect(candidate: Path, persist: Boolean = true) {
        status = "connecting"
        runCatching {
            val root = candidate.toRealPath()
            require(Files.isDirectory(root)) { "Folder is not accessible: $root" }
            synchronized(stateLock) {
                stopWatcher()
                activeRoot = root
                activeFolderId = root.toString()
                if (persist) {
                    savedRoot = root
                    Files.createDirectories(settingsDir)
                    Files.writeString(savedRootFile, root.toString())
                }
                refreshSnapshot(root)
                startWatcher(root)
                status = "watching"
            }
        }.onFailure { fail(it.message ?: "Unable to connect folder") }
    }

    fun disconnect(forget: Boolean) {
        synchronized(stateLock) {
            stopWatcher()
            activeRoot = null
            activeFolderId = null
            snapshotJson = null
            expectedWrites.clear()
            if (forget) {
                savedRoot = null
                runCatching { Files.deleteIfExists(savedRootFile) }
            }
            status = if (!forget && savedRoot != null) "reconnect-needed" else "idle"
        }
    }

    fun fail(message: String) {
        System.err.println("Folder sync: $message")
        status = "error"
    }

    fun savedFolderName(): String? = (activeRoot ?: savedRoot)?.fileName?.toString()

    fun forget(id: String) {
        if (savedRoot?.toString() == id || activeFolderId == id) disconnect(forget = true)
    }

    fun writeInitial(root: Path, source: MissionDocumentSource) {
        val canonicalRoot = root.toAbsolutePath().normalize()
        val target = safeResolve(canonicalRoot, source.fileName)
        atomicWrite(target, source.content.toByteArray(StandardCharsets.UTF_8))
    }

    fun writeFromEditor(fileName: String, content: String) {
        val root = activeRoot ?: return
        runCatching {
            val target = safeResolve(root, fileName)
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            expectedWrites[target] = bytes.contentHashCode()
            atomicWrite(target, bytes)
        }.onFailure { fail(it.message ?: "Unable to write $fileName") }
    }

    private fun safeResolve(root: Path, relativeName: String): Path {
        require(!Path.of(relativeName).isAbsolute) { "Expected a relative project file path" }
        val target = root.resolve(relativeName.replace('/', java.io.File.separatorChar)).normalize().toAbsolutePath()
        require(target.startsWith(root.toAbsolutePath().normalize())) { "Project path escapes the connected folder" }
        require(target.fileName.toString().endsWith(".layout.md", ignoreCase = true)) { "Only .layout.md files are writable" }
        return target
    }

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".mv-write-", ".tmp")
        try {
            Files.write(temp, bytes)
            runCatching {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun startWatcher(root: Path) {
        val watcher = FileSystems.getDefault().newWatchService()
        watchService = watcher
        registerRecursively(root, watcher)
        watchThread = Thread({ watchLoop(root, watcher) }, "mission-folder-watch").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopWatcher() {
        runCatching { watchService?.close() }
        watchService = null
        watchThread = null
        watchKeys.clear()
    }

    private fun registerRecursively(root: Path, watcher: WatchService) {
        Files.walk(root).use { paths ->
            paths.filter(Files::isDirectory).forEach { directory ->
                val key = directory.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )
                watchKeys[key] = directory
            }
        }
    }

    private fun watchLoop(root: Path, watcher: WatchService) {
        try {
            while (watchService === watcher) {
                val key = watcher.take()
                val directory = watchKeys[key] ?: root
                var relevantExternalChange = false
                key.pollEvents().forEach { event ->
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        relevantExternalChange = true
                        return@forEach
                    }
                    @Suppress("UNCHECKED_CAST")
                    val changed = directory.resolve((event.context() as Path)).toAbsolutePath().normalize()
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                        runCatching { registerRecursively(changed, watcher) }
                    }
                    if (changed.fileName.toString().endsWith(".layout.md", ignoreCase = true)) {
                        if (!consumeExpectedWrite(changed)) relevantExternalChange = true
                    }
                }
                if (!key.reset()) watchKeys.remove(key)
                if (relevantExternalChange) {
                    Thread.sleep(80)
                    runCatching { refreshSnapshot(root) }.onFailure { fail(it.message ?: "Unable to read folder") }
                }
            }
        } catch (_: ClosedWatchServiceException) {
            // Normal shutdown.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (failure: Throwable) {
            if (watchService === watcher) fail(failure.message ?: "Folder watcher failed")
        }
    }

    private fun consumeExpectedWrite(path: Path): Boolean {
        val expected = expectedWrites[path] ?: return false
        val actual = runCatching { Files.readAllBytes(path).contentHashCode() }.getOrNull() ?: run {
            expectedWrites.remove(path)
            return false
        }
        return if (actual == expected) {
            // Atomic replace can produce more than one CREATE/MODIFY event on Windows. Keep the
            // expected hash until the file changes again so every duplicate echo is suppressed.
            true
        } else {
            expectedWrites.remove(path)
            false
        }
    }

    private fun refreshSnapshot(root: Path) {
        val sources = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".layout.md", ignoreCase = true) }
                .sorted()
                .map { file ->
                    MissionDocumentSource(
                        fileName = root.relativize(file).joinToString("/") { it.toString() },
                        content = Files.readString(file),
                    )
                }
                .toList()
        }
        val next = encodeProjectSourcesJson(root.fileName?.toString().orEmpty(), sources)
        if (next != snapshotJson) {
            snapshotJson = next
            revision.incrementAndGet()
        }
    }
}

// Narrow JVM test seam; production callers use the expect/actual functions above.
internal fun platformConnectFolderForTest(path: Path) = JvmFolderSync.connect(path, persist = false)

internal fun platformResetFolderSyncForTest() = JvmFolderSync.disconnect(forget = false)
