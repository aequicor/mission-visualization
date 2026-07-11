package io.aequicor.visualization.agent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.projection.ScreenComposer
import io.aequicor.visualization.subsystems.typography.compose.rememberBundledFontProvider
import kotlin.math.ceil
import org.jetbrains.skia.EncodedImageFormat

/**
 * Headless screen → PNG: hosts [DesignArtboard] in an off-screen skiko
 * [ImageComposeScene] (no window, real Compose text measurement + bundled fonts)
 * and encodes the rendered frame. This is the "read the screen" half of the agent
 * loop; the "update" half is editing the CNL sources themselves.
 */
object HeadlessRenderer {

    /** Default supersampling for crisp text in agent screenshots. */
    const val DEFAULT_SCALE: Float = 2f

    private const val FALLBACK_WIDTH = 1440.0
    private const val FALLBACK_HEIGHT = 1024.0

    /** Max render passes waiting for async invalidations (fonts/resources) to settle. */
    private const val MAX_RENDER_PASSES = 5
    private const val FRAME_NANOS = 16_000_000L

    /**
     * Renders one screen ([pageId]) of [document] to PNG bytes. The scene is sized to
     * the screen's root frame × [scale], so the artboard's zoom-to-fit lands at exactly
     * [scale] with no letterboxing.
     */
    fun renderPng(
        document: DesignDocument,
        pageId: String,
        scale: Float = DEFAULT_SCALE,
    ): ByteArray {
        require(scale > 0f) { "scale must be positive, got $scale" }
        val page = requireNotNull(document.pageById(pageId)) { "Unknown screen: $pageId" }
        val (docWidth, docHeight) = screenSize(document, pageId)
        val sceneWidth = ceil(docWidth * scale).toInt().coerceAtLeast(1)
        val sceneHeight = ceil(docHeight * scale).toInt().coerceAtLeast(1)

        return ImageComposeScene(
            width = sceneWidth,
            height = sceneHeight,
            density = Density(1f),
        ) {
            DesignArtboard(
                document = document,
                pageId = page.id,
                modifier = Modifier.fillMaxSize(),
                deviceWidth = docWidth,
                deviceHeight = docHeight,
                showSelection = false,
                interactive = false,
                fontProvider = rememberBundledFontProvider(),
            )
        }.use { scene ->
            // Fonts/resources may invalidate after the first pass; pump frames until stable.
            var image = scene.render(0L)
            var pass = 1
            while (scene.hasInvalidations() && pass < MAX_RENDER_PASSES) {
                image.close()
                image = scene.render(pass * FRAME_NANOS)
                pass++
            }
            image.use { frame ->
                val data = requireNotNull(frame.encodeToData(EncodedImageFormat.PNG)) {
                    "PNG encoding failed for screen $pageId"
                }
                data.use { it.bytes }
            }
        }
    }

    /**
     * Authored root-frame size of the screen; falls back to a Compose-free layout pass
     * (approximate text metrics) when the root has no explicit size, then to a desktop
     * frame preset.
     */
    fun screenSize(document: DesignDocument, pageId: String): Pair<Double, Double> {
        val root = document.pageById(pageId)?.children?.firstOrNull()
        val width = root?.size?.width
        val height = root?.size?.height
        if (width != null && height != null && width > 0 && height > 0) return width to height

        val composed = ScreenComposer(document, DesignLayoutEngine()).compose(pageId)
        val box = composed?.root
        if (box != null && box.width > 0 && box.height > 0) return box.width to box.height
        return FALLBACK_WIDTH to FALLBACK_HEIGHT
    }
}
