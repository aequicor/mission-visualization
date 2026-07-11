package io.aequicor.visualization.agent

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeadlessRendererTest {

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
        0x0D, 0x0A, 0x1A, 0x0A,
    )

    @Test
    fun rendersSampleScreenToValidPngAtRequestedScale() {
        val session = AgentSession.fromSamples()
        val document = assertNotNull(session.document)

        val png = HeadlessRenderer.renderPng(document, "welcomeVectors", scale = 1f)
        assertTrue(png.size > pngSignature.size, "png must not be empty")
        assertContentEquals(pngSignature, png.copyOfRange(0, pngSignature.size))

        // PNG IHDR: big-endian width at bytes 16..19, height at 20..23.
        assertEquals(1440, png.readIntBe(16), "width must equal the root frame at scale 1")
        assertEquals(1024, png.readIntBe(20), "height must equal the root frame at scale 1")
    }

    @Test
    fun scaleMultipliesPixelDimensions() {
        val session = AgentSession.fromSamples()
        val document = assertNotNull(session.document)
        val png = HeadlessRenderer.renderPng(document, "welcomeUml", scale = 2f)
        assertEquals(2880, png.readIntBe(16))
        assertEquals(2048, png.readIntBe(20))
    }

    @Test
    fun screenSizePrefersAuthoredRootFrame() {
        val session = AgentSession.fromSamples()
        val document = assertNotNull(session.document)
        assertEquals(1440.0 to 1024.0, HeadlessRenderer.screenSize(document, "welcomeEditor"))
    }

    private fun ByteArray.readIntBe(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}
