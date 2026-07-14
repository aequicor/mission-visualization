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
    val parentId: String? = null,
    val container: Boolean = false,
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

/**
 * Adds a container when the marquee fully covers all of its direct selectable children.
 * The child hits stay selected: a whole-panel drag therefore returns the complete hierarchy,
 * including the slightly larger padding/border container around the covered content.
 */
fun includeFullyCoveredContainers(
    marquee: DocumentRect,
    candidates: List<SelectableBounds>,
    hits: Set<String>,
    excludedIds: Set<String> = emptySet(),
): Set<String> {
    val byId = candidates.associateBy { it.id }
    val childrenByParent = candidates.groupBy { it.parentId }
    val result = hits.toMutableSet()

    fun isDescendantOf(id: String, ancestorId: String): Boolean {
        var parentId = byId[id]?.parentId
        while (parentId != null) {
            if (parentId == ancestorId) return true
            parentId = byId[parentId]?.parentId
        }
        return false
    }

    // Candidates are produced parent-first by the layout walk; reverse order adds the deepest
    // complete containers before considering their ancestors.
    candidates.asReversed().forEach { candidate ->
        if (!candidate.container || candidate.id in excludedIds || candidate.locked || !candidate.visible) return@forEach
        val directChildren = childrenByParent[candidate.id]
            .orEmpty()
            .filter { it.visible && !it.locked && it.id !in excludedIds }
        if (directChildren.size < 2 || directChildren.any { !marquee.contains(it.bounds) }) return@forEach
        if (result.none { it == candidate.id || isDescendantOf(it, candidate.id) }) return@forEach

        result += candidate.id
    }
    return result
}
