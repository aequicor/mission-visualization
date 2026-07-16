package io.aequicor.visualization.editor.domain

import io.aequicor.visualization.engine.ir.layout.DesignLayoutEngine
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.subsystems.annotations.AnnotatedNodeRef
import io.aequicor.visualization.subsystems.annotations.AnnotationLayer
import io.aequicor.visualization.subsystems.annotations.AnnotationPromptExporter
import io.aequicor.visualization.subsystems.annotations.AnnotationRect
import io.aequicor.visualization.subsystems.annotations.ExportScope

/**
 * Builds the "fix these design issues" prompt for an AI agent from the issue
 * annotations of [layers] ([AnnotationPromptExporter] — notes are never exported).
 * Node context is resolved from the working [document]: each page is resolved and
 * laid out, and every laid-out box becomes an [AnnotatedNodeRef] (label / type /
 * screen / absolute bounds) keyed by its authored node id — an anchor whose node no
 * longer resolves is reported as dangling by the exporter, never dropped.
 */
class ExportIssuesPromptUseCase {

    operator fun invoke(
        layers: List<AnnotationLayer>,
        scope: ExportScope,
        document: DesignDocument?,
        screenFileNameByPageId: Map<String, String> = emptyMap(),
    ): String {
        val refs = resolveNodeRefs(document, screenFileNameByPageId)
        return AnnotationPromptExporter.exportIssues(layers, scope) { nodeId -> refs[nodeId] }
    }

    /** Authored node id -> resolved context, over every page of [document]. */
    private fun resolveNodeRefs(
        document: DesignDocument?,
        screenFileNameByPageId: Map<String, String>,
    ): Map<String, AnnotatedNodeRef> {
        if (document == null) return emptyMap()
        val refs = LinkedHashMap<String, AnnotatedNodeRef>()
        val resolver = DesignResolver(document)
        val engine = DesignLayoutEngine()
        document.pages.forEach { page ->
            val screenFileName = screenFileNameByPageId[page.id]
            resolver.resolvePage(page).forEach { root ->
                engine.layout(root).allBoxes().forEach { box ->
                    val nodeId = box.node.sourceId.ifBlank { box.node.id }
                    // First resolution wins: a component instantiated twice keeps the
                    // first expansion's bounds for its authored source id.
                    if (nodeId !in refs) {
                        refs[nodeId] = AnnotatedNodeRef(
                            nodeId = nodeId,
                            label = box.node.name.takeIf { it.isNotBlank() },
                            type = box.node.type.takeIf { it.isNotBlank() },
                            screenFileName = screenFileName,
                            bounds = AnnotationRect.fromSize(box.x, box.y, box.width, box.height),
                        )
                    }
                    val graph = (document.nodeById(nodeId)?.kind as? DesignNodeKind.Diagram)?.graph
                    graph?.nodes?.forEach { element ->
                        val targetId = diagramAnnotationTargetId(nodeId, element.id.value)
                        if (targetId !in refs) {
                            refs[targetId] = AnnotatedNodeRef(
                                nodeId = "$nodeId/${element.id.value}",
                                label = element.annotationTargetLabel() ?: element.id.value,
                                type = element.annotationTargetType(),
                                screenFileName = screenFileName,
                                bounds = AnnotationRect.fromSize(
                                    box.x + element.x,
                                    box.y + element.y,
                                    element.width,
                                    element.height,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return refs
    }
}
