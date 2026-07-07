package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.VariableType

/**
 * Shared traversal of every [Bindable] slot on a node, used by StyleChecks
 * (variable refs and expected types) and DataChecks (`{{...}}` expressions).
 * The walk covers node visibility/opacity, layout scalars, paints, strokes,
 * effects, corner radii, text (characters, content params, style scalars,
 * ranges), media, instance props, interactions, and responsive patches.
 */
internal data class BindingUse(
    val bindable: Bindable<*>,
    /** Scalar type the binding site expects. */
    val expected: VariableType,
    /** Human-readable field path, e.g. "fills[0].color". */
    val field: String,
    val node: DesignNode,
)

internal fun collectBindingUses(node: DesignNode): List<BindingUse> {
    val uses = mutableListOf<BindingUse>()
    fun add(bindable: Bindable<*>?, expected: VariableType, field: String) {
        if (bindable != null) uses += BindingUse(bindable, expected, field, node)
    }

    fun addPaints(paints: List<DesignPaint>?, field: String) {
        paints?.forEachIndexed { index, paint ->
            add(paint.visible, VariableType.Bool, "$field[$index].visible")
            add(paint.opacity, VariableType.Number, "$field[$index].opacity")
            when (paint) {
                is DesignPaint.Solid -> add(paint.color, VariableType.Color, "$field[$index].color")
                is DesignPaint.Gradient -> paint.stops.forEachIndexed { stop, gradientStop ->
                    add(gradientStop.color, VariableType.Color, "$field[$index].stops[$stop].color")
                }
                else -> Unit
            }
        }
    }

    fun addStrokes(strokes: DesignStrokes?, field: String) {
        if (strokes == null) return
        addPaints(strokes.paints, "$field.paints")
        add(strokes.weight, VariableType.Number, "$field.weight")
        strokes.weightPerSide?.let { perSide ->
            add(perSide.top, VariableType.Number, "$field.weightPerSide.top")
            add(perSide.right, VariableType.Number, "$field.weightPerSide.right")
            add(perSide.bottom, VariableType.Number, "$field.weightPerSide.bottom")
            add(perSide.left, VariableType.Number, "$field.weightPerSide.left")
        }
    }

    fun addTextStyle(style: DesignTextStyle?, field: String) {
        if (style == null) return
        add(style.fontSize, VariableType.Number, "$field.fontSize")
        add(style.fontWeight, VariableType.Number, "$field.fontWeight")
    }

    fun addContentParams(content: TextContent?, field: String) {
        content?.params?.forEach { (name, value) ->
            add(value, VariableType.Text, "$field.params.$name")
        }
    }

    add(node.visible, VariableType.Bool, "visible")
    add(node.opacity, VariableType.Number, "opacity")

    (node.layout.gap as? DesignGap.Fixed)?.let { add(it.value, VariableType.Number, "layout.gap") }
    add(node.layout.crossGap, VariableType.Number, "layout.crossGap")
    add(node.layout.columnGap, VariableType.Number, "layout.columnGap")
    add(node.layout.rowGap, VariableType.Number, "layout.rowGap")
    node.layout.padding.let { padding ->
        add(padding.top, VariableType.Number, "layout.padding.top")
        add(padding.right, VariableType.Number, "layout.padding.right")
        add(padding.bottom, VariableType.Number, "layout.padding.bottom")
        add(padding.left, VariableType.Number, "layout.padding.left")
    }
    node.layout.paddingLogical?.let { logical ->
        add(logical.blockStart, VariableType.Number, "layout.paddingLogical.blockStart")
        add(logical.inlineEnd, VariableType.Number, "layout.paddingLogical.inlineEnd")
        add(logical.blockEnd, VariableType.Number, "layout.paddingLogical.blockEnd")
        add(logical.inlineStart, VariableType.Number, "layout.paddingLogical.inlineStart")
    }
    node.anchors?.let { anchors ->
        add(anchors.inlineStart, VariableType.Number, "anchors.inlineStart")
        add(anchors.inlineEnd, VariableType.Number, "anchors.inlineEnd")
        add(anchors.blockStart, VariableType.Number, "anchors.blockStart")
        add(anchors.blockEnd, VariableType.Number, "anchors.blockEnd")
    }

    addPaints(node.fills, "fills")
    addStrokes(node.strokes, "strokes")
    node.effects.forEachIndexed { index, effect ->
        add(effect.visible, VariableType.Bool, "effects[$index].visible")
    }
    node.cornerRadius?.let { radius ->
        add(radius.topLeft, VariableType.Number, "cornerRadius.topLeft")
        add(radius.topRight, VariableType.Number, "cornerRadius.topRight")
        add(radius.bottomRight, VariableType.Number, "cornerRadius.bottomRight")
        add(radius.bottomLeft, VariableType.Number, "cornerRadius.bottomLeft")
    }

    when (val kind = node.kind) {
        is DesignNodeKind.Text -> {
            add(kind.characters, VariableType.Text, "characters")
            addContentParams(kind.content, "content")
            addTextStyle(kind.textStyle, "textStyle")
            kind.styleRanges.forEachIndexed { index, range ->
                addTextStyle(range.style, "styleRanges[$index].style")
                addPaints(range.fills, "styleRanges[$index].fills")
            }
        }
        is DesignNodeKind.Instance -> {
            add(kind.componentId, VariableType.Text, "componentId")
            kind.props.forEach { (name, value) ->
                if (value is PropValue.Content) addContentParams(value.content, "props.$name")
            }
        }
        is DesignNodeKind.Media -> {
            add(kind.media.opacity, VariableType.Number, "media.opacity")
            addContentParams(kind.media.alt, "media.alt")
        }
        is DesignNodeKind.Table -> {
            add(kind.table.rowGap, VariableType.Number, "table.rowGap")
            add(kind.table.columnGap, VariableType.Number, "table.columnGap")
        }
        else -> Unit
    }

    node.interactions.forEachIndexed { index, interaction ->
        interaction.actions.forEachIndexed { actionIndex, action ->
            when (action) {
                is DesignAction.SetVariable ->
                    add(action.value, VariableType.Text, "interactions[$index].actions[$actionIndex].value")
                is DesignAction.OpenOverlay ->
                    add(action.overlay.background, VariableType.Color, "interactions[$index].actions[$actionIndex].background")
                else -> Unit
            }
        }
    }

    node.responsive.forEachIndexed { index, variant ->
        add(variant.patch.visible, VariableType.Bool, "responsive[$index].visible")
        add(variant.patch.opacity, VariableType.Number, "responsive[$index].opacity")
        addPaints(variant.patch.fills, "responsive[$index].fills")
        addStrokes(variant.patch.strokes, "responsive[$index].strokes")
        addTextStyle(variant.patch.textStyle, "responsive[$index].textStyle")
    }

    return uses
}

/** One `{{...}}` expression occurrence with the repeat bindings visible at that node. */
internal data class ExpressionUse(
    val expression: DesignExpression,
    /** What the expression drives: "condition", "repeat.collection", a binding field, ... */
    val field: String,
    val node: DesignNode,
    /** Repeat item/index names declared by this node or its ancestors. */
    val repeatBindings: Set<String>,
    /** True when this expression must evaluate to a list (repeat collections). */
    val expectsList: Boolean = false,
)

/** Collects every expression in the tree rooted at [node], tracking repeat scopes. */
internal fun collectExpressionUses(
    node: DesignNode,
    inherited: Set<String> = emptySet(),
): List<ExpressionUse> {
    val uses = mutableListOf<ExpressionUse>()
    node.condition?.let {
        uses += ExpressionUse(it.expression, "condition", node, inherited)
    }
    val bindings = node.repeat?.let { repeat ->
        uses += ExpressionUse(repeat.collection, "repeat.collection", node, inherited, expectsList = true)
        val itemScope = inherited + setOfNotNull(repeat.itemName, repeat.indexName)
        repeat.key?.let { uses += ExpressionUse(it, "repeat.key", node, itemScope) }
        itemScope
    } ?: inherited
    collectBindingUses(node).forEach { use ->
        (use.bindable as? Bindable.DataRef)?.let {
            uses += ExpressionUse(it.expression, use.field, node, bindings)
        }
    }
    (node.kind as? DesignNodeKind.Instance)?.props?.forEach { (name, value) ->
        if (value is PropValue.Data) {
            uses += ExpressionUse(value.expression, "props.$name", node, bindings)
        }
    }
    node.children.forEach { uses += collectExpressionUses(it, bindings) }
    return uses
}

/** First path segment of an expression (skipping a leading `!`), or "" when unparseable. */
internal fun rootNameOf(expression: DesignExpression): String =
    expression.raw.trim().removePrefix("!").trim()
        .takeWhile { it.isLetterOrDigit() || it == '_' }
