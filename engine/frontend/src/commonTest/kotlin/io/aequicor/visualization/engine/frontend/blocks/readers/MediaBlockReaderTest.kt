package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaBlockReaderTest {
    /** Spec image media example (~line 946-964). */
    @Test
    fun readsSpecImageExample() {
        val (patch, collector) = readSingle(
            """
            media:
              asset: assets/mission-map.png
              kind: image
              fillMode: crop
              focalPoint:
                x: 0.48
                y: 0.42
              alt:
                key: missionDashboard.map.alt
                defaultText: Карта активных миссий
              replaceable: true
              opacity: 1
              blendMode: normal
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            MediaPatch(
                asset = "assets/mission-map.png",
                kind = MediaKind.Image,
                fillMode = ImageScaleMode.Crop,
                focalPoint = DesignPoint(0.48, 0.42),
                alt = TextContent(
                    key = "missionDashboard.map.alt",
                    defaultText = "Карта активных миссий",
                ),
                replaceable = true,
                opacity = 1.0.bindable(),
                blendMode = "normal",
            ),
            patch,
        )
    }

    /** Spec video media example (~line 968-977). */
    @Test
    fun readsSpecVideoExample() {
        val (patch, collector) = readSingle(
            """
            media:
              asset: assets/launch-preview.mp4
              kind: video
              fillMode: fit
              poster: assets/launch-preview-poster.jpg
              autoplay: false
              loop: true
              muted: true
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            MediaPatch(
                asset = "assets/launch-preview.mp4",
                kind = MediaKind.Video,
                fillMode = ImageScaleMode.Fit,
                poster = "assets/launch-preview-poster.jpg",
                autoplay = false,
                loop = true,
                muted = true,
            ),
            patch,
        )
    }

    @Test
    fun centerFocalPointAndPlainAlt() {
        val (patch, _) = readSingle(
            """
            media:
              asset: a.png
              focalPoint: center
              alt: Карта
            """,
        )
        assertEquals(
            MediaPatch(
                asset = "a.png",
                focalPoint = DesignPoint(0.5, 0.5),
                alt = TextContent(defaultText = "Карта"),
            ),
            patch,
        )
    }
}
