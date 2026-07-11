package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation

/**
 * Surgical single-section patches over `*.annotations.md` source text, in the spirit of
 * `SlmPatcher`/`SectionWriter`: the target section is located by its explicit `{id=...}`
 * marker and spliced in place — bytes of every other section (and any preamble before
 * the first heading) are preserved exactly, never re-serialized, with two documented
 * normalizations applied first:
 * - CRLF line endings fold to LF (so patches never grow mixed line endings);
 * - sections without a `{id=...}` marker get their parser-synthesized id pinned into
 *   the header ([pinIds]) — otherwise an upsert of such a section would append a
 *   duplicate instead of editing it in place, and the edit would be lost on re-parse.
 *
 * A section's footprint runs from its `## ` line down to (but excluding) the next `## `
 * line, or to end-of-source for the trailing section — so the footprint owns its own
 * blank-line separation from the next section. Section ids come from a real parse of
 * the source, so a section skipped by the parser (malformed header, duplicate id) is
 * never a splice target — a stray `{id=...}` on a malformed line cannot hijack a patch.
 */
public object AnnotationSlmPatcher {

    /**
     * Pins parser-synthesized ids back into the source: every well-formed section
     * without a `{id=...}` marker gets ` {id=<synthesized>}` appended to its header
     * line — the surgical rewrite [AnnotationSlmParseResult.needsRewrite] asks for.
     * All other bytes are preserved (CRLF folds to LF); a source whose sections all
     * carry explicit ids is returned unchanged.
     */
    public fun pinIds(source: String): String {
        val normalized = normalizeLineEndings(source)
        val synthesized = AnnotationSlmParser.parse("", normalized).synthesizedIds
        if (synthesized.isEmpty()) return normalized
        val lines = normalized.split('\n').toMutableList()
        synthesized.forEach { (lineNumber, id) ->
            val index = lineNumber - 1
            lines[index] = lines[index].trimEnd() + " {${AnnotationSlmFormat.ID_KEY}=$id}"
        }
        return lines.joinToString("\n")
    }

    /**
     * Replaces the section whose id matches [annotation]'s id with a fresh render,
     * or appends a new section at the end of [source] when no such section exists
     * (framed with exactly one blank line, matching the writer's canonical separation).
     * Ids are pinned first ([pinIds]), so a section that parsed with a synthesized id
     * is edited in place, never duplicated.
     */
    public fun upsertSection(source: String, annotation: Annotation): String {
        val base = pinIds(source)
        val section = AnnotationSlmWriter.renderSection(annotation)
        val target = sectionSpans(base).firstOrNull { it.id == annotation.id }
            ?: return appendSection(base, section)
        val separator = if (target.end < base.length) "\n" else ""
        return base.substring(0, target.start) + section + separator + base.substring(target.end)
    }

    /**
     * Removes the section whose id matches [annotationId]; unknown ids are a no-op
     * returning [source] unchanged. Dropping the trailing section also trims the
     * now-dangling blank separation down to a single final newline.
     */
    public fun deleteSection(source: String, annotationId: String): String {
        val base = pinIds(source)
        val target = sectionSpans(base).firstOrNull { it.id == annotationId } ?: return source
        val remainder = base.substring(0, target.start) + base.substring(target.end)
        if (target.end < base.length) return remainder
        val trimmed = remainder.trimEnd('\n')
        return if (trimmed.isEmpty()) "" else trimmed + "\n"
    }

    // --- section spans ---

    private class SectionSpan(val id: String?, val start: Int, val end: Int)

    /**
     * Footprints of all `## ` lines with the id the parser assigned to each (null for
     * sections the parser skipped — malformed headers or duplicate ids — so they can
     * never be splice targets).
     */
    private fun sectionSpans(source: String): List<SectionSpan> {
        val idByHeaderLine = AnnotationSlmParser.parse("", source).sectionLines
            .entries.associate { (id, line) -> line to id }
        val starts = mutableListOf<Pair<Int, Int>>() // offset to 1-based line number
        var offset = 0
        source.split('\n').forEachIndexed { index, line ->
            if (line.startsWith(AnnotationSlmFormat.HEADER_PREFIX)) starts += offset to index + 1
            offset += line.length + 1 // '\n'; a missing final newline only overshoots past the last line
        }
        return starts.mapIndexed { index, (start, lineNumber) ->
            val end = starts.getOrNull(index + 1)?.first ?: source.length
            SectionSpan(id = idByHeaderLine[lineNumber], start = start, end = end)
        }
    }

    /** Appends [section] after the existing content with exactly one blank line between. */
    private fun appendSection(source: String, section: String): String {
        if (source.isBlank()) return section
        val trailingBreaks = source.takeLastWhile { it == '\n' }.length
        return source + "\n".repeat((2 - trailingBreaks).coerceAtLeast(0)) + section
    }

    /** LF-normalized [source]; the same instance when it carries no CRLF already. */
    private fun normalizeLineEndings(source: String): String =
        if ("\r\n" in source) source.replace("\r\n", "\n") else source
}
