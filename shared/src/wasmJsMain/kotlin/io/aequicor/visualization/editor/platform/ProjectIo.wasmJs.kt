package io.aequicor.visualization.editor.platform

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
private fun ensureProjectIoInstalled() {
    js(
        """
        (function () {
          if (window.__mvProjectIo) return;

          var draftKey = "mv.slm.draft.v1";
          var decoder = new TextDecoder("utf-8");
          var encoder = new TextEncoder();
          var pdfPages = [];
          var crcTable = null;

          function run(task) {
            Promise.resolve(task).catch(function (error) {
              console.error(error);
              var message = error && error.message ? error.message : String(error);
              window.alert("Mission Visualization: " + message);
            });
          }

          function safeName(name, fallback) {
            var raw = String(name || fallback || "document.layout.md").split("/").pop().split("\\").pop();
            var cleaned = raw.replace(/[<>:"/\\|?*\u0000-\u001F]+/g, "-").replace(/^\.+/, "").trim();
            return cleaned || fallback || "document.layout.md";
          }

          function safeProjectName(name, fallback) {
            var cleaned = String(name || fallback || "Mission Visualization")
              .replace(/[<>:"/\\|?*\u0000-\u001F]+/g, "-")
              .replace(/^\.+/, "")
              .trim();
            return cleaned || fallback || "Mission Visualization";
          }

          function projectNameFromArchive(name) {
            var raw = String(name || "").split("/").pop().split("\\").pop().replace(/\.zip$/i, "");
            return safeProjectName(raw, "Mission Visualization");
          }

          function projectNameFromPickedFiles(files) {
            for (var i = 0; i < files.length; i++) {
              var rel = files[i].webkitRelativePath || "";
              var root = rel.split("/")[0];
              if (root) return safeProjectName(root, "Mission Visualization");
            }
            return "Mission Visualization";
          }

          function parseProjectJson(sourcesJson) {
            var parsed = JSON.parse(sourcesJson || "{}");
            var files = Array.isArray(parsed.files) ? parsed.files : [];
            return {
              projectName: safeProjectName(parsed.projectName, "Mission Visualization"),
              files: files
                .filter(function (file) { return file && typeof file.fileName === "string" && typeof file.content === "string"; })
                .map(function (file, index) {
                  return {
                    fileName: safeName(file.fileName, "screen-" + (index + 1) + ".layout.md"),
                    content: file.content
                  };
                })
            };
          }

          function parseSourcesJson(sourcesJson) {
            return parseProjectJson(sourcesJson).files;
          }

          function persistSources(files, projectName) {
            if (!files || files.length === 0) {
              window.alert("В выбранном проекте не найдены .layout.md файлы.");
              return;
            }
            files.sort(function (a, b) { return a.fileName.localeCompare(b.fileName); });
            window.localStorage.setItem(draftKey, JSON.stringify({
              schemaVersion: 1,
              projectName: safeProjectName(projectName, "Mission Visualization"),
              files: files
            }));
            window.location.reload();
          }

          function sourceLike(name) {
            var lower = String(name || "").toLowerCase();
            return lower.endsWith(".layout.md") || lower.endsWith(".slm.md") || lower.endsWith(".slm") || lower.endsWith(".md");
          }

          function chooseFile(accept) {
            return new Promise(function (resolve) {
              var input = document.createElement("input");
              input.type = "file";
              input.accept = accept;
              input.style.position = "fixed";
              input.style.left = "-10000px";
              input.onchange = function () {
                resolve(input.files && input.files.length > 0 ? input.files[0] : null);
                input.remove();
              };
              document.body.appendChild(input);
              input.click();
            });
          }

          function chooseDirectoryFiles() {
            return new Promise(function (resolve) {
              var input = document.createElement("input");
              input.type = "file";
              input.multiple = true;
              input.accept = ".layout.md,.slm.md,.slm,.md,text/markdown,text/plain";
              if ("webkitdirectory" in input) {
                input.webkitdirectory = true;
                input.directory = true;
              }
              input.style.position = "fixed";
              input.style.left = "-10000px";
              input.onchange = function () {
                resolve(input.files ? Array.prototype.slice.call(input.files) : []);
                input.remove();
              };
              document.body.appendChild(input);
              input.click();
            });
          }

          function findEndOfCentralDirectory(bytes) {
            var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
            var min = Math.max(0, bytes.length - 65557);
            for (var i = bytes.length - 22; i >= min; i--) {
              if (view.getUint32(i, true) === 0x06054b50) return i;
            }
            throw new Error("ZIP archive is missing an end-of-central-directory record.");
          }

          async function inflateRaw(data) {
            if (typeof DecompressionStream === "undefined") {
              throw new Error("This browser cannot decompress ZIP deflate entries.");
            }
            var stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
            return new Uint8Array(await new Response(stream).arrayBuffer());
          }

          async function unzipSources(arrayBuffer) {
            var bytes = new Uint8Array(arrayBuffer);
            var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
            var eocd = findEndOfCentralDirectory(bytes);
            var count = view.getUint16(eocd + 10, true);
            var centralOffset = view.getUint32(eocd + 16, true);
            var ptr = centralOffset;
            var files = [];
            for (var entry = 0; entry < count; entry++) {
              if (view.getUint32(ptr, true) !== 0x02014b50) throw new Error("ZIP central directory is corrupt.");
              var method = view.getUint16(ptr + 10, true);
              var compressedSize = view.getUint32(ptr + 20, true);
              var nameLength = view.getUint16(ptr + 28, true);
              var extraLength = view.getUint16(ptr + 30, true);
              var commentLength = view.getUint16(ptr + 32, true);
              var localOffset = view.getUint32(ptr + 42, true);
              var name = decoder.decode(bytes.slice(ptr + 46, ptr + 46 + nameLength));
              ptr += 46 + nameLength + extraLength + commentLength;
              if (!sourceLike(name) || name.endsWith("/")) continue;
              if (view.getUint32(localOffset, true) !== 0x04034b50) throw new Error("ZIP local header is corrupt.");
              var localNameLength = view.getUint16(localOffset + 26, true);
              var localExtraLength = view.getUint16(localOffset + 28, true);
              var dataStart = localOffset + 30 + localNameLength + localExtraLength;
              var compressed = bytes.slice(dataStart, dataStart + compressedSize);
              var raw;
              if (method === 0) raw = compressed;
              else if (method === 8) raw = await inflateRaw(compressed);
              else throw new Error("Unsupported ZIP compression method: " + method + ".");
              files.push({ fileName: safeName(name, "screen-" + (files.length + 1) + ".layout.md"), content: decoder.decode(raw) });
            }
            return files;
          }

          async function openZip() {
            var file = await chooseFile(".zip,application/zip,application/x-zip-compressed");
            if (!file) return;
            persistSources(await unzipSources(await file.arrayBuffer()), projectNameFromArchive(file.name));
          }

          async function walkDirectory(handle, prefix, out) {
            for await (var pair of handle.entries()) {
              var name = pair[0];
              var child = pair[1];
              if (child.kind === "file" && sourceLike(name)) {
                var file = await child.getFile();
                out.push({ fileName: safeName(prefix + name, name), content: await file.text() });
              } else if (child.kind === "directory") {
                await walkDirectory(child, prefix + name + "/", out);
              }
            }
          }

          async function readPickedSources(files) {
            var out = [];
            for (var i = 0; i < files.length; i++) {
              var file = files[i];
              var name = file.webkitRelativePath || file.name;
              if (sourceLike(name)) {
                out.push({ fileName: safeName(name, file.name), content: await file.text() });
              }
            }
            return out;
          }

          async function openFolderViaInput() {
            var files = await chooseDirectoryFiles();
            if (!files.length) return;
            persistSources(await readPickedSources(files), projectNameFromPickedFiles(files));
          }

          async function openFolder() {
            if (!window.showDirectoryPicker) {
              await openFolderViaInput();
              return;
            }
            var handle = await window.showDirectoryPicker({ mode: "read" });
            var files = [];
            await walkDirectory(handle, "", files);
            persistSources(files, handle.name);
          }

          function crc32(bytes) {
            if (!crcTable) {
              crcTable = new Uint32Array(256);
              for (var n = 0; n < 256; n++) {
                var c = n;
                for (var k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
                crcTable[n] = c >>> 0;
              }
            }
            var crc = 0xffffffff;
            for (var i = 0; i < bytes.length; i++) crc = crcTable[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
            return (crc ^ 0xffffffff) >>> 0;
          }

          function dosTimeDate(date) {
            var time = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
            var year = Math.max(1980, date.getFullYear()) - 1980;
            var day = (year << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
            return { time: time, date: day };
          }

          function makeZip(files) {
            var chunks = [];
            var central = [];
            var offset = 0;
            var stamp = dosTimeDate(new Date());
            function push(chunk) { chunks.push(chunk); offset += chunk.length; }
            files.forEach(function (file) {
              var nameBytes = encoder.encode(safeName(file.fileName, "document.layout.md"));
              var data = encoder.encode(file.content);
              var crc = crc32(data);
              var localOffset = offset;
              var local = new Uint8Array(30 + nameBytes.length);
              var lv = new DataView(local.buffer);
              lv.setUint32(0, 0x04034b50, true);
              lv.setUint16(4, 20, true);
              lv.setUint16(6, 0x0800, true);
              lv.setUint16(8, 0, true);
              lv.setUint16(10, stamp.time, true);
              lv.setUint16(12, stamp.date, true);
              lv.setUint32(14, crc, true);
              lv.setUint32(18, data.length, true);
              lv.setUint32(22, data.length, true);
              lv.setUint16(26, nameBytes.length, true);
              local.set(nameBytes, 30);
              push(local);
              push(data);

              var cd = new Uint8Array(46 + nameBytes.length);
              var cv = new DataView(cd.buffer);
              cv.setUint32(0, 0x02014b50, true);
              cv.setUint16(4, 20, true);
              cv.setUint16(6, 20, true);
              cv.setUint16(8, 0x0800, true);
              cv.setUint16(10, 0, true);
              cv.setUint16(12, stamp.time, true);
              cv.setUint16(14, stamp.date, true);
              cv.setUint32(16, crc, true);
              cv.setUint32(20, data.length, true);
              cv.setUint32(24, data.length, true);
              cv.setUint16(28, nameBytes.length, true);
              cv.setUint32(42, localOffset, true);
              cd.set(nameBytes, 46);
              central.push(cd);
            });
            var centralOffset = offset;
            var centralSize = 0;
            central.forEach(function (cd) { chunks.push(cd); centralSize += cd.length; offset += cd.length; });
            var eocd = new Uint8Array(22);
            var ev = new DataView(eocd.buffer);
            ev.setUint32(0, 0x06054b50, true);
            ev.setUint16(8, files.length, true);
            ev.setUint16(10, files.length, true);
            ev.setUint32(12, centralSize, true);
            ev.setUint32(16, centralOffset, true);
            chunks.push(eocd);
            return new Blob(chunks, { type: "application/zip" });
          }

          function downloadBlob(blob, fileName) {
            var url = URL.createObjectURL(blob);
            var a = document.createElement("a");
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
          }

          async function saveFolder(sourcesJson) {
            if (!window.showDirectoryPicker) {
              saveZip(sourcesJson);
              return;
            }
            var handle = await window.showDirectoryPicker({ mode: "readwrite" });
            var files = parseSourcesJson(sourcesJson);
            for (var i = 0; i < files.length; i++) {
              var fileHandle = await handle.getFileHandle(safeName(files[i].fileName, "screen-" + (i + 1) + ".layout.md"), { create: true });
              var writable = await fileHandle.createWritable();
              await writable.write(files[i].content);
              await writable.close();
            }
          }

          function saveZip(sourcesJson) {
            var project = parseProjectJson(sourcesJson);
            downloadBlob(makeZip(project.files), safeName(project.projectName + ".zip", "mission-visualization-project.zip"));
          }

          function collectCanvases(root, out) {
            if (!root || !root.querySelectorAll) return;
            Array.prototype.forEach.call(root.querySelectorAll("canvas"), function (canvas) {
              out.push(canvas);
            });
            Array.prototype.forEach.call(root.querySelectorAll("*"), function (element) {
              if (element.shadowRoot) collectCanvases(element.shadowRoot, out);
            });
          }

          function findMainCanvas() {
            var canvases = [];
            collectCanvases(document, canvases);
            if (!canvases.length) throw new Error("Canvas is not ready yet.");
            canvases.sort(function (a, b) { return (b.width * b.height) - (a.width * a.height); });
            return canvases[0];
          }

          function cropCanvas(crop) {
            var source = findMainCanvas();
            var rect = source.getBoundingClientRect();
            var sxScale = source.width / Math.max(1, rect.width);
            var syScale = source.height / Math.max(1, rect.height);
            var sourceX = 0;
            var sourceY = 0;
            var sourceW = source.width;
            var sourceH = source.height;
            if (crop && crop.width > 0 && crop.height > 0) {
              sourceX = Math.round((crop.x - rect.left) * sxScale);
              sourceY = Math.round((crop.y - rect.top) * syScale);
              sourceW = Math.round(crop.width * sxScale);
              sourceH = Math.round(crop.height * syScale);
              sourceX = Math.max(0, Math.min(source.width - 1, sourceX));
              sourceY = Math.max(0, Math.min(source.height - 1, sourceY));
              sourceW = Math.max(1, Math.min(source.width - sourceX, sourceW));
              sourceH = Math.max(1, Math.min(source.height - sourceY, sourceH));
            }
            var out = document.createElement("canvas");
            out.width = sourceW;
            out.height = sourceH;
            out.getContext("2d").drawImage(source, sourceX, sourceY, sourceW, sourceH, 0, 0, sourceW, sourceH);
            return out;
          }

          function exportPng(fileName, crop) {
            cropCanvas(crop).toBlob(function (blob) {
              if (blob) downloadBlob(blob, safeName(fileName, "export.png"));
            }, "image/png");
          }

          function bytesFromBase64(dataUrl) {
            var base64 = dataUrl.split(",")[1] || "";
            var binary = atob(base64);
            var bytes = new Uint8Array(binary.length);
            for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return bytes;
          }

          function pdfAscii(text) {
            var bytes = new Uint8Array(text.length);
            for (var i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i) & 0xff;
            return bytes;
          }

          function makePdf(pages) {
            var chunks = [];
            var offsets = [0];
            var offset = 0;
            function pushBytes(bytes) { chunks.push(bytes); offset += bytes.length; }
            function pushText(text) { pushBytes(pdfAscii(text)); }
            function beginObj(id) { offsets[id] = offset; pushText(id + " 0 obj\n"); }
            function endObj() { pushText("\nendobj\n"); }

            pushText("%PDF-1.4\n");
            beginObj(1); pushText("<< /Type /Catalog /Pages 2 0 R >>"); endObj();
            var kids = [];
            for (var i = 0; i < pages.length; i++) kids.push((3 + i * 3) + " 0 R");
            beginObj(2); pushText("<< /Type /Pages /Kids [" + kids.join(" ") + "] /Count " + pages.length + " >>"); endObj();
            pages.forEach(function (page, i) {
              var pageId = 3 + i * 3;
              var contentId = pageId + 1;
              var imageId = pageId + 2;
              var w = Math.max(1, Math.round(page.width));
              var h = Math.max(1, Math.round(page.height));
              var img = bytesFromBase64(page.dataUrl);
              var content = "q\n" + w + " 0 0 " + h + " 0 0 cm\n/Im" + i + " Do\nQ\n";
              beginObj(pageId);
              pushText("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "] /Resources << /XObject << /Im" + i + " " + imageId + " 0 R >> >> /Contents " + contentId + " 0 R >>");
              endObj();
              beginObj(contentId);
              pushText("<< /Length " + content.length + " >>\nstream\n" + content + "endstream");
              endObj();
              beginObj(imageId);
              pushText("<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + img.length + " >>\nstream\n");
              pushBytes(img);
              pushText("\nendstream");
              endObj();
            });

            var xref = offset;
            var count = 3 + pages.length * 3;
            pushText("xref\n0 " + count + "\n0000000000 65535 f \n");
            for (var id = 1; id < count; id++) {
              pushText(String(offsets[id]).padStart(10, "0") + " 00000 n \n");
            }
            pushText("trailer\n<< /Size " + count + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
            return new Blob(chunks, { type: "application/pdf" });
          }

          window.__mvProjectIo = {
            openZip: function () { run(openZip()); },
            openFolder: function () { run(openFolder()); },
            saveFolder: function (sourcesJson) { run(saveFolder(sourcesJson)); },
            saveZip: function (sourcesJson) { run(Promise.resolve().then(function () { saveZip(sourcesJson); })); },
            exportPng: function (fileName, crop) { run(Promise.resolve().then(function () { exportPng(fileName, crop); })); },
            beginPdf: function () { pdfPages = []; },
            appendPdfPage: function (title, crop) {
              var canvas = cropCanvas(crop);
              pdfPages.push({ title: String(title || "Screen"), width: canvas.width, height: canvas.height, dataUrl: canvas.toDataURL("image/jpeg", 0.92) });
            },
            finishPdf: function (fileName) {
              if (!pdfPages.length) throw new Error("No screens were captured for PDF export.");
              downloadBlob(makePdf(pdfPages), safeName(fileName, "screens.pdf"));
              pdfPages = [];
            }
          };
        })();
        """
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformOpenProjectZipArchive() {
    ensureProjectIoInstalled()
    callOpenZip()
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformOpenProjectFolder() {
    ensureProjectIoInstalled()
    callOpenFolder()
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformSaveProjectFolder(sourcesJson: String) {
    ensureProjectIoInstalled()
    callSaveFolder(sourcesJson)
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformDownloadProjectZip(sourcesJson: String) {
    ensureProjectIoInstalled()
    callSaveZip(sourcesJson)
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformExportCanvasPng(fileName: String, crop: CanvasExportCrop?) {
    ensureProjectIoInstalled()
    val hasCrop = crop != null
    val x = crop?.x ?: 0.0
    val y = crop?.y ?: 0.0
    val width = crop?.width ?: 0.0
    val height = crop?.height ?: 0.0
    callExportPng(fileName, hasCrop, x, y, width, height)
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformBeginPdfExport() {
    ensureProjectIoInstalled()
    callBeginPdf()
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformAppendCanvasPdfPage(title: String, crop: CanvasExportCrop?) {
    ensureProjectIoInstalled()
    val hasCrop = crop != null
    val x = crop?.x ?: 0.0
    val y = crop?.y ?: 0.0
    val width = crop?.width ?: 0.0
    val height = crop?.height ?: 0.0
    callAppendPdfPage(title, hasCrop, x, y, width, height)
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformFinishPdfExport(fileName: String) {
    ensureProjectIoInstalled()
    callFinishPdf(fileName)
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformToggleFullscreen() {
    callToggleFullscreen()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun callOpenZip(): Unit = js("window.__mvProjectIo.openZip()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callOpenFolder(): Unit = js("window.__mvProjectIo.openFolder()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callSaveFolder(sourcesJson: String): Unit = js("window.__mvProjectIo.saveFolder(sourcesJson)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callSaveZip(sourcesJson: String): Unit = js("window.__mvProjectIo.saveZip(sourcesJson)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callExportPng(fileName: String, hasCrop: Boolean, x: Double, y: Double, width: Double, height: Double): Unit =
    js("window.__mvProjectIo.exportPng(fileName, hasCrop ? { x: x, y: y, width: width, height: height } : null)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callBeginPdf(): Unit = js("window.__mvProjectIo.beginPdf()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callAppendPdfPage(title: String, hasCrop: Boolean, x: Double, y: Double, width: Double, height: Double): Unit =
    js("window.__mvProjectIo.appendPdfPage(title, hasCrop ? { x: x, y: y, width: width, height: height } : null)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callFinishPdf(fileName: String): Unit = js("window.__mvProjectIo.finishPdf(fileName)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callToggleFullscreen(): Unit =
    js("(function(){if(document.fullscreenElement){if(document.exitFullscreen)document.exitFullscreen();}else{var el=document.documentElement;if(el.requestFullscreen)el.requestFullscreen().catch(function(){});}})()")
