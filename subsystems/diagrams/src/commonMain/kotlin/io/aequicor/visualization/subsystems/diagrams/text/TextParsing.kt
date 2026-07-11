package io.aequicor.visualization.subsystems.diagrams.text

import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.model.diagramGraph

/** A trimmed, non-empty source line with its original 1-based line number. */
internal data class SourceLine(
    val number: Int,
    val text: String,
)

/**
 * Splits [source] into trimmed non-empty lines. `%%`-style inline comments are cut when
 * [inlineCommentPrefix] is set; lines starting with [lineCommentPrefix] are dropped whole.
 */
internal fun sourceLines(
    source: String,
    inlineCommentPrefix: String? = null,
    lineCommentPrefix: String? = null,
): List<SourceLine> = source.lines().mapIndexedNotNull { index, raw ->
    var text = raw.trim()
    if (lineCommentPrefix != null && text.startsWith(lineCommentPrefix)) return@mapIndexedNotNull null
    if (inlineCommentPrefix != null) text = text.substringBefore(inlineCommentPrefix).trim()
    if (text.isEmpty()) null else SourceLine(index + 1, text)
}

internal fun failure(line: Int, message: String): TextDiagramResult.Failure =
    TextDiagramResult.Failure(listOf(TextDiagramDiagnostic(line, message)))

// --- UML member parsing (shared by Mermaid classDiagram and PlantUML class) ------------

/**
 * Parses one class-body member row: optional visibility prefix (`+ - # ~`), PlantUML
 * `{static}` / `{abstract}` modifiers, Mermaid `$` (static) / `*` (abstract) suffixes.
 */
internal fun parseUmlMember(raw: String): UmlMember? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    val visibility = when (text.first()) {
        '+' -> UmlVisibility.PUBLIC
        '-' -> UmlVisibility.PRIVATE
        '#' -> UmlVisibility.PROTECTED
        '~' -> UmlVisibility.PACKAGE
        else -> null
    }
    if (visibility != null) text = text.drop(1).trim()
    var isStatic = false
    var isAbstract = false
    if (text.contains("{static}")) {
        isStatic = true
        text = text.replace("{static}", "").trim()
    }
    if (text.contains("{abstract}")) {
        isAbstract = true
        text = text.replace("{abstract}", "").trim()
    }
    if (text.endsWith('$')) {
        isStatic = true
        text = text.dropLast(1).trim()
    }
    if (text.endsWith('*')) {
        isAbstract = true
        text = text.dropLast(1).trim()
    }
    if (text.isEmpty()) return null
    return UmlMember(
        text = text,
        visibility = visibility ?: UmlVisibility.PUBLIC,
        static = isStatic,
        abstract = isAbstract,
    )
}

internal val UmlMember.isOperation: Boolean get() = text.contains('(')

// --- Class relations (shared token vocabulary) -----------------------------------------

/** A class-diagram relation normalized to our model's source/target semantics. */
internal data class ClassRelationSpec(
    val sourceId: String,
    val targetId: String,
    val relation: DiagramRelation,
)

/** Relation tokens, longest-prefix-first so regex alternation matches greedily. */
private val CLASS_RELATION_TOKENS: List<String> = listOf(
    "<|--", "<|..", "--|>", "..|>", "*--", "--*", "o--", "--o",
    "-->", "<--", "..>", "<..", "--", "..",
)

/**
 * Our model's notation anchors: generalization/realization triangle at TARGET,
 * aggregation/composition diamond at SOURCE, dependency arrow at TARGET — so
 * `A <|-- B` (triangle at A) becomes source=B, target=A, Generalization.
 */
internal fun classRelationSpec(left: String, token: String, right: String): ClassRelationSpec? =
    when (token) {
        "<|--" -> ClassRelationSpec(right, left, DiagramRelation.Generalization)
        "<|.." -> ClassRelationSpec(right, left, DiagramRelation.Realization)
        "--|>" -> ClassRelationSpec(left, right, DiagramRelation.Generalization)
        "..|>" -> ClassRelationSpec(left, right, DiagramRelation.Realization)
        "*--" -> ClassRelationSpec(left, right, DiagramRelation.Composition)
        "--*" -> ClassRelationSpec(right, left, DiagramRelation.Composition)
        "o--" -> ClassRelationSpec(left, right, DiagramRelation.Aggregation)
        "--o" -> ClassRelationSpec(right, left, DiagramRelation.Aggregation)
        "-->" -> ClassRelationSpec(left, right, DiagramRelation.Association(directed = true))
        "<--" -> ClassRelationSpec(right, left, DiagramRelation.Association(directed = true))
        "..>" -> ClassRelationSpec(left, right, DiagramRelation.Dependency)
        "<.." -> ClassRelationSpec(right, left, DiagramRelation.Dependency)
        "--" -> ClassRelationSpec(left, right, DiagramRelation.Association())
        ".." -> ClassRelationSpec(left, right, DiagramRelation.Dependency)
        else -> null
    }

internal val classRelationRegex: Regex = Regex(
    """^([A-Za-z_][\w.]*)\s*(?:"[^"]*"\s*)?(""" +
        CLASS_RELATION_TOKENS.joinToString("|") { Regex.escape(it) } +
        """)\s*(?:"[^"]*"\s*)?([A-Za-z_][\w.]*)\s*(?::\s*(.+))?$""",
)

// --- Shared class-diagram body parser ---------------------------------------------------

private val classDeclarationRegex =
    Regex("""^(abstract\s+)?(class|interface|enum)\s+([A-Za-z_][\w.]*)\s*(\{)?\s*$""")

private val classMemberColonRegex = Regex("""^([A-Za-z_][\w.]*)\s*:\s*(.+)$""")

internal fun isClassDeclaration(text: String): Boolean = classDeclarationRegex.matches(text)

private class ClassDraft(val name: String) {
    var stereotype: String? = null
    var abstract: Boolean = false
    val attributes = mutableListOf<UmlMember>()
    val operations = mutableListOf<UmlMember>()

    fun addMember(member: UmlMember) {
        if (member.isOperation) operations += member else attributes += member
    }

    fun toPayload(): UmlClassNode = UmlClassNode(
        name = name,
        stereotype = stereotype,
        abstract = abstract,
        attributes = attributes.toList(),
        operations = operations.toList(),
    )

    val height: Double
        get() = (CLASS_HEADER_HEIGHT + CLASS_MEMBER_ROW_HEIGHT * (attributes.size + operations.size))
            .coerceAtLeast(56.0)
}

internal const val CLASS_NODE_WIDTH: Double = 180.0
internal const val CLASS_HEADER_HEIGHT: Double = 36.0
internal const val CLASS_MEMBER_ROW_HEIGHT: Double = 20.0

private data class RelationDraft(
    val spec: ClassRelationSpec,
    val label: String?,
)

/**
 * Parses a class-diagram body (Mermaid `classDiagram` and PlantUML share the grammar
 * subset we support: `class X { members }`, `X : member`, relation lines) into an
 * unlaid-out [DiagramGraph]. Errors go to [errors]; skipped directives to [warnings].
 */
internal fun parseClassDiagramBody(
    lines: List<SourceLine>,
    errors: MutableList<TextDiagramDiagnostic>,
    warnings: MutableList<TextDiagramDiagnostic>,
): DiagramGraph {
    val classes = LinkedHashMap<String, ClassDraft>()
    val relations = mutableListOf<RelationDraft>()
    fun classDraft(name: String): ClassDraft = classes.getOrPut(name) { ClassDraft(name) }

    var openClass: ClassDraft? = null
    for (line in lines) {
        val text = line.text
        val current = openClass
        if (current != null) {
            if (text == "}") {
                openClass = null
            } else {
                parseUmlMember(text)?.let(current::addMember)
            }
            continue
        }
        val declaration = classDeclarationRegex.matchEntire(text)
        if (declaration != null) {
            val draft = classDraft(declaration.groupValues[3])
            if (declaration.groupValues[1].isNotEmpty()) draft.abstract = true
            when (declaration.groupValues[2]) {
                "interface" -> draft.stereotype = "interface"
                "enum" -> draft.stereotype = "enumeration"
            }
            if (declaration.groupValues[4] == "{") openClass = draft
            continue
        }
        val relation = classRelationRegex.matchEntire(text)
        if (relation != null) {
            val spec = classRelationSpec(
                left = relation.groupValues[1],
                token = relation.groupValues[2],
                right = relation.groupValues[3],
            )
            if (spec == null) {
                errors += TextDiagramDiagnostic(line.number, "unsupported relation in: $text")
            } else {
                classDraft(spec.sourceId)
                classDraft(spec.targetId)
                relations += RelationDraft(spec, relation.groupValues[4].ifEmpty { null })
            }
            continue
        }
        val colonMember = classMemberColonRegex.matchEntire(text)
        if (colonMember != null) {
            parseUmlMember(colonMember.groupValues[2])
                ?.let { classDraft(colonMember.groupValues[1]).addMember(it) }
            continue
        }
        if (text.startsWith("direction") || text.startsWith("hide") || text.startsWith("show") ||
            text.startsWith("skinparam") || text.startsWith("title")
        ) {
            warnings += TextDiagramDiagnostic(line.number, "ignored directive: $text")
            continue
        }
        errors += TextDiagramDiagnostic(line.number, "unrecognized class-diagram statement: $text")
    }

    return diagramGraph {
        val ids = classes.values.associate { draft ->
            draft.name to node(
                id = draft.name,
                width = CLASS_NODE_WIDTH,
                height = draft.height,
                payload = draft.toPayload(),
            )
        }
        relations.forEachIndexed { index, draft ->
            edge(
                id = "e${index + 1}",
                source = DiagramEndpoint.FloatingAnchor(ids.getValue(draft.spec.sourceId)),
                target = DiagramEndpoint.FloatingAnchor(ids.getValue(draft.spec.targetId)),
                relation = draft.spec.relation,
                labels = draft.label
                    ?.let { listOf(DiagramEdgeLabel(DiagramLabel(it))) }
                    ?: emptyList(),
            )
        }
    }
}
