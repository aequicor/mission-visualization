package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.CnlContainerExtension
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.blocks.bodyLinesOrNull
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * The [CnlEmitter] hook for container-extension nodes: renders [node] as a full CNL
 * container section — the `## <Noun>: name id … size position …` heading line (design-node
 * side, existing descriptors) followed by a blank line and the extension's canonical body
 * sentences ([CnlContainerExtension.emitBody]). Deterministic; recompiling the lines with
 * the same registry reproduces the node's payload (grammar–emitter symmetry).
 *
 * Returns null when no registered container extension carries a payload on [node] —
 * callers then fall back to the ordinary [CnlEmitter] subtree emission.
 *
 * [stableHeading] switches the heading to the id-stable structural-insert form
 * (`## Diagram: name «…» id …` — canonical prefix title + explicit `name` phrase), the
 * same shape `NodeSectionWriter` emits for every other node, so a recompile keeps the
 * exact id set and display name.
 */
public fun emitCnlContainerSection(
    node: DesignNode,
    level: Int,
    extensions: SlmExtensionRegistry,
    includeId: Boolean = true,
    stableHeading: Boolean = false,
): List<String>? {
    val rendered = extensions.kinds
        .asSequence()
        .mapNotNull { kind -> extensions.find(kind) as? CnlContainerExtension<*, *> }
        .firstNotNullOfOrNull { extension -> extension.bodyLinesOrNull(node) }
        ?: return null
    val heading = if (stableHeading) {
        CnlEmitter.emitStableHeadingLine(node, level, includeId)
    } else {
        CnlEmitter.emitHeadingLine(node, level, includeId)
    }
    return if (rendered.isEmpty()) listOf(heading) else listOf(heading, "") + rendered
}
