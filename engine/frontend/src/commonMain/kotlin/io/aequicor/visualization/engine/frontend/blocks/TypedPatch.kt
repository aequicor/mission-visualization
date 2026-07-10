package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignCornerRadius
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignHandoff
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.DesignTextStyle
import io.aequicor.visualization.engine.ir.model.DesignViewBox
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.VectorNetwork
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.HorizontalConstraint
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.OverflowMode
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.ScrollOverflow
import io.aequicor.visualization.engine.ir.model.ShapeType
import io.aequicor.visualization.engine.ir.model.SizingMode
import io.aequicor.visualization.engine.ir.model.TextContent
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextTruncate
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VectorPath
import io.aequicor.visualization.engine.ir.model.VerticalConstraint
import io.aequicor.visualization.engine.ir.model.DesignGap

/**
 * A partial IR fragment read from one typed attribute block entry. Every field is
 * nullable: `null` means "not authored" and never overwrites an earlier value when
 * the patch is applied — see `normalize/PatchMerger`.
 */
sealed interface TypedPatch

/** `position.mode` of a node/layout block. */
enum class NodePositionMode { Auto, Absolute }

/** `node:` block — common node contract fields. */
data class NodePatch(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val role: String? = null,
    val visible: Bindable<Boolean>? = null,
    val locked: Boolean? = null,
    val order: Int? = null,
    val positionMode: NodePositionMode? = null,
    val x: Double? = null,
    val y: Double? = null,
    val rotation: Double? = null,
    val constraintsHorizontal: HorizontalConstraint? = null,
    val constraintsVertical: VerticalConstraint? = null,
) : TypedPatch

/** One axis of `layout.sizing` / `text.resizing`. */
data class SizingPatch(
    val mode: SizingMode? = null,
    val value: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
)

/** `layout:` block — auto layout, grid, absolute positioning, scroll, guides. */
data class LayoutPatch(
    val mode: LayoutMode? = null,
    val paddingBlockStart: Bindable<Double>? = null,
    val paddingInlineEnd: Bindable<Double>? = null,
    val paddingBlockEnd: Bindable<Double>? = null,
    val paddingInlineStart: Bindable<Double>? = null,
    val gap: DesignGap? = null,
    val rowGap: Bindable<Double>? = null,
    val columnGap: Bindable<Double>? = null,
    val alignInline: AlignItems? = null,
    val alignBlock: AlignItems? = null,
    val baseline: BaselineAlign? = null,
    val distribution: JustifyContent? = null,
    val wrap: Boolean? = null,
    val sizingWidth: SizingPatch? = null,
    val sizingHeight: SizingPatch? = null,
    val clipContent: Boolean? = null,
    val overflowX: OverflowMode? = null,
    val overflowY: OverflowMode? = null,
    val scrollDirection: ScrollOverflow? = null,
    val scrollFixedChildren: List<String>? = null,
    val ignoreAutoLayout: Boolean? = null,
    val positionMode: NodePositionMode? = null,
    val anchorInlineStart: Bindable<Double>? = null,
    val anchorInlineEnd: Bindable<Double>? = null,
    val anchorBlockStart: Bindable<Double>? = null,
    val anchorBlockEnd: Bindable<Double>? = null,
    val positionX: Double? = null,
    val positionY: Double? = null,
    val gridColumns: List<GridTrack>? = null,
    val gridRows: List<GridTrack>? = null,
    val implicitRows: GridTrack? = null,
    val implicitRowMin: Double? = null,
    val placement: GridPlacement? = null,
    val guides: List<GuideLine>? = null,
    val grids: List<LayoutGridDefinition>? = null,
) : TypedPatch

/** `style:` block — visible layer appearance and shared style refs. */
data class StylePatch(
    val opacity: Bindable<Double>? = null,
    val blendMode: String? = null,
    val radius: DesignCornerRadius? = null,
    val fills: List<DesignPaint>? = null,
    val strokes: DesignStrokes? = null,
    val effects: List<DesignEffect>? = null,
    val fillStyle: String? = null,
    val textStyle: String? = null,
    val effectStyle: String? = null,
    val gridStyle: String? = null,
) : TypedPatch

/** One `text.spans` entry with the text-match form already resolved to a range. */
data class TextSpanPatch(
    val start: Int,
    val end: Int,
    val styleRef: String? = null,
    /** Inline per-range typography (`spans[].typography:`), merged over any [styleRef]. */
    val style: DesignTextStyle? = null,
    /** Inline per-range glyph fills (`spans[].fills:`). */
    val fills: List<DesignPaint>? = null,
    val linkUrl: String? = null,
    val linkNodeTarget: String? = null,
)

/** `text:` block — content key, typography, resizing, truncation, rich spans. */
data class TextPatch(
    val key: String? = null,
    val defaultText: String? = null,
    val styleRef: String? = null,
    val typography: DesignTextStyle? = null,
    val resizingWidth: SizingMode? = null,
    val resizingHeight: SizingMode? = null,
    val truncate: TextTruncate? = null,
    val spans: List<TextSpanPatch>? = null,
    val list: TextListSettings? = null,
) : TypedPatch

/** `component:` block — instance reference side and definition side in one patch. */
data class ComponentPatch(
    val ref: String? = null,
    val libraryRef: String? = null,
    val name: String? = null,
    /** Definition side: explicit component-set id this definition contributes to. */
    val set: String? = null,
    /**
     * On an instance: the requested axis-value selection. On a component DEFINITION:
     * the axis values this definition provides to its component set (sibling
     * definitions sharing `name` or `set` group into one `DesignComponentSet`).
     */
    val variant: Map<String, String>? = null,
    /** Definition side: variant axes, e.g. status -> [nominal, warning, critical]. */
    val variantsAxes: Map<String, List<String>>? = null,
    /** Definition side: declared component properties. */
    val properties: Map<String, ComponentPropertyDefinition>? = null,
    val props: Map<String, PropValue>? = null,
    val detach: Boolean? = null,
    val resetOverrides: Boolean? = null,
) : TypedPatch

/** `props:` block — instance property values. */
data class PropsPatch(
    val props: Map<String, PropValue>,
) : TypedPatch

/** One slot fill of an `overrides.slots` entry. */
data class SlotOverridePatch(
    val instanceRef: String,
    val props: Map<String, PropValue> = emptyMap(),
    val variant: Map<String, String> = emptyMap(),
)

/** One `overrides.nestedInstances` entry. */
data class NestedInstancePatch(
    val variant: Map<String, String>? = null,
    val props: Map<String, PropValue>? = null,
)

/** `overrides:` block — slot fills and nested-instance overrides. */
data class OverridesPatch(
    val slots: Map<String, List<SlotOverridePatch>>? = null,
    val nestedInstances: Map<String, NestedInstancePatch>? = null,
) : TypedPatch

/** `media:` block — image/video convenience layer. */
data class MediaPatch(
    val asset: String? = null,
    val kind: MediaKind? = null,
    val fillMode: ImageScaleMode? = null,
    val focalPoint: DesignPoint? = null,
    val alt: TextContent? = null,
    val replaceable: Boolean? = null,
    val opacity: Bindable<Double>? = null,
    val blendMode: String? = null,
    val poster: String? = null,
    val autoplay: Boolean? = null,
    val loop: Boolean? = null,
    val muted: Boolean? = null,
) : TypedPatch

/** `shape:` block — parametric primitive geometry. */
data class ShapePatch(
    val kind: ShapeType? = null,
    val width: Double? = null,
    val height: Double? = null,
    val pointCount: Int? = null,
    val innerRadius: Double? = null,
) : TypedPatch

/** `vector.boolean` — boolean operation over sibling nodes referenced by id. */
data class BooleanOpPatch(
    val op: BooleanOperationKind,
    val children: List<String>,
)

/** `vector:` block — icon/path refs, inline paths, structural network, boolean ops. */
data class VectorPatch(
    val iconRef: String? = null,
    val pathRef: String? = null,
    val viewBox: DesignViewBox? = null,
    val paths: List<VectorPath>? = null,
    val network: VectorNetwork? = null,
    val boolean: BooleanOpPatch? = null,
) : TypedPatch

/** `mask:` block. [source] names the mask node; empty = the anchor node itself. */
data class MaskPatch(
    val type: MaskType? = null,
    val source: String? = null,
    val appliesTo: List<String>? = null,
) : TypedPatch

/** `action:` block — shorthand for a single onClick interaction. */
data class ActionPatch(
    val action: DesignAction,
) : TypedPatch

/** `interaction:` block — one trigger firing one or more actions. */
data class InteractionPatch(
    val trigger: InteractionTrigger? = null,
    val key: String? = null,
    val delayMs: Double? = null,
    val variable: String? = null,
    val actions: List<DesignAction> = emptyList(),
) : TypedPatch

/** `motion:` block. */
data class MotionPatch(
    val motion: DesignMotion,
) : TypedPatch

/** One `responsive.variants` entry; nested patches reuse the block patch types. */
data class ResponsiveVariantPatch(
    val selectors: Map<ResponsiveDimension, String>,
    val layout: LayoutPatch? = null,
    val style: StylePatch? = null,
    val text: TextPatch? = null,
)

/** `responsive:` block. */
data class ResponsivePatch(
    val variants: List<ResponsiveVariantPatch>,
) : TypedPatch

/** `variables:` block — collections with modes plus prototype variables. */
data class VariablesPatch(
    /** collection id -> collection (modes + typed per-mode values). */
    val collections: Map<String, VariableCollection>? = null,
    val prototype: Map<String, PrototypeVariable>? = null,
) : TypedPatch

/** `handoff:` block — annotations, measurements, code hints. */
data class HandoffPatch(
    val handoff: DesignHandoff,
) : TypedPatch

/** `export:` block. */
data class ExportPatch(
    val enabled: Boolean? = null,
    val settings: List<ExportSetting> = emptyList(),
) : TypedPatch
