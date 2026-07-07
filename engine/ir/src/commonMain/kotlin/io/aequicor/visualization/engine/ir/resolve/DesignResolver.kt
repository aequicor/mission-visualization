package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignAnchors
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignMask
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignNodePatch
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignRepeat
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignTable
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.ResponsiveVariant
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.appliedTo

/**
 * Turns the authored [DesignDocument] tree into a [ResolvedNode] tree.
 *
 * Per-node resolution order (design section B): responsive patch -> condition ->
 * repeat -> variables/modes -> instances/components/props (incl. libraries, slots,
 * nested overrides) -> i18n text -> `{{...}}` data bindings -> lowering (media,
 * tables, logical padding/anchors, RTL rows, explicit order, mask normalization).
 */
class DesignResolver(
    private val document: DesignDocument,
    private val context: ResolveContext = ResolveContext(),
) {

    /** Locale used for i18n text resolution: explicit context locale, else the source locale. */
    val activeLocale: String = context.locale ?: document.i18n.sourceLocale

    /** Layout direction: explicit in the context, else derived from [activeLocale]. */
    val direction: LayoutDirection = context.direction ?: directionForLocale(activeLocale)

    /** Explicit context breakpoint, else the first document breakpoint containing the viewport width. */
    val activeBreakpointId: String? = context.breakpointId
        ?: context.viewport?.width?.let { width ->
            document.breakpoints.firstOrNull { breakpoint ->
                (breakpoint.minWidth == null || width >= breakpoint.minWidth) &&
                    (breakpoint.maxWidth == null || width <= breakpoint.maxWidth)
            }?.id
        }

    /**
     * Active responsive dimension values, matched by [ResponsiveVariant.selectors]:
     * `screen.modes` defaults, then the derived Breakpoint/Locale/Direction entries,
     * then explicit [ResolveContext.dimensions] overrides.
     */
    val activeDimensions: Map<ResponsiveDimension, String> = buildMap {
        document.screen?.modes?.forEach { (name, value) ->
            ResponsiveDimension.entries
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { put(it, value) }
        }
        put(ResponsiveDimension.Locale, activeLocale)
        put(ResponsiveDimension.Direction, if (direction == LayoutDirection.Rtl) "rtl" else "ltr")
        activeBreakpointId?.let { put(ResponsiveDimension.Breakpoint, it) }
        putAll(context.dimensions)
    }

    /** Context resources win over the document bundles, per locale and key. */
    private val mergedResources: Map<String, Map<String, String>> =
        (document.i18n.resources.keys + context.resources.keys).associateWith { locale ->
            document.i18n.resources[locale].orEmpty() + context.resources[locale].orEmpty()
        }

    private val collectedDiagnostics = mutableListOf<DesignDiagnostic>()

    val diagnostics: List<DesignDiagnostic>
        get() = collectedDiagnostics

    private data class Scope(
        val modes: Map<String, String> = emptyMap(),
        val props: Map<String, PropValue> = emptyMap(),
        val idPrefix: String = "",
        val overrides: List<InstanceOverride> = emptyList(),
        val componentChain: List<String> = emptyList(),
        /** Authored id of the outermost instance being expanded, if any. */
        val selectableRootId: String? = null,
        /** Library whose document defines the component subtree being expanded; "" = host. */
        val libraryId: String = "",
        /** Layered `{{...}}` data: repeat item bindings over [ResolveContext.data]. */
        val data: EvalScope = EvalScope(),
    )

    private fun rootScope(): Scope =
        Scope(modes = context.modeSelections, data = EvalScope(bindings = context.data))

    fun resolvePage(page: DesignPage): List<ResolvedNode> =
        page.children.flatMap { resolveNodes(it, rootScope()) }

    fun resolveNodeTree(node: DesignNode): ResolvedNode? =
        resolveNodes(node, rootScope()).firstOrNull()

    // --- Steps 2-4: responsive patch, condition, repeat -----------------------

    private fun resolveNodes(node: DesignNode, scope: Scope): List<ResolvedNode> {
        val patched = applyResponsive(node)
        patched.repeat?.let { repeat -> return expandRepeat(patched, repeat, scope) }
        if (!passesCondition(patched, scope)) return emptyList()
        return listOfNotNull(resolveNode(patched, scope))
    }

    /** Best-matching responsive variants patch the raw node before anything else resolves. */
    private fun applyResponsive(node: DesignNode): DesignNode {
        if (node.responsive.isEmpty()) return node
        val applicable = node.responsive.filter { variant ->
            variant.selectors.all { (dimension, value) -> activeDimensions[dimension] == value }
        }
        if (applicable.isEmpty()) return node
        return mergedPatch(applicable, node.id).appliedTo(node)
    }

    /**
     * Per property group the variant with the most selectors wins; an equal-specificity
     * conflict on the same group warns and keeps the first-declared variant (the
     * validator reports it as an ambiguity error separately).
     */
    private fun mergedPatch(variants: List<ResponsiveVariant>, nodeId: String): DesignNodePatch {
        fun <T : Any> pick(group: String, property: (DesignNodePatch) -> T?): T? {
            val candidates = variants.filter { property(it.patch) != null }
            val winner = candidates.maxByOrNull { it.selectors.size } ?: return null
            if (candidates.count { it.selectors.size == winner.selectors.size } > 1) {
                warn(
                    "Responsive variants of equal specificity both patch '$group' " +
                        "on '$nodeId'; first declared wins",
                )
            }
            return property(winner.patch)
        }
        return DesignNodePatch(
            visible = pick("visible") { it.visible },
            opacity = pick("opacity") { it.opacity },
            layout = pick("layout") { it.layout },
            layoutChild = pick("layoutChild") { it.layoutChild },
            gridPlacement = pick("gridPlacement") { it.gridPlacement },
            sizing = pick("sizing") { it.sizing },
            size = pick("size") { it.size },
            minSize = pick("minSize") { it.minSize },
            maxSize = pick("maxSize") { it.maxSize },
            fills = pick("fills") { it.fills },
            strokes = pick("strokes") { it.strokes },
            effects = pick("effects") { it.effects },
            cornerRadius = pick("cornerRadius") { it.cornerRadius },
            textStyle = pick("textStyle") { it.textStyle },
            scroll = pick("scroll") { it.scroll },
        )
    }

    /** A false condition drops the node; an unevaluable one keeps it and warns. */
    private fun passesCondition(node: DesignNode, scope: Scope): Boolean {
        val condition = node.condition ?: return true
        val value = ExpressionEvaluator.evaluate(condition.expression, scope.data) { failure ->
            warn(
                "Condition '{{${condition.expression.raw}}}' on '${node.id}' " +
                    "failed to evaluate ($failure); node kept",
            )
        }
        return when (value) {
            is DataValue.Bool -> value.value
            DataValue.Null -> false
            null -> true
            else -> {
                warn("Condition '{{${condition.expression.raw}}}' on '${node.id}' is not a boolean; node kept")
                true
            }
        }
    }

    /**
     * One clone per collection item with a stable id `"<id>[<key>]"` (key expression
     * result, else the index); clone descendants are namespaced under `"<id>[<key>]/"`
     * and see the item (and index) bindings layered over the data scope. A per-item
     * condition is evaluated inside the item scope.
     *
     * Preview mode (documented): when the collection cannot be evaluated — typically no
     * runtime data is wired yet — the node passes through once, without a repeat warning.
     */
    private fun expandRepeat(node: DesignNode, repeat: DesignRepeat, scope: Scope): List<ResolvedNode> {
        val collection = ExpressionEvaluator.evaluate(repeat.collection, scope.data)
        val items = (collection as? DataValue.ListValue)?.items
        val template = node.copy(repeat = null)
        if (items == null) {
            if (!passesCondition(template, scope)) return emptyList()
            return listOfNotNull(resolveNode(template, scope))
        }
        return items.mapIndexedNotNull { index, item ->
            val bindings = buildMap {
                put(repeat.itemName, item)
                repeat.indexName?.let { put(it, DataValue.Num(index.toDouble())) }
            }
            val itemData = EvalScope(scope.data, bindings)
            val key = repeat.key
                ?.let { keyExpression ->
                    ExpressionEvaluator.evaluate(keyExpression, itemData) { failure ->
                        warn(
                            "Repeat key '{{${keyExpression.raw}}}' on '${node.id}' " +
                                "failed to evaluate ($failure); using index",
                        )
                    }?.let(::scalarToString)
                }
                ?: index.toString()
            val cloneId = "${node.id}[$key]"
            val itemScope = scope.copy(
                idPrefix = "${scope.idPrefix}$cloneId/",
                data = itemData,
            )
            if (!passesCondition(template, itemScope)) return@mapIndexedNotNull null
            resolveNode(template, itemScope)?.copy(id = scope.idPrefix + cloneId)
        }
    }

    // --- Steps 5-9: variables, instances, text, bindings, lowering -------------

    private fun resolveNode(node: DesignNode, outerScope: Scope): ResolvedNode? {
        val scope = outerScope.copy(modes = outerScope.modes + node.variableModes)
        val override = mergedOverrideFor(node, scope)

        val visible = override?.visible?.let { resolveBoolean(it, scope, true) }
            ?: resolveBoolean(node.visible, scope, true)
        if (!visible) return null
        if (node.kind is DesignNodeKind.Slice) return null
        if (node.kind is DesignNodeKind.Instance) return expandInstance(node, node.kind, scope, override)

        val childScope = scope.copy(
            overrides = scope.overrides.filterNot { it.target.size == 1 && it.target.first() == node.id },
        )
        val layout = resolveAutoLayout(node.layout, scope)
        val table = (node.kind as? DesignNodeKind.Table)?.table
        val (effectiveLayout, children) = if (table != null) {
            lowerTable(node, table, layout, childScope, scope)
        } else {
            layout to resolveChildren(node, childNodesFor(node, override, scope), layout, childScope)
        }
        val media = (node.kind as? DesignNodeKind.Media)?.media
        val resolvedMedia = media?.let { resolveMedia(it, scope) }
        return ResolvedNode(
            id = scope.idPrefix + node.id,
            sourceId = node.id,
            selectableId = scope.selectableRootId ?: node.id,
            type = node.type,
            name = node.name,
            opacity = override?.opacity?.let { resolveDouble(it, scope, 1.0) }
                ?: resolveDouble(node.opacity, scope, 1.0),
            rotation = node.rotation,
            position = node.position,
            constraints = node.constraints,
            size = node.size,
            sizing = node.sizing ?: DesignSizing(),
            minSize = node.minSize,
            maxSize = node.maxSize,
            layout = effectiveLayout,
            layoutChild = node.layoutChild,
            gridPlacement = node.gridPlacement,
            fills = resolveFills(node, override, scope) +
                listOfNotNull(media?.let { mediaFill(it, resolvedMedia!!, scope) }),
            strokes = resolveStrokes(node, override, scope),
            effects = resolveEffects(node, scope),
            cornerRadius = resolveCornerRadius(override?.cornerRadius ?: node.cornerRadius, scope),
            text = (node.kind as? DesignNodeKind.Text)?.let { resolveText(it, override, scope) },
            shape = node.kind as? DesignNodeKind.Shape,
            scroll = node.scroll,
            role = node.role,
            blendMode = node.blendMode,
            interactions = node.interactions.map { resolveInteraction(it, scope) },
            media = resolvedMedia,
            mask = resolveMask(node, scope),
            motion = node.motion,
            exportSettings = node.exportSettings,
            layoutGrids = resolveLayoutGrids(node),
            guides = node.guides,
            annotation = (node.kind as? DesignNodeKind.Annotation)?.annotation,
            sourceMap = node.sourceMap,
            children = children,
        )
    }

    /** Slot fill priority: override slotContent -> SlotContent prop of the same name -> authored children. */
    private fun childNodesFor(node: DesignNode, override: InstanceOverride?, scope: Scope): List<DesignNode> {
        val slot = node.kind as? DesignNodeKind.Slot ?: return node.children
        return override?.slotContent
            ?: (scope.props[slot.slotName] as? PropValue.SlotContent)?.nodes
            ?: node.children
    }

    /**
     * Resolves children with logical anchors mapped to physical position/constraints,
     * explicit `order` applied (stable sort; unordered nodes keep document position),
     * and RTL horizontal rows reversed so the layout engine stays direction-free.
     */
    private fun resolveChildren(
        parent: DesignNode,
        children: List<DesignNode>,
        layout: ResolvedAutoLayout,
        scope: Scope,
    ): List<ResolvedNode> {
        val resolved = children.flatMap { child ->
            resolveNodes(child, scope).map { resolvedChild ->
                val anchored = child.anchors
                    ?.let { anchors -> applyAnchors(resolvedChild, anchors, parent, scope) }
                    ?: resolvedChild
                anchored to (child.order ?: 0)
            }
        }
        val ordered = resolved.sortedBy { (_, order) -> order }.map { (child, _) -> child }
        return if (direction == LayoutDirection.Rtl && layout.mode == LayoutMode.Horizontal) {
            ordered.reversed()
        } else {
            ordered
        }
    }

    // --- Instances -------------------------------------------------------------

    private fun expandInstance(
        node: DesignNode,
        instance: DesignNodeKind.Instance,
        scope: Scope,
        override: InstanceOverride?,
    ): ResolvedNode? {
        // Nested-instance overrides (variant/props at the instance's id path) apply
        // before expansion; resetOverrides drops the instance's own baked overrides.
        val selection = instance.variant + (override?.variant ?: emptyMap())
        val instanceProps = instance.props + (override?.props ?: emptyMap())
        val instanceOverrides = if (instance.resetOverrides) emptyList() else instance.overrides
        val resolvedInstanceId = scope.idPrefix + node.id

        // libraryRef (or a "<libId>/" componentId prefix known to the context's library
        // registry) routes the component lookup through that library's document.
        val requestedId = resolveString(instance.componentId, scope, "")
        var libraryId = scope.libraryId
        var lookupId = requestedId
        if (instance.libraryRef.isNotEmpty()) {
            libraryId = instance.libraryRef
        } else {
            val prefix = requestedId.substringBefore('/')
            if (prefix != requestedId && prefix in context.libraries) {
                libraryId = prefix
                lookupId = requestedId.substringAfter('/')
            }
        }
        val host = if (libraryId.isEmpty()) document else context.libraries[libraryId]
        if (host == null) {
            warn("Unknown library '$libraryId' for instance '${node.id}'")
            return instancePlaceholder(node, resolvedInstanceId, scope)
        }
        val componentId = host.componentSets[lookupId]
            ?.let { set ->
                set.resolveVariant(selection) ?: run {
                    warn("Component set '$lookupId' has no variant for $selection")
                    null
                }
            }
            ?: lookupId
        val component = host.components[componentId]
        if (component == null) {
            warn("Unknown component '$requestedId' for instance '${node.id}'")
            return instancePlaceholder(node, resolvedInstanceId, scope)
        }
        val chainId = if (libraryId.isEmpty()) componentId else "$libraryId/$componentId"
        if (chainId in scope.componentChain) {
            warn("Component cycle detected at '$chainId'; instance '${node.id}' truncated")
            return instancePlaceholder(node, resolvedInstanceId, scope)
        }

        val defaults = component.properties.mapNotNull { (name, definition) ->
            definition.default?.let { name to it }
        }.toMap()
        val variantProps = selection.mapValues { (_, value) -> PropValue.Text(value) }

        // Visual attributes authored on the instance node itself, and any override
        // targeting the instance, are re-targeted at the component root. Order matters:
        // later entries win, so consumer overrides beat template-baked ones.
        val rootTarget = listOf(component.root.id)
        val authoredAsOverride = InstanceOverride(
            target = rootTarget,
            fills = node.fills,
            strokes = node.strokes,
            cornerRadius = node.cornerRadius,
        ).takeIf { node.fills != null || node.strokes != null || node.cornerRadius != null }
        val overrideForRoot = override?.copy(target = rootTarget, opacity = null, visible = null)
        val innerScope = Scope(
            modes = scope.modes,
            props = defaults + variantProps + instanceProps,
            idPrefix = "$resolvedInstanceId/",
            overrides = instanceOverrides +
                listOfNotNull(authoredAsOverride) +
                scope.overrides
                    .filter { it.target.size > 1 && it.target.first() == node.id }
                    .map { it.copy(target = it.target.drop(1)) } +
                listOfNotNull(overrideForRoot),
            componentChain = scope.componentChain + chainId,
            selectableRootId = scope.selectableRootId ?: node.id,
            libraryId = libraryId,
            data = scope.data,
        )

        val root = resolveNode(applyResponsive(component.root), innerScope) ?: return null
        val instanceOpacity = override?.opacity?.let { resolveDouble(it, scope, 1.0) }
            ?: resolveDouble(node.opacity, scope, 1.0)
        return root.copy(
            id = resolvedInstanceId,
            sourceId = node.id,
            selectableId = scope.selectableRootId ?: node.id,
            type = node.type,
            name = node.name.ifBlank { component.name },
            opacity = root.opacity * instanceOpacity,
            rotation = node.rotation,
            position = node.position,
            constraints = node.constraints,
            layoutChild = node.layoutChild,
            gridPlacement = node.gridPlacement,
            sizing = node.sizing ?: root.sizing,
            minSize = node.minSize ?: root.minSize,
            maxSize = node.maxSize ?: root.maxSize,
            size = DesignSize(
                width = node.size.width ?: root.size.width,
                height = node.size.height ?: root.size.height,
            ),
            effects = if (node.effects.isEmpty()) root.effects else node.effects.mapNotNull { resolveEffect(it, scope) },
            layout = if (node.layout.clipsContent) root.layout.copy(clipsContent = true) else root.layout,
            scroll = if (node.scroll != DesignScroll()) node.scroll else root.scroll,
            role = node.role.ifEmpty { root.role },
            blendMode = if (node.blendMode != "normal") node.blendMode else root.blendMode,
            interactions = node.interactions.map { resolveInteraction(it, scope) }.ifEmpty { root.interactions },
            mask = resolveMask(node, scope) ?: root.mask,
            motion = node.motion ?: root.motion,
            exportSettings = node.exportSettings.ifEmpty { root.exportSettings },
            layoutGrids = resolveLayoutGrids(node).ifEmpty { root.layoutGrids },
            guides = node.guides.ifEmpty { root.guides },
            sourceMap = node.sourceMap ?: root.sourceMap,
            detached = instance.detach,
        )
    }

    private fun instancePlaceholder(node: DesignNode, resolvedId: String, scope: Scope): ResolvedNode =
        ResolvedNode(
            id = resolvedId,
            sourceId = node.id,
            selectableId = scope.selectableRootId ?: node.id,
            type = node.type,
            name = node.name,
            opacity = resolveDouble(node.opacity, scope, 1.0),
            position = node.position,
            constraints = node.constraints,
            size = node.size,
            sizing = node.sizing ?: DesignSizing(),
            layoutChild = node.layoutChild,
            gridPlacement = node.gridPlacement,
            fills = listOf(ResolvedPaint.Unknown("missingComponent")),
            role = node.role,
            blendMode = node.blendMode,
            interactions = node.interactions.map { resolveInteraction(it, scope) },
            sourceMap = node.sourceMap,
        )

    private fun mergedOverrideFor(node: DesignNode, scope: Scope): InstanceOverride? {
        val matches = scope.overrides.filter { it.target.size == 1 && it.target.first() == node.id }
        if (matches.isEmpty()) return null
        return matches.reduce { merged, next ->
            InstanceOverride(
                target = merged.target,
                fills = next.fills ?: merged.fills,
                strokes = next.strokes ?: merged.strokes,
                opacity = next.opacity ?: merged.opacity,
                visible = next.visible ?: merged.visible,
                characters = next.characters ?: merged.characters,
                textStyle = merged.textStyle?.mergedWith(next.textStyle) ?: next.textStyle,
                cornerRadius = next.cornerRadius ?: merged.cornerRadius,
                variant = next.variant ?: merged.variant,
                props = next.props ?: merged.props,
                slotContent = next.slotContent ?: merged.slotContent,
            )
        }
    }

    // --- Lowering (step 9) ------------------------------------------------------

    /**
     * A table lowers to a grid: row frames flatten away, their cells get explicit
     * gridPlacement (1-based row/column, authored spans preserved). Empty table
     * columns become one equal flex track per widest row.
     */
    private fun lowerTable(
        node: DesignNode,
        table: DesignTable,
        layout: ResolvedAutoLayout,
        childScope: Scope,
        scope: Scope,
    ): Pair<ResolvedAutoLayout, List<ResolvedNode>> {
        val rows = node.children.flatMap { resolveNodes(it, childScope) }
        var columnCount = 0
        val cells = rows.flatMapIndexed { rowIndex, row ->
            var column = 1
            val rowCells = row.children.map { cell ->
                val columnSpan = (cell.gridPlacement?.columnSpan ?: 1).coerceAtLeast(1)
                val rowSpan = (cell.gridPlacement?.rowSpan ?: 1).coerceAtLeast(1)
                val placed = cell.copy(
                    gridPlacement = GridPlacement(
                        column = column,
                        row = rowIndex + 1,
                        columnSpan = columnSpan,
                        rowSpan = rowSpan,
                    ),
                )
                column += columnSpan
                placed
            }
            columnCount = maxOf(columnCount, column - 1)
            rowCells
        }
        val lowered = layout.copy(
            mode = LayoutMode.Grid,
            columns = table.columns.ifEmpty { List(columnCount.coerceAtLeast(1)) { GridTrack.Flex(1.0) } },
            rows = emptyList(),
            columnGap = resolveDouble(table.columnGap, scope, 0.0),
            rowGap = resolveDouble(table.rowGap, scope, 0.0),
        )
        return lowered to cells
    }

    /** Media lowers to an image/video placeholder fill plus a carried [ResolvedMedia]. */
    private fun resolveMedia(media: DesignMedia, scope: Scope): ResolvedMedia {
        val asset = document.assets[media.assetId]
        return ResolvedMedia(
            assetId = media.assetId,
            url = asset?.url.orEmpty(),
            kind = media.kind,
            fillMode = media.fillMode,
            focalPoint = media.focalPoint,
            altText = media.alt?.let { resolveTextContent(it, scope) }.orEmpty(),
            posterAssetId = media.posterAssetId,
            autoplay = media.autoplay,
            loop = media.loop,
            muted = media.muted,
            intrinsicWidth = asset?.width,
            intrinsicHeight = asset?.height,
        )
    }

    /** Video draws like the image placeholder until video rendering lands. */
    private fun mediaFill(media: DesignMedia, resolved: ResolvedMedia, scope: Scope): ResolvedPaint =
        ResolvedPaint.Image(
            assetId = resolved.assetId,
            url = resolved.url,
            scaleMode = resolved.fillMode,
            opacity = resolveDouble(media.opacity, scope, 1.0),
        )

    /** The legacy `isMask` flag normalizes to an alpha mask over following siblings. */
    private fun resolveMask(node: DesignNode, scope: Scope): ResolvedMask? {
        val mask = node.mask ?: DesignMask().takeIf { node.isMask } ?: return null
        return ResolvedMask(
            type = mask.type,
            appliesTo = mask.appliesTo.map { scope.idPrefix + it },
        )
    }

    private fun resolveLayoutGrids(node: DesignNode): List<LayoutGridDefinition> =
        node.layoutGrids.ifEmpty {
            (document.styles[node.gridStyleId] as? DesignStyle.Grid)?.value ?: emptyList()
        }

    /**
     * Logical anchors -> the physical position + constraints the layout engine already
     * understands for absolute children (inlineEnd in LTR pins to the right edge, in
     * RTL to the left). Offsets from an end edge need the parent's (and the child's)
     * authored size; when missing, the constraint is still set but the authored
     * position is kept, with a warning.
     */
    private fun applyAnchors(
        child: ResolvedNode,
        anchors: DesignAnchors,
        parent: DesignNode,
        scope: Scope,
    ): ResolvedNode {
        val rtl = direction == LayoutDirection.Rtl
        val start = anchors.inlineStart?.let { resolveDouble(it, scope, 0.0) }
        val end = anchors.inlineEnd?.let { resolveDouble(it, scope, 0.0) }
        val left = if (rtl) end else start
        val right = if (rtl) start else end
        val top = anchors.blockStart?.let { resolveDouble(it, scope, 0.0) }
        val bottom = anchors.blockEnd?.let { resolveDouble(it, scope, 0.0) }

        var x = child.position?.x ?: 0.0
        var y = child.position?.y ?: 0.0
        var width = child.size.width
        var height = child.size.height
        var sizing = child.sizing
        var horizontal = child.constraints.horizontal
        var vertical = child.constraints.vertical

        val parentWidth = parent.size.width
        when {
            left != null && right != null -> {
                horizontal = HorizontalConstraint.LeftRight
                x = left
                if (parentWidth != null) {
                    width = (parentWidth - left - right).coerceAtLeast(0.0)
                    sizing = sizing.copy(horizontal = SizingMode.Fixed)
                } else {
                    warnAnchorNeedsSize(child, "inline")
                }
            }
            left != null -> {
                horizontal = HorizontalConstraint.Left
                x = left
            }
            right != null -> {
                horizontal = HorizontalConstraint.Right
                if (parentWidth != null && width != null) {
                    x = parentWidth - right - width
                } else {
                    warnAnchorNeedsSize(child, "inline")
                }
            }
        }
        val parentHeight = parent.size.height
        when {
            top != null && bottom != null -> {
                vertical = VerticalConstraint.TopBottom
                y = top
                if (parentHeight != null) {
                    height = (parentHeight - top - bottom).coerceAtLeast(0.0)
                    sizing = sizing.copy(vertical = SizingMode.Fixed)
                } else {
                    warnAnchorNeedsSize(child, "block")
                }
            }
            top != null -> {
                vertical = VerticalConstraint.Top
                y = top
            }
            bottom != null -> {
                vertical = VerticalConstraint.Bottom
                if (parentHeight != null && height != null) {
                    y = parentHeight - bottom - height
                } else {
                    warnAnchorNeedsSize(child, "block")
                }
            }
        }
        return child.copy(
            position = DesignPoint(x, y),
            constraints = DesignConstraints(horizontal, vertical),
            size = DesignSize(width, height),
            sizing = sizing,
            layoutChild = child.layoutChild.copy(absolute = true),
        )
    }

    private fun warnAnchorNeedsSize(child: ResolvedNode, axis: String) {
        warn(
            "Anchor on '${child.sourceId}' needs fixed parent/child sizes on the $axis axis; " +
                "keeping the authored position",
        )
    }

    // --- Interactions ------------------------------------------------------------

    /** Interactions carry through with bindable action values resolved to literals where possible. */
    private fun resolveInteraction(interaction: DesignInteraction, scope: Scope): ResolvedInteraction =
        ResolvedInteraction(
            trigger = interaction.trigger,
            key = interaction.key,
            delayMs = interaction.delayMs,
            variable = interaction.variable,
            actions = interaction.actions.map { resolveAction(it, scope) },
            sourceMap = interaction.sourceMap,
        )

    private fun resolveAction(action: DesignAction, scope: Scope): DesignAction =
        when (action) {
            is DesignAction.SetVariable -> action.copy(value = resolveToLiteral(action.value, scope))
            else -> action
        }

    /** Best-effort literal substitution; an unresolvable binding keeps its original form. */
    private fun resolveToLiteral(value: Bindable<String>, scope: Scope): Bindable<String> =
        when (value) {
            is Bindable.Value -> value
            is Bindable.VarRef -> when (val resolved = resolveVariable(value.id, scope)) {
                is String -> Bindable.Value(resolved)
                is Double -> Bindable.Value(formatNumber(resolved))
                is Boolean -> Bindable.Value(resolved.toString())
                else -> value
            }
            is Bindable.PropRef -> when (val prop = scope.props[value.name]) {
                is PropValue.Text -> Bindable.Value(prop.value)
                is PropValue.Number -> Bindable.Value(formatNumber(prop.value))
                is PropValue.Bool -> Bindable.Value(prop.value.toString())
                else -> value
            }
            is Bindable.DataRef -> ExpressionEvaluator.evaluate(value.expression, scope.data)
                ?.let(::scalarToString)
                ?.let { Bindable.Value(it) }
                ?: value
        }

    // --- Visual attributes --------------------------------------------------

    private fun resolveFills(node: DesignNode, override: InstanceOverride?, scope: Scope): List<ResolvedPaint> {
        val paints = override?.fills
            ?: node.fills
            ?: (document.styles[node.fillStyleId] as? DesignStyle.Paint)?.value
            ?: emptyList()
        return paints.mapNotNull { resolvePaint(it, scope) }
    }

    private fun resolveStrokes(node: DesignNode, override: InstanceOverride?, scope: Scope): ResolvedStrokes? {
        val strokes = override?.strokes
            ?: node.strokes
            ?: (document.styles[node.strokeStyleId] as? DesignStyle.Paint)?.value?.let { paints ->
                DesignStrokes(paints = paints)
            }
            ?: return null
        val perSide = strokes.weightPerSide
        return ResolvedStrokes(
            paints = strokes.paints.mapNotNull { resolvePaint(it, scope) },
            weight = resolveDouble(strokes.weight, scope, 1.0),
            weightTop = perSide?.top?.let { resolveDouble(it, scope, 0.0) },
            weightRight = perSide?.right?.let { resolveDouble(it, scope, 0.0) },
            weightBottom = perSide?.bottom?.let { resolveDouble(it, scope, 0.0) },
            weightLeft = perSide?.left?.let { resolveDouble(it, scope, 0.0) },
            align = strokes.align,
            dashPattern = strokes.dashPattern,
        )
    }

    private fun resolveEffects(node: DesignNode, scope: Scope): List<ResolvedEffect> {
        val effects = node.effects.ifEmpty {
            (document.styles[node.effectStyleId] as? DesignStyle.Effect)?.value ?: emptyList()
        }
        return effects.mapNotNull { resolveEffect(it, scope) }
    }

    private fun resolveEffect(effect: DesignEffect, scope: Scope): ResolvedEffect? {
        if (!resolveBoolean(effect.visible, scope, true)) return null
        return when (effect) {
            is DesignEffect.DropShadow -> ResolvedEffect.DropShadow(
                color = resolveColor(effect.color, scope),
                offset = effect.offset,
                blur = effect.blur,
                spread = effect.spread,
            )
            is DesignEffect.InnerShadow -> ResolvedEffect.InnerShadow(
                color = resolveColor(effect.color, scope),
                offset = effect.offset,
                blur = effect.blur,
                spread = effect.spread,
            )
            is DesignEffect.LayerBlur -> ResolvedEffect.LayerBlur(effect.radius)
            is DesignEffect.BackgroundBlur -> ResolvedEffect.BackgroundBlur(effect.radius)
            is DesignEffect.Unknown -> null
        }
    }

    private fun resolvePaint(paint: DesignPaint, scope: Scope): ResolvedPaint? {
        if (!resolveBoolean(paint.visible, scope, true)) return null
        val opacity = resolveDouble(paint.opacity, scope, 1.0)
        return when (paint) {
            is DesignPaint.Solid -> ResolvedPaint.Solid(resolveColor(paint.color, scope), opacity)
            is DesignPaint.Gradient -> ResolvedPaint.Gradient(
                gradientType = paint.gradientType,
                from = paint.from,
                to = paint.to,
                stops = paint.stops.map { stop ->
                    ResolvedGradientStop(stop.position, resolveColor(stop.color, scope))
                },
                opacity = opacity,
            )
            is DesignPaint.Image -> ResolvedPaint.Image(
                assetId = paint.assetId,
                url = document.assets[paint.assetId]?.url.orEmpty(),
                scaleMode = paint.scaleMode,
                opacity = opacity,
            )
            // Until video rendering lands, a video paint draws like the image placeholder.
            is DesignPaint.Video -> ResolvedPaint.Image(
                assetId = paint.assetId,
                url = document.assets[paint.assetId]?.url.orEmpty(),
                scaleMode = paint.scaleMode,
                opacity = opacity,
            )
            is DesignPaint.Unknown -> ResolvedPaint.Unknown(paint.rawType, opacity)
        }
    }

    private fun resolveCornerRadius(radius: DesignCornerRadius?, scope: Scope): ResolvedCornerRadius {
        if (radius == null) return ResolvedCornerRadius()
        return ResolvedCornerRadius(
            topLeft = resolveDouble(radius.topLeft, scope, 0.0),
            topRight = resolveDouble(radius.topRight, scope, 0.0),
            bottomRight = resolveDouble(radius.bottomRight, scope, 0.0),
            bottomLeft = resolveDouble(radius.bottomLeft, scope, 0.0),
            smoothing = radius.smoothing,
        )
    }

    private fun resolveText(
        text: DesignNodeKind.Text,
        override: InstanceOverride?,
        scope: Scope,
    ): ResolvedText {
        val sharedStyle = (document.styles[text.textStyleId] as? DesignStyle.Text)?.value ?: DesignTextStyle()
        val baseStyle = sharedStyle.mergedWith(text.textStyle).mergedWith(override?.textStyle)
        val resolvedBase = resolveTextStyle(baseStyle, scope)
        val rawCharacters = {
            override?.characters?.let { resolveString(it, scope, "") }
                ?: resolveString(text.characters, scope, "")
        }
        return ResolvedText(
            characters = text.content?.let { resolveTextContent(it, scope, rawCharacters) }
                ?: rawCharacters(),
            style = resolvedBase,
            autoResize = text.autoResize,
            truncate = text.truncate,
            ranges = text.styleRanges.map { range ->
                ResolvedTextRange(
                    start = range.start,
                    end = range.end,
                    style = resolveTextStyle(baseStyle.mergedWith(range.style), scope),
                    fills = range.fills?.mapNotNull { resolvePaint(it, scope) },
                )
            },
            list = text.list,
            contentKey = text.content?.key.orEmpty(),
        )
    }

    /**
     * i18n text resolution (fallback chain, each fallback step warns when a key lookup
     * was intended): merged `resources[activeLocale][key]` -> `defaultText` ->
     * [rawCharacters]. The winning message is ICU-formatted with the content params.
     */
    private fun resolveTextContent(
        content: TextContent,
        scope: Scope,
        rawCharacters: (() -> String)? = null,
    ): String {
        val message = mergedResources[activeLocale]?.get(content.key)
        val template = when {
            message != null -> message
            content.defaultText.isNotEmpty() -> {
                if (content.key.isNotEmpty()) {
                    warn("Missing i18n resource '${content.key}' for locale '$activeLocale'; using defaultText")
                }
                content.defaultText
            }
            else -> {
                if (content.key.isNotEmpty()) {
                    warn(
                        "Missing i18n resource '${content.key}' for locale '$activeLocale' " +
                            "and no defaultText; using characters",
                    )
                }
                rawCharacters?.invoke().orEmpty()
            }
        }
        val params = content.params.mapNotNull { (name, value) ->
            if (value is Bindable.DataRef) {
                // A failed binding keeps the ICU {param} placeholder verbatim.
                evaluateBinding(value.expression, scope)
                    ?.let { evaluated ->
                        scalarToString(evaluated)
                            ?: bindingMismatch(value.expression, "scalar", null)
                    }
                    ?.let { name to it }
            } else {
                name to resolveString(value, scope, "")
            }
        }.toMap()
        return IcuLiteFormatter.format(template, params, activeLocale) { failure -> warn(failure) }
    }

    private fun resolveTextStyle(style: DesignTextStyle, scope: Scope): ResolvedTextStyle {
        val fontSize = style.fontSize?.let { resolveDouble(it, scope, 14.0) } ?: 14.0
        return ResolvedTextStyle(
            fontFamily = style.fontFamily.orEmpty(),
            fontWeight = (style.fontWeight?.let { resolveDouble(it, scope, 400.0) } ?: 400.0).toInt(),
            fontSize = fontSize,
            lineHeight = style.lineHeight.resolveAgainst(fontSize),
            letterSpacing = style.letterSpacing.resolveAgainst(fontSize),
            paragraphSpacing = style.paragraphSpacing ?: 0.0,
            textAlignHorizontal = style.textAlignHorizontal ?: TextAlignHorizontal.Left,
            textAlignVertical = style.textAlignVertical ?: TextAlignVertical.Top,
            textCase = style.textCase ?: TextCase.None,
            textDecoration = style.textDecoration ?: TextDecorationKind.None,
        )
    }

    private fun UnitValue?.resolveAgainst(fontSize: Double): Double =
        when (this?.unit) {
            null -> 0.0
            DesignUnit.Px -> value
            DesignUnit.Percent -> fontSize * value / 100.0
        }

    /**
     * Logical padding wins over physical per side, mapped by [direction]
     * (LTR: inlineStart -> left; RTL: inlineStart -> right). RTL horizontal rows also
     * flip JustifyContent Start/End; their children are reversed in [resolveChildren].
     */
    private fun resolveAutoLayout(layout: DesignAutoLayout, scope: Scope): ResolvedAutoLayout {
        val fixedGap = (layout.gap as? DesignGap.Fixed)?.let { resolveDouble(it.value, scope, 0.0) } ?: 0.0
        val rtl = direction == LayoutDirection.Rtl
        val logical = layout.paddingLogical
        val inlineStart = logical?.inlineStart?.let { resolveDouble(it, scope, 0.0) }
        val inlineEnd = logical?.inlineEnd?.let { resolveDouble(it, scope, 0.0) }
        val justifyContent = if (rtl && layout.mode == LayoutMode.Horizontal) {
            when (layout.justifyContent) {
                JustifyContent.Start -> JustifyContent.End
                JustifyContent.End -> JustifyContent.Start
                else -> layout.justifyContent
            }
        } else {
            layout.justifyContent
        }
        return ResolvedAutoLayout(
            mode = layout.mode,
            gap = fixedGap,
            gapAuto = layout.gap is DesignGap.Auto,
            crossGap = layout.crossGap?.let { resolveDouble(it, scope, 0.0) } ?: fixedGap,
            wrap = layout.wrap,
            paddingTop = logical?.blockStart?.let { resolveDouble(it, scope, 0.0) }
                ?: resolveDouble(layout.padding.top, scope, 0.0),
            paddingRight = (if (rtl) inlineStart else inlineEnd)
                ?: resolveDouble(layout.padding.right, scope, 0.0),
            paddingBottom = logical?.blockEnd?.let { resolveDouble(it, scope, 0.0) }
                ?: resolveDouble(layout.padding.bottom, scope, 0.0),
            paddingLeft = (if (rtl) inlineEnd else inlineStart)
                ?: resolveDouble(layout.padding.left, scope, 0.0),
            alignItems = layout.alignItems,
            justifyContent = justifyContent,
            baseline = layout.baseline,
            clipsContent = layout.clipsContent,
            columns = layout.columns,
            rows = layout.rows,
            columnGap = layout.columnGap?.let { resolveDouble(it, scope, 0.0) } ?: fixedGap,
            rowGap = layout.rowGap?.let { resolveDouble(it, scope, 0.0) } ?: fixedGap,
            implicitRows = layout.implicitRows,
            implicitRowMin = layout.implicitRowMin,
        )
    }

    // --- Scalar binding resolution -------------------------------------------

    private fun resolveDouble(bindable: Bindable<Double>, scope: Scope, fallback: Double): Double =
        when (bindable) {
            is Bindable.Value -> bindable.value
            is Bindable.VarRef -> (resolveVariable(bindable.id, scope) as? Double) ?: run {
                warn("Variable '${bindable.id}' did not resolve to a number")
                fallback
            }
            is Bindable.PropRef -> when (val prop = scope.props[bindable.name]) {
                is PropValue.Number -> prop.value
                is PropValue.Data -> when (val value = evaluateBinding(prop.expression, scope)) {
                    is DataValue.Num -> value.value
                    null -> fallback
                    else -> bindingMismatch(prop.expression, "number", fallback)
                }
                else -> {
                    warn("Prop '${bindable.name}' did not resolve to a number")
                    fallback
                }
            }
            is Bindable.DataRef -> when (val value = evaluateBinding(bindable.expression, scope)) {
                is DataValue.Num -> value.value
                null -> fallback
                else -> bindingMismatch(bindable.expression, "number", fallback)
            }
        }

    private fun resolveString(bindable: Bindable<String>, scope: Scope, fallback: String): String =
        when (bindable) {
            is Bindable.Value -> bindable.value
            is Bindable.VarRef -> (resolveVariable(bindable.id, scope) as? String) ?: run {
                warn("Variable '${bindable.id}' did not resolve to a string")
                fallback
            }
            is Bindable.PropRef -> when (val prop = scope.props[bindable.name]) {
                is PropValue.Text -> prop.value
                is PropValue.Number -> formatNumber(prop.value)
                is PropValue.Bool -> prop.value.toString()
                is PropValue.Content -> resolveTextContent(prop.content, scope)
                is PropValue.Data -> when (val value = evaluateBinding(prop.expression, scope)) {
                    is DataValue.Str -> value.value
                    is DataValue.Num -> formatNumber(value.value)
                    is DataValue.Bool -> value.value.toString()
                    null -> fallback
                    else -> bindingMismatch(prop.expression, "string", fallback)
                }
                is PropValue.SlotContent -> fallback
                else -> {
                    warn("Prop '${bindable.name}' did not resolve to a string")
                    fallback
                }
            }
            is Bindable.DataRef -> when (val value = evaluateBinding(bindable.expression, scope)) {
                is DataValue.Str -> value.value
                null -> fallback
                else -> bindingMismatch(bindable.expression, "string", fallback)
            }
        }

    private fun resolveBoolean(bindable: Bindable<Boolean>, scope: Scope, fallback: Boolean): Boolean =
        when (bindable) {
            is Bindable.Value -> bindable.value
            is Bindable.VarRef -> (resolveVariable(bindable.id, scope) as? Boolean) ?: run {
                warn("Variable '${bindable.id}' did not resolve to a boolean")
                fallback
            }
            is Bindable.PropRef -> when (val prop = scope.props[bindable.name]) {
                is PropValue.Bool -> prop.value
                is PropValue.Data -> when (val value = evaluateBinding(prop.expression, scope)) {
                    is DataValue.Bool -> value.value
                    null -> fallback
                    else -> bindingMismatch(prop.expression, "boolean", fallback)
                }
                else -> {
                    warn("Prop '${bindable.name}' did not resolve to a boolean")
                    fallback
                }
            }
            is Bindable.DataRef -> when (val value = evaluateBinding(bindable.expression, scope)) {
                is DataValue.Bool -> value.value
                null -> fallback
                else -> bindingMismatch(bindable.expression, "boolean", fallback)
            }
        }

    private fun resolveColor(bindable: Bindable<DesignColor>, scope: Scope): DesignColor =
        when (bindable) {
            is Bindable.Value -> bindable.value
            is Bindable.VarRef -> (resolveVariable(bindable.id, scope) as? DesignColor) ?: run {
                warn("Variable '${bindable.id}' did not resolve to a color")
                DesignColor.Black
            }
            is Bindable.PropRef -> {
                warn("Prop '${bindable.name}' cannot provide a color")
                DesignColor.Black
            }
            is Bindable.DataRef -> {
                if (evaluateBinding(bindable.expression, scope) != null) {
                    bindingMismatch(bindable.expression, "color", Unit)
                }
                DesignColor.Black
            }
        }

    /** Evaluates a `{{...}}` binding, reporting parse/eval failures as warnings. */
    private fun evaluateBinding(expression: DesignExpression, scope: Scope): DataValue? =
        ExpressionEvaluator.evaluate(expression, scope.data) { failure ->
            warn("Data binding '{{${expression.raw}}}' failed to evaluate: $failure")
        }

    private fun <T> bindingMismatch(expression: DesignExpression, expected: String, fallback: T): T {
        warn("Data binding '{{${expression.raw}}}' did not resolve to a $expected; using fallback")
        return fallback
    }

    private fun scalarToString(value: DataValue): String? =
        when (value) {
            is DataValue.Str -> value.value
            is DataValue.Num -> formatNumber(value.value)
            is DataValue.Bool -> value.value.toString()
            else -> null
        }

    /** Resolves a variable to a concrete scalar, following alias chains cycle-safely. */
    private fun resolveVariable(varId: String, scope: Scope, visited: Set<String> = emptySet()): Any? {
        if (varId in visited) {
            warn("Variable alias cycle detected at '$varId'")
            return null
        }
        val found = document.variables.findVariable(varId) ?: run {
            warn("Unknown variable '$varId'")
            return null
        }
        val (collectionId, variable) = found
        val collection = document.variables.collections.getValue(collectionId)
        val mode = scope.modes[collectionId] ?: collection.defaultMode
        val value = variable.values[mode]
            ?: variable.values[collection.defaultMode]
            ?: variable.values.values.firstOrNull()
            ?: return null
        return when (value) {
            is VariableValue.Alias -> resolveVariable(value.varId, scope, visited + varId)
            is VariableValue.ColorValue -> value.value
            is VariableValue.NumberValue -> value.value
            is VariableValue.TextValue -> value.value
            is VariableValue.BoolValue -> value.value
        }
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun warn(message: String) {
        collectedDiagnostics += DesignDiagnostic(DesignSeverity.Warning, message)
    }
}
