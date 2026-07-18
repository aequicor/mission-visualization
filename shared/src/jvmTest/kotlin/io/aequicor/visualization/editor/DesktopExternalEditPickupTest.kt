package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.decodeProjectSnapshot
import io.aequicor.visualization.editor.platform.folderSyncRevision
import io.aequicor.visualization.editor.platform.folderSyncSnapshotJson
import io.aequicor.visualization.editor.platform.platformConnectFolderForTest
import io.aequicor.visualization.editor.platform.platformResetFolderSyncForTest
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * External edits (an agent writing `*.layout.md` behind the editor's back) must reach the
 * snapshot within the watch loop's rescan bound. The platform WatchService alone is not
 * enough: macOS ships a polling implementation with a ten-second default period, which is
 * exactly the "canvas did not follow the file for seconds" failure this guards against.
 */
class DesktopExternalEditPickupTest {
    @BeforeTest
    fun setUp() = platformResetFolderSyncForTest()

    @AfterTest
    fun tearDown() = platformResetFolderSyncForTest()

    @Test
    fun anExternalEditReachesTheSnapshotWithinTheRescanBound() {
        val root = createTempDirectory("desktop-external-edit")
        val file = root.resolve("screen.layout.md")
        file.writeText(
            """
            |---
            |screen: pickup
            |page: Pickup
            |---
            |
            |# Pickup id frame_root name «Root»
            """.trimMargin(),
        )

        platformConnectFolderForTest(root)
        assertNotNull(folderSyncSnapshotJson(), "connect must publish the initial snapshot")
        val initialRevision = folderSyncRevision()

        // The external writer: no editor write-back, no expected-write bookkeeping.
        file.writeText(
            """
            |---
            |screen: pickup
            |page: Pickup
            |---
            |
            |# Pickup id frame_root name «Externally Renamed»
            """.trimMargin(),
        )

        val deadline = System.currentTimeMillis() + 6_000
        while (folderSyncRevision() == initialRevision && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(
            folderSyncRevision() != initialRevision,
            "external edit must bump the sync revision within the rescan bound",
        )
        val snapshot = assertNotNull(folderSyncSnapshotJson()?.let(::decodeProjectSnapshot))
        assertTrue(
            snapshot.sources.single().content.contains("Externally Renamed"),
            "snapshot must carry the external content",
        )
    }
}
