package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.SlmCompileResult
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.fnv1a64
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Applies one surgical [SlmEdit] to the SLM source text (design section J). SLM
 * text is the source of truth: the flow is edit intent -> `applySlmEdit` -> new
 * source -> [compileSlm] -> new IR -> re-render. The patcher never serializes
 * markdown from the IR and never creates or deletes nodes, so generated ids stay
 * stable across edit -> recompile.
 *
 * [compiled] must be the compile of exactly [source]: a fingerprint mismatch fails
 * hard ("Stale compile result"). After a successful apply the caller must
 * recompile — [SlmEditResult] deliberately carries no document.
 */
fun applySlmEdit(
    source: String,
    edit: SlmEdit,
    compiled: SlmCompileResult,
    patchedNode: DesignNode? = null,
    options: SlmCompileOptions = SlmCompileOptions(),
): SlmEditResult {
    staleResult(source, compiled)?.let { return it }
    return applyResolved(source, edit, compiled.editIndex, DefaultFileName, patchedNode, options.extensions)
}

/**
 * Applies [edits] sequentially with re-resolution: apply edit 1 -> internal
 * [compileSlm] recompile -> resolve edit 2 against the fresh result -> ... This is
 * unconditionally correct for same-anchor interactions (width + height, or a second
 * edit landing in a block the first created). A failing middle edit aborts the
 * whole batch: partial diagnostics, `newSource = null`. [options] configure the
 * internal recompiles.
 */
fun applySlmEdits(
    source: String,
    edits: List<SlmEdit>,
    compiled: SlmCompileResult,
    options: SlmCompileOptions = SlmCompileOptions(),
    patchedNode: DesignNode? = null,
): SlmEditResult {
    staleResult(source, compiled)?.let { return it }
    if (edits.isEmpty()) return SlmEditResult(source, null, emptyList())
    var currentSource = source
    var currentIndex = compiled.editIndex
    val diagnostics = mutableListOf<DesignDiagnostic>()
    edits.forEachIndexed { index, edit ->
        val result = applyResolved(currentSource, edit, currentIndex, options.fileName, patchedNode, options.extensions)
        diagnostics += result.diagnostics
        currentSource = result.newSource
            ?: return SlmEditResult(newSource = null, appliedRange = null, diagnostics = diagnostics)
        if (index < edits.lastIndex) {
            val recompiled = compileSlm(currentSource, options)
            currentIndex = recompiled.editIndex
        }
    }
    return SlmEditResult(
        newSource = currentSource,
        appliedRange = diffRange(source, currentSource),
        diagnostics = diagnostics,
    )
}

/** Result of a patch attempt; [newSource] is null when nothing was applied. */
data class SlmEditResult(
    val newSource: String?,
    /** Range of the rewritten bytes, in NEW source coordinates. */
    val appliedRange: SlmTextRange?,
    val diagnostics: List<DesignDiagnostic>,
) {
    val isApplied: Boolean get() = newSource != null
}

/** Half-open `[startOffset, endOffset)` character range. */
data class SlmTextRange(val startOffset: Int, val endOffset: Int)

private const val DefaultFileName: String = "document.layout.md"

// --- single-edit application ---

private fun staleResult(source: String, compiled: SlmCompileResult): SlmEditResult? {
    if (fnv1a64(source) == compiled.sourceFingerprint) return null
    val diagnostics = DiagnosticCollector(DefaultFileName)
    diagnostics.error("Stale compile result: recompile before editing")
    return SlmEditResult(newSource = null, appliedRange = null, diagnostics = diagnostics.diagnostics)
}

private fun applyResolved(
    source: String,
    edit: SlmEdit,
    editIndex: SlmEditIndex,
    fileName: String,
    patchedNode: DesignNode? = null,
    extensions: SlmExtensionRegistry = SlmExtensionRegistry.Empty,
): SlmEditResult {
    val diagnostics = DiagnosticCollector(fileName)

    fun failed(): SlmEditResult =
        SlmEditResult(newSource = null, appliedRange = null, diagnostics = diagnostics.diagnostics)

    val lineIndex = LineIndex(source)
    // Structural edits (create/delete/move) synthesize or drop whole heading sections and
    // resolve their footprint arithmetically; attribute edits patch a CNL sentence in place.
    var anchorLine = 0
    // Every attribute edit routes through CnlWriter: a CNL-authored node patches its own sentence
    // (tier-1 replace / tier-2 append / tier-3 whole-sentence re-emit from the patched node). The
    // legacy YAML typed-block writers are gone, so a node that is NOT authored as a CNL sentence —
    // or a CNL edit even tier-3 cannot express — fails cleanly to an in-memory fallback (the
    // reducer's fidelity veto keeps it in-memory, source byte-identical, non-corrupting).
    val cnlSpan = if (edit !is StructuralSlmEdit) editIndex.cnlOwners[edit.nodeId] else null
    val plan = if (cnlSpan != null) {
        anchorLine = cnlSpan.startLine
        CnlWriter.plan(source, cnlSpan, edit, lineIndex, patchedNode)
    } else if (edit is StructuralSlmEdit) {
        structuralPlan(edit, editIndex, source, lineIndex, fileName, extensions)
    } else {
        WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), anchorLine)
    }
    val ops = when (plan) {
        is WritePlan.Failed -> {
            diagnostics.error(plan.message, plan.line)
            return failed()
        }
        is WritePlan.Ops -> plan.ops
    }
    if (ops.isEmpty()) {
        return SlmEditResult(newSource = source, appliedRange = null, diagnostics = diagnostics.diagnostics)
    }
    val ordered = ops.sortedBy { it.start }
    ordered.zipWithNext().forEach { (previous, next) ->
        if (next.start < previous.end) {
            diagnostics.error("Internal patcher error: overlapping write operations", anchorLine)
            return failed()
        }
    }
    val (newSource, range) = applyOps(source, ordered)
    return SlmEditResult(newSource = newSource, appliedRange = range, diagnostics = diagnostics.diagnostics)
}

/**
 * Resolves a structural edit's anchor(s) via [SlmEditIndex.anchorOwners] and hands the footprint
 * arithmetic to [SectionWriter]. An unaddressable node (prose segment, ir-splice, missing sibling)
 * fails with the same "promote it to its own heading / edit the embedded JSON" guidance as the
 * attribute path, so the caller can fall back to an in-memory edit.
 */
private fun structuralPlan(
    edit: StructuralSlmEdit,
    editIndex: SlmEditIndex,
    source: String,
    lineIndex: LineIndex,
    fileName: String,
    extensions: SlmExtensionRegistry = SlmExtensionRegistry.Empty,
): WritePlan {
    val writer = SectionWriter(source, lineIndex, fileName, extensions)
    return when (edit) {
        is DeleteSection -> {
            val span = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            writer.delete(span)
        }
        is ReplaceSection -> {
            val span = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            writer.replace(span, edit.subtree)
        }
        is InsertChildSubtree -> {
            val parentSpan = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            val afterSpan = edit.afterSiblingId?.let { sibling ->
                editIndex.anchorOwners[sibling]
                    ?: return WritePlan.Failed(unaddressableMessage(sibling, editIndex), 0)
            }
            writer.insert(parentSpan, edit.subtree, afterSpan)
        }
        is MoveSection -> {
            val subtreeSpan = editIndex.anchorOwners[edit.nodeId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.nodeId, editIndex), 0)
            val parentSpan = editIndex.anchorOwners[edit.newParentId]
                ?: return WritePlan.Failed(unaddressableMessage(edit.newParentId, editIndex), 0)
            val afterSpan = edit.afterSiblingId?.let { sibling ->
                editIndex.anchorOwners[sibling]
                    ?: return WritePlan.Failed(unaddressableMessage(sibling, editIndex), 0)
            }
            writer.move(subtreeSpan, parentSpan, afterSpan)
        }
    }
}

private fun applyOps(source: String, ordered: List<TextOp>): Pair<String, SlmTextRange> {
    val builder = StringBuilder(source.length + ordered.sumOf { it.text.length })
    var cursor = 0
    ordered.forEach { op ->
        builder.append(source, cursor, op.start)
        builder.append(op.text)
        cursor = op.end
    }
    builder.append(source, cursor, source.length)

    var delta = 0
    var start = Int.MAX_VALUE
    var end = -1
    ordered.forEach { op ->
        val newStart = op.start + delta
        start = minOf(start, newStart)
        end = maxOf(end, newStart + op.text.length)
        delta += op.text.length - (op.end - op.start)
    }
    return builder.toString() to SlmTextRange(start, end)
}

/** Smallest range in NEW coordinates outside of which [old] and [new] agree. */
private fun diffRange(old: String, new: String): SlmTextRange? {
    if (old == new) return null
    val shortest = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < shortest && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < shortest - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    return SlmTextRange(prefix, new.length - suffix)
}
