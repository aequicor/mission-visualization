package io.aequicor.visualization.subsystems.annotations

/** Which annotations to export. */
public sealed interface ExportScope {
    /** Only annotations whose ids are in [ids]. */
    public data class Selected(val ids: Set<String>) : ExportScope

    /** Only annotations of the layer for [fileName]. */
    public data class Screen(val fileName: String) : ExportScope

    /** All annotations of all layers. */
    public data object WholeDocument : ExportScope
}

/**
 * Resolved design-node context for the prompt. The core does not read the IR; the
 * caller maps `ResolvedNode` to this shape and passes a `nodeContext` lookup.
 */
public data class AnnotatedNodeRef(
    val nodeId: String,
    val label: String?,
    val type: String?,
    val screenFileName: String?,
    val bounds: AnnotationRect?,
)

/**
 * Builds a plain-text prompt for an AI coding agent from the non-closed **issue**
 * annotations ([AnnotationKind.Issue]); notes and closed issues are never exported.
 * Ordering is deterministic:
 * layers sorted by screen file name, then the layer's own annotation order.
 */
public object AnnotationPromptExporter {

    public fun exportIssues(
        layers: List<AnnotationLayer>,
        scope: ExportScope,
        nodeContext: (String) -> AnnotatedNodeRef?,
    ): String {
        val issues = layers
            .sortedBy { it.screenFileName }
            .flatMap { layer -> layer.annotations.map { layer.screenFileName to it } }
            .filter { (screen, annotation) ->
                annotation.kind == AnnotationKind.Issue &&
                    annotation.status != AnnotationStatus.Closed &&
                    scope.includes(screen, annotation)
            }

        val builder = StringBuilder()
        builder.appendLine(PROMPT_HEADER)
        if (issues.isEmpty()) {
            builder.appendLine()
            builder.appendLine("No design issues in the selected scope.")
            return builder.toString().trimEnd() + "\n"
        }
        issues.forEachIndexed { index, (screen, annotation) ->
            builder.appendLine()
            builder.appendIssue(index + 1, screen, annotation, nodeContext)
        }
        return builder.toString().trimEnd() + "\n"
    }

    private fun ExportScope.includes(screenFileName: String, annotation: Annotation): Boolean =
        when (this) {
            is ExportScope.Selected -> annotation.id in ids
            is ExportScope.Screen -> screenFileName == fileName
            ExportScope.WholeDocument -> true
        }

    private fun StringBuilder.appendIssue(
        number: Int,
        screenFileName: String,
        annotation: Annotation,
        nodeContext: (String) -> AnnotatedNodeRef?,
    ) {
        appendLine("$number. Screen: $screenFileName")
        when (val anchor = annotation.anchor) {
            is AnnotationAnchor.NodeAnchor -> {
                val ref = nodeContext(anchor.nodeId)
                if (ref != null) appendLine("   Node: ${ref.describe()}")
                else appendLine("   Node: ${anchor.nodeId.singleLine()} (node deleted or unresolved)")
            }
            is AnnotationAnchor.FreePoint ->
                appendLine("   Location: free point at (${anchor.x.fmt()}, ${anchor.y.fmt()})")
        }
        if (annotation.references.isNotEmpty()) {
            val refs = annotation.references.joinToString(", ") { nodeId ->
                nodeContext(nodeId)?.describe() ?: "${nodeId.singleLine()} (node deleted or unresolved)"
            }
            appendLine("   Also references: $refs")
        }
        // Continuation lines of a multi-line body are indented deeper than the item's
        // own fields, so a body line like "2. Screen: ..." can never masquerade as a
        // real numbered item and the body stays visually inside its item.
        val bodyLines = annotation.body.text.ifBlank { "(no text)" }.split('\n')
        appendLine("   Status: ${annotation.status.promptToken()}")
        appendLine("   Issue: ${bodyLines.first()}")
        bodyLines.drop(1).forEach { line -> appendLine("      $line") }
        if (annotation.image != null) appendLine("   [attached image]")
    }

    private fun AnnotatedNodeRef.describe(): String = buildString {
        append(nodeId.singleLine())
        if (label != null) append(" \"${label.singleLine()}\"")
        if (type != null) append(" (${type.singleLine()})")
        if (screenFileName != null) append(" on ${screenFileName.singleLine()}")
        if (bounds != null) {
            append(
                ", bounds ${bounds.width.fmt()}x${bounds.height.fmt()}" +
                    " at (${bounds.left.fmt()}, ${bounds.top.fmt()})",
            )
        }
    }

    /** Flattens embedded line breaks so interpolated names cannot break the prompt structure. */
    private fun String.singleLine(): String = replace('\r', ' ').replace('\n', ' ')

    /** Trims a trailing `.0` so integral coordinates read naturally in the prompt. */
    private fun Double.fmt(): String {
        val truncated = toLong()
        return if (truncated.toDouble() == this) truncated.toString() else toString()
    }

    private fun AnnotationStatus.promptToken(): String = when (this) {
        AnnotationStatus.Open -> "open"
        AnnotationStatus.InReview -> "in review"
        AnnotationStatus.Closed -> "closed"
    }

    private val PROMPT_HEADER: String = buildString {
        appendLine("You are an AI coding agent asked to fix design issues in a design document.")
        appendLine("Each numbered item below is a reviewer-reported issue with its location context")
        appendLine("(target node id, label, type, screen and bounds when the node still resolves).")
        append("Fix every issue in the design source; leave unrelated parts of the design untouched.")
    }
}
