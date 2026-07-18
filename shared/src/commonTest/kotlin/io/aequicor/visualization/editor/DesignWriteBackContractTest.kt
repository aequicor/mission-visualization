package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.EffectOp
import io.aequicor.visualization.editor.presentation.EffectType
import io.aequicor.visualization.editor.presentation.FillOp
import io.aequicor.visualization.editor.presentation.PaddingSide
import io.aequicor.visualization.editor.presentation.NewObjectKind
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.StrokeOp
import io.aequicor.visualization.editor.presentation.TypographyPatch
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.parentNodeOf
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.semanticallyEquivalent
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignWriteBackContractTest {
    private fun fresh() = createDesignEditorState(missionDemoDocuments())

    private fun assertRoundTrip(beforeSources: List<io.aequicor.visualization.editor.domain.MissionDocumentSource>, state: io.aequicor.visualization.editor.presentation.DesignEditorState) {
        assertNotEquals(beforeSources, state.sources, state.diagnostics.joinToString { it.message })
        val expected = assertNotNull(state.document)
        val recompiled = assertNotNull(compileMissionDocuments(state.sources).document)
        assertTrue(semanticallyEquivalent(expected, recompiled), state.diagnostics.joinToString { it.message })
    }

    @Test
    fun genericLayoutAlignmentWriteBackRoundTrips() {
        val before = fresh()
        val next = reduceDesignEditor(
            before,
            DesignEditorIntent.SetLayoutAlign("frame_eventlog", alignItems = AlignItems.End),
        )

        assertEquals(AlignItems.End, next.document?.nodeById("frame_eventlog")?.layout?.alignItems)
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun paddingRoundTripIsAccepted() {
        val before = fresh()
        val next = reduceDesignEditor(before, DesignEditorIntent.SetLayoutPadding("frame_eventlog", PaddingSide.All, 32.0))
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun renameRoundTripIsAccepted() {
        val before = fresh()
        val next = reduceDesignEditor(before, DesignEditorIntent.RenameNode("frame_overview", "CommandDeck"))
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun positionedReparentInteractionRoundTrips() {
        val before = fresh()
        val root = assertNotNull(before.document?.nodeById("frame_overview"))
        val oldParent = assertNotNull(root.children.firstOrNull { it.children.any { child -> !child.locked } })
        val moving = assertNotNull(oldParent.children.firstOrNull { !it.locked })
        var next = reduceDesignEditor(before, DesignEditorIntent.BeginInteraction)
        next = reduceDesignEditor(
            next,
            DesignEditorIntent.ReparentNode(
                nodeId = moving.id,
                newParentId = root.id,
                position = DesignPoint(321.0, 123.0),
                rotation = 37.0,
            ),
        )
        next = reduceDesignEditor(next, DesignEditorIntent.EndInteraction)

        assertEquals(root.id, next.document?.parentNodeOf(moving.id)?.id, next.diagnostics.joinToString { it.message })
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun reparentOnCreatedScreenRoundTrips() {
        val before = fresh()
        var next = reduceDesignEditor(before, DesignEditorIntent.CreateScreen(ScreenPreset.Desktop, "Reparent"))
        val root = next.selectedNodeId
        next = reduceDesignEditor(next, DesignEditorIntent.CreateObject(NewObjectKind.Frame, root, 40.0, 40.0, 400.0, 300.0))
        val a = next.selectedNodeId
        next = reduceDesignEditor(next, DesignEditorIntent.CreateObject(NewObjectKind.Frame, root, 600.0, 500.0, 200.0, 150.0))
        val b = next.selectedNodeId
        next = reduceDesignEditor(next, DesignEditorIntent.ReparentNode(b, a, position = DesignPoint(20.0, 20.0)))

        assertEquals(a, next.document?.parentNodeOf(b)?.id, next.diagnostics.joinToString { it.message })
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun multiFileDeleteRoundTrips() {
        val before = fresh()
        val next = reduceDesignEditor(before, DesignEditorIntent.DeleteNodes(setOf("win_bg", "telemetry_header")))
        assertNull(next.document?.nodeById("win_bg"), next.diagnostics.joinToString { it.message })
        assertNull(next.document?.nodeById("telemetry_header"), next.diagnostics.joinToString { it.message })
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun crossFileReparentRoundTrips() {
        val before = fresh()
        val next = reduceDesignEditor(before, DesignEditorIntent.ReparentNode("win_bg", "frame_telemetry"))
        assertEquals("frame_telemetry", next.document?.parentNodeOf("win_bg")?.id, next.diagnostics.joinToString { it.message })
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun previewDoesNotPublishSourcesAndCancelRestoresExactCheckpoint() {
        val before = fresh()
        var next = reduceDesignEditor(before, DesignEditorIntent.BeginInteraction)
        next = reduceDesignEditor(next, DesignEditorIntent.UpdatePosition("win_bg", x = 100.0, y = 120.0))
        next = reduceDesignEditor(next, DesignEditorIntent.UpdatePosition("win_bg", x = 160.0, y = 180.0))

        assertEquals(before.sources, next.sources)
        assertFalse(semanticallyEquivalent(assertNotNull(before.document), assertNotNull(next.document)))

        next = reduceDesignEditor(next, DesignEditorIntent.CancelInteraction)
        assertEquals(before.sources, next.sources)
        assertEquals(before.document, next.document)
        assertFalse(next.interacting)
    }

    @Test
    fun interactionPublishesOneRoundTrippableCommitAtEnd() {
        val before = fresh()
        var next = reduceDesignEditor(before, DesignEditorIntent.BeginInteraction)
        next = reduceDesignEditor(next, DesignEditorIntent.UpdatePosition("win_bg", x = 101.0, y = 121.0))
        next = reduceDesignEditor(next, DesignEditorIntent.UpdatePosition("win_bg", x = 161.0, y = 181.0))
        assertEquals(before.sources, next.sources)

        next = reduceDesignEditor(next, DesignEditorIntent.EndInteraction)
        assertFalse(next.interacting)
        assertRoundTrip(before.sources, next)
    }

    @Test
    fun editsTouchOnlyTheOwningScreenFile() {
        val before = fresh()
        val overviewName = "mission-overview.layout.md"
        val telemetryName = "mission-telemetry.layout.md"
        val beforeByName = before.sources.associate { it.fileName to it.content }

        val overviewEdit = reduceDesignEditor(before, DesignEditorIntent.RenameNode("win_bg", "OverviewBackground"))
        val overviewByName = overviewEdit.sources.associate { it.fileName to it.content }
        assertNotEquals(beforeByName[overviewName], overviewByName[overviewName])
        assertEquals(beforeByName[telemetryName], overviewByName[telemetryName])

        val telemetryEdit = reduceDesignEditor(overviewEdit, DesignEditorIntent.RenameNode("telemetry_header", "TelemetryHeader"))
        val telemetryByName = telemetryEdit.sources.associate { it.fileName to it.content }
        assertEquals(overviewByName[overviewName], telemetryByName[overviewName])
        assertNotEquals(overviewByName[telemetryName], telemetryByName[telemetryName])
        assertRoundTrip(before.sources, telemetryEdit)
    }

    @Test
    fun undoAndRedoRestoreSourceSnapshotsThatReopenFromSlm() {
        val before = fresh()
        val edited = reduceDesignEditor(before, DesignEditorIntent.RenameNode("win_bg", "PersistentBackground"))
        val undone = reduceDesignEditor(edited, DesignEditorIntent.Undo)
        assertEquals(before.sources, undone.sources)
        assertTrue(
            semanticallyEquivalent(
                assertNotNull(undone.document),
                assertNotNull(compileMissionDocuments(undone.sources).document),
            ),
        )

        val redone = reduceDesignEditor(undone, DesignEditorIntent.Redo)
        assertEquals(edited.sources, redone.sources)
        assertTrue(
            semanticallyEquivalent(
                assertNotNull(redone.document),
                assertNotNull(compileMissionDocuments(redone.sources).document),
            ),
        )
    }

    @Test
    fun supportedMutatingIntentsNeverPublishDocumentOnlyState() {
        val cases = listOf(
            "position" to DesignEditorIntent.PositionNode("win_bg", 31.0, 47.0),
            "size" to DesignEditorIntent.ResizeNode("win_bg", 901.0, 411.0),
            "rename" to DesignEditorIntent.RenameNode("win_bg", "InvariantWindow"),
            "opacity" to DesignEditorIntent.UpdateOpacity("win_bg", 0.71),
            "visibility" to DesignEditorIntent.SetVisible("win_bg", false),
            "lock" to DesignEditorIntent.SetLocked("win_bg", true),
            "layout-gap" to DesignEditorIntent.SetLayoutGap("frame_eventlog", 17.0),
            "typography" to DesignEditorIntent.UpdateTypography("src_title", TypographyPatch(fontSize = 31.0)),
            "fill" to DesignEditorIntent.FillCommand("win_bg", FillOp.SetColor(0, DesignColor.fromHex("#E53935")!!)),
            "stroke" to DesignEditorIntent.StrokeCommand("win_bg", StrokeOp.SetColor(DesignColor.fromHex("#3949AB")!!)),
            "effect" to DesignEditorIntent.EffectCommand("win_bg", EffectOp.Add(EffectType.DropShadow)),
            "create" to DesignEditorIntent.CreateObject(NewObjectKind.Rectangle, "frame_overview", 12.0, 18.0, 90.0, 44.0),
            "duplicate" to DesignEditorIntent.DuplicateNodes(setOf("win_bg")),
            "paste" to fresh().let { seed ->
                DesignEditorIntent.PasteNodes(
                    nodes = listOf(seed.document!!.nodeById("win_bg")!!),
                    parentIds = mapOf("win_bg" to seed.document!!.parentNodeOf("win_bg")!!.id),
                )
            },
            "delete" to DesignEditorIntent.DeleteNodes(setOf("win_bg")),
            "reorder" to DesignEditorIntent.ReorderNode("src_panel", ZOrderMove.Forward),
        )

        cases.forEach { (name, intent) ->
            val before = fresh()
            val next = reduceDesignEditor(before, intent)
            assertNotEquals(before.document, next.document, "$name must mutate the document")
            assertNotEquals(before.sources, next.sources, "$name produced forbidden document-only state")
            assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error }, "$name: ${next.diagnostics}")
            assertTrue(
                semanticallyEquivalent(
                    assertNotNull(next.document),
                    assertNotNull(compileMissionDocuments(next.sources).document),
                ),
                "$name did not round-trip",
            )
        }
    }
}
