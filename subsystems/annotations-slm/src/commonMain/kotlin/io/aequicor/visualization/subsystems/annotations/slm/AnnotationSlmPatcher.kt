package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation

/**
 * Surgical single-section patches over `*.annotations.md` source text, in the spirit of
 * `SlmPatcher`/`SectionWriter`: the target section is located by its explicit `{id=...}`
 * marker and spliced in place — bytes of every other section (and any preamble before
 * the first heading) are preserved exactly, never re-serialized.
 *
 * A section's footprint runs from its `## ` line down to (but excluding) the next `## `
 * line, or to end-of-source for the trailing section — so the footprint owns its own
 * blank-line separation from the next section.
 */
public object AnnotationSlmPatcher {

    /**
     * Replaces the section whose `{id=...}` matches [annotation]'s id with a fresh render,
     * or appends a new section at the end of [source] when no such section exists (framed
     * with exactly one blank line, matching the writer's canonical separation).
     */
    public fun upsertSection(source: String, annotation: Annotation): String {
        val section = AnnotationSlmWriter.renderSection(annotation)
        val target = sectionSpans(source).firstOrNull { it.id == annotation.id }
            ?: return appendSection(source, section)
        val separator = if (target.end < source.length) "\n" else ""
        return source.substring(0, target.start) + section + separator + source.substring(target.end)
    }

    /**
     * Removes the section whose `{id=...}` matches [annotationId]; unknown ids are a
     * no-op returning [source] unchanged. Dropping the trailing section also trims the
     * now-dangling blank separation down to a single final newline.
     */
    public fun deleteSection(source: String, annotationId: String): String {
        val target = sectionSpans(source).firstOrNull { it.id == annotationId } ?: return source
        val remainder = source.substring(0, target.start) + source.substring(target.end)
        if (target.end < source.length) return remainder
        val trimmed = remainder.trimEnd('\n')
        return if (trimmed.isEmpty()) "" else trimmed + "\n"
    }

    // --- section spans ---

    private class SectionSpan(val id: String?, val start: Int, val end: Int)

    /** Footprints of all `## ` sections with their explicit ids (null when unmarked). */
    private fun sectionSpans(source: String): List<SectionSpan> {
        val starts = mutableListOf<Int>()
        var offset = 0
        for (line in source.split('\n')) {
            if (line.startsWith(AnnotationSlmFormat.HEADER_PREFIX)) starts += offset
            offset += line.length + 1 // '\n'; a missing final newline only overshoots past the last line
        }
        return starts.mapIndexed { index, start ->
            val end = starts.getOrElse(index + 1) { source.length }
            val headerLine = source.substring(start, source.indexOf('\n', start).takeIf { it >= 0 } ?: source.length)
            SectionSpan(id = AnnotationSlmFormat.headerExplicitId(headerLine), start = start, end = end)
        }
    }

    /** Appends [section] after the existing content with exactly one blank line between. */
    private fun appendSection(source: String, section: String): String {
        if (source.isBlank()) return section
        val trailingBreaks = source.takeLastWhile { it == '\n' }.length
        return source + "\n".repeat((2 - trailingBreaks).coerceAtLeast(0)) + section
    }
}
