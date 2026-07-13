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
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
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
            onProjectVerification: (McpProjectVerification) -> Unit = {},
        ): MissionMcpServerHost {
            val renderer = renderOutputDirectory?.let { LayoutPngRenderer(allowedRoot, it) }
                ?: LayoutPngRenderer(allowedRoot)
            val engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                mcpStreamableHttp(path = "/mcp") {
                    createServer(renderer, allowedRoot, onProjectVerification)
                }
            }
            engine.start(wait = false)
            return MissionMcpServerHost {
                engine.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
            }
        }

        private fun createServer(
            renderer: LayoutPngRenderer,
            allowedRoot: Path,
            onProjectVerification: (McpProjectVerification) -> Unit,
        ): Server {
            val server = Server(
                serverInfo = Implementation("mission-visualization", "1.0.0"),
                options = ServerOptions(
                    capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
                ),
                instructions = """
                    Mission Visualization MCP is read-only and its layouts root is restricted to ${allowedRoot.toAbsolutePath()}.
                    The layouts root is not necessarily the AI agent's project root. Install all
                    project-scoped skills at the agent project root, not automatically at the layouts root.
                    During project setup call get_mcp_skill, install it with the client's native
                    project-skill mechanism, then call get_slm_skills with skill=all and finally
                    validate_project_setup. For layout work edit *.layout.md with normal file tools,
                    call check_layout until valid, call render_layout, inspect the PNG, and iterate.
                """.trimIndent(),
            )
            server.addTool(
                name = "get_mcp_skill",
                description = "Get the canonical root skill that documents every Mission Visualization MCP tool and workflow.",
                inputSchema = emptyToolSchema(),
                toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
            ) {
                val markdown = getMissionVisualizationMcpSkill()
                CallToolResult(
                    content = listOf(
                        TextContent(
                            """
                            skill_name: $MissionVisualizationMcpSkillName
                            skill_version: $MissionVisualizationMcpSkillVersion
                            sha256: ${sha256(markdown)}
                            install_hint: Install the Markdown below at the AI agent's actual project root using this client's native project-scoped skill mechanism. The MCP allowed layouts root may only be a subfolder; do not infer skill scope from it.

                            --- BEGIN SKILL.md ---
                            $markdown--- END SKILL.md ---
                            """.trimIndent(),
                        ),
                    ),
                )
            }
            server.addTool(
                name = "get_slm_skills",
                description = "Get canonical, self-contained instructions for authoring SLM and its specialist subsystems.",
                inputSchema = skillToolSchema(),
                toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
            ) { request ->
                try {
                    val args = request.arguments ?: buildJsonObject {}
                    val selector = args["skill"]?.jsonPrimitive?.contentOrNull ?: "all"
                    val bundle = getSlmSkillBundle(selector)
                    CallToolResult(
                        content = listOf(
                            TextContent(
                                """
                                skill_set: ${bundle.selector}
                                included_skills: ${bundle.includedSkills.joinToString(", ")}
                                file_count: ${bundle.files.size}
                                install_hint: Install every returned SKILL.md at the AI agent's actual project root using this client's native project-scoped skill mechanism. The MCP allowed layouts root may only be a subfolder. Preserve unrelated skills and instructions.
                                """.trimIndent(),
                            ),
                        ) + bundle.files.map { file ->
                            TextContent(
                                """
                                skill_name: ${file.name}
                                sha256: ${sha256(file.markdown)}

                                --- BEGIN ${file.name}/SKILL.md ---
                                ${file.markdown}--- END ${file.name}/SKILL.md ---
                                """.trimIndent(),
                            )
                        },
                    )
                } catch (failure: Exception) {
                    CallToolResult(
                        content = listOf(TextContent(failure.message ?: "get_slm_skills failed")),
                        isError = true,
                    )
                }
            }
            server.addTool(
                name = "validate_project_setup",
                description = "Verify MCP connectivity, the separate agent project and layouts roots, and project-scoped skill setup.",
                inputSchema = validationToolSchema(),
                toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
            ) { request ->
                try {
                    val verification = validateProjectSetup(request.arguments ?: buildJsonObject {}, allowedRoot)
                    onProjectVerification(verification)
                    CallToolResult(
                        content = listOf(
                            TextContent(
                                """
                                verified: ${verification.verified}
                                agent_name: ${verification.agentName}
                                agent_project_path: ${verification.agentProjectPath}
                                layouts_path: ${verification.layoutsPath}
                                allowed_layouts_root: ${allowedRoot.toRealPath()}
                                message: ${verification.message}
                                """.trimIndent(),
                            ),
                        ),
                    )
                } catch (failure: Exception) {
                    CallToolResult(
                        content = listOf(TextContent(failure.message ?: "validate_project_setup failed")),
                        isError = true,
                    )
                }
            }
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

        private fun emptyToolSchema(): ToolSchema = ToolSchema(
            properties = buildJsonObject {},
            required = emptyList(),
        )

        private fun skillToolSchema(): ToolSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("skill") {
                    put("type", "string")
                    put("description", "Return all skills, base SLM only, or one SLM specialist together with the base skill.")
                    put("enum", buildJsonArray {
                        listOf("all", "slm", "diagrams", "vector_graphics", "typography", "annotations", "editor")
                            .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                    put("default", "all")
                }
            },
            required = emptyList(),
        )

        private fun checkToolSchema(): ToolSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("layout_path") {
                    put("type", "string")
                    put("description", "Absolute path inside the allowed folder, or a path relative to it.")
                }
            },
            required = listOf("layout_path"),
        )

        private fun validationToolSchema(): ToolSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("agent_project_path") {
                    put("type", "string")
                    put("description", "Absolute root of the project opened by the AI coding client, where project-scoped skills were installed.")
                }
                putJsonObject("layouts_path") {
                    put("type", "string")
                    put("description", "Absolute folder containing layouts. It may be a project subfolder and must match the MCP allowed layouts root.")
                }
                putJsonObject("agent_name") {
                    put("type", "string")
                    put("description", "Name of the connected AI coding client or agent.")
                }
                putJsonObject("root_skill_installed") {
                    put("type", "boolean")
                    put("description", "True after the canonical mission-visualization-mcp skill was installed project-scoped at agent_project_path.")
                }
                putJsonObject("slm_skills_installed") {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                    put("description", "Installed canonical SLM skill names returned by get_slm_skills.")
                }
            },
            required = listOf("agent_project_path", "layouts_path", "agent_name", "root_skill_installed", "slm_skills_installed"),
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

        private fun validateProjectSetup(
            args: kotlinx.serialization.json.JsonObject,
            allowedRoot: Path,
        ): McpProjectVerification {
            val agentProjectPath = args["agent_project_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val layoutsPath = args["layouts_path"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val agentName = args["agent_name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "unknown" }
            val rootSkillInstalled = args["root_skill_installed"]?.jsonPrimitive?.booleanOrNull == true
            val installedSkills = args["slm_skills_installed"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.toSet()
                .orEmpty()
            val requiredSkills = setOf(
                "slm",
                "slm-diagrams",
                "slm-vector-graphics",
                "slm-typography",
                "slm-annotations",
                "slm-editor",
            )
            val canonicalAllowedRoot = allowedRoot.toRealPath()
            val canonicalAgentProjectRoot = agentProjectPath.takeIf(String::isNotBlank)?.let { raw ->
                runCatching { Path.of(raw).toRealPath() }.getOrNull()
            }
            val canonicalLayoutsRoot = layoutsPath.takeIf(String::isNotBlank)?.let { raw ->
                runCatching { Path.of(raw).toRealPath() }.getOrNull()
            }
            val agentProjectExists = canonicalAgentProjectRoot?.let(java.nio.file.Files::isDirectory) == true
            val layoutsRootMatches = canonicalLayoutsRoot == canonicalAllowedRoot
            val missingSkills = requiredSkills - installedSkills
            val verified = agentProjectExists && layoutsRootMatches && rootSkillInstalled && missingSkills.isEmpty()
            val message = when {
                !agentProjectExists -> "The AI agent project root is missing or is not an accessible directory."
                !layoutsRootMatches -> "The layouts path does not match the MCP allowed layouts root."
                !rootSkillInstalled -> "The canonical root skill was not reported as installed."
                missingSkills.isNotEmpty() -> "Missing SLM skills: ${missingSkills.sorted().joinToString(", ")}"
                else -> "Mission Visualization MCP and project skills are ready."
            }
            return McpProjectVerification(
                verified = verified,
                agentName = agentName,
                agentProjectPath = canonicalAgentProjectRoot?.toString() ?: agentProjectPath,
                layoutsPath = canonicalLayoutsRoot?.toString() ?: layoutsPath,
                message = message,
            )
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

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
