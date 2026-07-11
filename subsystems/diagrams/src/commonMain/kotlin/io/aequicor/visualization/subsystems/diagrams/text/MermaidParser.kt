package io.aequicor.visualization.subsystems.diagrams.text

import io.aequicor.visualization.subsystems.diagrams.layout.DiagramLayoutConfig
import io.aequicor.visualization.subsystems.diagrams.layout.LayoutDirection
import io.aequicor.visualization.subsystems.diagrams.layout.autoLayout
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph

/**
 * Parses a Mermaid diagram source into a laid-out [DiagramGraph]
 * (via [autoLayout]; sequence diagrams place lifelines side by side instead).
 *
 * Supported diagram types:
 * - `flowchart TD|LR` / `graph TD|LR` — nodes `A[..]` (process), `A(..)` (rounded),
 *   `A{..}` (decision), `A((..))` (ellipse), `A([..])` (terminator); edges `-->`, `---`,
 *   `-.->`, `==>`, with `|label|` edge labels and chained statements `A --> B --> C`;
 * - `classDiagram` — `class X { +field +method() }`, `X : member`, relations
 *   `<|--`, `--|>`, `<|..`, `..|>`, `*--`, `o--`, `-->`, `--`, `..>` with `: label`;
 * - `stateDiagram-v2` / `stateDiagram` — `[*]` initial/final, `A --> B : event`,
 *   `state "Title" as id`, `id : description`;
 * - `sequenceDiagram` — `participant A [as Name]`, `actor A`, messages
 *   `->>` (sync), `-->>` (async), `->` (sync), `-->` (return) with `: text`.
 */
fun mermaidToDiagram(
    source: String,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): TextDiagramResult {
    val lines = sourceLines(source, inlineCommentPrefix = "%%")
    val header = lines.firstOrNull()
        ?: return failure(1, "empty source: expected a mermaid diagram header")
    val body = lines.drop(1)
    val headerWord = header.text.substringBefore(' ')
    return when (headerWord) {
        "flowchart", "graph" -> parseMermaidFlowchart(header, body, config)
        "classDiagram" -> parseMermaidClass(body, config)
        "stateDiagram-v2", "stateDiagram" -> parseMermaidState(body, config)
        "sequenceDiagram" -> parseMermaidSequence(body)
        else -> failure(header.number, "unsupported mermaid diagram type: $headerWord")
    }
}

// --- flowchart --------------------------------------------------------------------------

private val flowchartArrowRegex = Regex("""(-\.->|==>|-->|---)\s*(?:\|([^|]*)\|)?""")
private val flowchartNodeRefRegex = Regex("""^([A-Za-z_][\w-]*)\s*(.*)$""")

private val flowchartSkipPrefixes =
    listOf("subgraph", "direction", "style", "classDef", "class ", "click", "linkStyle")

private data class FlowNodeDraft(
    val label: String,
    val payload: DiagramNodePayload,
)

private data class FlowEdgeDraft(
    val fromId: String,
    val toId: String,
    val relation: DiagramRelation,
    val style: DiagramStyle,
    val label: String?,
)

private fun parseMermaidFlowchart(
    header: SourceLine,
    body: List<SourceLine>,
    config: DiagramLayoutConfig,
): TextDiagramResult {
    val errors = mutableListOf<TextDiagramDiagnostic>()
    val warnings = mutableListOf<TextDiagramDiagnostic>()
    val direction = when (header.text.substringAfter(' ', "").trim()) {
        "LR", "RL" -> LayoutDirection.LEFT_RIGHT
        else -> LayoutDirection.TOP_DOWN
    }
    val nodes = LinkedHashMap<String, FlowNodeDraft>()
    val edgeDrafts = mutableListOf<FlowEdgeDraft>()

    fun parseNodeRef(raw: String, line: SourceLine): String? {
        val text = raw.trim()
        val match = flowchartNodeRefRegex.matchEntire(text)
        if (match == null) {
            errors += TextDiagramDiagnostic(line.number, "cannot parse node reference: $text")
            return null
        }
        val id = match.groupValues[1]
        val rest = match.groupValues[2].trim()
        if (rest.isEmpty()) {
            nodes.getOrPut(id) { FlowNodeDraft(id, DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS)) }
            return id
        }
        val (open, close, payload) = when {
            rest.startsWith("((") -> Triple("((", "))", DiagramNodePayload.BasicShape(DiagramShapeKind.ELLIPSE))
            rest.startsWith("([") -> Triple("([", "])", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.TERMINATOR))
            rest.startsWith("[") -> Triple("[", "]", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.PROCESS))
            rest.startsWith("{") -> Triple("{", "}", DiagramNodePayload.FlowchartNode(FlowchartNodeKind.DECISION))
            rest.startsWith("(") -> Triple("(", ")", DiagramNodePayload.BasicShape(DiagramShapeKind.ROUNDED_RECTANGLE))
            else -> {
                errors += TextDiagramDiagnostic(line.number, "unexpected text after node id '$id': $rest")
                return null
            }
        }
        if (!rest.endsWith(close)) {
            errors += TextDiagramDiagnostic(line.number, "unbalanced node brackets in: $text")
            return null
        }
        val label = rest.removePrefix(open).removeSuffix(close).trim().removeSurrounding("\"")
        nodes[id] = FlowNodeDraft(label.ifEmpty { id }, payload)
        return id
    }

    for (line in body) {
        for (statementRaw in line.text.split(';')) {
            val statement = statementRaw.trim()
            if (statement.isEmpty()) continue
            if (statement == "end" || flowchartSkipPrefixes.any { statement.startsWith(it) }) {
                warnings += TextDiagramDiagnostic(line.number, "ignored statement: $statement")
                continue
            }
            val arrows = flowchartArrowRegex.findAll(statement).toList()
            if (arrows.isEmpty()) {
                parseNodeRef(statement, line)
                continue
            }
            val parts = mutableListOf<String>()
            var previousEnd = 0
            for (match in arrows) {
                parts += statement.substring(previousEnd, match.range.first)
                previousEnd = match.range.last + 1
            }
            parts += statement.substring(previousEnd)
            val ids = parts.map { parseNodeRef(it, line) }
            arrows.forEachIndexed { index, match ->
                val fromId = ids[index] ?: return@forEachIndexed
                val toId = ids[index + 1] ?: return@forEachIndexed
                val token = match.groupValues[1]
                val label = match.groupValues[2].trim().ifEmpty { null }
                val relation = if (token == "---") {
                    DiagramRelation.Plain
                } else {
                    DiagramRelation.Association(directed = true)
                }
                val style = when (token) {
                    "-.->" -> DiagramStyle(pattern = DiagramStrokePattern.DASHED)
                    "==>" -> DiagramStyle(strokeWidth = 2.5)
                    else -> DiagramStyle.Default
                }
                edgeDrafts += FlowEdgeDraft(fromId, toId, relation, style, label)
            }
        }
    }

    if (errors.isNotEmpty()) return TextDiagramResult.Failure(errors)
    if (nodes.isEmpty()) return failure(header.number, "flowchart contains no nodes")

    val graph = diagramGraph {
        val nodeIds = nodes.entries.associate { (id, draft) ->
            val decision = (draft.payload as? DiagramNodePayload.FlowchartNode)
                ?.kind == FlowchartNodeKind.DECISION
            id to node(
                id = id,
                width = 140.0,
                height = if (decision) 80.0 else 56.0,
                payload = draft.payload,
                label = draft.label,
            )
        }
        edgeDrafts.forEachIndexed { index, draft ->
            edge(
                id = "e${index + 1}",
                source = DiagramEndpoint.FloatingAnchor(nodeIds.getValue(draft.fromId)),
                target = DiagramEndpoint.FloatingAnchor(nodeIds.getValue(draft.toId)),
                relation = draft.relation,
                style = draft.style,
                labels = draft.label
                    ?.let { listOf(DiagramEdgeLabel(DiagramLabel(it))) }
                    ?: emptyList(),
            )
        }
    }
    return TextDiagramResult.Success(
        graph = autoLayout(graph, config = config.copy(direction = direction)),
        warnings = warnings,
    )
}

// --- classDiagram -----------------------------------------------------------------------

private fun parseMermaidClass(
    body: List<SourceLine>,
    config: DiagramLayoutConfig,
): TextDiagramResult {
    val errors = mutableListOf<TextDiagramDiagnostic>()
    val warnings = mutableListOf<TextDiagramDiagnostic>()
    val graph = parseClassDiagramBody(body, errors, warnings)
    if (errors.isNotEmpty()) return TextDiagramResult.Failure(errors)
    if (graph.nodes.isEmpty()) return failure(1, "class diagram contains no classes")
    return TextDiagramResult.Success(autoLayout(graph, config = config), warnings)
}

// --- stateDiagram -----------------------------------------------------------------------

private val stateTransitionRegex =
    Regex("""^(\[\*\]|[A-Za-z_][\w.]*)\s*-->\s*(\[\*\]|[A-Za-z_][\w.]*)\s*(?::\s*(.+))?$""")
private val stateAliasRegex = Regex("""^state\s+"([^"]*)"\s+as\s+([A-Za-z_][\w.]*)$""")
private val stateDescriptionRegex = Regex("""^([A-Za-z_][\w.]*)\s*:\s*(.+)$""")

private const val STATE_INITIAL_ID = "__initial"
private const val STATE_FINAL_ID = "__final"

private fun parseMermaidState(
    body: List<SourceLine>,
    config: DiagramLayoutConfig,
): TextDiagramResult {
    val errors = mutableListOf<TextDiagramDiagnostic>()
    val warnings = mutableListOf<TextDiagramDiagnostic>()
    val states = LinkedHashMap<String, UmlStateNode>()
    data class Transition(val fromId: String, val toId: String, val label: String?)
    val transitions = mutableListOf<Transition>()

    fun stateId(token: String, isSource: Boolean): String {
        if (token == "[*]") {
            val id = if (isSource) STATE_INITIAL_ID else STATE_FINAL_ID
            val kind = if (isSource) UmlStateKind.INITIAL else UmlStateKind.FINAL
            states.getOrPut(id) { UmlStateNode(kind = kind) }
            return id
        }
        states.getOrPut(token) { UmlStateNode(name = token) }
        return token
    }

    for (line in body) {
        val text = line.text
        val transition = stateTransitionRegex.matchEntire(text)
        if (transition != null) {
            transitions += Transition(
                fromId = stateId(transition.groupValues[1], isSource = true),
                toId = stateId(transition.groupValues[2], isSource = false),
                label = transition.groupValues[3].trim().ifEmpty { null },
            )
            continue
        }
        val alias = stateAliasRegex.matchEntire(text)
        if (alias != null) {
            states[alias.groupValues[2]] = UmlStateNode(name = alias.groupValues[1])
            continue
        }
        val description = stateDescriptionRegex.matchEntire(text)
        if (description != null) {
            states[description.groupValues[1]] = UmlStateNode(name = description.groupValues[2])
            continue
        }
        if (text.startsWith("direction") || text.startsWith("note") || text == "end note") {
            warnings += TextDiagramDiagnostic(line.number, "ignored statement: $text")
            continue
        }
        errors += TextDiagramDiagnostic(line.number, "unrecognized state-diagram statement: $text")
    }

    if (errors.isNotEmpty()) return TextDiagramResult.Failure(errors)
    if (states.isEmpty()) return failure(1, "state diagram contains no states")

    val graph = diagramGraph {
        val ids = states.entries.associate { (id, payload) ->
            val pseudo = payload.kind == UmlStateKind.INITIAL || payload.kind == UmlStateKind.FINAL
            id to node(
                id = id,
                width = if (pseudo) 28.0 else 140.0,
                height = if (pseudo) 28.0 else 48.0,
                payload = payload,
            )
        }
        transitions.forEachIndexed { index, transition ->
            edge(
                id = "e${index + 1}",
                source = DiagramEndpoint.FloatingAnchor(ids.getValue(transition.fromId)),
                target = DiagramEndpoint.FloatingAnchor(ids.getValue(transition.toId)),
                relation = DiagramRelation.Transition,
                labels = transition.label
                    ?.let { listOf(DiagramEdgeLabel(DiagramLabel(it))) }
                    ?: emptyList(),
            )
        }
    }
    return TextDiagramResult.Success(autoLayout(graph, config = config), warnings)
}

// --- sequenceDiagram --------------------------------------------------------------------

private val mermaidParticipantRegex =
    Regex("""^(participant|actor)\s+([A-Za-z_][\w.]*)(?:\s+as\s+(.+))?$""")
private val mermaidMessageRegex =
    Regex("""^([A-Za-z_][\w.]*)\s*(-->>|->>|-->|->)\s*([A-Za-z_][\w.]*)\s*(?::\s*(.*))?$""")

internal const val LIFELINE_WIDTH: Double = 140.0
internal const val LIFELINE_HEIGHT: Double = 320.0
internal const val LIFELINE_SPACING: Double = 200.0

private fun mermaidMessageKind(token: String): UmlMessageKind = when (token) {
    "->>" -> UmlMessageKind.SYNC
    "-->>" -> UmlMessageKind.ASYNC
    "->" -> UmlMessageKind.SYNC
    else -> UmlMessageKind.RETURN
}

private fun parseMermaidSequence(body: List<SourceLine>): TextDiagramResult {
    val errors = mutableListOf<TextDiagramDiagnostic>()
    val warnings = mutableListOf<TextDiagramDiagnostic>()
    val participants = LinkedHashMap<String, UmlLifelineNode>()
    data class Message(val fromId: String, val toId: String, val kind: UmlMessageKind, val text: String?)
    val messages = mutableListOf<Message>()

    fun participant(id: String): String {
        participants.getOrPut(id) { UmlLifelineNode(name = id) }
        return id
    }

    for (line in body) {
        val text = line.text
        val declaration = mermaidParticipantRegex.matchEntire(text)
        if (declaration != null) {
            val id = declaration.groupValues[2]
            participants[id] = UmlLifelineNode(
                name = declaration.groupValues[3].trim().ifEmpty { id },
                actor = declaration.groupValues[1] == "actor",
            )
            continue
        }
        val message = mermaidMessageRegex.matchEntire(text)
        if (message != null) {
            messages += Message(
                fromId = participant(message.groupValues[1]),
                toId = participant(message.groupValues[3]),
                kind = mermaidMessageKind(message.groupValues[2]),
                text = message.groupValues[4].trim().ifEmpty { null },
            )
            continue
        }
        if (text.startsWith("autonumber") || text.startsWith("activate") ||
            text.startsWith("deactivate") || text.startsWith("Note") || text.startsWith("note") ||
            text.startsWith("loop") || text.startsWith("alt") || text.startsWith("else") ||
            text.startsWith("opt") || text == "end"
        ) {
            warnings += TextDiagramDiagnostic(line.number, "ignored statement: $text")
            continue
        }
        errors += TextDiagramDiagnostic(line.number, "unrecognized sequence-diagram statement: $text")
    }

    if (errors.isNotEmpty()) return TextDiagramResult.Failure(errors)
    if (participants.isEmpty()) return failure(1, "sequence diagram contains no participants")

    val graph = buildSequenceGraph(
        participants = participants,
        messages = messages.map { SequenceMessage(it.fromId, it.toId, it.kind, it.text) },
    )
    return TextDiagramResult.Success(graph, warnings)
}

// --- shared sequence graph assembly (also used by the PlantUML parser) -------------------

internal data class SequenceMessage(
    val fromId: String,
    val toId: String,
    val kind: UmlMessageKind,
    val text: String?,
)

internal fun buildSequenceGraph(
    participants: Map<String, UmlLifelineNode>,
    messages: List<SequenceMessage>,
): DiagramGraph = diagramGraph {
    val ids = participants.entries.mapIndexed { index, (id, payload) ->
        id to node(
            id = id,
            x = index * LIFELINE_SPACING,
            y = 0.0,
            width = LIFELINE_WIDTH,
            height = LIFELINE_HEIGHT,
            payload = payload,
        )
    }.toMap()
    messages.forEachIndexed { index, message ->
        edge(
            id = "m${index + 1}",
            source = DiagramEndpoint.FloatingAnchor(ids.getValue(message.fromId)),
            target = DiagramEndpoint.FloatingAnchor(ids.getValue(message.toId)),
            relation = DiagramRelation.Message(message.kind),
            routing = DiagramRoutingStyle.STRAIGHT,
            labels = message.text
                ?.let { listOf(DiagramEdgeLabel(DiagramLabel(it))) }
                ?: emptyList(),
        )
    }
}
