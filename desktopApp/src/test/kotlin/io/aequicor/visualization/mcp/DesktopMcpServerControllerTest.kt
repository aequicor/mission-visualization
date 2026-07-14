package io.aequicor.visualization.mcp

import io.aequicor.visualization.editor.data.KeyValueStore
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DesktopMcpServerControllerTest {
    @Test
    fun startsStopsAndReleasesPort() {
        runBlocking {
            val root = createTempDirectory("mcp-controller")
            val port = ServerSocket(0).use { it.localPort }
            val controller = controller(root.toString(), port)
            try {
                controller.start()
                awaitStatus(controller, McpServerStatus.Running)
                assertEquals("http://127.0.0.1:$port/mcp", controller.endpoint)
                assertTrue(controller.connectionPrompt.contains("native project-scoped MCP configuration"))
                assertTrue(controller.connectionPrompt.contains(controller.endpoint))
                assertTrue(controller.connectionPrompt.contains("Do not install skills"))
                assertTrue(controller.connectionPrompt.contains("not necessarily the root"))
                assertFalse(controller.connectionPrompt.contains("get_mcp_skill"))
                assertTrue(controller.setupPrompt.contains("get_mcp_skill"))
                assertTrue(controller.setupPrompt.contains("get_slm_skills"))
                assertTrue(controller.setupPrompt.contains("validate_project_setup"))
                assertTrue(controller.setupPrompt.contains("agent_project_path"))
                assertTrue(controller.setupPrompt.contains("layouts_path"))
                assertFalse(controller.setupPrompt.contains(controller.endpoint))
                assertTrue(controller.setupPrompt.contains(root.toString()))
                assertTrue(controller.updatePrompt.contains("get_mcp_skill"))
                assertTrue(controller.updatePrompt.contains("get_slm_skills"))
                assertTrue(controller.updatePrompt.contains("validate_project_setup"))
                assertFalse(controller.updatePrompt.contains(controller.endpoint))
                assertTrue(controller.updatePrompt.contains(root.toString()))

                controller.stop()
                awaitStatus(controller, McpServerStatus.Stopped)
                ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
                    assertEquals(port, socket.localPort)
                }

                controller.start()
                awaitStatus(controller, McpServerStatus.Running)
                controller.stop()
                awaitStatus(controller, McpServerStatus.Stopped)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun activeProjectFolderBecomesTheDefaultAllowedRoot() {
        val previous = createTempDirectory("mcp-previous")
        val project = createTempDirectory("mcp-active-project")
        val controller = controller(previous.toString(), ServerSocket(0).use { it.localPort })
        try {
            controller.useProjectFolder(project.toString())
            assertEquals(project.toRealPath().toString(), controller.allowedFolder)
            assertTrue(controller.connectionPrompt.contains(project.toRealPath().toString()))
            assertTrue(controller.setupPrompt.contains(project.toRealPath().toString()))
            assertTrue(controller.updatePrompt.contains(project.toRealPath().toString()))
        } finally {
            controller.close()
        }
    }

    @Test
    fun reportsOccupiedPort() {
        runBlocking {
            val root = createTempDirectory("mcp-controller-busy")
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { occupied ->
                val controller = controller(root.toString(), occupied.localPort)
                try {
                    controller.start()
                    awaitStatus(controller, McpServerStatus.Error)
                    assertNotNull(controller.errorMessage)
                } finally {
                    controller.close()
                }
            }
        }
    }

    @Test
    fun rejectsInvalidPortAndRoot() {
        runBlocking {
            val invalidPort = controller(createTempDirectory("mcp-invalid-port").toString(), 70_000)
            invalidPort.start()
            assertEquals(McpServerStatus.Error, invalidPort.status)
            invalidPort.close()

            val missingRoot = controller("Z:\\missing-mcp-root-${System.nanoTime()}", ServerSocket(0).use { it.localPort })
            try {
                missingRoot.start()
                awaitStatus(missingRoot, McpServerStatus.Error)
                assertNotNull(missingRoot.errorMessage)
            } finally {
                missingRoot.close()
            }
        }
    }

    private fun controller(root: String, port: Int): DesktopMcpServerController =
        DesktopMcpServerController(
            MemoryStore(
                mutableMapOf(
                    "mcp-server-root" to root,
                    "mcp-server-port" to port.toString(),
                ),
            ),
        )

    private suspend fun awaitStatus(controller: DesktopMcpServerController, expected: McpServerStatus) {
        withTimeout(8_000) {
            while (controller.status != expected) delay(25)
        }
    }

    private class MemoryStore(private val values: MutableMap<String, String>) : KeyValueStore {
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
