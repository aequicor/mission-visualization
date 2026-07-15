package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer

/**
 * Embedded comment storage inside a screen's `*.layout.md` source.
 *
 * The payload uses the annotation section grammar so anchors, ids and bodies keep the
 * same round-trip contract as sidecars, but it is wrapped in an HTML comment ignored by
 * the SLM compiler. The only byte escaping protects the HTML terminator (`--`) and the
 * escape marker itself (`%`); decoding is single-pass, so arbitrary reviewer text is
 * preserved without being able to close the block early.
 */
public object AnnotationLayoutComments {
    public const val StartMarker: String = "<!-- visualization-comments:v1"
    public const val EndMarker: String = "visualization-comments:end -->"

    /** Parses the embedded block, or an empty layer when the screen has no comments. */
    public fun parse(screenFileName: String, source: String): AnnotationSlmParseResult {
        val block = findBlock(source)
            ?: return AnnotationSlmParseResult(AnnotationLayer(screenFileName))
        return AnnotationSlmParser.parse(screenFileName, decodePayload(block.payload))
    }

    /** Inserts or replaces one comment while preserving every byte outside the block. */
    public fun upsert(source: String, annotation: Annotation): String {
        val block = findBlock(source)
        val payload = block?.payload?.let(::decodePayload).orEmpty()
        val patched = AnnotationSlmPatcher.upsertSection(payload, annotation)
        return replaceBlock(source, block, patched)
    }

    /** Deletes one comment. The wrapper disappears when the last comment is removed. */
    public fun delete(source: String, annotationId: String): String {
        val block = findBlock(source) ?: return source
        val payload = decodePayload(block.payload)
        val patched = AnnotationSlmPatcher.deleteSection(payload, annotationId)
        if (patched == payload) return source
        return replaceBlock(source, block, patched)
    }

    /** Canonical replacement used by load-time migration from legacy sidecars. */
    public fun write(source: String, layer: AnnotationLayer): String {
        val payload = AnnotationSlmWriter.write(layer)
        return replaceBlock(source, findBlock(source), payload)
    }

    private data class EmbeddedBlock(
        val start: Int,
        val endExclusive: Int,
        val payload: String,
    )

    private fun findBlock(source: String): EmbeddedBlock? {
        val match = BlockRegex.find(source) ?: return null
        return EmbeddedBlock(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            payload = match.groupValues[1],
        )
    }

    private fun replaceBlock(source: String, block: EmbeddedBlock?, payload: String): String {
        if (payload.isBlank()) {
            if (block == null) return source
            var start = block.start
            // Blocks authored by this writer are separated from the screen with one blank
            // line; own that separator when removing the now-empty block.
            if (start >= 2 && source.substring(0, start).endsWith("\n\n")) start--
            return source.substring(0, start) + source.substring(block.endExclusive)
        }
        val rendered = buildString {
            append(StartMarker).append('\n')
            append(encodePayload(payload))
            if (!payload.endsWith('\n')) append('\n')
            append(EndMarker).append('\n')
        }
        if (block != null) {
            return source.substring(0, block.start) + rendered + source.substring(block.endExclusive)
        }
        val insertion = insertionOffset(source)
        return source.substring(0, insertion) + rendered + source.substring(insertion)
    }

    /**
     * Keep the embedded block outside every authored node section: immediately after
     * frontmatter, or at byte zero when no frontmatter exists. Structural section
     * deletes/replacements may own everything below their heading, so appending the
     * block at EOF would let an unrelated node edit remove comments accidentally.
     */
    private fun insertionOffset(source: String): Int {
        if (!source.startsWith("---\n") && !source.startsWith("---\r\n")) return 0
        var offset = source.indexOf('\n') + 1
        while (offset in source.indices) {
            val end = source.indexOf('\n', offset).let { if (it < 0) source.length else it }
            if (source.substring(offset, end).removeSuffix("\r").trim() == "---") {
                return if (end < source.length) end + 1 else end
            }
            if (end >= source.length) break
            offset = end + 1
        }
        return 0
    }

    private fun encodePayload(payload: String): String = buildString(payload.length) {
        var index = 0
        while (index < payload.length) {
            when {
                payload[index] == '%' -> append("%25")
                payload[index] == '-' && payload.getOrNull(index + 1) == '-' -> {
                    append("%2D%2D")
                    index++
                }
                else -> append(payload[index])
            }
            index++
        }
    }

    private fun decodePayload(payload: String): String = buildString(payload.length) {
        var index = 0
        while (index < payload.length) {
            when {
                payload.startsWith("%25", index) -> {
                    append('%')
                    index += 3
                }
                payload.startsWith("%2D", index) -> {
                    append('-')
                    index += 3
                }
                else -> {
                    append(payload[index])
                    index++
                }
            }
        }
    }

    private val BlockRegex: Regex = Regex(
        pattern = "^${Regex.escape(StartMarker)}\\r?\\n(.*?)^${Regex.escape(EndMarker)}[ \\t]*(?:\\r?\\n)?",
        options = setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )
}
