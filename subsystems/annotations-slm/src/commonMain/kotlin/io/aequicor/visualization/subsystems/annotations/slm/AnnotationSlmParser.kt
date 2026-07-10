package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationBody
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer

/** One recoverable parse problem; [line] is 1-based in the sidecar source. */
public data class AnnotationSlmWarning(val line: Int, val message: String)

/**
 * Result of parsing a sidecar file. [needsRewrite] is true when at least one section
 * carried no explicit `{id=...}` marker, so a deterministic id was synthesized — the
 * caller should write the layer back once to pin the ids in the source (the same
 * id-stability invariant as structural SLM edits).
 */
public data class AnnotationSlmParseResult(
    val layer: AnnotationLayer,
    val warnings: List<AnnotationSlmWarning> = emptyList(),
    val needsRewrite: Boolean = false,
)

/**
 * Parses `*.annotations.md` sidecar text into an [AnnotationLayer]. Tolerant by design:
 * a malformed section is skipped and reported as a warning, it never fails the whole
 * file; content before the first `## ` heading (titles, comments) is ignored.
 */
public object AnnotationSlmParser {

    /** Parses [text]; [fileName] becomes [AnnotationLayer.screenFileName]. */
    public fun parse(fileName: String, text: String): AnnotationSlmParseResult {
        val warnings = mutableListOf<AnnotationSlmWarning>()
        val annotations = mutableListOf<Annotation>()
        val usedIds = mutableSetOf<String>()
        var needsRewrite = false

        splitSections(text).forEachIndexed { index, section ->
            val header = parseHeader(section.headerLine)
            if (header == null) {
                warnings += AnnotationSlmWarning(section.headerLineNumber, "Malformed annotation header, section skipped")
                return@forEachIndexed
            }
            val explicitId = header.id
            if (explicitId != null && explicitId in usedIds) {
                warnings += AnnotationSlmWarning(
                    section.headerLineNumber,
                    "Duplicate annotation id '$explicitId', section skipped",
                )
                return@forEachIndexed
            }
            val id = explicitId ?: synthesizeId(index, usedIds).also { needsRewrite = true }
            usedIds += id

            val (body, image) = parseBody(section.bodyLines)
            annotations += Annotation(
                id = id,
                kind = header.kind,
                anchor = header.anchor,
                body = body,
                image = image,
                defaultExpanded = header.expanded,
                references = header.references,
                author = header.author,
            )
        }

        return AnnotationSlmParseResult(
            layer = AnnotationLayer(screenFileName = fileName, annotations = annotations),
            warnings = warnings,
            needsRewrite = needsRewrite,
        )
    }

    // --- section splitting ---

    private class Section(
        val headerLineNumber: Int,
        val headerLine: String,
        val bodyLines: List<String>,
    )

    private fun splitSections(text: String): List<Section> {
        val lines = text.split('\n')
        val headerIndices = lines.indices.filter { lines[it].startsWith(AnnotationSlmFormat.HEADER_PREFIX) }
        return headerIndices.mapIndexed { position, headerIndex ->
            val end = headerIndices.getOrElse(position + 1) { lines.size }
            Section(
                headerLineNumber = headerIndex + 1,
                headerLine = lines[headerIndex],
                bodyLines = lines.subList(headerIndex + 1, end),
            )
        }
    }

    /**
     * Deterministic id for a section written without a marker: `ann-<1-based index>`,
     * disambiguated with a numeric suffix on collision with an already-taken id.
     */
    private fun synthesizeId(sectionIndex: Int, usedIds: Set<String>): String {
        val base = "ann-${sectionIndex + 1}"
        if (base !in usedIds) return base
        var attempt = 2
        while ("$base-$attempt" in usedIds) attempt++
        return "$base-$attempt"
    }

    // --- body ---

    /**
     * The first line shaped like a markdown image becomes the embedded image; the rest,
     * with trailing/leading blank lines dropped (they are section framing, not content),
     * is the plain-text body.
     */
    private fun parseBody(lines: List<String>): Pair<AnnotationBody, AnnotationImage?> {
        val imageIndex = lines.indexOfFirst { AnnotationSlmFormat.imageLineRegex.matches(it) }
        val image = if (imageIndex >= 0) AnnotationSlmFormat.parseImageLine(lines[imageIndex]) else null
        val bodyLines = lines
            .filterIndexed { index, _ -> index != imageIndex }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
        return AnnotationBody(bodyLines.joinToString("\n")) to image
    }

    // --- header ---

    private class Header(
        val kind: AnnotationKind,
        val anchor: AnnotationAnchor,
        val references: List<String>,
        val expanded: Boolean,
        val id: String?,
        val author: String?,
    )

    /** Parses a `## kind @anchor [+@ref…] [expanded] {id=…}` line; null when malformed. */
    private fun parseHeader(line: String): Header? {
        val cursor = Cursor(line.removePrefix(AnnotationSlmFormat.HEADER_PREFIX).trim())

        val kind = AnnotationSlmFormat.kindFromToken(cursor.takeWhile { it != ' ' }) ?: return null
        cursor.skipSpaces()
        val anchor = parseAnchor(cursor) ?: return null

        val references = mutableListOf<String>()
        var expanded = false
        var id: String? = null
        var author: String? = null
        while (true) {
            cursor.skipSpaces()
            if (cursor.atEnd) break
            when {
                cursor.consume("+@") -> {
                    val nodeId = cursor.takeWhile(AnnotationSlmFormat::isIdChar)
                    if (nodeId.isEmpty()) return null
                    references += nodeId
                }
                cursor.consume(AnnotationSlmFormat.EXPANDED_FLAG) -> expanded = true
                cursor.consume("{") -> {
                    val content = cursor.takeWhile { it != '}' }
                    if (!cursor.consume("}")) return null
                    for (entry in content.split(',')) {
                        val key = entry.substringBefore('=').trim()
                        val value = entry.substringAfter('=', "").trim()
                        when (key) {
                            AnnotationSlmFormat.ID_KEY -> id = value.takeIf { it.isNotEmpty() }
                            AnnotationSlmFormat.AUTHOR_KEY -> author = value.takeIf { it.isNotEmpty() }
                            else -> return null
                        }
                    }
                }
                else -> return null
            }
        }
        return Header(kind, anchor, references, expanded, id, author)
    }

    private fun parseAnchor(cursor: Cursor): AnnotationAnchor? {
        if (!cursor.consume("@")) return null
        if (cursor.consume("(")) {
            val (x, y) = parsePair(cursor) ?: return null
            return AnnotationAnchor.FreePoint(x, y)
        }
        val nodeId = cursor.takeWhile(AnnotationSlmFormat::isIdChar)
        if (nodeId.isEmpty()) return null
        if (!cursor.consume("(")) return AnnotationAnchor.NodeAnchor(nodeId)
        val (dx, dy) = parsePair(cursor) ?: return null
        return AnnotationAnchor.NodeAnchor(nodeId, dx, dy)
    }

    /** Parses `x,y)` (the opening paren is already consumed). */
    private fun parsePair(cursor: Cursor): Pair<Double, Double>? {
        val content = cursor.takeWhile { it != ')' }
        if (!cursor.consume(")")) return null
        val parts = content.split(',')
        if (parts.size != 2) return null
        val x = parts[0].trim().toDoubleOrNull() ?: return null
        val y = parts[1].trim().toDoubleOrNull() ?: return null
        return x to y
    }

    /** Minimal sequential scanner over a header line. */
    private class Cursor(private val text: String) {
        private var position = 0

        val atEnd: Boolean get() = position >= text.length

        fun skipSpaces() {
            while (position < text.length && text[position] == ' ') position++
        }

        fun consume(prefix: String): Boolean {
            if (!text.startsWith(prefix, position)) return false
            position += prefix.length
            return true
        }

        inline fun takeWhile(predicate: (Char) -> Boolean): String {
            val start = position
            while (position < text.length && predicate(text[position])) position++
            return text.substring(start, position)
        }
    }
}
