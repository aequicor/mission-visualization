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
    /** Semantic role from SLM, e.g. "primaryAction"; free-form, resolve-neutral. */
    val role: String = "",
    val visible: Bindable<Boolean> = true.bindable(),
    val locked: Boolean = false,
    /** Explicit sibling order; null keeps document order. */
    val order: Int? = null,
    val opacity: Bindable<Double> = 1.0.bindable(),
    val blendMode: String = "normal",
    val rotation: Double = 0.0,
    /** Legacy sibling-scope mask flag; [mask] is the explicit form. */
    val isMask: Boolean = false,
    val mask: DesignMask? = null,
    val position: DesignPoint? = null,
    val constraints: DesignConstraints = DesignConstraints(),
    /** Logical anchors for absolute children; win over [position]/[constraints]. */
    val anchors: DesignAnchors? = null,
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
    val gridStyleId: String = "",
    /** Overlay/validator metadata only; no layout effect. */
    val layoutGrids: List<LayoutGridDefinition> = emptyList(),
    val guides: List<GuideLine> = emptyList(),
    val variableModes: Map<String, String> = emptyMap(),
    val condition: DesignCondition? = null,
    val repeat: DesignRepeat? = null,
    val interactions: List<DesignInteraction> = emptyList(),
    val motion: DesignMotion? = null,
    val responsive: List<ResponsiveVariant> = emptyList(),
    val exportSettings: List<ExportSetting> = emptyList(),
    val scroll: DesignScroll = DesignScroll(),
    val sourceMap: SourceLocation? = null,
    /** Per property-group source maps, e.g. "layout" -> location. */
    val blockSourceMaps: Map<String, SourceLocation> = emptyMap(),
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
        /** Raw/legacy content; [content] wins when present. */
        val characters: Bindable<String> = "".bindable(),
        /** i18n content intent; wins over [characters]. */
        val content: TextContent? = null,
        val format: TextFormat? = null,
        val textStyleId: String = "",
        val textStyle: DesignTextStyle? = null,
        val autoResize: TextAutoResize = TextAutoResize.None,
        val truncate: TextTruncate? = null,
        val styleRanges: List<TextStyleRange> = emptyList(),
        val links: List<TextLink> = emptyList(),
        val list: TextListSettings = TextListSettings(),
    ) : DesignNodeKind

    /** Parametric primitives and freeform vectors. */
    data class Shape(
        val shape: ShapeType,
        val pointCount: Int? = null,
        val innerRadius: Double? = null,
        val paths: List<VectorPath> = emptyList(),
        /** Design-system icon reference, e.g. "ds/Icon/Alert". */
        val iconRef: String = "",
        /** Asset id of an SVG asset. */
        val pathRef: String = "",
        val viewBox: DesignViewBox? = null,
    ) : DesignNodeKind

    data class Instance(
        val componentId: Bindable<String>,
        val libraryRef: String = "",
        val variant: Map<String, String> = emptyMap(),
        val props: Map<String, PropValue> = emptyMap(),
        val overrides: List<InstanceOverride> = emptyList(),
        /** Authoring flag: resolves identically, marks the resolved node as detached. */
        val detach: Boolean = false,
        val resetOverrides: Boolean = false,
    ) : DesignNodeKind

    data class BooleanOperation(
        val operation: BooleanOperationKind,
    ) : DesignNodeKind

    data object Slice : DesignNodeKind

    data class Media(val media: DesignMedia) : DesignNodeKind

    data class Table(val table: DesignTable) : DesignNodeKind

    /** Slot target inside a component tree, filled by instances. */
    data class Slot(val slotName: String) : DesignNodeKind

    data class Annotation(val annotation: DesignAnnotation) : DesignNodeKind

    /** Forward compatibility: unknown node types render as a fallback, never fail. */
    data class Unknown(val rawType: String) : DesignNodeKind
}

enum class ShapeType { Rectangle, Ellipse, Polygon, Star, Line, Arrow, Vector }

data class DesignViewBox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
)

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

    /** i18n text prop. */
    data class Content(val content: TextContent) : PropValue

    /** Data-binding prop. */
    data class Data(val expression: DesignExpression) : PropValue

    /** Slot fill: authored nodes injected into a component's slot. */
    data class SlotContent(val nodes: List<DesignNode>) : PropValue
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
    /** Nested-instance overrides: applied when the target path ends at an instance. */
    val variant: Map<String, String>? = null,
    val props: Map<String, PropValue>? = null,
    /** Slot fill; the target path addresses a Slot node. */
    val slotContent: List<DesignNode>? = null,
)
