package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.AnnotationAnchor
import io.aequicor.visualization.subsystems.annotations.AnnotationImage
import io.aequicor.visualization.subsystems.annotations.AnnotationKind
import kotlin.math.abs
import kotlin.math.floor

/**
 * Shared grammar of the `*.annotations.md` sidecar format. One markdown section per
 * annotation:
 *
 * ```
 * ## issue @node-abc123(8,-12) +@node-def456 [expanded] {id=ann-1, author=Alice}
 * Body text (may span
 * multiple lines).
 * ![320x200](data:image/png;base64,....)
 * ```
 *
 * Header grammar: `## ` + kind (`issue`|`note`) + ` ` + anchor + zero or more ` +@nodeId`
 * extra references + optional ` [expanded]` flag + attribute block ` {id=..., author=...}`.
 * The anchor is `@nodeId` for a node anchor (with `(dx,dy)` appended when the offset is
 * non-zero) or `@(x,y)` for a free point. A node id containing characters outside the
 * bare id charset (see [isIdChar]) is written quoted — `@"hero (main)"` — with `\"`,
 * `\\`, `\n` and `\r` escapes inside the quotes; the same form is accepted for `+@`
 * references. The `{id=...}` marker is ALWAYS written — the same explicit-id stability
 * invariant as structural SLM edits; a parsed section without one gets a deterministic
 * synthesized id and flags the layer for rewrite (pin the ids back with
 * [AnnotationSlmPatcher.pinIds]).
 *
 * Body lines that would be structural on re-parse — a line starting with `## ` or a
 * lone markdown image — are escaped with one leading backslash on write and unescaped
 * on parse ([escapeBodyLine]/[unescapeBodyLine]), so arbitrary body text survives the
 * write→parse round trip. Attribute values must not contain `,` or `}` (v1 limitation,
 * documented, not enforced).
 */
internal object AnnotationSlmFormat {

    const val HEADER_PREFIX: String = "## "
    const val KIND_ISSUE: String = "issue"
    const val KIND_NOTE: String = "note"
    const val EXPANDED_FLAG: String = "[expanded]"
    const val ID_KEY: String = "id"
    const val AUTHOR_KEY: String = "author"

    /** Characters allowed in bare (unquoted) node/annotation ids inside the header. */
    fun isIdChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_' || char == '-' || char == '.' || char == ':' || char == '/'

    /** A whole line that is a markdown image: `![alt](source)`. */
    val imageLineRegex: Regex = Regex("""^!\[([^\]]*)]\((.+)\)\s*$""")

    /** Image alt text carrying the intrinsic size: `320x200` (decimals allowed). */
    private val imageDimsRegex = Regex("""^(-?\d+(?:\.\d+)?)x(-?\d+(?:\.\d+)?)$""")

    fun kindToken(kind: AnnotationKind): String = when (kind) {
        AnnotationKind.Issue -> KIND_ISSUE
        AnnotationKind.Note -> KIND_NOTE
    }

    fun kindFromToken(token: String): AnnotationKind? = when (token) {
        KIND_ISSUE -> AnnotationKind.Issue
        KIND_NOTE -> AnnotationKind.Note
        else -> null
    }

    fun renderAnchor(anchor: AnnotationAnchor): String = when (anchor) {
        is AnnotationAnchor.NodeAnchor -> buildString {
            append('@').append(renderNodeId(anchor.nodeId))
            if (anchor.offsetX != 0.0 || anchor.offsetY != 0.0) {
                append(renderPair(anchor.offsetX, anchor.offsetY))
            }
        }
        is AnnotationAnchor.FreePoint -> "@" + renderPair(anchor.x, anchor.y)
    }

    /**
     * A node id for the header: bare when every character is an id char, quoted with
     * escapes otherwise — so ids with spaces, parentheses or non-BMP characters (all
     * legal explicit SLM ids) stay parseable instead of breaking the header.
     */
    fun renderNodeId(nodeId: String): String =
        if (nodeId.isNotEmpty() && nodeId.all(::isIdChar)) {
            nodeId
        } else {
            buildString {
                append('"')
                for (char in nodeId) {
                    when (char) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        else -> append(char)
                    }
                }
                append('"')
            }
        }

    // --- body-line escaping ---

    /** True when a raw body [line] would be structural on re-parse: a `## ` section header or a lone markdown image. */
    fun isStructuralBodyLine(line: String): Boolean =
        line.startsWith(HEADER_PREFIX) || imageLineRegex.matches(line)

    /** Escapes a body [line] for writing: a structural-looking line gains one leading backslash. */
    fun escapeBodyLine(line: String): String =
        if (isStructuralBodyLine(line.dropWhile { it == '\\' })) "\\" + line else line

    /** Reverse of [escapeBodyLine]: strips one leading backslash guarding a structural-looking line. */
    fun unescapeBodyLine(line: String): String =
        if (line.startsWith('\\') && isStructuralBodyLine(line.dropWhile { it == '\\' })) line.substring(1) else line

    // --- image / coordinates ---

    fun renderImage(image: AnnotationImage): String =
        "![" + formatCoord(image.width) + "x" + formatCoord(image.height) + "](" + image.source + ")"

    fun parseImageLine(line: String): AnnotationImage? {
        val match = imageLineRegex.find(line) ?: return null
        val (alt, source) = match.destructured
        val dims = imageDimsRegex.find(alt.trim())
        val width = canonicalCoord(dims?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0)
        val height = canonicalCoord(dims?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0)
        return AnnotationImage(source = source, width = width, height = height)
    }

    fun renderPair(x: Double, y: Double): String = "(" + formatCoord(x) + "," + formatCoord(y) + ")"

    /** Folds `-0.0` to `0.0` so parsed and written coordinates compare sign-canonically. */
    fun canonicalCoord(value: Double): Double = value + 0.0

    /** Canonical coordinate rendering: `-0.0` folded, integral doubles without the trailing `.0`. */
    fun formatCoord(value: Double): String {
        val canonical = canonicalCoord(value)
        return if (canonical == floor(canonical) && !canonical.isInfinite() && abs(canonical) < 1e15) {
            canonical.toLong().toString()
        } else {
            canonical.toString()
        }
    }
}
