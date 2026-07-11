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
