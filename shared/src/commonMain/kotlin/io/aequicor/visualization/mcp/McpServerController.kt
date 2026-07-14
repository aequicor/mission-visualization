package io.aequicor.visualization.mcp

import androidx.compose.runtime.Stable

enum class McpServerStatus { Stopped, Starting, Running, Error }

data class McpProjectVerification(
    val verified: Boolean,
    val agentName: String,
    val agentProjectPath: String,
    val layoutsPath: String,
    val message: String,
)

/** Platform boundary used by the common editor UI. Only the desktop app supplies a real server. */
@Stable
interface McpServerController {
    val available: Boolean
    val status: McpServerStatus
    val port: String
    val allowedFolder: String
    val endpoint: String
    val connectionPrompt: String
    val setupPrompt: String
    val projectVerification: McpProjectVerification?
    val errorMessage: String?

    fun useProjectFolder(path: String?)
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
    override val connectionPrompt: String = ""
    override val setupPrompt: String = ""
    override val projectVerification: McpProjectVerification? = null
    override val errorMessage: String? = null
    override fun useProjectFolder(path: String?) = Unit
    override fun updatePort(value: String) = Unit
    override fun chooseAllowedFolder() = Unit
    override fun start() = Unit
    override fun stop() = Unit
}
