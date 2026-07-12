package io.aequicor.visualization.editor.platform

import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Rasterizes SVG on web by drawing it onto an off-screen `<canvas>` at its intrinsic size and
 * reading back a PNG — no Skia SVG dependency. Bytes cross the boundary as base64. A tainted or
 * unloadable SVG yields null (placeholder stays).
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalWasmJsInterop::class)
internal actual suspend fun rasterizeSvgToPng(svgBytes: ByteArray): ByteArray? {
    val svgBase64 = Base64.encode(svgBytes)
    val pngBase64 = suspendCancellableCoroutine { cont ->
        rasterizeSvg(svgBase64) { result -> cont.resume(result) }
    }
    return if (pngBase64.isEmpty()) null else runCatching { Base64.decode(pngBase64) }.getOrNull()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun rasterizeSvg(svgBase64: String, onResult: (String) -> Unit): Unit =
    js(
        """{
      try {
        var binary = atob(svgBase64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        var blob = new Blob([bytes], { type: "image/svg+xml" });
        var url = URL.createObjectURL(blob);
        var img = new Image();
        img.onload = function () {
          var w = Math.max(1, Math.round(img.naturalWidth || img.width || 300));
          var h = Math.max(1, Math.round(img.naturalHeight || img.height || 150));
          var canvas = document.createElement("canvas");
          canvas.width = w; canvas.height = h;
          try {
            canvas.getContext("2d").drawImage(img, 0, 0, w, h);
            var dataUrl = canvas.toDataURL("image/png");
            URL.revokeObjectURL(url);
            onResult((dataUrl.split(",")[1]) || "");
          } catch (e) { URL.revokeObjectURL(url); onResult(""); }
        };
        img.onerror = function () { URL.revokeObjectURL(url); onResult(""); };
        img.src = url;
      } catch (e) { onResult(""); }
    }"""
    )
