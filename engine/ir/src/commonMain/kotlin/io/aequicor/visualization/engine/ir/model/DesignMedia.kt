package io.aequicor.visualization.engine.ir.model

enum class MediaKind { Image, Video }

/** Payload of a `media` node: a placed image or video referencing an asset. */
data class DesignMedia(
    val assetId: Bindable<String>,
    val kind: MediaKind = MediaKind.Image,
    val fillMode: ImageScaleMode = ImageScaleMode.Fill,
    /** Normalized 0..1 crop focus inside the asset. */
    val focalPoint: DesignPoint? = null,
    val alt: TextContent? = null,
    val replaceable: Boolean = false,
    val opacity: Bindable<Double> = 1.0.bindable(),
    val blendMode: String = "normal",
    val posterAssetId: Bindable<String> = "".bindable(),
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = true,
)

/**
 * Payload of a `table` node. Children are row frames; row children are cells.
 * The resolver lowers a table to a grid layout.
 */
data class DesignTable(
    /** Empty = equal Flex(1.0) track per widest row. */
    val columns: List<GridTrack> = emptyList(),
    val headerRows: Int = 1,
    val rowGap: Bindable<Double> = 0.0.bindable(),
    val columnGap: Bindable<Double> = 0.0.bindable(),
)
