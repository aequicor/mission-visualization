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

/**
 * Re-establishes access to the previously connected folder. Must be called from a user gesture:
 * the browser re-prompts for permission on a fresh page load.
 */
internal expect fun platformReconnectSavedFolder()

/** Stops the watcher and forgets the live connection. */
internal expect fun platformDisconnectFolder()

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

/**
 * Writes [content] to [fileName] in the connected folder, recording the write so the watcher does
 * not mistake this echo for an external change. No-op when not connected.
 */
internal expect fun platformWriteFolderFile(fileName: String, content: String)
