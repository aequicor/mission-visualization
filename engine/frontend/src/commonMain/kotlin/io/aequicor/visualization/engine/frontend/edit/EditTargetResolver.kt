package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.HeadingBlock
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.ParagraphBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownDocument
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TypedAttributeBlock

/**
 * Resolves an edit's node id to its source anchor on a FRESH parse of the passed
 * source (markdown + yaml stages only, never a full compile): the fingerprint gate
 * guarantees the text matches the compile that produced the [SlmEditIndex], so the
 * recorded anchor spans line up with the fresh CST. Typed entries bound to the
 * anchor are collected via [TypedBlockBinding].
 */
internal fun resolveEditTarget(
    source: String,
    nodeId: String,
    editIndex: SlmEditIndex,
    lineIndex: LineIndex,
    fileName: String,
): EditTargetResolution {
    val span = editIndex.anchorOwners[nodeId]
        ?: return EditTargetResolution.Failed(unaddressableMessage(nodeId, editIndex))
    // Throwaway collector: the source already compiled once; repeating its parse
    // diagnostics on every edit would be noise.
    val document = SlmMarkdownParser(DiagnosticCollector(fileName)).parse(source)
    val binding = TypedBlockBinding.anchoredEntries(document, editIndex.anchorOwners.values, lineIndex)
    val anchor = binding.anchors[span]
    val target = if (anchor != null) {
        EditTarget(
            nodeId = nodeId,
            anchorSpan = span,
            boundGroups = anchor.groups.toList(),
            insertion = TypedBlockInsertion(
                line = span.endLine + 1,
                indent = anchor.contentIndent,
                blankLineBefore = lineContinuesParagraph(document.blocks, span.endLine),
            ),
        )
    } else {
        // No element starts this span: the node is the screen root.
        EditTarget(
            nodeId = nodeId,
            anchorSpan = span,
            boundGroups = binding.rootGroups,
            insertion = rootInsertion(document),
        )
    }
    return EditTargetResolution.Resolved(target)
}

internal sealed interface EditTargetResolution {
    class Resolved(val target: EditTarget) : EditTargetResolution

    class Failed(val message: String) : EditTargetResolution
}

internal class EditTarget(
    val nodeId: String,
    val anchorSpan: SlmSourceSpan,
    /** Typed groups bound to the anchor, in document order. */
    val boundGroups: List<TypedAttributeBlock>,
    /** Where a brand-new typed block goes when the anchor has no group yet. */
    val insertion: TypedBlockInsertion,
)

internal class TypedBlockInsertion(
    /** 1-based line BEFORE which the new typed-block lines are inserted. */
    val line: Int,
    /** 0-based indent column of the new block's reserved key. */
    val indent: Int,
    /**
     * Whether a separating blank line must precede the block: mandatory after
     * paragraph anchors, where a directly adjacent `key:` line would be absorbed
     * as paragraph continuation prose.
     */
    val blankLineBefore: Boolean,
)

internal fun unaddressableMessage(nodeId: String, editIndex: SlmEditIndex): String =
    if (nodeId in editIndex.irSpliceNodes) {
        "Node \"$nodeId\" originates from a ```ir block; edit the embedded JSON directly"
    } else {
        "Node \"$nodeId\" has no addressable source anchor; promote it to its own " +
            "heading or list item, or edit the parent node"
    }

/**
 * Root-anchored blocks go right after the H1 line (after the closing `---` when
 * there is no H1, at the top otherwise) — before any following paragraph, which is
 * itself a potential anchor.
 */
private fun rootInsertion(document: SlmMarkdownDocument): TypedBlockInsertion {
    val h1 = document.blocks.filterIsInstance<HeadingBlock>().firstOrNull { it.level == 1 }
    val line = when {
        h1 != null -> h1.span.endLine + 1
        document.frontmatter != null -> document.frontmatter.span.endLine + 1
        else -> 1
    }
    return TypedBlockInsertion(line = line, indent = 0, blankLineBefore = false)
}

/**
 * True when [line] belongs to a paragraph (top-level or inside a list item), i.e.
 * a typed block inserted on the next line would be parsed as prose continuation.
 * Blockquote paragraphs are excluded — their continuation requires a `>` prefix.
 */
private fun lineContinuesParagraph(blocks: List<SlmBlock>, line: Int): Boolean =
    blocks.any { block ->
        when (block) {
            is ParagraphBlock -> line in block.span.startLine..block.span.endLine
            is ListBlock -> block.items.any { item -> lineContinuesParagraph(item.children, line) }
            else -> false
        }
    }
