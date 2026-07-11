package io.aequicor.visualization.editor.platform

internal actual fun platformOpenProjectZipArchive() = Unit

internal actual fun platformOpenProjectFolder() = Unit

internal actual fun platformSaveProjectFolder(sourcesJson: String) = Unit

internal actual fun platformDownloadProjectZip(sourcesJson: String) = Unit

internal actual fun platformExportCanvasPng(fileName: String, crop: CanvasExportCrop?) = Unit

internal actual fun platformBeginPdfExport() = Unit

internal actual fun platformAppendCanvasPdfPage(title: String, crop: CanvasExportCrop?) = Unit

internal actual fun platformFinishPdfExport(fileName: String) = Unit

internal actual fun platformToggleFullscreen() = Unit

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun platformOpenUrl(url: String) {
    platform.Foundation.NSURL.URLWithString(url)?.let { nsUrl ->
        platform.UIKit.UIApplication.sharedApplication.openURL(nsUrl)
    }
}
