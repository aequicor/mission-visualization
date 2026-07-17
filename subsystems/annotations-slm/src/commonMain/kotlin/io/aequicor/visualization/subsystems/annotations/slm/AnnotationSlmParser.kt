package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationBody
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationStatus

/** One recoverable parse problem; [line] is 1-based in the sidecar source. */
public data class AnnotationSlmWarning(val line: Int, val message: String)

/**
 * Result of parsing a sidecar file. [needsRewrite] is true when at least one section
 * carried no explicit `{id=...}` marker, so a deterministic id was synthesized — the
 * caller should pin the ids back into the source once (surgically via
 * [AnnotationSlmPatcher.pinIds]; the patcher's upsert/delete also pin on their own)
 * — the same id-stability invariant as structural SLM edits.
 */
public data class AnnotationSlmParseResult(
    val layer: AnnotationLayer,
    val warnings: List<AnnotationSlmWarning> = emptyList(),
    val needsRewrite: Boolean = false,
    /** Ids synthesized for sections without a `{id=...}` marker, keyed by 1-based header line. */
    val synthesizedIds: Map<Int, String> = emptyMap(),
    /** 1-based header line of every parsed annotation, by id (skipped sections are absent). */
    val sectionLines: Map<String, Int> = emptyMap(),
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
        val synthesizedIds = LinkedHashMap<Int, String>()
        val sectionLines = LinkedHashMap<String, Int>()
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
            val id = explicitId ?: synthesizeId(index, usedIds).also { synthesized ->
                needsRewrite = true
                synthesizedIds[section.headerLineNumber] = synthesized
            }
            usedIds += id
            sectionLines[id] = section.headerLineNumber

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
                status = header.status,
            )
        }

        return AnnotationSlmParseResult(
            layer = AnnotationLayer(screenFileName = fileName, annotations = annotations),
            warnings = warnings,
            needsRewrite = needsRewrite,
            synthesizedIds = synthesizedIds,
            sectionLines = sectionLines,
        )
    }

    // --- section splitting ---

    private class Section(
        val headerLineNumber: Int,
        val headerLine: String,
        val bodyLines: List<String>,
    )

    private fun splitSections(text: String): List<Section> {
        // CRLF input is normalized per line (same precedent as SlmMarkdownParser), so
        // carriage returns never leak into headers or body text.
        val lines = text.split('\n').map { it.removeSuffix("\r") }
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
     * with trailing/leading blank lines dropped (they are section framing, not content)
     * and writer escapes stripped ([AnnotationSlmFormat.unescapeBodyLine]), is the
     * plain-text body.
     */
    private fun parseBody(lines: List<String>): Pair<AnnotationBody, AnnotationImage?> {
        val imageIndex = lines.indexOfFirst { AnnotationSlmFormat.imageLineRegex.matches(it) }
        val image = if (imageIndex >= 0) AnnotationSlmFormat.parseImageLine(lines[imageIndex]) else null
        val bodyLines = lines
            .filterIndexed { index, _ -> index != imageIndex }
            .map(AnnotationSlmFormat::unescapeBodyLine)
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
        val status: AnnotationStatus,
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
        var status = AnnotationStatus.Open
        while (true) {
            cursor.skipSpaces()
            if (cursor.atEnd) break
            when {
                cursor.consume("+@") -> {
                    val nodeId = parseNodeId(cursor) ?: return null
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
                            AnnotationSlmFormat.STATUS_KEY -> {
                                status = AnnotationSlmFormat.statusFromToken(value) ?: return null
                            }
                            else -> return null
                        }
                    }
                }
                else -> return null
            }
        }
        return Header(kind, anchor, references, expanded, id, author, status)
    }

    private fun parseAnchor(cursor: Cursor): AnnotationAnchor? {
        if (!cursor.consume("@")) return null
        if (cursor.consume("(")) {
            val (x, y) = parsePair(cursor) ?: return null
            return AnnotationAnchor.FreePoint(x, y)
        }
        val nodeId = parseNodeId(cursor) ?: return null
        if (!cursor.consume("(")) return AnnotationAnchor.NodeAnchor(nodeId)
        val (dx, dy) = parsePair(cursor) ?: return null
        return AnnotationAnchor.NodeAnchor(nodeId, dx, dy)
    }

    /**
     * A node id token: bare (id chars only) or quoted with escapes — the form
     * [AnnotationSlmFormat.renderNodeId] writes for ids outside the bare charset.
     * Null when empty or unterminated.
     */
    private fun parseNodeId(cursor: Cursor): String? {
        if (cursor.consume("\"")) return cursor.takeQuoted()
        return cursor.takeWhile(AnnotationSlmFormat::isIdChar).ifEmpty { null }
    }

    /** Parses `x,y)` (the opening paren is already consumed); `-0` folds to `0.0`. */
    private fun parsePair(cursor: Cursor): Pair<Double, Double>? {
        val content = cursor.takeWhile { it != ')' }
        if (!cursor.consume(")")) return null
        val parts = content.split(',')
        if (parts.size != 2) return null
        val x = parts[0].trim().toDoubleOrNull() ?: return null
        val y = parts[1].trim().toDoubleOrNull() ?: return null
        return AnnotationSlmFormat.canonicalCoord(x) to AnnotationSlmFormat.canonicalCoord(y)
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

        /**
         * Reads a quoted token body up to the closing `"` (the opening quote is already
         * consumed), resolving `\"`, `\\`, `\n` and `\r` escapes; null when unterminated
         * or empty.
         */
        fun takeQuoted(): String? {
            val builder = StringBuilder()
            while (position < text.length) {
                when (val char = text[position]) {
                    '"' -> {
                        position++
                        return builder.toString().ifEmpty { null }
                    }
                    '\\' -> {
                        if (position + 1 >= text.length) return null
                        builder.append(
                            when (val escaped = text[position + 1]) {
                                'n' -> '\n'
                                'r' -> '\r'
                                else -> escaped
                            },
                        )
                        position += 2
                    }
                    else -> {
                        builder.append(char)
                        position++
                    }
                }
            }
            return null
        }
    }
}
