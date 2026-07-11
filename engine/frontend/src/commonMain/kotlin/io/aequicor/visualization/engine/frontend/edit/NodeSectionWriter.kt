package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.cnl.CnlEmitter
import io.aequicor.visualization.engine.frontend.cnl.emitCnlContainerSection
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Renders a [DesignNode] subtree into a fresh CNL `#`-heading section for structural inserts —
 * the counterpart of the surgical [CnlWriter]. Every node becomes one stable heading line
 * (canonical type prefix + explicit `name` phrase + id, so a recompile keeps the same id set),
 * with its properties expressed as inline CNL phrases via [CnlEmitter]. A node whose payload
 * belongs to a CNL-container extension (e.g. a diagram graph) emits as a full CNL container
 * section — stable heading plus the extension's canonical body sentences. Extension inserts
 * are CNL-only: a payload of a non-container extension is not emittable and is dropped
 * (gated upstream to expressible subtrees).
 *
 * Positional framing (blank lines, insert offset) is [SectionWriter]'s job; this object only
 * produces the lines. Structural inserts are gated upstream to expressible subtrees
 * (Frame / Text / simple Shape / container-extension nodes), all of which round-trip at IR parity.
 */
internal object NodeSectionWriter {

    /** The node's own stable heading line (+ container body), then each child one level deeper. */
    fun emitSubtree(
        node: DesignNode,
        level: Int,
        extensions: SlmExtensionRegistry = SlmExtensionRegistry.Empty,
    ): List<String> {
        val container = emitCnlContainerSection(node, level, extensions, includeId = true, stableHeading = true)
        val lines = container?.toMutableList()
            ?: mutableListOf(CnlEmitter.emitStableHeadingLine(node, level, includeId = true))
        node.children.forEach { child ->
            lines += ""
            lines += emitSubtree(child, level + 1, extensions)
        }
        return lines
    }
}
