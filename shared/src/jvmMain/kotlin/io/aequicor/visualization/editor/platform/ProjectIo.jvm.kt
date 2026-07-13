package io.aequicor.visualization.editor.platform

import io.aequicor.visualization.editor.data.decodeProjectSnapshot
import io.aequicor.visualization.editor.data.EditorStateRelativePath
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

internal actual val platformProjectLandingMode: ProjectLandingMode = ProjectLandingMode.Compose

internal actual val platformProjectStorageMode: ProjectStorageMode = ProjectStorageMode.DiskOnly

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

internal actual fun platformConnectFolderById(id: String) = JvmFolderSync.connectById(id)

internal actual fun platformCreateFolderProject(sourcesJson: String) {
    val snapshot = decodeProjectSnapshot(sourcesJson) ?: return
    val folder = JvmFolderSync.chooseFolder() ?: return
    JvmFolderSync.createProject(folder, snapshot.sources)
}

internal actual fun platformReconnectSavedFolder() = JvmFolderSync.reconnect()

internal actual fun platformDisconnectFolder() = JvmFolderSync.disconnect(forget = true)

internal actual fun platformRefreshFolder() = JvmFolderSync.refresh()

internal actual fun platformSavedFolderName(): String? = JvmFolderSync.savedFolderName()

internal actual fun folderSyncRevision(): Int = JvmFolderSync.revision.get()

internal actual fun folderSyncSnapshotJson(): String? = JvmFolderSync.snapshotJson

internal actual fun folderSyncStatus(): String? = JvmFolderSync.status

internal actual fun folderSyncError(): String? = JvmFolderSync.lastError

internal actual fun platformWriteFolderFiles(writes: List<FolderFileWrite>): Boolean =
    JvmFolderSync.writeBatchFromEditor(writes)

internal actual fun platformWriteFolderFile(fileName: String, content: String) =
    JvmFolderSync.writeFromEditor(fileName, content)

internal actual fun platformWriteFolderEditorState(content: String) =
    JvmFolderSync.writeEditorState(content)

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
    private val expectedDeletes = ConcurrentHashMap.newKeySet<Path>()
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null
    private var activeRoot: Path? = null
    private var savedRoot: Path? = null
    private val watchKeys = ConcurrentHashMap<WatchKey, Path>()

    val revision = AtomicInteger(0)
    @Volatile var snapshotJson: String? = null
    @Volatile var status: String = "idle"
    @Volatile var activeFolderId: String? = null
    @Volatile var lastError: String? = null

    fun init() {
        if (activeRoot != null) return
        savedRoot = runCatching {
            savedRootFile.takeIf(Files::isRegularFile)?.let { Path.of(Files.readString(it)).toRealPath() }
        }.getOrNull()?.takeIf(Files::isDirectory)
        // Desktop paths do not require a browser-style permission reconnect. The remembered path
        // is only the initial directory for the next native folder chooser; recents drive opening.
        status = "idle"
    }

    fun chooseFolder(): Path? {
        return chooseNativeFolder(
            title = "Choose a folder with .layout.md files",
            initialDirectory = activeRoot ?: savedRoot,
        )
    }

    fun reconnect() {
        val root = savedRoot
        if (root == null || !Files.isDirectory(root)) {
            status = "error"
            return
        }
        connect(root)
    }

    fun connectById(id: String) {
        runCatching { Path.of(id) }
            .onSuccess(::connect)
            .onFailure { fail("project-unavailable") }
    }

    fun createProject(folder: Path, sources: List<MissionDocumentSource>) {
        status = "connecting"
        lastError = null
        runCatching {
            val root = folder.toRealPath()
            require(Files.isDirectory(root)) { "project-unavailable" }
            val alreadyContainsProject = Files.walk(root).use { paths ->
                paths.anyMatch { Files.isRegularFile(it) && it.fileName.toString().endsWith(".layout.md", ignoreCase = true) }
            }
            require(!alreadyContainsProject) {
                "folder-contains-project"
            }
            sources.forEach { source ->
                val target = safeResolve(root, source.fileName)
                require(!Files.exists(target)) { "project-file-exists" }
            }
            sources.forEach { source -> writeInitial(root, source) }
            root
        }.onSuccess(::connect)
            .onFailure { failure ->
                val code = failure.message?.takeIf {
                    it == "project-unavailable" || it == "folder-contains-project" || it == "project-file-exists"
                } ?: "project-create-failed"
                fail(code)
            }
    }

    fun connect(candidate: Path, persist: Boolean = true) {
        status = "connecting"
        lastError = null
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
                lastError = null
            }
        }.onFailure { fail("project-unavailable") }
    }

    fun disconnect(forget: Boolean) {
        synchronized(stateLock) {
            stopWatcher()
            activeRoot = null
            activeFolderId = null
            snapshotJson = null
            expectedWrites.clear()
            expectedDeletes.clear()
            if (forget) {
                savedRoot = null
                runCatching { Files.deleteIfExists(savedRootFile) }
            }
            status = "idle"
            lastError = null
        }
    }

    fun refresh() {
        synchronized(stateLock) {
            val root = activeRoot ?: return
            if (status != "watching") return
            runCatching { refreshSnapshot(root) }
                .onFailure { fail(it.message ?: "Unable to read folder") }
        }
    }

    fun fail(message: String) {
        System.err.println("Folder sync: $message")
        lastError = message
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

    /**
     * Compare-and-swap all affected source files. Temp files are fully written before the commit
     * phase. Existing targets are moved to same-directory backups, making rollback possible if an
     * atomic replace in the middle of a multi-file transaction fails.
     */
    fun writeBatchFromEditor(writes: List<FolderFileWrite>): Boolean = synchronized(stateLock) {
        val root = activeRoot ?: return@synchronized false
        if (status != "watching") return@synchronized false
        if (writes.isEmpty()) return@synchronized true

        data class Prepared(
            val write: FolderFileWrite,
            val target: Path,
            val bytes: ByteArray?,
            var staged: Path? = null,
            var backup: Path? = null,
            var installed: Boolean = false,
        )

        val prepared = mutableListOf<Prepared>()
        try {
            writes.forEach { write ->
                val target = safeResolve(root, write.fileName)
                val actual = target.takeIf(Files::isRegularFile)?.let(Files::readString)
                require(actual == write.baseContent) { "folder-write-conflict:${write.fileName}" }
                val bytes = write.content?.toByteArray(StandardCharsets.UTF_8)
                val item = Prepared(write, target, bytes)
                if (bytes != null) {
                    Files.createDirectories(target.parent)
                    item.staged = Files.createTempFile(target.parent, ".mv-stage-", ".tmp").also {
                        Files.write(it, bytes)
                    }
                }
                prepared += item
            }

            prepared.forEach { item ->
                if (Files.exists(item.target)) {
                    val backup = Files.createTempFile(item.target.parent, ".mv-backup-", ".tmp")
                    Files.deleteIfExists(backup)
                    moveReplacing(item.target, backup)
                    item.backup = backup
                }
                item.staged?.let { staged ->
                    moveReplacing(staged, item.target)
                    item.staged = null
                    item.installed = true
                }
            }

            prepared.forEach { item ->
                item.backup?.let(Files::deleteIfExists)
                item.bytes?.let { bytes ->
                    expectedDeletes.remove(item.target)
                    expectedWrites[item.target] = bytes.contentHashCode()
                } ?: run {
                    expectedWrites.remove(item.target)
                    expectedDeletes.add(item.target)
                }
            }
            true
        } catch (failure: Throwable) {
            prepared.asReversed().forEach { item ->
                runCatching {
                    if (item.installed) Files.deleteIfExists(item.target)
                    item.backup?.takeIf(Files::exists)?.let { moveReplacing(it, item.target) }
                }
            }
            val message = failure.message ?: "Unable to commit folder transaction"
            if (message.startsWith("folder-write-conflict:")) {
                // Keep watching and publish the conflicting disk version immediately. The holder
                // retains the local sources as its recoverable conflict backup when it adopts this
                // newer revision.
                lastError = message
                refreshSnapshot(root)
                status = "watching"
            } else {
                fail(message)
            }
            false
        } finally {
            prepared.forEach { item ->
                item.staged?.let { runCatching { Files.deleteIfExists(it) } }
                item.backup?.let { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }

    fun writeEditorState(content: String) {
        val root = activeRoot ?: return
        runCatching {
            val target = root.resolve(EditorStateRelativePath).normalize().toAbsolutePath()
            require(target.startsWith(root)) { "Editor state path escapes the project folder" }
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            expectedWrites[target] = bytes.contentHashCode()
            atomicWrite(target, bytes)
        }.onFailure { fail(it.message ?: "Unable to write editor state") }
    }

    private fun safeResolve(root: Path, relativeName: String): Path {
        require(!Path.of(relativeName).isAbsolute) { "Expected a relative project file path" }
        val canonicalRoot = root.toRealPath()
        val target = canonicalRoot.resolve(relativeName.replace('/', java.io.File.separatorChar)).normalize().toAbsolutePath()
        require(target.startsWith(canonicalRoot)) { "Project path escapes the connected folder" }
        val existingAncestor = generateSequence(target.parent) { it.parent }.firstOrNull { Files.exists(it) }
            ?: error("Project target has no accessible parent")
        require(existingAncestor.toRealPath().startsWith(canonicalRoot)) {
            "Project path escapes the connected folder through a symbolic link"
        }
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

    private fun moveReplacing(from: Path, to: Path) {
        runCatching {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
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
                    if (
                        changed.fileName.toString().endsWith(".layout.md", ignoreCase = true) ||
                        changed == root.resolve(EditorStateRelativePath).normalize().toAbsolutePath()
                    ) {
                        if (!consumeExpectedWrite(changed)) relevantExternalChange = true
                    }
                }
                if (!key.reset()) watchKeys.remove(key)
                if (relevantExternalChange) {
                    Thread.sleep(80)
                    // A closed watcher can still finish a batch it took before project switching.
                    // Serialize the final identity check with connect/disconnect so an old folder
                    // can never publish its snapshot over the newly active project.
                    synchronized(stateLock) {
                        if (watchService === watcher && activeRoot == root) {
                            runCatching { refreshSnapshot(root, suppressEditorEcho = true) }
                                .onFailure { fail(it.message ?: "Unable to read folder") }
                        }
                    }
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
        if (expectedDeletes.remove(path) && !Files.exists(path)) return true
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

    private fun refreshSnapshot(root: Path, suppressEditorEcho: Boolean = false) {
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
        val editorStatePath = root.resolve(EditorStateRelativePath)
        val editorStateJson = editorStatePath.takeIf(Files::isRegularFile)?.let(Files::readString)
        val next = encodeProjectSourcesJson(root.fileName?.toString().orEmpty(), sources, editorStateJson)
        if (next != snapshotJson) {
            val previousSnapshot = snapshotJson?.let(::decodeProjectSnapshot)
            val previousByName = previousSnapshot?.sources.orEmpty()
                .associate { it.fileName to it.content }
            val nextByName = sources.associate { it.fileName to it.content }
            val changedNames = previousByName.keys.union(nextByName.keys)
                .filter { previousByName[it] != nextByName[it] }
            val sourceChangesAreEditorEchoes = changedNames.all { fileName ->
                val target = root.resolve(fileName.replace('/', java.io.File.separatorChar)).normalize().toAbsolutePath()
                val content = nextByName[fileName]
                if (content == null) {
                    target in expectedDeletes
                } else {
                    expectedWrites[target] == content.toByteArray(StandardCharsets.UTF_8).contentHashCode()
                }
            }
            val editorStateChanged = previousSnapshot?.editorStateJson != editorStateJson
            val editorStateIsEditorEcho = !editorStateChanged || editorStateJson?.let { content ->
                val target = root.resolve(EditorStateRelativePath).normalize().toAbsolutePath()
                expectedWrites[target] == content.toByteArray(StandardCharsets.UTF_8).contentHashCode()
            } == true
            val editorEchoOnly = suppressEditorEcho &&
                (changedNames.isNotEmpty() || editorStateChanged) &&
                sourceChangesAreEditorEchoes && editorStateIsEditorEcho
            snapshotJson = next
            if (!editorEchoOnly) revision.incrementAndGet()
        }
    }
}

// Narrow JVM test seam; production callers use the expect/actual functions above.
internal fun platformConnectFolderForTest(path: Path) = JvmFolderSync.connect(path, persist = false)

internal fun platformConnectFolderByIdForTest(id: String) = JvmFolderSync.connectById(id)

internal fun platformCreateFolderForTest(path: Path, sourcesJson: String) {
    val snapshot = decodeProjectSnapshot(sourcesJson) ?: error("Invalid project snapshot")
    JvmFolderSync.createProject(path, snapshot.sources)
}

internal fun platformResetFolderSyncForTest() = JvmFolderSync.disconnect(forget = false)
