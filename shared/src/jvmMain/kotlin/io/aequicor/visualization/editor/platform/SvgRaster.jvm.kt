package io.aequicor.visualization.editor.platform

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

/** Rasterizes SVG resources through the same Skia runtime Compose Desktop renders with. */
internal actual suspend fun rasterizeSvgToPng(svgBytes: ByteArray): ByteArray? =
    withContext(Dispatchers.Default) {
        runCatching {
            val intrinsic = parseSvgIntrinsicSize(svgBytes) ?: return@runCatching null
            val (width, height) = boundedRasterSize(intrinsic.first, intrinsic.second)
            Data.makeFromBytes(svgBytes).use { data ->
                SVGDOM(data).use { dom ->
                    require(dom.root != null) { "Invalid SVG document" }
                    dom.setContainerSize(width.toFloat(), height.toFloat())
                    Surface.makeRasterN32Premul(width, height).use { surface ->
                        surface.canvas.clear(0x00000000)
                        dom.render(surface.canvas)
                        surface.makeImageSnapshot().use { image ->
                            image.encodeToData(EncodedImageFormat.PNG)?.use { encoded -> encoded.bytes }
                        }
                    }
                }
            }
        }.getOrNull()
    }

/** Keeps hostile/accidental huge intrinsic sizes from allocating an unbounded raster surface. */
private fun boundedRasterSize(width: Double, height: Double): Pair<Int, Int> {
    val maxDimensionScale = min(1.0, MaxSvgRasterDimension / max(width, height))
    val pixelScale = min(1.0, sqrt(MaxSvgRasterPixels / (width * height)))
    val scale = min(maxDimensionScale, pixelScale)
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}

private const val MaxSvgRasterDimension = 4096.0
private const val MaxSvgRasterPixels = 16_777_216.0
