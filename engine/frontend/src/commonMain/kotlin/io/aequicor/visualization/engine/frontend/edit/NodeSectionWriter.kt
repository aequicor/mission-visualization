package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.cnl.CnlEmitter
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Renders a [DesignNode] subtree into a fresh CNL `#`-heading section for structural inserts —
 * the counterpart of the surgical [CnlWriter]. Every node becomes one stable heading line
 * (canonical type prefix + explicit `name` phrase + id, so a recompile keeps the same id set),
 * with its properties expressed as inline CNL phrases via [CnlEmitter]. A node carrying a
 * registered extension payload (e.g. a diagram graph) gets that `<kind>:` typed block emitted
 * contiguously right under its heading line, so the recompile reapplies the payload.
 *
 * Positional framing (blank lines, insert offset) is [SectionWriter]'s job; this object only
 * produces the lines. Structural inserts are gated upstream to expressible subtrees
 * (Frame / Text / simple Shape / extension-payload nodes), all of which round-trip at IR parity.
 */
internal object NodeSectionWriter {

    /** The node's own stable heading line (+ extension blocks), then each child one level deeper. */
    fun emitSubtree(
        node: DesignNode,
        level: Int,
        extensions: SlmExtensionRegistry = SlmExtensionRegistry.Empty,
    ): List<String> {
        val lines = mutableListOf(CnlEmitter.emitStableHeadingLine(node, level, includeId = true))
        // Typed extension blocks (e.g. `diagram:`) sit directly under the heading, contiguous —
        // a blank line inside a typed block would terminate it at parse time.
        extensions.blockTextsFor(node).forEach { block -> lines += block.lines() }
        node.children.forEach { child ->
            lines += ""
            lines += emitSubtree(child, level + 1, extensions)
        }
        return lines
    }
}
