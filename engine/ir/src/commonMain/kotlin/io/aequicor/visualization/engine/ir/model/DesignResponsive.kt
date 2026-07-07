package io.aequicor.visualization.engine.ir.model

enum class ResponsiveDimension {
    Breakpoint, DevicePreset, Platform, Theme, Density, Locale, Direction, Brand, State,
}

/**
 * Conditional node override. [selectors] ("when:") are AND-ed; the variant applies
 * when every selector equals the active dimension value. The most specific variant
 * wins per property group.
 */
data class ResponsiveVariant(
    val selectors: Map<ResponsiveDimension, String>,
    val patch: DesignNodePatch,
    val sourceMap: SourceLocation? = null,
)

/** Partial node override; null = untouched. Applied BEFORE variables/instances. */
data class DesignNodePatch(
    val visible: Bindable<Boolean>? = null,
    val opacity: Bindable<Double>? = null,
    /** Whole-block replacement. */
    val layout: DesignAutoLayout? = null,
    val layoutChild: DesignLayoutChild? = null,
    val gridPlacement: GridPlacement? = null,
    val sizing: DesignSizing? = null,
    val size: DesignSize? = null,
    val minSize: DesignSize? = null,
    val maxSize: DesignSize? = null,
    val fills: List<DesignPaint>? = null,
    val strokes: DesignStrokes? = null,
    val effects: List<DesignEffect>? = null,
    val cornerRadius: DesignCornerRadius? = null,
    /** Merged into the node's text style. */
    val textStyle: DesignTextStyle? = null,
    val scroll: DesignScroll? = null,
)

/**
 * Returns [node] with this patch's non-null property groups applied. Non-null groups
 * replace the whole block; [DesignNodePatch.textStyle] merges into a text node's style.
 */
fun DesignNodePatch.appliedTo(node: DesignNode): DesignNode {
    val kind = node.kind
    val patchedKind = if (textStyle != null && kind is DesignNodeKind.Text) {
        kind.copy(textStyle = (kind.textStyle ?: DesignTextStyle()).mergedWith(textStyle))
    } else {
        kind
    }
    return node.copy(
        kind = patchedKind,
        visible = visible ?: node.visible,
        opacity = opacity ?: node.opacity,
        layout = layout ?: node.layout,
        layoutChild = layoutChild ?: node.layoutChild,
        gridPlacement = gridPlacement ?: node.gridPlacement,
        sizing = sizing ?: node.sizing,
        size = size ?: node.size,
        minSize = minSize ?: node.minSize,
        maxSize = maxSize ?: node.maxSize,
        fills = fills ?: node.fills,
        strokes = strokes ?: node.strokes,
        effects = effects ?: node.effects,
        cornerRadius = cornerRadius ?: node.cornerRadius,
        scroll = scroll ?: node.scroll,
    )
}
