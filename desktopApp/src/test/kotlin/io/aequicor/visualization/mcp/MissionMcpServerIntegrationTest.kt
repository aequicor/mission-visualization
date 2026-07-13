package io.aequicor.visualization.mcp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class MissionMcpServerIntegrationTest {
    @Test
    fun initializeListToolsAndRenderOverStreamableHttp() = runBlocking {
        val root = createTempDirectory("mcp-http-test")
        val validSource = """
            ---
            screen: httpTest
            frame: { width: 96, height: 64 }
            ---

            # HTTP Test id httpTest 96 by 64 color #FFFFFF
            Text id title «MCP»
        """.trimIndent() + "\n"
        root.resolve("screen.layout.md").writeText(validSource)
        root.resolve("broken.layout.md").writeText(validSource + "Text id title «Duplicate»\n")
        val port = ServerSocket(0).use { it.localPort }
        val host = MissionMcpServerHost.start(port, root, root.resolve("renders"))
        val httpClient = HttpClient { install(SSE) }
        val client = Client(Implementation("mission-test", "1.0"))
        try {
            client.connect(StreamableHttpClientTransport(httpClient, "http://127.0.0.1:$port/mcp"))
            val tools = client.listTools().tools
            assertEquals(listOf("check_layout", "render_layout"), tools.map { it.name })

            val validCheck = client.callTool(
                name = "check_layout",
                arguments = buildJsonObject { put("layout_path", "screen.layout.md") },
            )
            assertTrue(validCheck.isError != true)
            assertContains(assertIs<TextContent>(validCheck.content.single()).text, "valid: true")

            val invalidCheck = client.callTool(
                name = "check_layout",
                arguments = buildJsonObject { put("layout_path", "broken.layout.md") },
            )
            assertTrue(invalidCheck.isError != true)
            val invalidText = assertIs<TextContent>(invalidCheck.content.single()).text
            assertContains(invalidText, "valid: false")
            assertContains(invalidText, "errors:")

            val result = client.callTool(
                name = "render_layout",
                arguments = buildJsonObject {
                    put("layout_path", "screen.layout.md")
                    putJsonObject("target") { put("kind", "screen") }
                },
            )
            assertTrue(result.isError != true)
            assertIs<TextContent>(result.content.first())
            val image = assertIs<ImageContent>(result.content.last())
            assertEquals("image/png", image.mimeType)
            assertTrue(image.data.isNotBlank())
        } finally {
            client.close()
            httpClient.close()
            host.stop()
        }
    }
}
