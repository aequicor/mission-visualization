package io.aequicor.visualization.subsystems.diagrams.text

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph

/**
 * One parser message anchored to a source line (1-based). Used both for hard errors
 * (in [TextDiagramResult.Failure]) and for skipped-construct warnings
 * (in [TextDiagramResult.Success]).
 */
data class TextDiagramDiagnostic(
    val line: Int,
    val message: String,
)

/**
 * Outcome of a text-to-diagram parse ([mermaidToDiagram] / [plantUmlToDiagram]).
 * Parsers never throw on malformed input — they report diagnostics instead.
 */
sealed interface TextDiagramResult {

    /** Parsed and laid out successfully; [warnings] list constructs that were skipped. */
    data class Success(
        val graph: DiagramGraph,
        val warnings: List<TextDiagramDiagnostic> = emptyList(),
    ) : TextDiagramResult

    /** The source could not be parsed; [diagnostics] explain why, with line numbers. */
    data class Failure(
        val diagnostics: List<TextDiagramDiagnostic>,
    ) : TextDiagramResult
}
