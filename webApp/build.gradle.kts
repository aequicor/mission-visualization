import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.util.zip.GZIPOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared)

                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Production first-load packaging.
//
// wasmJsBrowserDistribution already runs Binaryen wasm-opt + DCE (the app wasm
// drops from ~27 MB dev to ~4 MB). This task takes the finished distribution
// the last mile for FIRST-load latency over the network:
//
//  1. Emits pre-compressed .gz (always, via the JDK) and .br (when the `brotli`
//     CLI is present) copies of every servable asset. wasm compresses ~3× (gzip)
//     / ~3.5× (brotli): the ~13 MB distribution ships as ~4.5 MB / ~3.8 MB.
//     Static hosts serve these transparently (nginx `gzip_static`/`brotli_static`,
//     Netlify/Cloudflare Pages, etc.).
//  2. Injects window.__MV_WASM_SIZES — the *uncompressed* wasm byte sizes — into
//     index.html so the boot download-meter (see resources/index.html) stays
//     byte-accurate even when the transport is compressed (there the browser
//     hands JS decompressed bytes while Content-Length is the compressed size).
//
// Run:  ./gradlew :webApp:packWasmDist   → build/dist/wasmJs/productionExecutable
// ---------------------------------------------------------------------------
tasks.register("packWasmDist") {
    group = "distribution"
    description = "Optimize the production wasm distribution for first load: inject the uncompressed-size manifest and emit pre-compressed .gz/.br copies."
    dependsOn("wasmJsBrowserDistribution")

    val distDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    outputs.dir(distDir)

    doLast {
        val dir = distDir.get().asFile
        require(dir.isDirectory) {
            "Distribution not found at $dir — run :webApp:wasmJsBrowserDistribution first."
        }

        // 1) Inject the uncompressed-size manifest right before the app loader.
        val wasms = (dir.listFiles { f -> f.isFile && f.extension == "wasm" } ?: emptyArray())
            .sortedBy { it.name }
        val manifest = wasms.joinToString(",") { "\"${it.name}\":${it.length()}" }
        val manifestTag = "<script>window.__MV_WASM_SIZES={$manifest};</script>"
        val indexHtml = File(dir, "index.html")
        if (indexHtml.isFile && wasms.isNotEmpty()) {
            var html = indexHtml.readText()
            // strip a manifest from any earlier run, then re-inject (idempotent)
            html = html.replace(Regex("<script>window\\.__MV_WASM_SIZES=\\{[^}]*};</script>\\n?"), "")
            val anchor = "<script type=\"application/javascript\" src=\"webApp.js\">"
            html = if (html.contains(anchor)) html.replaceFirst(anchor, "$manifestTag\n$anchor")
            else html.replaceFirst("</body>", "$manifestTag\n</body>")
            indexHtml.writeText(html)
            logger.lifecycle(
                "packWasmDist: injected wasm size manifest — " +
                    wasms.joinToString { "${it.name}=${it.length() / 1024}KB" }
            )
        }

        // 2) Pre-compress servable assets. gzip via the JDK; brotli if the CLI exists.
        val brotli = listOf("brotli", "/opt/homebrew/bin/brotli", "/usr/local/bin/brotli", "/usr/bin/brotli")
            .firstOrNull { path ->
                runCatching {
                    ProcessBuilder(path, "--version").redirectErrorStream(true).start().waitFor() == 0
                }.getOrDefault(false)
            }
        val servable = setOf("wasm", "js", "css", "html", "json", "webmanifest", "svg")
        var gz = 0
        var br = 0
        var raw = 0L
        var packed = 0L
        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in servable }
            .forEach { f ->
                val gzFile = File(f.parentFile, f.name + ".gz")
                runCatching {
                    gzFile.outputStream().use { out ->
                        GZIPOutputStream(out).use { z -> f.inputStream().use { it.copyTo(z) } }
                    }
                    gz++; raw += f.length(); packed += gzFile.length()
                }.onFailure { logger.warn("packWasmDist: gzip failed for ${f.name}: ${it.message}") }
                if (brotli != null) {
                    runCatching {
                        val ok = ProcessBuilder(brotli, "-f", "-q", "11", f.absolutePath)
                            .redirectErrorStream(true).start().waitFor() == 0
                        if (ok) br++
                    }
                }
            }
        logger.lifecycle(
            "packWasmDist: pre-compressed $gz asset(s) as .gz" +
                (if (brotli != null) " and $br as .br" else " (brotli CLI not found — .br skipped)") +
                " — ${raw / 1024}KB → gzip ${packed / 1024}KB."
        )
    }
}
