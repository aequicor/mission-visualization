package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.SlmCompileOptions
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.DirectPatchEntry
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.markdown.TypedAttributeBlock
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
 * Plans the surgical persistence of node [nodeId]'s new diagram [graph]. The CNL container
 * (`## Diagram: …` heading with body sentences) is the only write-back route: the
 * container's body sentence span is replaced with the canonical [DiagramCnlWriter]
 * sentences — the heading line (design-node side) is never touched. Prose interleaved
 * *between* the first and last sentence line is superseded by the canonical body; prose
 * before/after the sentence block survives.
 *
 * Unaddressable cases return [DiagramWriteBackPlan.Failed] (the caller keeps the edit
 * in memory): source does not compile, node missing, or the node's graph is not anchored
 * to a `## Diagram: …` CNL container (e.g. an `ir` splice).
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

    val entries = collectPatchEntries(source, fileName, registry)
    val lineStarts = lineStartOffsets(source)

    val diagramLine = node.blockSourceMaps[DiagramSlmExtension.kind]?.line
        ?: return DiagramWriteBackPlan.Failed(
            "node '$nodeId' has no `## Diagram: …` CNL container to persist the graph into; " +
                "keep the edit in memory",
        )
    val entry = entries.firstOrNull {
        it.key == DiagramSlmExtension.kind && it.span.startLine == diagramLine
    } ?: return DiagramWriteBackPlan.Failed(
        "diagram graph of node '$nodeId' is not addressable as a CNL container " +
            "(ir splice); keep the edit in memory",
    )
    return cnlBodyReplacePlan(source, lineStarts, entry, graph)
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

/**
 * The CNL-container branch: replaces the `## Diagram: …` container's collected sentence
 * span (the [DirectPatchEntry] the markdown parser aggregated from the body) with the
 * canonical CNL body for [graph]. When the container body was empty the entry span is
 * anchored at the heading line itself — then the body is inserted *below* the heading
 * (blank-line separated) and the heading is left untouched. An empty [graph] with an
 * already-empty body is a no-op plan.
 */
private fun cnlBodyReplacePlan(
    source: String,
    lineStarts: IntArray,
    entry: DirectPatchEntry,
    graph: DiagramGraph,
): DiagramWriteBackPlan {
    val body = DiagramSlmExtension.emitBody(graph)
    val startOffset = lineStarts.offsetOf(entry.span.startLine, source.length)
    val startLineText = source.substring(
        startOffset,
        minOf(lineStarts.offsetOf(entry.span.startLine + 1, source.length), source.length),
    )
    val spanIsHeading = startLineText.trimStart().startsWith("#")
    if (spanIsHeading) {
        // Empty container body: the aggregate span points at the heading line. Insert the
        // sentences below it; never rewrite the heading (it carries the design-node side).
        if (body.isEmpty()) return DiagramWriteBackPlan.Ops(emptyList())
        val insertAt = lineStarts.offsetOf(entry.span.startLine + 1, source.length)
        val needsLeadingBreak = insertAt == source.length && !source.endsWith("\n")
        val text = buildString {
            if (needsLeadingBreak) append('\n')
            append('\n')
            append(body.joinToString("\n"))
            append('\n')
        }
        return DiagramWriteBackPlan.Ops(listOf(DiagramTextOp(insertAt, insertAt, text)))
    }
    val indent = indentOfLine(source, lineStarts, entry.span.startLine)
    val end = lineStarts.offsetOf(entry.span.endLine + 1, source.length)
    val keepNewline = end > startOffset && source[end - 1] == '\n'
    val replacement = if (body.isEmpty()) {
        "" // graph emptied: drop the sentence lines entirely
    } else {
        indentBlock(body.joinToString("\n"), indent) + if (keepNewline) "\n" else ""
    }
    return DiagramWriteBackPlan.Ops(listOf(DiagramTextOp(startOffset, end, replacement)))
}

private fun withDiagramExtension(extensions: SlmExtensionRegistry): SlmExtensionRegistry =
    if (extensions.find(DiagramSlmExtension.kind) != null) {
        extensions
    } else {
        SlmExtensionRegistry.of(
            extensions.kinds.mapNotNull { extensions.find(it) } + DiagramSlmExtension,
        )
    }

/**
 * All CNL patch entries of the document, in source order (top level, lists,
 * blockquotes) — heading desugar and container aggregates. The `diagram` key entry
 * of a `## Diagram: …` container carries the aggregated graph payload.
 */
private fun collectPatchEntries(
    source: String,
    fileName: String,
    registry: SlmExtensionRegistry,
): List<DirectPatchEntry> {
    val parsed = SlmMarkdownParser(DiagnosticCollector(fileName), registry).parse(source)
    val entries = mutableListOf<DirectPatchEntry>()
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
