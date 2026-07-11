package io.aequicor.visualization.subsystems.annotations

/**
 * Annotation core model. Pure Kotlin, no Compose, no :engine:* deps — annotations live
 * in a sidecar review layer next to the design SLM and reference design nodes only by
 * node id. Expansion state at runtime is view state (workspace), not document state;
 * the model carries only the authored [Annotation.defaultExpanded] hint.
 */

/** Kind of an annotation: neutral note or an actionable issue (exported to AI prompts). */
public enum class AnnotationKind { Note, Issue }

/** Where an annotation is pinned on the canvas. */
public sealed interface AnnotationAnchor {
    /**
     * Pinned to a design node by id; the badge follows the node. [offsetX]/[offsetY]
     * displace the badge from the node's top-center. A node id that no longer resolves
     * makes the annotation "dangling" — it is kept, not lost.
     */
    public data class NodeAnchor(
        val nodeId: String,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
    ) : AnnotationAnchor

    /** Detached from any node: an absolute point in screen coordinates. */
    public data class FreePoint(val x: Double, val y: Double) : AnnotationAnchor
}

/** Annotation text body. v1 is plain text; a distinct type keeps the door open for RichText. */
public data class AnnotationBody(val text: String)

/**
 * Canonical body text: leading and trailing blank lines are dropped (in the sidecar
 * format they are section framing, not content); internal newlines and whitespace are
 * preserved. The editor operations and the sidecar writer both canonicalize through
 * this, so a layer built through them round-trips the sidecar byte-stably.
 */
public fun normalizeAnnotationBodyText(text: String): String =
    text.split('\n')
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n")

/** An image attached to an annotation; [source] is a data-URI or an asset reference. */
public data class AnnotationImage(
    val source: String,
    val width: Double,
    val height: Double,
)

/** A single annotation in the review layer. */
public data class Annotation(
    val id: String,
    val kind: AnnotationKind,
    val anchor: AnnotationAnchor,
    val body: AnnotationBody = AnnotationBody(""),
    val image: AnnotationImage? = null,
    val defaultExpanded: Boolean = false,
    /** Extra node ids this annotation also refers to (besides the anchor). */
    val references: List<String> = emptyList(),
    val author: String? = null,
)

/** Sidecar model of one screen's annotations (`<screen>.annotations.md`). */
public data class AnnotationLayer(
    val screenFileName: String,
    val annotations: List<Annotation> = emptyList(),
)
