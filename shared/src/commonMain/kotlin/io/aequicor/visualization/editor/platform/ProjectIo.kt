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

internal expect fun platformOpenProjectZipArchive()

internal expect fun platformOpenProjectFolder()

internal expect fun platformSaveProjectFolder(sourcesJson: String)

internal expect fun platformDownloadProjectZip(sourcesJson: String)

internal expect fun platformExportCanvasPng(fileName: String, crop: CanvasExportCrop?)

internal expect fun platformBeginPdfExport()

internal expect fun platformAppendCanvasPdfPage(title: String, crop: CanvasExportCrop?)

internal expect fun platformFinishPdfExport(fileName: String)

internal expect fun platformToggleFullscreen()

/** Copies plain text to the platform clipboard (best-effort; no-op where unavailable). */
internal expect fun platformCopyTextToClipboard(text: String)

/** Opens an external URL in the platform browser (best-effort; no-op where unavailable). */
internal expect fun platformOpenUrl(url: String)
