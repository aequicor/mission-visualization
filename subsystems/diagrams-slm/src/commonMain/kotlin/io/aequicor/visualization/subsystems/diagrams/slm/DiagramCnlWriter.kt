package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.cnl.CnlLexemes
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
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
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
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

/**
 * Deterministic [DiagramGraph] → diagram CNL sentences: one line per element, canonical
 * order `Layer*` → `Node*` → `Edge*` → `Group*` (model list order inside each), canonical
 * phrase order inside each sentence, defaults omitted.
 * `parse(emit(graph)) == graph` through [DiagramCnlReader] for every valid graph.
 */
public object DiagramCnlWriter {

    /** The canonical body sentences of a `## Diagram: …` container, one line each. */
    public fun sentences(graph: DiagramGraph): List<String> =
        graph.layers.map(::layerSentence) +
            graph.nodes.map(::nodeSentence) +
            graph.edges.map { edgeSentence(it, graph) } +
            graph.groups.map(::groupSentence)

    /** The body as one text block (no trailing newline); empty for an empty graph. */
    public fun bodyText(graph: DiagramGraph): String = sentences(graph).joinToString("\n")

    // --- lexemes ---

    private fun num(value: Double): String = CnlLexemes.num(value)

    private fun quote(value: String): String = CnlLexemes.quoteText(value)

    private val idUnsafe = setOf(' ', '\t', '(', ')', '«', '»', '"')

    /** Plain id token, quoted only when it is empty or carries lexeme-breaking characters. */
    private fun idToken(value: String): String =
        if (value.isEmpty() || value.any { it in idUnsafe }) quote(value) else value

    /** Canonical CNL enum word: lowercase with `-` in place of `_`. */
    private fun Enum<*>.cnlToken(): String = name.lowercase().replace('_', '-')

    private fun labelToken(label: DiagramLabel): String =
        if (label.markdown) "(${quote(label.text)} markdown)" else quote(label.text)

    // --- Layer / Group ---

    private fun layerSentence(layer: DiagramLayer): String = buildList {
        add("Layer")
        add(idToken(layer.id.value))
        if (layer.name != layer.id.value) add(quote(layer.name))
        if (!layer.visible) add("visible no")
        if (layer.locked) add("locked yes")
    }.joinToString(" ")

    private fun groupSentence(group: DiagramGroup): String = buildList {
        add("Group")
        add(idToken(group.id.value))
        group.name?.let { add(quote(it)) }
        add("members (${group.memberIds.joinToString(" ") { idToken(it.value) }})")
    }.joinToString(" ")

    // --- Node ---

    private fun nodeSentence(node: DiagramNode): String = buildList {
        add("Node")
        add(payloadTypeWord(node.payload))
        add(idToken(node.id.value))
        addAll(payloadHead(node.payload))
        add("${num(node.width)} by ${num(node.height)}")
        if (node.sizing == DiagramNodeSizing.Hug) add("hug")
        add("position ${num(node.x)} ${num(node.y)}")
        if (node.rotation != 0.0) add("rotate ${num(node.rotation)}")
        addAll(payloadItems(node.payload))
        node.ports.forEach { add(portPhrase(it)) }
        styleGroup(node.style)?.let { add("style $it") }
        node.labels.forEach { add("label ${labelToken(it)}") }
        node.parentId?.let { add("parent ${idToken(it.value)}") }
        node.layerId?.let { add("layer ${idToken(it.value)}") }
        if (node.locked) add("locked yes")
        if (!node.visible) add("visible no")
    }.joinToString(" ")

    private fun payloadTypeWord(payload: DiagramNodePayload): String =
        payloadTypeToken(payload).replace('_', '-')

    /** Scalar identity parts emitted between the id and the size (§4.2 head order). */
    private fun payloadHead(payload: DiagramNodePayload): List<String> = when (payload) {
        is DiagramNodePayload.BasicShape -> emptyList()
        is DiagramNodePayload.ContainerNode -> buildList {
            payload.title?.let { add("title ${labelToken(it)}") }
            if (payload.collapsed) add("collapsed")
        }
        is DiagramNodePayload.SwimlaneNode -> buildList {
            if (payload.orientation == DiagramOrientation.VERTICAL) add("vertical")
            payload.title?.let { add("title ${labelToken(it)}") }
        }
        is DiagramNodePayload.FlowchartNode -> listOf(payload.kind.cnlToken())
        is DiagramNodePayload.ErEntityNode -> listOf(quote(payload.name))
        is DiagramNodePayload.BpmnNode -> listOf(payload.kind.cnlToken())
        is TableNode -> emptyList()
        is UmlClassNode -> buildList {
            add(quote(payload.name))
            payload.stereotype?.let { add("stereotype ${quote(it)}") }
            if (payload.abstract) add("abstract")
        }
        is UmlLifelineNode -> buildList {
            add(quote(payload.name))
            if (payload.actor) add("actor")
        }
        is UmlStateNode -> buildList {
            if (payload.name.isNotEmpty()) add(quote(payload.name))
            if (payload.kind != UmlStateKind.SIMPLE) add(payload.kind.cnlToken())
        }
        is UmlActivityNode -> buildList {
            add(payload.kind.cnlToken())
            if (payload.name.isNotEmpty()) add(quote(payload.name))
        }
        is UmlActorNode -> listOf(quote(payload.name))
        is UmlUseCaseNode -> listOf(quote(payload.name))
        is UmlComponentNode -> buildList {
            add(quote(payload.name))
            payload.stereotype?.let { add("stereotype ${quote(it)}") }
        }
        is UmlDeploymentNode -> buildList {
            add(quote(payload.name))
            payload.stereotype?.let { add("stereotype ${quote(it)}") }
        }
        is UmlNoteNode -> listOf(quote(payload.text))
        is UmlPackageNode -> listOf(quote(payload.name))
    }

    /** Repeated record groups emitted after rotation (§4.2 items order, list order kept). */
    private fun payloadItems(payload: DiagramNodePayload): List<String> = when (payload) {
        is DiagramNodePayload.SwimlaneNode -> payload.lanes.map { lane ->
            val title = lane.title
            when {
                title == null -> "lane ${num(lane.size)}"
                lane.size != 120.0 -> "lane (${labelToken(title)} ${num(lane.size)})"
                else -> "lane (${labelToken(title)})"
            }
        }
        is DiagramNodePayload.ErEntityNode -> payload.attributes.map { attribute ->
            val parts = buildList {
                add(quote(attribute.name))
                attribute.type?.let { add("type ${quote(it)}") }
                if (attribute.primaryKey) add("pk")
                if (attribute.foreignKey) add("fk")
            }
            "attribute (${parts.joinToString(" ")})"
        }
        is TableNode -> buildList {
            payload.rows.forEach { row ->
                add(if (row.header) "row (${num(row.height)} header)" else "row ${num(row.height)}")
            }
            payload.columns.forEach { column ->
                add(if (column.header) "col (${num(column.width)} header)" else "col ${num(column.width)}")
            }
            payload.cells.forEach { cell ->
                val parts = buildList {
                    add("${cell.row} ${cell.column}")
                    if (cell.isMerged) add("span ${cell.rowSpan} by ${cell.colSpan}")
                    cell.label?.let { label ->
                        add(if (label.markdown) "label (${quote(label.text)} markdown)" else quote(label.text))
                    }
                    cell.style?.let { style -> styleGroup(style)?.let { add("style $it") } }
                }
                add("cell (${parts.joinToString(" ")})")
            }
        }
        is UmlClassNode ->
            payload.attributes.map { "field ${memberGroup(it)}" } +
                payload.operations.map { "method ${memberGroup(it)}" }
        is UmlLifelineNode -> payload.activations.map { "activation (${num(it.start)} ${num(it.end)})" }
        else -> emptyList()
    }

    private fun memberGroup(member: UmlMember): String {
        val parts = buildList {
            add(member.visibility.symbol.toString())
            if (member.static) add("static")
            if (member.abstract) add("abstract")
            add(quote(member.text))
        }
        return "(${parts.joinToString(" ")})"
    }

    private fun portPhrase(port: DiagramPort): String = when (val anchor = port.anchor) {
        is DiagramPortAnchor.SideOffset -> {
            val offset = if (anchor.offset != 0.5) " ${num(anchor.offset)}" else ""
            "port (${idToken(port.id.value)} ${anchor.side.cnlToken()}$offset)"
        }
        is DiagramPortAnchor.RelativePoint ->
            "port (${idToken(port.id.value)} at ${num(anchor.x)} ${num(anchor.y)})"
    }

    /** `( … )` style group with §4.3 key order, or null when the style is the default. */
    private fun styleGroup(style: DiagramStyle): String? {
        if (style == DiagramStyle.Default) return null
        val parts = buildList {
            style.fill?.let { add("fill ${formatCnlDiagramColor(it)}") }
            style.stroke?.let { add("stroke ${formatCnlDiagramColor(it)}") }
            if (style.strokeWidth != 1.0) add("weight ${num(style.strokeWidth)}")
            if (style.pattern != DiagramStrokePattern.SOLID) add("pattern ${style.pattern.cnlToken()}")
            if (style.opacity != 1.0) add("opacity ${num(style.opacity)}")
            if (style.cornerStyle != DiagramStyle.Default.cornerStyle) add("corners ${style.cornerStyle.cnlToken()}")
            if (style.sketch) add("sketch")
            if (style.shadow) add("shadow")
        }
        return "(${parts.joinToString(" ")})"
    }

    // --- Edge ---

    private fun edgeSentence(edge: DiagramEdge, graph: DiagramGraph): String = buildList {
        add("Edge")
        add(idToken(edge.id.value))
        add("from ${endpointToken(edge.source, graph)}")
        add("to ${endpointToken(edge.target, graph)}")
        relationPhrase(edge.relation)?.let { add(it) }
        if (edge.routing != DiagramRoutingStyle.ORTHOGONAL) add("routing ${edge.routing.cnlToken()}")
        edge.waypoints.forEach { add("via (${num(it.x)} ${num(it.y)})") }
        edge.labels.forEach { add(edgeLabelPhrase(it, edge.labels.size)) }
        styleGroup(edge.style)?.let { add("style $it") }
        arrowheadPhrase(edge.sourceArrowhead)?.let { add("arrow source $it") }
        arrowheadPhrase(edge.targetArrowhead)?.let { add("arrow target $it") }
        if (edge.lineJumps != LineJumpStyle.ARC) add("jumps ${edge.lineJumps.cnlToken()}")
        if (edge.connectionMode != DiagramConnectionMode.LINE) add("mode ${edge.connectionMode.cnlToken()}")
        if (edge.flowAnimation) add("animated yes")
        edge.layerId?.let { add("layer ${idToken(it.value)}") }
    }.joinToString(" ")

    /**
     * Bare `id` / `id.port` / `(x y)`; the explicit `(node …)` group whenever a dot would
     * make the bare form ambiguous — a dotted id, a dotted port, or a fixed port whose
     * `node.port` spelling collides with an existing dotted node id.
     */
    private fun endpointToken(endpoint: DiagramEndpoint, graph: DiagramGraph): String = when (endpoint) {
        is DiagramEndpoint.FreePoint -> "(${num(endpoint.x)} ${num(endpoint.y)})"
        is DiagramEndpoint.FloatingAnchor ->
            if ('.' in endpoint.nodeId.value) {
                "(node ${idToken(endpoint.nodeId.value)})"
            } else {
                idToken(endpoint.nodeId.value)
            }
        is DiagramEndpoint.FixedPort -> {
            val dotted = "${endpoint.nodeId.value}.${endpoint.portId.value}"
            val collides = graph.nodes.any { it.id.value == dotted }
            if ('.' in endpoint.nodeId.value || '.' in endpoint.portId.value || collides) {
                "(node ${idToken(endpoint.nodeId.value)} port ${idToken(endpoint.portId.value)})"
            } else {
                idToken(dotted)
            }
        }
    }

    /** Null for [DiagramRelation.Plain] (the default, omitted). */
    private fun relationPhrase(relation: DiagramRelation): String? = when (relation) {
        DiagramRelation.Plain -> null
        is DiagramRelation.Association ->
            if (relation.directed) "relation association directed" else "relation association"
        DiagramRelation.Aggregation -> "relation aggregation"
        DiagramRelation.Composition -> "relation composition"
        DiagramRelation.Generalization -> "relation generalization"
        DiagramRelation.Dependency -> "relation dependency"
        DiagramRelation.Realization -> "relation realization"
        DiagramRelation.Transition -> "relation transition"
        DiagramRelation.Include -> "relation include"
        DiagramRelation.Extend -> "relation extend"
        is DiagramRelation.Message -> "relation message ${relation.kind.cnlToken()}"
        is DiagramRelation.EntityRelation ->
            if (relation.sourceCardinality == ErCardinality.ONE &&
                relation.targetCardinality == ErCardinality.MANY
            ) {
                "relation er"
            } else {
                "relation er ${relation.sourceCardinality.cnlToken()} to ${relation.targetCardinality.cnlToken()}"
            }
    }

    /** Short `label «t»` for a lone default-position plain middle label; group form otherwise. */
    private fun edgeLabelPhrase(label: DiagramEdgeLabel, labelCount: Int): String {
        val isShort = labelCount == 1 &&
            label.position == DiagramEdgeLabelPosition.MIDDLE &&
            label.offsetX == 0.0 && label.offsetY == 0.0 && !label.label.markdown
        if (isShort) return "label ${quote(label.label.text)}"
        val parts = buildList {
            add(quote(label.label.text))
            if (label.label.markdown) add("markdown")
            if (label.position != DiagramEdgeLabelPosition.MIDDLE) add("at ${label.position.cnlToken()}")
            if (label.offsetX != 0.0) add("dx ${num(label.offsetX)}")
            if (label.offsetY != 0.0) add("dy ${num(label.offsetY)}")
        }
        return "label (${parts.joinToString(" ")})"
    }

    /** Null for [DiagramArrowhead.None] (relation notation applies); bare kind when defaults. */
    private fun arrowheadPhrase(arrowhead: DiagramArrowhead): String? {
        if (arrowhead == DiagramArrowhead.None) return null
        if (arrowhead.size == 8.0 && arrowhead.inset == 0.0) return arrowhead.kind.cnlToken()
        val parts = buildList {
            add(arrowhead.kind.cnlToken())
            if (arrowhead.size != 8.0) add("size ${num(arrowhead.size)}")
            if (arrowhead.inset != 0.0) add("inset ${num(arrowhead.inset)}")
        }
        return "(${parts.joinToString(" ")})"
    }
}
