package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DesignEditorState
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Inline/inspector text editing of typed-payload diagram nodes through [reduceDesignEditor].
 *
 * Typed payloads (use-case, class, note, …) carry their caption in the payload itself, which is
 * what the canvas renders; `DiagramNode.labels` is the store for basic shapes only. These tests
 * pin the editor to the same store the renderer reads, so a commit changes the visible text
 * instead of writing an orphaned `label «…»` that nothing draws.
 */
class DiagramNodeTextReducerTest {

    private val fileName = "diagram-text-test.layout.md"

    private val source = """
        |---
        |screen: diagramTextTest
        |page: Diagram Text Test
        |---
        |
        |# Diagram Text Test id frame_root name «Root»
        |
        |## Diagram: Canvas id canvas
        |
        |Node use-case uc1 «Submit mission» 180 by 80 position 40 40
        |Node rectangle shape1 120 by 60 position 40 200 label «Shape»
    """.trimMargin()

    private fun freshState(): DesignEditorState =
        createDesignEditorState(compileMissionDocuments(listOf(MissionDocumentSource(fileName, source))))

    private fun DesignEditorState.graphOf(nodeId: String): DiagramGraph {
        val node = assertNotNull(document?.nodeById(nodeId), "node $nodeId missing")
        return assertIs<DesignNodeKind.Diagram>(node.kind, "node $nodeId is not a diagram").graph
    }

    private fun DesignEditorState.sourceContent(): String =
        assertNotNull(sources.firstOrNull { it.fileName == fileName }, "source missing").content

    @Test
    fun setNodeLabelOnUseCaseRewritesThePayloadNameTheCanvasDraws() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.SetDiagramNodeLabel("canvas", "uc1", "Вести контакты собственников"),
        )

        val element = assertNotNull(next.graphOf("canvas").nodeById(DiagramNodeId("uc1")))
        val payload = assertIs<UmlUseCaseNode>(element.payload)
        assertEquals(
            "Вести контакты собственников",
            payload.name,
            "the commit must land in the store the renderer draws (payload.name)",
        )
        assertTrue(
            element.labels.isEmpty(),
            "no orphaned node label may be created for a typed payload, got ${element.labels}",
        )
    }

    @Test
    fun setNodeLabelOnUseCaseWritesTheHeadPhraseAndNoOrphanLabel() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.SetDiagramNodeLabel("canvas", "uc1", "Renamed use case"),
        )

        val content = next.sourceContent()
        assertTrue(
            "«Renamed use case»" in content,
            "the new caption belongs in the head position of the node phrase:\n$content",
        )
        assertTrue(
            "label «Renamed use case»" !in content,
            "an orphaned `label «…»` must never be written for a typed payload:\n$content",
        )
        assertTrue(next.diagnostics.none { it.severity == DesignSeverity.Error }, "${next.diagnostics}")
        assertTrue(
            next.diagnostics.none { "kept in memory" in it.message },
            "write-back must not fall back to memory: ${next.diagnostics}",
        )
    }

    @Test
    fun setNodeLabelOnBasicShapeStillUsesTheNodeLabelStore() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.SetDiagramNodeLabel("canvas", "shape1", "Renamed shape"),
        )

        val element = assertNotNull(next.graphOf("canvas").nodeById(DiagramNodeId("shape1")))
        assertEquals(
            "Renamed shape",
            element.labels.firstOrNull()?.text,
            "basic shapes keep carrying their caption in node.labels",
        )
        assertTrue("label «Renamed shape»" in next.sourceContent())
    }

    @Test
    fun switchingSizingToHugIsWrittenBackToTheSource() {
        val next = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.SetDiagramNodeSizing("canvas", "uc1", DiagramNodeSizing.Hug),
        )

        assertEquals(
            DiagramNodeSizing.Hug,
            next.graphOf("canvas").nodeById(DiagramNodeId("uc1"))?.sizing,
        )
        val content = next.sourceContent()
        assertTrue("hug" in content, "hug must reach the source, not stay in memory:\n$content")
        assertTrue("180 by 80" in content, "the size stays as the last measured result:\n$content")
        assertTrue(
            next.diagnostics.none { "kept in memory" in it.message },
            "write-back must not fall back to memory: ${next.diagnostics}",
        )
    }

    @Test
    fun changingPayloadTypePreservesTheAuthoredText() {
        val renamed = reduceDesignEditor(
            freshState(),
            DiagramEditorIntent.SetDiagramNodeLabel("canvas", "uc1", "Keep me"),
        )

        val next = reduceDesignEditor(
            renamed,
            DiagramEditorIntent.SetDiagramNodePayload("canvas", "uc1", UmlClassNode(name = "Class")),
        )

        val element = assertNotNull(next.graphOf("canvas").nodeById(DiagramNodeId("uc1")))
        val payload = assertIs<UmlClassNode>(element.payload)
        assertEquals(
            "Keep me",
            payload.name,
            "switching the payload type must carry the user's text over, not stamp the palette placeholder",
        )
    }
}
