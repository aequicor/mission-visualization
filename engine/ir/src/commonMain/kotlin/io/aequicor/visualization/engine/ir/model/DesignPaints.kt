package io.aequicor.visualization.engine.ir.model

/** One paint layer; `fills` is always a bottom-to-top list of layers. */
sealed interface DesignPaint {
    val visible: Bindable<Boolean>
    val opacity: Bindable<Double>
    val blendMode: String

    data class Solid(
        val color: Bindable<DesignColor>,
        override val visible: Bindable<Boolean> = true.bindable(),
        override val opacity: Bindable<Double> = 1.0.bindable(),
        override val blendMode: String = "normal",
    ) : DesignPaint

    data class Gradient(
        val gradientType: GradientKind,
        val from: DesignPoint = DesignPoint(0.0, 0.0),
        val to: DesignPoint = DesignPoint(0.0, 1.0),
        val stops: List<GradientStop> = emptyList(),
        override val visible: Bindable<Boolean> = true.bindable(),
        override val opacity: Bindable<Double> = 1.0.bindable(),
        override val blendMode: String = "normal",
    ) : DesignPaint

    data class Image(
        val assetId: String,
        val scaleMode: ImageScaleMode = ImageScaleMode.Fill,
        /** Normalized 0..1 crop focus inside the asset. */
        val focalPoint: DesignPoint? = null,
        val replaceable: Boolean = false,
        override val visible: Bindable<Boolean> = true.bindable(),
        override val opacity: Bindable<Double> = 1.0.bindable(),
        override val blendMode: String = "normal",
    ) : DesignPaint

    data class Video(
        val assetId: String,
        val scaleMode: ImageScaleMode = ImageScaleMode.Fill,
        val focalPoint: DesignPoint? = null,
        val posterAssetId: String = "",
        val autoplay: Boolean = false,
        val loop: Boolean = false,
        val muted: Boolean = true,
        override val visible: Bindable<Boolean> = true.bindable(),
        override val opacity: Bindable<Double> = 1.0.bindable(),
        override val blendMode: String = "normal",
    ) : DesignPaint

    /** Unknown paint type: readers render a fallback instead of failing. */
    data class Unknown(
        val rawType: String,
        override val visible: Bindable<Boolean> = true.bindable(),
        override val opacity: Bindable<Double> = 1.0.bindable(),
        override val blendMode: String = "normal",
    ) : DesignPaint
}

enum class GradientKind { Linear, Radial, Angular, Diamond }

data class GradientStop(
    val position: Double,
    val color: Bindable<DesignColor>,
)

enum class ImageScaleMode { Fill, Fit, Crop, Tile, Stretch }

data class DesignStrokes(
    val paints: List<DesignPaint> = emptyList(),
    val weight: Bindable<Double> = 1.0.bindable(),
    val weightPerSide: DesignInsets? = null,
    val align: StrokeAlign = StrokeAlign.Inside,
    val dashPattern: List<Double> = emptyList(),
    val cap: String = "butt",
    val join: String = "miter",
)

enum class StrokeAlign { Inside, Center, Outside }

sealed interface DesignEffect {
    val visible: Bindable<Boolean>

    data class DropShadow(
        val color: Bindable<DesignColor>,
        val offset: DesignPoint = DesignPoint(),
        val blur: Bindable<Double> = 0.0.bindable(),
        val spread: Bindable<Double> = 0.0.bindable(),
        override val visible: Bindable<Boolean> = true.bindable(),
    ) : DesignEffect

    data class InnerShadow(
        val color: Bindable<DesignColor>,
        val offset: DesignPoint = DesignPoint(),
        val blur: Bindable<Double> = 0.0.bindable(),
        val spread: Bindable<Double> = 0.0.bindable(),
        override val visible: Bindable<Boolean> = true.bindable(),
    ) : DesignEffect

    data class LayerBlur(
        val radius: Bindable<Double>,
        override val visible: Bindable<Boolean> = true.bindable(),
    ) : DesignEffect

    data class BackgroundBlur(
        val radius: Bindable<Double>,
        override val visible: Bindable<Boolean> = true.bindable(),
    ) : DesignEffect

    data class Unknown(
        val rawType: String,
        override val visible: Bindable<Boolean> = true.bindable(),
    ) : DesignEffect
}

/** Per-corner radii; a plain number in JSON is shorthand for all four corners. */
data class DesignCornerRadius(
    val topLeft: Bindable<Double> = 0.0.bindable(),
    val topRight: Bindable<Double> = 0.0.bindable(),
    val bottomRight: Bindable<Double> = 0.0.bindable(),
    val bottomLeft: Bindable<Double> = 0.0.bindable(),
    val smoothing: Double = 0.0,
) {
    companion object {
        fun all(radius: Bindable<Double>): DesignCornerRadius =
            DesignCornerRadius(radius, radius, radius, radius)
    }
}
