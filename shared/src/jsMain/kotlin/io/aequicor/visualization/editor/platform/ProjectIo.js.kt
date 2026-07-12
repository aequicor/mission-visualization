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
    kotlinx.browser.window.open(url, "_blank")
}

internal actual fun platformSetActiveProjectId(id: String) = Unit

internal actual val platformSupportsFolderSync: Boolean = false

internal actual fun platformInitFolderSync() = Unit

internal actual fun platformConnectFolderLive() = Unit

internal actual fun platformCreateFolderProject(sourcesJson: String) = Unit

internal actual fun platformReconnectSavedFolder() = Unit

internal actual fun platformDisconnectFolder() = Unit

internal actual fun platformSavedFolderName(): String? = null

internal actual fun folderSyncRevision(): Int = 0

internal actual fun folderSyncSnapshotJson(): String? = null

internal actual fun folderSyncStatus(): String? = null

internal actual fun platformWriteFolderFile(fileName: String, content: String) = Unit

internal actual fun platformEpochMillis(): Long = kotlin.js.Date.now().toLong()

internal actual fun platformActiveFolderId(): String? = null

internal actual fun platformForgetFolder(id: String) = Unit

internal actual val platformSupportsLanding: Boolean = false

internal actual fun platformInstallLanding(configJson: String) = Unit

internal actual fun platformHideLanding() = Unit

internal actual fun platformLandingPendingActionJson(): String? = null
