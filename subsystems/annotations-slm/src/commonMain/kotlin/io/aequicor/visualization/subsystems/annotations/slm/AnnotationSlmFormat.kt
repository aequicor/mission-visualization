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
 * non-zero) or `@(x,y)` for a free point. The `{id=...}` marker is ALWAYS written — the
 * same explicit-id stability invariant as structural SLM edits; a parsed section without
 * one gets a deterministic synthesized id and flags the layer for rewrite.
 *
 * v1 limitations (documented, not enforced): body lines must not themselves start with
 * `## ` or be a lone markdown image, and attribute values must not contain `,` or `}`.
 */
internal object AnnotationSlmFormat {

    const val HEADER_PREFIX: String = "## "
    const val KIND_ISSUE: String = "issue"
    const val KIND_NOTE: String = "note"
    const val EXPANDED_FLAG: String = "[expanded]"
    const val ID_KEY: String = "id"
    const val AUTHOR_KEY: String = "author"

    /** Characters allowed in node/annotation ids inside the header. */
    fun isIdChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_' || char == '-' || char == '.' || char == ':' || char == '/'

    /** A whole line that is a markdown image: `![alt](source)`. */
    val imageLineRegex: Regex = Regex("""^!\[([^\]]*)]\((.+)\)\s*$""")

    /** Image alt text carrying the intrinsic size: `320x200` (decimals allowed). */
    private val imageDimsRegex = Regex("""^(-?\d+(?:\.\d+)?)x(-?\d+(?:\.\d+)?)$""")

    /** Extracts the explicit id from a header line, or null when the marker is absent. */
    private val headerIdRegex = Regex("""\{\s*id=([^,}]*)""")

    fun headerExplicitId(headerLine: String): String? =
        headerIdRegex.find(headerLine)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

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
            append('@').append(anchor.nodeId)
            if (anchor.offsetX != 0.0 || anchor.offsetY != 0.0) {
                append(renderPair(anchor.offsetX, anchor.offsetY))
            }
        }
        is AnnotationAnchor.FreePoint -> "@" + renderPair(anchor.x, anchor.y)
    }

    fun renderImage(image: AnnotationImage): String =
        "![" + formatCoord(image.width) + "x" + formatCoord(image.height) + "](" + image.source + ")"

    fun parseImageLine(line: String): AnnotationImage? {
        val match = imageLineRegex.find(line) ?: return null
        val (alt, source) = match.destructured
        val dims = imageDimsRegex.find(alt.trim())
        val width = dims?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val height = dims?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
        return AnnotationImage(source = source, width = width, height = height)
    }

    fun renderPair(x: Double, y: Double): String = "(" + formatCoord(x) + "," + formatCoord(y) + ")"

    /** Canonical coordinate rendering: integral doubles without the trailing `.0`. */
    fun formatCoord(value: Double): String =
        if (value == floor(value) && !value.isInfinite() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}
