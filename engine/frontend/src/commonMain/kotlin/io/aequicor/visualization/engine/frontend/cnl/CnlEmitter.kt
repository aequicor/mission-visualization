package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignComponent
import io.aequicor.visualization.engine.ir.model.DesignComponentSet
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue

/**
 * Deterministic IR → CNL generator: the exact inverse of [CnlParser], reading the same
 * [CnlGrammar] registry so a keyword works in both directions. Every node renders to exactly
 * one sentence; tree structure comes from markdown heading nesting. This is the "generate"
 * half of full bidirectional CNL — it powers write-back of new nodes, whole-document
 * regeneration (YAML→CNL migration), and round-trip tests (`parse(emit(node)) ≡ node`).
 *
 * P0 emits the buckets the grammar registry currently covers; later phases extend the
 * registry and this emitter grows automatically with it.
 */
internal object CnlEmitter {
    /** Renders [node] as one CNL sentence: `Noun [id …] [«text»] phrase…`. */
    fun emitSentence(node: DesignNode, includeId: Boolean = false): String {
        val parts = mutableListOf(CnlGrammar.canonicalNoun(node) ?: "Frame")
        if (includeId && node.id.isNotEmpty()) parts += "id ${node.id}"
        CnlGrammar.textLiteral(node)?.let { parts += "«$it»" }
        parts += phrasesOf(node)
        return parts.joinToString(" ")
    }

    /** The property phrases of [node] in canonical order (no noun/name) — a heading suffix. */
    fun emitHeadingSuffix(node: DesignNode): String = phrasesOf(node, includeName = false).joinToString(" ")

    /** A container heading line at markdown [level]: `## Name id node_id suffix`. */
    fun emitHeadingLine(
        node: DesignNode,
        level: Int,
        includeId: Boolean = false,
        titleOverride: String? = null,
        textAsCharacters: Boolean = false,
        preserveEmptyTitle: Boolean = false,
        forceNamePhrase: Boolean = false,
    ): String {
        val name = titleOverride ?: headingTitle(node, preserveEmpty = preserveEmptyTitle)
        val suffix = buildList {
            if (includeId && node.id.isNotEmpty()) add("id ${node.id}")
            if (textAsCharacters) headingCharactersPhrase(node)?.let { add(it) }
            addAll(phrasesOf(node, includeName = forceNamePhrase || node.type == "screen"))
        }.joinToString(" ")
        val heading = "#".repeat(level.coerceAtLeast(1))
        return if (suffix.isEmpty()) "$heading $name" else "$heading $name $suffix"
    }

    /**
     * Emits [node] as CNL source lines: a container becomes a heading followed by its
     * children (each recursively), a leaf becomes a single sentence. Blank lines separate
     * blocks so each element parses independently.
     */
    fun emitSubtree(
        node: DesignNode,
        level: Int,
        includeId: Boolean = false,
        titleOverride: String? = null,
    ): List<String> {
        if (!isContainer(node)) return listOf(emitSentence(node, includeId))
        val lines = mutableListOf(emitHeadingLine(node, level, includeId, titleOverride))
        node.children.forEach { child ->
            lines += ""
            lines += emitSubtree(child, level + 1, includeId)
        }
        return lines
    }

    /**
     * Regenerates a complete standalone SLM document: YAML frontmatter plus CNL document
     * dictionaries and the CNL page body. This is the migration entrypoint from IR/YAML-backed
     * sources to CNL-only layout authoring.
     */
    fun emitDocument(document: DesignDocument, includeId: Boolean = true): String =
        buildString {
            append(emitFrontmatter(document))
            val sections = listOf(
                emitVariables(document),
                emitStyles(document),
                emitDocumentBody(document, includeId),
                emitComponents(document, includeId = includeId),
            ).filter { it.isNotBlank() }
            if (sections.isNotEmpty()) {
                append("\n\n")
                append(sections.joinToString("\n\n"))
                append("\n")
            }
        }

    /**
     * Regenerates the CNL **body** (markdown heading tree + one sentence per node) for every
     * screen in [document]. Node ids are included by default so a recompile keeps the same id set
     * (structural stability). Document-level dictionaries are emitted by [emitDocument].
     */
    fun emitDocumentBody(document: DesignDocument, includeId: Boolean = true): String =
        document.pages
            .flatMap { it.children }
            .mapIndexed { index, screenNode ->
                val title = if (index == 0) document.screen?.name?.takeIf { it.isNotEmpty() } else null
                emitStableSubtree(screenNode, level = 1, includeId, titleOverride = title).joinToString("\n")
            }
            .joinToString("\n\n")

    /** Emits the frontmatter YAML retained by SLM; layout authoring itself remains CNL. */
    fun emitFrontmatter(document: DesignDocument): String {
        val screen = document.screen
        val screenId = screen?.id?.takeIf { it.isNotEmpty() }
            ?: document.id.takeIf { it.isNotEmpty() }
            ?: document.pages.firstOrNull()?.id.orEmpty()
        val pageName = screen?.page?.takeIf { it.isNotEmpty() }
            ?: document.pages.firstOrNull()?.name.orEmpty()
        return buildString {
            appendLine("---")
            appendLine("screen: ${frontmatterScalar(screenId)}")
            if (pageName.isNotEmpty()) appendLine("page: ${frontmatterScalar(pageName)}")
            document.i18n.sourceLocale.takeIf { it.isNotEmpty() }
                ?.let { appendLine("sourceLocale: ${frontmatterScalar(it)}") }
            if (document.i18n.targetLocales.isNotEmpty()) {
                appendLine("targetLocales: [${document.i18n.targetLocales.joinToString(", ") { frontmatterScalar(it) }}]")
            }
            screen?.modes.orEmpty().forEach { (dimension, value) ->
                if (dimension in setOf("density", "platform", "theme")) {
                    appendLine("$dimension: ${frontmatterScalar(value)}")
                }
            }
            screen?.frame?.let { frame ->
                appendLine("frame:")
                if (frame.preset.isNotEmpty()) appendLine("  preset: ${frontmatterScalar(frame.preset)}")
                frame.width?.let { appendLine("  width: ${formatNumber(it)}") }
                frame.height?.let { appendLine("  height: ${formatNumber(it)}") }
            }
            screen?.canvas?.let { canvas ->
                appendLine("canvas:")
                if (canvas.section.isNotEmpty()) appendLine("  section: ${frontmatterScalar(canvas.section)}")
                appendLine("  position:")
                appendLine("    x: ${formatNumber(canvas.x)}")
                appendLine("    y: ${formatNumber(canvas.y)}")
            }
            screen?.flow?.let { flow ->
                appendLine("flow:")
                if (flow.id.isNotEmpty()) appendLine("  id: ${frontmatterScalar(flow.id)}")
                if (flow.node.isNotEmpty()) appendLine("  node: ${frontmatterScalar(flow.node)}")
                if (flow.next.isNotEmpty()) {
                    appendLine("  next: [${flow.next.joinToString(", ") { frontmatterScalar(it) }}]")
                }
            }
            if (document.breakpoints.isNotEmpty()) {
                appendLine("breakpoints:")
                document.breakpoints.forEach { breakpoint ->
                    appendLine("  - id: ${frontmatterScalar(breakpoint.id)}")
                    breakpoint.minWidth?.let { appendLine("    minWidth: ${formatNumber(it)}") }
                    breakpoint.maxWidth?.let { appendLine("    maxWidth: ${formatNumber(it)}") }
                }
            }
            if (document.libraries.isNotEmpty()) {
                appendLine("libraries:")
                document.libraries.forEach { library ->
                    appendLine("  - id: ${frontmatterScalar(library.id)}")
                    appendLine("    source: ${frontmatterScalar(library.source)}")
                }
            }
            append("---")
        }
    }

    /** Emits document-scoped CNL variable sections; returns an empty string when none exist. */
    fun emitVariables(document: DesignDocument): String {
        val sections = mutableListOf<String>()
        document.variables.collections.forEach { (id, collection) ->
            val options = buildList {
                if (collection.modes.isNotEmpty()) {
                    add("modes")
                    addAll(collection.modes)
                }
                if (collection.defaultMode.isNotEmpty()) {
                    add("default")
                    add(collection.defaultMode)
                }
            }
            val header = if (options.isEmpty()) {
                "# Collection $id${collection.cnlDisplayName(id)}"
            } else {
                "# Collection $id${collection.cnlDisplayName(id)} (${options.joinToString(" ")})"
            }
            val lines = collection.vars.map { (name, variable) ->
                val modeOrder = collection.modes.filter { it in variable.values } +
                    variable.values.keys.filter { it !in collection.modes }
                val values = modeOrder.flatMap { mode ->
                    listOf(mode, emitVariableValue(variable.values.getValue(mode)))
                }
                "${variable.type.cnlTypeName()} $name ${values.joinToString(" ")}"
            }
            sections += (listOf(header) + listOf("") + lines).joinToString("\n")
        }
        if (document.prototypeVariables.isNotEmpty()) {
            val lines = document.prototypeVariables.map { (name, variable) ->
                val default = variable.default?.let { " default ${emitVariableValue(it)}" }.orEmpty()
                "${variable.type.cnlTypeName()} $name$default"
            }
            sections += (listOf("# Prototype Variables") + listOf("") + lines).joinToString("\n")
        }
        return sections.joinToString("\n\n")
    }

    /** Emits document-scoped shared styles as a CNL `# Styles` section. */
    fun emitStyles(document: DesignDocument): String {
        if (document.styles.isEmpty()) return ""
        val lines = document.styles.map { (id, style) ->
            val (prefix, node) = stylePrototype(style)
            val suffix = phrasesOf(node).joinToString(" ")
            "$prefix $id $suffix".trimEnd()
        }
        return (listOf("# Styles", "") + lines).joinToString("\n")
    }

    /** Emits lifted component definitions as CNL `Component:` sections. */
    fun emitComponents(document: DesignDocument, level: Int = 2, includeId: Boolean = true): String =
        document.components
            .map { (id, component) -> emitComponentDefinition(id, component, document.componentSets, level, includeId) }
            .joinToString("\n\n")

    /** A node is a container (heading) when it is a frame or has children. */
    fun isContainer(node: DesignNode): Boolean =
        node.kind is DesignNodeKind.Frame || node.children.isNotEmpty()

    private fun emitStableSubtree(
        node: DesignNode,
        level: Int,
        includeId: Boolean,
        titleOverride: String? = null,
    ): List<String> {
        val lines = mutableListOf(
            emitHeadingLine(
                node = node,
                level = level,
                includeId = includeId,
                titleOverride = titleOverride ?: stableHeadingTitle(node),
                textAsCharacters = true,
                preserveEmptyTitle = true,
                forceNamePhrase = true,
            ),
        )
        node.children.forEach { child ->
            lines += ""
            lines += emitStableSubtree(child, level + 1, includeId)
        }
        return lines
    }

    private fun phrasesOf(node: DesignNode, includeName: Boolean = true): List<String> =
        CnlGrammar.descriptors
            .filter { includeName || it.kind != CnlPropertyKind.NodeName }
            .sortedBy { it.order }
            .mapNotNull { it.render(node) }

    private fun headingTitle(node: DesignNode, preserveEmpty: Boolean = false): String {
        val name = if (preserveEmpty) node.name else node.name.ifEmpty { "Section" }
        val prefix = headingPrefix(node) ?: return name
        if (name.isEmpty()) return "$prefix:"
        return "$prefix: $name"
    }

    private fun stableHeadingTitle(node: DesignNode): String {
        val prefix = headingPrefix(node) ?: if (node.type == "section") "Section" else null
        return prefix?.let { "$it:" } ?: node.name.ifEmpty { "Section" }
    }

    private fun headingPrefix(node: DesignNode): String? {
        if (node.type == "screen") return null
        return when (node.type) {
            "frame" -> "Frame"
            "group" -> "Group"
            "section" -> null
            "text" -> if (node.role == "button") "Button" else "Text"
            "media" -> "Image"
            "vector" -> "Vector"
            "instance" -> "Instance"
            "shape" -> CnlGrammar.canonicalNoun(node)
            else -> null
        }
    }

    private fun headingCharactersPhrase(node: DesignNode): String? {
        val text = node.kind as? DesignNodeKind.Text ?: return null
        text.content?.defaultText?.takeIf { it.isNotEmpty() }?.let { return "characters ${cnlText(it)}" }
        return when (val characters = text.characters) {
            is Bindable.Value -> characters.value.takeIf { it.isNotEmpty() }?.let { "characters ${cnlText(it)}" }
            else -> null
        }
    }

    private fun stylePrototype(style: DesignStyle): Pair<String, DesignNode> = when (style) {
        is DesignStyle.Paint -> "Paint" to DesignNode(
            id = "__style",
            type = "shape",
            kind = DesignNodeKind.Shape(ShapeType.Rectangle),
            fills = style.value,
        )
        is DesignStyle.Text -> "TextStyle" to DesignNode(
            id = "__style",
            type = "text",
            kind = DesignNodeKind.Text(textStyle = style.value),
        )
        is DesignStyle.Effect -> "Effect" to DesignNode(
            id = "__style",
            type = "shape",
            kind = DesignNodeKind.Shape(ShapeType.Rectangle),
            effects = style.value,
        )
        is DesignStyle.Grid -> "Grid" to DesignNode(
            id = "__style",
            type = "frame",
            kind = DesignNodeKind.Frame,
            layoutGrids = style.value,
        )
    }

    private fun emitComponentDefinition(
        id: String,
        component: DesignComponent,
        sets: Map<String, DesignComponentSet>,
        level: Int,
        includeId: Boolean,
    ): String {
        val root = component.root
        val title = root.name.ifBlank { component.name.ifBlank { id } }
        val phrases = buildList {
            if (includeId && root.id.isNotEmpty()) add("id ${root.id}")
            if (component.name.isNotEmpty()) add("component-name ${cnlTextOrToken(component.name)}")
            componentSetFor(id, sets)?.let { (setId, set, variant) ->
                add("set $setId")
                set.axes.forEach { (axis, values) ->
                    if (values.isNotEmpty()) add("axis $axis (${values.joinToString(" ")})")
                }
                if (variant.isNotEmpty()) {
                    add("variant (${variant.entries.sortedBy { it.key }.joinToString(" ") { "${it.key} ${it.value}" }})")
                }
            }
            component.properties.entries.sortedBy { it.key }.forEach { (name, definition) ->
                add(componentPropPhrase(name, definition))
            }
            addAll(phrasesOf(root, includeName = false))
        }
        val heading = "#".repeat(level.coerceAtLeast(1))
        val lines = mutableListOf("$heading Component: $title ${phrases.joinToString(" ")}".trimEnd())
        root.children.forEach { child ->
            lines += ""
            lines += emitStableSubtree(child, level + 1, includeId)
        }
        return lines.joinToString("\n")
    }

    private fun componentSetFor(
        componentId: String,
        sets: Map<String, DesignComponentSet>,
    ): Triple<String, DesignComponentSet, Map<String, String>>? =
        sets.entries.firstNotNullOfOrNull { (setId, set) ->
            val key = set.variants.entries.firstOrNull { (_, id) -> id == componentId }?.key ?: return@firstNotNullOfOrNull null
            Triple(setId, set, DesignComponentSet.parseVariantKey(key))
        }

    private fun componentPropPhrase(name: String, definition: ComponentPropertyDefinition): String {
        val parts = mutableListOf(componentPropertyTypeWord(definition.type))
        definition.default?.let { parts += "default ${componentPropDefaultValue(it)}" }
        if (definition.preferredValues.isNotEmpty()) {
            parts += "preferred (${definition.preferredValues.joinToString(" ")})"
        }
        definition.minItems?.let { parts += "min $it" }
        definition.maxItems?.let { parts += "max $it" }
        if (definition.allowedContent.isNotEmpty()) {
            parts += "allow (${definition.allowedContent.joinToString(" ")})"
        }
        return "prop $name (${parts.joinToString(" ")})"
    }

    private fun componentPropertyTypeWord(type: ComponentPropertyType): String = when (type) {
        ComponentPropertyType.Text -> "text"
        ComponentPropertyType.Boolean -> "boolean"
        ComponentPropertyType.InstanceSwap -> "instanceSwap"
        ComponentPropertyType.Variant -> "variant"
        ComponentPropertyType.Slot -> "slot"
        ComponentPropertyType.Number -> "number"
        ComponentPropertyType.RawString -> "string"
        ComponentPropertyType.DataBinding -> "dataBinding"
    }

    private fun componentPropDefaultValue(value: PropValue): String = when (value) {
        is PropValue.Text -> cnlText(value.value)
        is PropValue.Bool -> if (value.value) "yes" else "no"
        is PropValue.Number -> formatNumber(value.value)
        is PropValue.Reference -> value.value
        is PropValue.Data -> "{{${value.expression.raw}}}"
        is PropValue.Content -> cnlText(value.content.defaultText)
        is PropValue.SlotContent -> "()"
    }

    private fun cnlTextOrToken(value: String): String =
        if (value.matches(Regex("""[A-Za-z0-9_.\-/]+"""))) value else cnlText(value)

    private fun cnlText(value: String): String =
        "«" + value.replace("»", "\\»") + "»"

    private fun frontmatterScalar(value: String): String =
        if (value.isNotEmpty() && value.none { it == ':' || it == '#' || it == '"' || it == '\'' || it == '\n' }) {
            value
        } else {
            yamlString(value)
        }

    private fun yamlString(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""

    private fun VariableType.cnlTypeName(): String = when (this) {
        VariableType.Color -> "Color"
        VariableType.Number -> "Number"
        VariableType.Text -> "String"
        VariableType.Bool -> "Boolean"
    }

    private fun io.aequicor.visualization.engine.ir.model.VariableCollection.cnlDisplayName(id: String): String =
        name.takeIf { it.isNotEmpty() && it != id }?.let { " ${cnlText(it)}" }.orEmpty()

    private fun emitVariableValue(value: VariableValue): String = when (value) {
        is VariableValue.ColorValue -> value.value.toCnlHex()
        is VariableValue.NumberValue -> formatNumber(value.value)
        is VariableValue.TextValue -> "«${value.value}»"
        is VariableValue.BoolValue -> if (value.value) "yes" else "no"
        is VariableValue.Alias -> "\$${value.varId}"
    }

    private fun DesignColor.toCnlHex(): String {
        val rgb = (argb and 0x00FFFFFF).toString(16).uppercase().padStart(6, '0')
        if (alpha == 0xFF) return "#$rgb"
        val a = alpha.toString(16).uppercase().padStart(2, '0')
        return "#$rgb$a"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
