package io.aequicor.visualization.subsystems.diagrams.model

import kotlin.jvm.JvmInline

/**
 * A device-independent ARGB color, packed as `0xAARRGGBB` in the low 32 bits.
 * Pure-Kotlin analog of a UI color; the Compose adapter maps it to `Color`.
 */
@JvmInline
value class DiagramColor(val argb: ULong) {
    val alpha: Int get() = ((argb shr 24) and 0xFFu).toInt()
    val red: Int get() = ((argb shr 16) and 0xFFu).toInt()
    val green: Int get() = ((argb shr 8) and 0xFFu).toInt()
    val blue: Int get() = (argb and 0xFFu).toInt()

    companion object {
        val Transparent: DiagramColor = DiagramColor(0x00000000u)
        val Black: DiagramColor = DiagramColor(0xFF000000u)
        val White: DiagramColor = DiagramColor(0xFFFFFFFFu)

        fun fromArgb(alpha: Int, red: Int, green: Int, blue: Int): DiagramColor = DiagramColor(
            ((alpha.toULong() and 0xFFu) shl 24) or
                ((red.toULong() and 0xFFu) shl 16) or
                ((green.toULong() and 0xFFu) shl 8) or
                (blue.toULong() and 0xFFu),
        )

        fun fromRgb(red: Int, green: Int, blue: Int): DiagramColor =
            fromArgb(alpha = 0xFF, red = red, green = green, blue = blue)
    }
}

/** Stroke dash pattern. */
enum class DiagramStrokePattern { SOLID, DASHED, DOTTED }

/** How polyline corners of nodes and routed edges are drawn. */
enum class DiagramCornerStyle { SHARP, ROUNDED, CURVED }

/**
 * Visual style shared by nodes and edges. `null` colors mean "theme default" —
 * the renderer resolves them from editor theme tokens.
 */
data class DiagramStyle(
    val fill: DiagramColor? = null,
    val stroke: DiagramColor? = null,
    val strokeWidth: Double = 1.0,
    val pattern: DiagramStrokePattern = DiagramStrokePattern.SOLID,
    val opacity: Double = 1.0,
    val cornerStyle: DiagramCornerStyle = DiagramCornerStyle.ROUNDED,
    val sketch: Boolean = false,
    val shadow: Boolean = false,
) {
    init {
        require(strokeWidth >= 0.0) { "strokeWidth must be >= 0, got $strokeWidth" }
        require(opacity in 0.0..1.0) { "opacity must be in 0..1, got $opacity" }
    }

    companion object {
        val Default: DiagramStyle = DiagramStyle()
    }
}

/** Arrowhead (line-end marker) kinds, draw.io-parity set including UML and ER notation. */
enum class DiagramArrowheadKind {
    NONE,
    OPEN,
    BLOCK,
    BLOCK_FILLED,
    DIAMOND,
    DIAMOND_FILLED,
    TRIANGLE,
    TRIANGLE_FILLED,
    OVAL,
    OVAL_FILLED,
    CROSS,
    DASH,
    ER_ONE,
    ER_MANY,
    ER_ONE_OR_MANY,
    ER_ZERO_OR_ONE,
    ER_ZERO_OR_MANY,
}

/**
 * Arrowhead at one end of an edge.
 *
 * @param size marker size in document units.
 * @param inset distance the line end is pulled back from the attachment point.
 */
data class DiagramArrowhead(
    val kind: DiagramArrowheadKind = DiagramArrowheadKind.NONE,
    val size: Double = 8.0,
    val inset: Double = 0.0,
) {
    init {
        require(size >= 0.0) { "size must be >= 0, got $size" }
    }

    companion object {
        val None: DiagramArrowhead = DiagramArrowhead()
        val Open: DiagramArrowhead = DiagramArrowhead(DiagramArrowheadKind.OPEN)
    }
}

/** Edge routing algorithm selector (routing itself lives in a separate layer). */
enum class DiagramRoutingStyle {
    STRAIGHT,
    ORTHOGONAL,
    SIMPLE,
    ISOMETRIC,
    CURVED,
    ENTITY_RELATION,
}

/** How an edge is drawn where it crosses another edge. */
enum class LineJumpStyle { NONE, ARC, GAP, SHARP }

/** Connection mode: a plain line, a double-stroked link, or a filled arrow shape. */
enum class DiagramConnectionMode { LINE, LINK, ARROW }

/**
 * A text label on a node or edge. Plain string in the model; the renderer feeds it to
 * the typography subsystem (rich text) and interprets [markdown] when set.
 */
data class DiagramLabel(
    val text: String,
    val markdown: Boolean = false,
)
