package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode

/** A diagram element scoped by the authored design node that owns its graph. */
internal data class DiagramAnnotationTarget(
    val diagramNodeId: String,
    val elementId: String,
)

private const val DiagramTargetPrefix: String = "diagram-node:"

/**
 * Opaque id stored in [io.aequicor.visualization.subsystems.annotations.AnnotationAnchor.NodeAnchor]
 * for a node inside an embedded diagram. Length-prefixed parts keep arbitrary authored ids
 * unambiguous while retaining the existing annotation sidecar grammar.
 */
internal fun diagramAnnotationTargetId(diagramNodeId: String, elementId: String): String {
    require(diagramNodeId.isNotEmpty()) { "diagramNodeId must not be empty" }
    require(elementId.isNotEmpty()) { "elementId must not be empty" }
    return buildString {
        append(DiagramTargetPrefix)
        append(diagramNodeId.length).append(':').append(diagramNodeId)
        append(elementId.length).append(':').append(elementId)
    }
}

/** Decodes an id emitted by [diagramAnnotationTargetId], or null for a plain design-node id. */
internal fun parseDiagramAnnotationTargetId(targetId: String): DiagramAnnotationTarget? {
    if (!targetId.startsWith(DiagramTargetPrefix)) return null
    var cursor = DiagramTargetPrefix.length

    fun readPart(): String? {
        val lengthEnd = targetId.indexOf(':', startIndex = cursor)
        if (lengthEnd <= cursor) return null
        val length = targetId.substring(cursor, lengthEnd).toIntOrNull() ?: return null
        if (length <= 0) return null
        val start = lengthEnd + 1
        val end = start + length
        if (end > targetId.length) return null
        cursor = end
        return targetId.substring(start, end)
    }

    val diagramNodeId = readPart() ?: return null
    val elementId = readPart() ?: return null
    if (cursor != targetId.length) return null
    return DiagramAnnotationTarget(diagramNodeId, elementId)
}

/** Human-facing label for an annotation pinned to this diagram node. */
internal fun DiagramNode.annotationTargetLabel(): String? =
    labels.firstOrNull()?.text?.takeIf(String::isNotBlank)
        ?: when (val value = payload) {
            is UmlClassNode -> value.name
            is UmlLifelineNode -> value.name
            is UmlStateNode -> value.name
            is UmlActivityNode -> value.name
            is UmlActorNode -> value.name
            is UmlUseCaseNode -> value.name
            is UmlComponentNode -> value.name
            is UmlDeploymentNode -> value.name
            is UmlNoteNode -> value.text
            is UmlPackageNode -> value.name
            else -> null
        }?.takeIf(String::isNotBlank)

/** Stable semantic type used in exported issue context. */
internal fun DiagramNode.annotationTargetType(): String = when (payload) {
    is UmlClassNode -> "uml-class"
    is UmlLifelineNode -> "uml-lifeline"
    is UmlStateNode -> "uml-state"
    is UmlActivityNode -> "uml-activity"
    is UmlActorNode -> "uml-actor"
    is UmlUseCaseNode -> "uml-use-case"
    is UmlComponentNode -> "uml-component"
    is UmlDeploymentNode -> "uml-deployment"
    is UmlNoteNode -> "uml-note"
    is UmlPackageNode -> "uml-package"
    else -> "diagram-node"
}
