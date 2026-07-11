package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Base64 tolerant of data URIs that omit trailing padding. */
private val DataUriBase64: Base64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

/**
 * Extracts the base64 payload from a `data:*;base64,...` URI, or null when [source] is
 * not a base64 data URI (plain asset refs and utf8 data URIs are not decodable here).
 * Whitespace inside the payload is dropped.
 */
fun dataUriBase64Payload(source: String): String? {
    if (!source.startsWith("data:", ignoreCase = true)) return null
    val comma = source.indexOf(',')
    if (comma < 0) return null
    val header = source.substring("data:".length, comma)
    if (!header.contains("base64", ignoreCase = true)) return null
    return source.substring(comma + 1).filterNot(Char::isWhitespace).ifEmpty { null }
}

/**
 * Decodes an [io.aequicor.visualization.subsystems.annotations.AnnotationImage.source]
 * data URI into an [ImageBitmap]; null when the source is not a base64 data URI or the
 * payload is not a decodable bitmap (the card simply omits the image then).
 */
fun decodeAnnotationImage(source: String): ImageBitmap? {
    val payload = dataUriBase64Payload(source) ?: return null
    return runCatching { DataUriBase64.decode(payload).decodeToImageBitmap() }.getOrNull()
}

/** A recorded decode outcome; [bitmap] is null when the source was not decodable. */
data class AnnotationImageDecodeResult(val bitmap: ImageBitmap?)

/**
 * Small LRU of decode results keyed by the image source, shared across composition sites
 * (canvas card, inspector preview) so one attachment is decoded at most once instead of
 * on every card expand. Failed decodes are cached too, so an undecodable payload is not
 * re-parsed on each recomposition. Not thread-safe: confine to the UI thread (composition).
 */
class AnnotationImageCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    private val entries = LinkedHashMap<String, AnnotationImageDecodeResult>()

    /** Number of cached decode results (successful and failed). */
    val size: Int get() = entries.size

    /** Cached decode result for [source], or null when it has not been decoded yet. */
    fun get(source: String): AnnotationImageDecodeResult? {
        val result = entries.remove(source) ?: return null
        entries[source] = result // Refresh recency.
        return result
    }

    /** Records a decode outcome for [source], evicting the least recently used entry. */
    fun put(source: String, bitmap: ImageBitmap?) {
        entries.remove(source)
        entries[source] = AnnotationImageDecodeResult(bitmap)
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    companion object {
        private const val DEFAULT_MAX_ENTRIES = 8

        /** Process-wide cache used by [rememberAnnotationImage] by default. */
        val Shared: AnnotationImageCache = AnnotationImageCache()
    }
}

/**
 * [decodeAnnotationImage] memoized per [source] in [cache] (shared across composition
 * sites and expand/collapse cycles) and executed off the composition pass on
 * [decodeDispatcher]. Returns null until the decode lands or when the source is not
 * decodable; callers simply omit the image then.
 */
@Composable
fun rememberAnnotationImage(
    source: String,
    cache: AnnotationImageCache = AnnotationImageCache.Shared,
    decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
): ImageBitmap? {
    val cached = cache.get(source)
    if (cached != null) return cached.bitmap
    val decoded by produceState<ImageBitmap?>(initialValue = null, source, cache, decodeDispatcher) {
        val result = withContext(decodeDispatcher) { decodeAnnotationImage(source) }
        cache.put(source, result)
        value = result
    }
    return decoded
}
