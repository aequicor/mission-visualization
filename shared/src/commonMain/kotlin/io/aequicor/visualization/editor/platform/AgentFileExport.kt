package io.aequicor.visualization.editor.platform

/** True when this platform can download generated agent Markdown files. */
internal expect val platformSupportsAgentFileExport: Boolean

/** Downloads [markdown] as a UTF-8 Markdown file named [fileName]. */
internal expect fun platformDownloadAgentFile(fileName: String, markdown: String)
