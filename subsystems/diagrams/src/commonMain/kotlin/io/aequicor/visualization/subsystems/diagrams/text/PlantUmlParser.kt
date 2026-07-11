package io.aequicor.visualization.subsystems.diagrams.text

import io.aequicor.visualization.subsystems.diagrams.layout.DiagramLayoutConfig
import io.aequicor.visualization.subsystems.diagrams.layout.autoLayout
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind

/**
 * Parses a PlantUML source (class or sequence diagram minimum) into a laid-out
 * [io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph].
 *
 * - Class: `class X { +field +method() }`, `abstract class X`, `interface X`,
 *   relations `X <|-- Y`, `X --|> Y`, `X *-- Y`, `X o-- Y`, `X --> Y`, `X ..> Y`,
 *   `X ..|> Y` with `: label`.
 * - Sequence: `participant A`, `participant "Name" as A`, `actor A`, messages
 *   `A -> B : msg` (sync), `A ->> B : msg` (async), `A --> B : msg` (return).
 *
 * `@startuml` / `@enduml` fences and `'` line comments are ignored. The diagram type is
 * detected from the statements; sources that declare neither classes nor participants
 * nor messages fail with a diagnostic.
 */
fun plantUmlToDiagram(
    source: String,
    config: DiagramLayoutConfig = DiagramLayoutConfig.Default,
): TextDiagramResult {
    val lines = sourceLines(source, lineCommentPrefix = "'")
        .filterNot { it.text.startsWith("@startuml") || it.text.startsWith("@enduml") }
    if (lines.isEmpty()) return failure(1, "empty source: expected PlantUML statements")

    val classMode = lines.any { line ->
        isClassDeclaration(line.text) || containsClassOnlyRelation(line.text)
    }
    if (classMode) return parsePlantUmlClass(lines, config)

    val sequenceMode = lines.any { line ->
        plantUmlParticipantRegex.matches(line.text) || plantUmlMessageRegex.matches(line.text)
    }
    if (sequenceMode) return parsePlantUmlSequence(lines)

    return failure(lines.first().number, "unable to detect PlantUML diagram type (class/sequence)")
}

/** Tokens that only occur in class diagrams (never in sequence messages). */
private val classOnlyRelationTokens = listOf("<|--", "--|>", "<|..", "..|>", "*--", "--*", "o--", "--o", "..>", "<..")

private fun containsClassOnlyRelation(text: String): Boolean {
    val relation = classRelationRegex.matchEntire(text) ?: return false
    return relation.groupValues[2] in classOnlyRelationTokens
}

// --- class ------------------------------------------------------------------------------

private fun parsePlantUmlClass(
    lines: List<SourceLine>,
    config: DiagramLayoutConfig,
): TextDiagramResult {
    val errors = mutableListOf<TextDiagramDiagnostic>()
    val warnings = mutableListOf<TextDiagramDiagnostic>()
    val graph = parseClassDiagramBody(lines, errors, warnings)
    if (errors.isNotEmpty()) return TextDiagramResult.Failure(errors)
    if (graph.nodes.isEmpty()) return failure(lines.first().number, "class diagram contains no classes")
    return TextDiagramResult.Success(autoLayout(graph, config = config), warnings)
}

// --- sequence ---------------------------------------------------------------------------

private val plantUmlParticipantRegex =
    Regex("""^(participant|actor)\s+(?:"([^"]+)"|([A-Za-z_][\w.]*))(?:\s+as\s+([A-Za-z_][\w.]*))?$""")
private val plantUmlMessageRegex =
    Regex("""^([A-Za-z_][\w.]*)\s*(-->>|->>|-->|->)\s*([A-Za-z_][\w.]*)\s*(?::\s*(.*))?$""")

private fun plantUmlMessageKind(token: String): UmlMessageKind = when (token) {
    "->" -> UmlMessageKind.SYNC
    "->>" -> UmlMessageKind.ASYNC
    else -> UmlMessageKind.RETURN
}

private fun parsePlantUmlSequence(lines: List<SourceLine>): TextDiagramResult {
    val errors = mutableListOf<TextDiagramDiagnostic>()
    val warnings = mutableListOf<TextDiagramDiagnostic>()
    val participants = LinkedHashMap<String, UmlLifelineNode>()
    val messages = mutableListOf<SequenceMessage>()

    fun participant(id: String): String {
        participants.getOrPut(id) { UmlLifelineNode(name = id) }
        return id
    }

    for (line in lines) {
        val text = line.text
        val declaration = plantUmlParticipantRegex.matchEntire(text)
        if (declaration != null) {
            val quotedName = declaration.groupValues[2]
            val bareName = declaration.groupValues[3]
            val alias = declaration.groupValues[4]
            val display = quotedName.ifEmpty { bareName }
            val id = alias.ifEmpty { display }
            participants[id] = UmlLifelineNode(
                name = display,
                actor = declaration.groupValues[1] == "actor",
            )
            continue
        }
        val message = plantUmlMessageRegex.matchEntire(text)
        if (message != null) {
            messages += SequenceMessage(
                fromId = participant(message.groupValues[1]),
                toId = participant(message.groupValues[3]),
                kind = plantUmlMessageKind(message.groupValues[2]),
                text = message.groupValues[4].trim().ifEmpty { null },
            )
            continue
        }
        if (text.startsWith("autonumber") || text.startsWith("activate") ||
            text.startsWith("deactivate") || text.startsWith("note") || text == "end note" ||
            text.startsWith("title") || text.startsWith("skinparam") || text.startsWith("hide")
        ) {
            warnings += TextDiagramDiagnostic(line.number, "ignored statement: $text")
            continue
        }
        errors += TextDiagramDiagnostic(line.number, "unrecognized sequence-diagram statement: $text")
    }

    if (errors.isNotEmpty()) return TextDiagramResult.Failure(errors)
    if (participants.isEmpty()) return failure(lines.first().number, "sequence diagram contains no participants")

    return TextDiagramResult.Success(buildSequenceGraph(participants, messages), warnings)
}
