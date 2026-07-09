package io.aequicor.visualization.editor.presentation

/**
 * Pure, Compose-free resize geometry for the canvas handles. Kept out of the UI so the
 * eight handle semantics can be unit-tested headlessly against the design-book cases.
 *
 * All math derives from fixed drag-start references — the box size at press
 * ([baseWidth]/[baseHeight]) plus the cumulative pointer displacement in document units
 * ([docDx]/[docDy]) — so a resize sets absolute geometry each frame rather than
 * compounding on the already-mutated live node.
 */
enum class ResizeHandle { TopLeft, Top, TopRight, Left, Right, BottomLeft, Bottom, BottomRight }

val ResizeHandle.movesLeft: Boolean
    get() = this == ResizeHandle.TopLeft || this == ResizeHandle.Left || this == ResizeHandle.BottomLeft

val ResizeHandle.movesRight: Boolean
    get() = this == ResizeHandle.TopRight || this == ResizeHandle.Right || this == ResizeHandle.BottomRight

val ResizeHandle.movesTop: Boolean
    get() = this == ResizeHandle.TopLeft || this == ResizeHandle.Top || this == ResizeHandle.TopRight

val ResizeHandle.movesBottom: Boolean
    get() = this == ResizeHandle.BottomLeft || this == ResizeHandle.Bottom || this == ResizeHandle.BottomRight

val ResizeHandle.isCorner: Boolean
    get() = (movesLeft || movesRight) && (movesTop || movesBottom)

/**
 * New size plus the parent-relative position delta of a resize. [dx]/[dy] are the shift
 * of the authored top-left; they are non-zero only for handles that move the left/top
 * edge (the opposite edge stays pinned). The parent does not move during a resize, so
 * the change in authored position equals the change in absolute origin.
 */
data class ResizeResult(
    val width: Double,
    val height: Double,
    val dx: Double,
    val dy: Double,
)

/**
 * Resolves a handle drag into absolute geometry.
 *
 * Rules (design-book §3):
 * - dragging an edge changes only the axis that edge controls;
 * - dragging left/top moves the origin and the size so the opposite side stays fixed;
 * - dragging right/bottom never moves the origin;
 * - a corner changes both axes;
 * - width/height are clamped into `[minWidth, maxWidth]` / `[minHeight, maxHeight]`, and —
 *   crucially — when a left/top edge is dragged past the opposite edge the position stops at
 *   `oppositeEdge - minSize` instead of sliding off (the delta is derived from the *clamped*
 *   size, not the raw pointer travel), and when the size caps at [maxWidth]/[maxHeight] the
 *   origin stops there too (so a max-bound edge doesn't shove the opposite edge);
 * - an edge whose `canMove*` flag is `false` is inert — it contributes no size change and no
 *   origin shift, so dragging a direction the node cannot grow in (e.g. the flow-pinned start
 *   edge of an auto-layout child) leaves the box untouched;
 * - [lockRatio] on a corner preserves the start aspect ratio (only when both of the corner's
 *   directions are movable).
 */
fun computeResize(
    baseWidth: Double,
    baseHeight: Double,
    handle: ResizeHandle,
    docDx: Double,
    docDy: Double,
    minWidth: Double = 1.0,
    minHeight: Double = 1.0,
    maxWidth: Double? = null,
    maxHeight: Double? = null,
    canMoveLeft: Boolean = true,
    canMoveRight: Boolean = true,
    canMoveTop: Boolean = true,
    canMoveBottom: Boolean = true,
    lockRatio: Boolean = false,
): ResizeResult {
    val movesLeft = handle.movesLeft && canMoveLeft
    val movesRight = handle.movesRight && canMoveRight
    val movesTop = handle.movesTop && canMoveTop
    val movesBottom = handle.movesBottom && canMoveBottom

    var width = baseWidth
    var height = baseHeight
    if (movesRight) width = baseWidth + docDx
    if (movesLeft) width = baseWidth - docDx
    if (movesBottom) height = baseHeight + docDy
    if (movesTop) height = baseHeight - docDy
    width = clamp(width, minWidth, maxWidth)
    height = clamp(height, minHeight, maxHeight)

    // Aspect lock only when the grabbed corner can actually move on both axes; otherwise a
    // pinned edge would fight the ratio and distort the free axis.
    if (lockRatio && baseHeight > 0.0 && baseWidth > 0.0 && handle.isCorner &&
        (movesLeft || movesRight) && (movesTop || movesBottom)
    ) {
        val ratio = baseWidth / baseHeight
        // Drive height from the (already clamped) width, then re-clamp.
        height = clamp(width / ratio, minHeight, maxHeight)
        width = clamp(height * ratio, minWidth, maxWidth)
    }

    // Position deltas come from the *clamped* size so the opposite edge stays pinned even after
    // width/height bottoms out at the minimum or caps at the maximum. A blocked edge left the
    // size at its base, so the delta is naturally 0.
    val dx = if (movesLeft) baseWidth - width else 0.0
    val dy = if (movesTop) baseHeight - height else 0.0
    return ResizeResult(width = width, height = height, dx = dx, dy = dy)
}

/** Clamps [value] into `[min, max]`; a [max] below [min] is raised to [min] so min always wins. */
private fun clamp(value: Double, min: Double, max: Double?): Double {
    val lower = value.coerceAtLeast(min)
    return if (max != null) lower.coerceAtMost(max.coerceAtLeast(min)) else lower
}
