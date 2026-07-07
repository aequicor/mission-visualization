package io.aequicor.visualization.designdoc.domain.resolve

import io.aequicor.visualization.designdoc.domain.model.AlignItems
import io.aequicor.visualization.designdoc.domain.model.DesignColor
import io.aequicor.visualization.designdoc.domain.model.DesignConstraints
import io.aequicor.visualization.designdoc.domain.model.DesignLayoutChild
import io.aequicor.visualization.designdoc.domain.model.DesignNodeKind
import io.aequicor.visualization.designdoc.domain.model.DesignPoint
import io.aequicor.visualization.designdoc.domain.model.DesignScroll
import io.aequicor.visualization.designdoc.domain.model.DesignSize
import io.aequicor.visualization.designdoc.domain.model.DesignSizing
import io.aequicor.visualization.designdoc.domain.model.GradientKind
import io.aequicor.visualization.designdoc.domain.model.GridPlacement
import io.aequicor.visualization.designdoc.domain.model.GridTrack
import io.aequicor.visualization.designdoc.domain.model.ImageScaleMode
import io.aequicor.visualization.designdoc.domain.model.JustifyContent
import io.aequicor.visualization.designdoc.domain.model.LayoutMode
import io.aequicor.visualization.designdoc.domain.model.StrokeAlign
import io.aequicor.visualization.designdoc.domain.model.TextAlignHorizontal
import io.aequicor.visualization.designdoc.domain.model.TextAlignVertical
import io.aequicor.visualization.designdoc.domain.model.TextAutoResize
import io.aequicor.visualization.designdoc.domain.model.TextCase
import io.aequicor.visualization.designdoc.domain.model.TextDecorationKind
import io.aequicor.visualization.designdoc.domain.model.TextTruncate

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
    val scroll: DesignScroll = DesignScroll(),
    val children: List<ResolvedNode> = emptyList(),
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
    val clipsContent: Boolean = false,
    val columns: List<GridTrack> = emptyList(),
    val rows: List<GridTrack> = emptyList(),
    val columnGap: Double = 0.0,
    val rowGap: Double = 0.0,
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

data class ResolvedStrokes(
    val paints: List<ResolvedPaint> = emptyList(),
    val weight: Double = 1.0,
    val weightTop: Double? = null,
    val weightRight: Double? = null,
    val weightBottom: Double? = null,
    val weightLeft: Double? = null,
    val align: StrokeAlign = StrokeAlign.Inside,
    val dashPattern: List<Double> = emptyList(),
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
)

data class ResolvedTextStyle(
    val fontFamily: String = "",
    val fontWeight: Int = 400,
    val fontSize: Double = 14.0,
    /** Line height in px, already resolved from percent against font size. */
    val lineHeight: Double = 0.0,
    /** Letter spacing in px. */
    val letterSpacing: Double = 0.0,
    val paragraphSpacing: Double = 0.0,
    val textAlignHorizontal: TextAlignHorizontal = TextAlignHorizontal.Left,
    val textAlignVertical: TextAlignVertical = TextAlignVertical.Top,
    val textCase: TextCase = TextCase.None,
    val textDecoration: TextDecorationKind = TextDecorationKind.None,
)

data class ResolvedTextRange(
    val start: Int,
    val end: Int,
    val style: ResolvedTextStyle,
    val fills: List<ResolvedPaint>? = null,
)
