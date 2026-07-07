package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.frontend.expr.parseSlmExpression

/** Extracts the key from an `i18n:key=...` comment payload, or null. */
internal fun i18nKeyOf(commentText: String): String? {
    val match = Regex("""^i18n:key=(\S+)$""").matchEntire(commentText.trim()) ?: return null
    return match.groupValues[1]
}

/**
 * Minimal inline parser: `{{...}}` expressions, `[Label](/target)` links,
 * `![alt](path)` images, `<!-- ... -->` comments; everything else is plain text.
 * Unrecognized starters (unclosed constructs) fall back to literal text.
 */
class SlmInlineParser(private val diagnostics: DiagnosticCollector) {
    /**
     * Parses one physical line. [columnOffset] is the number of original-source
     * characters stripped before [text] (list markers, quote markers, indents), so a
     * character at index `i` has 1-based column `columnOffset + i + 1`.
     */
    fun parseLine(text: String, line: Int, columnOffset: Int = 0): List<SlmInline> {
        val runs = mutableListOf<SlmInline>()
        var textStart = 0
        var i = 0

        fun column(index: Int): Int = columnOffset + index + 1

        fun flushText(end: Int) {
            if (end > textStart) {
                runs += TextRun(text.substring(textStart, end), line, column(textStart))
            }
        }

        while (i < text.length) {
            val consumed = when {
                text.startsWith("{{", i) -> parseExpressionAt(text, i, line, ::column)?.also {
                    flushText(i)
                    runs += it.run
                }
                text.startsWith("![", i) -> parseImageAt(text, i, line, ::column)?.also {
                    flushText(i)
                    runs += it.run
                }
                text[i] == '[' -> parseLinkAt(text, i, line, columnOffset)?.also {
                    flushText(i)
                    runs += it.run
                }
                text.startsWith("<!--", i) -> parseCommentAt(text, i, line, ::column)?.also {
                    flushText(i)
                    runs += it.run
                }
                else -> null
            }
            if (consumed != null) {
                i = consumed.nextIndex
                textStart = i
            } else {
                i++
            }
        }
        flushText(text.length)
        applyI18nOverrides(runs)
        return runs
    }

    private class Consumed(val run: SlmInline, val nextIndex: Int)

    private fun parseExpressionAt(
        text: String,
        start: Int,
        line: Int,
        column: (Int) -> Int,
    ): Consumed? {
        val close = text.indexOf("}}", start + 2)
        if (close < 0) return null
        val raw = text.substring(start + 2, close).trim()
        val expression = parseSlmExpression(raw)
        if (expression is SlmExpression.Raw) {
            diagnostics.warning("Unparseable expression \"{{$raw}}\"", line, column(start))
        }
        return Consumed(ExpressionRun(expression, raw, line, column(start)), close + 2)
    }

    private fun parseImageAt(
        text: String,
        start: Int,
        line: Int,
        column: (Int) -> Int,
    ): Consumed? {
        val altEnd = text.indexOf(']', start + 2)
        if (altEnd < 0 || text.getOrNull(altEnd + 1) != '(') return null
        val pathEnd = text.indexOf(')', altEnd + 2)
        if (pathEnd < 0) return null
        val alt = text.substring(start + 2, altEnd)
        val path = text.substring(altEnd + 2, pathEnd)
        return Consumed(InlineImageRun(alt, path, line, column(start)), pathEnd + 1)
    }

    private fun parseLinkAt(
        text: String,
        start: Int,
        line: Int,
        columnOffset: Int,
    ): Consumed? {
        val labelEnd = text.indexOf(']', start + 1)
        if (labelEnd < 0 || text.getOrNull(labelEnd + 1) != '(') return null
        val targetEnd = text.indexOf(')', labelEnd + 2)
        if (targetEnd < 0) return null
        val labelText = text.substring(start + 1, labelEnd)
        val label = parseLine(labelText, line, columnOffset + start + 1)
        val target = text.substring(labelEnd + 2, targetEnd)
        val run = LinkRun(
            label = label,
            target = target,
            line = line,
            column = columnOffset + start + 1,
        )
        return Consumed(run, targetEnd + 1)
    }

    private fun parseCommentAt(
        text: String,
        start: Int,
        line: Int,
        column: (Int) -> Int,
    ): Consumed? {
        val close = text.indexOf("-->", start + 4)
        if (close < 0) return null
        val inner = text.substring(start + 4, close).trim()
        return Consumed(CommentRun(inner, line, column(start)), close + 3)
    }

    /** `[Label](/t) <!-- i18n:key=x -->` → the key lands on the preceding link run. */
    private fun applyI18nOverrides(runs: MutableList<SlmInline>) {
        for (index in runs.indices) {
            val comment = runs[index] as? CommentRun ?: continue
            val key = i18nKeyOf(comment.text) ?: continue
            var j = index - 1
            while (j >= 0 && runs[j].let { it is TextRun && it.text.isBlank() }) j--
            val link = runs.getOrNull(j) as? LinkRun ?: continue
            if (link.i18nKeyOverride == null) {
                runs[j] = link.copy(i18nKeyOverride = key)
            }
        }
    }
}
