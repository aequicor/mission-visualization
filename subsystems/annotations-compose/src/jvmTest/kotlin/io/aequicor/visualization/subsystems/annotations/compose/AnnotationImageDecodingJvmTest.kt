package io.aequicor.visualization.subsystems.annotations.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class AnnotationImageDecodingJvmTest {

    /** A 1x1 transparent PNG. */
    private val onePixelPng =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    @Test
    fun decodesPngDataUriToImageBitmap() {
        val bitmap = decodeAnnotationImage("data:image/png;base64,$onePixelPng")

        assertNotNull(bitmap)
        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
    }

    @Test
    fun toleratesMissingBase64Padding() {
        assertNotNull(decodeAnnotationImage("data:image/png;base64,${onePixelPng.trimEnd('=')}"))
    }

    @Test
    fun returnsNullForUndecodablePayloads() {
        assertNull(decodeAnnotationImage("data:image/png;base64,%%%not-base64%%%"))
        assertNull(decodeAnnotationImage("data:image/png;base64,QUJD")) // "ABC" is not a bitmap
        assertNull(decodeAnnotationImage("assets/screenshot.png"))
    }

    @Test
    fun cacheReturnsTheSameDecodedBitmapInstanceAcrossHits() {
        val cache = AnnotationImageCache()
        val source = "data:image/png;base64,$onePixelPng"
        val bitmap = decodeAnnotationImage(source)
        assertNotNull(bitmap)

        cache.put(source, bitmap)

        val first = cache.get(source)
        val second = cache.get(source)
        assertNotNull(first)
        assertNotNull(second)
        assertSame(bitmap, first.bitmap, "cache must hand back the decoded bitmap, not re-decode")
        assertSame(bitmap, second.bitmap, "repeated hits (e.g. expand/collapse) reuse one instance")
    }
}
