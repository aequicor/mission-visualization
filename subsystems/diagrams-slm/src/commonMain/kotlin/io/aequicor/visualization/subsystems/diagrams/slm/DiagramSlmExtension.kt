package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.blocks.CnlContainerExtension
import io.aequicor.visualization.engine.frontend.blocks.CnlContainerLine
import io.aequicor.visualization.engine.frontend.blocks.SlmExtensionRegistry
import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph

/**
 * The `diagram` extension: diagram CNL container <-> [DiagramGraph] <-> IR diagram node.
 *
 * The composition root registers it via `SlmCompileOptions(extensions = [registry])`.
 * Authoring is the `## Diagram: …` CNL container — the heading carries the design-node
 * side (name/id/size/position) and every body line is one element sentence
 * (`Layer …` / `Node …` / `Edge …` / `Group …`, see [DiagramCnlReader]); the canonical
 * inverse is [DiagramCnlWriter]. There is no YAML surface for diagram payloads.
 */
public object DiagramSlmExtension :
    CnlContainerExtension<DiagramCnlSentence, DiagramGraph> {

    override val kind: String = "diagram"

    override fun validate(payload: DiagramGraph, reading: BlockReading) {
        validateDiagramReferences(payload, reading)
    }

    override fun applyToNode(node: DesignNode, payload: DiagramGraph): DesignNode =
        node.copy(type = "diagram", kind = DesignNodeKind.Diagram(payload))

    override fun payloadOf(node: DesignNode): DiagramGraph? =
        (node.kind as? DesignNodeKind.Diagram)?.graph

    // --- CNL container authoring (`## Diagram: …` body sentences) ---

    override fun parseSentence(
        line: String,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): CnlContainerLine<DiagramCnlSentence> = DiagramCnlReader.parseSentence(line, lineNumber, diagnostics)

    override fun aggregateSentences(
        elements: List<DiagramCnlSentence>,
        span: SlmSourceSpan,
        diagnostics: DiagnosticCollector,
    ): DiagramGraph = DiagramCnlReader.aggregate(elements, diagnostics)

    override fun emitBody(payload: DiagramGraph): List<String> = DiagramCnlWriter.sentences(payload)

    /** A registry containing just this extension, ready for `SlmCompileOptions.extensions`. */
    public fun registry(): SlmExtensionRegistry = SlmExtensionRegistry.of(DiagramSlmExtension)
}

/**
 * Cross-reference checks over a parsed graph: broken edge endpoints and port references
 * are errors, broken layer/parent references are errors, broken group members warnings.
 * Mirrors the IR-level `IR-DIAGRAM` validation but fires at SLM parse time with the
 * block's source location.
 */
internal fun validateDiagramReferences(graph: DiagramGraph, reading: BlockReading) {
    diagramReferenceIssues(graph).forEach { issue ->
        if (issue.isError) {
            reading.diagnostics.error(issue.message, blockPath = reading.blockPath)
        } else {
            reading.diagnostics.warning(issue.message, blockPath = reading.blockPath)
        }
    }
}

/** Which graph element a [DiagramReferenceIssue] belongs to (for per-sentence locations). */
internal sealed interface DiagramRefOwner {
    public data class OfNode(val id: String) : DiagramRefOwner
    public data class OfEdge(val id: String) : DiagramRefOwner
    public data class OfGroup(val id: String) : DiagramRefOwner
}

internal data class DiagramReferenceIssue(
    val isError: Boolean,
    val message: String,
    val owner: DiagramRefOwner,
)

/**
 * The cross-reference rule set behind the CNL aggregation: each issue is reported at its
 * owning sentence's source line. Message texts match the IR-level `IR-DIAGRAM` checks.
 */
internal fun diagramReferenceIssues(graph: DiagramGraph): List<DiagramReferenceIssue> = buildList {
    val nodeIds = graph.nodes.map { it.id }.toSet()
    val layerIds = graph.layers.map { it.id }.toSet()

    graph.edges.forEach { edge ->
        val owner = DiagramRefOwner.OfEdge(edge.id.value)
        listOf("from" to edge.source, "to" to edge.target).forEach { (key, endpoint) ->
            when (endpoint) {
                is DiagramEndpoint.FloatingAnchor ->
                    if (endpoint.nodeId !in nodeIds) {
                        add(
                            DiagramReferenceIssue(
                                isError = true,
                                message = "diagram edge '${edge.id.value}' `$key` references missing node " +
                                    "'${endpoint.nodeId.value}'",
                                owner = owner,
                            ),
                        )
                    }
                is DiagramEndpoint.FixedPort -> {
                    val node = graph.nodeById(endpoint.nodeId)
                    if (node == null) {
                        add(
                            DiagramReferenceIssue(
                                isError = true,
                                message = "diagram edge '${edge.id.value}' `$key` references missing node " +
                                    "'${endpoint.nodeId.value}'",
                                owner = owner,
                            ),
                        )
                    } else if (node.portById(endpoint.portId) == null) {
                        add(
                            DiagramReferenceIssue(
                                isError = true,
                                message = "diagram edge '${edge.id.value}' `$key` references port " +
                                    "'${endpoint.portId.value}' that node '${endpoint.nodeId.value}' " +
                                    "does not declare",
                                owner = owner,
                            ),
                        )
                    }
                }
                is DiagramEndpoint.FreePoint -> Unit
            }
        }
        edge.layerId?.let { layerId ->
            if (layerId !in layerIds) {
                add(
                    DiagramReferenceIssue(
                        isError = true,
                        message = "diagram edge '${edge.id.value}' references missing layer '${layerId.value}'",
                        owner = owner,
                    ),
                )
            }
        }
    }

    graph.nodes.forEach { node ->
        val owner = DiagramRefOwner.OfNode(node.id.value)
        node.parentId?.let { parentId ->
            if (parentId !in nodeIds) {
                add(
                    DiagramReferenceIssue(
                        isError = true,
                        message = "diagram node '${node.id.value}' references missing parent '${parentId.value}'",
                        owner = owner,
                    ),
                )
            }
        }
        node.layerId?.let { layerId ->
            if (layerId !in layerIds) {
                add(
                    DiagramReferenceIssue(
                        isError = true,
                        message = "diagram node '${node.id.value}' references missing layer '${layerId.value}'",
                        owner = owner,
                    ),
                )
            }
        }
    }

    graph.groups.forEach { group ->
        group.memberIds.forEach { memberId ->
            if (memberId !in nodeIds) {
                add(
                    DiagramReferenceIssue(
                        isError = false,
                        message = "diagram group '${group.id.value}' references missing node '${memberId.value}'",
                        owner = DiagramRefOwner.OfGroup(group.id.value),
                    ),
                )
            }
        }
    }
}
