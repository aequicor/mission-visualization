package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector

/**
 * Diagnostics context of one typed block: messages are reported at
 * `file:line#<kind>` — the block path is the kind key of the entry. Public because
 * registry extensions (`TypedBlockExtension`) receive it in `validate`.
 */
class BlockReading(
    val diagnostics: DiagnosticCollector,
    val blockPath: String,
) {
    fun error(message: String, line: Int = 0) =
        diagnostics.error(message, line, blockPath = blockPath)

    fun warning(message: String, line: Int = 0) =
        diagnostics.warning(message, line, blockPath = blockPath)

    fun info(message: String, line: Int = 0) =
        diagnostics.info(message, line, blockPath = blockPath)
}
