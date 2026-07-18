package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the folder-open error dialog has to work with.
 *
 * The loader compile-gates on [io.aequicor.visualization.editor.domain.MissionDocuments.hasErrors]
 * and holds the last good canvas, so a broken external project simply stops opening. These pin the
 * inputs the dialog reports: a real reason must exist, name its file, and be a real error rather
 * than a passing warning.
 */
class FolderErrorDetailsTest {

    private val fileName = "broken.layout.md"

    private fun compile(body: String) = compileMissionDocuments(
        listOf(MissionDocumentSource(fileName, body)),
    )

    @Test
    fun aBrokenProjectYieldsAnErrorThatNamesItsFile() {
        val docs = compile(
            """
            |---
            |screen: brokenScreen
            |---
            |
            |# Broken id frame_root name «Root»
            |
            |## Diagram: Canvas id canvas
            |
            |Node use-case uc1 «Submit» 180 by 80 position 0 0 nonsense-word
            """.trimMargin(),
        )

        assertTrue(docs.hasErrors, "the fixture must actually be broken")
        val errors = docs.diagnostics.filter { it.severity == DesignSeverity.Error }
        assertTrue(errors.isNotEmpty(), "a compile gate with no reason to show is the bug itself")
        assertTrue(
            errors.any { it.location?.file == fileName },
            "the reason must name the file the user has to fix: ${errors.map { it.location }}",
        )
        assertTrue(
            errors.any { it.message.isNotBlank() },
            "an empty message would render an empty dialog",
        )
    }

    @Test
    fun aHealthyProjectHasNothingToReport() {
        val docs = compile(
            """
            |---
            |screen: okScreen
            |---
            |
            |# Ok id frame_root name «Root»
            |
            |## Diagram: Canvas id canvas
            |
            |Node use-case uc1 «Submit» 180 by 80 position 0 0
            """.trimMargin(),
        )

        assertTrue(!docs.hasErrors, "a healthy project must open: ${docs.diagnostics}")
    }
}
