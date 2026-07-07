package io.aequicor.visualization.engine.ir.model

data class DesignMask(
    val type: MaskType = MaskType.Alpha,
    /** Node ids clipped by this mask. Empty = legacy Figma semantics (following siblings). */
    val appliesTo: List<String> = emptyList(),
)

enum class MaskType { Alpha, Vector, Luminance }
