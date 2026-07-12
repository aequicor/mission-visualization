package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Injected by the app to dereference an image reference — a media node's asset url/id or an
 * image paint's url — into a decoded [ImageBitmap]. Mirrors
 * [io.aequicor.visualization.subsystems.figures.VectorAssetProvider], but for raster (and
 * rasterized-SVG) content: the engine holds no image bytes, so the app loads them (from the
 * project resource store) and decodes them, handing the renderer only the bitmap.
 *
 * Loading is typically asynchronous. [generation] must change whenever a previously-unresolved
 * reference becomes resolvable, so the canvas re-reads the provider and redraws with the real
 * bitmap in place of the placeholder (see [DesignArtboard]).
 */
interface ImageAssetProvider {
    /** Monotonic value that changes when the set of resolvable images changes. */
    val generation: Int

    /**
     * The decoded bitmap for [ref] (an image paint/media url, else its asset id), or null when
     * the reference is unknown or not yet loaded — the renderer keeps its placeholder then.
     */
    fun resolve(ref: String): ImageBitmap?
}

/** No-op provider: resolves nothing. Media and image-paint nodes keep their placeholder render. */
object NoImages : ImageAssetProvider {
    override val generation: Int = 0
    override fun resolve(ref: String): ImageBitmap? = null
}
