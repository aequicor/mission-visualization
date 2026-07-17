package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.domain.diagramAnnotationTargetId
import io.aequicor.visualization.editor.domain.parseDiagramAnnotationTargetId
import io.aequicor.visualization.editor.domain.ExportIssuesPromptUseCase
import io.aequicor.visualization.editor.presentation.DiagramSelection
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.annotationAnchorForPress
import io.aequicor.visualization.editor.presentation.annotationTargetVisualBounds
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.editor.presentation.screenFileNamesByPageId
import io.aequicor.visualization.editor.ui.resolveAnnotationTargetAt
import io.aequicor.visualization.editor.ui.resolveDiagramElementSelection
import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.ExportScope
import io.aequicor.visualization.subsystems.annotations.slm.AnnotationLayoutComments
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.updateNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unified single-click selection resolves a diagram element (block/edge) from a canvas press —
 * the seam that lets one click pick a UML block instead of the whole diagram container, and that
 * lets a click switch straight from one diagram's edit layer to another's element.
 *
 * Coordinate contract: [resolveDiagramElementSelection] takes document coordinates. Graph node
 * positions are relative to the diagram node's laid-out box top-left, so a node's document point is
 * `box.x/y + graphPosition`.
 */
class DiagramSingleClickSelectionTest {

    private val source = """
        |---
        |screen: diagramsTest
        |page: Diagrams Test
        |---
        |
        |# Diagrams Test id frame_root name «Root»
        |
        |## Diagram: Canvas id canvas
        |
        |Node rectangle a 120 by 60 position 20 20 label «A»
        |Node rectangle b 120 by 60 position 220 20 label «B»
        |Node rectangle c 120 by 60 position 20 260 label «C»
        |Node component uml_component «Payments» stereotype «service» 140 by 60 position 20 380
        |Edge e1 from a to b
    """.trimMargin()

    private fun layoutRoot(): LayoutBox {
        val document = assertNotNull(
            compileMissionDocuments(listOf(MissionDocumentSource("diagrams-test.layout.md", source))).document,
            "document compiles",
        )
        val page = document.pages.first()
        val resolved = assertNotNull(DesignResolver(document).resolvePage(page).firstOrNull(), "root frame resolves")
        return DesignLayoutEngine().layout(resolved)
    }

    private fun documentOf() = assertNotNull(
        compileMissionDocuments(listOf(MissionDocumentSource("diagrams-test.layout.md", source))).document,
    )

    @Test
    fun clickOnBlockSelectsThatBlockInsideItsDiagram() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"), "diagram laid out")

        // Node "a" center: graph-local (80, 50) → document (box.x + 80, box.y + 50).
        val target = assertNotNull(
            resolveDiagramElementSelection(root, document, box.x + 80.0, box.y + 50.0, zoomPx = 1f),
            "press on block A resolves to a diagram element",
        )
        assertEquals("canvas", target.diagramId)
        assertEquals(DiagramSelection(elementIds = setOf("a")), target.selection)

        // Node "b" center: graph-local (280, 50).
        val targetB = assertNotNull(
            resolveDiagramElementSelection(root, document, box.x + 280.0, box.y + 50.0, zoomPx = 1f),
        )
        assertEquals(DiagramSelection(elementIds = setOf("b")), targetB.selection)
    }

    @Test
    fun clickOnEmptyDiagramAreaResolvesToNoElement() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"))

        // Graph-local (180, 160): between the a/b row (y 20..80) and node c (y 260..320), and in the
        // gap between column a (x ≤ 140) and column b (x ≥ 220) — no block, no edge. The caller then
        // selects the whole diagram (container) instead of drilling in.
        assertNull(resolveDiagramElementSelection(root, document, box.x + 180.0, box.y + 160.0, zoomPx = 1f))
    }

    @Test
    fun clickOutsideAnyDiagramResolvesToNull() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"))

        assertNull(resolveDiagramElementSelection(root, document, box.right + 500.0, box.bottom + 500.0, zoomPx = 1f))
    }

    @Test
    fun annotationPressOnUmlComponentUsesScopedElementAnchorThatFollowsTheElement() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"))
        val docX = box.x + 90.0
        val docY = box.y + 410.0

        val targetId = resolveAnnotationTargetAt(
            root,
            document,
            docX,
            docY,
            zoomPx = 1f,
            fallbackNodeId = "canvas",
        )
        val expectedTargetId = diagramAnnotationTargetId("canvas", "uml_component")
        assertEquals(expectedTargetId, targetId)
        assertEquals("canvas", assertNotNull(parseDiagramAnnotationTargetId(targetId)).diagramNodeId)

        val bounds = assertNotNull(annotationTargetVisualBounds(root, document, targetId))
        assertEquals(box.x + 20.0, bounds.left)
        assertEquals(box.y + 380.0, bounds.top)
        assertEquals(
            AnnotationAnchor.NodeAnchor(expectedTargetId, offsetX = 0.0, offsetY = 30.0),
            annotationAnchorForPress(docX, docY, targetId, bounds),
        )

        val movedDocument = document.updateNode("canvas") { node ->
            val graph = (node.kind as DesignNodeKind.Diagram).graph.updateNode(DiagramNodeId("uml_component")) {
                it.copy(x = it.x + 75.0, y = it.y - 25.0)
            }
            node.copy(kind = DesignNodeKind.Diagram(graph))
        }
        val movedBounds = assertNotNull(annotationTargetVisualBounds(root, movedDocument, targetId))
        assertEquals(bounds.left + 75.0, movedBounds.left)
        assertEquals(bounds.top - 25.0, movedBounds.top)
    }

    @Test
    fun annotationPressOnEmptyDiagramAreaFallsBackToDiagramContainer() {
        val root = layoutRoot()
        val document = documentOf()
        val box = assertNotNull(root.findBySourceId("canvas"))

        assertEquals(
            "canvas",
            resolveAnnotationTargetAt(
                root,
                document,
                box.x + 180.0,
                box.y + 160.0,
                zoomPx = 1f,
                fallbackNodeId = "canvas",
            ),
        )
    }

    @Test
    fun deletingAnchoredUmlComponentFreezesAnnotationAtItsCurrentPosition() {
        val documents = compileMissionDocuments(listOf(MissionDocumentSource("diagrams-test.layout.md", source)))
        var state = createDesignEditorState(documents)
        val targetId = diagramAnnotationTargetId("canvas", "uml_component")
        val root = layoutRoot()
        val bounds = assertNotNull(annotationTargetVisualBounds(root, state.document, targetId))
        val expectedX = bounds.centerX
        val expectedY = bounds.top + 30.0

        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(
                "diagrams-test.layout.md",
                AnnotationAnchor.NodeAnchor(targetId, offsetY = 30.0),
                AnnotationKind.Note,
            ),
        )
        val sourceAfterAdd = assertNotNull(state.sources.firstOrNull { it.fileName == "diagrams-test.layout.md" })
        assertEquals(
            AnnotationAnchor.NodeAnchor(targetId, offsetY = 30.0),
            AnnotationLayoutComments.parse(sourceAfterAdd.fileName, sourceAfterAdd.content).layer.annotations.single().anchor,
            "scoped UML target survives the layout-comment round trip",
        )
        state = reduceDesignEditor(
            state,
            DiagramEditorIntent.DeleteDiagramElement(
                nodeId = "canvas",
                elementIds = setOf("uml_component"),
            ),
        )

        val anchor = assertIs<AnnotationAnchor.FreePoint>(
            assertNotNull(state.annotationLayers["diagrams-test.layout.md"]).annotations.single().anchor,
        )
        assertEquals(expectedX, anchor.x)
        assertEquals(expectedY, anchor.y)
        val remainingGraph = (assertNotNull(state.document).nodeById("canvas")?.kind as DesignNodeKind.Diagram).graph
        assertNull(remainingGraph.nodeById(DiagramNodeId("uml_component")))
        val sourceAfterDelete = assertNotNull(state.sources.firstOrNull { it.fileName == "diagrams-test.layout.md" })
        assertEquals(
            anchor,
            AnnotationLayoutComments.parse(sourceAfterDelete.fileName, sourceAfterDelete.content).layer.annotations.single().anchor,
        )
    }

    @Test
    fun exportedIssueResolvesUmlComponentContext() {
        var state = createDesignEditorState(
            compileMissionDocuments(listOf(MissionDocumentSource("diagrams-test.layout.md", source))),
        )
        val targetId = diagramAnnotationTargetId("canvas", "uml_component")
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.AddAnnotation(
                "diagrams-test.layout.md",
                AnnotationAnchor.NodeAnchor(targetId),
                AnnotationKind.Issue,
            ),
        )
        state = reduceDesignEditor(
            state,
            DesignEditorIntent.SetAnnotationText("diagrams-test.layout.md", "ann-1", "Fix UML component."),
        )

        val prompt = ExportIssuesPromptUseCase().invoke(
            layers = state.annotationLayers.values.toList(),
            scope = ExportScope.WholeDocument,
            document = state.document,
            screenFileNameByPageId = state.screenFileNamesByPageId(),
        )

        assertTrue("canvas/uml_component \"Payments\" (uml-component)" in prompt)
        assertFalse("node deleted or unresolved" in prompt)
    }
}
