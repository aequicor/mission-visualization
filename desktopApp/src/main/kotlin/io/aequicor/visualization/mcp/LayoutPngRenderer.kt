package io.aequicor.visualization.mcp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.use
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import io.aequicor.visualization.editor.data.SlmVectorAssetProvider
import io.aequicor.visualization.editor.domain.editorSlmCompileOptions
import io.aequicor.visualization.editor.ui.rememberVectorAssetProvider
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.backend.compose.ImageAssetProvider
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.literalOrNull
import io.aequicor.visualization.engine.ir.validate.validateDesignDocument
import io.aequicor.visualization.subsystems.typography.compose.rememberBundledFontProvider
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import kotlin.io.path.name
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.EncodedImageFormat

internal enum class RenderTargetKind { Screen, Component, Group }

internal data class RenderLayoutRequest(
    val layoutPath: String,
    val targetKind: RenderTargetKind,
    val nodeId: String? = null,
    val scale: Double = 1.0,
    val padding: Double = 0.0,
)

internal data class RenderLayoutResult(
    val source: Path,
    val target: String,
    val width: Int,
    val height: Int,
    val scale: Double,
    val fingerprint: Long,
    val diagnostics: List<DesignDiagnostic>,
    val savedPath: Path,
    val pngBytes: ByteArray,
)

internal data class CheckLayoutResult(
    val source: Path,
    val valid: Boolean,
    val fingerprint: Long,
    val diagnostics: List<DesignDiagnostic>,
)

internal class RenderLayoutException(message: String) : IllegalArgumentException(message)

internal class LayoutPngRenderer(
    private val allowedRoot: Path,
    private val outputDirectory: Path = Path.of(
        System.getProperty("user.home") ?: ".",
        ".mission-visualization",
        "mcp-renders",
    ),
) {
    private val renderMutex = Mutex()
    private val canonicalRoot = allowedRoot.toRealPath()

    suspend fun check(layoutPath: String): CheckLayoutResult = withContext(Dispatchers.IO) {
        val sourcePath = resolveLayoutPath(layoutPath)
        val source = Files.readString(sourcePath, StandardCharsets.UTF_8)
        val result = compileSlm(source, editorSlmCompileOptions(sourcePath.fileName.toString()))
        val diagnostics = result.document
            ?.let { document -> (result.diagnostics + validateDesignDocument(document)).distinct() }
            ?: result.diagnostics
        CheckLayoutResult(
            source = sourcePath,
            valid = result.document != null && diagnostics.none { it.severity == DesignSeverity.Error },
            fingerprint = result.sourceFingerprint,
            diagnostics = diagnostics,
        )
    }

    suspend fun render(request: RenderLayoutRequest): RenderLayoutResult {
        requireRequest(request)
        val compiled = withContext(Dispatchers.IO) {
            val sourcePath = resolveLayoutPath(request.layoutPath)
            val source = Files.readString(sourcePath, StandardCharsets.UTF_8)
            val result = compileSlm(source, editorSlmCompileOptions(sourcePath.fileName.toString()))
            if (result.document == null) {
                throw RenderLayoutException(formatCompileFailure(result.diagnostics))
            }
            val document = requireNotNull(result.document)
            val diagnostics = (result.diagnostics + validateDesignDocument(document)).distinct()
            val errors = diagnostics.filter { it.severity == DesignSeverity.Error }
            if (errors.isNotEmpty()) {
                throw RenderLayoutException(formatCompileFailure(errors))
            }
            val page = document.pages.firstOrNull()
                ?: throw RenderLayoutException("The compiled layout contains no page")
            val rootNode = page.children.firstOrNull()
                ?: throw RenderLayoutException("The compiled layout contains no screen root")
            validateTarget(document, rootNode, request)
            CompiledInput(
                sourcePath = sourcePath,
                document = document,
                pageId = page.id,
                rootNode = rootNode,
                fingerprint = result.sourceFingerprint,
                diagnostics = diagnostics,
                imageAssets = loadLocalImages(document, sourcePath),
            )
        }

        val rendered = renderMutex.withLock {
            withContext(Dispatchers.Swing) { renderOnSwing(compiled, request) }
        }
        val savedPath = withContext(Dispatchers.IO) {
            savePng(compiled.sourcePath, request, rendered.pngBytes)
        }
        return RenderLayoutResult(
            source = compiled.sourcePath,
            target = targetLabel(request),
            width = rendered.width,
            height = rendered.height,
            scale = request.scale,
            fingerprint = compiled.fingerprint,
            diagnostics = compiled.diagnostics,
            savedPath = savedPath,
            pngBytes = rendered.pngBytes,
        )
    }

    private fun requireRequest(request: RenderLayoutRequest) {
        if (request.scale !in 0.25..4.0) throw RenderLayoutException("scale must be between 0.25 and 4.0")
        if (!request.padding.isFinite() || request.padding < 0.0) {
            throw RenderLayoutException("padding must be a finite non-negative number")
        }
        if (request.targetKind != RenderTargetKind.Screen && request.nodeId.isNullOrBlank()) {
            throw RenderLayoutException("target.node_id is required for ${request.targetKind.name.lowercase()}")
        }
    }

    private fun resolveLayoutPath(value: String): Path {
        if (value.isBlank()) throw RenderLayoutException("layout_path is required")
        val requested = Path.of(value)
        val candidate = if (requested.isAbsolute) requested else canonicalRoot.resolve(requested)
        val real = runCatching { candidate.toRealPath() }
            .getOrElse { throw RenderLayoutException("Layout file is not accessible: $candidate") }
        if (!real.startsWith(canonicalRoot)) throw RenderLayoutException("layout_path is outside the allowed folder")
        if (!Files.isRegularFile(real)) throw RenderLayoutException("layout_path is not a file")
        if (!real.fileName.toString().endsWith(".layout.md", ignoreCase = true)) {
            throw RenderLayoutException("Only *.layout.md files can be rendered")
        }
        return real
    }

    private fun validateTarget(document: DesignDocument, root: DesignNode, request: RenderLayoutRequest) {
        val node = request.nodeId?.let(document::nodeById)
        when (request.targetKind) {
            RenderTargetKind.Screen -> Unit
            RenderTargetKind.Component -> if (node?.kind !is DesignNodeKind.Instance) {
                throw RenderLayoutException("Component '${request.nodeId}' was not found or is not a placed instance")
            }
            RenderTargetKind.Group -> if (node == null || node.id == root.id || node.kind !is DesignNodeKind.Frame) {
                throw RenderLayoutException("Group '${request.nodeId}' was not found or is not a non-root frame/group container")
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderOnSwing(input: CompiledInput, request: RenderLayoutRequest): RenderedImage {
        var measuredLayout: LayoutBox? = null
        ImageComposeScene(
            width = 1,
            height = 1,
            density = Density(1f),
        ) {
            DesignArtboard(
                document = input.document,
                pageId = input.pageId,
                modifier = Modifier.fillMaxSize(),
                deviceWidth = input.document.screen?.frame?.width ?: input.rootNode.size.width,
                deviceHeight = input.document.screen?.frame?.height ?: input.rootNode.size.height,
                showSelection = false,
                interactive = false,
                vectorAssets = SlmVectorAssetProvider(document = input.document),
                imageAssets = input.imageAssets,
                fontProvider = rememberBundledFontProvider(),
                onLayoutComputed = { measuredLayout = it },
            )
        }.use { scene ->
            scene.render().close()
            if (measuredLayout == null) scene.render(1L).close()
        }
        val rootLayout = measuredLayout ?: throw RenderLayoutException("Unable to measure the rendered artboard")
        val crop = when (request.targetKind) {
            RenderTargetKind.Screen -> rootLayout
            else -> rootLayout.findBySourceId(requireNotNull(request.nodeId))
                ?: throw RenderLayoutException("Target node '${request.nodeId}' has no rendered layout box")
        }
        if (crop.width <= 0.0 || crop.height <= 0.0) {
            throw RenderLayoutException("Target '${targetLabel(request)}' has empty dimensions")
        }
        val width = ceil((crop.width + request.padding * 2.0) * request.scale).toInt()
        val height = ceil((crop.height + request.padding * 2.0) * request.scale).toInt()
        enforcePixelLimits(width, height)
        val zoom = request.scale.toFloat()
        val viewport = CanvasViewport(
            zoom = zoom,
            panX = ((request.padding - crop.x) * request.scale).toFloat(),
            panY = ((request.padding - crop.y) * request.scale).toFloat(),
        )
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            val vectorAssets = rememberVectorAssetProvider(input.document)
            DesignArtboard(
                document = input.document,
                pageId = input.pageId,
                modifier = Modifier.fillMaxSize(),
                deviceWidth = input.document.screen?.frame?.width ?: input.rootNode.size.width,
                deviceHeight = input.document.screen?.frame?.height ?: input.rootNode.size.height,
                viewport = viewport,
                showSelection = false,
                interactive = false,
                vectorAssets = vectorAssets,
                imageAssets = input.imageAssets,
                fontProvider = rememberBundledFontProvider(),
            )
        }
        return scene.use {
            it.render().close()
            val image = it.render(1L)
            try {
                val png = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                    ?: throw RenderLayoutException("Skia failed to encode the rendered image as PNG")
                RenderedImage(width, height, png)
            } finally {
                image.close()
            }
        }
    }

    private fun enforcePixelLimits(width: Int, height: Int) {
        if (width <= 0 || height <= 0) throw RenderLayoutException("Rendered image dimensions are invalid")
        if (width > 8192 || height > 8192 || width.toLong() * height.toLong() > 16_000_000L) {
            throw RenderLayoutException("Rendered image exceeds the 8192 px side or 16 MP limit ($width x $height)")
        }
    }

    private fun loadLocalImages(document: DesignDocument, layoutPath: Path): ImageAssetProvider {
        val decoded = collectImageRefs(document).mapNotNull { ref ->
            val bytes = localAssetBytes(document, layoutPath, ref) ?: return@mapNotNull null
            val bitmap = runCatching { bytes.decodeToImageBitmap() }.getOrNull() ?: return@mapNotNull null
            ref to bitmap
        }.toMap()
        return FixedImageAssetProvider(decoded)
    }

    private fun collectImageRefs(document: DesignDocument): Set<String> = buildSet {
        fun visit(node: DesignNode) {
            (node.kind as? DesignNodeKind.Media)?.media?.let { media ->
                media.assetId.literalOrNull()?.takeIf(String::isNotBlank)?.let(::add)
                media.posterAssetId.literalOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            }
            node.fills.orEmpty().forEach { paint ->
                when (paint) {
                    is DesignPaint.Image -> add(paint.assetId)
                    is DesignPaint.Video -> {
                        add(paint.assetId)
                        paint.posterAssetId.takeIf(String::isNotBlank)?.let(::add)
                    }
                    else -> Unit
                }
            }
            node.children.forEach(::visit)
        }
        document.pages.forEach { page -> page.children.forEach(::visit) }
    }

    private fun localAssetBytes(document: DesignDocument, layoutPath: Path, ref: String): ByteArray? {
        val value = document.assets[ref]?.url?.takeIf(String::isNotBlank) ?: ref
        if (value.startsWith("data:", ignoreCase = true)) return decodeDataUri(value)
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) return null
        val rawPath = if (value.startsWith("file:", ignoreCase = true)) {
            runCatching { Path.of(URI(value)) }.getOrNull() ?: return null
        } else {
            Path.of(value.replace('/', java.io.File.separatorChar))
        }
        val candidates = if (rawPath.isAbsolute) listOf(rawPath) else listOf(layoutPath.parent.resolve(rawPath), canonicalRoot.resolve(rawPath))
        val real = candidates.firstNotNullOfOrNull { candidate ->
            runCatching { candidate.toRealPath() }.getOrNull()?.takeIf { it.startsWith(canonicalRoot) && Files.isRegularFile(it) }
        } ?: return null
        return runCatching { Files.readAllBytes(real) }.getOrNull()
    }

    private fun decodeDataUri(value: String): ByteArray? {
        val comma = value.indexOf(',')
        if (comma < 0) return null
        val header = value.substring(0, comma)
        val payload = value.substring(comma + 1)
        return if (header.contains(";base64", ignoreCase = true)) {
            runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
        } else {
            runCatching { java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8).toByteArray() }.getOrNull()
        }
    }

    private fun savePng(source: Path, request: RenderLayoutRequest, pngBytes: ByteArray): Path {
        Files.createDirectories(outputDirectory)
        val sourceName = source.name.removeSuffix(".layout.md").safeName()
        val targetName = targetLabel(request).safeName()
        val scaleName = request.scale.toString().replace('.', '_')
        val output = outputDirectory.resolve("${sourceName}__${targetName}__${scaleName}x.png")
        val temp = Files.createTempFile(outputDirectory, ".mcp-render-", ".tmp")
        try {
            Files.write(temp, pngBytes)
            runCatching {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        return output
    }

    private fun String.safeName(): String = replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(80).ifBlank { "render" }

    private fun targetLabel(request: RenderLayoutRequest): String = when (request.targetKind) {
        RenderTargetKind.Screen -> "screen"
        RenderTargetKind.Component -> "component-${request.nodeId}"
        RenderTargetKind.Group -> "group-${request.nodeId}"
    }

    private fun formatCompileFailure(diagnostics: List<DesignDiagnostic>): String = buildString {
        appendLine("SLM compilation failed:")
        diagnostics.forEach { diagnostic ->
            val line = diagnostic.location?.line?.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            appendLine("${diagnostic.severity.name.uppercase()}$line — ${diagnostic.message}")
        }
    }.trimEnd()

    private data class CompiledInput(
        val sourcePath: Path,
        val document: DesignDocument,
        val pageId: String,
        val rootNode: DesignNode,
        val fingerprint: Long,
        val diagnostics: List<DesignDiagnostic>,
        val imageAssets: ImageAssetProvider,
    )

    private data class RenderedImage(val width: Int, val height: Int, val pngBytes: ByteArray)

    private class FixedImageAssetProvider(private val images: Map<String, ImageBitmap>) : ImageAssetProvider {
        override val generation: Int = images.hashCode()
        override fun resolve(ref: String): ImageBitmap? = images[ref]
    }
}
