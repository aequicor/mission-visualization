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

internal actual fun platformCopyTextToClipboard(text: String): Unit = js(
    """
    (function () {
      var value = String(text || "");
      function fallback() {
        try {
          var area = document.createElement("textarea");
          area.value = value;
          area.setAttribute("readonly", "");
          area.style.position = "fixed";
          area.style.left = "-10000px";
          document.body.appendChild(area);
          area.select();
          document.execCommand("copy");
          area.remove();
        } catch (e) {}
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(value).catch(fallback);
      } else {
        fallback();
      }
    })()
    """,
)

internal actual fun platformOpenUrl(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}
