package io.aequicor.visualization.mcp

import androidx.compose.runtime.Stable

enum class McpServerStatus { Stopped, Starting, Running, Error }

/** Platform boundary used by the common editor UI. Only the desktop app supplies a real server. */
@Stable
interface McpServerController {
    val available: Boolean
    val status: McpServerStatus
    val port: String
    val allowedFolder: String
    val endpoint: String
    val prompt: String
    val errorMessage: String?

    fun updatePort(value: String)
    fun chooseAllowedFolder()
    fun start()
    fun stop()
}

object NoMcpServerController : McpServerController {
    override val available: Boolean = false
    override val status: McpServerStatus = McpServerStatus.Stopped
    override val port: String = "7331"
    override val allowedFolder: String = ""
    override val endpoint: String = ""
    override val prompt: String = ""
    override val errorMessage: String? = null
    override fun updatePort(value: String) = Unit
    override fun chooseAllowedFolder() = Unit
    override fun start() = Unit
    override fun stop() = Unit
}
