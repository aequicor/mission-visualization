package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.cnl.CnlEmitter
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Renders a [DesignNode] subtree into a fresh CNL `#`-heading section for structural inserts —
 * the counterpart of the surgical [CnlWriter]. Every node becomes one stable heading line
 * (canonical type prefix + explicit `name` phrase + id, so a recompile keeps the same id set),
 * with its properties expressed as inline CNL phrases via [CnlEmitter].
 *
 * Positional framing (blank lines, insert offset) is [SectionWriter]'s job; this object only
 * produces the lines. Structural inserts are gated upstream to expressible subtrees
 * (Frame / Text / simple Shape), all of which [CnlEmitter] round-trips at IR parity.
 */
internal object NodeSectionWriter {

    /** The node's own stable heading line, then each child one level deeper. */
    fun emitSubtree(node: DesignNode, level: Int): List<String> =
        CnlEmitter.emitStableSubtree(node, level, includeId = true)
}
