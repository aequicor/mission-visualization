package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.InstanceOverride
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.VariableValue

/**
 * Turns the authored [DesignDocument] tree into a [ResolvedNode] tree: resolves
 * `$var` bindings against variable collections and active modes, `$prop` bindings
 * against component properties, shared styles, i18n text content against the active
 * locale, and expands component instances (variant selection, prop substitution,
 * id-path overrides).
 */
class DesignResolver(
    private val document: DesignDocument,
    private val context: ResolveContext = ResolveContext(),
) {

    /** Locale used for i18n text resolution: explicit context locale, else the source locale. */
    val activeLocale: String = context.locale ?: document.i18n.sourceLocale

    /** Layout direction: explicit in the context, else derived from [activeLocale]. */
    val direction: LayoutDirection = context.direction ?: directionForLocale(activeLocale)

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
    )

    private fun rootScope(): Scope = Scope(modes = context.modeSelections)

    fun resolvePage(page: DesignPage): List<ResolvedNode> =
        page.children.mapNotNull { resolveNode(it, rootScope()) }

    fun resolveNodeTree(node: DesignNode): ResolvedNode? =
        resolveNode(node, rootScope())

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
            layout = resolveAutoLayout(node.layout, scope),
            layoutChild = node.layoutChild,
            gridPlacement = node.gridPlacement,
            fills = resolveFills(node, override, scope),
            strokes = resolveStrokes(node, override, scope),
            effects = resolveEffects(node, scope),
            cornerRadius = resolveCornerRadius(override?.cornerRadius ?: node.cornerRadius, scope),
            text = (node.kind as? DesignNodeKind.Text)?.let { resolveText(it, override, scope) },
            shape = node.kind as? DesignNodeKind.Shape,
            scroll = node.scroll,
            children = node.children.mapNotNull { resolveNode(it, childScope) },
        )
    }

    private fun expandInstance(
        node: DesignNode,
        instance: DesignNodeKind.Instance,
        scope: Scope,
        override: InstanceOverride?,
    ): ResolvedNode? {
        val requestedId = resolveString(instance.componentId, scope, "")
        val componentId = document.componentSets[requestedId]
            ?.let { set ->
                set.resolveVariant(instance.variant) ?: run {
                    warn("Component set '$requestedId' has no variant for ${instance.variant}")
                    null
                }
            }
            ?: requestedId
        val component = document.components[componentId]
        val resolvedInstanceId = scope.idPrefix + node.id
        if (component == null) {
            warn("Unknown component '$requestedId' for instance '${node.id}'")
            return instancePlaceholder(node, resolvedInstanceId, scope)
        }
        if (componentId in scope.componentChain) {
            warn("Component cycle detected at '$componentId'; instance '${node.id}' truncated")
            return instancePlaceholder(node, resolvedInstanceId, scope)
        }

        val defaults = component.properties.mapNotNull { (name, definition) ->
            definition.default?.let { name to it }
        }.toMap()
        val variantProps = instance.variant.mapValues { (_, value) -> PropValue.Text(value) }

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
            props = defaults + variantProps + instance.props,
            idPrefix = "$resolvedInstanceId/",
            overrides = instance.overrides +
                listOfNotNull(authoredAsOverride) +
                scope.overrides
                    .filter { it.target.size > 1 && it.target.first() == node.id }
                    .map { it.copy(target = it.target.drop(1)) } +
                listOfNotNull(overrideForRoot),
            componentChain = scope.componentChain + componentId,
            selectableRootId = scope.selectableRootId ?: node.id,
        )

        val root = resolveNode(component.root, innerScope) ?: return null
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
            // Data-bound params are skipped until the expression evaluator lands (stage 5.3);
            // their placeholders stay verbatim in the formatted text.
            if (value is Bindable.DataRef) {
                warnUnevaluatedExpression(value.expression)
                null
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

    private fun resolveAutoLayout(layout: DesignAutoLayout, scope: Scope): ResolvedAutoLayout {
        val fixedGap = (layout.gap as? DesignGap.Fixed)?.let { resolveDouble(it.value, scope, 0.0) } ?: 0.0
        return ResolvedAutoLayout(
            mode = layout.mode,
            gap = fixedGap,
            gapAuto = layout.gap is DesignGap.Auto,
            crossGap = layout.crossGap?.let { resolveDouble(it, scope, 0.0) } ?: fixedGap,
            wrap = layout.wrap,
            paddingTop = resolveDouble(layout.padding.top, scope, 0.0),
            paddingRight = resolveDouble(layout.padding.right, scope, 0.0),
            paddingBottom = resolveDouble(layout.padding.bottom, scope, 0.0),
            paddingLeft = resolveDouble(layout.padding.left, scope, 0.0),
            alignItems = layout.alignItems,
            justifyContent = layout.justifyContent,
            clipsContent = layout.clipsContent,
            columns = layout.columns,
            rows = layout.rows,
            columnGap = layout.columnGap?.let { resolveDouble(it, scope, 0.0) } ?: fixedGap,
            rowGap = layout.rowGap?.let { resolveDouble(it, scope, 0.0) } ?: fixedGap,
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
                else -> {
                    warn("Prop '${bindable.name}' did not resolve to a number")
                    fallback
                }
            }
            is Bindable.DataRef -> {
                warnUnevaluatedExpression(bindable.expression)
                fallback
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
                is PropValue.Data -> {
                    warnUnevaluatedExpression(prop.expression)
                    fallback
                }
                is PropValue.SlotContent -> fallback
                else -> {
                    warn("Prop '${bindable.name}' did not resolve to a string")
                    fallback
                }
            }
            is Bindable.DataRef -> {
                warnUnevaluatedExpression(bindable.expression)
                fallback
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
                else -> {
                    warn("Prop '${bindable.name}' did not resolve to a boolean")
                    fallback
                }
            }
            is Bindable.DataRef -> {
                warnUnevaluatedExpression(bindable.expression)
                fallback
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
                warnUnevaluatedExpression(bindable.expression)
                DesignColor.Black
            }
        }

    /** No expression evaluator yet (stage 5.3): data bindings warn and use the fallback. */
    private fun warnUnevaluatedExpression(expression: DesignExpression) {
        warn("Data binding '{{${expression.raw}}}' is not evaluated yet; using fallback")
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
