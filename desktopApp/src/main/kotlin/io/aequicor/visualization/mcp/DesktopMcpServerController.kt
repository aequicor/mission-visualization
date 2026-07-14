package io.aequicor.visualization.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aequicor.visualization.editor.data.KeyValueStore
import io.aequicor.visualization.editor.data.createKeyValueStore
import io.aequicor.visualization.editor.platform.chooseNativeFolder
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.swing.Swing

class DesktopMcpServerController(
    private val store: KeyValueStore = createKeyValueStore(),
) : McpServerController, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val lifecycleLock = Any()
    private var host: MissionMcpServerHost? = null
    private var closed = false
    private var projectFolder: String? = null
    private var manualFolderOverride = false

    override val available: Boolean = true
    override var status: McpServerStatus by mutableStateOf(McpServerStatus.Stopped)
        private set
    override var port: String by mutableStateOf(store.getString(PortKey)?.toIntOrNull()?.toString() ?: DefaultPort.toString())
        private set
    override var allowedFolder: String by mutableStateOf(store.getString(RootKey).orEmpty())
        private set
    override var errorMessage: String? by mutableStateOf(null)
        private set
    override var projectVerification: McpProjectVerification? by mutableStateOf(null)
        private set

    override val endpoint: String
        get() = "http://127.0.0.1:${port.toIntOrNull() ?: DefaultPort}/mcp"

    override val connectionPrompt: String
        get() = buildConnectionPrompt(endpoint, allowedFolder)

    override val setupPrompt: String
        get() = buildSetupPrompt(allowedFolder)

    override val updatePrompt: String
        get() = buildUpdatePrompt(allowedFolder)

    override fun useProjectFolder(path: String?) {
        val normalized = path?.takeIf(String::isNotBlank)?.let { raw ->
            runCatching { Path.of(raw).toRealPath().toString() }
                .getOrDefault(Path.of(raw).toAbsolutePath().normalize().toString())
        } ?: return
        projectFolder = normalized
        if (status == McpServerStatus.Starting || status == McpServerStatus.Running) return
        if (!manualFolderOverride || allowedFolder.isBlank()) {
            allowedFolder = normalized
            store.putString(RootKey, normalized)
            projectVerification = null
        }
    }

    override fun updatePort(value: String) {
        if (status == McpServerStatus.Starting || status == McpServerStatus.Running) return
        port = value
        value.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { store.putString(PortKey, it.toString()) }
        errorMessage = null
        projectVerification = null
        if (status == McpServerStatus.Error) status = McpServerStatus.Stopped
    }

    override fun chooseAllowedFolder() {
        if (status == McpServerStatus.Starting || status == McpServerStatus.Running) return
        val selectedFolder = chooseNativeFolder(
            title = "Choose the folder available to MCP",
            initialDirectory = allowedFolder.takeIf(String::isNotBlank)?.let(Path::of),
        )
        if (selectedFolder != null) {
            allowedFolder = runCatching { selectedFolder.toRealPath().toString() }
                .getOrDefault(selectedFolder.toAbsolutePath().normalize().toString())
            manualFolderOverride = allowedFolder != projectFolder
            store.putString(RootKey, allowedFolder)
            errorMessage = null
            projectVerification = null
            if (status == McpServerStatus.Error) status = McpServerStatus.Stopped
        }
    }

    override fun start() {
        if (status == McpServerStatus.Starting || status == McpServerStatus.Running) return
        val parsedPort = port.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1024..65535) {
            fail("Port must be between 1024 and 65535")
            return
        }
        if (allowedFolder.isBlank()) {
            fail("Choose an allowed folder before starting the server")
            return
        }
        status = McpServerStatus.Starting
        errorMessage = null
        projectVerification = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val root = Path.of(allowedFolder).toRealPath()
                    require(Files.isDirectory(root)) { "Allowed folder is not accessible: $root" }
                    val started = MissionMcpServerHost.start(
                        port = parsedPort,
                        allowedRoot = root,
                        onProjectVerification = { verification ->
                            scope.launch {
                                if (status == McpServerStatus.Running) projectVerification = verification
                            }
                        },
                    )
                    val stopImmediately = synchronized(lifecycleLock) {
                        if (closed) true else {
                            host = started
                            false
                        }
                    }
                    if (stopImmediately) started.stop()
                    started
                }
            }.onSuccess { startedHost ->
                if (synchronized(lifecycleLock) { closed || host !== startedHost }) return@onSuccess
                store.putString(PortKey, parsedPort.toString())
                store.putString(RootKey, allowedFolder)
                status = McpServerStatus.Running
            }.onFailure { failure ->
                if (!synchronized(lifecycleLock) { closed }) fail(friendlyStartError(failure))
            }
        }
    }

    override fun stop() {
        val running = synchronized(lifecycleLock) { host.also { host = null } } ?: run {
            status = McpServerStatus.Stopped
            projectVerification = null
            return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { running.stop() } }
                .onFailure { errorMessage = it.message }
            status = McpServerStatus.Stopped
            projectVerification = null
        }
    }

    override fun close() {
        val running = synchronized(lifecycleLock) {
            closed = true
            host.also { host = null }
        }
        runCatching { running?.stop() }
        scope.cancel()
        status = McpServerStatus.Stopped
        projectVerification = null
    }

    private fun fail(message: String) {
        errorMessage = message
        status = McpServerStatus.Error
    }

    private fun friendlyStartError(failure: Throwable): String {
        val message = generateSequence(failure) { it.cause }.mapNotNull { it.message }.firstOrNull().orEmpty()
        return when {
            message.contains("Address already in use", ignoreCase = true) ||
                message.contains("bind", ignoreCase = true) -> "Port $port is already in use"
            message.isNotBlank() -> message
            else -> "MCP server failed to start"
        }
    }

    private fun buildConnectionPrompt(endpoint: String, root: String): String = """
        Connect Mission Visualization MCP to the project you are currently working in.

        MCP server name: `mission_visualization`
        Transport: Streamable HTTP
        Endpoint: `$endpoint`
        Layouts folder exposed by MCP: `$root`

        Important: the layouts folder is a content root and is not necessarily the root of the
        project opened by your AI coding client. Determine the agent project root from the current
        workspace or repository. Store project-scoped MCP configuration at that agent project root.
        Do not create client configuration or skills inside the layouts folder unless it is also the
        actual agent project root.

        Use the native project-scoped MCP configuration mechanism supported by your current AI
        coding client. Preserve every unrelated existing MCP server, setting, instruction, and skill.

        Add or update only the `mission_visualization` connection using the endpoint above.
        Do not install skills and do not call MCP tools in this step. After saving the configuration,
        tell me exactly what must be reloaded or restarted for the connection to become available.
        If your client applies project MCP changes immediately, tell me that I can paste Prompt 2 now.
    """.trimIndent()

    private fun buildSetupPrompt(root: String): String = """
        Finish Mission Visualization setup in the project you are currently working in.

        Layouts folder exposed by MCP: `$root`

        The project-scoped `mission_visualization` MCP connection was configured in the previous
        step. Do not edit MCP configuration in this step.

        Determine the root of the project currently opened by your AI coding client from its
        workspace or repository context. Install all project-scoped skills at that agent project
        root. The layouts folder above is only the MCP content root; do not use it as the skill scope
        unless both roots genuinely coincide.

        - Confirm that the `mission_visualization` MCP tools are available. If they are not, stop and
          report that the connection is not active yet.
        - Call `get_mcp_skill` and install the returned canonical root skill project-scoped using your
          client's native skill mechanism.
        - Call `get_slm_skills` with `skill: "all"` and install every returned SLM and subsystem skill
          project-scoped. Preserve every unrelated project instruction and skill.
        - Call `validate_project_setup` with:
          - `agent_project_path`: the actual root of the project opened by the AI coding client
          - `layouts_path`: `$root`
          - `agent_name`: the name of your current AI coding client
          - `root_skill_installed`: `true` only after installation succeeds
          - `slm_skills_installed`: every skill name returned by `get_slm_skills`

        Report setup complete only when `validate_project_setup` returns `verified: true`.
        If its allowed layouts root differs from `layouts_path`, stop and report the mismatch.
    """.trimIndent()

    private fun buildUpdatePrompt(root: String): String = """
        Refresh Mission Visualization skills in the project you are currently working in.

        Layouts folder exposed by MCP: `$root`

        The project-scoped `mission_visualization` MCP connection and skills were installed earlier.
        Do not edit MCP configuration in this step and do not touch unrelated skills.

        Determine the root of the project currently opened by your AI coding client. Installed skills
        live there, project-scoped. Never write skills into the layouts folder above unless both
        roots genuinely coincide.

        - Confirm that the `mission_visualization` MCP tools are available. If they are not, stop and
          report that the connection is not active yet.
        - Call `get_mcp_skill`. Compare its `skill_version` and `sha256` with the installed
          `mission-visualization-mcp` skill. If it is missing or differs, overwrite the installed copy
          with the returned Markdown.
        - Call `get_slm_skills` with `skill: "all"`. For every returned skill file, compare its
          `sha256` with the installed copy and overwrite each one that is missing or differs.
        - Change only the Mission Visualization skills. Preserve every unrelated project skill,
          instruction, and MCP server.
        - Call `validate_project_setup` with:
          - `agent_project_path`: the actual root of the project opened by the AI coding client
          - `layouts_path`: `$root`
          - `agent_name`: the name of your current AI coding client
          - `root_skill_installed`: `true`
          - `slm_skills_installed`: every skill name returned by `get_slm_skills`

        Report which skills were updated, which were already current, and the final
        `validate_project_setup` result. Report done only when it returns `verified: true`.
    """.trimIndent()

    private companion object {
        const val DefaultPort = 7331
        const val PortKey = "mcp-server-port"
        const val RootKey = "mcp-server-root"
    }
}
