package io.aequicor.visualization.engine.ir.model

/**
 * Everything is a node in the tree — page frame, text, icon, component instance —
 * sharing one base contract discriminated by [type]. Order in [children] is paint
 * order; there is no separate z-index.
 */
data class DesignNode(
    val id: String,
    val type: String,
    val kind: DesignNodeKind,
    val name: String = "",
    val visible: Bindable<Boolean> = true.bindable(),
    val locked: Boolean = false,
    val opacity: Bindable<Double> = 1.0.bindable(),
    val blendMode: String = "normal",
    val rotation: Double = 0.0,
    val isMask: Boolean = false,
    val position: DesignPoint? = null,
    val constraints: DesignConstraints = DesignConstraints(),
    val size: DesignSize = DesignSize(),
    /** Null when not authored: instances then inherit the component root's sizing. */
    val sizing: DesignSizing? = null,
    val minSize: DesignSize? = null,
    val maxSize: DesignSize? = null,
    val layout: DesignAutoLayout = DesignAutoLayout(),
    val layoutChild: DesignLayoutChild = DesignLayoutChild(),
    val gridPlacement: GridPlacement? = null,
    val fills: List<DesignPaint>? = null,
    val strokes: DesignStrokes? = null,
    val effects: List<DesignEffect> = emptyList(),
    val cornerRadius: DesignCornerRadius? = null,
    val fillStyleId: String = "",
    val strokeStyleId: String = "",
    val effectStyleId: String = "",
    val variableModes: Map<String, String> = emptyMap(),
    val scroll: DesignScroll = DesignScroll(),
    val children: List<DesignNode> = emptyList(),
) {
    fun findById(nodeId: String): DesignNode? {
        if (id == nodeId) return this
        return children.firstNotNullOfOrNull { it.findById(nodeId) }
    }

    fun allDescendants(): List<DesignNode> =
        children.flatMap { listOf(it) + it.allDescendants() }

    /** Returns a copy of the tree with the node [nodeId] replaced via [transform]. */
    fun updateNode(nodeId: String, transform: (DesignNode) -> DesignNode): DesignNode {
        if (id == nodeId) return transform(this)
        var changed = false
        val updated = children.map { child ->
            val next = child.updateNode(nodeId, transform)
            if (next !== child) changed = true
            next
        }
        return if (changed) copy(children = updated) else this
    }
}

/** Type-specific payload of a node, discriminated by the JSON `type` field. */
sealed interface DesignNodeKind {
    /** `frame`, `group`, `section`, and component roots share container semantics. */
    data object Frame : DesignNodeKind

    data class Text(
        val characters: Bindable<String> = "".bindable(),
        val textStyleId: String = "",
        val textStyle: DesignTextStyle? = null,
        val autoResize: TextAutoResize = TextAutoResize.None,
        val truncate: TextTruncate? = null,
        val styleRanges: List<TextStyleRange> = emptyList(),
        val links: List<TextLink> = emptyList(),
    ) : DesignNodeKind

    /** Parametric primitives and freeform vectors. */
    data class Shape(
        val shape: ShapeType,
        val pointCount: Int? = null,
        val innerRadius: Double? = null,
        val paths: List<VectorPath> = emptyList(),
    ) : DesignNodeKind

    data class Instance(
        val componentId: Bindable<String>,
        val variant: Map<String, String> = emptyMap(),
        val props: Map<String, PropValue> = emptyMap(),
        val overrides: List<InstanceOverride> = emptyList(),
    ) : DesignNodeKind

    data class BooleanOperation(
        val operation: BooleanOperationKind,
    ) : DesignNodeKind

    data object Slice : DesignNodeKind

    /** Forward compatibility: unknown node types render as a fallback, never fail. */
    data class Unknown(val rawType: String) : DesignNodeKind
}

enum class ShapeType { Rectangle, Ellipse, Polygon, Star, Line, Vector }

data class VectorPath(
    val windingRule: String = "nonzero",
    val d: String,
)

enum class BooleanOperationKind { Union, Subtract, Intersect, Exclude }

/** Value passed into a component property slot. */
sealed interface PropValue {
    data class Text(val value: String) : PropValue

    data class Bool(val value: Boolean) : PropValue

    data class Number(val value: Double) : PropValue

    /** Component id for `instanceSwap`, or a variant axis value. */
    data class Reference(val value: String) : PropValue
}

/**
 * Instance override addressed by an id path inside the component tree (not by index),
 * so overrides survive component edits. Nested instances extend the path.
 */
data class InstanceOverride(
    val target: List<String>,
    val fills: List<DesignPaint>? = null,
    val strokes: DesignStrokes? = null,
    val opacity: Bindable<Double>? = null,
    val visible: Bindable<Boolean>? = null,
    val characters: Bindable<String>? = null,
    val textStyle: DesignTextStyle? = null,
    val cornerRadius: DesignCornerRadius? = null,
)
