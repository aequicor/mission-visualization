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

    fun contains(other: DocumentRect): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun strictlyContains(other: DocumentRect): Boolean = contains(other) && this != other

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
    excludedIds: Set<String> = emptySet(),
): Set<String> =
    candidates
        .asSequence()
        .filter {
            it.id !in excludedIds &&
                it.visible &&
                !it.locked &&
                it.bounds.intersects(marquee) &&
                // A drag made inside a component must not select every enclosing visual
                // layer behind it. Equal bounds remain selectable; only a strictly larger
                // candidate that contains the entire marquee is treated as context/backdrop.
                !it.bounds.strictlyContains(marquee)
        }
        .map { it.id }
        .toSet()
