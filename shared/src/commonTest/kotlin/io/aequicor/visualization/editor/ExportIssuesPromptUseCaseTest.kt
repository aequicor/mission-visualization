package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.ExportIssuesPromptUseCase
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.ExportScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ExportIssuesPromptUseCase] over a real editor state: issues (never notes) become a
 * numbered AI-agent prompt with node context resolved from the working document
 * (label / type / screen / laid-out bounds), a dangling anchor is reported — not
 * dropped — and all three [ExportScope]s filter correctly.
 */
class ExportIssuesPromptUseCaseTest {

    private val overviewFile = "mission-overview.layout.md"
    private val telemetryFile = "mission-telemetry.layout.md"
    private val useCase = ExportIssuesPromptUseCase()

    /**
     * ann-1: issue on `tile_1` (overview); ann-2: note (never exported); ann-3: issue
     * on a deleted node (dangling); ann-4: free-point issue on the telemetry screen
     * with an extra `tile_1` reference.
     */
    private fun annotatedState(): DesignEditorState {
        var state = createDesignEditorState(legacyMissionDocuments())
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(overviewFile, AnnotationAnchor.NodeAnchor("tile_1"), AnnotationKind.Issue),
        )
        state = reduceDesignEditor(state, DesignEditorIntent.SetAnnotationText(overviewFile, "ann-1", "Fix tile contrast."))
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(overviewFile, AnnotationAnchor.NodeAnchor("hero"), AnnotationKind.Note),
        )
        state = reduceDesignEditor(state, DesignEditorIntent.SetAnnotationText(overviewFile, "ann-2", "Just a note."))
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(overviewFile, AnnotationAnchor.NodeAnchor("ghost_gone"), AnnotationKind.Issue),
        )
        state = reduceDesignEditor(state, DesignEditorIntent.SetAnnotationText(overviewFile, "ann-3", "Dangling issue."))
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(telemetryFile, AnnotationAnchor.FreePoint(120.0, 340.0), AnnotationKind.Issue),
        )
        state = reduceDesignEditor(state, DesignEditorIntent.SetAnnotationText(telemetryFile, "ann-4", "Crowded gauges."))
        state = reduceDesignEditor(state, DesignEditorIntent.AddAnnotationReference(telemetryFile, "ann-4", "tile_1"))
        return state
    }

    private fun export(state: DesignEditorState, scope: ExportScope): String =
        useCase(
            layers = state.annotationLayers.values.toList(),
            scope = scope,
            document = state.document,
            screenFileNameByPageId = state.screenFileNamesByPageId(),
        )

    @Test
    fun wholeDocumentExportsEveryIssueWithNodeContextAndSkipsNotes() {
        val state = annotatedState()
        val prompt = export(state, ExportScope.WholeDocument)

        assertTrue(prompt.startsWith("You are an AI coding agent"), "agent instruction header")
        assertTrue("Fix tile contrast." in prompt)
        assertTrue("Crowded gauges." in prompt)
        assertTrue("Dangling issue." in prompt)
        assertFalse("Just a note." in prompt, "notes are never exported")

        // Resolved node context: authored id, label, screen and laid-out bounds.
        assertTrue("tile_1 \"Tile 1\"" in prompt, "label resolved from the document")
        assertTrue("on $overviewFile" in prompt, "screen file name resolved")
        assertTrue(", bounds " in prompt, "laid-out bounds included")
        // Dangling anchor: kept and marked, never dropped.
        assertTrue("ghost_gone (node deleted or unresolved)" in prompt)
        // Free-point anchor and the extra reference.
        assertTrue("free point at (120, 340)" in prompt)
        assertTrue("Also references: tile_1 \"Tile 1\"" in prompt)

        // Deterministic ordering: layers by screen file name => overview issues first.
        assertEquals(
            listOf("Fix tile contrast.", "Dangling issue.", "Crowded gauges."),
            listOf("Fix tile contrast.", "Dangling issue.", "Crowded gauges.").sortedBy { prompt.indexOf(it) },
        )
    }

    @Test
    fun screenScopeExportsOnlyThatScreensIssues() {
        val prompt = export(annotatedState(), ExportScope.Screen(telemetryFile))

        assertTrue("Crowded gauges." in prompt)
        assertFalse("Fix tile contrast." in prompt)
        assertFalse("Dangling issue." in prompt)
    }

    @Test
    fun selectedScopeExportsOnlyTheGivenIds() {
        val prompt = export(annotatedState(), ExportScope.Selected(setOf("ann-1")))

        assertTrue("Fix tile contrast." in prompt)
        assertFalse("Crowded gauges." in prompt)
        assertFalse("Dangling issue." in prompt)
    }

    @Test
    fun selectingOnlyANoteYieldsTheEmptyScopeMessage() {
        val prompt = export(annotatedState(), ExportScope.Selected(setOf("ann-2")))

        assertTrue("No design issues in the selected scope." in prompt)
        assertFalse("Just a note." in prompt)
    }
}
