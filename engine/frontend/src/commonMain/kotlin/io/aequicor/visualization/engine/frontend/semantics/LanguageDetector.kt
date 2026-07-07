package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.frontmatter.SlmFrontmatter
import io.aequicor.visualization.engine.frontend.markdown.BlockquoteBlock
import io.aequicor.visualization.engine.frontend.markdown.HeadingBlock
import io.aequicor.visualization.engine.frontend.markdown.LinkRun
import io.aequicor.visualization.engine.frontend.markdown.ListBlock
import io.aequicor.visualization.engine.frontend.markdown.ParagraphBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmInline
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownDocument
import io.aequicor.visualization.engine.frontend.markdown.TableBlock
import io.aequicor.visualization.engine.frontend.markdown.TextRun

/** BCP-47-ish `xx-XX` / `xxx-XX` shape accepted without a warning. */
private val localeTagFormat = Regex("""[a-z]{2,3}-[A-Z]{2}""")

/**
 * Document-level source-locale detection (design section D).
 *
 * `frontmatter.sourceLocale` always wins (a malformed tag only warns). Otherwise
 * the Cyrillic ratio over prose letters — headings, paragraphs, list items,
 * blockquotes and table cells, excluding expressions, link targets, typed blocks
 * and fences — decides between `ru-RU` and [fallbackLocale].
 */
fun detectSourceLocale(
    document: SlmMarkdownDocument,
    frontmatter: SlmFrontmatter,
    diagnostics: DiagnosticCollector,
    fallbackLocale: SlmLocale = SlmLocale("en-US"),
    cyrillicRatioThreshold: Double = 0.3,
): SlmLocale {
    frontmatter.sourceLocale?.let { declared ->
        if (!localeTagFormat.matches(declared.tag)) {
            diagnostics.warning(
                "Frontmatter sourceLocale \"${declared.tag}\" is not in xx-XX format",
                line = 1,
                blockPath = "frontmatter",
            )
        }
        return declared
    }
    val counter = LetterCounter()
    document.blocks.forEach { counter.countBlock(it) }
    if (counter.total == 0) return fallbackLocale
    val ratio = counter.cyrillic.toDouble() / counter.total
    return if (ratio >= cyrillicRatioThreshold) SlmLocale("ru-RU") else fallbackLocale
}

private class LetterCounter {
    var total = 0
    var cyrillic = 0

    fun countBlock(block: SlmBlock) {
        when (block) {
            is HeadingBlock -> countInlines(block.inlines)
            is ParagraphBlock -> countInlines(block.inlines)
            is ListBlock -> block.items.forEach { item ->
                countInlines(item.inlines)
                item.children.forEach { countBlock(it) }
            }
            is BlockquoteBlock -> block.blocks.forEach { countBlock(it) }
            is TableBlock -> {
                block.header.forEach { countInlines(it) }
                block.rows.forEach { row -> row.forEach { countInlines(it) } }
            }
            // Typed blocks, fences, images and comments carry no prose.
            else -> {}
        }
    }

    /** Text runs and link labels are prose; expressions and link targets are not. */
    fun countInlines(inlines: List<SlmInline>) {
        inlines.forEach { inline ->
            when (inline) {
                is TextRun -> countText(inline.text)
                is LinkRun -> countInlines(inline.label)
                else -> {}
            }
        }
    }

    fun countText(text: String) {
        text.forEach { char ->
            if (char.isLetter()) {
                total++
                if (char in 'Ѐ'..'ӿ') cyrillic++
            }
        }
    }
}
