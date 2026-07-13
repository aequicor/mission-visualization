package io.aequicor.visualization.mcp

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LayoutPngRendererTest {
    @Test
    fun rendersScreenAndGroupAndPersistsIdenticalBytes() = runBlocking {
        val root = createTempDirectory("mcp-render-test")
        val source = root.resolve("sample.layout.md")
        source.writeText(testLayout("#FFFFFF"))
        val renderer = LayoutPngRenderer(root, root.resolve("renders"))

        val screen = renderer.render(
            RenderLayoutRequest("sample.layout.md", RenderTargetKind.Screen),
        )
        val group = renderer.render(
            RenderLayoutRequest("sample.layout.md", RenderTargetKind.Group, nodeId = "crop", scale = 2.0, padding = 4.0),
        )
        val component = renderer.render(
            RenderLayoutRequest("sample.layout.md", RenderTargetKind.Component, nodeId = "tileInstance"),
        )

        assertTrue(screen.width == 200 && screen.height == 120, "Unexpected screen size: ${screen.width}x${screen.height}")
        assertTrue(group.width == 176 && group.height == 96, "Unexpected group size: ${group.width}x${group.height}")
        assertTrue(component.width > 0 && component.height > 0)
        assertContentEquals(PngSignature, screen.pngBytes.copyOfRange(0, PngSignature.size))
        assertContentEquals(group.pngBytes, Files.readAllBytes(group.savedPath))
    }

    @Test
    fun rereadsSourceAndRejectsPathsOutsideAllowedRoot() {
        runBlocking {
            val root = createTempDirectory("mcp-render-root")
            val outside = createTempDirectory("mcp-render-outside").resolve("outside.layout.md")
            val source = root.resolve("sample.layout.md")
            source.writeText(testLayout("#FFFFFF"))
            outside.writeText(testLayout("#FFFFFF"))
            val renderer = LayoutPngRenderer(root, root.resolve("renders"))

            val first = renderer.render(RenderLayoutRequest("sample.layout.md", RenderTargetKind.Screen))
            source.writeText(testLayout("#112233"))
            val second = renderer.render(RenderLayoutRequest("sample.layout.md", RenderTargetKind.Screen))

            assertNotEquals(first.fingerprint, second.fingerprint)
            assertTrue(!first.pngBytes.contentEquals(second.pngBytes))
            assertFailsWith<RenderLayoutException> {
                renderer.render(RenderLayoutRequest(outside.toString(), RenderTargetKind.Screen))
            }
            assertFailsWith<RenderLayoutException> {
                renderer.render(RenderLayoutRequest("../${outside.fileName}", RenderTargetKind.Screen))
            }
        }
    }

    @Test
    fun rejectsInvalidFilesTargetsAndRenderLimits() = runBlocking {
        val root = createTempDirectory("mcp-render-validation")
        val renderer = LayoutPngRenderer(root, root.resolve("renders"))
        root.resolve("notes.md").writeText(testLayout("#FFFFFF"))
        root.resolve("broken.layout.md").writeText(testLayout("#FFFFFF").replace("id cropLabel", "id crop"))
        root.resolve("huge.layout.md").writeText(testLayout("#FFFFFF").replace("200", "9000"))
        root.resolve("sample.layout.md").writeText(testLayout("#FFFFFF"))

        assertTrue(renderer.check("sample.layout.md").valid)
        val invalidCheck = renderer.check("broken.layout.md")
        assertFalse(invalidCheck.valid)
        assertTrue(invalidCheck.diagnostics.isNotEmpty())

        assertContains(
            assertFailsWith<RenderLayoutException> {
                renderer.render(RenderLayoutRequest("notes.md", RenderTargetKind.Screen))
            }.message.orEmpty(),
            "*.layout.md",
        )
        assertFailsWith<RenderLayoutException> {
            renderer.render(RenderLayoutRequest("broken.layout.md", RenderTargetKind.Screen))
        }
        assertFailsWith<RenderLayoutException> {
            renderer.render(RenderLayoutRequest("sample.layout.md", RenderTargetKind.Group, nodeId = "missing"))
        }
        assertFailsWith<RenderLayoutException> {
            renderer.render(RenderLayoutRequest("sample.layout.md", RenderTargetKind.Screen, scale = 4.1))
        }
        assertContains(
            assertFailsWith<RenderLayoutException> {
                renderer.render(RenderLayoutRequest("huge.layout.md", RenderTargetKind.Screen))
            }.message.orEmpty(),
            "16 MP limit",
        )
    }

    private fun testLayout(color: String): String = """
        ---
        screen: renderTest
        page: Test
        sourceLocale: en-US
        frame:
          width: 200
          height: 120
        ---

        # Render Test id renderTest name «Render Test» 200 by 120 position 0 0 color $color

        ## Crop id crop name «Crop» 80 by 40 position 20 30 color #FF3366
        Text id cropLabel «Crop target»

        Instance id tileInstance of cmpTile position 120 70

        ## Component: Tile id cmpTile component-name Tile 50 by 24 color #22AA66
        Text id tileLabel «Tile»
    """.trimIndent() + "\n"

    private companion object {
        val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
