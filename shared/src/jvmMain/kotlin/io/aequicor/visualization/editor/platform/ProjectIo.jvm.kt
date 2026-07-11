package io.aequicor.visualization.editor.platform

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
