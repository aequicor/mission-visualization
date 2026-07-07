package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan

/**
 * Opaque node-to-source index built by the IR normalizer and consumed by SlmPatcher
 * (этап 8). Only nodes that own a markdown anchor element are addressable; nodes
 * spliced from ```ir fences are recorded separately so edits can be redirected to
 * the embedded JSON.
 */
class SlmEditIndex internal constructor(
    internal val anchorOwners: Map<String, SlmSourceSpan>,
    internal val irSpliceNodes: Set<String>,
) {
    companion object {
        val Empty: SlmEditIndex = SlmEditIndex(emptyMap(), emptySet())
    }
}
