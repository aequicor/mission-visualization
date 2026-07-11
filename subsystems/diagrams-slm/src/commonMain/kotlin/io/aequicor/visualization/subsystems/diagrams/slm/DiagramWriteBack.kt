package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.markdown.TypedAttributeBlock
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph

/** One text splice; offsets are half-open `[startOffset, endOffset)` in the OLD source. */
public data class DiagramTextOp(
    val startOffset: Int,
    val endOffset: Int,
    val replacement: String,
)

/**
 * Result of planning a `diagram:` block write-back — either a list of non-overlapping
 * [DiagramTextOp]s or a failure with a human-readable reason (the caller keeps the edit
 * in memory, mirroring the editor's structural-edit fallback).
 */
public sealed interface DiagramWriteBackPlan {

    public data class Ops(val ops: List<DiagramTextOp>) : DiagramWriteBackPlan {
        /** Applies the ops (already ordered, non-overlapping) to [source]. */
        public fun applyTo(source: String): String {
            val ordered = ops.sortedBy { it.startOffset }
            return buildString(source.length) {
                var cursor = 0
                ordered.forEach { op ->
                    append(source, cursor, op.startOffset)
                    append(op.replacement)
                    cursor = op.endOffset
                }
                append(source, cursor, source.length)
            }
        }
    }

    public data class Failed(val message: String) : DiagramWriteBackPlan
}

/**
 * Plans the surgical replacement of the `diagram:` block bound to node [nodeId] with the
 * canonical emission of [graph] (see [DiagramYamlWriter.blockText]). Compatible with the
 * `SlmPatcher`/`typedBlockSetPlan` approach: whole-entry replacement addressed by the
 * entry's own line span, sibling `node:`/`layout:`/`style:` entries untouched.
 *
 * - When the node already has a `diagram:` entry, its full span is replaced in place
 *   (indentation preserved).
 * - When it does not, the block is inserted right below the node's last typed entry
 *   (same indentation).
 * - Unaddressable cases return [DiagramWriteBackPlan.Failed]: source does not compile,
 *   node missing, the `diagram:` entry lives inside a fenced ```yaml block, or the node
 *   has no typed-block anchor at all (author `node: { id: ... }` first).
 *
 * [extensions] must be able to parse the document; [DiagramSlmExtension] is added to the
 * registry automatically when absent.
 */
public fun diagramBlockSetPlan(
    source: String,
    nodeId: String,
    graph: DiagramGraph,
    extensions: SlmExtensionRegistry = DiagramSlmExtension.registry(),
    fileName: String = "document.layout.md",
): DiagramWriteBackPlan {
    val registry = withDiagramExtension(extensions)
    val compiled = compileSlm(source, SlmCompileOptions(fileName = fileName, extensions = registry))
    val document = compiled.document
        ?: return DiagramWriteBackPlan.Failed("source does not compile; diagram write-back aborted")
    val node = document.nodeById(nodeId)
        ?: return DiagramWriteBackPlan.Failed("node '$nodeId' not found in the compiled document")

    val entries = collectTypedEntries(source, fileName, registry)
    val lineStarts = lineStartOffsets(source)
    val blockText = DiagramSlmExtension.write(graph)

    val diagramLine = node.blockSourceMaps[DiagramSlmExtension.kind]?.line
    if (diagramLine != null) {
        val entry = entries.firstOrNull {
            it.key == DiagramSlmExtension.kind && it.span.startLine == diagramLine
        } ?: return DiagramWriteBackPlan.Failed(
            "diagram block of node '$nodeId' is not addressable as a typed entry " +
                "(fenced yaml or ir splice); keep the edit in memory",
        )
        val indent = indentOfLine(source, lineStarts, entry.span.startLine)
        val start = lineStarts.offsetOf(entry.span.startLine, source.length)
        val end = lineStarts.offsetOf(entry.span.endLine + 1, source.length)
        val keepNewline = end > start && source[end - 1] == '\n'
        val replacement = indentBlock(blockText, indent) + if (keepNewline) "\n" else ""
        return DiagramWriteBackPlan.Ops(listOf(DiagramTextOp(start, end, replacement)))
    }

    // No diagram entry yet: insert below the node's last typed entry.
    val ownedStartLines = node.blockSourceMaps.values.map { it.line }.toSet()
    val anchorEntry = entries
        .filter { it.span.startLine in ownedStartLines }
        .maxByOrNull { it.span.endLine }
        ?: return DiagramWriteBackPlan.Failed(
            "node '$nodeId' has no typed-block anchor to attach a diagram block to; " +
                "author `node: { id: $nodeId }` first",
        )
    val indent = indentOfLine(source, lineStarts, anchorEntry.span.startLine)
    val insertAt = lineStarts.offsetOf(anchorEntry.span.endLine + 1, source.length)
    val needsLeadingBreak = insertAt == source.length && !source.endsWith("\n")
    val text = buildString {
        if (needsLeadingBreak) append('\n')
        append(indentBlock(blockText, indent))
        append('\n')
    }
    return DiagramWriteBackPlan.Ops(listOf(DiagramTextOp(insertAt, insertAt, text)))
}

/** Result of [applyDiagramWriteBack]; [newSource] is null when nothing was applied. */
public data class DiagramWriteBackResult(
    val newSource: String?,
    val message: String? = null,
) {
    val isApplied: Boolean get() = newSource != null
}

/**
 * Plans and applies the write-back, then recompiles the patched source and verifies the
 * node's graph round-trips to exactly [graph] (anti-corruption veto, mirroring the
 * editor's `withStructuralSource` check). On any drift the original source is left
 * untouched and the failure reason is reported.
 */
public fun applyDiagramWriteBack(
    source: String,
    nodeId: String,
    graph: DiagramGraph,
    extensions: SlmExtensionRegistry = DiagramSlmExtension.registry(),
    fileName: String = "document.layout.md",
): DiagramWriteBackResult {
    val plan = diagramBlockSetPlan(source, nodeId, graph, extensions, fileName)
    val ops = when (plan) {
        is DiagramWriteBackPlan.Failed -> return DiagramWriteBackResult(null, plan.message)
        is DiagramWriteBackPlan.Ops -> plan
    }
    val newSource = ops.applyTo(source)

    val registry = withDiagramExtension(extensions)
    val recompiled = compileSlm(newSource, SlmCompileOptions(fileName = fileName, extensions = registry))
    val document = recompiled.document
        ?: return DiagramWriteBackResult(null, "patched source no longer compiles; edit reverted")
    val node = document.nodeById(nodeId)
        ?: return DiagramWriteBackResult(null, "node '$nodeId' disappeared after the patch; edit reverted")
    val roundTripped = (node.kind as? DesignNodeKind.Diagram)?.graph
    if (roundTripped != graph) {
        return DiagramWriteBackResult(
            null,
            "diagram graph did not round-trip through the patched source; edit reverted",
        )
    }
    return DiagramWriteBackResult(newSource)
}

// --- helpers ---

private fun withDiagramExtension(extensions: SlmExtensionRegistry): SlmExtensionRegistry =
    if (extensions.find(DiagramSlmExtension.kind) != null) {
        extensions
    } else {
        SlmExtensionRegistry.of(
            extensions.kinds.mapNotNull { extensions.find(it) } + DiagramSlmExtension,
        )
    }

/** All typed entries of the document, in source order (top level, lists, blockquotes). */
private fun collectTypedEntries(
    source: String,
    fileName: String,
    registry: SlmExtensionRegistry,
): List<TypedEntry> {
    val parsed = SlmMarkdownParser(DiagnosticCollector(fileName), registry).parse(source)
    val entries = mutableListOf<TypedEntry>()
    fun walk(blocks: List<SlmBlock>) {
        blocks.forEach { block ->
            when (block) {
                is TypedAttributeBlock -> entries += block.entries
                is ListBlock -> block.items.forEach { walk(it.children) }
                is BlockquoteBlock -> walk(block.blocks)
                else -> Unit
            }
        }
    }
    walk(parsed.blocks)
    return entries
}

/** Start offset of each 1-based line; index [line - 1]. */
private fun lineStartOffsets(source: String): IntArray {
    val starts = mutableListOf(0)
    source.forEachIndexed { index, char ->
        if (char == '\n') starts += index + 1
    }
    return starts.toIntArray()
}

/** Offset of 1-based [line]'s first character; [sourceLength] past the last line. */
private fun IntArray.offsetOf(line: Int, sourceLength: Int): Int =
    if (line - 1 < size) this[line - 1] else sourceLength

private fun indentOfLine(source: String, lineStarts: IntArray, line: Int): Int {
    var offset = lineStarts.offsetOf(line, source.length)
    var indent = 0
    while (offset < source.length && source[offset] == ' ') {
        indent++
        offset++
    }
    return indent
}

private fun indentBlock(blockText: String, indent: Int): String {
    if (indent == 0) return blockText
    val pad = " ".repeat(indent)
    return blockText.lines().joinToString("\n") { line -> if (line.isEmpty()) line else pad + line }
}
