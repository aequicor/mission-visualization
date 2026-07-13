package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.EditorStateRelativePath
import io.aequicor.visualization.editor.data.decodeProjectSnapshot
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.platform.FolderFileWrite
import io.aequicor.visualization.editor.platform.folderSyncSnapshotJson
import io.aequicor.visualization.editor.platform.platformConnectFolderForTest
import io.aequicor.visualization.editor.platform.platformResetFolderSyncForTest
import io.aequicor.visualization.editor.platform.platformWriteFolderEditorState
import io.aequicor.visualization.editor.platform.platformWriteFolderFiles
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.semanticallyEquivalent
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopSlmPersistenceIntegrationTest {
    @AfterTest
    fun tearDown() = platformResetFolderSyncForTest()

    @Test
    fun propertyAndStructuralEditsReopenFromLayoutFilesWithoutSidecar() {
        val root = createTempDirectory("desktop-slm-reopen")
        val initialSources = missionDemoDocuments().sources
        initialSources.forEach { source ->
            val target = root.resolve(source.fileName)
            target.parent?.createDirectories()
            target.writeText(source.content)
        }

        platformConnectFolderForTest(root)
        val opened = assertNotNull(folderSyncSnapshotJson()?.let(::decodeProjectSnapshot))
        var state = createDesignEditorState(compileMissionDocuments(opened.sources))
        state = reduceDesignEditor(state, DesignEditorIntent.RenameNode("win_bg", "PersistentBackground"))
        state = reduceDesignEditor(state, DesignEditorIntent.ResizeNode("win_bg", width = 777.0, height = 333.0))
        state = reduceDesignEditor(state, DesignEditorIntent.RenameNode("telemetry_header", "PersistentTelemetry"))
        state = reduceDesignEditor(state, DesignEditorIntent.DuplicateNodes(setOf("t_tile_1")))

        val initialByName = initialSources.associate { it.fileName to it.content }
        val editedByName = state.sources.associate { it.fileName to it.content }
        val writes = initialByName.keys.union(editedByName.keys)
            .filter { initialByName[it] != editedByName[it] }
            .map { FolderFileWrite(it, initialByName[it], editedByName[it]) }
        assertTrue(writes.size >= 2, "the edit series must affect multiple screen files")
        assertTrue(platformWriteFolderFiles(writes))

        // Prove the desktop fallback is irrelevant: create it, disconnect, then remove it.
        platformWriteFolderEditorState("{\"ignored\":true}")
        platformResetFolderSyncForTest()
        Files.deleteIfExists(root.resolve(EditorStateRelativePath))

        platformConnectFolderForTest(root)
        val reopenedSources = assertNotNull(folderSyncSnapshotJson()?.let(::decodeProjectSnapshot)).sources
        assertNotEquals(initialSources, reopenedSources)
        val reopenedDocument = assertNotNull(compileMissionDocuments(reopenedSources).document)
        assertTrue(semanticallyEquivalent(assertNotNull(state.document), reopenedDocument))
    }
}
