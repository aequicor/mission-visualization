package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.domain.MissionDocumentSource
import io.aequicor.visualization.editor.domain.compileMissionDocuments
import io.aequicor.visualization.editor.presentation.DiagramEditorIntent
import io.aequicor.visualization.editor.presentation.createDesignEditorState
import io.aequicor.visualization.editor.presentation.reduceDesignEditor
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The exact dispatch the inspector's Fit-to-text button makes, run through the real reducer. */
class FitResizeIntentRepro {
    @Test
    fun resizeDiagramNodeIntentActuallyResizesAndWritesBack() {
        val source = MissionDocumentSource(
            "uml.layout.md",
            """
            |---
            |screen: welcomeUml
            |page: Architecture
            |---
            |
            |# Architecture id frame_uml name «Architecture»
            |
            |## Diagram: id module_map name «Module Map» 640 by 700 position 48 130
            |
            |Node component mod_diagrams «Диаграммы проекта» stereotype «core + compose» 180 by 56 position 440 560
            """.trimMargin(),
        )
        val state = createDesignEditorState(compileMissionDocuments(listOf(source)))

        val next = reduceDesignEditor(
            state,
            DiagramEditorIntent.ResizeDiagramNode(
                nodeId = "module_map",
                elementId = "mod_diagrams",
                x = 462.3, y = 571.9, width = 135.34, height = 32.25,
            ),
        )

        val node = assertNotNull(next.document?.nodeById("module_map"))
        val graph = (node.kind as DesignNodeKind.Diagram).graph
        val element = assertNotNull(graph.nodeById(DiagramNodeId("mod_diagrams")))
        assertEquals(135.34, element.width, 0.01, "resize must land in the graph")
        val content = assertNotNull(next.sources.firstOrNull()).content
        kotlin.test.assertTrue("135.34 by 32.25" in content, "resize must write back:\n$content")
    }
}
