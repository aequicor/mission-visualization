package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.figures.PathGeometry
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.BaselineAlign
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignAnnotation
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignConstraints
import io.aequicor.visualization.engine.ir.model.DesignLayoutChild
import io.aequicor.visualization.engine.ir.model.DesignMotion
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignScroll
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.DesignSizing
import io.aequicor.visualization.engine.ir.model.ExportSetting
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GridPlacement
import io.aequicor.visualization.engine.ir.model.GridTrack
import io.aequicor.visualization.engine.ir.model.GuideLine
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.model.JustifyContent
import io.aequicor.visualization.engine.ir.model.LayoutGridDefinition
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.MaskType
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.LeadingTrim
import io.aequicor.visualization.engine.ir.model.TextAlignHorizontal
import io.aequicor.visualization.engine.ir.model.TextAlignVertical
import io.aequicor.visualization.engine.ir.model.TextAutoResize
import io.aequicor.visualization.engine.ir.model.TextCase
import io.aequicor.visualization.engine.ir.model.TextDecorationKind
import io.aequicor.visualization.engine.ir.model.TextDecorationStyle
import io.aequicor.visualization.engine.ir.model.TextLink
import io.aequicor.visualization.engine.ir.model.TextListSettings
import io.aequicor.visualization.engine.ir.model.TextScriptPosition
import io.aequicor.visualization.engine.ir.model.TextTruncate

/**
 * Node tree after variable, style, prop, and instance resolution: every value is
 * concrete, instances are expanded into their component subtrees. Input to the
 * layout engine.
 */
data class ResolvedNode(
    /** Unique id in the resolved tree; instance internals are prefixed with the instance path. */
    val id: String,
    /** Id of the authored node this one came from (selection/inspector target). */
    val sourceId: String,
    /**
     * Authored id the editor selects when this node is hit: instance internals map
     * to their outermost enclosing instance, everything else to itself.
     */
    val selectableId: String = sourceId,
    val type: String,
    val name: String,
    val opacity: Double = 1.0,
    val rotation: Double = 0.0,
    val position: DesignPoint? = null,
    val constraints: DesignConstraints = DesignConstraints(),
    val size: DesignSize = DesignSize(),
    val sizing: DesignSizing = DesignSizing(),
    val minSize: DesignSize? = null,
    val maxSize: DesignSize? = null,
    val layout: ResolvedAutoLayout = ResolvedAutoLayout(),
    val layoutChild: DesignLayoutChild = DesignLayoutChild(),
    val gridPlacement: GridPlacement? = null,
    val fills: List<ResolvedPaint> = emptyList(),
    val strokes: ResolvedStrokes? = null,
    val effects: List<ResolvedEffect> = emptyList(),
    val cornerRadius: ResolvedCornerRadius = ResolvedCornerRadius(),
    val text: ResolvedText? = null,
    val shape: DesignNodeKind.Shape? = null,
    /**
     * Embedded diagram of a `diagram` node, carried through resolution as-is (the graph is
     * already concrete). Graph coordinates are local to the node's laid-out box.
     */
    val diagram: DiagramGraph? = null,
    /**
     * Lowered, device-independent outline for shape nodes (vector/network/inline-`d` in
     * view-box space, or null when geometry is built draw-time from the laid-out box).
     */
    val geometry: PathGeometry? = null,
    /** Per-region fills (Figma region paint): each region's own lowered geometry + resolved paints. */
    val regionPaints: List<ResolvedRegionPaint> = emptyList(),
    /** Populated for boolean-operation nodes; drives path combination at render time. */
    val booleanOp: BooleanOperationKind? = null,
    val scroll: DesignScroll = DesignScroll(),
    /** Semantic role from SLM; carried through, resolve-neutral. */
    val role: String = "",
    val blendMode: String = "normal",
    val interactions: List<ResolvedInteraction> = emptyList(),
    /** Media payload of a lowered `media` node (its placeholder paint is in [fills]). */
    val media: ResolvedMedia? = null,
    val mask: ResolvedMask? = null,
    val motion: DesignMotion? = null,
    val exportSettings: List<ExportSetting> = emptyList(),
    /** Overlay/validator metadata only; no layout effect. */
    val layoutGrids: List<LayoutGridDefinition> = emptyList(),
    val guides: List<GuideLine> = emptyList(),
    /** Payload of an `annotation` node; handoff metadata, never affects geometry. */
    val annotation: DesignAnnotation? = null,
    val sourceMap: SourceLocation? = null,
    /** True when the node came from an instance authored with `detach: true`. */
    val detached: Boolean = false,
    val children: List<ResolvedNode> = emptyList(),
)

/**
 * Prototyping interaction carried through resolution; bindable action values
 * ([DesignAction.SetVariable]) are substituted with literals where possible.
 */
data class ResolvedInteraction(
    val trigger: InteractionTrigger,
    val key: String = "",
    val delayMs: Double? = null,
    val variable: String = "",
    val actions: List<DesignAction> = emptyList(),
    val sourceMap: SourceLocation? = null,
)

/** Media payload carried alongside the placeholder fill of a lowered `media` node. */
data class ResolvedMedia(
    val assetId: String,
    val url: String = "",
    val kind: MediaKind = MediaKind.Image,
    val fillMode: ImageScaleMode = ImageScaleMode.Fill,
    /** Normalized 0..1 crop focus inside the asset. */
    val focalPoint: DesignPoint? = null,
    /** Alt text resolved through the i18n path. */
    val altText: String = "",
    val posterAssetId: String = "",
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = true,
    /** Intrinsic asset size for Hug sizing; null when the asset does not declare one. */
    val intrinsicWidth: Double? = null,
    val intrinsicHeight: Double? = null,
)

/** Mask carried on the resolved node; the legacy `isMask` flag normalizes to Alpha. */
data class ResolvedMask(
    val type: MaskType = MaskType.Alpha,
    /** Resolved ids clipped by this mask. Empty = legacy Figma semantics (following siblings). */
    val appliesTo: List<String> = emptyList(),
    /** Resolved id of the node providing the mask geometry; empty = the carrying node is its own source. */
    val source: String = "",
)

data class ResolvedAutoLayout(
    val mode: LayoutMode = LayoutMode.None,
    val gap: Double = 0.0,
    val gapAuto: Boolean = false,
    val crossGap: Double = 0.0,
    val wrap: Boolean = false,
    val paddingTop: Double = 0.0,
    val paddingRight: Double = 0.0,
    val paddingBottom: Double = 0.0,
    val paddingLeft: Double = 0.0,
    val alignItems: AlignItems = AlignItems.Start,
    val justifyContent: JustifyContent = JustifyContent.Start,
    /** Which text baseline [AlignItems.Baseline] aligns on. */
    val baseline: BaselineAlign = BaselineAlign.First,
    val clipsContent: Boolean = false,
    val columns: List<GridTrack> = emptyList(),
    val rows: List<GridTrack> = emptyList(),
    val columnGap: Double = 0.0,
    val rowGap: Double = 0.0,
    /** Template for implicit grid rows when [rows] is empty. */
    val implicitRows: GridTrack? = null,
    /** Minimum implicit row size; a resolved literal so the layout engine reads it via `.orZero`. */
    val implicitRowMin: Bindable<Double>? = null,
)

sealed interface ResolvedPaint {
    val opacity: Double

    data class Solid(
        val color: DesignColor,
        override val opacity: Double = 1.0,
    ) : ResolvedPaint

    data class Gradient(
        val gradientType: GradientKind,
        val from: DesignPoint,
        val to: DesignPoint,
        val stops: List<ResolvedGradientStop>,
        override val opacity: Double = 1.0,
    ) : ResolvedPaint

    data class Image(
        val assetId: String,
        val url: String,
        val scaleMode: ImageScaleMode,
        override val opacity: Double = 1.0,
    ) : ResolvedPaint

    data class Unknown(
        val rawType: String,
        override val opacity: Double = 1.0,
    ) : ResolvedPaint
}

data class ResolvedGradientStop(
    val position: Double,
    val color: DesignColor,
)

/** One region of a vector network with its own lowered outline and resolved fills (region paint). */
data class ResolvedRegionPaint(
    val geometry: PathGeometry,
    val paints: List<ResolvedPaint>,
)

data class ResolvedStrokes(
    val paints: List<ResolvedPaint> = emptyList(),
    val weight: Double = 1.0,
    val weightTop: Double? = null,
    val weightRight: Double? = null,
    val weightBottom: Double? = null,
    val weightLeft: Double? = null,
    val align: StrokeAlign = StrokeAlign.Inside,
    val dashPattern: List<Double> = emptyList(),
    /** Line-cap: "butt" | "round" | "square". */
    val cap: String = "butt",
    /** Line-join: "miter" | "round" | "bevel". */
    val join: String = "miter",
)

sealed interface ResolvedEffect {
    data class DropShadow(
        val color: DesignColor,
        val offset: DesignPoint,
        val blur: Double,
        val spread: Double,
    ) : ResolvedEffect

    data class InnerShadow(
        val color: DesignColor,
        val offset: DesignPoint,
        val blur: Double,
        val spread: Double,
    ) : ResolvedEffect

    data class LayerBlur(val radius: Double) : ResolvedEffect

    data class BackgroundBlur(val radius: Double) : ResolvedEffect
}

data class ResolvedCornerRadius(
    val topLeft: Double = 0.0,
    val topRight: Double = 0.0,
    val bottomRight: Double = 0.0,
    val bottomLeft: Double = 0.0,
    val smoothing: Double = 0.0,
) {
    val isZero: Boolean
        get() = topLeft == 0.0 && topRight == 0.0 && bottomRight == 0.0 && bottomLeft == 0.0
}

data class ResolvedText(
    val characters: String,
    val style: ResolvedTextStyle,
    val autoResize: TextAutoResize = TextAutoResize.None,
    val truncate: TextTruncate? = null,
    val ranges: List<ResolvedTextRange> = emptyList(),
    /** Hyperlink ranges; offsets follow the same clamping rules as [ranges]. */
    val links: List<TextLink> = emptyList(),
    val list: TextListSettings = TextListSettings(),
    /** i18n resource key the characters came from; "" for raw/legacy content. */
    val contentKey: String = "",
)

data class ResolvedTextStyle(
    val fontFamily: String = "",
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val fontSize: Double = 14.0,
    /** Line height in px, already resolved from percent against font size. */
    val lineHeight: Double = 0.0,
    /** Letter spacing in px. */
    val letterSpacing: Double = 0.0,
    val paragraphSpacing: Double = 0.0,
    /** First-line indent of each paragraph, px. */
    val paragraphIndent: Double = 0.0,
    val textAlignHorizontal: TextAlignHorizontal = TextAlignHorizontal.Left,
    val textAlignVertical: TextAlignVertical = TextAlignVertical.Top,
    val textCase: TextCase = TextCase.None,
    val textDecoration: TextDecorationKind = TextDecorationKind.None,
    val decorationStyle: TextDecorationStyle = TextDecorationStyle.Solid,
    /** null = decoration follows the glyph color. */
    val decorationColor: DesignColor? = null,
    /** Decoration thickness in px; null = automatic. */
    val decorationThickness: Double? = null,
    val decorationSkipInk: Boolean = false,
    val textPosition: TextScriptPosition = TextScriptPosition.None,
    val leadingTrim: LeadingTrim = LeadingTrim.None,
    val hangingPunctuation: Boolean = false,
    val hangingList: Boolean = false,
    /** OpenType features by tag. */
    val fontFeatures: Map<String, Boolean> = emptyMap(),
    /** Variable font axes. */
    val variableAxes: Map<String, Double> = emptyMap(),
)

data class ResolvedTextRange(
    val start: Int,
    val end: Int,
    val style: ResolvedTextStyle,
    val fills: List<ResolvedPaint>? = null,
)
