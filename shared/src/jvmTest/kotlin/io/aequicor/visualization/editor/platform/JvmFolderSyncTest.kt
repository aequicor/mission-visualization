package io.aequicor.visualization.editor.platform

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmFolderSyncTest {
    @AfterTest
    fun tearDown() = platformResetFolderSyncForTest()

    @Test
    fun watchesExternalChangesAndSuppressesOwnAtomicWriteEcho() {
        val root = createTempDirectory("folder-sync-test")
        val file = root.resolve("screen.layout.md")
        file.writeText("# Initial\n")
        platformConnectFolderForTest(root)

        assertEquals("watching", folderSyncStatus())
        assertNotNull(folderSyncSnapshotJson())
        val initialRevision = folderSyncRevision()

        file.writeText("# External\n")
        waitUntil { folderSyncRevision() > initialRevision }
        assertTrue(folderSyncSnapshotJson().orEmpty().contains("# External"))

        val beforeOwnWrite = folderSyncRevision()
        platformWriteFolderFile("screen.layout.md", "# Editor\n")
        waitUntil { Files.readString(file) == "# Editor\n" }
        Thread.sleep(350)
        assertEquals(beforeOwnWrite, folderSyncRevision())
    }

    private fun waitUntil(timeoutMillis: Long = 4_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) error("Timed out waiting for folder sync")
            Thread.sleep(25)
        }
    }
}
