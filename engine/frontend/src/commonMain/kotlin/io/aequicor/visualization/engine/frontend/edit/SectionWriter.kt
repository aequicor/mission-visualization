package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.HeadingBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Computes the [TextOp]s for structural edits (subtree insert, section delete) against a FRESH
 * throwaway parse of [source], guarded by the fingerprint check upstream so the recorded anchor
 * spans line up with this CST.
 *
 * A heading section's *footprint* runs from its `#` line down to (but excluding) the next
 * same-or-shallower heading, or to end-of-source when it is the last section. Deletes drop that
 * whole range (the leading blank line before the heading stays, keeping siblings separated);
 * inserts frame the rendered subtree ([NodeSectionWriter], CNL sentences) with exactly one blank
 * line on each side so the parser keeps it as its own block instead of folding it into an adjacent
 * paragraph (risk noted in [EditTargetResolver]). Only heading lines and their contiguous blocks
 * are relocated.
 */
internal class SectionWriter(
    private val source: String,
    private val lineIndex: LineIndex,
    fileName: String,
    private val extensions: SlmExtensionRegistry = SlmExtensionRegistry.Empty,
) {
    private val document = SlmMarkdownParser(DiagnosticCollector(fileName)).parse(source)
    private val headings: List<HeadingBlock> = document.blocks.filterIsInstance<HeadingBlock>()

    /** Deletes the heading section owning [anchorSpan]. */
    fun delete(anchorSpan: SlmSourceSpan): WritePlan {
        val heading = headingAt(anchorSpan.startLine)
            ?: return WritePlan.Failed(
                "Node has no heading section to delete; only heading-anchored nodes are structurally editable",
                anchorSpan.startLine,
            )
        if (heading.level <= 1) {
            return WritePlan.Failed("The screen root section cannot be deleted from the source", heading.span.startLine)
        }
        val start = lineIndex.lineStartOffset(heading.span.startLine)
        val end = lineIndex.lineStartOffset(footprintEndLine(heading))
        return WritePlan.Ops(listOf(TextOp(start, end, "")))
    }

    /** Re-emits a subtree at the exact heading level and byte footprint of its current section. */
    fun replace(anchorSpan: SlmSourceSpan, subtree: DesignNode): WritePlan {
        val heading = headingAt(anchorSpan.startLine)
            ?: return WritePlan.Failed(
                "Node has no heading section to replace; only heading-anchored nodes are structurally editable",
                anchorSpan.startLine,
            )
        if (heading.level <= 1) {
            return WritePlan.Failed("The screen root section cannot be replaced", heading.span.startLine)
        }
        val start = lineIndex.lineStartOffset(heading.span.startLine)
        val end = lineIndex.lineStartOffset(footprintEndLine(heading))
        val text = frameInsert(NodeSectionWriter.emitSubtree(subtree, heading.level, extensions), start)
        return WritePlan.Ops(listOf(TextOp(start, end, text)))
    }

    /**
     * Inserts [subtree] under the parent owning [parentSpan]. When [afterSiblingSpan] is given the
     * section lands right after that sibling's footprint; otherwise it appends at the end of the
     * parent's footprint.
     */
    fun insert(parentSpan: SlmSourceSpan, subtree: DesignNode, afterSiblingSpan: SlmSourceSpan?): WritePlan {
        val parentHeading = headingAt(parentSpan.startLine)
        // A non-heading anchor (whole-document root span) is the screen root at level 1.
        val parentLevel = parentHeading?.level ?: 1
        val childLevel = parentLevel + 1
        if (childLevel > 6) {
            return WritePlan.Failed(
                "Inserting here would exceed the maximum heading depth of 6",
                parentSpan.startLine,
            )
        }

        val insertBeforeLine = when {
            afterSiblingSpan != null -> {
                val sibling = headingAt(afterSiblingSpan.startLine)
                    ?: return WritePlan.Failed(
                        "The sibling to insert after is not a heading section",
                        afterSiblingSpan.startLine,
                    )
                footprintEndLine(sibling)
            }
            parentHeading != null -> footprintEndLine(parentHeading)
            else -> parentSpan.endLine + 1
        }
        val offset = lineIndex.lineStartOffset(insertBeforeLine)
        val text = frameInsert(NodeSectionWriter.emitSubtree(subtree, childLevel, extensions), offset)
        return WritePlan.Ops(listOf(TextOp(offset, offset, text)))
    }

    /**
     * Moves the heading section owning [subtreeSpan] to become a child of the parent owning
     * [newParentSpan], re-leveling every heading line in the moved footprint by the depth delta.
     * Emitted as a delete of the old footprint plus a re-leveled, blank-line-framed insert at the
     * new location (after [afterSiblingSpan] when given, else appended at the end of the parent's
     * footprint). Non-overlapping ops fed through the shared [applyOps]. Refuses the screen root,
     * a non-heading anchor at either end, a re-level past ATX depth 6, or an insert that would land
     * inside the moved footprint (moving a section into itself).
     */
    fun move(subtreeSpan: SlmSourceSpan, newParentSpan: SlmSourceSpan, afterSiblingSpan: SlmSourceSpan?): WritePlan {
        val heading = headingAt(subtreeSpan.startLine)
            ?: return WritePlan.Failed(
                "Node has no heading section to move; only heading-anchored nodes are structurally movable",
                subtreeSpan.startLine,
            )
        if (heading.level <= 1) {
            return WritePlan.Failed("The screen root section cannot be moved from the source", heading.span.startLine)
        }
        val newParentHeading = headingAt(newParentSpan.startLine)
        // A non-heading anchor (whole-document root span) is the screen root at level 1.
        val childLevel = (newParentHeading?.level ?: 1) + 1
        val delta = childLevel - heading.level

        val startLine = heading.span.startLine
        val endLine = footprintEndLine(heading)
        val movedHeadings = headings.filter { it.span.startLine in startLine until endLine }
        val deepestNewLevel = movedHeadings.maxOf { it.level } + delta
        if (deepestNewLevel > 6) {
            return WritePlan.Failed(
                "Moving here would exceed the maximum heading depth of 6",
                newParentSpan.startLine,
            )
        }

        val insertBeforeLine = when {
            afterSiblingSpan != null -> {
                val sibling = headingAt(afterSiblingSpan.startLine)
                    ?: return WritePlan.Failed(
                        "The sibling to move after is not a heading section",
                        afterSiblingSpan.startLine,
                    )
                footprintEndLine(sibling)
            }
            newParentHeading != null -> footprintEndLine(newParentHeading)
            else -> newParentSpan.endLine + 1
        }

        val deleteStart = lineIndex.lineStartOffset(startLine)
        val deleteEnd = lineIndex.lineStartOffset(endLine)
        val insertOffset = lineIndex.lineStartOffset(insertBeforeLine)
        if (insertOffset > deleteStart && insertOffset < deleteEnd) {
            return WritePlan.Failed("A section cannot be moved inside itself", newParentSpan.startLine)
        }

        val headingStartLines = movedHeadings.map { it.span.startLine }.toSet()
        val sectionLines = (startLine until endLine)
            .map { line -> lineIndex.lineText(line).let { if (line in headingStartLines) relevelHeading(it, delta) else it } }
            .dropLastWhile { it.isBlank() }
        val text = frameInsert(sectionLines, insertOffset)
        // Reparenting a node under its immediately preceding sibling appends at exactly the
        // moved section's current start. Represent that adjacent delete+insert as one replacement;
        // two ops at the same offset would otherwise look overlapping even though the transform is
        // perfectly well-defined (the bytes stay in place, only heading depth changes).
        if (insertOffset == deleteStart) {
            return WritePlan.Ops(listOf(TextOp(deleteStart, deleteEnd, text)))
        }
        return WritePlan.Ops(listOf(TextOp(deleteStart, deleteEnd, ""), TextOp(insertOffset, insertOffset, text)))
    }

    // --- footprint arithmetic ---

    /** Re-levels a heading line's leading `#`-run by [delta], clamped to the ATX range 1..6. */
    private fun relevelHeading(line: String, delta: Int): String {
        if (delta == 0) return line
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes == 0) return line
        return "#".repeat((hashes + delta).coerceIn(1, 6)) + line.substring(hashes)
    }

    private fun headingAt(line: Int): HeadingBlock? = headings.firstOrNull { it.span.startLine == line }

    /**
     * First line NOT belonging to [heading]'s section: the start line of the next heading whose
     * level is <= [heading]'s (a sibling or an ancestor's next child), or one past the last block
     * (which [LineIndex] clamps to end-of-source) when this is the trailing section.
     */
    private fun footprintEndLine(heading: HeadingBlock): Int =
        headings.firstOrNull { it.span.startLine > heading.span.startLine && it.level <= heading.level }
            ?.span?.startLine
            ?: ((document.blocks.lastOrNull()?.span?.endLine ?: lineIndex.lineCount) + 1)

    /**
     * Frames [sectionLines] with exactly one blank line before (unless already present or at the
     * very start) and one blank line after (unless at end-of-source), so both the heading and the
     * following content stay cleanly separated regardless of the source's existing whitespace.
     */
    private fun frameInsert(sectionLines: List<String>, offset: Int): String = buildString {
        if (offset > 0) {
            val existingBreaks = trailingNewlines(offset)
            repeat((2 - existingBreaks).coerceAtLeast(0)) { append('\n') }
        }
        sectionLines.forEach { append(it).append('\n') }
        if (offset < source.length) append('\n')
    }

    /** Count of consecutive `\n` immediately before [offset] (capped at 2 — all we need). */
    private fun trailingNewlines(offset: Int): Int {
        var count = 0
        var index = offset - 1
        while (index >= 0 && count < 2 && source[index] == '\n') {
            count++
            index--
        }
        return count
    }
}
