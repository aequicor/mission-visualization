package io.aequicor.visualization.engine.ir.model

/** Designer-to-developer handoff metadata; never affects geometry or rendering. */
data class DesignHandoff(
    val annotations: List<DesignAnnotation> = emptyList(),
    val measurements: List<DesignMeasurement> = emptyList(),
    val code: CodeHints? = null,
)

data class DesignAnnotation(
    val id: String = "",
    val target: String = "",
    val text: String,
    val audience: String = "",
)

data class DesignMeasurement(
    val from: String,
    val to: String,
    val axis: MeasureAxis,
    val value: Bindable<Double>? = null,
)

enum class MeasureAxis { Inline, Block }

data class CodeHints(
    val framework: String = "",
    val componentHint: String = "",
)

data class ExportSetting(
    val format: ExportFormat,
    val scale: Double = 1.0,
    val suffix: String = "",
)

enum class ExportFormat { Png, Jpg, Svg, Pdf }
