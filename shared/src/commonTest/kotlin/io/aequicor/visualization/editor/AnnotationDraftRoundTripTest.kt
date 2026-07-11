package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DraftEnvelopeDto
import io.aequicor.visualization.editor.data.toDomain
import io.aequicor.visualization.editor.data.toDto
import io.aequicor.visualization.editor.domain.DraftSchemaVersion
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.WorkspaceDraft
import io.aequicor.visualization.editor.domain.annotationSidecarFileName
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Annotation sidecars ride local-draft persistence for free: they live in the same
 * [WorkspaceDraft.files] source list as the SLM screens, so the localStorage save ->
 * reload path ([DraftEnvelopeDto] + [Json], exactly what `DefaultDraftRepository`
 * persists) round-trips them byte-for-byte, and restore (recompile + state creation)
 * reproduces the same annotation layer.
 */
class AnnotationDraftRoundTripTest {

    private val screenFile = "mission-overview.layout.md"
    private val sidecarFile = annotationSidecarFileName(screenFile)

    /** Same JSON config as `DefaultDraftRepository`. */
    private val json: Json = Json { ignoreUnknownKeys = true }

    /** A state whose sources carry a sidecar with every annotation field, via real write-back. */
    private fun annotatedState(): DesignEditorState {
        var state = createDesignEditorState(annotationFixtureDocuments())
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.NodeAnchor("tile_1"), AnnotationKind.Issue),
        )
        state = reduceDesignEditor(state, DesignEditorIntent.SetAnnotationText(screenFile, "ann-1", "Fix contrast."))
        state = reduceDesignEditor(state, DesignEditorIntent.MoveAnnotation(screenFile, "ann-1", 8.0, -12.0))
        state = reduceDesignEditor(state, DesignEditorIntent.AddAnnotationReference(screenFile, "ann-1", "hero"))
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AttachAnnotationImage(
                screenFile,
                "ann-1",
                AnnotationImage("data:image/png;base64,QUJD", 320.0, 200.0),
            ),
        )
        return state
    }

    /** Round-trips [sources] through the exact envelope the draft repository persists. */
    private fun envelopeRoundTrip(sources: List<MissionDocumentSource>): List<MissionDocumentSource> {
        val draft = WorkspaceDraft(DraftSchemaVersion, sources, projectName = "Annotated")
        val raw = json.encodeToString(DraftEnvelopeDto.serializer(), draft.toDto())
        return json.decodeFromString(DraftEnvelopeDto.serializer(), raw).toDomain().files
    }

    @Test
    fun sidecarSourceSurvivesTheDraftEnvelopeByteForByte() {
        val sources = annotatedState().sources
        assertTrue(sources.any { it.fileName == sidecarFile }, "sidecar rides in the persisted source list")

        val restored = envelopeRoundTrip(sources)

        assertEquals(sources, restored, "every source, sidecar included, survives the envelope")
    }

    @Test
    fun restoreReproducesTheAnnotationLayerFromThePersistedSidecar() {
        val state = annotatedState()
        val restoredState = createDesignEditorState(compileMissionDocuments(envelopeRoundTrip(state.sources)))

        val layer = assertNotNull(restoredState.annotationLayers[screenFile])
        assertEquals(state.annotationLayers[screenFile], layer, "the layer round-trips the restore")
        val annotation = assertNotNull(layer.annotations.singleOrNull())
        assertEquals("ann-1", annotation.id)
        assertEquals(AnnotationKind.Issue, annotation.kind)
        assertEquals(AnnotationAnchor.NodeAnchor("tile_1", 8.0, -12.0), annotation.anchor)
        assertEquals("Fix contrast.", annotation.body.text)
        assertEquals(listOf("hero"), annotation.references)
        assertEquals(AnnotationImage("data:image/png;base64,QUJD", 320.0, 200.0), annotation.image)
    }
}
