package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.DraftEnvelopeDto
import io.aequicor.visualization.editor.data.toDomain
import io.aequicor.visualization.editor.data.toDto
import io.aequicor.visualization.editor.domain.DraftSchemaVersion
import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.WorkspaceDraft
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Local-draft persistence for the new figure fields. Drafts serialize the SLM *sources*
 * (the single source of truth — see [WorkspaceDraft]); this guards the localStorage
 * save -> reload path by round-tripping SLM that carries ellipse arc, a per-vertex corner
 * radius, region fills and a flattened `vector.paths` shape through exactly the envelope the
 * repository persists ([DraftEnvelopeDto] + [Json]), then asserting the new SLM keys survive
 * and still recompile to a document with the fields intact.
 */
class FigureDraftRoundTripTest {

    private val owningFile = "shapes-showcase.layout.md"

    /** Same JSON config as `DefaultDraftRepository`. */
    private val json: Json = Json { ignoreUnknownKeys = true }

    /** A state whose SLM sources carry every new figure field, produced via real write-back. */
    private fun figureLoadedState(): DesignEditorState {
        var state = createDesignEditorState(missionDemoDocuments())
        val fill = listOf(DesignPaint.Solid(DesignColor(0xFFEF476F).bindable()))
        state = reduceDesignEditor(state, DesignEditorIntent.SetArcStart("showcase_ellipse", -90.0))
        state = reduceDesignEditor(state, DesignEditorIntent.SetArcSweep("showcase_ellipse", 270.0))
        state = reduceDesignEditor(state, DesignEditorIntent.SetArcRatio("showcase_ellipse", 0.5))
        state = reduceDesignEditor(state, DesignEditorIntent.SetVertexCornerRadius("showcase_network", 1, 8.0))
        state = reduceDesignEditor(state, DesignEditorIntent.SetRegionFill("showcase_network", 0, fill))
        // NOTE: FlattenNode is intentionally omitted here — it is a multi-section structural rewrite
        // that lands in-memory only under CNL-only write-back (tracked gap), so it would not appear
        // in the persisted SLM sources this draft round-trip guards. The scalar figure fields below
        // (arc, inner radius, vertex corner radius, region fill) all persist as CNL and round-trip.
        return state
    }

    private fun List<MissionDocumentSource>.owning(): String =
        assertNotNull(firstOrNull { it.fileName == owningFile }, "missing $owningFile").content

    /** Round-trips [sources] through the exact envelope the draft repository persists. */
    private fun envelopeRoundTrip(sources: List<MissionDocumentSource>): List<MissionDocumentSource> {
        val draft = WorkspaceDraft(DraftSchemaVersion, sources, projectName = "Figures")
        val raw = json.encodeToString(DraftEnvelopeDto.serializer(), draft.toDto())
        return json.decodeFromString(DraftEnvelopeDto.serializer(), raw).toDomain().files
    }

    @Test
    fun figureSourcesSurviveDraftEnvelopeRoundTrip() {
        val sources = figureLoadedState().sources
        // The authored figure edits must be present in the owning SLM before we persist it.
        val owningBefore = sources.owning()
        assertTrue("arc (-90 270)" in owningBefore, "arc phrase authored")
        assertTrue("inner 0.5" in owningBefore, "arc ratio authored")
        assertTrue("radius 8" in owningBefore, "vertex corner radius authored")
        assertTrue("fill " in owningBefore, "region fill authored")

        val restored = envelopeRoundTrip(sources)

        // Byte-for-byte the whole source set survives the localStorage envelope.
        assertEquals(sources, restored, "every SLM source survives the draft envelope")
        // And explicitly: the new figure keys survive in the owning source.
        val owningAfter = restored.owning()
        assertTrue("arc (-90 270)" in owningAfter, "arc phrase survived")
        assertTrue("inner 0.5" in owningAfter, "arc ratio survived")
        assertTrue("radius 8" in owningAfter, "vertex radius survived")
        assertTrue("fill " in owningAfter, "region fill survived")
    }

    @Test
    fun restoredFigureSourcesRecompileWithFieldsIntact() {
        val restored = envelopeRoundTrip(figureLoadedState().sources)
        val recompiled = compileMissionDocuments(restored)
        val document = assertNotNull(recompiled.document, "restored SLM recompiles to a document")

        val ellipse = assertNotNull(
            document.nodeById("showcase_ellipse")?.kind as? DesignNodeKind.Shape,
            "ellipse shape survives",
        )
        assertEquals(-90.0, ellipse.arcStartDeg, "arcStart restored")
        assertEquals(270.0, ellipse.arcSweepDeg, "arcSweep restored")
        assertEquals(0.5, ellipse.innerRadius, "innerRadius restored")

        val network = assertNotNull(
            document.nodeById("showcase_network")?.kind as? DesignNodeKind.Shape,
            "network shape survives",
        )
        assertEquals(8.0, network.network?.vertices?.get(1)?.cornerRadius, "vertex corner radius restored")
        assertEquals(1, network.regionFills[0]?.size, "region fill restored")
    }
}
