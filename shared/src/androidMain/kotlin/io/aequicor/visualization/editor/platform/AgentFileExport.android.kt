package io.aequicor.visualization.editor.platform

internal actual val platformSupportsAgentFileExport: Boolean = false

internal actual fun platformDownloadAgentFile(fileName: String, markdown: String) = Unit
