package io.aequicor.visualization.engine.frontend.diagnostics

import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.SourceLocation

/**
 * Accumulates [DesignDiagnostic]s for one `compileSlm` run.
 *
 * Locations are reported as `SourceLocation(file = fileName, line = line)`; the
 * human-readable block path (e.g. the typed-block kind) travels in the `pointer`
 * field with a `#` prefix: `#layout`. Column arguments are accepted for call-site
 * clarity but only the line is persisted — [SourceLocation] carries file/line.
 */
class DiagnosticCollector(val fileName: String = "") {
    private val collected = mutableListOf<DesignDiagnostic>()

    val diagnostics: List<DesignDiagnostic> get() = collected

    val hasErrors: Boolean get() = collected.any { it.severity == DesignSeverity.Error }

    fun error(message: String, line: Int = 0, column: Int = 0, blockPath: String = "") =
        report(DesignSeverity.Error, message, line, blockPath)

    fun error(message: String, span: SlmSourceSpan, blockPath: String = "") =
        report(DesignSeverity.Error, message, span.startLine, blockPath)

    fun warning(message: String, line: Int = 0, column: Int = 0, blockPath: String = "") =
        report(DesignSeverity.Warning, message, line, blockPath)

    fun warning(message: String, span: SlmSourceSpan, blockPath: String = "") =
        report(DesignSeverity.Warning, message, span.startLine, blockPath)

    fun info(message: String, line: Int = 0, column: Int = 0, blockPath: String = "") =
        report(infoSeverity, message, line, blockPath)

    fun info(message: String, span: SlmSourceSpan, blockPath: String = "") =
        report(infoSeverity, message, span.startLine, blockPath)

    private fun report(severity: DesignSeverity, message: String, line: Int, blockPath: String) {
        collected += DesignDiagnostic(
            severity = severity,
            message = message,
            location = SourceLocation(
                pointer = if (blockPath.isEmpty()) "" else "#$blockPath",
                file = fileName,
                line = line,
            ),
        )
    }

    private companion object {
        /** engine/ir has no Info severity yet; hints are downgraded to warnings. */
        val infoSeverity: DesignSeverity = DesignSeverity.Warning
    }
}
