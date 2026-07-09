package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.ast.KeyHint
import io.aequicor.visualization.engine.frontend.ast.SemanticKind
import io.aequicor.visualization.engine.frontend.ast.SemanticNode
import io.aequicor.visualization.engine.frontend.ast.SemanticScreen
import io.aequicor.visualization.engine.frontend.ast.SemanticText
import io.aequicor.visualization.engine.frontend.blocks.ActionPatch
import io.aequicor.visualization.engine.frontend.blocks.ComponentPatch
import io.aequicor.visualization.engine.frontend.blocks.ExportPatch
import io.aequicor.visualization.engine.frontend.blocks.HandoffPatch
import io.aequicor.visualization.engine.frontend.blocks.InteractionPatch
import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.frontend.blocks.MaskPatch
import io.aequicor.visualization.engine.frontend.blocks.MediaPatch
import io.aequicor.visualization.engine.frontend.blocks.MotionPatch
import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.OverridesPatch
import io.aequicor.visualization.engine.frontend.blocks.PropsPatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsivePatch
import io.aequicor.visualization.engine.frontend.blocks.ShapePatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TypedBlockReader
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.blocks.VariablesPatch
import io.aequicor.visualization.engine.frontend.blocks.VectorPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.edit.SlmEditIndex
import io.aequicor.visualization.engine.frontend.escape.IrEscapeHatch
import io.aequicor.visualization.engine.frontend.expr.ComparisonOp
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.frontend.i18n.TextEntry
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.CodeHints
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignBreakpoint
import io.aequicor.visualization.engine.ir.model.DesignCondition
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignI18n
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignLibrary
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignRepeat
import io.aequicor.visualization.engine.ir.model.DesignScreenMeta
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignTable
import io.aequicor.visualization.engine.ir.model.DesignVariables
import io.aequicor.visualization.engine.ir.model.FramePreset
import io.aequicor.visualization.engine.ir.model.CanvasPlacement
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignFlow
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.bindable

/** Output of the normalizer: the IR document plus i18n and edit side products. */
data class NormalizedScreen(
    val document: DesignDocument,
    val textEntries: List<TextEntry>,
    val editIndex: SlmEditIndex,
)

/**
 * Materializes a [SemanticScreen] into a [DesignDocument]: one page, one screen
 * root frame sized from the frontmatter, ids via [SlugGenerator], sibling order via
 * [resolveOrder], typed patches via [PatchMerger], component subtrees lifted via
 * [ComponentLifter], document-level `variables`/`handoff` blocks lifted to document
 * fields. Also collects [TextEntry] per [SemanticText] and builds the [SlmEditIndex].
 */
class IrNormalizer(
    private val diagnostics: DiagnosticCollector,
    private val fileName: String,
) {
    fun normalize(screen: SemanticScreen): NormalizedScreen =
        Normalization(screen, diagnostics, fileName).run()
}

private class Normalization(
    private val screen: SemanticScreen,
    private val diagnostics: DiagnosticCollector,
    private val fileName: String,
) {
    private val slugGenerator = SlugGenerator(diagnostics)
    private val merger = PatchMerger(diagnostics, screen.sourceLocale.tag, fileName)
    private val lifter = ComponentLifter(diagnostics)
    private val escapeHatch = IrEscapeHatch(diagnostics, fileName)
    private val textEntries = mutableListOf<TextEntry>()
    private val anchorOwners = mutableMapOf<String, SlmSourceSpan>()
    private val cnlOwners = mutableMapOf<String, SlmSourceSpan>()
    private val irSpliceNodes = mutableSetOf<String>()
    private val variableCollections = LinkedHashMap<String, VariableCollection>()
    private val prototypeVariables = LinkedHashMap<String, PrototypeVariable>()
    private var handoff = DesignHandoff()

    fun run(): NormalizedScreen {
        val frontmatter = screen.frontmatter
        var root = checkNotNull(materialize(screen.root, isRoot = true)) {
            "The screen root is never an ir splice"
        }

        val defs = screen.componentDefs.mapNotNull { def ->
            val defRoot = materialize(def, isRoot = false) ?: return@mapNotNull null
            // Re-read without the shared collector: materialize already reported
            // this node's block diagnostics.
            val componentPatch = def.explicitPatches
                .mapNotNull { TypedBlockReader.read(it, DiagnosticCollector(fileName)) }
                .filterIsInstance<ComponentPatch>()
                .lastOrNull()
            lifter.register(defRoot, componentPatch, def.span.startLine)
            collectComponentPropTexts(defRoot.id, componentPatch, def.span)
            defRoot
        }
        // Validate instance refs used inside the definition trees as well.
        defs.forEach { lifter.resolveInstances(it) }
        root = lifter.resolveInstances(root)

        screen.title?.let { collectText(it, root.id) }

        val page = DesignPage(
            id = latinizeToCamelCase(frontmatter.page).ifEmpty { "page1" },
            name = frontmatter.page,
            children = listOf(root),
        )
        val document = DesignDocument(
            id = frontmatter.screen,
            name = screen.title?.defaultText ?: frontmatter.screen,
            pages = listOf(page),
            components = lifter.components,
            componentSets = lifter.componentSets,
            variables = DesignVariables(collections = variableCollections),
            prototypeVariables = prototypeVariables,
            screen = DesignScreenMeta(
                id = frontmatter.screen,
                name = screen.title?.defaultText ?: frontmatter.screen,
                page = frontmatter.page,
                // Explicit frontmatter modes win over prose-extracted ones.
                modes = screen.modes + frontmatter.modes,
                frame = frontmatter.frame?.let {
                    FramePreset(preset = it.preset, width = it.width, height = it.height)
                },
                canvas = frontmatter.canvas?.let {
                    CanvasPlacement(section = it.section, x = it.x ?: 0.0, y = it.y ?: 0.0)
                },
                flow = frontmatter.flow?.let {
                    DesignFlow(id = it.id, node = it.node, next = it.next)
                },
            ),
            libraries = frontmatter.libraries.map { DesignLibrary(id = it.id, source = it.source) },
            breakpoints = frontmatter.breakpoints.map {
                DesignBreakpoint(id = it.id, minWidth = it.minWidth, maxWidth = it.maxWidth)
            },
            i18n = DesignI18n(
                sourceLocale = screen.sourceLocale.tag,
                targetLocales = screen.frontmatter.targetLocales.map { it.tag },
            ),
            handoff = handoff,
        )
        return NormalizedScreen(
            document = document,
            textEntries = textEntries.toList(),
            editIndex = SlmEditIndex(anchorOwners.toMap(), irSpliceNodes.toSet(), cnlOwners.toMap()),
        )
    }

    // --- node materialization ---

    /** Null only when an ```ir splice fails to parse (the node is skipped). */
    private fun materialize(node: SemanticNode, isRoot: Boolean): DesignNode? {
        if (node.kind == SemanticKind.IrSplice) return splice(node)
        val explicit = readPatches(node)
        val semantic = node.semanticPatches.map {
            AppliedPatch(it, blockKeyOf(it), node.span.startLine)
        }
        liftDocumentPatches(explicit.map { it.patch } + semantic.map { it.patch })

        val nodePatch = explicit.map { it.patch }.filterIsInstance<NodePatch>()
            .lastOrNull { it.id != null }
        val explicitId = if (isRoot) {
            nodePatch?.id ?: screen.frontmatter.screen.ifBlank { null }
        } else {
            nodePatch?.id
        }
        val typeHint = explicit.map { it.patch }.filterIsInstance<NodePatch>()
            .lastOrNull { it.type != null }?.type
        val id = slugGenerator.idFor(
            explicitId = explicitId,
            slugHint = node.slugHint,
            name = node.name,
            role = node.role,
            kind = typeHint ?: kindOrdinalName(node.kind),
            line = node.span.startLine,
        )

        var design = baseNode(node, id, isRoot)
        design = merger.apply(design, semantic, explicit)

        // `props.i18nKey` is an explicit key override for the instance label.
        var i18nKeyProp: String? = null
        (design.kind as? DesignNodeKind.Instance)?.let { kind ->
            val keyProp = kind.props["i18nKey"]
            if (keyProp is PropValue.Text) {
                i18nKeyProp = keyProp.value
                design = design.copy(kind = kind.copy(props = kind.props - "i18nKey"))
            }
        }

        val children = node.children.mapNotNull { materialize(it, isRoot = false) }
        val ordered = resolveOrder(children, diagnostics, parentLabel = id, line = node.span.startLine)

        val blockSourceMaps = node.explicitPatches.associate { entry ->
            entry.kind.key to SourceLocation(file = fileName, line = entry.span.startLine)
        }
        design = design.copy(
            children = ordered,
            sourceMap = SourceLocation(file = fileName, line = node.span.startLine),
            blockSourceMaps = blockSourceMaps,
        )

        if (node.isAnchor) anchorOwners[id] = node.span
        if (node.isCnlElement) cnlOwners[id] = node.span
        collectNodeTexts(node, id, explicit, i18nKeyProp)
        return design
    }

    private fun splice(node: SemanticNode): DesignNode? {
        val block = node.irSplice ?: return null
        var spliced = escapeHatch.splice(block, slugGenerator) ?: return null
        irSpliceNodes += spliced.id
        node.condition?.let {
            spliced = spliced.copy(condition = DesignCondition(DesignExpression(renderExpression(it))))
        }
        return spliced
    }

    /**
     * Collects the node's localizable text: the semantic text (or action label)
     * with any explicit `text.key`/`text.defaultText`/`props.i18nKey` override
     * applied, or a patch-only text when there is no semantic one.
     */
    private fun collectNodeTexts(
        node: SemanticNode,
        id: String,
        explicit: List<AppliedPatch>,
        i18nKeyProp: String?,
    ) {
        val textPatch = explicit.map { it.patch }.filterIsInstance<TextPatch>()
            .lastOrNull { it.key != null || it.defaultText != null }
        val semanticText = node.text ?: node.action?.label
        when {
            semanticText != null -> textEntries += TextEntry(
                keyHint = semanticText.keyHint,
                explicitKey = i18nKeyProp ?: textPatch?.key ?: semanticText.explicitKey,
                defaultText = textPatch?.defaultText ?: semanticText.defaultText,
                params = semanticText.params,
                span = semanticText.span,
                nodeId = id,
            )
            textPatch != null -> textEntries += TextEntry(
                keyHint = KeyHint.Plain,
                explicitKey = i18nKeyProp ?: textPatch.key,
                defaultText = textPatch.defaultText.orEmpty(),
                params = emptyMap(),
                span = node.span,
                nodeId = id,
            )
        }
    }

    /** Component text-prop defaults become `components.{slug}.{prop}` resources. */
    private fun collectComponentPropTexts(
        componentId: String,
        patch: ComponentPatch?,
        span: SlmSourceSpan,
    ) {
        val properties = patch?.properties ?: return
        val slug = componentSlug(patch.name.orEmpty(), componentId)
        properties.forEach { (propName, definition) ->
            if (definition.type != ComponentPropertyType.Text) return@forEach
            val (explicitKey, defaultText) = when (val default = definition.default) {
                is PropValue.Content ->
                    default.content.key.takeIf { it.isNotEmpty() } to default.content.defaultText
                is PropValue.Text -> null to default.value
                else -> return@forEach
            }
            textEntries += TextEntry(
                keyHint = KeyHint.ComponentProp(componentId, slug, propName),
                explicitKey = explicitKey,
                defaultText = defaultText,
                params = emptyMap(),
                span = span,
                nodeId = componentId,
            )
        }
    }

    private fun baseNode(node: SemanticNode, id: String, isRoot: Boolean): DesignNode {
        val condition = node.condition?.let { DesignCondition(DesignExpression(renderExpression(it))) }
        val base = DesignNode(
            id = id,
            type = typeOf(node.kind),
            kind = kindOf(node),
            name = node.name,
            role = node.role.ifEmpty { defaultRole(node.kind) },
            condition = condition,
            repeat = node.repeat?.let {
                DesignRepeat(
                    itemName = it.itemName,
                    collection = DesignExpression(it.collection.segments.joinToString(".")),
                )
            },
            interactions = node.action?.let {
                listOf(
                    DesignInteraction(
                        trigger = InteractionTrigger.OnClick,
                        actions = listOf(DesignAction.Navigate(to = it.navigateTo)),
                        sourceMap = SourceLocation(file = fileName, line = node.span.startLine),
                    ),
                )
            } ?: emptyList(),
        )
        if (!isRoot) return base
        val frame = screen.frontmatter.frame ?: return base
        return base.copy(
            size = DesignSize(width = frame.width, height = frame.height),
            sizing = if (frame.width != null || frame.height != null) {
                DesignSizing(SizingMode.Fixed, SizingMode.Fixed)
            } else {
                null
            },
        )
    }

    private fun typeOf(kind: SemanticKind): String = when (kind) {
        SemanticKind.Screen -> "screen"
        SemanticKind.Section -> "section"
        SemanticKind.Group -> "group"
        SemanticKind.Text, SemanticKind.Action -> "text"
        SemanticKind.Instance -> "instance"
        SemanticKind.Media -> "media"
        SemanticKind.Table -> "table"
        SemanticKind.Callout, SemanticKind.EmptyState, SemanticKind.Repeat -> "frame"
        SemanticKind.IrSplice -> "ir"
    }

    private fun defaultRole(kind: SemanticKind): String = when (kind) {
        SemanticKind.Callout -> "callout"
        SemanticKind.EmptyState -> "emptyState"
        SemanticKind.Repeat -> "card"
        else -> ""
    }

    private fun kindOrdinalName(kind: SemanticKind): String = when (kind) {
        SemanticKind.Screen -> "screen"
        SemanticKind.Section -> "section"
        SemanticKind.Group, SemanticKind.Callout, SemanticKind.EmptyState -> "group"
        SemanticKind.Text -> "text"
        SemanticKind.Action -> "action"
        SemanticKind.Instance -> "instance"
        SemanticKind.Media -> "media"
        SemanticKind.Table -> "table"
        SemanticKind.Repeat -> "repeat"
        SemanticKind.IrSplice -> "ir"
    }

    private fun kindOf(node: SemanticNode): DesignNodeKind = when (node.kind) {
        SemanticKind.Text -> DesignNodeKind.Text(content = node.text?.let(::contentOf))
        SemanticKind.Action -> DesignNodeKind.Text(content = node.action?.label?.let(::contentOf))
        SemanticKind.Instance -> DesignNodeKind.Instance(
            componentId = node.componentRef.orEmpty().bindable(),
            variant = node.variant,
            props = buildMap {
                (node.text ?: node.action?.label)?.let {
                    put("label", PropValue.Content(contentOf(it)))
                }
                node.propBindings.forEach { (name, expression) ->
                    put(name, PropValue.Data(DesignExpression(renderExpression(expression))))
                }
            },
        )
        SemanticKind.Media -> DesignNodeKind.Media(
            DesignMedia(
                assetId = "",
                alt = node.text?.let(::contentOf),
            ),
        )
        SemanticKind.Table -> DesignNodeKind.Table(DesignTable())
        SemanticKind.IrSplice -> DesignNodeKind.Unknown("ir")
        else -> DesignNodeKind.Frame
    }

    /**
     * IR text content for a semantic text. Explicit keys are carried through;
     * generated keys are wired in afterwards by the i18n stage
     * (`i18n/applyGeneratedKeys`).
     */
    private fun contentOf(text: SemanticText): TextContent = TextContent(
        key = text.explicitKey.orEmpty(),
        defaultLocale = screen.sourceLocale.tag,
        defaultText = text.defaultText,
        params = text.params.mapValues { (_, expression) ->
            Bindable.DataRef(DesignExpression(renderExpression(expression)))
        },
    )

    private fun collectText(text: SemanticText, nodeId: String) {
        textEntries += TextEntry(
            keyHint = text.keyHint,
            explicitKey = text.explicitKey,
            defaultText = text.defaultText,
            params = text.params,
            span = text.span,
            nodeId = nodeId,
        )
    }

    // --- typed patches ---

    private fun readPatches(node: SemanticNode): List<AppliedPatch> =
        node.explicitPatches.mapNotNull { entry ->
            TypedBlockReader.read(entry, diagnostics)?.let {
                AppliedPatch(it, entry.kind.key, entry.span.startLine)
            }
        }

    /** `variables`/`handoff` blocks contribute to the document, wherever anchored. */
    private fun liftDocumentPatches(patches: List<TypedPatch>) {
        patches.forEach { patch ->
            when (patch) {
                is VariablesPatch -> {
                    patch.collections?.forEach { (id, collection) ->
                        variableCollections[id] = collection
                    }
                    patch.prototype?.forEach { (name, variable) ->
                        prototypeVariables[name] = variable
                    }
                }
                is HandoffPatch -> handoff = DesignHandoff(
                    annotations = handoff.annotations + patch.handoff.annotations,
                    measurements = handoff.measurements + patch.handoff.measurements,
                    code = patch.handoff.code ?: handoff.code,
                )
                else -> {}
            }
        }
    }
}

/** `ds/MissionCard` -> `missionCard`; falls back to [fallback] (the component id). */
internal fun componentSlug(name: String, fallback: String): String {
    val base = name.substringAfterLast('/').trim()
    if (base.isEmpty()) return fallback
    return if (base.any { !it.isLetterOrDigit() }) {
        latinizeToCamelCase(base).ifEmpty { fallback }
    } else {
        base.replaceFirstChar { it.lowercaseChar() }
    }
}

/** Reserved key of the block a patch type belongs to (for provenance labels). */
internal fun blockKeyOf(patch: TypedPatch): String = when (patch) {
    is NodePatch -> "node"
    is LayoutPatch -> "layout"
    is StylePatch -> "style"
    is TextPatch -> "text"
    is ComponentPatch -> "component"
    is PropsPatch -> "props"
    is OverridesPatch -> "overrides"
    is MediaPatch -> "media"
    is ShapePatch -> "shape"
    is VectorPatch -> "vector"
    is MaskPatch -> "mask"
    is ActionPatch -> "action"
    is InteractionPatch -> "interaction"
    is MotionPatch -> "motion"
    is ResponsivePatch -> "responsive"
    is VariablesPatch -> "variables"
    is HandoffPatch -> "handoff"
    is ExportPatch -> "export"
}

/** Renders an [SlmExpression] back to its canonical `{{...}}` inner text. */
internal fun renderExpression(expression: SlmExpression): String = when (expression) {
    is SlmExpression.Path -> expression.segments.joinToString(".")
    is SlmExpression.Repeat ->
        "${expression.itemName} in ${renderExpression(expression.collection)}"
    is SlmExpression.Comparison ->
        "${renderExpression(expression.left)} ${expression.op.symbol} ${renderExpression(expression.right)}"
    is SlmExpression.Literal.Num ->
        if (expression.value == expression.value.toLong().toDouble()) {
            expression.value.toLong().toString()
        } else {
            expression.value.toString()
        }
    is SlmExpression.Literal.Str -> "'${expression.value}'"
    is SlmExpression.Literal.Bool -> expression.value.toString()
    is SlmExpression.Raw -> expression.text
}
