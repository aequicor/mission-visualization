package io.aequicor.visualization.editor.platform

import kotlin.js.ExperimentalWasmJsInterop

/**
 * DOM-level drag-drop and paste ingestion for the wasm canvas. The Compose canvas sits in a
 * shadow-root, so image files dropped/pasted from the OS arrive as document-level DOM events, not
 * Compose events. Listeners read the first image file, hand back its base64 bytes + intrinsic size
 * + drop coordinates, and the Kotlin caller maps the point into the canvas and dispatches.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun installResourceIngestion(
    onDrop: (base64: String, name: String, width: Double, height: Double, clientX: Double, clientY: Double) -> Unit,
    onPaste: (base64: String, name: String, width: Double, height: Double) -> Unit,
    onDragOver: (active: Boolean) -> Unit,
    onError: (error: IngestionError) -> Unit,
): ResourceIngestionHandle {
    installIngestionListeners(
        onDrop,
        onPaste,
        { active -> onDragOver(active != 0) },
        // JS marshals a plain code: 1 = no image in the payload, 2 = file read/decrypt failed.
        { code -> onError(if (code == 1) IngestionError.UnsupportedType else IngestionError.ReadFailed) },
    )
    return object : ResourceIngestionHandle {
        override fun dispose() = removeIngestionListeners()
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun installIngestionListeners(
    onDrop: (String, String, Double, Double, Double, Double) -> Unit,
    onPaste: (String, String, Double, Double) -> Unit,
    onDragOver: (Int) -> Unit,
    onError: (Int) -> Unit,
): Unit =
    js(
        """{
      if (window.__mvResIngest) { return; }

      function extFromType(type) {
        if (type === "image/png") return ".png";
        if (type === "image/jpeg") return ".jpg";
        if (type === "image/svg+xml") return ".svg";
        if (type === "image/gif") return ".gif";
        if (type === "image/webp") return ".webp";
        return ".png";
      }

      function handleFile(file, clientX, clientY, isPaste) {
        var reader = new FileReader();
        reader.onload = function () {
          var dataUrl = String(reader.result || "");
          var base64 = (dataUrl.split(",")[1]) || "";
          if (!base64) { return; }
          var name = file.name || ("pasted-" + Date.now() + extFromType(file.type || ""));
          var img = new Image();
          img.onload = function () {
            var w = img.naturalWidth || img.width || 0;
            var h = img.naturalHeight || img.height || 0;
            if (isPaste) { onPaste(base64, name, w, h); } else { onDrop(base64, name, w, h, clientX, clientY); }
          };
          img.onerror = function () {
            if (isPaste) { onPaste(base64, name, 0, 0); } else { onDrop(base64, name, 0, 0, clientX, clientY); }
          };
          img.src = dataUrl;
        };
        reader.onerror = function () { onError(2); };
        reader.readAsDataURL(file);
      }

      function firstImage(fileList) {
        if (!fileList) { return null; }
        for (var i = 0; i < fileList.length; i++) {
          var f = fileList[i];
          if (f && f.type && f.type.indexOf("image/") === 0) { return f; }
        }
        return null;
      }

      // dragover fires continuously while a drag hovers the page; a short timeout auto-clears the
      // active state when it stops (drag left the window or dropped) — robust against the flicker of
      // dragenter/dragleave crossing child elements without tracking a counter.
      var dragTimer = null;
      function markDragActive() {
        onDragOver(1);
        if (dragTimer) { clearTimeout(dragTimer); }
        dragTimer = setTimeout(function () { onDragOver(0); dragTimer = null; }, 160);
      }
      function clearDragActive() {
        if (dragTimer) { clearTimeout(dragTimer); dragTimer = null; }
        onDragOver(0);
      }

      function isFileDrag(e) {
        var types = (e.dataTransfer && e.dataTransfer.types) || [];
        return Array.prototype.indexOf.call(types, "Files") >= 0;
      }

      function onDragOverEvt(e) {
        if (isFileDrag(e)) { e.preventDefault(); markDragActive(); }
      }

      function onDropEvt(e) {
        clearDragActive();
        if (!e.dataTransfer) { return; }
        var file = firstImage(e.dataTransfer.files);
        if (!file) {
          // A file was dropped but nothing was an image — tell the user instead of a silent no-op.
          if (e.dataTransfer.files && e.dataTransfer.files.length > 0) { e.preventDefault(); onError(1); }
          return;
        }
        e.preventDefault();
        handleFile(file, e.clientX, e.clientY, false);
      }

      function onPasteEvt(e) {
        var items = (e.clipboardData && e.clipboardData.items) || [];
        for (var i = 0; i < items.length; i++) {
          if (items[i].kind === "file" && items[i].type && items[i].type.indexOf("image/") === 0) {
            var f = items[i].getAsFile();
            if (f) { e.preventDefault(); handleFile(f, 0, 0, true); return; }
          }
        }
      }

      document.addEventListener("dragover", onDragOverEvt, false);
      document.addEventListener("drop", onDropEvt, false);
      document.addEventListener("paste", onPasteEvt, false);
      window.__mvResIngest = { dragover: onDragOverEvt, drop: onDropEvt, paste: onPasteEvt };
    }"""
    )

@OptIn(ExperimentalWasmJsInterop::class)
private fun removeIngestionListeners(): Unit =
    js(
        """{
      if (window.__mvResIngest) {
        document.removeEventListener("dragover", window.__mvResIngest.dragover, false);
        document.removeEventListener("drop", window.__mvResIngest.drop, false);
        document.removeEventListener("paste", window.__mvResIngest.paste, false);
        window.__mvResIngest = null;
      }
    }"""
    )
