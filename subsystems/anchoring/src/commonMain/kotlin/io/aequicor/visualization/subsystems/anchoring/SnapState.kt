package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs

/**
 * Per-axis sticky-snap latch carried across the frames of one move drag: the document coordinate
 * each axis is currently snapped to, or null when that axis is free. Pure state — the caller
 * threads it through [solveMoveSnap] and stores the returned [MoveSnapOutput.state].
 */
data class MoveSnapState(val latchX: Double? = null, val latchY: Double? = null) {
    companion object {
        val None = MoveSnapState()
    }
}

/**
 * Result of one [solveMoveSnap] frame: the per-axis correction to add to the raw drag, whether each
 * axis is magnetically snapped **this** frame ([snappedX]/[snappedY] — drives the solid center line),
 * the overlay to draw, and the next [MoveSnapState] to feed back in.
 */
data class MoveSnapOutput(
    val dx: Double,
    val dy: Double,
    val snappedX: Boolean,
    val snappedY: Boolean,
    val guides: List<AnchorGuide>,
    val spacing: List<SpacingBar>,
    val state: MoveSnapState,
)

/**
 * Sticky (hysteresis) magnetic snap for a free-move drag — [computeAnchors] with a catch/release
 * radius pair for a stronger "magnetic" feel. An axis engages when one of the box's source lines
 * comes within [catch] of an anchor; it then **holds**, searching at the wider [release] radius, and
 * only lets go once every source line drifts beyond [release] of the latched line (design-book §18).
 *
 * [allowX]/[allowY] gate each axis — pass false for an axis a Shift lock has zeroed, which also drops
 * that axis's latch. Pure and deterministic: thread [prior] in each frame and store the returned
 * [MoveSnapOutput.state]. Feed the RAW (pointer-driven) box, never a previously snapped one, or an
 * axis would measure its release distance against itself and stick forever.
 */
fun solveMoveSnap(
    moved: SnapBox,
    containers: List<SnapBox>,
    siblings: List<SnapBox>,
    catch: Double,
    release: Double,
    allowX: Boolean,
    allowY: Boolean,
    prior: MoveSnapState,
): MoveSnapOutput {
    val rel = maxOf(release, catch)
    val x = if (allowX) resolveAxisSticky(AxisX, moved, containers, siblings, catch, rel, prior.latchX) else null
    val y = if (allowY) resolveAxisSticky(AxisY, moved, containers, siblings, catch, rel, prior.latchY) else null

    val dx = x?.delta ?: 0.0
    val dy = y?.delta ?: 0.0
    val snapped = moved.translate(dx, dy)

    val guides = ArrayList<AnchorGuide>()
    val spacing = ArrayList<SpacingBar>()
    x?.let { buildAxisOverlay(it, alongX = true, snapped, guides, spacing) }
    y?.let { buildAxisOverlay(it, alongX = false, snapped, guides, spacing) }
    return MoveSnapOutput(
        dx = dx,
        dy = dy,
        snappedX = x != null,
        snappedY = y != null,
        guides = guides,
        spacing = spacing,
        state = MoveSnapState(latchX = x?.target, latchY = y?.target),
    )
}

/**
 * Resolves one axis with hysteresis: while a [priorLatch] is held and still within [release] of the
 * box, search at the wide [release] radius (sticky); otherwise search at the tighter [catch] radius.
 * The winner's target becomes the next latch (null → released).
 */
private fun resolveAxisSticky(
    axis: BoxAxis,
    moved: SnapBox,
    containers: List<SnapBox>,
    siblings: List<SnapBox>,
    catch: Double,
    release: Double,
    priorLatch: Double?,
): AxisWinner? {
    val holding = priorLatch != null && abs(priorLatch - nearestSource(axis, moved, priorLatch)) <= release
    val radius = if (holding) release else catch
    return resolveAxis(axis, moved, containers, siblings, radius)
}

/** The box's source line (low edge / center / high edge) closest to [target] on [axis]. */
private fun nearestSource(axis: BoxAxis, moved: SnapBox, target: Double): Double =
    listOf(axis.lo(moved), axis.center(moved), axis.hi(moved)).minBy { abs(target - it) }
