package io.aequicor.visualization.editor.platform

import java.awt.Canvas
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JPanel
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourceIngestionJvmTest {
    @Test
    fun installsDropHandlingOnHeavyweightComposeCanvas() {
        val root = JPanel()
        val composePanel = JPanel()
        val skiaCanvas = Canvas()
        composePanel.add(skiaCanvas)
        root.add(composePanel)

        assertSame(skiaCanvas, findResourceIngestionTarget(root))
    }

    @Test
    fun readsPngFileWithOriginalBytesAndIntrinsicSize() {
        val path = createTempFile("desktop-ingestion", ".png")
        val source = BufferedImage(7, 5, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(2, 3, 0x7fff2200)
        ImageIO.write(source, "png", path.toFile())
        val expected = Files.readAllBytes(path)

        val payload = assertNotNull(readJvmImageFile(path.toFile()))

        assertEquals(path.fileName.toString(), payload.name)
        assertEquals(7.0, payload.width)
        assertEquals(5.0, payload.height)
        assertContentEquals(expected, payload.bytes)
    }

    @Test
    fun readsSvgViewBoxSizeAndRasterizesItOnDesktop() {
        runBlocking {
            val svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 18">
                  <rect width="32" height="18" fill="#ff3366"/>
                </svg>
            """.trimIndent().toByteArray()
            val path = createTempFile("desktop-ingestion", ".svg")
            Files.write(path, svg)

            val payload = assertNotNull(readJvmImageFile(path.toFile()))
            assertEquals(32.0, payload.width)
            assertEquals(18.0, payload.height)

            val png = assertNotNull(rasterizeSvgToPng(svg))
            assertTrue(png.size > 8)
            assertContentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
                png.copyOfRange(0, 8),
            )
            ImageIO.read(png.inputStream()).also { image ->
                assertEquals(32, image.width)
                assertEquals(18, image.height)
            }
        }
    }

    @Test
    fun svgPhysicalUnitsAndViewBoxInferMissingDimension() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="1in" viewBox="0 0 4 3"/>
        """.trimIndent().toByteArray()

        assertEquals(96.0 to 72.0, parseSvgIntrinsicSize(svg))
    }

    @Test
    fun parsesSvgThatDeclaresADoctype() {
        // Illustrator/Inkscape routinely emit this PUBLIC DOCTYPE pointing at the SVG 1.1 DTD.
        // The parser must tolerate the DOCTYPE (and must NOT fetch the remote DTD URL) and still
        // report the intrinsic size. This test stays offline precisely because the DTD is not read.
        val svg = (
            """<?xml version="1.0" encoding="UTF-8"?>
""" +
                """<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" """ +
                """"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
""" +
                """<svg width="24" height="24" xmlns="http://www.w3.org/2000/svg"/>"""
            ).toByteArray()

        assertEquals(24.0 to 24.0, parseSvgIntrinsicSize(svg))
    }

    @Test
    fun doesNotResolveExternalEntityDuringSvgParse() {
        // XXE probe: an external general entity points at a file that does not exist. If the parser
        // fetched external entities, the missing file would raise an exception and parsing would
        // fail (null). Because external entities are disabled and the resolver hands back an empty
        // source, parsing succeeds and the width/height are read as-is — no external I/O occurs.
        val missing = Files.createTempFile("xxe-probe", ".txt")
        Files.delete(missing)
        val systemId = missing.toUri().toString()
        val svg = (
            """<?xml version="1.0"?>
""" +
                """<!DOCTYPE svg [ <!ENTITY xxe SYSTEM "$systemId"> ]>
""" +
                """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">""" +
                """<desc>&xxe;</desc></svg>"""
            ).toByteArray()

        assertEquals(24.0 to 24.0, parseSvgIntrinsicSize(svg))
    }
}
