package io.aequicor.visualization.subsystems.anchoring

import kotlin.math.abs

/** The key rotation angles the magnet catches without Shift: 0/45/90/…/315°. */
val KeyRotationAngles: List<Double> = List(8) { it * 45.0 }

/** What a rotation snap caught — drives the indicator styling. */
enum class RotationSnapTargetKind { KeyAngle, Neighbor }

/** Per-drag rotation-snap latch: the angle currently caught, or null when free. */
data class RotateSnapState(val latch: Double? = null) {
    companion object {
        val None = RotateSnapState()
    }
}

/**
 * Result of one [solveRotateSnap] frame: the resolved [angle] to apply, whether it magnetically
 * [caught] and to what [kind] (for the indicator), and the next [RotateSnapState].
 */
data class RotateSnapOutput(
    val angle: Double,
    val caught: Boolean,
    val kind: RotationSnapTargetKind?,
    val state: RotateSnapState,
)

/** Normalizes an angle into `[0, 360)`. */
private fun norm(a: Double): Double {
    val m = a % 360.0
    return if (m < 0.0) m + 360.0 else m
}

/** Signed smallest angular difference from [a] to [b], in `(-180, 180]` degrees (wrap-aware). */
internal fun angularDistance(a: Double, b: Double): Double {
    var d = (b - a) % 360.0
    if (d > 180.0) d -= 360.0
    if (d <= -180.0) d += 360.0
    return d
}

/**
 * Expands each neighbour's rotation into snap candidates: the rotation itself and its +90° (so the
 * dragged component can line its edges up horizontally/vertically with a neighbour's edges).
 */
fun neighborRotationCandidates(neighborRotations: List<Double>): List<Double> =
    neighborRotations.flatMap { listOf(norm(it), norm(it + 90.0)) }.distinct()

/**
 * Magnetic rotation snap (design-book §18). [freeAngle] is the raw pointer-driven angle; candidates
 * are [keyAngles] (0/45/90/…) plus [neighborAngles] (see [neighborRotationCandidates]). Uses the
 * same catch/release hysteresis as the move/resize snaps: engages within [catch]°, then holds until
 * the pointer swings [release]° past the latched angle. Key angles win ties over neighbour angles.
 * Shift's hard 15° step is applied by the caller instead (magnet off), so pass no prior latch then.
 * Pure: thread [prior] in and store [RotateSnapOutput.state].
 */
fun solveRotateSnap(
    freeAngle: Double,
    keyAngles: List<Double>,
    neighborAngles: List<Double>,
    catch: Double,
    release: Double,
    prior: RotateSnapState,
): RotateSnapOutput {
    val rel = maxOf(release, catch)
    val f = norm(freeAngle)
    val candidates = keyAngles.map { norm(it) to RotationSnapTargetKind.KeyAngle } +
        neighborAngles.map { norm(it) to RotationSnapTargetKind.Neighbor }
    val radius = if (prior.latch != null && abs(angularDistance(f, norm(prior.latch))) <= rel) rel else catch
    val winner = candidates
        .filter { abs(angularDistance(f, it.first)) <= radius }
        .minWithOrNull(compareBy({ abs(angularDistance(f, it.first)) }, { if (it.second == RotationSnapTargetKind.KeyAngle) 0 else 1 }))
    return if (winner == null) {
        RotateSnapOutput(f, caught = false, kind = null, state = RotateSnapState.None)
    } else {
        RotateSnapOutput(norm(winner.first), caught = true, kind = winner.second, state = RotateSnapState(winner.first))
    }
}
