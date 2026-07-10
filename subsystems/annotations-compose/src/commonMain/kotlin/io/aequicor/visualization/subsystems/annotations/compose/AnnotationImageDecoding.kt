package io.aequicor.visualization.subsystems.annotations.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
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

/** [decodeAnnotationImage] memoized per [source] across recompositions. */
@Composable
fun rememberAnnotationImage(source: String): ImageBitmap? =
    remember(source) { decodeAnnotationImage(source) }
