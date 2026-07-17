package io.aequicor.visualization.editor.platform

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ProjectResourceStoreJvmTest {
    // Resetting first redirects the folder-sync settings dir away from ~/.mission-visualization.
    @BeforeTest
    fun setUp() = platformResetFolderSyncForTest()

    @AfterTest
    fun tearDown() = platformResetFolderSyncForTest()

    @Test
    fun writesResourcesIntoActiveProjectAndSharesStoreAcrossPanes() = runBlocking {
        val root = createTempDirectory("desktop-resource-store")
        root.resolve("screen.layout.md").writeText("# Screen\n")
        platformConnectFolderForTest(root)
        val canvasStore = createProjectResourceStore()
        val resourcesPaneStore = createProjectResourceStore()
        val bytes = byteArrayOf(1, 3, 5, 7)

        canvasStore.put("res/icons/mark.svg", bytes)

        assertSame(canvasStore, resourcesPaneStore)
        assertContentEquals(bytes, Files.readAllBytes(root.resolve("res/icons/mark.svg")))
        assertContentEquals(bytes, resourcesPaneStore.read("res/icons/mark.svg"))
        assertEquals(listOf("res/icons/mark.svg"), resourcesPaneStore.list())
    }

    @Test
    fun rejectsResourcePathsThatEscapeProjectFolder() {
        runBlocking {
            val root = createTempDirectory("desktop-resource-store-safe")
            root.resolve("screen.layout.md").writeText("# Screen\n")
            platformConnectFolderForTest(root)

            assertFailsWith<IllegalArgumentException> {
                createProjectResourceStore().put("res/../outside.png", byteArrayOf(1))
            }
        }
    }

    @Test
    fun migratesResourcesDroppedBeforeConnectOntoDiskOnConnect() = runBlocking {
        // No folder connected yet: the store buffers the drop in memory only.
        platformResetFolderSyncForTest()
        val store = createProjectResourceStore()
        store.replaceAll(emptyList()) // clear any pre-connect buffer from earlier tests
        val bytes = byteArrayOf(9, 8, 7, 6)
        store.put("res/icons/pre.svg", bytes)

        // Now a folder is connected. The buffered resource must be migrated to disk and readable.
        val root = createTempDirectory("desktop-resource-store-migrate")
        root.resolve("screen.layout.md").writeText("# Screen\n")
        platformConnectFolderForTest(root)

        assertContentEquals(bytes, store.read("res/icons/pre.svg"))
        assertEquals(listOf("res/icons/pre.svg"), store.list())
        assertContentEquals(bytes, Files.readAllBytes(root.resolve("res/icons/pre.svg")))
    }

    @Test
    fun rejectsResourceDirectorySymlinkThatEscapesProjectFolder() = runBlocking {
        platformResetFolderSyncForTest()
        val store = createProjectResourceStore()
        store.replaceAll(emptyList())

        val root = createTempDirectory("desktop-resource-store-symlink")
        root.resolve("screen.layout.md").writeText("# Screen\n")
        val outside = createTempDirectory("desktop-resource-store-outside")
        try {
            Files.createSymbolicLink(root.resolve("res"), outside)
        } catch (_: Exception) {
            // Filesystem/OS without symlink support (e.g. Windows without privilege) — skip.
            return@runBlocking
        }
        platformConnectFolderForTest(root)

        assertFailsWith<IllegalArgumentException> {
            store.put("res/pwned.png", byteArrayOf(1))
        }
        assertFalse(Files.exists(outside.resolve("pwned.png")))
    }
}
