package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind

/**
 * Rewrites the WHOLE SET of typed-block entries of one [kind] bound to a node's anchor. Unlike
 * `YamlPathWriter` (which merges into the last entry of a kind and cannot delete or address the
 * 2nd-of-N), this addresses each entry by its own line span, so it handles create / replace /
 * edit-2nd-of-3 / delete-all uniformly and leaves interspersed sibling `node:`/`layout:`/`style:`
 * lines untouched. Used by `SetInteractions` (N sibling `interaction:` entries) and `SetMotion`
 * (a single last-win `motion:` entry, list of 0 or 1).
 *
 * The new entries are re-emitted in list order, replacing the first existing entry's span in place
 * and deleting the remaining entries; an empty [blocks] deletes them all. When none exist yet the
 * block is appended into the anchor's last typed group (or at the anchor insertion point).
 */
internal fun typedBlockSetPlan(
    source: String,
    target: EditTarget,
    lineIndex: LineIndex,
    kind: TypedBlockKind,
    blocks: List<YamlPayload.Mapping>,
): WritePlan {
    val existing = target.boundGroups.flatMap { it.entries }.filter { it.kind == kind }

    if (existing.isEmpty()) {
        if (blocks.isEmpty()) return WritePlan.Ops(emptyList())
        val lastGroup = target.boundGroups.lastOrNull()
        val op = if (lastGroup != null) {
            val indent = lineIndex.indentOf(lastGroup.span.startLine)
            insertLinesAt(lineIndex, lastGroup.span.endLine + 1, renderEntries(kind, blocks, indent), blankBefore = false)
        } else {
            insertLinesAt(
                lineIndex,
                target.insertion.line,
                renderEntries(kind, blocks, target.insertion.indent),
                blankBefore = target.insertion.blankLineBefore,
            )
        }
        return WritePlan.Ops(listOf(op))
    }

    val first = existing.first()
    val indent = lineIndex.indentOf(first.span.startLine)
    val lines = renderEntries(kind, blocks, indent)
    val ops = mutableListOf<TextOp>()

    val firstStart = lineIndex.lineStartOffset(first.span.startLine)
    val firstEnd = minOf(lineIndex.lineStartOffset(first.span.endLine + 1), source.length)
    val keptNewline = firstEnd > firstStart && source[firstEnd - 1] == '\n'
    val replacement = if (lines.isEmpty()) "" else lines.joinToString("\n") + if (keptNewline) "\n" else ""
    ops += TextOp(firstStart, firstEnd, replacement)

    existing.drop(1).forEach { entry ->
        val start = lineIndex.lineStartOffset(entry.span.startLine)
        val end = minOf(lineIndex.lineStartOffset(entry.span.endLine + 1), source.length)
        ops += TextOp(start, end, "")
    }
    return WritePlan.Ops(ops)
}

private fun renderEntries(kind: TypedBlockKind, blocks: List<YamlPayload.Mapping>, indent: Int): List<String> =
    blocks.flatMap { SlmBlockRenderer.entryLines(kind.key, it, indent) }

/** Mirrors `YamlPathWriter.insertAt`: line-start insertion with optional leading blank / EOF break. */
private fun insertLinesAt(
    lineIndex: LineIndex,
    beforeLine: Int,
    lines: List<String>,
    blankBefore: Boolean,
): TextOp {
    val offset = lineIndex.lineStartOffset(beforeLine)
    val needsLeadingBreak = beforeLine > lineIndex.lineCount && !lineIndex.endsWithNewline
    val text = buildString {
        if (needsLeadingBreak) append('\n')
        if (blankBefore) append('\n')
        lines.forEach { line -> append(line).append('\n') }
    }
    return TextOp(offset, offset, text)
}
