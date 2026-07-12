package io.aequicor.visualization.agent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import io.aequicor.visualization.engine.backend.compose.CanvasViewport
import io.aequicor.visualization.engine.backend.compose.DesignArtboard
import io.aequicor.visualization.engine.backend.compose.ImageAssetProvider
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deterministic pixel test for the raster image render seam ([DesignArtboard]'s `imageAssets`):
 * a media node referencing a resource ref must paint the injected bitmap, not the placeholder.
 */
class MediaImageRenderTest {

    private val magenta = ImageBitmap(40, 40).also { bmp ->
        Canvas(bmp).drawRect(Rect(0f, 0f, 40f, 40f), Paint().apply { color = Color.Magenta })
    }

    private val provider = object : ImageAssetProvider {
        override val generation: Int = 1
        override fun resolve(ref: String): ImageBitmap? = if (ref == "res/logo.png") magenta else null
    }

    private fun documentWithMedia(): DesignDocument {
        val media = DesignNode(
            id = "img1",
            type = "media",
            kind = DesignNodeKind.Media(
                DesignMedia(assetId = "res/logo.png".bindable(), fillMode = ImageScaleMode.Fill),
            ),
            position = DesignPoint(50.0, 50.0),
            size = DesignSize(200.0, 140.0),
            sizing = DesignSizing(SizingMode.Fixed, SizingMode.Fixed),
        )
        val frame = DesignNode(
            id = "frame1",
            type = "frame",
            kind = DesignNodeKind.Frame,
            size = DesignSize(400.0, 300.0),
            sizing = DesignSizing(SizingMode.Fixed, SizingMode.Fixed),
            children = listOf(media),
        )
        return DesignDocument(pages = listOf(DesignPage(id = "p1", children = listOf(frame))))
    }

    @Test
    fun mediaNodePaintsInjectedBitmap() {
        val document = documentWithMedia()
        ImageComposeScene(width = 400, height = 300, density = Density(1f)) {
            DesignArtboard(
                document = document,
                pageId = "p1",
                modifier = Modifier.fillMaxSize(),
                deviceWidth = 400.0,
                deviceHeight = 300.0,
                showSelection = false,
                interactive = false,
                imageAssets = provider,
            )
        }.use { scene ->
            val pixels = scene.render(0L).toComposeImageBitmap().toPixelMap()
            // Media node covers doc (50,50)-(250,190); sample its centre.
            val center = pixels[150, 120]
            assertTrue(
                center.red > 0.6f && center.blue > 0.6f && center.green < 0.4f,
                "media centre should be magenta from the injected bitmap, was $center",
            )
        }
    }

    private fun mediaNode() = DesignNode(
        id = "dropimg",
        type = "media",
        kind = DesignNodeKind.Media(
            DesignMedia(assetId = "res/logo.png".bindable(), fillMode = ImageScaleMode.Fill),
        ),
        position = DesignPoint(600.0, 422.0),
        size = DesignSize(240.0, 180.0),
        sizing = DesignSizing(SizingMode.Fixed, SizingMode.Fixed),
        layoutChild = io.aequicor.visualization.engine.ir.model.DesignLayoutChild(absolute = true),
    )

    private fun countMagenta(doc: DesignDocument, pageId: String): Int {
        val (w, h) = HeadlessRenderer.screenSize(doc, pageId)
        var magenta = 0
        ImageComposeScene(width = w.toInt(), height = h.toInt(), density = Density(1f)) {
            DesignArtboard(
                document = doc, pageId = pageId, modifier = Modifier.fillMaxSize(),
                deviceWidth = w, deviceHeight = h, showSelection = false, interactive = false,
                imageAssets = provider,
            )
        }.use { scene ->
            val pixels = scene.render(0L).toComposeImageBitmap().toPixelMap()
            var y = 0
            while (y < pixels.height) {
                var x = 0
                while (x < pixels.width) {
                    val c = pixels[x, y]
                    if (c.red > 0.6f && c.blue > 0.6f && c.green < 0.4f) magenta++
                    x += 4
                }
                y += 4
            }
        }
        return magenta
    }

    @Test
    fun mediaDroppedIntoWelcomeMockupRendersOnTop() {
        // Regression for the z-order bug: the layout sorts siblings by explicit `order`, so a
        // dropped media with an above-siblings order must render on top of the mockup's panels
        // (a null-order media sorted behind them and was hidden). Mirrors the reducer's ordering.
        val session = AgentSession.fromSamples()
        val document = assertNotNull(session.document)
        val rootFrame = document.pageById("welcomeEditor")!!.children.first()
        val topOrder = (rootFrame.children.mapNotNull { it.order }.maxOrNull() ?: 0) + 1
        val media = mediaNode().copy(order = topOrder)
        val doc = document.updateNode(rootFrame.id) { it.copy(children = it.children + media) }
        val magenta = countMagenta(doc, "welcomeEditor")
        assertTrue(magenta > 500, "on-top dropped image must render in the mockup; found $magenta")
    }

    @Test
    fun mediaNodePaintsUnderFractionalZoomViewport() {
        val document = documentWithMedia()
        // Mimic the editor: a fractional-zoom + panned viewport (the wasm canvas transform).
        val zoom = 0.5f
        val panX = 10f
        val panY = 10f
        ImageComposeScene(width = 400, height = 300, density = Density(1f)) {
            DesignArtboard(
                document = document,
                pageId = "p1",
                modifier = Modifier.fillMaxSize(),
                deviceWidth = 400.0,
                deviceHeight = 300.0,
                showSelection = false,
                interactive = false,
                viewport = CanvasViewport(zoom, panX, panY),
                imageAssets = provider,
            )
        }.use { scene ->
            val pixels = scene.render(0L).toComposeImageBitmap().toPixelMap()
            // Media centre doc (150,120) -> screen (150*zoom+panX, 120*zoom+panY).
            val sx = (150f * zoom + panX).toInt()
            val sy = (120f * zoom + panY).toInt()
            val center = pixels[sx, sy]
            assertTrue(
                center.red > 0.6f && center.blue > 0.6f && center.green < 0.4f,
                "media centre at screen ($sx,$sy) should be magenta under zoom, was $center",
            )
        }
    }
}
