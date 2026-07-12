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

          // Directory-preserving sanitizer for resource paths ("res/photo.png" keeps its two
          // segments; safeName() would collapse it to the basename). Strips path separators (handled
          // by split), leading dots and reserved chars, but never touches letters, digits, '.', '-',
          // '_' or spaces (including consecutive runs) that sanitizeResourceFileName() may emit, so the
          // key stays byte-identical and a saved resource re-links on reopen. Guards path traversal.
          function safeResSegment(segment) {
            var forbidden = "<>:|?*" + String.fromCharCode(34) + String.fromCharCode(92);
            var out = "";
            for (var i = 0; i < segment.length; i++) {
              var ch = segment.charAt(i);
              if (segment.charCodeAt(i) < 32 || forbidden.indexOf(ch) >= 0) continue;
              out += ch;
            }
            while (out.charAt(0) === ".") out = out.slice(1);
            return out.trim();
          }
          function safeResPath(path) {
            return String(path || "").split("/").map(safeResSegment).filter(function (segment) {
              return segment && segment !== "." && segment !== "..";
            }).join("/");
          }

          function rawBytesFromBase64(base64) {
            var binary = atob(base64 || "");
            var bytes = new Uint8Array(binary.length);
            for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return bytes;
          }

          function rawBase64FromBytes(bytes) {
            var CHUNK = 0x8000;
            var binary = "";
            for (var i = 0; i < bytes.length; i += CHUNK) {
              binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
            }
            return btoa(binary);
          }

          // res/ refs mentioned anywhere in the SLM sources — a project's resources are exactly the
          // ones its documents reference, so orphaned bytes in IndexedDB are not written to disk.
          function referencedResPaths(sourcesJson) {
            var set = new Set();
            parseSourcesJson(sourcesJson).forEach(function (file) {
              var matches = String(file.content || "").match(/res\/[^\s"'«»)\]}]+/g);
              if (matches) matches.forEach(function (ref) { set.add(ref); });
            });
            return set;
          }

          async function collectResources(sourcesJson) {
            if (!window.__mvResStore) return [];
            var joined = await new Promise(function (resolve) { window.__mvResStore.list(resolve); });
            var paths = (joined ? joined.split("\n") : []).filter(function (p) { return p && p.indexOf("res/") === 0; });
            var referenced = referencedResPaths(sourcesJson);
            paths = paths.filter(function (p) { return referenced.has(p); });
            var out = [];
            for (var i = 0; i < paths.length; i++) {
              var base64 = await new Promise(function (resolve) { window.__mvResStore.read(paths[i], resolve); });
              if (base64) out.push({ path: paths[i], base64: base64 });
            }
            return out;
          }

          async function storeResources(resources) {
            if (!window.__mvResStore) return;
            var paths = resources.map(function (r) { return r.path; }).join("\n");
            var payloads = resources.map(function (r) { return r.base64; }).join("\n");
            await new Promise(function (resolve) { window.__mvResStore.replaceAll(paths, payloads, resolve); });
          }

          async function writeResourceToDir(rootHandle, path, bytes) {
            var parts = safeResPath(path).split("/");
            if (!parts.length) return;
            var dir = rootHandle;
            for (var i = 0; i < parts.length - 1; i++) {
              dir = await dir.getDirectoryHandle(parts[i], { create: true });
            }
            var fileHandle = await dir.getFileHandle(parts[parts.length - 1], { create: true });
            var writable = await fileHandle.createWritable();
            await writable.write(bytes);
            await writable.close();
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

          // Commits an opened project. The resource store is replaced ONLY after the source-files
          // check passes, so opening a folder/zip with no .layout.md (an aborted open) never wipes the
          // currently-loaded project's resources. storeResources is awaited before reload so the
          // IndexedDB write is durable across the navigation.
          async function persistSources(files, resources, projectName) {
            if (!files || files.length === 0) {
              window.alert("В выбранном проекте не найдены .layout.md файлы.");
              return;
            }
            files.sort(function (a, b) { return a.fileName.localeCompare(b.fileName); });
            await storeResources(resources || []);
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
              input.accept = ".layout.md,.slm.md,.slm,.md,.png,.jpg,.jpeg,.gif,.webp,.svg,text/markdown,text/plain,image/*";
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

          async function unzipProject(arrayBuffer) {
            var bytes = new Uint8Array(arrayBuffer);
            var view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
            var eocd = findEndOfCentralDirectory(bytes);
            var count = view.getUint16(eocd + 10, true);
            var centralOffset = view.getUint32(eocd + 16, true);
            var ptr = centralOffset;
            var files = [];
            var resources = [];
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
              if (name.endsWith("/")) continue;
              var isResource = name.indexOf("res/") === 0;
              if (!isResource && !sourceLike(name)) continue;
              if (view.getUint32(localOffset, true) !== 0x04034b50) throw new Error("ZIP local header is corrupt.");
              var localNameLength = view.getUint16(localOffset + 26, true);
              var localExtraLength = view.getUint16(localOffset + 28, true);
              var dataStart = localOffset + 30 + localNameLength + localExtraLength;
              var compressed = bytes.slice(dataStart, dataStart + compressedSize);
              var raw;
              if (method === 0) raw = compressed;
              else if (method === 8) raw = await inflateRaw(compressed);
              else throw new Error("Unsupported ZIP compression method: " + method + ".");
              if (isResource) resources.push({ path: safeResPath(name), base64: rawBase64FromBytes(raw) });
              else files.push({ fileName: safeName(name, "screen-" + (files.length + 1) + ".layout.md"), content: decoder.decode(raw) });
            }
            return { files: files, resources: resources };
          }

          async function openZip() {
            var file = await chooseFile(".zip,application/zip,application/x-zip-compressed");
            if (!file) return;
            var project = await unzipProject(await file.arrayBuffer());
            await persistSources(project.files, project.resources, projectNameFromArchive(file.name));
          }

          async function walkDirectory(handle, prefix, out, resOut) {
            for await (var pair of handle.entries()) {
              var name = pair[0];
              var child = pair[1];
              if (child.kind === "file") {
                var full = prefix + name;
                if (full.indexOf("res/") === 0) {
                  var resFile = await child.getFile();
                  resOut.push({ path: safeResPath(full), base64: rawBase64FromBytes(new Uint8Array(await resFile.arrayBuffer())) });
                } else if (sourceLike(name)) {
                  var file = await child.getFile();
                  out.push({ fileName: safeName(full, name), content: await file.text() });
                }
              } else if (child.kind === "directory") {
                await walkDirectory(child, prefix + name + "/", out, resOut);
              }
            }
          }

          function relativeUnderRoot(path) {
            var parts = String(path || "").split("/");
            return parts.length > 1 ? parts.slice(1).join("/") : path;
          }

          async function readPickedSources(files) {
            var out = [];
            for (var i = 0; i < files.length; i++) {
              var file = files[i];
              var name = file.webkitRelativePath || file.name;
              if (relativeUnderRoot(name).indexOf("res/") === 0) continue;
              if (sourceLike(name)) {
                out.push({ fileName: safeName(name, file.name), content: await file.text() });
              }
            }
            return out;
          }

          async function readPickedResources(files) {
            var out = [];
            for (var i = 0; i < files.length; i++) {
              var file = files[i];
              var sub = relativeUnderRoot(file.webkitRelativePath || file.name);
              if (sub.indexOf("res/") === 0) {
                out.push({ path: safeResPath(sub), base64: rawBase64FromBytes(new Uint8Array(await file.arrayBuffer())) });
              }
            }
            return out;
          }

          async function openFolderViaInput() {
            var files = await chooseDirectoryFiles();
            if (!files.length) return;
            await persistSources(await readPickedSources(files), await readPickedResources(files), projectNameFromPickedFiles(files));
          }

          async function openFolder() {
            if (!window.showDirectoryPicker) {
              await openFolderViaInput();
              return;
            }
            var handle = await window.showDirectoryPicker({ mode: "read" });
            var files = [];
            var resources = [];
            await walkDirectory(handle, "", files, resources);
            await persistSources(files, resources, handle.name);
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

          function makeZipEntries(entries) {
            var chunks = [];
            var central = [];
            var offset = 0;
            var stamp = dosTimeDate(new Date());
            function push(chunk) { chunks.push(chunk); offset += chunk.length; }
            entries.forEach(function (entry) {
              var nameBytes = encoder.encode(entry.name);
              var data = entry.bytes;
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
            ev.setUint16(8, entries.length, true);
            ev.setUint16(10, entries.length, true);
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

          async function saveFolder(sourcesJson, onSaved) {
            if (!window.showDirectoryPicker) {
              await saveZip(sourcesJson);
              if (onSaved) onSaved();
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
            var resources = await collectResources(sourcesJson);
            for (var j = 0; j < resources.length; j++) {
              await writeResourceToDir(handle, resources[j].path, rawBytesFromBase64(resources[j].base64));
            }
            if (onSaved) onSaved();
          }

          async function saveZip(sourcesJson) {
            var project = parseProjectJson(sourcesJson);
            var entries = project.files.map(function (file) {
              return { name: safeName(file.fileName, "document.layout.md"), bytes: encoder.encode(file.content) };
            });
            var resources = await collectResources(sourcesJson);
            resources.forEach(function (resource) {
              entries.push({ name: safeResPath(resource.path), bytes: rawBytesFromBase64(resource.base64) });
            });
            downloadBlob(makeZipEntries(entries), safeName(project.projectName + ".zip", "mission-visualization-project.zip"));
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
            return rawBytesFromBase64(dataUrl.split(",")[1] || "");
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
            saveFolder: function (sourcesJson, onSaved) { run(saveFolder(sourcesJson, onSaved)); },
            saveZip: function (sourcesJson, onSaved) { run(saveZip(sourcesJson).then(function () { if (onSaved) onSaved(); })); },
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

internal actual val platformSupportsProjectDiskIo: Boolean = true

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformOpenProjectZipArchive() {
    ensureProjectIoInstalled()
    ensureResStoreInstalled()
    callOpenZip()
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformOpenProjectFolder() {
    ensureProjectIoInstalled()
    ensureResStoreInstalled()
    callOpenFolder()
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformSaveProjectFolder(sourcesJson: String, onSaved: () -> Unit) {
    ensureProjectIoInstalled()
    ensureResStoreInstalled()
    callSaveFolder(sourcesJson, onSaved)
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformDownloadProjectZip(sourcesJson: String, onSaved: () -> Unit) {
    ensureProjectIoInstalled()
    ensureResStoreInstalled()
    callSaveZip(sourcesJson, onSaved)
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
private fun callSaveFolder(sourcesJson: String, onSaved: () -> Unit): Unit = js("window.__mvProjectIo.saveFolder(sourcesJson, onSaved)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun callSaveZip(sourcesJson: String, onSaved: () -> Unit): Unit = js("window.__mvProjectIo.saveZip(sourcesJson, onSaved)")

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

private fun openUrlInBrowser(url: String): Unit = js("{ window.open(url, '_blank'); }")

internal actual fun platformOpenUrl(url: String) = openUrlInBrowser(url)

// --- Live local-folder sync (window.__mvFolderSync) -------------------------------------------
// A sibling of window.__mvProjectIo. Owns the File System Access API, IndexedDB (the picked
// directory handle survives reloads there — it cannot go in localStorage), a polling watcher that
// re-reads externally-changed *.layout.md, and echo-suppressed write-back. Kotlin drives it purely
// through the synchronous getters below plus a coroutine that polls `revision`.

@OptIn(ExperimentalWasmJsInterop::class)
private fun ensureFolderSyncInstalled() {
    js(
        """
        (function () {
          if (window.__mvFolderSync) return;

          var ECHO_WINDOW_MS = 4000;
          var POLL_MS = 500;

          var state = { handle: null, status: "idle", folderName: null, revision: 0, snapshotJson: null, activeId: null, lastError: null };
          var meta = {};             // path -> { mtime, size }
          var publishedByName = {};  // path -> content: our notion of what is on disk (the base)
          var writtenEcho = {};      // path -> { hash, expiry }: suppress our own writes
          var pendingSig = null;     // settle: last-seen signature awaiting one stable tick
          var scanning = false;
          var timer = null;

          function sourceLike(name) {
            var lower = String(name || "").toLowerCase();
            return lower.endsWith(".layout.md") || lower.endsWith(".slm.md") || lower.endsWith(".slm") || lower.endsWith(".md");
          }

          function safeSeg(seg) {
            // Names come from files we READ, so keep them verbatim to round-trip on write;
            // only guard empty segments and path traversal.
            var cleaned = String(seg || "").trim();
            if (cleaned === "" || cleaned === "." || cleaned === "..") return "screen.layout.md";
            return cleaned;
          }

          function hashStr(s) {
            var h = 0x811c9dc5;
            for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = (h * 0x01000193) >>> 0; }
            return h >>> 0;
          }

          function idbOpen() {
            return new Promise(function (resolve, reject) {
              var req = window.indexedDB.open("mv-folder-sync", 1);
              req.onupgradeneeded = function () { req.result.createObjectStore("kv"); };
              req.onsuccess = function () { resolve(req.result); };
              req.onerror = function () { reject(req.error); };
            });
          }
          function idbReq(mode, run) {
            return idbOpen().then(function (db) {
              return new Promise(function (resolve, reject) {
                var tx = db.transaction("kv", mode);
                var store = tx.objectStore("kv");
                var out = run(store);
                tx.oncomplete = function () { resolve(out && out.result !== undefined ? out.result : null); };
                tx.onerror = function () { reject(tx.error); };
              });
            });
          }
          function idbGet(key) { return idbReq("readonly", function (s) { return s.get(key); }); }
          function idbPut(key, val) { return idbReq("readwrite", function (s) { s.put(val, key); return null; }); }
          function idbDelete(key) { return idbReq("readwrite", function (s) { s.delete(key); return null; }); }
          function idbKeys() { return idbReq("readonly", function (s) { return s.getAllKeys(); }); }

          async function walk(dir, prefix, out) {
            for await (var pair of dir.entries()) {
              var name = pair[0];
              var child = pair[1];
              if (child.kind === "file" && sourceLike(name)) {
                out.push({ path: prefix + name, file: await child.getFile() });
              } else if (child.kind === "directory") {
                await walk(child, prefix + name + "/", out);
              }
            }
          }

          async function listSources() {
            var out = [];
            await walk(state.handle, "", out);
            out.sort(function (a, b) { return a.path.localeCompare(b.path); });
            return out;
          }

          function signatureOf(list) {
            return list.map(function (it) { return it.path + ":" + it.file.lastModified + ":" + it.file.size; }).join("|");
          }

          function buildSnapshot() {
            var files = Object.keys(publishedByName).sort().map(function (name) {
              return { fileName: name, content: publishedByName[name] };
            });
            state.snapshotJson = JSON.stringify({ projectName: state.folderName || "", files: files });
          }

          async function initialScan() {
            var list = await listSources();
            meta = {}; publishedByName = {};
            for (var i = 0; i < list.length; i++) {
              var it = list[i];
              meta[it.path] = { mtime: it.file.lastModified, size: it.file.size };
              publishedByName[it.path] = await it.file.text();
            }
            pendingSig = signatureOf(list);
            buildSnapshot();
            state.revision += 1;
          }

          async function scan() {
            if (scanning || state.status !== "watching" || !state.handle) return;
            scanning = true;
            try {
              var list = await listSources();
              var sig = signatureOf(list);
              var current = {};
              var changed = false;
              for (var i = 0; i < list.length; i++) {
                var it = list[i];
                current[it.path] = true;
                var m = meta[it.path];
                if (!m || m.mtime !== it.file.lastModified || m.size !== it.file.size) changed = true;
              }
              for (var gone in meta) { if (!current[gone]) changed = true; }
              if (!changed) { pendingSig = sig; return; }
              // Settle: only read content once the on-disk signature is stable for one extra tick,
              // so a half-written overwrite is never parsed.
              if (sig !== pendingSig) { pendingSig = sig; return; }

              var now = Date.now();
              var nextMeta = {};
              var nextPublished = {};
              var external = false;
              for (var j = 0; j < list.length; j++) {
                var e = list[j];
                nextMeta[e.path] = { mtime: e.file.lastModified, size: e.file.size };
                var content = await e.file.text();
                nextPublished[e.path] = content;
                var prev = publishedByName[e.path];
                if (prev === content) continue;
                var echo = writtenEcho[e.path];
                if (echo && echo.expiry > now && echo.hash === hashStr(content)) continue; // our own write
                external = true;
              }
              for (var removed in publishedByName) { if (!current[removed]) external = true; }
              meta = nextMeta;
              publishedByName = nextPublished;
              if (external) { buildSnapshot(); state.revision += 1; }
            } catch (err) {
              console.error(err);
            } finally {
              scanning = false;
            }
          }

          function startWatcher() { if (!timer) timer = window.setInterval(function () { scan(); }, POLL_MS); }
          function stopWatcher() { if (timer) { window.clearInterval(timer); timer = null; } }

          async function resolveFileHandle(path, create) {
            var segs = path.split("/").filter(function (s) { return s.length > 0; });
            var dir = state.handle;
            for (var i = 0; i < segs.length - 1; i++) {
              dir = await dir.getDirectoryHandle(safeSeg(segs[i]), { create: create });
            }
            return await dir.getFileHandle(safeSeg(segs[segs.length - 1]), { create: create });
          }

          function mintId() {
            if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
            return "f-" + Date.now().toString(36) + "-" + Math.floor(Math.random() * 1e9).toString(36);
          }

          // Reuse the same id when the user re-picks a folder we already know (so recents upsert
          // rather than duplicate). File System Access exposes isSameEntry for exactly this.
          async function resolveIdFor(handle) {
            try {
              var keys = (await idbKeys()) || [];
              for (var i = 0; i < keys.length; i++) {
                var k = keys[i];
                if (typeof k === "string" && k.indexOf("handles/") === 0) {
                  var saved = await idbGet(k);
                  if (saved && typeof saved.isSameEntry === "function") {
                    try { if (await saved.isSameEntry(handle)) return k.slice("handles/".length); } catch (e) {}
                  }
                }
              }
            } catch (e) { console.error(e); }
            return mintId();
          }

          async function adoptHandle(handle, id) {
            state.handle = handle;
            state.folderName = handle.name;
            state.activeId = id;
            state.lastError = null;
            try { await idbPut("handles/" + id, handle); } catch (e) { console.error(e); }
            try { await idbPut("root", handle); } catch (e) { console.error(e); }  // legacy "last active" pointer
            await initialScan();
            state.status = "watching";
            startWatcher();
          }

          async function connect() {
            state.status = "connecting";
            try {
              var handle = await window.showDirectoryPicker({ id: "mv-folder-sync", mode: "readwrite" });
              await adoptHandle(handle, await resolveIdFor(handle));
            } catch (err) {
              console.error(err);
              state.status = "idle"; // user dismissed the picker or permission failed
            }
          }

          // Reconnect a specific saved folder by id. MUST be invoked from a user gesture: when the
          // browser reset the grant to "prompt", requestPermission needs transient activation.
          async function connectById(id) {
            try {
              state.status = "connecting";
              var h = await idbGet("handles/" + id);
              if (!h) { state.status = "idle"; state.lastError = "missing"; return; }
              state.handle = h; state.folderName = h.name; state.activeId = id;
              var perm = await h.queryPermission({ mode: "readwrite" });
              if (perm !== "granted") perm = await h.requestPermission({ mode: "readwrite" });
              if (perm !== "granted") { state.status = "reconnect-needed"; state.lastError = "denied"; return; }
              try { await idbPut("root", h); } catch (e) {}
              await initialScan();
              state.status = "watching";
              startWatcher();
            } catch (err) { console.error(err); state.status = "reconnect-needed"; state.lastError = "error"; }
          }

          async function forget(id) {
            try { await idbDelete("handles/" + id); } catch (e) { console.error(e); }
          }

          async function grantAndWatch() {
            var perm = await state.handle.queryPermission({ mode: "readwrite" });
            if (perm !== "granted") perm = await state.handle.requestPermission({ mode: "readwrite" });
            if (perm !== "granted") { state.status = "reconnect-needed"; return; }
            await initialScan();
            state.status = "watching";
            startWatcher();
          }

          async function reconnect() {
            try {
              if (!state.handle) {
                var h = await idbGet("root");
                if (!h) { state.status = "idle"; return; }
                state.handle = h; state.folderName = h.name;
              }
              state.status = "connecting";
              // Recover the folder's stable id so this counts as an explicit user connect (an
              // id-less folder is treated as a gesture-free probe resume and dropped by the editor).
              state.activeId = await resolveIdFor(state.handle);
              await grantAndWatch();
            } catch (err) { console.error(err); state.status = "reconnect-needed"; }
          }

          async function probe() {
            try {
              var h = await idbGet("root");
              if (!h) { state.status = "idle"; return; }
              state.handle = h; state.folderName = h.name;
              var perm = await h.queryPermission({ mode: "readwrite" });
              if (perm === "granted") { await initialScan(); state.status = "watching"; startWatcher(); }
              else { state.status = "reconnect-needed"; }
            } catch (err) { console.error(err); state.status = "reconnect-needed"; }
          }

          function disconnect() {
            stopWatcher();
            state.handle = null;
            state.status = "idle";
            state.snapshotJson = null;
            state.activeId = null;
            state.lastError = null;
            meta = {}; publishedByName = {}; writtenEcho = {};
            // Only forget the "last active" pointer — the per-id handle stays so its recent card
            // can reconnect later.
            idbDelete("root").catch(function () {});
          }

          async function writeFile(name, content) {
            if (!state.handle || state.status !== "watching") return;
            try {
              var existing = null;
              try { existing = await (await (await resolveFileHandle(name, false)).getFile()).text(); } catch (e) { existing = null; }
              var base = publishedByName[name];
              // Pre-write concurrency guard: if the file drifted from our base, an external edit
              // landed since we last synced — do not clobber it. The watcher surfaces it inbound.
              if (existing !== null && base !== undefined && existing !== base) return;
              if (existing === content) { publishedByName[name] = content; return; }
              var target = await resolveFileHandle(name, true);
              var writable = await target.createWritable();
              await writable.write(content);
              await writable.close();
              var f = await target.getFile();
              meta[name] = { mtime: f.lastModified, size: f.size };
              publishedByName[name] = content;
              writtenEcho[name] = { hash: hashStr(content), expiry: Date.now() + ECHO_WINDOW_MS };
            } catch (err) { console.error(err); }
          }

          window.__mvFolderSync = {
            get revision() { return state.revision; },
            get status() { return state.status; },
            get folderName() { return state.folderName; },
            get snapshotJson() { return state.snapshotJson; },
            get activeId() { return state.activeId; },
            get lastError() { return state.lastError; },
            connect: function () { connect(); },
            connectById: function (id) { connectById(id); },
            reconnect: function () { reconnect(); },
            disconnect: function () { disconnect(); },
            forget: function (id) { forget(id); },
            writeFile: function (name, content) { writeFile(name, content); }
          };
          probe();
        })();
        """
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun detectFolderSyncSupport(): Boolean =
    js("(typeof window !== 'undefined' && typeof window.showDirectoryPicker === 'function' && typeof window.indexedDB !== 'undefined')")

internal actual val platformSupportsFolderSync: Boolean = detectFolderSyncSupport()

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformInitFolderSync() {
    if (platformSupportsFolderSync) ensureFolderSyncInstalled()
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformConnectFolderLive() {
    ensureFolderSyncInstalled()
    folderSyncConnect()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun folderSyncConnect(): Unit = js("window.__mvFolderSync.connect()")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformReconnectSavedFolder() {
    ensureFolderSyncInstalled()
    folderSyncReconnect()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun folderSyncReconnect(): Unit = js("window.__mvFolderSync.reconnect()")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformDisconnectFolder() {
    js("if (window.__mvFolderSync) window.__mvFolderSync.disconnect()")
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformSavedFolderName(): String? =
    js("(window.__mvFolderSync ? window.__mvFolderSync.folderName : null)")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun folderSyncRevision(): Int =
    js("(window.__mvFolderSync ? window.__mvFolderSync.revision : 0)")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun folderSyncSnapshotJson(): String? =
    js("(window.__mvFolderSync ? window.__mvFolderSync.snapshotJson : null)")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun folderSyncStatus(): String? =
    js("(window.__mvFolderSync ? window.__mvFolderSync.status : null)")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformWriteFolderFile(fileName: String, content: String) {
    js("if (window.__mvFolderSync) window.__mvFolderSync.writeFile(fileName, content)")
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsEpochMillis(): Double = js("Date.now()")

internal actual fun platformEpochMillis(): Long = jsEpochMillis().toLong()

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformActiveFolderId(): String? =
    js("(window.__mvFolderSync ? (window.__mvFolderSync.activeId || null) : null)")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformForgetFolder(id: String) {
    js("if (window.__mvFolderSync) window.__mvFolderSync.forget(id)")
}

// --- Startup landing (window.__mvLanding) -----------------------------------------------------
// A DOM overlay shown over #compose-root on boot (below the #mv-loader intro, which fades to reveal
// it). Renders the recent-project list + the always-present Welcome card from a JSON config Kotlin
// builds (localized strings + theme colors + recents). Folder cards reconnect via
// window.__mvFolderSync.connectById inside the click gesture (so a permission re-grant is allowed).
// The user's choice is read back through a small polled action queue (pendingAction).

@OptIn(ExperimentalWasmJsInterop::class)
private fun ensureLandingInstalled() {
    js(
        """
        (function () {
          if (window.__mvLanding) return;

          var ROOT_ID = "mv-landing";
          var STYLE_ID = "mv-landing-style";
          var Z = 2147482000;
          var actions = [];
          var cfg = null;
          var STR = {};            // strings for the currently-selected language
          var cards = [];
          var selected = 0;
          var keyHandler = null;
          var folderTimer = 0;
          var langMenuEl = null;

          function queue(a) { actions.push(a); }

          function currentStrings() {
            return (cfg && cfg.stringsByLang && cfg.stringsByLang[cfg.currentLanguage]) || {};
          }
          function langNativeName(code) {
            var list = (cfg && cfg.languages) || [];
            for (var i = 0; i < list.length; i++) { if (list[i].code === code) return list[i].nativeName; }
            return code;
          }
          function closeLangMenu() { if (langMenuEl) langMenuEl.classList.remove("mvl-open"); }

          function cssText() {
            return "" +
              "#mv-landing{position:fixed;inset:0;z-index:" + Z + ";display:flex;align-items:center;justify-content:center;padding:24px;overflow:auto;" +
              "background:var(--mvl-backdrop);font-family:system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;opacity:0;transition:opacity .28s ease;}" +
              "#mv-landing.mvl-in{opacity:1;}" +
              "#mv-landing.mvl-out{opacity:0;pointer-events:none;}" +
              "#mv-landing *{box-sizing:border-box;}" +
              ".mvl-panel{width:100%;max-width:760px;margin:auto;}" +
              ".mvl-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;}" +
              ".mvl-app{font-size:13px;font-weight:600;letter-spacing:.3px;color:var(--mvl-accent);}" +
              ".mvl-title{font-size:24px;font-weight:700;color:var(--mvl-ink);margin:2px 0 4px;}" +
              ".mvl-sub{font-size:14px;color:var(--mvl-muted);margin:0 0 8px;}" +
              ".mvl-close{border:0;background:transparent;color:var(--mvl-muted);font-size:22px;line-height:1;cursor:pointer;padding:6px 10px;border-radius:8px;}" +
              ".mvl-close:hover{background:var(--mvl-card-hover);color:var(--mvl-ink);}" +
              ".mvl-section{font-size:12px;font-weight:600;letter-spacing:.5px;text-transform:uppercase;color:var(--mvl-muted);margin:18px 0 10px;}" +
              ".mvl-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px;}" +
              ".mvl-card{position:relative;text-align:left;border:1px solid var(--mvl-stroke);background:var(--mvl-card);border-radius:14px;padding:16px;cursor:pointer;display:flex;gap:12px;align-items:flex-start;transition:border-color .15s ease,background .15s ease,transform .05s ease;font:inherit;}" +
              ".mvl-card:hover{background:var(--mvl-card-hover);border-color:var(--mvl-accent);}" +
              ".mvl-card:active{transform:translateY(1px);}" +
              ".mvl-card.mvl-selected{border-color:var(--mvl-accent);box-shadow:0 0 0 2px var(--mvl-accent) inset;}" +
              ".mvl-ic{flex:0 0 auto;width:34px;height:34px;border-radius:9px;display:flex;align-items:center;justify-content:center;background:var(--mvl-accent-soft);color:var(--mvl-on-accent-soft);font-size:17px;}" +
              ".mvl-body{min-width:0;flex:1 1 auto;}" +
              ".mvl-name{font-size:15px;font-weight:600;color:var(--mvl-ink);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}" +
              ".mvl-meta{font-size:12px;color:var(--mvl-muted);margin-top:3px;line-height:1.35;overflow-wrap:anywhere;}" +
              ".mvl-x{position:absolute;top:8px;right:8px;border:0;background:transparent;color:var(--mvl-muted);font-size:14px;line-height:1;cursor:pointer;padding:4px 6px;border-radius:7px;opacity:0;transition:opacity .12s ease;}" +
              ".mvl-card:hover .mvl-x{opacity:1;}" +
              ".mvl-x:hover{background:var(--mvl-card-hover);color:var(--mvl-danger);}" +
              ".mvl-add{align-items:center;color:var(--mvl-accent);border-style:dashed;}" +
              ".mvl-add .mvl-name{color:var(--mvl-accent);}" +
              ".mvl-note{font-size:12px;color:var(--mvl-muted);margin-top:16px;}" +
              ".mvl-flag-copy{display:inline-flex;align-items:center;margin-left:6px;border:1px solid var(--mvl-stroke);background:var(--mvl-card);color:var(--mvl-accent);border-radius:7px;padding:3px 8px;font:inherit;font-size:11px;font-weight:600;line-height:1.4;cursor:pointer;white-space:nowrap;}" +
              ".mvl-flag-copy:hover{background:var(--mvl-card-hover);border-color:var(--mvl-accent);}" +
              ".mvl-actions{display:flex;align-items:flex-start;gap:8px;flex:0 0 auto;}" +
              ".mvl-lang-wrap{position:relative;}" +
              ".mvl-lang{display:inline-flex;align-items:center;gap:6px;border:1px solid var(--mvl-stroke);background:var(--mvl-card);color:var(--mvl-ink);border-radius:9px;padding:7px 10px;font:inherit;font-size:13px;font-weight:600;line-height:1;cursor:pointer;}" +
              ".mvl-lang:hover{background:var(--mvl-card-hover);border-color:var(--mvl-accent);}" +
              ".mvl-lang-ic{font-size:14px;}" +
              ".mvl-lang-chev{color:var(--mvl-muted);font-size:10px;}" +
              ".mvl-lang-menu{position:absolute;top:calc(100% + 6px);right:0;min-width:170px;background:var(--mvl-card);border:1px solid var(--mvl-stroke);border-radius:12px;box-shadow:0 12px 34px rgba(15,23,42,.16);padding:6px;z-index:5;display:none;}" +
              ".mvl-lang-menu.mvl-open{display:block;}" +
              ".mvl-lang-item{display:flex;align-items:center;gap:8px;width:100%;text-align:left;border:0;background:transparent;color:var(--mvl-ink);border-radius:8px;padding:9px 10px;font:inherit;font-size:14px;cursor:pointer;}" +
              ".mvl-lang-item:hover{background:var(--mvl-card-hover);}" +
              ".mvl-lang-check{width:16px;flex:0 0 auto;display:inline-flex;justify-content:center;color:var(--mvl-accent);}" +
              "@media (max-width:520px){.mvl-title{font-size:20px;}.mvl-grid{grid-template-columns:1fr;}}";
          }

          function ensureStyle() {
            if (document.getElementById(STYLE_ID)) return;
            var s = document.createElement("style");
            s.id = STYLE_ID;
            s.textContent = cssText();
            document.head.appendChild(s);
          }

          function el(tag, cls) { var e = document.createElement(tag); if (cls) e.className = cls; return e; }

          function makeCard(glyph, name, meta, onOpen, onRemove) {
            // A div (not a button) so the optional remove <button> can nest without invalid markup.
            var card = el("div", "mvl-card");
            card.setAttribute("role", "button");
            card.tabIndex = -1;   // keyboard is driven by the overlay's own arrow/Enter handling
            card.setAttribute("aria-label", meta ? (name + " — " + meta) : name);
            var ic = el("div", "mvl-ic"); ic.textContent = glyph; ic.setAttribute("aria-hidden", "true"); card.appendChild(ic);
            var body = el("div", "mvl-body");
            var nm = el("div", "mvl-name"); nm.textContent = name; nm.title = name; body.appendChild(nm);
            if (meta) { var mt = el("div", "mvl-meta"); mt.textContent = meta; body.appendChild(mt); }
            card.appendChild(body);
            card.__open = onOpen;
            card.addEventListener("click", function (e) { e.preventDefault(); onOpen(); });
            if (onRemove) {
              var x = el("button", "mvl-x"); x.type = "button"; x.textContent = "✕";
              x.title = STR.remove; x.setAttribute("aria-label", STR.remove);
              x.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); onRemove(card); });
              card.appendChild(x);
            }
            return card;
          }

          function applyColors(root) {
            var c = (cfg && cfg.colors) || {};
            function set(k, v) { if (v) root.style.setProperty(k, v); }
            set("--mvl-backdrop", c.backdrop);
            set("--mvl-card", c.card);
            set("--mvl-card-hover", c.cardHover);
            set("--mvl-stroke", c.stroke);
            set("--mvl-ink", c.ink);
            set("--mvl-muted", c.muted);
            set("--mvl-accent", c.accent);
            set("--mvl-accent-soft", c.accentSoft);
            set("--mvl-on-accent-soft", c.onAccentSoft);
            set("--mvl-danger", c.danger);
          }

          function applySelected() {
            for (var i = 0; i < cards.length; i++) {
              if (i === selected) cards[i].classList.add("mvl-selected");
              else cards[i].classList.remove("mvl-selected");
            }
          }
          function move(d) {
            if (!cards.length) return;
            selected = (selected + d + cards.length) % cards.length;
            applySelected();
            if (cards[selected].scrollIntoView) cards[selected].scrollIntoView({ block: "nearest" });
          }

          function stopFolderTimer() { if (folderTimer) { clearInterval(folderTimer); folderTimer = 0; } }

          function hide() {
            stopFolderTimer();
            var root = document.getElementById(ROOT_ID);
            if (root) {
              root.classList.remove("mvl-in");
              root.classList.add("mvl-out");
              setTimeout(function () { if (root.parentNode) root.parentNode.removeChild(root); }, 340);
            }
            if (keyHandler) { window.removeEventListener("keydown", keyHandler, true); keyHandler = null; }
          }

          function openWelcome() { queue({ type: "openWelcome" }); hide(); }
          function openRecovery() { queue({ type: "openRecovery" }); hide(); }
          function dismiss() { queue({ type: "dismiss" }); hide(); }

          // Folder open (existing by id, or a fresh pick). The connect call runs inside this click
          // gesture so a permission re-grant is permitted. Hides the overlay once the folder starts
          // watching; Kotlin's folder-sync loop then adopts the snapshot and records the recent.
          function startConnect(initiator) {
            if (!window.__mvFolderSync) return;
            var fs = window.__mvFolderSync;
            try { initiator(); } catch (e) { console.error(e); }
            stopFolderTimer();
            var waited = 0, sawConnecting = false;
            folderTimer = setInterval(function () {
              waited += 200;
              var st = fs.status;
              if (st === "connecting") sawConnecting = true;
              if (st === "watching") { stopFolderTimer(); hide(); }
              // Denied / missing handle after a connecting phase: stop polling and leave the overlay
              // open so the user can retry or pick another project (rather than spin for 120s).
              else if (sawConnecting && (st === "idle" || st === "reconnect-needed")) { stopFolderTimer(); }
              else if (waited > 120000) { stopFolderTimer(); }
            }, 200);
          }
          function openFolder(id) { startConnect(function () { window.__mvFolderSync.connectById(id); }); }
          function openNewFolder() { startConnect(function () { window.__mvFolderSync.connect(); }); }
          function dropCard(cardEl) {
            var idx = cards.indexOf(cardEl);
            if (idx >= 0) {
              cards.splice(idx, 1);
              if (idx < selected) selected -= 1;   // keep the highlight on the same card it was on
            }
            if (cardEl.parentNode) cardEl.parentNode.removeChild(cardEl);
            if (selected >= cards.length) selected = Math.max(0, cards.length - 1);
            if (selected < 0) selected = 0;
            applySelected();
          }
          function removeRecent(id, cardEl) {
            if (window.__mvFolderSync) window.__mvFolderSync.forget(id);
            queue({ type: "remove", id: id });
            dropCard(cardEl);
          }
          function discardRecovery(cardEl) {
            queue({ type: "clearRecovery" });
            dropCard(cardEl);
          }

          function deepLinkToken() {
            var h = window.location.hash || "";
            var t = h.replace(/^#\/?/, "");
            t = t.split("?")[0].split("/")[0].trim();
            return t;
          }

          // Switch the overlay's language: re-render locally (all languages ship in the config) and
          // queue the choice so Kotlin re-localizes the editor underneath and persists it.
          function selectLanguage(code) {
            closeLangMenu();
            if (!cfg || code === cfg.currentLanguage) return;
            cfg.currentLanguage = code;
            queue({ type: "setLanguage", id: code });
            render(deepLinkToken());
          }

          function makeLangDropdown() {
            var wrap = el("div", "mvl-lang-wrap");
            var trigger = el("button", "mvl-lang"); trigger.type = "button";
            trigger.setAttribute("aria-haspopup", "true");
            trigger.setAttribute("aria-label", STR.language || "Language");
            trigger.title = STR.language || "Language";
            var gic = el("span", "mvl-lang-ic"); gic.textContent = "🌐"; gic.setAttribute("aria-hidden", "true"); trigger.appendChild(gic);
            var lbl = el("span"); lbl.textContent = langNativeName(cfg.currentLanguage); trigger.appendChild(lbl);
            var chev = el("span", "mvl-lang-chev"); chev.textContent = "▾"; chev.setAttribute("aria-hidden", "true"); trigger.appendChild(chev);
            var menu = el("div", "mvl-lang-menu");
            var langs = (cfg && cfg.languages) || [];
            for (var i = 0; i < langs.length; i++) {
              (function (lang) {
                var item = el("button", "mvl-lang-item"); item.type = "button";
                item.setAttribute("aria-label", lang.nativeName);
                var chk = el("span", "mvl-lang-check"); chk.textContent = (lang.code === cfg.currentLanguage) ? "✓" : ""; item.appendChild(chk);
                var nm = el("span"); nm.textContent = lang.nativeName; item.appendChild(nm);
                item.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); selectLanguage(lang.code); });
                menu.appendChild(item);
              })(langs[i]);
            }
            trigger.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); menu.classList.toggle("mvl-open"); });
            wrap.appendChild(trigger); wrap.appendChild(menu);
            langMenuEl = menu;
            return wrap;
          }

          function render(token) {
            // Idempotent: drop any prior overlay + its listener/timer so a re-render (language switch)
            // or a repeated install never stacks a second overlay or leaks a keydown handler.
            var existing = document.getElementById(ROOT_ID);
            var wasShowing = !!existing;   // a re-render (e.g. language switch) should swap instantly
            if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
            if (keyHandler) { window.removeEventListener("keydown", keyHandler, true); keyHandler = null; }
            stopFolderTimer();
            ensureStyle();
            STR = currentStrings();
            langMenuEl = null;
            cards = [];
            selected = 0;
            var root = el("div"); root.id = ROOT_ID;
            applyColors(root);
            // A click anywhere outside the language menu closes it; a click on the backdrop dismisses.
            root.addEventListener("click", function (e) { closeLangMenu(); if (e.target === root) dismiss(); });

            var panel = el("div", "mvl-panel");
            var head = el("div", "mvl-head");
            var headText = el("div");
            var app = el("div", "mvl-app"); app.textContent = cfg.appName || "Mission Visualization"; headText.appendChild(app);
            var title = el("div", "mvl-title"); title.textContent = STR.heading; headText.appendChild(title);
            var sub = el("div", "mvl-sub"); sub.textContent = STR.subtitle; headText.appendChild(sub);
            head.appendChild(headText);
            // Top-right actions: language switcher + close.
            var actionsBar = el("div", "mvl-actions");
            actionsBar.appendChild(makeLangDropdown());
            var close = el("button", "mvl-close"); close.type = "button"; close.textContent = "✕";
            close.title = STR.dismiss; close.setAttribute("aria-label", STR.dismiss);
            close.addEventListener("click", function (e) { e.preventDefault(); dismiss(); });
            actionsBar.appendChild(close);
            head.appendChild(actionsBar);
            panel.appendChild(head);

            var grid = el("div", "mvl-grid");

            var welcome = makeCard("✦", STR.welcomeTitle, STR.welcomeSubtitle, openWelcome, null);
            grid.appendChild(welcome); cards.push(welcome);

            if (cfg.hasRecovery) {
              var rec = makeCard("↺", STR.recoverTitle, STR.recoverSubtitle, openRecovery,
                function (cardEl) { discardRecovery(cardEl); });
              grid.appendChild(rec); cards.push(rec);
            }

            var recents = (cfg.recents || []);
            for (var i = 0; i < recents.length; i++) {
              (function (r) {
                if (r.kind !== "localFolder") return;
                var card = makeCard("📁", r.displayName || STR.localFolder, STR.localFolder,
                  function () { openFolder(r.id); },
                  function (cardEl) { removeRecent(r.id, cardEl); });
                card.setAttribute("data-mvl-id", r.id);
                grid.appendChild(card); cards.push(card);
              })(recents[i]);
            }

            if (cfg.supportsFolders) {
              var add = makeCard("+", STR.connectFolder, "", openNewFolder, null);
              add.classList.add("mvl-add");
              grid.appendChild(add); cards.push(add);
            }

            panel.appendChild(grid);

            if (!cfg.supportsFolders) {
              var note = el("div", "mvl-note");
              var isBrave = (typeof navigator !== "undefined" && !!navigator.brave);
              if (isBrave) {
                note.textContent = STR.foldersUnavailableBrave + " ";
                var copyBtn = el("button", "mvl-flag-copy");
                copyBtn.type = "button";
                copyBtn.textContent = STR.copyFlagAddress;
                copyBtn.addEventListener("click", function (e) {
                  e.preventDefault();
                  var flagUrl = "brave://flags/#file-system-access-api";
                  function done() {
                    copyBtn.textContent = STR.flagAddressCopied;
                    setTimeout(function () { copyBtn.textContent = STR.copyFlagAddress; }, 1600);
                  }
                  if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(flagUrl).then(done, done);
                  } else {
                    var ta = document.createElement("textarea");
                    ta.value = flagUrl; ta.style.position = "fixed"; ta.style.opacity = "0";
                    document.body.appendChild(ta); ta.select();
                    try { document.execCommand("copy"); } catch (err) {}
                    document.body.removeChild(ta);
                    done();
                  }
                });
                note.appendChild(copyBtn);
              } else {
                note.textContent = STR.foldersUnavailable;
              }
              panel.appendChild(note);
            }

            root.appendChild(panel);
            document.body.appendChild(root);

            // Preselect the deep-linked folder card if present, else Welcome.
            if (token) {
              for (var s = 0; s < cards.length; s++) {
                if (cards[s].getAttribute && cards[s].getAttribute("data-mvl-id") === token) { selected = s; break; }
              }
            }
            applySelected();

            keyHandler = function (e) {
              if (e.key === "Escape") {
                e.preventDefault();
                if (langMenuEl && langMenuEl.classList.contains("mvl-open")) closeLangMenu(); else dismiss();
              }
              else if (e.key === "Enter") { e.preventDefault(); if (cards[selected] && cards[selected].__open) cards[selected].__open(); }
              else if (e.key === "ArrowRight" || e.key === "ArrowDown") { e.preventDefault(); move(1); }
              else if (e.key === "ArrowLeft" || e.key === "ArrowUp") { e.preventDefault(); move(-1); }
            };
            window.addEventListener("keydown", keyHandler, true);

            // Initial install fades in; a re-render (language switch) swaps in place without a flash.
            if (wasShowing) root.classList.add("mvl-in");
            else requestAnimationFrame(function () { root.classList.add("mvl-in"); });
          }

          window.__mvLanding = {
            install: function (configJson) {
              try { cfg = JSON.parse(configJson); } catch (e) { console.error(e); return; }
              var prev = document.getElementById(ROOT_ID);
              if (prev && prev.parentNode) prev.parentNode.removeChild(prev);
              var token = deepLinkToken();
              if (token === "welcome") { queue({ type: "openWelcome" }); return; }  // deep-link straight to Welcome
              render(token);
            },
            hide: function () { hide(); },
            pendingAction: function () { return actions.length ? JSON.stringify(actions.shift()) : null; }
          };
        })();
        """
    )
}

internal actual val platformSupportsLanding: Boolean = true

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformInstallLanding(configJson: String) {
    ensureLandingInstalled()
    callLandingInstall(configJson)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun callLandingInstall(configJson: String): Unit = js("window.__mvLanding.install(configJson)")

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformHideLanding() {
    js("if (window.__mvLanding) window.__mvLanding.hide()")
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformLandingPendingActionJson(): String? =
    js("(window.__mvLanding ? window.__mvLanding.pendingAction() : null)")
