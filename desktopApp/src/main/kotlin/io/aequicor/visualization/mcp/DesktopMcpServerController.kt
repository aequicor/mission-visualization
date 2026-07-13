package io.aequicor.visualization.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aequicor.visualization.editor.data.KeyValueStore
import io.aequicor.visualization.editor.data.createKeyValueStore
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
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

    override val available: Boolean = true
    override var status: McpServerStatus by mutableStateOf(McpServerStatus.Stopped)
        private set
    override var port: String by mutableStateOf(store.getString(PortKey)?.toIntOrNull()?.toString() ?: DefaultPort.toString())
        private set
    override var allowedFolder: String by mutableStateOf(store.getString(RootKey).orEmpty())
        private set
    override var errorMessage: String? by mutableStateOf(null)
        private set

    override val endpoint: String
        get() = "http://127.0.0.1:${port.toIntOrNull() ?: DefaultPort}/mcp"

    override val prompt: String
        get() = buildPrompt(endpoint, allowedFolder)

    override fun updatePort(value: String) {
        if (status == McpServerStatus.Starting || status == McpServerStatus.Running) return
        port = value
        value.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { store.putString(PortKey, it.toString()) }
        errorMessage = null
        if (status == McpServerStatus.Error) status = McpServerStatus.Stopped
    }

    override fun chooseAllowedFolder() {
        if (status == McpServerStatus.Starting || status == McpServerStatus.Running) return
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose the folder available to MCP"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            allowedFolder.takeIf(String::isNotBlank)?.let { currentDirectory = Path.of(it).toFile() }
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            allowedFolder = runCatching { chooser.selectedFile.toPath().toRealPath().toString() }
                .getOrDefault(chooser.selectedFile.absolutePath)
            store.putString(RootKey, allowedFolder)
            errorMessage = null
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
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val root = Path.of(allowedFolder).toRealPath()
                    require(Files.isDirectory(root)) { "Allowed folder is not accessible: $root" }
                    val started = MissionMcpServerHost.start(parsedPort, root)
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
            return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { running.stop() } }
                .onFailure { errorMessage = it.message }
            status = McpServerStatus.Stopped
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

    private fun buildPrompt(endpoint: String, root: String): String = """
        Connect the Mission Visualization MCP server named `mission_visualization`.

        Add this project-scoped configuration to `.codex/config.toml`:

        ```toml
        [mcp_servers.mission_visualization]
        url = "$endpoint"
        ```

        Restart Codex after adding the MCP server.

        Allowed folder: $root

        Workflow:
        1. Edit a `*.layout.md` file inside the allowed folder with normal file tools.
        2. Call `check_layout` with `layout_path` and fix compiler diagnostics until `valid: true`.
        3. Call `render_layout` with `layout_path` and a target: `screen`, `component`, or `group`.
        4. Analyze both the returned PNG and diagnostics, then iterate on the source file.

        The MCP server is read-only and never edits `.layout.md` itself.
    """.trimIndent()

    private companion object {
        const val DefaultPort = 7331
        const val PortKey = "mcp-server-port"
        const val RootKey = "mcp-server-root"
    }
}
