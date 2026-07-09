package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.presentation.DefaultProjectDisplayName
import io.aequicor.visualization.editor.presentation.projectDisplayName
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectDisplayNameTest {

    @Test
    fun metadataNameWins() {
        assertEquals(
            "Opened Folder",
            projectDisplayName(" Opened Folder ", "Document Name", listOf(MissionDocumentSource("source.layout.md", ""))),
        )
    }

    @Test
    fun fallsBackToDocumentName() {
        assertEquals(
            "Document Name",
            projectDisplayName("", " Document Name ", listOf(MissionDocumentSource("source.layout.md", ""))),
        )
    }

    @Test
    fun fallsBackToFirstSourceBaseName() {
        assertEquals(
            "source",
            projectDisplayName("", "", listOf(MissionDocumentSource("folder/source.layout.md", ""))),
        )
    }

    @Test
    fun fallsBackToDefaultName() {
        assertEquals(DefaultProjectDisplayName, projectDisplayName("", null, emptyList()))
    }
}
