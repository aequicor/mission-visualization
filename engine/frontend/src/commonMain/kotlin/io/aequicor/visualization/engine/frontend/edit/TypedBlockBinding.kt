package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.CnlElementBlock
import io.aequicor.visualization.engine.frontend.markdown.FencedCodeBlock
import io.aequicor.visualization.engine.frontend.markdown.HeadingBlock
import io.aequicor.visualization.engine.frontend.markdown.HtmlCommentBlock
import io.aequicor.visualization.engine.frontend.markdown.ImageBlock
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.ParagraphBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmListItem
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownDocument
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TableBlock
import io.aequicor.visualization.engine.frontend.markdown.TypedAttributeBlock

/**
 * Anchor binding of typed attribute blocks, shared between the semantic pipeline's
 * verdicts and the patcher: every [TypedAttributeBlock] binds to the nearest
 * preceding anchor element (heading / list item / image / table / blockquote /
 * instruction paragraph), or to the screen root when none precedes; blank lines do
 * not reset the binding, and typed blocks inside a list item's children bind to the
 * item.
 *
 * Which paragraphs are anchors is a lexicon decision made by the semantic
 * extractor, so this helper takes the extractor's verdicts — the anchor spans
 * recorded in [SlmEditIndex] — instead of re-running lexicon matching; the two
 * therefore cannot diverge. Paragraphs without a recorded anchor span are plain
 * prose and keep the current binding, mirroring the extractor's walk.
 */
internal object TypedBlockBinding {
    fun anchoredEntries(
        document: SlmMarkdownDocument,
        anchorSpans: Collection<SlmSourceSpan>,
        lineIndex: LineIndex,
    ): TypedBlockBindingResult {
        val walker = Walker(anchorSpans, lineIndex)
        walker.walk(document.blocks, Walker.Cursor.Root)
        return TypedBlockBindingResult(walker.anchors, walker.rootGroups)
    }
}

internal class TypedBlockBindingResult(
    /** One binding per anchor element the walk visited, keyed by its anchor span. */
    val anchors: Map<SlmSourceSpan, AnchorBinding>,
    /** Typed groups bound to the screen root (before any anchor / after the H1). */
    val rootGroups: List<TypedAttributeBlock>,
)

internal class AnchorBinding(
    val span: SlmSourceSpan,
    /** 0-based typed-block indent column: item content column for list items. */
    val contentIndent: Int,
) {
    val groups = mutableListOf<TypedAttributeBlock>()
}

private class Walker(
    anchorSpans: Collection<SlmSourceSpan>,
    private val lineIndex: LineIndex,
) {
    sealed interface Cursor {
        /** Screen root: typed groups before any anchor or right after the H1. */
        data object Root : Cursor

        /** Structural anchor missing from the index; its groups are unaddressable. */
        data object Foreign : Cursor

        data class At(val binding: AnchorBinding) : Cursor
    }

    private val byStartLine: Map<Int, List<SlmSourceSpan>> = anchorSpans.groupBy { it.startLine }

    val anchors = LinkedHashMap<SlmSourceSpan, AnchorBinding>()
    val rootGroups = mutableListOf<TypedAttributeBlock>()

    fun walk(blocks: List<SlmBlock>, initial: Cursor) {
        var current = initial
        blocks.forEach { block ->
            when (block) {
                is TypedAttributeBlock -> bind(current, block)

                // Every top-level heading re-anchors: sections own recorded spans,
                // the screen-title H1 re-binds to the root.
                is HeadingBlock -> current = cursorAt(block.span)
                    ?: if (block.level == 1) Cursor.Root else Cursor.Foreign

                // Instruction paragraphs and group leads are anchors (their spans
                // are recorded); plain paragraphs keep the current binding.
                is ParagraphBlock -> cursorAt(block.span)?.let { current = it }

                // Top-level lists never re-anchor the outer walk; each item anchors
                // its own children (group leads keep the anchor across the list).
                is ListBlock -> walkList(block)

                is BlockquoteBlock -> {
                    val cursor = cursorAt(block.span) ?: Cursor.Foreign
                    walk(block.blocks, cursor)
                    current = cursor
                }

                is ImageBlock -> current = cursorAt(block.span) ?: Cursor.Foreign

                is TableBlock -> current = cursorAt(block.span) ?: Cursor.Foreign

                // A CNL element sentence is its own anchor; a following typed group binds to it.
                is CnlElementBlock -> current = cursorAt(block.span) ?: Cursor.Foreign

                is FencedCodeBlock, is HtmlCommentBlock -> {}
            }
        }
    }

    private fun walkList(list: ListBlock) {
        list.items.forEach { item ->
            val span = anchorSpanFor(item.span)
            val cursor = span
                ?.let { Cursor.At(bindingFor(it, contentColumnOf(item))) }
                ?: Cursor.Foreign
            walk(item.children, cursor)
        }
    }

    private fun bind(cursor: Cursor, group: TypedAttributeBlock) {
        when (cursor) {
            is Cursor.At -> cursor.binding.groups += group
            Cursor.Root -> rootGroups += group
            Cursor.Foreign -> {}
        }
    }

    private fun cursorAt(elementSpan: SlmSourceSpan): Cursor? =
        anchorSpanFor(elementSpan)?.let { span ->
            Cursor.At(bindingFor(span, lineIndex.indentOf(span.startLine)))
        }

    /**
     * Anchor span owned by the element at [elementSpan]: exact match first, then a
     * covering span starting on the same line (a group-lead's anchor span extends
     * past its lead paragraph to the end of the absorbed list).
     */
    private fun anchorSpanFor(elementSpan: SlmSourceSpan): SlmSourceSpan? {
        val candidates = byStartLine[elementSpan.startLine] ?: return null
        return candidates.firstOrNull { it.endLine == elementSpan.endLine }
            ?: candidates.firstOrNull { it.endLine >= elementSpan.endLine }
            ?: candidates.first()
    }

    private fun bindingFor(span: SlmSourceSpan, contentIndent: Int): AnchorBinding =
        anchors.getOrPut(span) { AnchorBinding(span, contentIndent) }

    /** 0-based content column of a list item's marker line, per the markdown parser. */
    private fun contentColumnOf(item: SlmListItem): Int {
        val text = lineIndex.lineText(item.span.startLine)
        val indent = text.takeWhile { it == ' ' }.length
        val rest = text.substring(indent)
        val markerLength = when {
            rest.startsWith("- ") || rest.startsWith("* ") -> 1
            rest == "-" || rest == "*" -> return text.length
            else -> rest.takeWhile { it.isDigit() }.length + 1
        }
        var content = indent + markerLength
        while (content < text.length && text[content] == ' ') content++
        return content
    }
}
