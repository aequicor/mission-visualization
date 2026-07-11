package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.AnnotationSidecarDiagnosticCode
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.annotationSidecarFileName
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Load-boundary normalization of `*.annotations.md` sidecars — the review layer's
 * id-stability invariant:
 * - sections written without `{id=...}` get their synthesized ids pinned into the
 *   source once, so surgical edits address the same section instead of appending a
 *   duplicate that silently reverts on reload;
 * - ids duplicated across two screens' sidecars are re-minted to be globally unique,
 *   so id-keyed selection/edit/export can never target the wrong screen's annotation;
 * - recoverable parse warnings surface as editor diagnostics (file + 1-based line) and
 *   refresh on sidecar source edits.
 */
class AnnotationSidecarNormalizationTest {

    private val overviewScreen = "mission-overview.layout.md"
    private val overviewSidecar = annotationSidecarFileName(overviewScreen)
    private val telemetryScreen = "mission-telemetry.layout.md"
    private val telemetrySidecar = annotationSidecarFileName(telemetryScreen)

    private fun stateWithSidecars(vararg sidecars: Pair<String, String>): DesignEditorState =
        createDesignEditorState(
            compileMissionDocuments(
                legacyMissionDocuments().sources + sidecars.map { (name, content) -> MissionDocumentSource(name, content) },
            ),
        )

    private fun DesignEditorState.sidecarContent(fileName: String): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "missing sidecar $fileName").content

    // --- Pinning synthesized ids -------------------------------------------

    @Test
    fun loadPinsSynthesizedIdsIntoTheSidecarSource() {
        val state = stateWithSidecars(overviewSidecar to "## note @tile_1\nOriginal body.\n")

        assertEquals(
            "## note @tile_1 {id=ann-1}\nOriginal body.\n",
            state.sidecarContent(overviewSidecar),
            "the synthesized id is pinned into the header at load",
        )
        val layer = assertNotNull(state.annotationLayers[overviewScreen])
        assertEquals(listOf("ann-1"), layer.annotations.map { it.id })
        assertTrue(state.previousSources.isEmpty(), "load-time normalization records no undo history entry")
    }

    @Test
    fun editsAfterLoadTargetThePinnedSectionInsteadOfAppendingADuplicate() {
        val loaded = stateWithSidecars(overviewSidecar to "## note @tile_1\nOriginal body.\n")

        val edited = reduceDesignEditor(
            loaded,
            DesignEditorIntent.SetAnnotationText(overviewScreen, "ann-1", "EDITED body."),
        )

        assertEquals(
            "## note @tile_1 {id=ann-1}\nEDITED body.\n",
            edited.sidecarContent(overviewSidecar),
            "the unmarked section is edited in place, never duplicated",
        )
        // Reload the edited sources: the edit survives the round trip.
        val reloaded = createDesignEditorState(compileMissionDocuments(edited.sources))
        assertEquals(
            "EDITED body.",
            assertNotNull(reloaded.annotationLayers[overviewScreen]).annotations.single().body.text,
        )
    }

    @Test
    fun deleteAfterLoadRemovesThePinnedSectionForGood() {
        val loaded = stateWithSidecars(overviewSidecar to "## note @tile_1\nDoomed.\n")

        val deleted = reduceDesignEditor(loaded, DesignEditorIntent.DeleteAnnotation(overviewScreen, "ann-1"))

        assertEquals("", deleted.sidecarContent(overviewSidecar), "the section leaves the file")
        val reloaded = createDesignEditorState(compileMissionDocuments(deleted.sources))
        assertTrue(
            assertNotNull(reloaded.annotationLayers[overviewScreen]).annotations.isEmpty(),
            "the deleted annotation cannot resurrect on reload",
        )
    }

    // --- Cross-file id uniqueness ------------------------------------------

    @Test
    fun duplicateIdsAcrossScreensAreRemintedKeepingTheEarlierFile() {
        val state = stateWithSidecars(
            overviewSidecar to "## issue @tile_1 {id=ann-1}\nOverview issue.\n",
            telemetrySidecar to "## issue @(10,20) {id=ann-1}\nTelemetry issue.\n",
        )

        val overview = assertNotNull(state.annotationLayers[overviewScreen])
        val telemetry = assertNotNull(state.annotationLayers[telemetryScreen])
        assertEquals(listOf("ann-1"), overview.annotations.map { it.id }, "the earlier file keeps its id")
        assertEquals(listOf("ann-2"), telemetry.annotations.map { it.id }, "the collision is re-minted")
        assertEquals("Telemetry issue.", telemetry.annotations.single().body.text, "body survives the rename")
        assertTrue(
            "{id=ann-2}" in state.sidecarContent(telemetrySidecar),
            "the re-minted id is pinned into the telemetry sidecar",
        )
        // Editing by the new id touches only the telemetry file.
        val edited = reduceDesignEditor(
            state,
            DesignEditorIntent.SetAnnotationText(telemetryScreen, "ann-2", "Telemetry EDITED."),
        )
        assertEquals(state.sidecarContent(overviewSidecar), edited.sidecarContent(overviewSidecar))
        assertTrue("Telemetry EDITED." in edited.sidecarContent(telemetrySidecar))
    }

    @Test
    fun remintDodgesIdsOfEveryFileNotJustEarlierOnes() {
        val state = stateWithSidecars(
            overviewSidecar to "## note @(1,1) {id=ann-1}\n\n## note @(2,2) {id=ann-2}\n",
            telemetrySidecar to "## note @(3,3) {id=ann-1}\n",
        )

        val telemetry = assertNotNull(state.annotationLayers[telemetryScreen])
        assertEquals(listOf("ann-3"), telemetry.annotations.map { it.id }, "fresh mint avoids ann-2 kept by the other file")
    }

    // --- Warnings as diagnostics -------------------------------------------

    @Test
    fun sidecarParseWarningsSurfaceAsEditorDiagnosticsWithFileAndLine() {
        val state = stateWithSidecars(
            overviewSidecar to "## note @(1,1) {id=ann-1}\nFine.\n\n## bogus @tile_1\nSkipped kind.\n",
        )

        val warning = assertNotNull(
            state.diagnostics.firstOrNull { it.code == AnnotationSidecarDiagnosticCode },
            "the sidecar warning reaches the editor diagnostics list",
        )
        assertEquals(DesignSeverity.Warning, warning.severity)
        assertEquals(overviewSidecar, assertNotNull(warning.location).file)
        assertEquals(4, assertNotNull(warning.location).line, "1-based line of the malformed header")
    }

    @Test
    fun editingTheSidecarSourceRefreshesTheDiagnostics() {
        val state = stateWithSidecars(overviewSidecar to "## bogus @tile_1\nBad.\n")
        assertTrue(state.diagnostics.any { it.code == AnnotationSidecarDiagnosticCode })
        val index = state.sources.indexOfFirst { it.fileName == overviewSidecar }

        val fixed = reduceDesignEditor(
            state,
            DesignEditorIntent.EditSource(index, "## note @tile_1 {id=ann-1}\nGood.\n"),
        )

        assertTrue(
            fixed.diagnostics.none { it.code == AnnotationSidecarDiagnosticCode },
            "fixing the section clears its warning",
        )
    }

    // --- EditSource normalization ------------------------------------------

    @Test
    fun editSourcePinsSynthesizedIdsWithoutASourceHistoryEntry() {
        val state = stateWithSidecars(overviewSidecar to "## note @(0,0) {id=ann-1}\nSeed.\n")
        val index = state.sources.indexOfFirst { it.fileName == overviewSidecar }
        val historyBefore = state.previousSources

        val edited = reduceDesignEditor(
            state,
            DesignEditorIntent.EditSource(index, "## issue @tile_1\nHand written.\n"),
        )

        assertEquals(
            "## issue @tile_1 {id=ann-1}\nHand written.\n",
            edited.sidecarContent(overviewSidecar),
            "the hand-written section gets its id pinned on commit",
        )
        assertEquals(historyBefore, edited.previousSources, "EditSource keeps SLM undo semantics: no source history entry")
        val layer = assertNotNull(edited.annotationLayers[overviewScreen])
        assertEquals(listOf("ann-1"), layer.annotations.map { it.id })
    }

    @Test
    fun editSourceRemintsIdsCollidingWithOtherScreens() {
        val state = stateWithSidecars(
            overviewSidecar to "## note @(1,1) {id=ann-1}\nOverview.\n",
            telemetrySidecar to "## note @(2,2) {id=ann-2}\nTelemetry.\n",
        )
        val index = state.sources.indexOfFirst { it.fileName == telemetrySidecar }

        val edited = reduceDesignEditor(
            state,
            DesignEditorIntent.EditSource(index, "## note @(2,2) {id=ann-1}\nTelemetry now clashes.\n"),
        )

        val telemetry = assertNotNull(edited.annotationLayers[telemetryScreen])
        assertEquals(listOf("ann-2"), telemetry.annotations.map { it.id }, "the clash re-mints against the other screen's ids")
        assertTrue("{id=ann-2}" in edited.sidecarContent(telemetrySidecar))
    }
}
