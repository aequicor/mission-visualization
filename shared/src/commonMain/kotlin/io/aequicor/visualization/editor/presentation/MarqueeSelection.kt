package io.aequicor.visualization.editor.presentation

/** Rectangle in document coordinates, normalized so left <= right and top <= bottom. */
data class DocumentRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top

    fun intersects(other: DocumentRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    companion object {
        fun fromCorners(x1: Double, y1: Double, x2: Double, y2: Double): DocumentRect =
            DocumentRect(
                left = minOf(x1, x2),
                top = minOf(y1, y2),
                right = maxOf(x1, x2),
                bottom = maxOf(y1, y2),
            )
    }
}

/** Hit-testable layer bounds for marquee selection. Locked/hidden rows are skipped. */
data class SelectableBounds(
    val id: String,
    val bounds: DocumentRect,
    val locked: Boolean = false,
    val visible: Boolean = true,
)

fun marqueeSelection(
    marquee: DocumentRect,
    candidates: List<SelectableBounds>,
): Set<String> =
    candidates
        .asSequence()
        .filter { it.visible && !it.locked && it.bounds.intersects(marquee) }
        .map { it.id }
        .toSet()
