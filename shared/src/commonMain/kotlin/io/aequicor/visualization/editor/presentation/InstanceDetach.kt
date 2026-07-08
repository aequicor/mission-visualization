package io.aequicor.visualization.editor.presentation

import io.aequicor.visualization.engine.ir.model.DesignAutoLayout
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignInsets
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignUnit
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.UnitValue
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.resolve.DesignResolver
import io.aequicor.visualization.engine.ir.resolve.ResolvedAutoLayout
import io.aequicor.visualization.engine.ir.resolve.ResolvedCornerRadius
import io.aequicor.visualization.engine.ir.resolve.ResolvedEffect
import io.aequicor.visualization.engine.ir.resolve.ResolvedNode
import io.aequicor.visualization.engine.ir.resolve.ResolvedPaint
import io.aequicor.visualization.engine.ir.resolve.ResolvedStrokes
import io.aequicor.visualization.engine.ir.resolve.ResolvedTextStyle

/**
 * Detaches a component instance into a plain, editable Frame subtree (Figma "Detach
 * instance"). The instance is expanded to concrete geometry/paint/text via [DesignResolver]
 * — props, variants, overrides and visibility are already baked into the resolved tree — and
 * that tree is converted back into authored [DesignNode]s with fresh ids. Variable/token links
 * flatten to concrete values, matching Figma detach.
 *
 * The outer frame keeps the instance's identity (`id`, `name`, transform, `layoutChild`,
 * `constraints`) so it neither jumps nor loses its place in a parent's Auto layout; its layout,
 * fills, strokes, corner radius, effects and children come from the resolved component root.
 * After detach `kind` is [DesignNodeKind.Frame], so the inspector's Auto layout controls apply.
 *
 * Returns the updated document, or null when [nodeId] is not an instance (or can't be resolved).
 * In-memory only — the SLM source keeps the original instance ([io.aequicor.visualization.engine.frontend.SlmPatcher]
 * can't insert/replace subtrees), consistent with the editor's structural-edit boundary.
 */
internal fun detachInstance(document: DesignDocument, nodeId: String): DesignDocument? {
    val instance = document.nodeById(nodeId) ?: return null
    if (instance.kind !is DesignNodeKind.Instance) return null
    val resolved = DesignResolver(document).resolveNodeTree(instance) ?: return null

    val used = buildSet {
        document.pages.forEach { page ->
            add(page.id)
            page.allNodes().forEach { add(it.id) }
        }
    }.toMutableSet()
    fun freshId(): String {
        var n = 1
        while ("detached_$n" in used) n++
        used += "detached_$n"
        return "detached_$n"
    }

    val frame = instance.copy(
        type = "frame",
        kind = DesignNodeKind.Frame,
        size = resolved.size,
        sizing = instance.sizing ?: resolved.sizing,
        layout = resolved.layout.toDesign(),
        fills = resolved.fills.map { it.toDesign() }.ifEmpty { null },
        strokes = resolved.strokes?.toDesign(),
        cornerRadius = resolved.cornerRadius.toDesignOrNull(),
        effects = resolved.effects.map { it.toDesign() },
        children = resolved.children.map { it.toDesignNode(::freshId) },
    )
    return document.updateNode(nodeId) { frame }
}

/** Converts a fully resolved node back into an editable [DesignNode] with a fresh id. */
private fun ResolvedNode.toDesignNode(freshId: () -> String): DesignNode {
    val txt = text
    val shp = shape
    val designKind: DesignNodeKind = when {
        txt != null -> DesignNodeKind.Text(
            characters = txt.characters.bindable(),
            textStyle = txt.style.toDesign(),
            autoResize = txt.autoResize,
        )
        shp != null -> shp
        else -> DesignNodeKind.Frame
    }
    val designType = when {
        txt != null -> "text"
        shp != null -> "shape"
        else -> "frame"
    }
    return DesignNode(
        id = freshId(),
        type = designType,
        kind = designKind,
        name = name,
        role = role,
        opacity = opacity.bindable(),
        blendMode = blendMode,
        rotation = rotation,
        position = position,
        constraints = constraints,
        size = size,
        sizing = sizing,
        minSize = minSize,
        maxSize = maxSize,
        layout = layout.toDesign(),
        layoutChild = layoutChild,
        gridPlacement = gridPlacement,
        fills = fills.map { it.toDesign() }.ifEmpty { null },
        strokes = strokes?.toDesign(),
        effects = effects.map { it.toDesign() },
        cornerRadius = cornerRadius.toDesignOrNull(),
        scroll = scroll,
        children = children.map { it.toDesignNode(freshId) },
    )
}

private fun ResolvedAutoLayout.toDesign(): DesignAutoLayout = DesignAutoLayout(
    mode = mode,
    gap = if (gapAuto) DesignGap.Auto else DesignGap.Fixed(gap.bindable()),
    crossGap = crossGap.takeIf { it != 0.0 }?.bindable(),
    wrap = wrap,
    padding = DesignInsets(paddingTop.bindable(), paddingRight.bindable(), paddingBottom.bindable(), paddingLeft.bindable()),
    alignItems = alignItems,
    justifyContent = justifyContent,
    baseline = baseline,
    clipsContent = clipsContent,
    columns = columns,
    rows = rows,
    columnGap = columnGap.takeIf { it != 0.0 }?.bindable(),
    rowGap = rowGap.takeIf { it != 0.0 }?.bindable(),
    implicitRows = implicitRows,
    implicitRowMin = implicitRowMin,
)

private fun ResolvedPaint.toDesign(): DesignPaint = when (this) {
    is ResolvedPaint.Solid -> DesignPaint.Solid(color.bindable(), opacity = opacity.bindable())
    is ResolvedPaint.Gradient -> DesignPaint.Gradient(
        gradientType = gradientType,
        from = from,
        to = to,
        stops = stops.map { GradientStop(it.position, it.color.bindable()) },
        opacity = opacity.bindable(),
    )
    is ResolvedPaint.Image -> DesignPaint.Image(assetId = assetId, scaleMode = scaleMode, opacity = opacity.bindable())
    is ResolvedPaint.Unknown -> DesignPaint.Unknown(rawType = rawType, opacity = opacity.bindable())
}

private fun ResolvedStrokes.toDesign(): DesignStrokes {
    val perSide = if (weightTop != null || weightRight != null || weightBottom != null || weightLeft != null) {
        DesignInsets(
            top = (weightTop ?: weight).bindable(),
            right = (weightRight ?: weight).bindable(),
            bottom = (weightBottom ?: weight).bindable(),
            left = (weightLeft ?: weight).bindable(),
        )
    } else {
        null
    }
    return DesignStrokes(
        paints = paints.map { it.toDesign() },
        weight = weight.bindable(),
        weightPerSide = perSide,
        align = align,
        dashPattern = dashPattern,
    )
}

private fun ResolvedCornerRadius.toDesignOrNull(): DesignCornerRadius? =
    if (isZero) {
        null
    } else {
        DesignCornerRadius(topLeft.bindable(), topRight.bindable(), bottomRight.bindable(), bottomLeft.bindable(), smoothing = smoothing)
    }

private fun ResolvedEffect.toDesign(): DesignEffect = when (this) {
    is ResolvedEffect.DropShadow -> DesignEffect.DropShadow(color.bindable(), offset, blur, spread)
    is ResolvedEffect.InnerShadow -> DesignEffect.InnerShadow(color.bindable(), offset, blur, spread)
    is ResolvedEffect.LayerBlur -> DesignEffect.LayerBlur(radius)
    is ResolvedEffect.BackgroundBlur -> DesignEffect.BackgroundBlur(radius)
}

private fun ResolvedTextStyle.toDesign(): DesignTextStyle = DesignTextStyle(
    fontFamily = fontFamily.ifEmpty { null },
    fontWeight = fontWeight.toDouble().bindable(),
    fontSize = fontSize.bindable(),
    lineHeight = lineHeight.takeIf { it > 0.0 }?.let { UnitValue(DesignUnit.Px, it) },
    letterSpacing = letterSpacing.takeIf { it != 0.0 }?.let { UnitValue(DesignUnit.Px, it) },
    paragraphSpacing = paragraphSpacing.takeIf { it != 0.0 },
    textAlignHorizontal = textAlignHorizontal,
    textAlignVertical = textAlignVertical,
    textCase = textCase,
    textDecoration = textDecoration,
)
