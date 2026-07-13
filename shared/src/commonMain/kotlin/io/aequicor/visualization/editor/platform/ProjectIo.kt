package io.aequicor.visualization.editor.platform

data class CanvasExportBounds(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val density: Float,
)

data class CanvasExportCrop(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/** How this platform presents the project picker at application startup. */
internal enum class ProjectLandingMode { None, WebDom, Compose }

/** Whether a project may live in app-private draft storage without a folder. */
internal enum class ProjectStorageMode { EmbeddedDraft, DiskOnly }

internal expect val platformProjectLandingMode: ProjectLandingMode

internal expect val platformProjectStorageMode: ProjectStorageMode

/** Wall-clock time in epoch milliseconds; used to stamp recent-project recency. */
internal expect fun platformEpochMillis(): Long

/**
 * True where the open/save-project-to-disk actions are actually implemented (web today);
 * platforms with stub actuals hide those menu items instead of silently doing nothing.
 */
internal expect val platformSupportsProjectDiskIo: Boolean

internal expect fun platformOpenProjectZipArchive()

internal expect fun platformOpenProjectFolder()

/** Saves the project to a user-picked folder; [onSaved] runs only after a successful write. */
internal expect fun platformSaveProjectFolder(sourcesJson: String, onSaved: () -> Unit)

/** Downloads the project as a ZIP; [onSaved] runs only after the download is handed off. */
internal expect fun platformDownloadProjectZip(sourcesJson: String, onSaved: () -> Unit)

internal expect fun platformExportCanvasPng(fileName: String, crop: CanvasExportCrop?)

internal expect fun platformBeginPdfExport()

internal expect fun platformAppendCanvasPdfPage(title: String, crop: CanvasExportCrop?)

internal expect fun platformFinishPdfExport(fileName: String)

internal expect fun platformToggleFullscreen()

/** Opens an external URL in the platform browser (best-effort; no-op where unavailable). */
internal expect fun platformOpenUrl(url: String)

/** Replaces the current URL hash with the active project's stable id. */
internal expect fun platformSetActiveProjectId(id: String)

// --- Live local-folder sync (the "browser IDE" mode) ------------------------------------------
// Real only on wasmJs (Chromium File System Access API): a picked folder's handle is persisted in
// IndexedDB, a watcher re-reads externally-changed *.layout.md into the canvas, and editor edits
// write back to the folder. All the async File System Access + IndexedDB work lives in a JS blob
// (window.__mvFolderSync); Kotlin drives it through the cheap synchronous getters below and a
// coroutine that polls [folderSyncRevision]. Every non-wasm target stubs these out.

/**
 * True where live folder sync is available (Chromium web only: `window.showDirectoryPicker`).
 * Platforms without it hide the menu entry instead of offering an action that does nothing.
 */
internal expect val platformSupportsFolderSync: Boolean

/**
 * Installs the JS sync layer and probes for a previously connected folder: if the browser still
 * holds permission it auto-resumes watching, otherwise it reports `reconnect-needed`. Call once on
 * boot (safe to call on every target — non-web is a no-op).
 */
internal expect fun platformInitFolderSync()

/** Picks a folder (readwrite), persists its handle, enumerates sources and starts the watcher. */
internal expect fun platformConnectFolderLive()

/** Opens a previously recorded folder by its stable id (an absolute path on desktop). */
internal expect fun platformConnectFolderById(id: String)

/** Creates a live folder project from the current in-memory sources. */
internal expect fun platformCreateFolderProject(sourcesJson: String)

/**
 * Re-establishes access to the previously connected folder. Must be called from a user gesture:
 * the browser re-prompts for permission on a fresh page load.
 */
internal expect fun platformReconnectSavedFolder()

/** Stops the watcher and forgets the live connection. */
internal expect fun platformDisconnectFolder()

/** Re-reads every supported source file from the currently watched folder. No-op when disconnected. */
internal expect fun platformRefreshFolder()

/** Name of the saved/connected folder, or null when none is remembered. */
internal expect fun platformSavedFolderName(): String?

/** Monotonic counter, bumped whenever a fresh source snapshot is ready to pull. */
internal expect fun folderSyncRevision(): Int

/** The current source set as a `ProjectFilesEnvelope` JSON, or null when disconnected. */
internal expect fun folderSyncSnapshotJson(): String?

/**
 * Coarse connection state, one of: `idle`, `connecting`, `reconnect-needed`, `watching`,
 * `error`. Null is treated as `idle`.
 */
internal expect fun folderSyncStatus(): String?

/** Last human-readable folder error, cleared when a new operation starts or succeeds. */
internal expect fun folderSyncError(): String?

/** One compare-and-swap entry in an outbound folder transaction. */
internal data class FolderFileWrite(
    val fileName: String,
    /** Content observed at the last successful sync; null means the file must not exist. */
    val baseContent: String?,
    /** New content, or null to delete the file. */
    val content: String?,
)

/**
 * Commits all [writes] as one optimistic transaction. Implementations must verify every base
 * before changing any target. False means nothing was committed (or a partial commit was rolled
 * back) and the caller must keep its previous sync base.
 */
internal expect fun platformWriteFolderFiles(writes: List<FolderFileWrite>): Boolean

/**
 * Writes [content] to [fileName] in the connected folder, recording the write so the watcher does
 * not mistake this echo for an external change. No-op when not connected.
 */
internal expect fun platformWriteFolderFile(fileName: String, content: String)

/** Id of the folder currently connected/watched, or null when none — used to record recents. */
internal expect fun platformActiveFolderId(): String?

/** Forgets a saved folder's directory handle by [id] (IndexedDB). No-op where unsupported. */
internal expect fun platformForgetFolder(id: String)

// --- Startup landing ("recent projects + Welcome") --------------------------------------------
// A DOM overlay (window.__mvLanding, a sibling of window.__mvProjectIo / window.__mvFolderSync)
// shown over the editor on boot. Kotlin owns the copy (localized string catalog) and colors
// (theme tokens) and hands them to the overlay as a JSON config; the overlay owns the DOM,
// the URL-hash deep-link, the click gestures (folder re-grant must run inside the click) and the
// IndexedDB handle list. The user's choice is read back through a small polled action queue.
// Real only on wasmJs; every other target stubs these out (the landing is a web-startup concept).

/** True where the startup landing overlay is available (web). */
internal expect val platformSupportsLanding: Boolean

/**
 * Renders (or re-renders) the landing overlay from [configJson]: localized strings, theme colors,
 * the recent-project list, feature flags and the URL-hash deep-link target. Safe to call repeatedly.
 */
internal expect fun platformInstallLanding(configJson: String)

/** Hides and tears down the landing overlay (the user picked a project or dismissed it). */
internal expect fun platformHideLanding()

/**
 * Dequeues the next user action from the landing as JSON (`{"type":…,"id":…}`), or null when the
 * queue is empty. Polled by the state holder to drive the editor (open Welcome / a folder / recover,
 * remove a recent, or dismiss).
 */
internal expect fun platformLandingPendingActionJson(): String?
