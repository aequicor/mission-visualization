package io.aequicor.visualization.editor.platform

import kotlin.js.ExperimentalWasmJsInterop

internal actual val platformSupportsAgentFileExport: Boolean = true

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformDownloadAgentFile(fileName: String, markdown: String) {
    downloadAgentFile(fileName, markdown)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun downloadAgentFile(fileName: String, markdown: String): Unit = js(
    """
    {
      const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = fileName;
      anchor.style.display = "none";
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 0);
    }
    """,
)
