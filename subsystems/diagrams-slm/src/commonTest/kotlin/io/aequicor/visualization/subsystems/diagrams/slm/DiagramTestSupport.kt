package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal fun compileWithDiagrams(source: String): SlmCompileResult =
    compileSlm(source, SlmCompileOptions(extensions = DiagramSlmExtension.registry()))

internal fun SlmCompileResult.diagramGraphOf(nodeId: String): DiagramGraph {
    val document = assertNotNull(document, "document did not compile: $diagnostics")
    val node = assertNotNull(document.nodeById(nodeId), "node '$nodeId' not found")
    assertEquals("diagram", node.type)
    return (node.kind as DesignNodeKind.Diagram).graph
}
