package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.text.ApproximateDiagramTextMeasurer
import kotlin.test.Test
import kotlin.test.assertTrue

/** Repro of the inspector's Fit-to-text handler for a stereotyped component (E2E finding). */
class FitComponentRepro {
    @Test
    fun fitToTextOnAStereotypedComponentActuallyResizes() {
        val id = DiagramNodeId("mod_diagrams")
        val node = DiagramNode(
            id = id, x = 440.0, y = 560.0, width = 180.0, height = 56.0,
            payload = UmlComponentNode(name = "Диаграммы проекта", stereotype = "core + compose"),
        )
        val fitted = DiagramGraph().withNode(node)
            .fitNodeToText(id, ApproximateDiagramTextMeasurer(), force = true)
            .nodeById(id)!!
        println("FITTED: ${fitted.width} x ${fitted.height} (was 180 x 56)")
        assertTrue(fitted.bounds != node.bounds, "fit must change an oversized component")
    }
}
