package io.aequicor.visualization.engine.ir.model

data class DesignMask(
    val type: MaskType = MaskType.Alpha,
    /** Node ids clipped by this mask. Empty = legacy Figma semantics (following siblings). */
    val appliesTo: List<String> = emptyList(),
    /** Id of the node providing the mask geometry; empty = the mask-carrying node is its own source. */
    val source: String = "",
)

enum class MaskType { Alpha, Vector, Luminance }
