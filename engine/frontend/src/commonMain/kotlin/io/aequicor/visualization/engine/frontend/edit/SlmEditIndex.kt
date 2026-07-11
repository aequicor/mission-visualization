package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan

/**
 * Opaque node-to-source index built by the IR normalizer and consumed by SlmPatcher
 * (этап 8). Only nodes that own a markdown anchor element are addressable; every other
 * node degrades to the generic unaddressable path.
 */
class SlmEditIndex internal constructor(
    internal val anchorOwners: Map<String, SlmSourceSpan>,
    /** Nodes authored as a CNL element sentence; their edits route through [CnlWriter]. */
    internal val cnlOwners: Map<String, SlmSourceSpan> = emptyMap(),
) {
    companion object {
        val Empty: SlmEditIndex = SlmEditIndex(emptyMap())
    }
}

/**
 * Diagnostic for a node that cannot be addressed in the SLM source: it owns neither a CNL
 * sentence ([SlmEditIndex.cnlOwners]) nor a structural heading anchor ([SlmEditIndex.anchorOwners]),
 * so [SlmPatcher] falls the edit back to an in-memory-only change (source untouched).
 */
internal fun unaddressableMessage(nodeId: String, editIndex: SlmEditIndex): String =
    "Node \"$nodeId\" has no addressable source anchor; promote it to its own " +
        "heading or list item, or edit the parent node"
