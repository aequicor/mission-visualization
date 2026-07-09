package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.cnl.CnlElement
import io.aequicor.visualization.engine.frontend.yaml.YamlValue

/** 1-based inclusive line range of a block in the SLM source. */
data class SlmSourceSpan(val startLine: Int, val endLine: Int)

/** Result of the block-level markdown parse: frontmatter (unparsed) plus body blocks. */
data class SlmMarkdownDocument(
    val fileName: String,
    val frontmatter: RawYamlBlock?,
    val blocks: List<SlmBlock>,
)

/**
 * Raw frontmatter YAML, handed off unparsed. [startLine] is the 1-based line of the
 * first YAML line (the line after the opening `---`); [span] covers both fences.
 */
data class RawYamlBlock(
    val text: String,
    val startLine: Int,
    val span: SlmSourceSpan,
)

sealed interface SlmBlock {
    val span: SlmSourceSpan
}

/** ATX heading `#`–`######`. Levels 4–6 are accepted with an info diagnostic. */
data class HeadingBlock(
    val level: Int,
    val inlines: List<SlmInline>,
    override val span: SlmSourceSpan,
) : SlmBlock

data class ParagraphBlock(
    val inlines: List<SlmInline>,
    override val span: SlmSourceSpan,
) : SlmBlock

data class ListBlock(
    val ordered: Boolean,
    val items: List<SlmListItem>,
    override val span: SlmSourceSpan,
) : SlmBlock

/**
 * One list item: [inlines] from the marker line, [children] parsed from lines indented
 * to the item content column — nested lists, blockquotes, typed blocks, paragraphs.
 */
data class SlmListItem(
    val inlines: List<SlmInline>,
    val children: List<SlmBlock>,
    val span: SlmSourceSpan,
)

data class BlockquoteBlock(
    val blocks: List<SlmBlock>,
    override val span: SlmSourceSpan,
) : SlmBlock

/** Standalone `![alt](path)` line, optionally with a trailing `<!-- i18n:key=... -->`. */
data class ImageBlock(
    val alt: String,
    val path: String,
    val i18nKeyOverride: String? = null,
    override val span: SlmSourceSpan,
) : SlmBlock

/** GFM table; cells are inline runs. `\|` escapes a literal pipe inside a cell. */
data class TableBlock(
    val header: List<List<SlmInline>>,
    val rows: List<List<List<SlmInline>>>,
    override val span: SlmSourceSpan,
) : SlmBlock

/**
 * Group of consecutive typed attribute entries bound to the nearest preceding anchor
 * (heading / list item / image / table / blockquote), or to the screen root when
 * there is no anchor. A blank line closes the group.
 */
data class TypedAttributeBlock(
    val entries: List<TypedEntry>,
    override val span: SlmSourceSpan,
) : SlmBlock

/**
 * One reserved-key entry of a typed block; [value] is the YAML value under the key.
 * [span] covers the entry's full extent including same-line trailing comments.
 */
data class TypedEntry(
    val kind: TypedBlockKind,
    val value: YamlValue,
    val span: SlmSourceSpan,
)

/** Fenced ``` block; [content] is captured verbatim, starting at [contentStartLine]. */
data class FencedCodeBlock(
    val info: String,
    val content: String,
    val contentStartLine: Int,
    override val span: SlmSourceSpan,
) : SlmBlock

/** Standalone `<!-- ... -->` line(s); attaches to the next text-bearing element. */
data class HtmlCommentBlock(
    val text: String,
    override val span: SlmSourceSpan,
) : SlmBlock

/**
 * A controlled-natural-language element sentence, e.g. `Прямоугольник 120 на 15 цвет #00B843`.
 * The parsed [element] carries per-value source spans; the extractor turns it into a node and
 * the write-back path re-parses the sentence to patch a value in place.
 */
data class CnlElementBlock(
    val element: CnlElement,
    override val span: SlmSourceSpan,
) : SlmBlock
