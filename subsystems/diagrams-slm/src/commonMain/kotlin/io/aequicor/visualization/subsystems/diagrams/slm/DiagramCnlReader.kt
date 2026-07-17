package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.frontend.blocks.CnlContainerLine
import io.aequicor.visualization.engine.frontend.cnl.CnlLexemes
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.subsystems.diagrams.model.BpmnNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramColor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramConnectionMode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramCornerStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeLabelPosition
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEndpoint
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroup
import io.aequicor.visualization.subsystems.diagrams.model.DiagramGroupId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayer
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLayerId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSide
import io.aequicor.visualization.subsystems.diagrams.model.DiagramOrientation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPort
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortAnchor
import io.aequicor.visualization.subsystems.diagrams.model.DiagramPortId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRoutingStyle
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStyle
import io.aequicor.visualization.subsystems.diagrams.model.ErAttribute
import io.aequicor.visualization.subsystems.diagrams.model.ErCardinality
import io.aequicor.visualization.subsystems.diagrams.model.FlowchartNodeKind
import io.aequicor.visualization.subsystems.diagrams.model.LineJumpStyle
import io.aequicor.visualization.subsystems.diagrams.model.SwimlaneLane
import io.aequicor.visualization.subsystems.diagrams.model.TableCell
import io.aequicor.visualization.subsystems.diagrams.model.TableColumn
import io.aequicor.visualization.subsystems.diagrams.model.TableNode
import io.aequicor.visualization.subsystems.diagrams.model.TableRow
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivation
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlActivityNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlActorNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlComponentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlDeploymentNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlLifelineNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlMember
import io.aequicor.visualization.subsystems.diagrams.model.UmlMessageKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlNoteNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlPackageNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlStateNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlVisibility
import io.aequicor.visualization.subsystems.diagrams.ops.primaryText
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * The diagram CNL sentence grammar: one line per diagram element inside a `## Diagram: …`
 * container body. `Node`/`Edge`/`Layer`/`Group` are matched at token[0] (scoped nouns —
 * never added to the global CNL vocabulary); all sub-keywords resolve sentence-locally.
 * The semantic source of truth for diagram authoring (defaults, coercions, drops,
 * first-id-wins) — the deterministic inverse of [DiagramCnlWriter].
 *
 * Lenient by design: malformed values coerce or drop with a diagnostic, an unknown
 * type/kind/enum word drops the sentence with an error — never a throw.
 */
internal object DiagramCnlReader {

    private const val BLOCK_PATH = "diagram"
    private val numberRegex = Regex("""-?\d+(\.\d+)?""")
    private val sizeConnectors = setOf("by", "x", "×", "*")
    private val boolWords = mapOf(
        "yes" to true, "no" to false, "on" to true, "off" to false, "true" to true, "false" to false,
    )

    // --- tokenizer (mirrors CnlParser.tokenize: text literals, parens, plain tokens) ---

    private data class Tok(val text: String, val isText: Boolean)

    private sealed interface GNode
    private data class GLeaf(val tok: Tok) : GNode
    private data class GGroup(val children: List<GNode>) : GNode

    private fun GNode.word(): String? = (this as? GLeaf)?.tok?.takeUnless { it.isText }?.text
    private fun GNode.lowerWord(): String? = word()?.lowercase()
    private fun GNode.text(): String? = (this as? GLeaf)?.tok?.takeIf { it.isText }?.text
    private fun GNode.idToken(): String? = (this as? GLeaf)?.tok?.text
    private fun GNode.number(): Double? = word()?.takeIf { numberRegex.matches(it) }?.toDouble()
    private fun GNode.group(): GGroup? = this as? GGroup

    private fun tokenize(line: String, lineNumber: Int, diagnostics: DiagnosticCollector): List<Tok> {
        val tokens = mutableListOf<Tok>()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == ' ' || c == '\t' -> i++
                c == '«' || c == '"' -> {
                    val close = if (c == '«') '»' else '"'
                    val scan = CnlLexemes.scanTextLiteral(line, i + 1, close)
                    if (!scan.terminated) {
                        diagnostics.warning("Unterminated quoted text", lineNumber, blockPath = BLOCK_PATH)
                    }
                    tokens += Tok(scan.text, isText = true)
                    i = if (scan.terminated) scan.closeIndex + 1 else scan.closeIndex
                }
                c == '(' || c == ')' -> {
                    tokens += Tok(c.toString(), isText = false)
                    i++
                }
                else -> {
                    var j = i
                    while (j < line.length && line[j] != ' ' && line[j] != '\t' && line[j] != '(' && line[j] != ')') j++
                    tokens += Tok(line.substring(i, j), isText = false)
                    i = j
                }
            }
        }
        return tokens
    }

    /** Folds `( … )` runs into groups; tolerates a missing `)` at end of line. */
    private fun tree(tokens: List<Tok>, from: Int): List<GNode> {
        val result = mutableListOf<GNode>()
        var i = from
        while (i < tokens.size) {
            val tok = tokens[i]
            when {
                !tok.isText && tok.text == "(" -> {
                    val (group, next) = parseGroup(tokens, i)
                    result += group
                    i = next
                }
                !tok.isText && tok.text == ")" -> i++
                else -> {
                    result += GLeaf(tok)
                    i++
                }
            }
        }
        return result
    }

    private fun parseGroup(tokens: List<Tok>, openIdx: Int): Pair<GGroup, Int> {
        val children = mutableListOf<GNode>()
        var i = openIdx + 1
        while (i < tokens.size && !(tokens[i].text == ")" && !tokens[i].isText)) {
            if (tokens[i].text == "(" && !tokens[i].isText) {
                val (group, next) = parseGroup(tokens, i)
                children += group
                i = next
            } else {
                children += GLeaf(tokens[i])
                i++
            }
        }
        return GGroup(children) to (if (i < tokens.size) i + 1 else i)
    }

    // --- sentence entry point ---

    fun parseSentence(
        line: String,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): CnlContainerLine<DiagramCnlSentence> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return CnlContainerLine.Prose
        val tokens = tokenize(trimmed, lineNumber, diagnostics)
        val noun = tokens.firstOrNull()?.takeUnless { it.isText }?.text?.lowercase()
            ?: return CnlContainerLine.Prose
        if (noun !in scopedNouns) return CnlContainerLine.Prose
        if (!looksQualified(noun, tokens)) return CnlContainerLine.Prose
        val parts = tree(tokens, from = 1)
        val sentence = when (noun) {
            "layer" -> parseLayer(parts, lineNumber, diagnostics)
            "node" -> parseNode(parts, lineNumber, diagnostics)
            "edge" -> parseEdge(parts, lineNumber, diagnostics)
            else -> parseGroupSentence(parts, lineNumber, diagnostics)
        }
        return sentence?.let { CnlContainerLine.Sentence(it) } ?: CnlContainerLine.Invalid
    }

    private val scopedNouns = setOf("node", "edge", "layer", "group")

    /**
     * The scope's `looksQualified` analog: a noun-led line commits to the grammar only when
     * it carries a real sentence signal; otherwise it stays prose (typo-guarded upstream).
     */
    private fun looksQualified(noun: String, tokens: List<Tok>): Boolean {
        val rest = tokens.drop(1)
        val hasSignal = rest.any { tok ->
            tok.isText || numberRegex.matches(tok.text) || tok.text == "(" || tok.text == ")"
        }
        return when (noun) {
            "node" -> hasSignal || rest.firstOrNull()?.text?.lowercase()?.let(::isNodeTypeWord) == true
            "edge" -> hasSignal || rest.any { !it.isText && it.text.lowercase() in setOf("from", "to") }
            "layer" -> hasSignal || rest.size == 1 ||
                rest.any { !it.isText && it.text.lowercase() in setOf("visible", "locked") }
            else -> rest.any { !it.isText && it.text.lowercase() == "members" }
        }
    }

    // --- Layer ---

    private fun parseLayer(
        parts: List<GNode>,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramCnlSentence? {
        val id = parts.firstOrNull()?.idToken() ?: run {
            diagnostics.error("diagram layer is missing `id`", line, blockPath = BLOCK_PATH)
            return null
        }
        var name: String? = null
        var visible = true
        var locked = false
        var idx = 1
        while (idx < parts.size) {
            val part = parts[idx]
            val text = part.text()
            if (text != null) {
                if (name == null) name = text
                idx++
                continue
            }
            when (part.lowerWord()) {
                "visible" -> {
                    val (value, next) = flagValue(parts, idx, default = true)
                    visible = value
                    idx = next
                }
                "locked" -> {
                    val (value, next) = flagValue(parts, idx, default = true)
                    locked = value
                    idx = next
                }
                else -> {
                    diagnostics.error(
                        "unknown word \"${wordLabel(part)}\" in diagram layer '$id'",
                        line,
                        blockPath = BLOCK_PATH,
                    )
                    return null
                }
            }
        }
        val layer = DiagramLayer(
            id = DiagramLayerId(id),
            name = name ?: id,
            visible = visible,
            locked = locked,
        )
        return DiagramCnlSentence.LayerSentence(layer, line)
    }

    // --- Group ---

    private fun parseGroupSentence(
        parts: List<GNode>,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramCnlSentence? {
        val id = parts.firstOrNull()?.idToken() ?: run {
            diagnostics.error("diagram group is missing `id`", line, blockPath = BLOCK_PATH)
            return null
        }
        var name: String? = null
        var members: List<String>? = null
        var idx = 1
        while (idx < parts.size) {
            val part = parts[idx]
            val text = part.text()
            if (text != null) {
                if (name == null) name = text
                idx++
                continue
            }
            when (part.lowerWord()) {
                "members" -> {
                    val group = parts.getOrNull(idx + 1)?.group() ?: run {
                        diagnostics.error(
                            "diagram group '$id' `members` needs a `( … )` id list",
                            line,
                            blockPath = BLOCK_PATH,
                        )
                        return null
                    }
                    members = group.children.mapNotNull { it.idToken() }
                    idx += 2
                }
                else -> {
                    diagnostics.error(
                        "unknown word \"${wordLabel(part)}\" in diagram group '$id'",
                        line,
                        blockPath = BLOCK_PATH,
                    )
                    return null
                }
            }
        }
        val distinct = members.orEmpty().distinct()
        if (distinct.size != members.orEmpty().size) {
            diagnostics.error(
                "diagram group '$id' lists duplicate members; duplicates dropped",
                line,
                blockPath = BLOCK_PATH,
            )
        }
        if (distinct.isEmpty()) {
            diagnostics.error("diagram group '$id' must list at least one member", line, blockPath = BLOCK_PATH)
            return null
        }
        val group = DiagramGroup(
            id = DiagramGroupId(id),
            memberIds = distinct.map(::DiagramNodeId),
            name = name,
        )
        return DiagramCnlSentence.GroupSentence(group, line)
    }

    // --- Node ---

    private fun parseNode(
        parts: List<GNode>,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramCnlSentence? {
        val typeWord = parts.getOrNull(0)?.lowerWord() ?: run {
            diagnostics.error("diagram node sentence needs a type word", line, blockPath = BLOCK_PATH)
            return null
        }
        if (!isNodeTypeWord(typeWord)) {
            diagnostics.error("unknown diagram node type '$typeWord'", line, blockPath = BLOCK_PATH)
            return null
        }
        val id = parts.getOrNull(1)?.idToken() ?: run {
            diagnostics.error("diagram node is missing `id`", line, blockPath = BLOCK_PATH)
            return null
        }
        val payload = PayloadBuilder(typeWord)

        var width: Double? = null
        var height: Double? = null
        var x: Double? = null
        var y: Double? = null
        var rotation = 0.0
        val ports = mutableListOf<DiagramPort>()
        var style: DiagramStyle = DiagramStyle.Default
        val labels = mutableListOf<DiagramLabel>()
        var parentId: String? = null
        var layerId: String? = null
        var locked = false
        var visible = true
        var sizing = DiagramNodeSizing.Fixed

        fun fail(message: String): DiagramCnlSentence? {
            diagnostics.error(message, line, blockPath = BLOCK_PATH)
            return null
        }

        var idx = 2
        while (idx < parts.size) {
            val part = parts[idx]
            val text = part.text()
            if (text != null) {
                if (!payload.acceptText(text)) {
                    return fail("unexpected text ${CnlLexemes.quoteText(text)} on diagram node '$id'")
                }
                idx++
                continue
            }
            if (part is GGroup) return fail("unexpected `( … )` group on diagram node '$id'")
            val word = part.lowerWord().orEmpty()
            val number = part.number()
            when {
                number != null -> {
                    val connector = parts.getOrNull(idx + 1)?.lowerWord()
                    val second = parts.getOrNull(idx + 2)?.number()
                    if (connector != null && connector in sizeConnectors && second != null && width == null) {
                        width = nonNegative(number, "w", id, line, diagnostics)
                        height = nonNegative(second, "h", id, line, diagnostics)
                        idx += 3
                    } else {
                        return fail("number \"${CnlLexemes.num(number)}\" is not attached to a property on diagram node '$id'")
                    }
                }
                word == "position" -> {
                    val px = parts.getOrNull(idx + 1)?.number()
                    val py = parts.getOrNull(idx + 2)?.number()
                    if (px == null || py == null) return fail("diagram node '$id' `position` needs two numbers")
                    x = px
                    y = py
                    idx += 3
                }
                word == "rotate" || word == "rotation" -> {
                    rotation = parts.getOrNull(idx + 1)?.number()
                        ?: return fail("diagram node '$id' `$word` needs a number")
                    idx += 2
                }
                word == "port" -> {
                    val group = parts.getOrNull(idx + 1)?.group()
                        ?: return fail("diagram node '$id' `port` needs a `( … )` group")
                    parsePort(group, id, line, diagnostics)?.let { port ->
                        if (ports.any { it.id == port.id }) {
                            diagnostics.error("duplicate port id '${port.id.value}' on node '$id'", line, blockPath = BLOCK_PATH)
                        } else {
                            ports += port
                        }
                    }
                    idx += 2
                }
                word == "style" -> {
                    val group = parts.getOrNull(idx + 1)?.group()
                        ?: return fail("diagram node '$id' `style` needs a `( … )` group")
                    style = parseStyle(group, line, diagnostics)
                    idx += 2
                }
                word == "label" -> {
                    val (label, next) = parseLabelPhrase(parts, idx, line, diagnostics)
                    label?.let(labels::add) ?: return null
                    idx = next
                }
                word == "parent" -> {
                    parentId = parts.getOrNull(idx + 1)?.idToken()
                        ?: return fail("diagram node '$id' `parent` needs an id")
                    idx += 2
                }
                word == "layer" -> {
                    layerId = parts.getOrNull(idx + 1)?.idToken()
                        ?: return fail("diagram node '$id' `layer` needs an id")
                    idx += 2
                }
                word == "locked" -> {
                    val (value, next) = flagValue(parts, idx, default = true)
                    locked = value
                    idx = next
                }
                word == "visible" -> {
                    val (value, next) = flagValue(parts, idx, default = true)
                    visible = value
                    idx = next
                }
                // Node-level, not payload-level: any shape with a caption may hug it.
                word == "hug" -> {
                    sizing = DiagramNodeSizing.Hug
                    idx++
                }
                word in payloadItemKeywords -> {
                    val next = payload.acceptItem(word, parts, idx, id, line, diagnostics) ?: return null
                    idx = next
                }
                payload.acceptBareWord(word) -> idx++
                else -> return fail("unknown word \"${wordLabel(part)}\" on diagram node '$id'")
            }
        }

        if (width == null || height == null) return fail("diagram node '$id' is missing its `<w> by <h>` size")
        if (x == null || y == null) return fail("diagram node '$id' is missing its `position <x> <y>`")
        val built = payload.build(id, line, diagnostics) ?: return null
        val node = DiagramNode(
            id = DiagramNodeId(id),
            x = x,
            y = y,
            width = width,
            height = height,
            rotation = rotation,
            payload = built,
            ports = ports.toList(),
            style = style,
            labels = labels.toList(),
            parentId = parentId?.let(::DiagramNodeId),
            layerId = layerId?.let(::DiagramLayerId),
            locked = locked,
            visible = visible,
            sizing = sizing,
        )
        // A typed payload renders the caption it carries itself (the `«…»` head phrase), so a
        // `label` on it would round-trip through the source without ever being drawn. Reject the
        // orphan rather than accept text the canvas will silently ignore.
        val firstLabel = labels.firstOrNull()?.text
        if (firstLabel != null && node.primaryText() != firstLabel) {
            return fail(
                "diagram node '$id' does not render `label «…»` — its caption belongs in the " +
                    "`«…»` head phrase of the node sentence",
            )
        }
        return DiagramCnlSentence.NodeSentence(node, line)
    }

    private fun nonNegative(
        value: Double,
        key: String,
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): Double {
        if (value < 0.0) {
            diagnostics.warning("diagram node '$nodeId' has negative $key, coerced to 0", line, blockPath = BLOCK_PATH)
            return 0.0
        }
        return value
    }

    private val payloadItemKeywords = setOf(
        "title", "lane", "attribute", "row", "col", "cell", "field", "method", "activation", "stereotype",
    )

    private val shapeWords: Map<String, DiagramShapeKind> =
        DiagramShapeKind.entries.associateBy { it.name.lowercase().replace('_', '-') }

    private val structuredTypeWords = setOf(
        "container", "swimlane", "flowchart", "entity", "bpmn", "table", "class", "lifeline",
        "state", "activity", "actor", "use-case", "use_case", "usecase", "component",
        "deployment", "note", "package",
    )

    private fun isNodeTypeWord(word: String): Boolean =
        word in shapeWords || word.replace('_', '-') in shapeWords || word in structuredTypeWords ||
            word.replace('_', '-') in structuredTypeWords

    /** Per-type payload state; type-specific words/items are validated against [typeWord]. */
    private class PayloadBuilder(rawTypeWord: String) {
        val typeWord: String = rawTypeWord.replace('_', '-').let { if (it == "usecase") "use-case" else it }

        var name: String? = null
        var stereotype: String? = null
        var abstractFlag = false
        var collapsed = false
        var vertical = false
        var actorFlag = false
        var stateKind: UmlStateKind? = null
        var flowKind: FlowchartNodeKind? = null
        var bpmnKind: BpmnNodeKind? = null
        var activityKind: UmlActivityKind? = null
        var title: DiagramLabel? = null
        val lanes = mutableListOf<SwimlaneLane>()
        val attributes = mutableListOf<ErAttribute>()
        val rows = mutableListOf<TableRow>()
        val columns = mutableListOf<TableColumn>()
        val cells = mutableListOf<TableCell>()
        val fields = mutableListOf<UmlMember>()
        val methods = mutableListOf<UmlMember>()
        val activations = mutableListOf<UmlActivation>()

        val hasNameSlot: Boolean = typeWord in setOf(
            "entity", "class", "lifeline", "state", "activity", "actor", "use-case",
            "component", "deployment", "note", "package",
        )

        fun acceptText(text: String): Boolean {
            if (!hasNameSlot || name != null) return false
            name = text
            return true
        }

        fun acceptBareWord(word: String): Boolean = when (typeWord) {
            "container" -> claimFlag(word == "collapsed") { collapsed = true }
            "swimlane" -> when (word) {
                "vertical" -> claimFlag(true) { vertical = true }
                "horizontal" -> true
                else -> false
            }
            "lifeline" -> claimFlag(word == "actor") { actorFlag = true }
            "class" -> claimFlag(word == "abstract") { abstractFlag = true }
            "flowchart" -> enumFromToken<FlowchartNodeKind>(word)?.also { flowKind = it } != null
            "bpmn" -> enumFromToken<BpmnNodeKind>(word)?.also { bpmnKind = it } != null
            "state" -> enumFromToken<UmlStateKind>(word)?.also { stateKind = it } != null
            "activity" -> enumFromToken<UmlActivityKind>(word)?.also { activityKind = it } != null
            else -> false
        }

        private inline fun claimFlag(matches: Boolean, set: () -> Unit): Boolean {
            if (matches) set()
            return matches
        }
    }

    /** Consumes one payload item phrase at [idx]; returns the next index, or null to drop the sentence. */
    private fun PayloadBuilder.acceptItem(
        word: String,
        parts: List<GNode>,
        idx: Int,
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): Int? {
        fun reject(): Int? {
            diagnostics.error(
                "`$word` is not valid on a `$typeWord` diagram node ('$nodeId')",
                line,
                blockPath = BLOCK_PATH,
            )
            return null
        }
        return when (word) {
            "title" -> {
                if (typeWord != "container" && typeWord != "swimlane") return reject()
                val (label, next) = parseLabelPhrase(parts, idx, line, diagnostics)
                title = label ?: return null
                next
            }
            "stereotype" -> {
                if (typeWord !in setOf("class", "component", "deployment")) return reject()
                stereotype = parts.getOrNull(idx + 1)?.text() ?: run {
                    diagnostics.error(
                        "diagram node '$nodeId' `stereotype` needs a «…» text",
                        line,
                        blockPath = BLOCK_PATH,
                    )
                    return null
                }
                idx + 2
            }
            "lane" -> {
                if (typeWord != "swimlane") return reject()
                val next = parts.getOrNull(idx + 1)
                val bare = next?.number()
                if (bare != null) {
                    lanes += SwimlaneLane(size = maxOf(bare, 0.0))
                    return idx + 2
                }
                val group = next?.group() ?: run {
                    diagnostics.error("diagram node '$nodeId' `lane` needs a size or a `( … )` group", line, blockPath = BLOCK_PATH)
                    return null
                }
                var laneTitle: DiagramLabel? = null
                var size = 120.0
                group.children.forEach { child ->
                    child.text()?.let { laneTitle = DiagramLabel(it) }
                        ?: child.group()?.let { nested -> laneTitle = labelGroupValue(nested, line, diagnostics) }
                        ?: child.number()?.let { size = maxOf(it, 0.0) }
                }
                lanes += SwimlaneLane(title = laneTitle, size = size)
                idx + 2
            }
            "attribute" -> {
                if (typeWord != "entity") return reject()
                val group = parts.getOrNull(idx + 1)?.group() ?: run {
                    diagnostics.error("diagram node '$nodeId' `attribute` needs a `( … )` group", line, blockPath = BLOCK_PATH)
                    return null
                }
                parseAttribute(group, nodeId, line, diagnostics)?.let(attributes::add)
                idx + 2
            }
            "row", "col" -> {
                if (typeWord != "table") return reject()
                val next = parts.getOrNull(idx + 1)
                val bare = next?.number()
                val track: Pair<Double, Boolean>? = if (bare != null) {
                    maxOf(bare, 0.0) to false
                } else {
                    next?.group()?.let { group ->
                        val size = group.children.firstNotNullOfOrNull { it.number() }
                        val header = group.children.any { it.lowerWord() == "header" }
                        size?.let { maxOf(it, 0.0) to header }
                    }
                }
                if (track == null) {
                    diagnostics.error("diagram node '$nodeId' `$word` needs a track size", line, blockPath = BLOCK_PATH)
                    return null
                }
                if (word == "row") rows += TableRow(height = track.first, header = track.second)
                else columns += TableColumn(width = track.first, header = track.second)
                idx + 2
            }
            "cell" -> {
                if (typeWord != "table") return reject()
                val group = parts.getOrNull(idx + 1)?.group() ?: run {
                    diagnostics.error("diagram node '$nodeId' `cell` needs a `( … )` group", line, blockPath = BLOCK_PATH)
                    return null
                }
                parseCell(group, nodeId, line, diagnostics)?.let(cells::add)
                idx + 2
            }
            "field", "method" -> {
                if (typeWord != "class") return reject()
                val group = parts.getOrNull(idx + 1)?.group() ?: run {
                    diagnostics.error("diagram node '$nodeId' `$word` needs a `( … )` group", line, blockPath = BLOCK_PATH)
                    return null
                }
                val member = parseMember(group, nodeId, line, diagnostics)
                member?.let { if (word == "field") fields += it else methods += it }
                idx + 2
            }
            "activation" -> {
                if (typeWord != "lifeline") return reject()
                val group = parts.getOrNull(idx + 1)?.group()
                val numbers = group?.children?.mapNotNull { it.number() }
                if (numbers?.size != 2) {
                    diagnostics.error("lifeline activation on '$nodeId' must be `(start end)`", line, blockPath = BLOCK_PATH)
                    return null
                }
                val start = numbers[0].coerceIn(0.0, 1.0)
                val end = numbers[1].coerceIn(0.0, 1.0)
                if (start != numbers[0] || end != numbers[1] || start > end) {
                    diagnostics.warning("lifeline activation on '$nodeId' coerced into 0..1", line, blockPath = BLOCK_PATH)
                }
                activations += UmlActivation(start = minOf(start, end), end = maxOf(start, end))
                idx + 2
            }
            else -> reject()
        }
    }

    /** Builds the typed payload; null (with an error already reported) drops the sentence. */
    private fun PayloadBuilder.build(
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramNodePayload? {
        fun requiredName(): String? = name ?: run {
            diagnostics.error(
                "diagram node '$nodeId' of type `$typeWord` needs a «…» name",
                line,
                blockPath = BLOCK_PATH,
            )
            null
        }
        shapeWords[typeWord]?.let { return DiagramNodePayload.BasicShape(it) }
        return when (typeWord) {
            "container" -> DiagramNodePayload.ContainerNode(title = title, collapsed = collapsed)
            "swimlane" -> DiagramNodePayload.SwimlaneNode(
                orientation = if (vertical) DiagramOrientation.VERTICAL else DiagramOrientation.HORIZONTAL,
                lanes = lanes.toList(),
                title = title,
            )
            "flowchart" -> flowKind?.let(DiagramNodePayload::FlowchartNode) ?: run {
                diagnostics.error("diagram node '$nodeId' is missing its flowchart kind word", line, blockPath = BLOCK_PATH)
                null
            }
            "entity" -> requiredName()?.let { DiagramNodePayload.ErEntityNode(name = it, attributes = attributes.toList()) }
            "bpmn" -> bpmnKind?.let(DiagramNodePayload::BpmnNode) ?: run {
                diagnostics.error("diagram node '$nodeId' is missing its bpmn kind word", line, blockPath = BLOCK_PATH)
                null
            }
            "table" -> buildTable(nodeId, line, diagnostics)
            "class" -> requiredName()?.let {
                UmlClassNode(
                    name = it,
                    stereotype = stereotype,
                    abstract = abstractFlag,
                    attributes = fields.toList(),
                    operations = methods.toList(),
                )
            }
            "lifeline" -> requiredName()?.let {
                UmlLifelineNode(name = it, actor = actorFlag, activations = activations.toList())
            }
            "state" -> UmlStateNode(name = name.orEmpty(), kind = stateKind ?: UmlStateKind.SIMPLE)
            "activity" -> activityKind?.let { UmlActivityNode(kind = it, name = name.orEmpty()) } ?: run {
                diagnostics.error("diagram node '$nodeId' is missing its activity kind word", line, blockPath = BLOCK_PATH)
                null
            }
            "actor" -> requiredName()?.let(::UmlActorNode)
            "use-case" -> requiredName()?.let(::UmlUseCaseNode)
            "component" -> requiredName()?.let { UmlComponentNode(name = it, stereotype = stereotype) }
            "deployment" -> requiredName()?.let { UmlDeploymentNode(name = it, stereotype = stereotype) }
            "note" -> requiredName()?.let(::UmlNoteNode)
            "package" -> requiredName()?.let(::UmlPackageNode)
            else -> null
        }
    }

    private fun PayloadBuilder.buildTable(
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramNodePayload {
        val covered = mutableSetOf<Pair<Int, Int>>()
        val valid = cells.filter { cell ->
            val inBounds = cell.row >= 0 && cell.column >= 0 &&
                cell.row + cell.rowSpan <= rows.size && cell.column + cell.colSpan <= columns.size
            if (!inBounds) {
                diagnostics.error(
                    "table cell (${cell.row}, ${cell.column}) span ${cell.rowSpan}x${cell.colSpan} is out of the " +
                        "${rows.size}x${columns.size} grid of node '$nodeId'",
                    line,
                    blockPath = BLOCK_PATH,
                )
                return@filter false
            }
            val positions = (cell.row until cell.row + cell.rowSpan).flatMap { r ->
                (cell.column until cell.column + cell.colSpan).map { c -> r to c }
            }
            if (positions.any { it in covered }) {
                diagnostics.error(
                    "table cell (${cell.row}, ${cell.column}) overlaps another cell on node '$nodeId'",
                    line,
                    blockPath = BLOCK_PATH,
                )
                return@filter false
            }
            covered += positions
            true
        }
        return TableNode(rows = rows.toList(), columns = columns.toList(), cells = valid)
    }

    // --- node sub-grammars ---

    private fun parsePort(
        group: GGroup,
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramPort? {
        val id = group.children.firstOrNull()?.idToken() ?: run {
            diagnostics.error("diagram port on node '$nodeId' is missing `id`", line, blockPath = BLOCK_PATH)
            return null
        }
        val second = group.children.getOrNull(1)
        if (second?.lowerWord() == "at") {
            val px = group.children.getOrNull(2)?.number()
            val py = group.children.getOrNull(3)?.number()
            if (px == null || py == null) {
                diagnostics.error("port '$id' `at` must be two numbers", line, blockPath = BLOCK_PATH)
                return null
            }
            return DiagramPort(id = DiagramPortId(id), anchor = DiagramPortAnchor.RelativePoint(x = px, y = py))
        }
        val side = second?.lowerWord()?.let { enumFromToken<DiagramNodeSide>(it) } ?: run {
            diagnostics.error("port '$id' on node '$nodeId' needs a side word or `at x y`", line, blockPath = BLOCK_PATH)
            return null
        }
        val rawOffset = group.children.getOrNull(2)?.number() ?: 0.5
        val offset = rawOffset.coerceIn(0.0, 1.0)
        if (offset != rawOffset) {
            diagnostics.warning("port '$id' offset coerced into 0..1", line, blockPath = BLOCK_PATH)
        }
        return DiagramPort(id = DiagramPortId(id), anchor = DiagramPortAnchor.SideOffset(side = side, offset = offset))
    }

    private fun parseAttribute(
        group: GGroup,
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): ErAttribute? {
        var name: String? = null
        var type: String? = null
        var pk = false
        var fk = false
        var idx = 0
        while (idx < group.children.size) {
            val child = group.children[idx]
            val text = child.text()
            when {
                text != null -> {
                    if (name == null) name = text
                    idx++
                }
                child.lowerWord() == "type" -> {
                    type = group.children.getOrNull(idx + 1)?.text()
                    idx += 2
                }
                child.lowerWord() == "pk" -> {
                    pk = true
                    idx++
                }
                child.lowerWord() == "fk" -> {
                    fk = true
                    idx++
                }
                else -> idx++
            }
        }
        if (name == null) {
            diagnostics.error("entity attribute on '$nodeId' is missing its «…» name", line, blockPath = BLOCK_PATH)
            return null
        }
        return ErAttribute(name = name, type = type, primaryKey = pk, foreignKey = fk)
    }

    private val visibilitySymbols: Map<String, UmlVisibility> =
        UmlVisibility.entries.associateBy { it.symbol.toString() }

    private fun parseMember(
        group: GGroup,
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): UmlMember? {
        var visibility = UmlVisibility.PUBLIC
        var static = false
        var abstract = false
        var text: String? = null
        group.children.forEach { child ->
            val literal = child.text()
            val symbol = child.word()?.let { visibilitySymbols[it] }
            when {
                literal != null -> if (text == null) text = literal
                symbol != null -> visibility = symbol
                child.lowerWord() == "static" -> static = true
                child.lowerWord() == "abstract" -> abstract = true
                else -> {}
            }
        }
        val body = text ?: run {
            diagnostics.error("uml member on '$nodeId' is missing its «…» text", line, blockPath = BLOCK_PATH)
            return null
        }
        return UmlMember(text = body, visibility = visibility, static = static, abstract = abstract)
    }

    private fun parseCell(
        group: GGroup,
        nodeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): TableCell? {
        val row = group.children.getOrNull(0)?.number()?.toInt()
        val column = group.children.getOrNull(1)?.number()?.toInt()
        if (row == null || column == null || row < 0 || column < 0) {
            diagnostics.error("table cell on '$nodeId' needs `row column` leading integers", line, blockPath = BLOCK_PATH)
            return null
        }
        var rowSpan = 1
        var colSpan = 1
        var label: DiagramLabel? = null
        var style: DiagramStyle? = null
        var idx = 2
        while (idx < group.children.size) {
            val child = group.children[idx]
            val text = child.text()
            when {
                text != null -> {
                    if (label == null) label = DiagramLabel(text)
                    idx++
                }
                child.lowerWord() == "span" -> {
                    val rs = group.children.getOrNull(idx + 1)?.number()?.toInt()
                    val connector = group.children.getOrNull(idx + 2)?.lowerWord()
                    val cs = group.children.getOrNull(idx + 3)?.number()?.toInt()
                    if (rs == null || connector == null || connector !in sizeConnectors || cs == null) {
                        diagnostics.error("table cell span on '$nodeId' must be `span R by C`", line, blockPath = BLOCK_PATH)
                        return null
                    }
                    rowSpan = rs.coerceAtLeast(1)
                    colSpan = cs.coerceAtLeast(1)
                    idx += 4
                }
                child.lowerWord() == "label" -> {
                    label = group.children.getOrNull(idx + 1)?.group()?.let { labelGroupValue(it, line, diagnostics) }
                    idx += 2
                }
                child.lowerWord() == "style" -> {
                    style = group.children.getOrNull(idx + 1)?.group()?.let { parseStyle(it, line, diagnostics) }
                    idx += 2
                }
                else -> idx++
            }
        }
        return TableCell(row = row, column = column, rowSpan = rowSpan, colSpan = colSpan, label = label, style = style)
    }

    /** `label «t»` / `label («t» markdown)` / `title …` — returns the label and the next index. */
    private fun parseLabelPhrase(
        parts: List<GNode>,
        idx: Int,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): Pair<DiagramLabel?, Int> {
        val next = parts.getOrNull(idx + 1)
        next?.text()?.let { return DiagramLabel(it) to idx + 2 }
        next?.group()?.let { return labelGroupValue(it, line, diagnostics) to idx + 2 }
        diagnostics.error("`${parts[idx].lowerWord()}` needs a «…» text or a `( … )` group", line, blockPath = BLOCK_PATH)
        return null to idx + 1
    }

    private fun labelGroupValue(group: GGroup, line: Int, diagnostics: DiagnosticCollector): DiagramLabel? {
        val text = group.children.firstNotNullOfOrNull { it.text() } ?: run {
            diagnostics.error("label group is missing its «…» text", line, blockPath = BLOCK_PATH)
            return null
        }
        val markdown = group.children.any { it.lowerWord() == "markdown" }
        return DiagramLabel(text = text, markdown = markdown)
    }

    private fun parseStyle(group: GGroup, line: Int, diagnostics: DiagnosticCollector): DiagramStyle {
        var fill: DiagramColor? = null
        var stroke: DiagramColor? = null
        var strokeWidth = 1.0
        var pattern = DiagramStrokePattern.SOLID
        var opacity = 1.0
        var corners = DiagramCornerStyle.ROUNDED
        var sketch = false
        var shadow = false
        var idx = 0
        while (idx < group.children.size) {
            val child = group.children[idx]
            when (child.lowerWord()) {
                "fill" -> {
                    fill = colorValue(group.children.getOrNull(idx + 1), "fill", line, diagnostics)
                    idx += 2
                }
                "stroke" -> {
                    stroke = colorValue(group.children.getOrNull(idx + 1), "stroke", line, diagnostics)
                    idx += 2
                }
                "weight" -> {
                    strokeWidth = maxOf(group.children.getOrNull(idx + 1)?.number() ?: 1.0, 0.0)
                    idx += 2
                }
                "pattern" -> {
                    pattern = enumWord<DiagramStrokePattern>(group.children.getOrNull(idx + 1), "pattern", line, diagnostics)
                        ?: pattern
                    idx += 2
                }
                "opacity" -> {
                    val raw = group.children.getOrNull(idx + 1)?.number() ?: 1.0
                    opacity = raw.coerceIn(0.0, 1.0)
                    if (opacity != raw) diagnostics.warning("style opacity coerced into 0..1", line, blockPath = BLOCK_PATH)
                    idx += 2
                }
                "corners" -> {
                    corners = enumWord<DiagramCornerStyle>(group.children.getOrNull(idx + 1), "corners", line, diagnostics)
                        ?: corners
                    idx += 2
                }
                "sketch" -> {
                    sketch = true
                    idx++
                }
                "shadow" -> {
                    shadow = true
                    idx++
                }
                else -> {
                    diagnostics.error("unknown style key \"${wordLabel(child)}\"", line, blockPath = BLOCK_PATH)
                    idx++
                }
            }
        }
        return DiagramStyle(
            fill = fill,
            stroke = stroke,
            strokeWidth = strokeWidth,
            pattern = pattern,
            opacity = opacity,
            cornerStyle = corners,
            sketch = sketch,
            shadow = shadow,
        )
    }

    private fun colorValue(node: GNode?, key: String, line: Int, diagnostics: DiagnosticCollector): DiagramColor? {
        val token = node?.word() ?: run {
            diagnostics.error("`$key` must be a #RRGGBB[AA] color", line, blockPath = BLOCK_PATH)
            return null
        }
        return parseCnlDiagramColor(token) ?: run {
            diagnostics.error("malformed color '$token' under `$key` (expected #RRGGBB or #RRGGBBAA)", line, blockPath = BLOCK_PATH)
            null
        }
    }

    private inline fun <reified E : Enum<E>> enumWord(
        node: GNode?,
        key: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): E? {
        val token = node?.word() ?: run {
            diagnostics.error("`$key` needs a value word", line, blockPath = BLOCK_PATH)
            return null
        }
        return enumFromToken<E>(token) ?: run {
            diagnostics.error("unknown `$key` value '$token'", line, blockPath = BLOCK_PATH)
            null
        }
    }

    /** `<kw> [yes/no/on/off/true/false]` — the bool word is optional; bare keyword means [default]. */
    private fun flagValue(parts: List<GNode>, idx: Int, default: Boolean): Pair<Boolean, Int> {
        val next = parts.getOrNull(idx + 1)?.lowerWord()
        val value = next?.let { boolWords[it] }
        return if (value != null) value to idx + 2 else default to idx + 1
    }

    private fun wordLabel(node: GNode): String = node.word() ?: node.text() ?: "( … )"

    // --- Edge ---

    private fun parseEdge(
        parts: List<GNode>,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramCnlSentence? {
        val id = parts.firstOrNull()?.idToken() ?: run {
            diagnostics.error("diagram edge is missing `id`", line, blockPath = BLOCK_PATH)
            return null
        }

        fun fail(message: String): DiagramCnlSentence? {
            diagnostics.error(message, line, blockPath = BLOCK_PATH)
            return null
        }

        var source: DiagramCnlEndpoint? = null
        var target: DiagramCnlEndpoint? = null
        var relation: DiagramRelation = DiagramRelation.Plain
        var routing = DiagramRoutingStyle.ORTHOGONAL
        val waypoints = mutableListOf<DiagramPoint>()
        val labels = mutableListOf<DiagramEdgeLabel>()
        var style: DiagramStyle = DiagramStyle.Default
        var sourceArrowhead: DiagramArrowhead = DiagramArrowhead.None
        var targetArrowhead: DiagramArrowhead = DiagramArrowhead.None
        var lineJumps = LineJumpStyle.ARC
        var mode = DiagramConnectionMode.LINE
        var animated = false
        var layerId: String? = null

        var idx = 1
        while (idx < parts.size) {
            val part = parts[idx]
            when (part.lowerWord()) {
                "from", "to" -> {
                    val key = part.lowerWord()!!
                    val endpoint = endpointOf(parts.getOrNull(idx + 1))
                        ?: return fail("diagram edge '$id' `$key` must reference a node, `node.port`, `(x y)` or `(node …)`")
                    if (key == "from") source = endpoint else target = endpoint
                    idx += 2
                }
                "relation" -> {
                    val (parsed, next) = parseRelation(parts, idx, id, line, diagnostics)
                    relation = parsed
                    idx = next
                }
                "routing" -> {
                    routing = enumWord<DiagramRoutingStyle>(parts.getOrNull(idx + 1), "routing", line, diagnostics)
                        ?: routing
                    idx += 2
                }
                "via" -> {
                    val group = parts.getOrNull(idx + 1)?.group()
                    val numbers = group?.children?.mapNotNull { it.number() }
                    if (numbers?.size != 2) return fail("waypoint on edge '$id' must be `via (x y)`")
                    waypoints += DiagramPoint(x = numbers[0], y = numbers[1])
                    idx += 2
                }
                "label" -> {
                    val (label, next) = parseEdgeLabelPhrase(parts, idx, id, line, diagnostics)
                    label?.let(labels::add) ?: return null
                    idx = next
                }
                "style" -> {
                    val group = parts.getOrNull(idx + 1)?.group()
                        ?: return fail("diagram edge '$id' `style` needs a `( … )` group")
                    style = parseStyle(group, line, diagnostics)
                    idx += 2
                }
                "arrow" -> {
                    val end = parts.getOrNull(idx + 1)?.lowerWord()
                    if (end != "source" && end != "target") {
                        return fail("diagram edge '$id' `arrow` needs `source` or `target`")
                    }
                    val (arrowhead, next) = parseArrowhead(parts, idx + 2, id, line, diagnostics) ?: return null
                    if (end == "source") sourceArrowhead = arrowhead else targetArrowhead = arrowhead
                    idx = next
                }
                "jumps" -> {
                    lineJumps = enumWord<LineJumpStyle>(parts.getOrNull(idx + 1), "jumps", line, diagnostics)
                        ?: lineJumps
                    idx += 2
                }
                "mode" -> {
                    mode = enumWord<DiagramConnectionMode>(parts.getOrNull(idx + 1), "mode", line, diagnostics)
                        ?: mode
                    idx += 2
                }
                "animated" -> {
                    val (value, next) = flagValue(parts, idx, default = true)
                    animated = value
                    idx = next
                }
                "layer" -> {
                    layerId = parts.getOrNull(idx + 1)?.idToken()
                        ?: return fail("diagram edge '$id' `layer` needs an id")
                    idx += 2
                }
                else -> return fail("unknown word \"${wordLabel(part)}\" on diagram edge '$id'")
            }
        }

        if (source == null) return fail("diagram edge '$id' is missing `from`")
        if (target == null) return fail("diagram edge '$id' is missing `to`")

        // The model allows at most one label per position and three total (mirror the YAML reader).
        val seen = mutableSetOf<DiagramEdgeLabelPosition>()
        val keptLabels = labels.filter { label ->
            val kept = seen.add(label.position)
            if (!kept) {
                diagnostics.warning(
                    "edge '$id' has multiple labels at ${label.position.slmToken()}, keeping the first",
                    line,
                    blockPath = BLOCK_PATH,
                )
            }
            kept
        }

        val edge = DiagramCnlEdge(
            id = id,
            source = source,
            target = target,
            relation = relation,
            routing = routing,
            waypoints = waypoints.toList(),
            style = style,
            labels = keptLabels,
            sourceArrowhead = sourceArrowhead,
            targetArrowhead = targetArrowhead,
            lineJumps = lineJumps,
            connectionMode = mode,
            flowAnimation = animated,
            layerId = layerId?.let(::DiagramLayerId),
        )
        return DiagramCnlSentence.EdgeSentence(edge, line)
    }

    private fun endpointOf(node: GNode?): DiagramCnlEndpoint? {
        when (node) {
            null -> return null
            is GLeaf -> return node.idToken()?.takeIf { it.isNotEmpty() }?.let { DiagramCnlEndpoint.Bare(it) }
            is GGroup -> {
                val children = node.children
                if (children.firstOrNull()?.lowerWord() == "node") {
                    val nodeId = children.getOrNull(1)?.idToken() ?: return null
                    val portId = if (children.getOrNull(2)?.lowerWord() == "port") {
                        children.getOrNull(3)?.idToken() ?: return null
                    } else {
                        null
                    }
                    return DiagramCnlEndpoint.Explicit(nodeId = nodeId, portId = portId)
                }
                val numbers = children.mapNotNull { it.number() }
                if (numbers.size == 2 && children.size == 2) {
                    return DiagramCnlEndpoint.Free(x = numbers[0], y = numbers[1])
                }
                return null
            }
        }
    }

    private fun parseRelation(
        parts: List<GNode>,
        idx: Int,
        edgeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): Pair<DiagramRelation, Int> {
        val word = parts.getOrNull(idx + 1)?.lowerWord() ?: run {
            diagnostics.error("diagram edge '$edgeId' `relation` needs a relation word", line, blockPath = BLOCK_PATH)
            return DiagramRelation.Plain to idx + 1
        }
        return when (word.replace('-', '_')) {
            "plain" -> DiagramRelation.Plain to idx + 2
            "association" -> {
                if (parts.getOrNull(idx + 2)?.lowerWord() == "directed") {
                    DiagramRelation.Association(directed = true) to idx + 3
                } else {
                    DiagramRelation.Association() to idx + 2
                }
            }
            "aggregation" -> DiagramRelation.Aggregation to idx + 2
            "composition" -> DiagramRelation.Composition to idx + 2
            "generalization" -> DiagramRelation.Generalization to idx + 2
            "dependency" -> DiagramRelation.Dependency to idx + 2
            "realization" -> DiagramRelation.Realization to idx + 2
            "transition" -> DiagramRelation.Transition to idx + 2
            "include" -> DiagramRelation.Include to idx + 2
            "extend" -> DiagramRelation.Extend to idx + 2
            "message" -> {
                val kind = parts.getOrNull(idx + 2)?.lowerWord()?.let { enumFromToken<UmlMessageKind>(it) }
                if (kind == null) {
                    diagnostics.error(
                        "relation `message` on edge '$edgeId' needs a valid kind (sync/async/return/create/destroy)",
                        line,
                        blockPath = BLOCK_PATH,
                    )
                    DiagramRelation.Plain to idx + 2
                } else {
                    DiagramRelation.Message(kind) to idx + 3
                }
            }
            "er", "entity_relation" -> {
                val first = parts.getOrNull(idx + 2)?.lowerWord()?.let { enumFromToken<ErCardinality>(it) }
                if (first == null) {
                    DiagramRelation.EntityRelation() to idx + 2
                } else {
                    val hasTo = parts.getOrNull(idx + 3)?.lowerWord() == "to"
                    val second = parts.getOrNull(idx + 4)?.lowerWord()?.let { enumFromToken<ErCardinality>(it) }
                    if (!hasTo || second == null) {
                        diagnostics.error(
                            "relation `er` on edge '$edgeId' must be `er <cardinality> to <cardinality>`",
                            line,
                            blockPath = BLOCK_PATH,
                        )
                        DiagramRelation.EntityRelation(sourceCardinality = first) to idx + 3
                    } else {
                        DiagramRelation.EntityRelation(sourceCardinality = first, targetCardinality = second) to idx + 5
                    }
                }
            }
            else -> {
                diagnostics.error("unknown relation '$word' on edge '$edgeId'", line, blockPath = BLOCK_PATH)
                DiagramRelation.Plain to idx + 2
            }
        }
    }

    private fun parseEdgeLabelPhrase(
        parts: List<GNode>,
        idx: Int,
        edgeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): Pair<DiagramEdgeLabel?, Int> {
        val next = parts.getOrNull(idx + 1)
        next?.text()?.let { return DiagramEdgeLabel(label = DiagramLabel(it)) to idx + 2 }
        val group = next?.group() ?: run {
            diagnostics.error(
                "edge label on '$edgeId' needs a «…» text or a `( … )` group",
                line,
                blockPath = BLOCK_PATH,
            )
            return null to idx + 1
        }
        var text: String? = null
        var markdown = false
        var position = DiagramEdgeLabelPosition.MIDDLE
        var dx = 0.0
        var dy = 0.0
        var i = 0
        while (i < group.children.size) {
            val child = group.children[i]
            val literal = child.text()
            when {
                literal != null -> {
                    if (text == null) text = literal
                    i++
                }
                child.lowerWord() == "markdown" -> {
                    markdown = true
                    i++
                }
                child.lowerWord() == "at" -> {
                    position = enumWord<DiagramEdgeLabelPosition>(group.children.getOrNull(i + 1), "at", line, diagnostics)
                        ?: position
                    i += 2
                }
                child.lowerWord() == "dx" -> {
                    dx = group.children.getOrNull(i + 1)?.number() ?: 0.0
                    i += 2
                }
                child.lowerWord() == "dy" -> {
                    dy = group.children.getOrNull(i + 1)?.number() ?: 0.0
                    i += 2
                }
                else -> i++
            }
        }
        val body = text ?: run {
            diagnostics.error("edge label on '$edgeId' is missing `text`", line, blockPath = BLOCK_PATH)
            return null to idx + 2
        }
        return DiagramEdgeLabel(
            label = DiagramLabel(text = body, markdown = markdown),
            position = position,
            offsetX = dx,
            offsetY = dy,
        ) to idx + 2
    }

    /** Returns the arrowhead and the next index, or null (with an error) to drop the sentence. */
    private fun parseArrowhead(
        parts: List<GNode>,
        idx: Int,
        edgeId: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): Pair<DiagramArrowhead, Int>? {
        val node = parts.getOrNull(idx)
        node?.word()?.let { token ->
            val kind = enumFromToken<DiagramArrowheadKind>(token) ?: run {
                diagnostics.error("unknown arrowhead kind '$token'", line, blockPath = BLOCK_PATH)
                return null
            }
            return DiagramArrowhead(kind = kind) to idx + 1
        }
        val group = node?.group() ?: run {
            diagnostics.error("diagram edge '$edgeId' arrowhead needs a kind word or a `( … )` group", line, blockPath = BLOCK_PATH)
            return null
        }
        val kindToken = group.children.firstOrNull()?.word() ?: run {
            diagnostics.error("diagram edge '$edgeId' arrowhead group is missing its kind word", line, blockPath = BLOCK_PATH)
            return null
        }
        val kind = enumFromToken<DiagramArrowheadKind>(kindToken) ?: run {
            diagnostics.error("unknown arrowhead kind '$kindToken'", line, blockPath = BLOCK_PATH)
            return null
        }
        var size = 8.0
        var inset = 0.0
        var i = 1
        while (i < group.children.size) {
            when (group.children[i].lowerWord()) {
                "size" -> {
                    size = maxOf(group.children.getOrNull(i + 1)?.number() ?: 8.0, 0.0)
                    i += 2
                }
                "inset" -> {
                    inset = group.children.getOrNull(i + 1)?.number() ?: 0.0
                    i += 2
                }
                else -> i++
            }
        }
        return DiagramArrowhead(kind = kind, size = size, inset = inset) to idx + 1
    }

    // --- aggregation: collected sentences -> DiagramGraph ---

    fun aggregate(
        sentences: List<DiagramCnlSentence>,
        diagnostics: DiagnosticCollector,
    ): DiagramGraph {
        val layers = mutableListOf<DiagramCnlSentence.LayerSentence>()
        val nodes = mutableListOf<DiagramCnlSentence.NodeSentence>()
        val edges = mutableListOf<DiagramCnlSentence.EdgeSentence>()
        val groups = mutableListOf<DiagramCnlSentence.GroupSentence>()

        fun <T : DiagramCnlSentence> dedupe(
            list: MutableList<T>,
            sentence: T,
            what: String,
            id: String,
            idOf: (T) -> String,
        ) {
            if (list.any { idOf(it) == id }) {
                diagnostics.error("duplicate diagram $what id '$id'", sentence.line, blockPath = BLOCK_PATH)
            } else {
                list += sentence
            }
        }

        sentences.forEach { sentence ->
            when (sentence) {
                is DiagramCnlSentence.LayerSentence ->
                    dedupe(layers, sentence, "layer", sentence.layer.id.value) { it.layer.id.value }
                is DiagramCnlSentence.NodeSentence ->
                    dedupe(nodes, sentence, "node", sentence.node.id.value) { it.node.id.value }
                is DiagramCnlSentence.EdgeSentence ->
                    dedupe(edges, sentence, "edge", sentence.edge.id) { it.edge.id }
                is DiagramCnlSentence.GroupSentence ->
                    dedupe(groups, sentence, "group", sentence.group.id.value) { it.group.id.value }
            }
        }

        val graphNodes = nodes.map { it.node }
        val resolvedEdges = edges.mapNotNull { sentence ->
            val edge = sentence.edge
            val source = resolveEndpoint(edge.source, graphNodes, edge.id, "from", sentence.line, diagnostics)
                ?: return@mapNotNull null
            val target = resolveEndpoint(edge.target, graphNodes, edge.id, "to", sentence.line, diagnostics)
                ?: return@mapNotNull null
            DiagramEdge(
                id = DiagramEdgeId(edge.id),
                source = source,
                target = target,
                relation = edge.relation,
                routing = edge.routing,
                waypoints = edge.waypoints,
                style = edge.style,
                labels = edge.labels,
                sourceArrowhead = edge.sourceArrowhead,
                targetArrowhead = edge.targetArrowhead,
                lineJumps = edge.lineJumps,
                connectionMode = edge.connectionMode,
                flowAnimation = edge.flowAnimation,
                layerId = edge.layerId,
            ) to sentence.line
        }

        val graph = DiagramGraph(
            nodes = graphNodes,
            edges = resolvedEdges.map { it.first },
            layers = layers.map { it.layer },
            groups = groups.map { it.group },
        )

        val nodeLines = nodes.associate { it.node.id.value to it.line }
        val edgeLines = resolvedEdges.associate { (edge, line) -> edge.id.value to line }
        val groupLines = groups.associate { it.group.id.value to it.line }
        diagramReferenceIssues(graph).forEach { issue ->
            val line = when (val owner = issue.owner) {
                is DiagramRefOwner.OfEdge -> edgeLines[owner.id] ?: 0
                is DiagramRefOwner.OfNode -> nodeLines[owner.id] ?: 0
                is DiagramRefOwner.OfGroup -> groupLines[owner.id] ?: 0
            }
            if (issue.isError) {
                diagnostics.error(issue.message, line, blockPath = BLOCK_PATH)
            } else {
                diagnostics.warning(issue.message, line, blockPath = BLOCK_PATH)
            }
        }
        return graph
    }

    /**
     * §5 endpoint resolution against the collected node set. Bare `a.b`:
     * fixed-port reading (node `a` declares port `b`, split at the **last** dot) vs dotted
     * node id reading; both valid → ambiguity error (edge dropped); neither → the YAML
     * reader's split so the reference validation reports the usual missing-node/port error.
     */
    private fun resolveEndpoint(
        endpoint: DiagramCnlEndpoint,
        nodes: List<DiagramNode>,
        edgeId: String,
        key: String,
        line: Int,
        diagnostics: DiagnosticCollector,
    ): DiagramEndpoint? = when (endpoint) {
        is DiagramCnlEndpoint.Free -> DiagramEndpoint.FreePoint(x = endpoint.x, y = endpoint.y)
        is DiagramCnlEndpoint.Explicit ->
            endpoint.portId?.let { DiagramEndpoint.FixedPort(DiagramNodeId(endpoint.nodeId), DiagramPortId(it)) }
                ?: DiagramEndpoint.FloatingAnchor(DiagramNodeId(endpoint.nodeId))
        is DiagramCnlEndpoint.Bare -> {
            val token = endpoint.token
            val dot = token.lastIndexOf('.')
            val hasPortSplit = dot > 0 && dot < token.length - 1
            val nodePart = if (hasPortSplit) token.substring(0, dot) else token
            val portPart = if (hasPortSplit) token.substring(dot + 1) else ""
            val fixedReading = hasPortSplit && nodes.firstOrNull { it.id.value == nodePart }
                ?.ports?.any { it.id.value == portPart } == true
            val floatingReading = nodes.any { it.id.value == token }
            when {
                fixedReading && floatingReading -> {
                    diagnostics.error(
                        "ambiguous endpoint '$token' on diagram edge '$edgeId' `$key`; " +
                            "use `(node …)` or `(node … port …)`",
                        line,
                        blockPath = BLOCK_PATH,
                    )
                    null
                }
                fixedReading -> DiagramEndpoint.FixedPort(DiagramNodeId(nodePart), DiagramPortId(portPart))
                floatingReading -> DiagramEndpoint.FloatingAnchor(DiagramNodeId(token))
                hasPortSplit -> DiagramEndpoint.FixedPort(DiagramNodeId(nodePart), DiagramPortId(portPart))
                else -> DiagramEndpoint.FloatingAnchor(DiagramNodeId(token))
            }
        }
    }
}

/** Parses a CNL `#RRGGBB[AA]` color token (alpha **last**, unlike the YAML `#AARRGGBB`). */
internal fun parseCnlDiagramColor(text: String): DiagramColor? {
    val hex = text.trim().removePrefix("#")
    if (text.trim() == hex) return null
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toULongOrNull(16) ?: return null
    return if (hex.length == 6) {
        DiagramColor(value or 0xFF000000u)
    } else {
        DiagramColor(((value and 0xFFu) shl 24) or (value shr 8))
    }
}

/** Formats a [DiagramColor] as CNL `#RRGGBB[AA]` uppercase, alpha omitted when `FF`. */
internal fun formatCnlDiagramColor(color: DiagramColor): String {
    val rgb = (color.argb and 0x00FFFFFFu).toString(16).uppercase().padStart(6, '0')
    if (color.alpha == 0xFF) return "#$rgb"
    val alpha = color.alpha.toString(16).uppercase().padStart(2, '0')
    return "#$rgb$alpha"
}
