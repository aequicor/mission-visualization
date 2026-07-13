package io.aequicor.visualization.editor.platform

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ProjectResourceStoreJvmTest {
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
}
