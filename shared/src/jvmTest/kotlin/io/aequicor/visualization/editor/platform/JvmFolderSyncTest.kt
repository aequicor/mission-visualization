package io.aequicor.visualization.editor.platform

import io.aequicor.visualization.editor.data.encodeProjectSourcesJson
import io.aequicor.visualization.editor.data.decodeProjectSnapshot
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
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

    @Test
    fun commitsMultiFileUpdateAndDeletionAsOneCheckedBatch() {
        val root = createTempDirectory("folder-batch")
        val first = root.resolve("first.layout.md")
        val second = root.resolve("second.layout.md")
        first.writeText("# First\n")
        second.writeText("# Second\n")
        platformConnectFolderForTest(root)

        assertTrue(
            platformWriteFolderFiles(
                listOf(
                    FolderFileWrite("first.layout.md", "# First\n", "# Updated\n"),
                    FolderFileWrite("second.layout.md", "# Second\n", null),
                    FolderFileWrite("third.layout.md", null, "# Third\n"),
                ),
            ),
        )

        assertEquals("# Updated\n", Files.readString(first))
        assertFalse(Files.exists(second))
        assertEquals("# Third\n", Files.readString(root.resolve("third.layout.md")))
    }

    @Test
    fun staleBaseRejectsWholeBatchBeforeAnyFileChanges() {
        val root = createTempDirectory("folder-batch-conflict")
        val first = root.resolve("first.layout.md")
        val second = root.resolve("second.layout.md")
        first.writeText("# External\n")
        second.writeText("# Second\n")
        platformConnectFolderForTest(root)

        assertFalse(
            platformWriteFolderFiles(
                listOf(
                    FolderFileWrite("first.layout.md", "# Stale\n", "# Editor one\n"),
                    FolderFileWrite("second.layout.md", "# Second\n", "# Editor two\n"),
                ),
            ),
        )

        assertEquals("# External\n", Files.readString(first))
        assertEquals("# Second\n", Files.readString(second))
        assertTrue(folderSyncError().orEmpty().startsWith("folder-write-conflict:"))
        assertEquals("watching", folderSyncStatus())
        assertTrue(folderSyncSnapshotJson().orEmpty().contains("# External"))
    }

    @Test
    fun manualRefreshImmediatelyPullsAllScreensFromDisk() {
        val root = createTempDirectory("folder-manual-refresh")
        val first = root.resolve("first.layout.md")
        first.writeText("# First\n")
        platformConnectFolderForTest(root)

        first.writeText("# Updated\n")
        root.resolve("second.layout.md").writeText("# Second\n")
        platformRefreshFolder()

        val snapshot = folderSyncSnapshotJson().orEmpty()
        assertTrue(snapshot.contains("# Updated"))
        assertTrue(snapshot.contains("# Second"))
    }

    @Test
    fun desktopUsesComposeLandingAndDiskOnlyProjects() {
        assertEquals(ProjectLandingMode.Compose, platformProjectLandingMode)
        assertEquals(ProjectStorageMode.DiskOnly, platformProjectStorageMode)
    }

    @Test
    fun opensAnyRecentFolderByCanonicalPath() {
        val first = createTempDirectory("folder-recent-first")
        val second = createTempDirectory("folder-recent-second")
        first.resolve("first.layout.md").writeText("# First\n")
        second.resolve("second.layout.md").writeText("# Second\n")

        platformConnectFolderByIdForTest(first.toString())
        assertEquals(first.toRealPath().toString(), platformActiveFolderId())
        platformConnectFolderByIdForTest(second.toString())

        assertEquals("watching", folderSyncStatus())
        assertEquals(second.toRealPath().toString(), platformActiveFolderId())
        assertTrue(folderSyncSnapshotJson().orEmpty().contains("# Second"))
    }

    @Test
    fun oldWatcherCannotPublishAfterRapidProjectSwitch() {
        val first = createTempDirectory("folder-switch-first")
        val second = createTempDirectory("folder-switch-second")
        val firstFile = first.resolve("screen.layout.md")
        firstFile.writeText("# First\n")
        second.resolve("screen.layout.md").writeText("# Second\n")

        platformConnectFolderForTest(first)
        // Queue an event in the first watcher's settle window, then switch immediately.
        firstFile.writeText("# Stale first\n")
        platformConnectFolderForTest(second)

        Thread.sleep(300)
        assertEquals(second.toRealPath().toString(), platformActiveFolderId())
        assertTrue(folderSyncSnapshotJson().orEmpty().contains("# Second"))
        assertFalse(folderSyncSnapshotJson().orEmpty().contains("# Stale first"))
    }

    @Test
    fun fullEditorStateSurvivesDisconnectAndReconnect() {
        val root = createTempDirectory("folder-editor-state")
        root.resolve("screen.layout.md").writeText("# Screen\n")
        platformConnectFolderForTest(root)

        platformWriteFolderEditorState("{\"saved\":true}")
        platformResetFolderSyncForTest()
        platformConnectFolderForTest(root)

        val snapshot = assertNotNull(folderSyncSnapshotJson()?.let(::decodeProjectSnapshot))
        assertEquals("{\"saved\":true}", snapshot.editorStateJson)
    }

    @Test
    fun createsWelcomeCopyWithoutOverwritingExistingProject() {
        val target = createTempDirectory("folder-create")
        val sources = listOf(
            MissionDocumentSource("welcome.layout.md", "# Welcome\n"),
            MissionDocumentSource("nested/vectors.layout.md", "# Vectors\n"),
        )
        platformCreateFolderForTest(target, encodeProjectSourcesJson("Welcome", sources))

        assertEquals("watching", folderSyncStatus())
        assertEquals("# Welcome\n", Files.readString(target.resolve("welcome.layout.md")))
        assertEquals("# Vectors\n", Files.readString(target.resolve("nested/vectors.layout.md")))

        platformResetFolderSyncForTest()
        val existing = createTempDirectory("folder-existing")
        existing.resolve("existing.layout.md").writeText("# Existing\n")
        platformCreateFolderForTest(existing, encodeProjectSourcesJson("Welcome", sources))

        assertEquals("error", folderSyncStatus())
        assertEquals("folder-contains-project", folderSyncError())
        assertFalse(Files.exists(existing.resolve("welcome.layout.md")))
        assertEquals("# Existing\n", Files.readString(existing.resolve("existing.layout.md")))
    }

    @Test
    fun rejectsEscapingProjectFilePath() {
        val target = createTempDirectory("folder-safe")
        val escaped = target.parent.resolve("escaped.layout.md")
        Files.deleteIfExists(escaped)
        val sources = listOf(MissionDocumentSource("../escaped.layout.md", "# Escape\n"))

        platformCreateFolderForTest(target, encodeProjectSourcesJson("Unsafe", sources))

        assertEquals("error", folderSyncStatus())
        assertEquals("project-create-failed", folderSyncError())
        assertFalse(Files.exists(escaped))
    }

    @Test
    fun unavailableRecentPathReportsErrorWithoutOpeningAnotherProject() {
        val missing = createTempDirectory("folder-missing").resolve("gone")
        platformConnectFolderByIdForTest(missing.toString())

        assertEquals("error", folderSyncStatus())
        assertEquals("project-unavailable", folderSyncError())
        assertEquals(null, platformActiveFolderId())
    }

    private fun waitUntil(timeoutMillis: Long = 4_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) error("Timed out waiting for folder sync")
            Thread.sleep(25)
        }
    }
}
