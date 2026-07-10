package io.aequicor.visualization.subsystems.annotations.slm

import io.aequicor.visualization.subsystems.annotations.Annotation
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer

/**
 * Renders an [AnnotationLayer] to `*.annotations.md` sidecar text. Round-trip stable
 * with [AnnotationSlmParser]: `parse(write(layer)).layer == layer` for every model
 * field (the `{id=...}` marker is always emitted, so a written file never needs id
 * synthesis on re-parse).
 */
public object AnnotationSlmWriter {

    /** Renders all annotations of [layer], one section each, separated by a blank line. */
    public fun write(layer: AnnotationLayer): String =
        layer.annotations.joinToString(separator = "\n") { renderSection(it) }

    /**
     * Renders a single annotation section: header line, body lines (when non-empty),
     * then the embedded image line. Ends with a single `\n`; no trailing blank line —
     * separation between sections is the concern of [write] and [AnnotationSlmPatcher].
     */
    public fun renderSection(annotation: Annotation): String = buildString {
        append(renderHeader(annotation)).append('\n')
        val text = annotation.body.text
        if (text.isNotEmpty()) {
            text.split('\n').forEach { line -> append(line).append('\n') }
        }
        annotation.image?.let { append(AnnotationSlmFormat.renderImage(it)).append('\n') }
    }

    private fun renderHeader(annotation: Annotation): String = buildString {
        append(AnnotationSlmFormat.HEADER_PREFIX)
        append(AnnotationSlmFormat.kindToken(annotation.kind))
        append(' ')
        append(AnnotationSlmFormat.renderAnchor(annotation.anchor))
        annotation.references.forEach { nodeId -> append(" +@").append(nodeId) }
        if (annotation.defaultExpanded) append(' ').append(AnnotationSlmFormat.EXPANDED_FLAG)
        append(" {").append(AnnotationSlmFormat.ID_KEY).append('=').append(annotation.id)
        annotation.author?.let { author ->
            append(", ").append(AnnotationSlmFormat.AUTHOR_KEY).append('=').append(author)
        }
        append('}')
    }
}
