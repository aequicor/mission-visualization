package io.aequicor.visualization.mcp

import io.aequicor.visualization.editor.data.KeyValueStore
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
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
                assertTrue(controller.prompt.contains("[mcp_servers.mission_visualization]"))

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
