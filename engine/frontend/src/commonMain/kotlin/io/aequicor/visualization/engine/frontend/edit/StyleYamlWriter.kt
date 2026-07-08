package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignEffect
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.DesignStrokes
import io.aequicor.visualization.engine.ir.model.GradientKind
import io.aequicor.visualization.engine.ir.model.GradientStop
import io.aequicor.visualization.engine.ir.model.ImageScaleMode
import io.aequicor.visualization.engine.ir.model.StrokeAlign
import io.aequicor.visualization.engine.ir.model.bindable

/**
 * Serializes style list values (fills, strokes, effects) from the IR back into
 * [YamlPayload] sequences for surgical write-back. It is the faithful inverse of the
 * style block readers: design-token colors round-trip as `token:` refs, literals as
 * `#hex`, and default attributes (opacity 1, blendMode normal, visible true) are
 * omitted so re-reading yields the same IR. Lists are marked [YamlPayload.Sequence.replaceWhole]
 * so the writer rewrites the whole existing list, not just its first item.
 */
internal object StyleYamlWriter {

    fun fills(paints: List<DesignPaint>): YamlPayload.Sequence =
        YamlPayload.Sequence(paints.map(::paintPayload), replaceWhole = true)

    fun strokes(strokes: DesignStrokes): YamlPayload.Sequence {
        val shared = buildList {
            add("weight" to num(strokes.weight))
            if (strokes.align != StrokeAlign.Inside) add("position" to str(strokeAlignToken(strokes.align)))
            if (strokes.dashPattern.isNotEmpty()) {
                add("dash" to YamlPayload.Sequence(strokes.dashPattern.map { numOf(it) }))
            }
            if (strokes.cap != "butt") add("caps" to str(strokes.cap))
            if (strokes.join != "miter") add("joins" to str(strokes.join))
        }
        // readStrokes hoists weight/position/dash/caps/joins from any item, so the shared
        // attributes ride on the first stroke paint; further paints carry only their color.
        val items = if (strokes.paints.isEmpty()) {
            listOf(YamlPayload.Mapping(shared))
        } else {
            strokes.paints.mapIndexed { index, paint ->
                val color = colorEntries(strokePaintColor(paint))
                YamlPayload.Mapping(if (index == 0) color + shared else color)
            }
        }
        return YamlPayload.Sequence(items, replaceWhole = true)
    }

    fun effects(effects: List<DesignEffect>): YamlPayload.Sequence =
        YamlPayload.Sequence(effects.map(::effectPayload), replaceWhole = true)

    // --- paints ---

    private fun paintPayload(paint: DesignPaint): YamlPayload = when (paint) {
        is DesignPaint.Solid -> YamlPayload.Mapping(colorEntries(paint.color) + commonPaintEntries(paint))
        is DesignPaint.Gradient -> YamlPayload.Mapping(
            buildList {
                add("type" to str(gradientKindToken(paint.gradientType)))
                add("from" to point(paint.from))
                add("to" to point(paint.to))
                add("stops" to YamlPayload.Sequence(paint.stops.map(::stopPayload)))
            } + commonPaintEntries(paint),
        )
        is DesignPaint.Image -> YamlPayload.Mapping(
            buildList {
                add("type" to str("image"))
                add("asset" to str(paint.assetId))
                if (paint.scaleMode != ImageScaleMode.Fill) add("fillMode" to str(fillModeToken(paint.scaleMode)))
                paint.focalPoint?.let { add("focalPoint" to point(it)) }
                if (paint.replaceable) add("replaceable" to bool(true))
            } + commonPaintEntries(paint),
        )
        is DesignPaint.Video -> YamlPayload.Mapping(
            buildList {
                add("type" to str("video"))
                add("asset" to str(paint.assetId))
                if (paint.scaleMode != ImageScaleMode.Fill) add("fillMode" to str(fillModeToken(paint.scaleMode)))
                paint.focalPoint?.let { add("focalPoint" to point(it)) }
                if (paint.posterAssetId.isNotEmpty()) add("poster" to str(paint.posterAssetId))
                if (paint.autoplay) add("autoplay" to bool(true))
                if (paint.loop) add("loop" to bool(true))
                if (!paint.muted) add("muted" to bool(false))
            } + commonPaintEntries(paint),
        )
        is DesignPaint.Unknown -> YamlPayload.Mapping(listOf("type" to str(paint.rawType)) + commonPaintEntries(paint))
    }

    private fun stopPayload(stop: GradientStop): YamlPayload =
        YamlPayload.Mapping(listOf("position" to numOf(stop.position)) + colorEntries(stop.color))

    /** `visible`/`opacity`/`blendMode`, emitted only when they differ from the defaults. */
    private fun commonPaintEntries(paint: DesignPaint): List<Pair<String, YamlPayload>> = buildList {
        if (paint.visible.literalFalse()) add("visible" to bool(false))
        paint.opacity.literalOrDefault(1.0)?.let { if (it != 1.0) add("opacity" to numOf(it)) }
        if (paint.blendMode != "normal") add("blendMode" to str(paint.blendMode))
    }

    private fun strokePaintColor(paint: DesignPaint): Bindable<DesignColor> = when (paint) {
        is DesignPaint.Solid -> paint.color
        is DesignPaint.Gradient -> paint.stops.firstOrNull()?.color ?: DesignColor.Black.bindable()
        else -> DesignColor.Black.bindable()
    }

    // --- effects ---

    private fun effectPayload(effect: DesignEffect): YamlPayload = when (effect) {
        is DesignEffect.DropShadow -> shadowPayload("dropShadow", effect.color, effect.offset, effect.blur, effect.spread, effect.visible)
        is DesignEffect.InnerShadow -> shadowPayload("innerShadow", effect.color, effect.offset, effect.blur, effect.spread, effect.visible)
        is DesignEffect.LayerBlur -> blurPayload("layerBlur", effect.radius, effect.visible)
        is DesignEffect.BackgroundBlur -> blurPayload("backgroundBlur", effect.radius, effect.visible)
        is DesignEffect.Unknown -> YamlPayload.Mapping(
            buildList {
                add("type" to str(effect.rawType))
                if (effect.visible.literalFalse()) add("visible" to bool(false))
            },
        )
    }

    private fun shadowPayload(
        type: String,
        color: Bindable<DesignColor>,
        offset: DesignPoint,
        blur: Double,
        spread: Double,
        visible: Bindable<Boolean>,
    ): YamlPayload = YamlPayload.Mapping(
        buildList {
            add("type" to str(type))
            addAll(colorEntries(color))
            if (offset.x != 0.0) add("x" to numOf(offset.x))
            if (offset.y != 0.0) add("y" to numOf(offset.y))
            if (blur != 0.0) add("blur" to numOf(blur))
            if (spread != 0.0) add("spread" to numOf(spread))
            if (visible.literalFalse()) add("visible" to bool(false))
        },
    )

    private fun blurPayload(type: String, radius: Double, visible: Bindable<Boolean>): YamlPayload =
        YamlPayload.Mapping(
            buildList {
                add("type" to str(type))
                add("blur" to numOf(radius))
                if (visible.literalFalse()) add("visible" to bool(false))
            },
        )

    // --- colors ---

    /** A design-token color round-trips as `token: id`; a literal as `color: "#hex"`. */
    private fun colorEntries(color: Bindable<DesignColor>): List<Pair<String, YamlPayload>> = when (color) {
        is Bindable.VarRef -> listOf("token" to str(color.id))
        is Bindable.Value -> listOf("color" to str(color.value.toHex()))
        else -> emptyList()
    }

    private fun DesignColor.toHex(): String {
        fun byte(v: Int): String = v.toString(16).padStart(2, '0')
        val rgb = "#${byte(red)}${byte(green)}${byte(blue)}"
        return if (alpha == 255) rgb else "$rgb${byte(alpha)}"
    }

    // --- payload helpers ---

    private fun str(value: String): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Str(value))
    private fun bool(value: Boolean): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Bool(value))
    private fun numOf(value: Double): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Num(value))
    private fun num(value: Bindable<Double>): YamlPayload = when (value) {
        is Bindable.VarRef -> YamlPayload.Scalar(YamlScalarValue.TokenRef(value.id))
        is Bindable.Value -> numOf(value.value)
        else -> numOf(0.0)
    }

    private fun point(p: DesignPoint): YamlPayload =
        YamlPayload.Mapping(listOf("x" to numOf(p.x), "y" to numOf(p.y)))

    private fun Bindable<Boolean>.literalFalse(): Boolean = this is Bindable.Value && !value
    private fun Bindable<Double>.literalOrDefault(default: Double): Double? =
        (this as? Bindable.Value)?.value ?: default

    private fun gradientKindToken(kind: GradientKind): String = when (kind) {
        GradientKind.Linear -> "linearGradient"
        GradientKind.Radial -> "radialGradient"
        GradientKind.Angular -> "angularGradient"
        GradientKind.Diamond -> "diamondGradient"
    }

    private fun strokeAlignToken(align: StrokeAlign): String = when (align) {
        StrokeAlign.Inside -> "inside"
        StrokeAlign.Center -> "center"
        StrokeAlign.Outside -> "outside"
    }

    private fun fillModeToken(mode: ImageScaleMode): String = when (mode) {
        ImageScaleMode.Fill -> "fill"
        ImageScaleMode.Fit -> "fit"
        ImageScaleMode.Crop -> "crop"
        ImageScaleMode.Tile -> "tile"
        ImageScaleMode.Stretch -> "stretch"
    }
}
