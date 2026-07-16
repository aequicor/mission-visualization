package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.annotationSidecarFileName
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.AnnotationTool
import io.aequicor.visualization.editor.presentation.DiagramSelection
import io.aequicor.visualization.editor.presentation.DiagramTool
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.EditorTool
import io.aequicor.visualization.editor.presentation.EditorWorkspaceState
import io.aequicor.visualization.editor.presentation.VectorPointRef
import io.aequicor.visualization.editor.presentation.VectorVertexRef
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceAnnotationWorkspace
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationStatus
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationLayoutComments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Annotation intents through the reducer: every document-side edit applies the pure
 * core operation to the screen's [io.aequicor.visualization.subsystems.annotations.AnnotationLayer]
 * AND surgically patches the owning `*.annotations.md` sidecar source in lock-step —
 * the first annotation on a screen creates the sidecar source entry. Annotations never
 * touch the design document (no undo entry), and the view intents only ever touch
 * [EditorWorkspaceState] via [reduceAnnotationWorkspace].
 */
class AnnotationReducerWriteBackTest {

    private val screenFile = "mission-overview.layout.md"
    private val sidecarFile = annotationSidecarFileName(screenFile)

    private fun freshState(): DesignEditorState = createDesignEditorState(annotationFixtureDocuments())

    private fun DesignEditorState.sidecarContent(): String =
        assertNotNull(sources.firstOrNull { it.fileName == sidecarFile }, "missing sidecar $sidecarFile").content

    private fun DesignEditorState.layoutComments() = AnnotationLayoutComments.parse(
        screenFile,
        assertNotNull(sources.firstOrNull { it.fileName == screenFile }).content,
    ).layer.annotations

    private fun DesignEditorState.layer() = assertNotNull(annotationLayers[screenFile], "missing layer for $screenFile")

    private fun DesignEditorState.withIssueOnTile(): DesignEditorState = reduceDesignEditor(
        this,
        DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.NodeAnchor("tile_1"), AnnotationKind.Issue),
    )

    @Test
    fun everyScreenLoadsWithAnEmptyLayerWhenNoSidecarExists() {
        val state = freshState()
        val layoutFiles = state.sources.map { it.fileName }
        assertEquals(layoutFiles.toSet(), state.annotationLayers.keys, "one layer per screen")
        assertTrue(state.annotationLayers.values.all { it.annotations.isEmpty() }, "no sidecar => empty layers")
    }

    @Test
    fun addAnnotationCreatesTheSidecarSourceAndTheLayerEntry() {
        val state = freshState()
        val before = state.sources

        val next = state.withIssueOnTile()

        val annotation = assertNotNull(next.layer().annotations.singleOrNull(), "one annotation in the layer")
        assertEquals("ann-1", annotation.id, "deterministic minted id")
        assertEquals(AnnotationKind.Issue, annotation.kind)
        assertEquals(AnnotationAnchor.NodeAnchor("tile_1"), annotation.anchor)

        assertEquals("## issue @tile_1 {id=ann-1}\n", next.sidecarContent(), "sidecar section written")
        assertEquals(next.sources.size, next.compiledResults.size, "sources/compiles stay index-aligned")
        before.forEach { source ->
            assertEquals(
                source.content,
                next.sources.first { it.fileName == source.fileName }.content,
                "${source.fileName} must stay byte-identical",
            )
        }
        assertEquals(listOf(before), next.previousSources, "source undo captured the pre-edit sources")
        assertSame(state.document, next.document, "annotations never touch the design document")
        assertTrue(next.undoStack.isEmpty(), "no document undo entry for an annotation edit")
    }

    @Test
    fun secondAnnotationMintsTheNextIdAndAppendsASection() {
        val next = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.FreePoint(120.0, 340.0), AnnotationKind.Note),
        )

        assertEquals(listOf("ann-1", "ann-2"), next.layer().annotations.map { it.id })
        assertEquals("## issue @tile_1 {id=ann-1}\n", next.sidecarContent())
        assertEquals(listOf("ann-2"), next.layoutComments().map { it.id }, "comment is embedded in layout.md")
    }

    @Test
    fun setTextWritesTheBodyIntoTheSection() {
        val next = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.SetAnnotationText(screenFile, "ann-1", "Contrast is below AA, fix the background."),
        )

        assertEquals("Contrast is below AA, fix the background.", next.layer().annotations.single().body.text)
        assertEquals(
            "## issue @tile_1 {id=ann-1}\nContrast is below AA, fix the background.\n",
            next.sidecarContent(),
        )
    }

    @Test
    fun setKindRewritesTheHeaderToken() {
        val next = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.SetAnnotationKind(screenFile, "ann-1", AnnotationKind.Note),
        )

        assertEquals(AnnotationKind.Note, next.layer().annotations.single().kind)
        assertEquals("", next.sidecarContent(), "the issue section leaves the sidecar")
        assertEquals(listOf("ann-1"), next.layoutComments().map { it.id }, "the comment moves into layout.md")
    }

    @Test
    fun setIssueStatusWritesItToTheSidecar() {
        val next = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.SetAnnotationStatus(screenFile, "ann-1", AnnotationStatus.InReview),
        )

        assertEquals(AnnotationStatus.InReview, next.layer().annotations.single().status)
        assertEquals("## issue @tile_1 {id=ann-1, status=in-review}\n", next.sidecarContent())
    }

    @Test
    fun moveWritesTheNodeOffsetIntoTheAnchor() {
        val next = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.MoveAnnotation(screenFile, "ann-1", 10.0, -20.0),
        )

        assertEquals(
            AnnotationAnchor.NodeAnchor("tile_1", 10.0, -20.0),
            next.layer().annotations.single().anchor,
        )
        assertEquals("## issue @tile_1(10,-20) {id=ann-1}\n", next.sidecarContent())
    }

    @Test
    fun attachAndDetachImageRoundTripTheImageLine() {
        val image = AnnotationImage("data:image/png;base64,AAAA", 320.0, 200.0)
        val attached = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.AttachAnnotationImage(screenFile, "ann-1", image),
        )
        assertEquals(image, attached.layer().annotations.single().image)
        assertEquals(
            "## issue @tile_1 {id=ann-1}\n![320x200](data:image/png;base64,AAAA)\n",
            attached.sidecarContent(),
        )

        val detached = reduceDesignEditor(
            attached,
            DesignEditorIntent.DetachAnnotationImage(screenFile, "ann-1"),
        )
        assertNull(detached.layer().annotations.single().image)
        assertEquals("## issue @tile_1 {id=ann-1}\n", detached.sidecarContent())
    }

    @Test
    fun attachToNodeAndDetachAnchorRewriteTheAnchor() {
        val free = reduceDesignEditor(
            freshState(),
            DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.FreePoint(5.0, 6.0), AnnotationKind.Issue),
        )

        val attached = reduceDesignEditor(
            free,
            DesignEditorIntent.AttachAnnotationToNode(screenFile, "ann-1", "tile_1", offsetX = 4.0, offsetY = 8.0),
        )
        assertEquals(AnnotationAnchor.NodeAnchor("tile_1", 4.0, 8.0), attached.layer().annotations.single().anchor)
        assertEquals("## issue @tile_1(4,8) {id=ann-1}\n", attached.sidecarContent())

        val detached = reduceDesignEditor(
            attached,
            DesignEditorIntent.DetachAnnotationAnchor(screenFile, "ann-1", x = 40.5, y = 60.0),
        )
        assertEquals(AnnotationAnchor.FreePoint(40.5, 60.0), detached.layer().annotations.single().anchor)
        assertEquals("## issue @(40.5,60) {id=ann-1}\n", detached.sidecarContent())
    }

    @Test
    fun referencesAddAndRemoveInTheHeader() {
        val added = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.AddAnnotationReference(screenFile, "ann-1", "hero"),
        )
        assertEquals(listOf("hero"), added.layer().annotations.single().references)
        assertEquals("## issue @tile_1 +@hero {id=ann-1}\n", added.sidecarContent())

        val removed = reduceDesignEditor(
            added,
            DesignEditorIntent.RemoveAnnotationReference(screenFile, "ann-1", "hero"),
        )
        assertTrue(removed.layer().annotations.single().references.isEmpty())
        assertEquals("## issue @tile_1 {id=ann-1}\n", removed.sidecarContent())
    }

    @Test
    fun deleteDropsTheSectionAndTouchesOnlyItsBytes() {
        val two = reduceDesignEditor(
            freshState().withIssueOnTile(),
            DesignEditorIntent.AddAnnotation(screenFile, AnnotationAnchor.FreePoint(1.0, 2.0), AnnotationKind.Note),
        )

        val next = reduceDesignEditor(two, DesignEditorIntent.DeleteAnnotation(screenFile, "ann-1"))

        assertEquals(listOf("ann-2"), next.layer().annotations.map { it.id })
        assertEquals("", next.sidecarContent(), "the deleted issue leaves the sidecar empty")
        assertEquals(listOf("ann-2"), next.layoutComments().map { it.id }, "the layout comment remains")
    }

    @Test
    fun unknownAnnotationIdIsANoOpReturningTheSameState() {
        val state = freshState().withIssueOnTile()
        assertSame(state, reduceDesignEditor(state, DesignEditorIntent.SetAnnotationText(screenFile, "missing", "x")))
        assertSame(state, reduceDesignEditor(state, DesignEditorIntent.DeleteAnnotation(screenFile, "missing")))
    }

    @Test
    fun unknownScreenIsANoOpReturningTheSameState() {
        val state = freshState()
        val intent = DesignEditorIntent.AddAnnotation(
            "no-such-screen.layout.md",
            AnnotationAnchor.FreePoint(0.0, 0.0),
            AnnotationKind.Note,
        )
        assertSame(state, reduceDesignEditor(state, intent))
    }

    @Test
    fun sidecarSourcesLoadIntoLayersAndAreNeverCompiledAsSlm() {
        val plain = annotationFixtureDocuments()
        val withSidecar = compileMissionDocuments(
            plain.sources + MissionDocumentSource(
                sidecarFile,
                "## issue @tile_1 {id=ann-9}\nFix the tile spacing.\n",
            ),
        )
        val state = createDesignEditorState(withSidecar)

        val annotation = assertNotNull(state.layer().annotations.singleOrNull())
        assertEquals("ann-9", annotation.id)
        assertEquals("Fix the tile spacing.", annotation.body.text)
        // The sidecar is routed to the annotation parser, not the SLM compiler: the
        // merged document has exactly the same pages as without the sidecar.
        assertEquals(
            assertNotNull(plain.document).pages.map { it.id },
            assertNotNull(withSidecar.document).pages.map { it.id },
        )
        assertNull(withSidecar.compiled.last().document, "placeholder compile entry for the sidecar")
        assertEquals(withSidecar.sources.size, withSidecar.compiled.size, "lists stay index-aligned")
    }

    @Test
    fun editingTheSidecarSourceReparsesTheLayer() {
        val state = freshState().withIssueOnTile()
        val index = state.sources.indexOfFirst { it.fileName == sidecarFile }

        val next = reduceDesignEditor(
            state,
            DesignEditorIntent.EditSource(index, "## note @(7,9) {id=ann-7}\nEdited by hand.\n"),
        )

        val annotation = assertNotNull(next.layer().annotations.singleOrNull())
        assertEquals("ann-7", annotation.id)
        assertEquals(AnnotationKind.Note, annotation.kind)
        assertEquals(AnnotationAnchor.FreePoint(7.0, 9.0), annotation.anchor)
        assertEquals("Edited by hand.", annotation.body.text)
        assertSame(state.document, next.document, "sidecar edits never recompile the design document")
    }

    // --- View intents (workspace state) ------------------------------------

    @Test
    fun workspaceIntentsToggleSelectAndSetTheTool() {
        val workspace = EditorWorkspaceState()

        val expanded = reduceAnnotationWorkspace(workspace, DesignEditorIntent.ToggleAnnotationExpanded("ann-1"))
        assertEquals(setOf("ann-1"), expanded.expandedAnnotationIds)
        val collapsed = reduceAnnotationWorkspace(expanded, DesignEditorIntent.ToggleAnnotationExpanded("ann-1"))
        assertTrue(collapsed.expandedAnnotationIds.isEmpty())

        val selected = reduceAnnotationWorkspace(workspace, DesignEditorIntent.SelectAnnotation("ann-1"))
        assertEquals("ann-1", selected.selectedAnnotationId)
        assertEquals("", reduceAnnotationWorkspace(selected, DesignEditorIntent.SelectAnnotation("")).selectedAnnotationId)

        val tool = reduceAnnotationWorkspace(workspace, DesignEditorIntent.SetAnnotationTool(AnnotationTool.Issue))
        assertEquals(AnnotationTool.Issue, tool.annotationTool)
    }

    @Test
    fun activatingAnnotationToolExitsCanvasSubEditorsThatOwnPointerInput() {
        val workspace = EditorWorkspaceState(
            tool = EditorTool.Rectangle,
            vectorEditNodeId = "vector-1",
            vectorSelectedPoint = VectorPointRef(pathIndex = 0, pointIndex = 1),
            vectorSelectedVertex = VectorVertexRef(vertexIndex = 2),
            vectorPaintBucket = true,
            diagramEditNodeId = "diagram-1",
            diagramTool = DiagramTool.DrawEdge(),
            diagramSelection = DiagramSelection(elementIds = setOf("step-1")),
            diagramTextEditRequest = "step-1",
        )

        val active = reduceAnnotationWorkspace(
            workspace,
            DesignEditorIntent.SetAnnotationTool(AnnotationTool.Note),
        )

        assertEquals(AnnotationTool.Note, active.annotationTool)
        assertEquals(EditorTool.Select, active.tool)
        assertEquals("", active.vectorEditNodeId)
        assertNull(active.vectorSelectedPoint)
        assertNull(active.vectorSelectedVertex)
        assertEquals(false, active.vectorPaintBucket)
        assertEquals("", active.diagramEditNodeId)
        assertEquals(DiagramTool.Select, active.diagramTool)
        assertTrue(active.diagramSelection.isEmpty)
        assertNull(active.diagramTextEditRequest)
    }

    @Test
    fun activatingAnnotationToolExitsInlineTextEdit() {
        val editing = freshState().copy(editingTextNodeId = "tile_1")

        val active = reduceDesignEditor(
            editing,
            DesignEditorIntent.SetAnnotationTool(AnnotationTool.Issue),
        )

        assertEquals("", active.editingTextNodeId)
        assertEquals(editing.document, active.document, "annotation activation does not edit the document")
        assertSame(
            editing,
            reduceDesignEditor(editing, DesignEditorIntent.SetAnnotationTool(AnnotationTool.None)),
            "turning annotation mode off does not disturb the current editor",
        )
    }

    @Test
    fun cancelAnnotationAuthoringDeletesOnlyAnEmptyComposerDraft() {
        val added = reduceDesignEditor(
            freshState(),
            DesignEditorIntent.AddAnnotation(
                screenFile,
                AnnotationAnchor.FreePoint(10.0, 20.0),
                AnnotationKind.Note,
            ),
        )
        val annotationId = added.layer().annotations.single().id

        val canceledEmpty = reduceDesignEditor(
            added,
            DesignEditorIntent.CancelAnnotationAuthoring(screenFile, annotationId),
        )

        assertTrue(canceledEmpty.layer().annotations.isEmpty())
        assertTrue(canceledEmpty.layoutComments().isEmpty(), "empty comment is removed from layout.md")

        val committed = reduceDesignEditor(
            added,
            DesignEditorIntent.SetAnnotationText(screenFile, annotationId, "Keep this comment"),
        )
        val canceledCommitted = reduceDesignEditor(
            committed,
            DesignEditorIntent.CancelAnnotationAuthoring(screenFile, annotationId),
        )

        assertEquals("Keep this comment", canceledCommitted.layer().annotations.single().body.text)
        assertEquals(committed.sources, canceledCommitted.sources, "Escape does not rewrite a non-empty annotation")
    }

    @Test
    fun cancelAnnotationAuthoringReturnsWorkspaceToSelectAndPrunesDraftViewState() {
        val workspace = EditorWorkspaceState(
            tool = EditorTool.Comment,
            annotationTool = AnnotationTool.Note,
            annotationComposerId = "ann-1",
            selectedAnnotationId = "ann-1",
            expandedAnnotationIds = setOf("ann-1", "ann-2"),
        )

        val canceled = reduceAnnotationWorkspace(
            workspace,
            DesignEditorIntent.CancelAnnotationAuthoring(screenFile, "ann-1"),
        )

        assertEquals(EditorTool.Select, canceled.tool)
        assertEquals(AnnotationTool.None, canceled.annotationTool)
        assertEquals("", canceled.annotationComposerId)
        assertEquals("", canceled.selectedAnnotationId)
        assertEquals(setOf("ann-2"), canceled.expandedAnnotationIds)
    }

    @Test
    fun deleteAnnotationPrunesTheViewStateAndDocumentIntentsLeaveItUntouched() {
        val workspace = EditorWorkspaceState(
            expandedAnnotationIds = setOf("ann-1", "ann-2"),
            selectedAnnotationId = "ann-1",
            annotationTool = AnnotationTool.Note,
        )

        val pruned = reduceAnnotationWorkspace(workspace, DesignEditorIntent.DeleteAnnotation(screenFile, "ann-1"))
        assertEquals(setOf("ann-2"), pruned.expandedAnnotationIds)
        assertEquals("", pruned.selectedAnnotationId)
        assertEquals(AnnotationTool.Note, pruned.annotationTool, "tool survives a delete")

        assertSame(
            workspace,
            reduceAnnotationWorkspace(workspace, DesignEditorIntent.SelectNode("tile_1")),
            "non-annotation intents are a no-op on workspace state",
        )
    }

    @Test
    fun documentSideAnnotationIntentsLeaveWorkspaceUntouched() {
        val workspace = EditorWorkspaceState(selectedAnnotationId = "ann-1")
        val intent = DesignEditorIntent.SetAnnotationText(screenFile, "ann-1", "text")
        assertSame(workspace, reduceAnnotationWorkspace(workspace, intent))
    }

    @Test
    fun placingAnAnnotationDoesNotDeactivateTheToolbarTool() {
        val workspace = EditorWorkspaceState(annotationTool = AnnotationTool.Note)
        val intent = DesignEditorIntent.AddAnnotation(
            screenFile,
            AnnotationAnchor.FreePoint(1.0, 2.0),
            AnnotationKind.Note,
        )

        assertSame(workspace, reduceAnnotationWorkspace(workspace, intent))
        assertEquals(AnnotationTool.Note, workspace.annotationTool)
    }
}
