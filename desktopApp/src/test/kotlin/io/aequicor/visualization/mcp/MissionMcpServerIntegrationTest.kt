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
import kotlinx.serialization.json.buildJsonArray
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
        var projectVerification: McpProjectVerification? = null
        val host = MissionMcpServerHost.start(
            port = port,
            allowedRoot = root,
            renderOutputDirectory = root.resolve("renders"),
            onProjectVerification = { projectVerification = it },
        )
        val httpClient = HttpClient { install(SSE) }
        val client = Client(Implementation("mission-test", "1.0"))
        try {
            client.connect(StreamableHttpClientTransport(httpClient, "http://127.0.0.1:$port/mcp"))
            val tools = client.listTools().tools
            assertEquals(
                listOf("get_mcp_skill", "get_slm_skills", "validate_project_setup", "check_layout", "render_layout"),
                tools.map { it.name },
            )

            val rootSkill = client.callTool(name = "get_mcp_skill", arguments = buildJsonObject {})
            assertTrue(rootSkill.isError != true)
            val rootSkillText = assertIs<TextContent>(rootSkill.content.single()).text
            assertContains(rootSkillText, "name: mission-visualization-mcp")
            assertContains(rootSkillText, "validate_project_setup")

            val skills = client.callTool(
                name = "get_slm_skills",
                arguments = buildJsonObject { put("skill", "diagrams") },
            )
            assertTrue(skills.isError != true)
            val skillsText = skills.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            assertContains(skillsText, "included_skills: slm, diagrams")
            assertContains(skillsText, "skill_name: slm-diagrams")
            assertTrue("skill_name: slm-vector-graphics" !in skillsText)

            val validation = client.callTool(
                name = "validate_project_setup",
                arguments = buildJsonObject {
                    put("project_path", root.toString())
                    put("agent_name", "integration-test-agent")
                    put("root_skill_installed", true)
                    put("slm_skills_installed", buildJsonArray {
                        listOf(
                            "slm",
                            "slm-diagrams",
                            "slm-vector-graphics",
                            "slm-typography",
                            "slm-annotations",
                            "slm-editor",
                        ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                },
            )
            assertTrue(validation.isError != true)
            assertContains(assertIs<TextContent>(validation.content.single()).text, "verified: true")
            assertEquals(true, projectVerification?.verified)
            assertEquals("integration-test-agent", projectVerification?.agentName)

            val wrongRoot = createTempDirectory("mcp-wrong-project")
            val invalidValidation = client.callTool(
                name = "validate_project_setup",
                arguments = buildJsonObject {
                    put("project_path", wrongRoot.toString())
                    put("agent_name", "integration-test-agent")
                    put("root_skill_installed", true)
                    put("slm_skills_installed", buildJsonArray {
                        listOf(
                            "slm",
                            "slm-diagrams",
                            "slm-vector-graphics",
                            "slm-typography",
                            "slm-annotations",
                            "slm-editor",
                        ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                },
            )
            assertTrue(invalidValidation.isError != true)
            val invalidValidationText = assertIs<TextContent>(invalidValidation.content.single()).text
            assertContains(invalidValidationText, "verified: false")
            assertContains(invalidValidationText, "does not match")
            assertEquals(false, projectVerification?.verified)

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
