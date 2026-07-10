package io.aequicor.visualization.subsystems.annotations.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
