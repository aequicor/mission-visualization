package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.expr.SlmExpression

/**
 * Inline run inside a paragraph, heading, list item, or table cell. Every run knows
 * its 1-based [line] and [column] in the original source. No emphasis/bold/inline
 * code in v1.
 */
sealed interface SlmInline {
    val line: Int
    val column: Int
}

data class TextRun(
    val text: String,
    override val line: Int,
    override val column: Int,
) : SlmInline

/** `[Label](/target)`, optionally followed by `<!-- i18n:key=... -->`. */
data class LinkRun(
    val label: List<SlmInline>,
    val target: String,
    val i18nKeyOverride: String? = null,
    override val line: Int,
    override val column: Int,
) : SlmInline

/** `{{...}}`; [raw] is the trimmed source text between the braces. */
data class ExpressionRun(
    val expression: SlmExpression,
    val raw: String,
    override val line: Int,
    override val column: Int,
) : SlmInline

/** `![alt](path)` inside other inline content (standalone images become [ImageBlock]). */
data class InlineImageRun(
    val alt: String,
    val path: String,
    override val line: Int,
    override val column: Int,
) : SlmInline

/** Same-line `<!-- ... -->`; only the `i18n:key=...` payload is interpreted. */
data class CommentRun(
    val text: String,
    override val line: Int,
    override val column: Int,
) : SlmInline
