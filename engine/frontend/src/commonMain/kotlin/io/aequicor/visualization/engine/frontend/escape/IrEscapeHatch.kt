package io.aequicor.visualization.engine.frontend.escape

import io.aequicor.visualization.engine.frontend.ast.IrSpliceBlock
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.normalize.SlugGenerator
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.serialization.DesignNodeParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignNode

/**
 * ```ir fence escape hatch: parses the embedded JSON via engine/ir
 * [parseDesignNode] and splices the node as a standalone child at markdown
 * order. Parser diagnostics are re-located to the fence (original JSON pointer
 * appended); malformed JSON reports an error, skips the node and compilation
 * continues. Spliced nodes are exempt from patch merging.
 */
internal class IrEscapeHatch(
    private val diagnostics: DiagnosticCollector,
    private val fileName: String,
) {
    /** Returns the spliced node with a document-unique id, or null on failure. */
    fun splice(block: IrSpliceBlock, slugGenerator: SlugGenerator): DesignNode? {
        val result = parseDesignNode(
            source = block.json,
            file = fileName,
            line = block.contentStartLine,
        )
        relocate(result.diagnostics, block.contentStartLine)
        val node = (result as? DesignNodeParseResult.Success)?.node ?: return null
        val id = slugGenerator.idFor(
            explicitId = node.id.takeIf { it.isNotBlank() },
            name = node.name,
            role = node.role,
            kind = node.type.ifBlank { "ir" },
            line = block.contentStartLine,
        )
        return if (id == node.id) node else node.copy(id = id)
    }

    /** Re-emits ir-parser diagnostics at the fence line, keeping the JSON pointer. */
    private fun relocate(parsed: List<DesignDiagnostic>, line: Int) {
        parsed.forEach { diagnostic ->
            val pointer = diagnostic.location?.pointer.orEmpty()
            val message = if (pointer.isEmpty()) {
                diagnostic.message
            } else {
                "${diagnostic.message} (at $pointer)"
            }
            when (diagnostic.severity) {
                DesignSeverity.Error -> diagnostics.error(message, line, blockPath = "ir")
                else -> diagnostics.warning(message, line, blockPath = "ir")
            }
        }
    }
}
