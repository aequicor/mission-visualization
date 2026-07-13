package io.aequicor.visualization.mcp

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.nio.file.Path
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal class MissionMcpServerHost private constructor(private val stopAction: () -> Unit) {
    fun stop() = stopAction()

    companion object {
        fun start(
            port: Int,
            allowedRoot: Path,
            renderOutputDirectory: Path? = null,
        ): MissionMcpServerHost {
            val renderer = renderOutputDirectory?.let { LayoutPngRenderer(allowedRoot, it) }
                ?: LayoutPngRenderer(allowedRoot)
            val engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                mcpStreamableHttp(path = "/mcp") { createServer(renderer, allowedRoot) }
            }
            engine.start(wait = false)
            return MissionMcpServerHost {
                engine.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
            }
        }

        private fun createServer(renderer: LayoutPngRenderer, allowedRoot: Path): Server {
            val server = Server(
                serverInfo = Implementation("mission-visualization", "1.0.0"),
                options = ServerOptions(
                    capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
                ),
                instructions = """
                    This server is read-only. It renders Semantic Layout Markdown files under:
                    ${allowedRoot.toAbsolutePath()}

                    Edit *.layout.md with the agent's normal file tools, call check_layout to validate it,
                    then call render_layout to inspect the resulting PNG. The MCP server never edits source files.
                """.trimIndent(),
            )
            server.addTool(
                name = "check_layout",
                description = "Compile a .layout.md file and return its validity, fingerprint, and diagnostics without rendering PNG.",
                inputSchema = checkToolSchema(),
                toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
            ) { request ->
                try {
                    val args = request.arguments ?: buildJsonObject {}
                    val layoutPath = args["layout_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    CallToolResult(content = listOf(TextContent(formatCheckResult(renderer.check(layoutPath)))))
                } catch (failure: Exception) {
                    CallToolResult(
                        content = listOf(TextContent(failure.message ?: "check_layout failed")),
                        isError = true,
                    )
                }
            }
            server.addTool(
                name = "render_layout",
                description = "Compile a .layout.md file and render a screen, placed component instance, or group as PNG.",
                inputSchema = renderToolSchema(),
                toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
            ) { request ->
                try {
                    val args = request.arguments ?: buildJsonObject {}
                    val layoutPath = args["layout_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val target = args["target"]?.jsonObject
                        ?: throw RenderLayoutException("target is required")
                    val kind = when (target["kind"]?.jsonPrimitive?.contentOrNull) {
                        "screen" -> RenderTargetKind.Screen
                        "component" -> RenderTargetKind.Component
                        "group" -> RenderTargetKind.Group
                        else -> throw RenderLayoutException("target.kind must be screen, component, or group")
                    }
                    val renderResult = renderer.render(
                        RenderLayoutRequest(
                            layoutPath = layoutPath,
                            targetKind = kind,
                            nodeId = target["node_id"]?.jsonPrimitive?.contentOrNull,
                            scale = args["scale"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                            padding = args["padding"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        ),
                    )
                    CallToolResult(
                        content = listOf(
                            TextContent(formatMetadata(renderResult)),
                            ImageContent(
                                data = Base64.getEncoder().encodeToString(renderResult.pngBytes),
                                mimeType = "image/png",
                            ),
                        ),
                    )
                } catch (failure: Exception) {
                    CallToolResult(
                        content = listOf(TextContent(failure.message ?: "render_layout failed")),
                        isError = true,
                    )
                }
            }
            return server
        }

        private fun checkToolSchema(): ToolSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("layout_path") {
                    put("type", "string")
                    put("description", "Absolute path inside the allowed folder, or a path relative to it.")
                }
            },
            required = listOf("layout_path"),
        )

        private fun renderToolSchema(): ToolSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("layout_path") {
                    put("type", "string")
                    put("description", "Absolute path inside the allowed folder, or a path relative to it.")
                }
                putJsonObject("target") {
                    put("description", "Render the full screen or crop to a placed node.")
                    put("oneOf", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("kind", buildJsonObject { put("const", "screen") })
                            })
                            put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("kind")) })
                            put("additionalProperties", false)
                        })
                        add(nodeTargetSchema("component"))
                        add(nodeTargetSchema("group"))
                    })
                }
                putJsonObject("scale") {
                    put("type", "number")
                    put("minimum", 0.25)
                    put("maximum", 4.0)
                    put("default", 1.0)
                }
                putJsonObject("padding") {
                    put("type", "number")
                    put("minimum", 0.0)
                    put("default", 0.0)
                    put("description", "Padding around the target in layout units.")
                }
            },
            required = listOf("layout_path", "target"),
        )

        private fun nodeTargetSchema(kind: String) = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("kind", buildJsonObject { put("const", kind) })
                put("node_id", buildJsonObject { put("type", "string") })
            })
            put("required", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("kind"))
                add(kotlinx.serialization.json.JsonPrimitive("node_id"))
            })
            put("additionalProperties", false)
        }

        private fun formatMetadata(result: RenderLayoutResult): String {
            return """
                source: ${result.source}
                target: ${result.target}
                dimensions: ${result.width}x${result.height} px
                scale: ${result.scale}
                source_fingerprint: ${java.lang.Long.toUnsignedString(result.fingerprint, 16)}
                saved_path: ${result.savedPath}
                diagnostics:
                ${formatDiagnostics(result.diagnostics)}
            """.trimIndent()
        }

        private fun formatCheckResult(result: CheckLayoutResult): String {
            val errors = result.diagnostics.count { it.severity == io.aequicor.visualization.engine.ir.model.DesignSeverity.Error }
            val warnings = result.diagnostics.count { it.severity == io.aequicor.visualization.engine.ir.model.DesignSeverity.Warning }
            return """
                source: ${result.source}
                valid: ${result.valid}
                source_fingerprint: ${java.lang.Long.toUnsignedString(result.fingerprint, 16)}
                errors: $errors
                warnings: $warnings
                diagnostics:
                ${formatDiagnostics(result.diagnostics)}
            """.trimIndent()
        }

        private fun formatDiagnostics(diagnostics: List<io.aequicor.visualization.engine.ir.model.DesignDiagnostic>): String =
            diagnostics.joinToString("\n") { diagnostic ->
                val code = diagnostic.code.takeIf(String::isNotBlank)?.let { " [$it]" }.orEmpty()
                val line = diagnostic.location?.line?.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
                "- ${diagnostic.severity.name.uppercase()}$code$line: ${diagnostic.message}"
            }.ifBlank { "- none" }
    }
}
