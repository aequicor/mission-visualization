package io.aequicor.visualization.engine.frontend.normalize

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
import io.aequicor.visualization.engine.frontend.blocks.NodePositionMode
import io.aequicor.visualization.engine.frontend.blocks.OverridesPatch
import io.aequicor.visualization.engine.frontend.blocks.SetOverridePatch
import io.aequicor.visualization.engine.frontend.blocks.PropsPatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsivePatch
import io.aequicor.visualization.engine.frontend.blocks.ResponsiveVariantPatch
import io.aequicor.visualization.engine.frontend.blocks.ShapePatch
import io.aequicor.visualization.engine.frontend.blocks.StylePatch
import io.aequicor.visualization.engine.frontend.blocks.StylesPatch
import io.aequicor.visualization.engine.frontend.blocks.TextPatch
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.blocks.VariablesPatch
import io.aequicor.visualization.engine.frontend.blocks.VectorPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.DesignAnchors
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignInteraction
import io.aequicor.visualization.engine.ir.model.DesignLogicalInsets
import io.aequicor.visualization.engine.ir.model.DesignMask
import io.aequicor.visualization.engine.ir.model.DesignMedia
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignNodePatch
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.orZero
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignTable
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.ResponsiveVariant
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextStyleRange
import io.aequicor.visualization.engine.ir.model.bindable

/** One patch with its provenance, used for conflict warnings and source maps. */
data class AppliedPatch(
    val patch: TypedPatch,
    val blockKey: String,
    val line: Int,
)

/**
 * Applies typed patches to a materialized [DesignNode] with the SLM precedence:
 * semantic patches first, explicit ones after (explicit wins because it only
 * overwrites with non-null fields). An explicit field replacing a DIFFERENT
 * non-null semantic value produces a conflict warning; duplicate same-kind
 * explicit blocks accumulate for `interaction`/`action` and last-win with a
 * warning otherwise.
 */
class PatchMerger(
    private val diagnostics: DiagnosticCollector,
    private val sourceLocale: String,
    private val fileName: String = "",
) {
    fun apply(
        node: DesignNode,
        semantic: List<AppliedPatch>,
        explicit: List<AppliedPatch>,
    ): DesignNode {
        val effectiveExplicit = dedupeExplicit(explicit)
        warnConflicts(semantic, effectiveExplicit)
        var current = node
        (semantic + effectiveExplicit).forEach { applied ->
            current = applyPatch(current, applied)
        }
        return current
    }

    private fun dedupeExplicit(explicit: List<AppliedPatch>): List<AppliedPatch> {
        val result = mutableListOf<AppliedPatch>()
        val lastOfKind = mutableMapOf<String, Int>()
        explicit.forEach { applied ->
            val accumulates = applied.patch is InteractionPatch || applied.patch is ActionPatch
            if (!accumulates) {
                val previous = lastOfKind[applied.blockKey]
                if (previous != null) {
                    diagnostics.warning(
                        "Duplicate `${applied.blockKey}` block; the last one wins",
                        applied.line,
                        blockPath = applied.blockKey,
                    )
                    result.removeAt(previous)
                    lastOfKind.keys.forEach { key ->
                        val index = lastOfKind.getValue(key)
                        if (index > previous) lastOfKind[key] = index - 1
                    }
                }
                lastOfKind[applied.blockKey] = result.size
            }
            result += applied
        }
        return result
    }

    private fun warnConflicts(semantic: List<AppliedPatch>, explicit: List<AppliedPatch>) {
        if (semantic.isEmpty() || explicit.isEmpty()) return
        explicit.forEach { exp ->
            semantic
                .filter { it.patch::class == exp.patch::class }
                .forEach { sem ->
                    val semFields = patchFields(sem.patch)
                    val expFields = patchFields(exp.patch)
                    semFields.forEach { (name, semValue) ->
                        val expValue = expFields[name]
                        if (semValue != null && expValue != null && semValue != expValue) {
                            diagnostics.warning(
                                "Explicit ${exp.blockKey}.$name overrides semantic extraction " +
                                    "($semValue -> $expValue) at line ${exp.line}",
                                exp.line,
                                blockPath = exp.blockKey,
                            )
                        }
                    }
                }
        }
    }

    // --- application ---

    fun applyPatch(node: DesignNode, applied: AppliedPatch): DesignNode {
        val location = SourceLocation(file = fileName, line = applied.line)
        return when (val patch = applied.patch) {
            is NodePatch -> applyNode(node, patch)
            is LayoutPatch -> applyLayout(node, patch)
            is StylePatch -> applyStyle(node, patch)
            is TextPatch -> applyText(node, patch)
            is ComponentPatch -> applyComponent(node, patch)
            is PropsPatch -> applyProps(node, patch)
            is OverridesPatch -> applyOverrides(node, patch)
            is MediaPatch -> applyMedia(node, patch)
            is ShapePatch -> applyShape(node, patch)
            is VectorPatch -> applyVector(node, patch, applied.line)
            is MaskPatch -> applyMask(node, patch, applied.line)
            is ActionPatch -> node.copy(
                interactions = node.interactions + DesignInteraction(
                    trigger = InteractionTrigger.OnClick,
                    actions = listOf(patch.action),
                    sourceMap = location,
                ),
            )
            is InteractionPatch -> node.copy(
                interactions = node.interactions + DesignInteraction(
                    trigger = patch.trigger ?: InteractionTrigger.OnClick,
                    key = patch.key.orEmpty(),
                    delayMs = patch.delayMs,
                    variable = patch.variable.orEmpty(),
                    actions = patch.actions,
                    sourceMap = location,
                ),
            )
            is MotionPatch -> node.copy(motion = patch.motion)
            is ResponsivePatch -> node.copy(
                responsive = node.responsive + patch.variants.map { variant ->
                    ResponsiveVariant(
                        selectors = variant.selectors,
                        patch = toNodePatch(variant),
                        sourceMap = location,
                    )
                },
            )
            is ExportPatch -> node.copy(
                exportSettings = if (patch.enabled == false) emptyList() else patch.settings,
            )
            // Document-level patches are lifted by the normalizer, not applied to nodes.
            is VariablesPatch, is StylesPatch, is HandoffPatch -> node
        }
    }

    private fun applyNode(node: DesignNode, patch: NodePatch): DesignNode {
        val position = if (patch.x != null || patch.y != null) {
            DesignPoint(
                x = patch.x ?: node.position?.x?.orZero ?: 0.0,
                y = patch.y ?: node.position?.y?.orZero ?: 0.0,
            )
        } else {
            node.position
        }
        return node.copy(
            type = patch.type ?: node.type,
            kind = patch.type?.let { kindForType(it, node.kind) } ?: node.kind,
            name = patch.name ?: node.name,
            role = patch.role ?: node.role,
            visible = patch.visible ?: node.visible,
            locked = patch.locked ?: node.locked,
            order = patch.order ?: node.order,
            variableModes = patch.variableModes?.let { node.variableModes + it } ?: node.variableModes,
            rotation = patch.rotation ?: node.rotation,
            position = position,
            constraints = DesignConstraints(
                horizontal = patch.constraintsHorizontal ?: node.constraints.horizontal,
                vertical = patch.constraintsVertical ?: node.constraints.vertical,
            ),
            layoutChild = when (patch.positionMode) {
                NodePositionMode.Absolute -> node.layoutChild.copy(absolute = true)
                NodePositionMode.Auto -> node.layoutChild.copy(absolute = false)
                null -> node.layoutChild
            },
        )
    }

    private fun applyLayout(node: DesignNode, patch: LayoutPatch): DesignNode {
        var layout = node.layout
        patch.mode?.let { layout = layout.copy(mode = it) }
        val mode = layout.mode

        val hasPadding = patch.paddingBlockStart != null || patch.paddingInlineEnd != null ||
            patch.paddingBlockEnd != null || patch.paddingInlineStart != null
        if (hasPadding) {
            val existing = layout.paddingLogical ?: DesignLogicalInsets()
            layout = layout.copy(
                paddingLogical = DesignLogicalInsets(
                    blockStart = patch.paddingBlockStart ?: existing.blockStart,
                    inlineEnd = patch.paddingInlineEnd ?: existing.inlineEnd,
                    blockEnd = patch.paddingBlockEnd ?: existing.blockEnd,
                    inlineStart = patch.paddingInlineStart ?: existing.inlineStart,
                ),
            )
        }

        patch.gap?.let { layout = layout.copy(gap = it) }
        patch.rowGap?.let { layout = layout.copy(rowGap = it) }
        patch.columnGap?.let { layout = layout.copy(columnGap = it) }
        if (patch.gap == null) {
            // Row/column gaps double as the main/cross axis gap of an auto layout.
            when (mode) {
                LayoutMode.Vertical -> {
                    patch.rowGap?.let { layout = layout.copy(gap = DesignGap.Fixed(it)) }
                    patch.columnGap?.let { layout = layout.copy(crossGap = it) }
                }
                LayoutMode.Horizontal -> {
                    patch.columnGap?.let { layout = layout.copy(gap = DesignGap.Fixed(it)) }
                    patch.rowGap?.let { layout = layout.copy(crossGap = it) }
                }
                else -> {}
            }
        }

        alignItemsOf(mode, patch)?.let { layout = layout.copy(alignItems = it) }
        patch.baseline?.let { layout = layout.copy(baseline = it) }
        patch.distribution?.let { layout = layout.copy(justifyContent = it) }
        patch.wrap?.let { layout = layout.copy(wrap = it) }
        patch.clipContent?.let { layout = layout.copy(clipsContent = it) }
        patch.gridColumns?.let { layout = layout.copy(columns = it) }
        patch.gridRows?.let { layout = layout.copy(rows = it) }
        patch.implicitRows?.let { layout = layout.copy(implicitRows = it) }
        patch.implicitRowMin?.let { layout = layout.copy(implicitRowMin = it) }

        val sizing = if (patch.sizingWidth?.mode != null || patch.sizingHeight?.mode != null) {
            val existing = node.sizing ?: DesignSizing()
            DesignSizing(
                horizontal = patch.sizingWidth?.mode ?: existing.horizontal,
                vertical = patch.sizingHeight?.mode ?: existing.vertical,
            )
        } else {
            node.sizing
        }
        val size = mergeSize(node.size, patch.sizingWidth?.value, patch.sizingHeight?.value)
        val minSize = mergeSize(node.minSize, patch.sizingWidth?.min, patch.sizingHeight?.min)
        val maxSize = mergeSize(node.maxSize, patch.sizingWidth?.max, patch.sizingHeight?.max)

        var scroll = node.scroll
        patch.overflowX?.let { scroll = scroll.copy(overflowX = it) }
        patch.overflowY?.let { scroll = scroll.copy(overflowY = it) }
        patch.scrollDirection?.let { scroll = scroll.copy(overflow = it) }
        patch.scrollSticky?.let { scroll = scroll.copy(sticky = it) }
        patch.scrollFixedChildren?.let { scroll = scroll.copy(fixedChildren = it) }

        val absolute = patch.ignoreAutoLayout == true ||
            patch.positionMode == NodePositionMode.Absolute
        val layoutChild = when {
            absolute -> node.layoutChild.copy(absolute = true)
            patch.positionMode == NodePositionMode.Auto -> node.layoutChild.copy(absolute = false)
            else -> node.layoutChild
        }

        val hasAnchors = patch.anchorInlineStart != null || patch.anchorInlineEnd != null ||
            patch.anchorBlockStart != null || patch.anchorBlockEnd != null
        val anchors = if (hasAnchors) {
            val existing = node.anchors ?: DesignAnchors()
            DesignAnchors(
                inlineStart = patch.anchorInlineStart ?: existing.inlineStart,
                inlineEnd = patch.anchorInlineEnd ?: existing.inlineEnd,
                blockStart = patch.anchorBlockStart ?: existing.blockStart,
                blockEnd = patch.anchorBlockEnd ?: existing.blockEnd,
            )
        } else {
            node.anchors
        }
        val position = if (patch.positionX != null || patch.positionY != null) {
            DesignPoint(
                x = patch.positionX ?: node.position?.x?.orZero ?: 0.0,
                y = patch.positionY ?: node.position?.y?.orZero ?: 0.0,
            )
        } else {
            node.position
        }

        return node.copy(
            layout = layout,
            sizing = sizing,
            size = size ?: DesignSize(),
            minSize = minSize,
            maxSize = maxSize,
            scroll = scroll,
            layoutChild = layoutChild,
            anchors = anchors,
            position = position,
            gridPlacement = patch.placement ?: node.gridPlacement,
            guides = patch.guides ?: node.guides,
            layoutGrids = patch.grids ?: node.layoutGrids,
        )
    }

    /** `align.inline`/`align.block` map onto the cross-axis [AlignItems] slot. */
    private fun alignItemsOf(mode: LayoutMode, patch: LayoutPatch): AlignItems? = when (mode) {
        LayoutMode.Vertical -> patch.alignInline ?: patch.alignBlock
        LayoutMode.Horizontal -> patch.alignBlock ?: patch.alignInline
        else -> patch.alignInline ?: patch.alignBlock
    }

    private fun mergeSize(existing: DesignSize?, width: Double?, height: Double?): DesignSize? {
        if (width == null && height == null) return existing
        return DesignSize(
            width = width ?: existing?.width,
            height = height ?: existing?.height,
        )
    }

    private fun applyStyle(node: DesignNode, patch: StylePatch): DesignNode {
        val kind = node.kind
        val patchedKind = if (patch.textStyle != null && kind is DesignNodeKind.Text) {
            kind.copy(textStyleId = patch.textStyle)
        } else {
            kind
        }
        return node.copy(
            kind = patchedKind,
            opacity = patch.opacity ?: node.opacity,
            blendMode = patch.blendMode ?: node.blendMode,
            cornerRadius = patch.radius ?: node.cornerRadius,
            fills = patch.fills ?: node.fills,
            strokes = patch.strokes ?: node.strokes,
            effects = patch.effects ?: node.effects,
            fillStyleId = patch.fillStyle ?: node.fillStyleId,
            strokeStyleId = patch.strokeStyle ?: node.strokeStyleId,
            effectStyleId = patch.effectStyle ?: node.effectStyleId,
            gridStyleId = patch.gridStyle ?: node.gridStyleId,
        )
    }

    private fun applyText(node: DesignNode, patch: TextPatch): DesignNode {
        val kind = node.kind as? DesignNodeKind.Text ?: DesignNodeKind.Text()
        val existing = kind.content
        val content = if (patch.key != null || patch.defaultText != null || existing != null) {
            TextContent(
                key = patch.key ?: existing?.key.orEmpty(),
                defaultLocale = sourceLocale,
                defaultText = patch.defaultText ?: existing?.defaultText.orEmpty(),
                params = existing?.params ?: emptyMap(),
            )
        } else {
            null
        }
        val autoResize = if (patch.resizingWidth != null || patch.resizingHeight != null) {
            when {
                patch.resizingWidth == SizingMode.Hug -> TextAutoResize.WidthAndHeight
                patch.resizingHeight == SizingMode.Hug -> TextAutoResize.Height
                else -> TextAutoResize.None
            }
        } else {
            kind.autoResize
        }
        val styleRanges = patch.spans?.filter { it.styleRef != null }?.map { span ->
            // IR style ranges carry inline styles; shared-ref resolution is a later stage.
            TextStyleRange(start = span.start, end = span.end, style = DesignTextStyle(), styleRef = span.styleRef.orEmpty())
        }
        val links = patch.spans
            ?.filter { it.linkUrl != null || it.linkNodeTarget != null }
            ?.map { span ->
                TextLink(
                    start = span.start,
                    end = span.end,
                    url = span.linkUrl.orEmpty(),
                    nodeTarget = span.linkNodeTarget.orEmpty(),
                )
            }
        val sizing = if (patch.resizingWidth != null || patch.resizingHeight != null) {
            val current = node.sizing ?: DesignSizing()
            DesignSizing(
                horizontal = patch.resizingWidth ?: current.horizontal,
                vertical = patch.resizingHeight ?: current.vertical,
            )
        } else {
            node.sizing
        }
        return node.copy(
            type = if (node.kind is DesignNodeKind.Text) node.type else "text",
            kind = kind.copy(
                characters = patch.characters ?: kind.characters,
                content = content,
                textStyleId = patch.styleRef ?: kind.textStyleId,
                textStyle = patch.typography
                    ?.let { (kind.textStyle ?: DesignTextStyle()).mergedWith(it) }
                    ?: kind.textStyle,
                autoResize = autoResize,
                truncate = patch.truncate ?: kind.truncate,
                styleRanges = styleRanges ?: kind.styleRanges,
                links = links ?: kind.links,
                list = patch.list ?: kind.list,
            ),
            sizing = sizing,
        )
    }

    private fun applyComponent(node: DesignNode, patch: ComponentPatch): DesignNode {
        // Definition-side fields (name/set/variants/properties) are consumed by
        // ComponentLifter. `variant` is dual-use: on an already-instance node it is
        // the instance selection; on anything else (a component definition) it marks
        // the definition's own axis values and must not instantiate the node.
        val instanceSide = patch.ref != null || patch.libraryRef != null ||
            patch.props != null || patch.detach != null || patch.resetOverrides != null
        if (!instanceSide && (patch.variant == null || node.kind !is DesignNodeKind.Instance)) {
            return node
        }
        val kind = node.kind as? DesignNodeKind.Instance
            ?: DesignNodeKind.Instance(componentId = "".bindable())
        return node.copy(
            type = if (node.kind is DesignNodeKind.Instance) node.type else "instance",
            kind = kind.copy(
                componentId = patch.ref?.bindable() ?: kind.componentId,
                libraryRef = patch.libraryRef ?: kind.libraryRef,
                variant = patch.variant?.let { kind.variant + it } ?: kind.variant,
                props = patch.props?.let { kind.props + it } ?: kind.props,
                detach = patch.detach ?: kind.detach,
                resetOverrides = patch.resetOverrides ?: kind.resetOverrides,
            ),
        )
    }

    private fun applyProps(node: DesignNode, patch: PropsPatch): DesignNode {
        val kind = node.kind as? DesignNodeKind.Instance
            ?: DesignNodeKind.Instance(componentId = "".bindable())
        return node.copy(
            type = if (node.kind is DesignNodeKind.Instance) node.type else "instance",
            kind = kind.copy(props = kind.props + patch.props),
        )
    }

    private fun applyOverrides(node: DesignNode, patch: OverridesPatch): DesignNode {
        val kind = node.kind as? DesignNodeKind.Instance
            ?: DesignNodeKind.Instance(componentId = "".bindable())
        val overrides = mutableListOf<InstanceOverride>()
        patch.slots?.forEach { (slotName, fills) ->
            overrides += InstanceOverride(
                target = listOf(slotName),
                slotContent = fills.mapIndexed { index, fill ->
                    DesignNode(
                        id = "${node.id}-$slotName-${index + 1}",
                        type = "instance",
                        kind = DesignNodeKind.Instance(
                            componentId = fill.instanceRef.bindable(),
                            variant = fill.variant,
                            props = fill.props,
                        ),
                    )
                },
            )
        }
        patch.sets?.forEach { set -> overrides += setOverride(set) }
        patch.nestedInstances?.forEach { (targetId, nested) ->
            overrides += InstanceOverride(
                target = listOf(targetId),
                variant = nested.variant,
                props = nested.props,
            )
        }
        return node.copy(
            type = if (node.kind is DesignNodeKind.Instance) node.type else "instance",
            kind = kind.copy(overrides = kind.overrides + overrides),
        )
    }

    /**
     * Reads a [SetOverridePatch]'s property groups back into an [InstanceOverride]. The style/node
     * fields map straight across; typography merges into a [DesignTextStyle] via the shared
     * [applyText]. Only the groups the patch actually carried become non-null.
     */
    private fun setOverride(set: SetOverridePatch): InstanceOverride {
        val text = set.text
        val characters = when {
            text?.characters != null -> text.characters
            text?.defaultText != null -> text.defaultText.bindable()
            else -> null
        }
        val textStyle = text?.typography?.let {
            (applyText(DesignNode(id = "", type = "text", kind = DesignNodeKind.Text()), text).kind as DesignNodeKind.Text).textStyle
        }
        return InstanceOverride(
            target = set.target,
            fills = set.style?.fills,
            strokes = set.style?.strokes,
            opacity = set.style?.opacity,
            visible = set.node?.visible,
            characters = characters,
            textStyle = textStyle,
            cornerRadius = set.style?.radius,
        )
    }

    private fun applyMedia(node: DesignNode, patch: MediaPatch): DesignNode {
        val kind = node.kind as? DesignNodeKind.Media
            ?: DesignNodeKind.Media(DesignMedia(assetId = "".bindable()))
        val media = kind.media
        return node.copy(
            type = if (node.kind is DesignNodeKind.Media) node.type else "media",
            kind = kind.copy(
                media = media.copy(
                    assetId = patch.asset ?: media.assetId,
                    kind = patch.kind ?: media.kind,
                    fillMode = patch.fillMode ?: media.fillMode,
                    focalPoint = patch.focalPoint ?: media.focalPoint,
                    alt = patch.alt?.copy(defaultLocale = sourceLocale) ?: media.alt,
                    replaceable = patch.replaceable ?: media.replaceable,
                    opacity = patch.opacity ?: media.opacity,
                    blendMode = patch.blendMode ?: media.blendMode,
                    posterAssetId = patch.poster ?: media.posterAssetId,
                    autoplay = patch.autoplay ?: media.autoplay,
                    loop = patch.loop ?: media.loop,
                    muted = patch.muted ?: media.muted,
                ),
            ),
        )
    }

    private fun applyShape(node: DesignNode, patch: ShapePatch): DesignNode {
        val kind = node.kind as? DesignNodeKind.Shape
            ?: DesignNodeKind.Shape(shape = ShapeType.Rectangle)
        return node.copy(
            type = if (node.kind is DesignNodeKind.Shape) node.type else "shape",
            kind = kind.copy(
                shape = patch.kind ?: kind.shape,
                pointCount = patch.pointCount ?: kind.pointCount,
                innerRadius = patch.innerRadius ?: kind.innerRadius,
            ),
            size = mergeSize(node.size, patch.width, patch.height) ?: node.size,
        )
    }

    private fun applyVector(node: DesignNode, patch: VectorPatch, line: Int): DesignNode {
        if (patch.boolean != null) {
            // Operands are the node's nested children; the operation carries only its kind.
            return node.copy(
                type = "vector",
                kind = DesignNodeKind.BooleanOperation(patch.boolean.op),
            )
        }
        val kind = node.kind as? DesignNodeKind.Shape
            ?: DesignNodeKind.Shape(shape = ShapeType.Vector)
        return node.copy(
            type = if (node.kind is DesignNodeKind.Shape) node.type else "vector",
            kind = kind.copy(
                shape = if (node.kind is DesignNodeKind.Shape) kind.shape else ShapeType.Vector,
                iconRef = patch.iconRef ?: kind.iconRef,
                pathRef = patch.pathRef ?: kind.pathRef,
                viewBox = patch.viewBox ?: kind.viewBox,
                paths = patch.paths ?: kind.paths,
                network = patch.network ?: kind.network,
            ),
        )
    }

    private fun applyMask(node: DesignNode, patch: MaskPatch, line: Int): DesignNode =
        node.copy(
            mask = DesignMask(
                type = patch.type ?: MaskType.Alpha,
                appliesTo = patch.appliesTo ?: emptyList(),
                source = patch.source?.takeIf { it != node.id }.orEmpty(),
            ),
        )

    /** Responsive variant sub-patches -> IR [DesignNodePatch] via a probe node. */
    private fun toNodePatch(variant: ResponsiveVariantPatch): DesignNodePatch {
        val probeBase = DesignNode(id = "", type = "frame", kind = DesignNodeKind.Frame)
        val layoutProbe = variant.layout?.let { applyLayout(probeBase, it) }
        val styleProbe = variant.style?.let { applyStyle(probeBase, it) }
        return DesignNodePatch(
            layout = layoutProbe?.layout,
            sizing = layoutProbe?.sizing,
            size = layoutProbe?.size?.takeIf { it.width != null || it.height != null },
            minSize = layoutProbe?.minSize,
            maxSize = layoutProbe?.maxSize,
            scroll = layoutProbe?.scroll,
            opacity = variant.style?.opacity,
            fills = styleProbe?.fills,
            strokes = styleProbe?.strokes,
            effects = variant.style?.effects,
            cornerRadius = variant.style?.radius,
            textStyle = variant.text?.typography,
        )
    }
}

/**
 * Named non-null-able fields of a patch, used to detect explicit-vs-semantic
 * conflicts. Only property groups both sources can produce need coverage.
 */
internal fun patchFields(patch: TypedPatch): Map<String, Any?> = when (patch) {
    is NodePatch -> mapOf(
        "type" to patch.type, "id" to patch.id, "name" to patch.name, "role" to patch.role,
        "visible" to patch.visible, "locked" to patch.locked, "order" to patch.order,
        "position.mode" to patch.positionMode, "position.x" to patch.x, "position.y" to patch.y,
        "position.rotation" to patch.rotation,
        "constraints.horizontal" to patch.constraintsHorizontal,
        "constraints.vertical" to patch.constraintsVertical,
    )
    is LayoutPatch -> mapOf(
        "mode" to patch.mode,
        "padding.blockStart" to patch.paddingBlockStart,
        "padding.inlineEnd" to patch.paddingInlineEnd,
        "padding.blockEnd" to patch.paddingBlockEnd,
        "padding.inlineStart" to patch.paddingInlineStart,
        "gap" to patch.gap, "gap.row" to patch.rowGap, "gap.column" to patch.columnGap,
        "align.inline" to patch.alignInline, "align.block" to patch.alignBlock,
        "align.baseline" to patch.baseline, "distribution" to patch.distribution,
        "wrap" to patch.wrap, "sizing.width" to patch.sizingWidth,
        "sizing.height" to patch.sizingHeight, "clipContent" to patch.clipContent,
        "overflow.x" to patch.overflowX, "overflow.y" to patch.overflowY,
        "scroll.direction" to patch.scrollDirection,
        "position.mode" to patch.positionMode,
    )
    is StylePatch -> mapOf(
        "opacity" to patch.opacity, "blendMode" to patch.blendMode, "radius" to patch.radius,
        "fills" to patch.fills, "strokes" to patch.strokes, "effects" to patch.effects,
        "fillStyle" to patch.fillStyle, "textStyle" to patch.textStyle,
        "effectStyle" to patch.effectStyle, "gridStyle" to patch.gridStyle,
    )
    is TextPatch -> mapOf(
        "key" to patch.key, "defaultText" to patch.defaultText, "style" to patch.styleRef,
        "typography" to patch.typography, "resizing.width" to patch.resizingWidth,
        "resizing.height" to patch.resizingHeight, "truncate" to patch.truncate,
    )
    is MediaPatch -> mapOf(
        "asset" to patch.asset, "kind" to patch.kind, "fillMode" to patch.fillMode,
        "focalPoint" to patch.focalPoint, "alt" to patch.alt,
        "replaceable" to patch.replaceable, "opacity" to patch.opacity,
    )
    is ShapePatch -> mapOf(
        "kind" to patch.kind, "width" to patch.width, "height" to patch.height,
        "pointCount" to patch.pointCount, "innerRadius" to patch.innerRadius,
    )
    is ComponentPatch -> mapOf(
        "ref" to patch.ref, "libraryRef" to patch.libraryRef, "variant" to patch.variant,
    )
    else -> emptyMap()
}

/** Base node kind for a `node.type` string, keeping a compatible existing payload. */
internal fun kindForType(type: String, current: DesignNodeKind): DesignNodeKind = when (type) {
    "frame", "screen", "group", "section", "component" ->
        if (current is DesignNodeKind.Frame) current else DesignNodeKind.Frame
    "text" -> current as? DesignNodeKind.Text ?: DesignNodeKind.Text()
    "shape" -> current as? DesignNodeKind.Shape ?: DesignNodeKind.Shape(shape = ShapeType.Rectangle)
    "vector" -> current as? DesignNodeKind.Shape ?: DesignNodeKind.Shape(shape = ShapeType.Vector)
    "instance" -> current as? DesignNodeKind.Instance
        ?: DesignNodeKind.Instance(componentId = "".bindable())
    "media" -> current as? DesignNodeKind.Media ?: DesignNodeKind.Media(DesignMedia(assetId = "".bindable()))
    "table" -> current as? DesignNodeKind.Table ?: DesignNodeKind.Table(DesignTable())
    "slot" -> current as? DesignNodeKind.Slot ?: DesignNodeKind.Slot("")
    "annotation" -> current as? DesignNodeKind.Annotation
        ?: DesignNodeKind.Annotation(DesignAnnotation(text = ""))
    "slice" -> DesignNodeKind.Slice
    else -> DesignNodeKind.Unknown(type)
}
