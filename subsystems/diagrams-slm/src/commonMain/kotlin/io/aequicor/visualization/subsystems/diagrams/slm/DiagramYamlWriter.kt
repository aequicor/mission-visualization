package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility

/**
 * Emits a [DiagramGraph] as the canonical SLM `diagram:` block text: deterministic key
 * order (`layers`, `nodes`, `edges`, `groups`; per node `id`, `type`, geometry, payload,
 * `ports`, `style`, labels, hierarchy flags), defaults omitted, two-space nesting, no
 * trailing newline. The output round-trips through [DiagramSlmExtension.read] and is
 * stable under re-emission, so surgical write-back diffs stay minimal.
 */
public object DiagramYamlWriter {

    /** Full block text starting with the `diagram:` line. */
    public fun blockText(graph: DiagramGraph): String =
        SlmYamlRender.entryLines("diagram", graphMapping(graph), 0).joinToString("\n")

    private fun graphMapping(graph: DiagramGraph): SlmYaml.Mapping = SlmYaml.Mapping(
        buildList {
            if (graph.layers.isNotEmpty()) {
                add("layers" to SlmYaml.Sequence(graph.layers.map(::layer)))
            }
            if (graph.nodes.isNotEmpty()) {
                add("nodes" to SlmYaml.Sequence(graph.nodes.map(::node)))
            }
            if (graph.edges.isNotEmpty()) {
                add("edges" to SlmYaml.Sequence(graph.edges.map(::edge)))
            }
            if (graph.groups.isNotEmpty()) {
                add("groups" to SlmYaml.Sequence(graph.groups.map(::group)))
            }
        },
    )

    // --- layers / groups ---

    private fun layer(layer: DiagramLayer): SlmYaml = SlmYaml.Mapping(
        buildList {
            add("id" to scalar(layer.id.value))
            if (layer.name != layer.id.value) add("name" to scalar(layer.name))
            if (!layer.visible) add("visible" to scalar(false))
            if (layer.locked) add("locked" to scalar(true))
        },
    )

    private fun group(group: DiagramGroup): SlmYaml = SlmYaml.Mapping(
        buildList {
            add("id" to scalar(group.id.value))
            group.name?.let { add("name" to scalar(it)) }
            add(
                "members" to SlmYaml.Sequence(
                    group.memberIds.map { scalar(it.value) },
                    inline = true,
                ),
            )
        },
    )

    // --- nodes ---

    private fun node(node: DiagramNode): SlmYaml = SlmYaml.Mapping(
        buildList {
            add("id" to scalar(node.id.value))
            add("type" to SlmYaml.Scalar(payloadTypeToken(node.payload)))
            add("x" to scalar(node.x))
            add("y" to scalar(node.y))
            add("w" to scalar(node.width))
            add("h" to scalar(node.height))
            if (node.rotation != 0.0) add("rotation" to scalar(node.rotation))
            addAll(payloadEntries(node.payload))
            if (node.ports.isNotEmpty()) {
                add("ports" to SlmYaml.Sequence(node.ports.map(::port)))
            }
            styleMapping(node.style)?.let { add("style" to it) }
            addAll(nodeLabelEntries(node.labels))
            node.parentId?.let { add("parent" to scalar(it.value)) }
            node.layerId?.let { add("layer" to scalar(it.value)) }
            if (node.locked) add("locked" to scalar(true))
            if (!node.visible) add("visible" to scalar(false))
        },
    )

    internal fun payloadTypeToken(payload: DiagramNodePayload): String = when (payload) {
        is DiagramNodePayload.BasicShape -> payload.shape.slmToken()
        is DiagramNodePayload.ContainerNode -> "container"
        is DiagramNodePayload.SwimlaneNode -> "swimlane"
        is DiagramNodePayload.FlowchartNode -> "flowchart"
        is DiagramNodePayload.ErEntityNode -> "entity"
        is DiagramNodePayload.BpmnNode -> "bpmn"
        is TableNode -> "table"
        is UmlClassNode -> "class"
        is UmlLifelineNode -> "lifeline"
        is UmlStateNode -> "state"
        is UmlActivityNode -> "activity"
        is UmlActorNode -> "actor"
        is UmlUseCaseNode -> "use_case"
        is UmlComponentNode -> "component"
        is UmlDeploymentNode -> "deployment"
        is UmlNoteNode -> "note"
        is UmlPackageNode -> "package"
    }

    private fun payloadEntries(payload: DiagramNodePayload): List<Pair<String, SlmYaml>> =
        when (payload) {
            is DiagramNodePayload.BasicShape -> emptyList()

            is DiagramNodePayload.ContainerNode -> buildList {
                payload.title?.let { add("title" to labelValue(it)) }
                if (payload.collapsed) add("collapsed" to scalar(true))
            }

            is DiagramNodePayload.SwimlaneNode -> buildList {
                if (payload.orientation != DiagramOrientation.HORIZONTAL) {
                    add("orientation" to SlmYaml.Scalar(payload.orientation.slmToken()))
                }
                payload.title?.let { add("title" to labelValue(it)) }
                if (payload.lanes.isNotEmpty()) {
                    add(
                        "lanes" to SlmYaml.Sequence(
                            payload.lanes.map { lane ->
                                val title = lane.title
                                if (title == null) {
                                    scalar(lane.size)
                                } else {
                                    SlmYaml.Mapping(
                                        buildList {
                                            add("title" to labelValue(title))
                                            if (lane.size != 120.0) add("size" to scalar(lane.size))
                                        },
                                    )
                                }
                            },
                        ),
                    )
                }
            }

            is DiagramNodePayload.FlowchartNode ->
                listOf("kind" to SlmYaml.Scalar(payload.kind.slmToken()))

            is DiagramNodePayload.ErEntityNode -> buildList {
                add("name" to scalar(payload.name))
                if (payload.attributes.isNotEmpty()) {
                    add(
                        "attributes" to SlmYaml.Sequence(
                            payload.attributes.map { attribute ->
                                SlmYaml.Mapping(
                                    buildList {
                                        add("name" to scalar(attribute.name))
                                        attribute.type?.let { add("type" to scalar(it)) }
                                        if (attribute.primaryKey) add("pk" to scalar(true))
                                        if (attribute.foreignKey) add("fk" to scalar(true))
                                    },
                                )
                            },
                        ),
                    )
                }
            }

            is DiagramNodePayload.BpmnNode ->
                listOf("kind" to SlmYaml.Scalar(payload.kind.slmToken()))

            is TableNode -> buildList {
                add(
                    "rows" to SlmYaml.Sequence(
                        payload.rows.map { row ->
                            if (!row.header) {
                                scalar(row.height)
                            } else {
                                SlmYaml.Mapping(
                                    listOf(
                                        "height" to scalar(row.height),
                                        "header" to scalar(true),
                                    ),
                                )
                            }
                        },
                    ),
                )
                add(
                    "columns" to SlmYaml.Sequence(
                        payload.columns.map { column ->
                            if (!column.header) {
                                scalar(column.width)
                            } else {
                                SlmYaml.Mapping(
                                    listOf(
                                        "width" to scalar(column.width),
                                        "header" to scalar(true),
                                    ),
                                )
                            }
                        },
                    ),
                )
                if (payload.cells.isNotEmpty()) {
                    add(
                        "cells" to SlmYaml.Sequence(
                            payload.cells.map { cell ->
                                SlmYaml.Mapping(
                                    buildList {
                                        add("row" to scalar(cell.row))
                                        add("col" to scalar(cell.column))
                                        if (cell.rowSpan != 1) add("rowSpan" to scalar(cell.rowSpan))
                                        if (cell.colSpan != 1) add("colSpan" to scalar(cell.colSpan))
                                        cell.label?.let { add("label" to labelValue(it)) }
                                        cell.style?.let { style ->
                                            styleMapping(style)?.let { add("style" to it) }
                                        }
                                    },
                                )
                            },
                        ),
                    )
                }
            }

            is UmlClassNode -> buildList {
                add("name" to scalar(payload.name))
                payload.stereotype?.let { add("stereotype" to scalar(it)) }
                if (payload.abstract) add("abstract" to scalar(true))
                if (payload.attributes.isNotEmpty()) {
                    add("fields" to SlmYaml.Sequence(payload.attributes.map(::member)))
                }
                if (payload.operations.isNotEmpty()) {
                    add("methods" to SlmYaml.Sequence(payload.operations.map(::member)))
                }
            }

            is UmlLifelineNode -> buildList {
                add("name" to scalar(payload.name))
                if (payload.actor) add("actor" to scalar(true))
                if (payload.activations.isNotEmpty()) {
                    add(
                        "activations" to SlmYaml.Sequence(
                            payload.activations.map { inlinePair(it.start, it.end) },
                        ),
                    )
                }
            }

            is UmlStateNode -> buildList {
                if (payload.name.isNotEmpty()) add("name" to scalar(payload.name))
                if (payload.kind != UmlStateKind.SIMPLE) {
                    add("kind" to SlmYaml.Scalar(payload.kind.slmToken()))
                }
            }

            is UmlActivityNode -> buildList {
                add("kind" to SlmYaml.Scalar(payload.kind.slmToken()))
                if (payload.name.isNotEmpty()) add("name" to scalar(payload.name))
            }

            is UmlActorNode -> listOf("name" to scalar(payload.name))
            is UmlUseCaseNode -> listOf("name" to scalar(payload.name))

            is UmlComponentNode -> buildList {
                add("name" to scalar(payload.name))
                payload.stereotype?.let { add("stereotype" to scalar(it)) }
            }

            is UmlDeploymentNode -> buildList {
                add("name" to scalar(payload.name))
                payload.stereotype?.let { add("stereotype" to scalar(it)) }
            }

            is UmlNoteNode -> listOf("text" to scalar(payload.text))
            is UmlPackageNode -> listOf("name" to scalar(payload.name))
        }

    /** `"+ text"` shorthand for plain members, a map when static/abstract flags are set. */
    private fun member(member: UmlMember): SlmYaml =
        if (!member.static && !member.abstract) {
            scalar("${member.visibility.symbol} ${member.text}")
        } else {
            SlmYaml.Mapping(
                buildList {
                    add("text" to scalar(member.text))
                    if (member.visibility != UmlVisibility.PUBLIC) {
                        add("visibility" to SlmYaml.Scalar(member.visibility.slmToken()))
                    }
                    if (member.static) add("static" to scalar(true))
                    if (member.abstract) add("abstract" to scalar(true))
                },
            )
        }

    private fun port(port: DiagramPort): SlmYaml = SlmYaml.Mapping(
        buildList {
            add("id" to scalar(port.id.value))
            when (val anchor = port.anchor) {
                is DiagramPortAnchor.SideOffset -> {
                    add("side" to SlmYaml.Scalar(anchor.side.slmToken()))
                    if (anchor.offset != 0.5) add("offset" to scalar(anchor.offset))
                }
                is DiagramPortAnchor.RelativePoint ->
                    add("at" to inlinePair(anchor.x, anchor.y))
            }
        },
    )

    // --- edges ---

    private fun edge(edge: DiagramEdge): SlmYaml = SlmYaml.Mapping(
        buildList {
            add("id" to scalar(edge.id.value))
            add("from" to endpoint(edge.source))
            add("to" to endpoint(edge.target))
            relationValue(edge.relation)?.let { add("relation" to it) }
            if (edge.routing != DiagramRoutingStyle.ORTHOGONAL) {
                add("routing" to SlmYaml.Scalar(edge.routing.slmToken()))
            }
            if (edge.waypoints.isNotEmpty()) {
                add(
                    "waypoints" to SlmYaml.Sequence(
                        edge.waypoints.map { inlinePair(it.x, it.y) },
                    ),
                )
            }
            addAll(edgeLabelEntries(edge.labels))
            styleMapping(edge.style)?.let { add("style" to it) }
            arrowheadsMapping(edge.sourceArrowhead, edge.targetArrowhead)?.let {
                add("arrowheads" to it)
            }
            if (edge.lineJumps != LineJumpStyle.NONE) {
                add("lineJumps" to SlmYaml.Scalar(edge.lineJumps.slmToken()))
            }
            if (edge.connectionMode != DiagramConnectionMode.LINE) {
                add("mode" to SlmYaml.Scalar(edge.connectionMode.slmToken()))
            }
            if (edge.flowAnimation) add("animated" to scalar(true))
            edge.layerId?.let { add("layer" to scalar(it.value)) }
        },
    )

    /**
     * `nodeId` (floating) / `nodeId.portId` (fixed) / `[x, y]` (free); ids containing a
     * dot fall back to the explicit `{ node: ..., port: ... }` map form to stay parseable.
     */
    private fun endpoint(endpoint: DiagramEndpoint): SlmYaml = when (endpoint) {
        is DiagramEndpoint.FloatingAnchor ->
            if ('.' in endpoint.nodeId.value) {
                SlmYaml.Mapping(listOf("node" to scalar(endpoint.nodeId.value)))
            } else {
                scalar(endpoint.nodeId.value)
            }
        is DiagramEndpoint.FixedPort ->
            if ('.' in endpoint.nodeId.value || '.' in endpoint.portId.value) {
                SlmYaml.Mapping(
                    listOf(
                        "node" to scalar(endpoint.nodeId.value),
                        "port" to scalar(endpoint.portId.value),
                    ),
                )
            } else {
                scalar("${endpoint.nodeId.value}.${endpoint.portId.value}")
            }
        is DiagramEndpoint.FreePoint -> inlinePair(endpoint.x, endpoint.y)
    }

    /** Null for [DiagramRelation.Plain] (the default, omitted). */
    private fun relationValue(relation: DiagramRelation): SlmYaml? = when (relation) {
        DiagramRelation.Plain -> null
        is DiagramRelation.Association ->
            if (relation.directed) {
                SlmYaml.Mapping(
                    listOf(
                        "type" to SlmYaml.Scalar("association"),
                        "directed" to scalar(true),
                    ),
                )
            } else {
                SlmYaml.Scalar("association")
            }
        DiagramRelation.Aggregation -> SlmYaml.Scalar("aggregation")
        DiagramRelation.Composition -> SlmYaml.Scalar("composition")
        DiagramRelation.Generalization -> SlmYaml.Scalar("generalization")
        DiagramRelation.Dependency -> SlmYaml.Scalar("dependency")
        DiagramRelation.Realization -> SlmYaml.Scalar("realization")
        DiagramRelation.Transition -> SlmYaml.Scalar("transition")
        DiagramRelation.Include -> SlmYaml.Scalar("include")
        DiagramRelation.Extend -> SlmYaml.Scalar("extend")
        is DiagramRelation.Message -> SlmYaml.Mapping(
            listOf(
                "type" to SlmYaml.Scalar("message"),
                "kind" to SlmYaml.Scalar(relation.kind.slmToken()),
            ),
        )
        is DiagramRelation.EntityRelation ->
            if (relation.sourceCardinality == ErCardinality.ONE &&
                relation.targetCardinality == ErCardinality.MANY
            ) {
                SlmYaml.Scalar("er")
            } else {
                SlmYaml.Mapping(
                    listOf(
                        "type" to SlmYaml.Scalar("er"),
                        "source" to SlmYaml.Scalar(relation.sourceCardinality.slmToken()),
                        "target" to SlmYaml.Scalar(relation.targetCardinality.slmToken()),
                    ),
                )
            }
    }

    // --- labels ---

    private fun labelValue(label: DiagramLabel): SlmYaml =
        if (!label.markdown) {
            scalar(label.text)
        } else {
            SlmYaml.Mapping(
                listOf(
                    "text" to scalar(label.text),
                    "markdown" to scalar(true),
                ),
            )
        }

    private fun nodeLabelEntries(labels: List<DiagramLabel>): List<Pair<String, SlmYaml>> =
        when {
            labels.isEmpty() -> emptyList()
            labels.size == 1 -> listOf("label" to labelValue(labels.single()))
            else -> listOf("labels" to SlmYaml.Sequence(labels.map(::labelValue)))
        }

    private fun edgeLabelEntries(labels: List<DiagramEdgeLabel>): List<Pair<String, SlmYaml>> {
        if (labels.isEmpty()) return emptyList()
        val single = labels.singleOrNull()
        if (single != null &&
            single.position == DiagramEdgeLabelPosition.MIDDLE &&
            single.offsetX == 0.0 && single.offsetY == 0.0
        ) {
            return listOf("label" to labelValue(single.label))
        }
        return listOf(
            "labels" to SlmYaml.Sequence(
                labels.map { edgeLabel ->
                    SlmYaml.Mapping(
                        buildList {
                            add("text" to scalar(edgeLabel.label.text))
                            if (edgeLabel.label.markdown) add("markdown" to scalar(true))
                            if (edgeLabel.position != DiagramEdgeLabelPosition.MIDDLE) {
                                add("position" to SlmYaml.Scalar(edgeLabel.position.slmToken()))
                            }
                            if (edgeLabel.offsetX != 0.0) add("dx" to scalar(edgeLabel.offsetX))
                            if (edgeLabel.offsetY != 0.0) add("dy" to scalar(edgeLabel.offsetY))
                        },
                    )
                },
            ),
        )
    }

    // --- style / arrowheads ---

    /** Null when the style equals [DiagramStyle.Default] (omitted entirely). */
    private fun styleMapping(style: DiagramStyle): SlmYaml? {
        if (style == DiagramStyle.Default) return null
        return SlmYaml.Mapping(
            buildList {
                style.fill?.let { add("fill" to scalar(formatDiagramColor(it))) }
                style.stroke?.let { add("stroke" to scalar(formatDiagramColor(it))) }
                if (style.strokeWidth != 1.0) add("strokeWidth" to scalar(style.strokeWidth))
                if (style.pattern != DiagramStrokePattern.SOLID) {
                    add("pattern" to SlmYaml.Scalar(style.pattern.slmToken()))
                }
                if (style.opacity != 1.0) add("opacity" to scalar(style.opacity))
                if (style.cornerStyle != DiagramCornerStyle.SHARP) {
                    add("corners" to SlmYaml.Scalar(style.cornerStyle.slmToken()))
                }
                if (style.sketch) add("sketch" to scalar(true))
                if (style.shadow) add("shadow" to scalar(true))
            },
        )
    }

    /** Null when both ends are [DiagramArrowhead.None] (relation notation applies). */
    private fun arrowheadsMapping(
        source: DiagramArrowhead,
        target: DiagramArrowhead,
    ): SlmYaml? {
        if (source == DiagramArrowhead.None && target == DiagramArrowhead.None) return null
        return SlmYaml.Mapping(
            buildList {
                if (source != DiagramArrowhead.None) add("source" to arrowheadValue(source))
                if (target != DiagramArrowhead.None) add("target" to arrowheadValue(target))
            },
        )
    }

    private fun arrowheadValue(arrowhead: DiagramArrowhead): SlmYaml =
        if (arrowhead.size == 8.0 && arrowhead.inset == 0.0) {
            SlmYaml.Scalar(arrowhead.kind.slmToken())
        } else {
            SlmYaml.Mapping(
                buildList {
                    add("kind" to SlmYaml.Scalar(arrowhead.kind.slmToken()))
                    if (arrowhead.size != 8.0) add("size" to scalar(arrowhead.size))
                    if (arrowhead.inset != 0.0) add("inset" to scalar(arrowhead.inset))
                },
            )
        }
}
