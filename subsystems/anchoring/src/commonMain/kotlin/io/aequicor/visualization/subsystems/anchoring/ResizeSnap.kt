package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs

/**
 * Which edges a resize handle drags (design-book §3): an edge handle sets one flag, a corner sets
 * one horizontal + one vertical. The editor maps its `ResizeHandle` onto this at the module boundary.
 */
data class MovingEdges(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean)

/** Per-axis resize-snap latch carried across the drag frames: the moving-edge doc target, or null. */
data class ResizeSnapState(val latchX: Double? = null, val latchY: Double? = null) {
    companion object {
        val None = ResizeSnapState()
    }
}

/** The snapped size and whether each dimension matched a neighbour's size (the editor tints the badge). */
data class SizeBadge(
    val width: Double,
    val height: Double,
    val widthMatched: Boolean,
    val heightMatched: Boolean,
)

/**
 * Result of one [solveResizeSnap] frame: the docDx/docDy adjustments to fold into the resize drag,
 * the alignment guides, the size badge (with match flags), and the next [ResizeSnapState].
 */
data class ResizeSnapOutput(
    val dx: Double,
    val dy: Double,
    val guides: List<AnchorGuide>,
    val match: SizeBadge,
    val state: ResizeSnapState,
)

/**
 * A candidate doc coordinate for a moving resize edge to land on. Edge/center *alignment* targets
 * carry the neighbour's cross extent so a guide line can be drawn; a dimension-*match* target
 * (equal width/height to a sibling) has [matched] = true and draws only the tinted size badge.
 */
private data class EdgeCandidate(
    val target: Double,
    val matched: Boolean,
    val crossLo: Double = 0.0,
    val crossHi: Double = 0.0,
)

/**
 * Magnetic snapping for a resize drag (unrotated components only — the editor gates rotated/grouped
 * resizes out). [candidate] is the box produced by the raw resize this frame; [edges] says which of
 * its edges the handle is dragging. Each moving edge snaps — with the same catch/release hysteresis
 * as [solveMoveSnap] — to the nearest of: a neighbour/container edge or center (an *alignment*, drawn
 * as a guide), or the position that makes the box's width/height equal a sibling's (a *dimension
 * match*, flagged on the size badge). Returns the docDx/docDy nudge to add to the drag.
 *
 * Pure and deterministic: thread [prior] in and store [ResizeSnapOutput.state]. Feed the raw
 * (pointer-driven) [candidate], never a previously snapped one, so the release distance is measured
 * against the pointer.
 */
fun solveResizeSnap(
    candidate: SnapBox,
    edges: MovingEdges,
    containers: List<SnapBox>,
    siblings: List<SnapBox>,
    catch: Double,
    release: Double,
    prior: ResizeSnapState,
): ResizeSnapOutput {
    val rel = maxOf(release, catch)
    val alignBoxes = containers + siblings
    val guides = ArrayList<AnchorGuide>()

    var dx = 0.0
    var latchX: Double? = null
    var widthMatched = false
    if (edges.left || edges.right) {
        val movingPos = if (edges.left) candidate.x else candidate.right
        val fixed = if (edges.left) candidate.right else candidate.x
        val candidates = ArrayList<EdgeCandidate>()
        alignBoxes.forEach { t ->
            candidates += EdgeCandidate(t.x, matched = false, crossLo = t.y, crossHi = t.bottom)
            candidates += EdgeCandidate(t.centerX, matched = false, crossLo = t.y, crossHi = t.bottom)
            candidates += EdgeCandidate(t.right, matched = false, crossLo = t.y, crossHi = t.bottom)
        }
        siblings.forEach { s ->
            candidates += EdgeCandidate(if (edges.right) fixed + s.width else fixed - s.width, matched = true)
        }
        pickEdge(movingPos, candidates, catch, rel, prior.latchX)?.let { winner ->
            dx = winner.target - movingPos
            latchX = winner.target
            if (winner.matched) widthMatched = true else guides += axisGuide(winner, target = winner.target, alongX = true, candidate = candidate)
        }
    }

    var dy = 0.0
    var latchY: Double? = null
    var heightMatched = false
    if (edges.top || edges.bottom) {
        val movingPos = if (edges.top) candidate.y else candidate.bottom
        val fixed = if (edges.top) candidate.bottom else candidate.y
        val candidates = ArrayList<EdgeCandidate>()
        alignBoxes.forEach { t ->
            candidates += EdgeCandidate(t.y, matched = false, crossLo = t.x, crossHi = t.right)
            candidates += EdgeCandidate(t.centerY, matched = false, crossLo = t.x, crossHi = t.right)
            candidates += EdgeCandidate(t.bottom, matched = false, crossLo = t.x, crossHi = t.right)
        }
        siblings.forEach { s ->
            candidates += EdgeCandidate(if (edges.bottom) fixed + s.height else fixed - s.height, matched = true)
        }
        pickEdge(movingPos, candidates, catch, rel, prior.latchY)?.let { winner ->
            dy = winner.target - movingPos
            latchY = winner.target
            if (winner.matched) heightMatched = true else guides += axisGuide(winner, target = winner.target, alongX = false, candidate = candidate)
        }
    }

    val newWidth = candidate.width + (if (edges.left) -dx else if (edges.right) dx else 0.0)
    val newHeight = candidate.height + (if (edges.top) -dy else if (edges.bottom) dy else 0.0)
    return ResizeSnapOutput(
        dx = dx,
        dy = dy,
        guides = guides,
        match = SizeBadge(newWidth, newHeight, widthMatched, heightMatched),
        state = ResizeSnapState(latchX, latchY),
    )
}

/** Nearest candidate to the moving edge [pos] within the hysteresis radius; ties prefer an alignment over a dimension match. */
private fun pickEdge(pos: Double, candidates: List<EdgeCandidate>, catch: Double, release: Double, priorLatch: Double?): EdgeCandidate? {
    val radius = if (priorLatch != null && abs(priorLatch - pos) <= release) release else catch
    return candidates
        .filter { abs(it.target - pos) <= radius }
        .minWithOrNull(compareBy({ abs(it.target - pos) }, { if (it.matched) 1 else 0 }))
}

/** A guide line at the snapped moving-edge [target], spanning the union of the [candidate] box and the neighbour's cross extent. */
private fun axisGuide(c: EdgeCandidate, target: Double, alongX: Boolean, candidate: SnapBox): AnchorGuide {
    val line = if (alongX) {
        SnapLine(target, minOf(candidate.y, c.crossLo), target, maxOf(candidate.bottom, c.crossHi))
    } else {
        SnapLine(minOf(candidate.x, c.crossLo), target, maxOf(candidate.right, c.crossHi), target)
    }
    return AnchorGuide(line, AnchorKind.Alignment)
}
