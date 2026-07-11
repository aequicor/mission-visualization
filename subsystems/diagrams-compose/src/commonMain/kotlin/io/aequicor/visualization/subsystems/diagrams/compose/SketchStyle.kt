package io.aequicor.visualization.subsystems.diagrams.compose

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint

/**
 * Hand-drawn ("sketch") look: a light deterministic jitter of the path anchors, seeded by
 * the owning element's id so the wobble is stable across frames and platforms.
 * Straight segments additionally bow through a jittered midpoint.
 */
internal fun DiagramPath.sketched(seed: Int, amplitude: Double = 1.1): DiagramPath {
    if (isEmpty) return this
    var state = seed * 31 + 17
    fun nextJitter(): Double {
        // xorshift32; maps to -amplitude..amplitude.
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        return ((state % 1000) / 1000.0) * amplitude
    }

    fun jittered(point: DiagramPoint): DiagramPoint =
        DiagramPoint(point.x + nextJitter(), point.y + nextJitter())

    var current = DiagramPoint.Zero
    val out = mutableListOf<DiagramPathSegment>()
    segments.forEach { segment ->
        when (segment) {
            is DiagramPathSegment.MoveTo -> {
                val p = jittered(segment.point)
                out += DiagramPathSegment.MoveTo(p)
                current = segment.point
            }

            is DiagramPathSegment.LineTo -> {
                val end = jittered(segment.point)
                val mid = jittered(
                    DiagramPoint(
                        (current.x + segment.point.x) / 2.0,
                        (current.y + segment.point.y) / 2.0,
                    ),
                )
                out += DiagramPathSegment.QuadTo(control = mid, end = end)
                current = segment.point
            }

            is DiagramPathSegment.QuadTo -> {
                out += DiagramPathSegment.QuadTo(jittered(segment.control), jittered(segment.end))
                current = segment.end
            }

            is DiagramPathSegment.CubicTo -> {
                out += DiagramPathSegment.CubicTo(
                    jittered(segment.control1),
                    jittered(segment.control2),
                    jittered(segment.end),
                )
                current = segment.end
            }

            is DiagramPathSegment.ArcTo -> {
                out += segment.copy(end = jittered(segment.end))
                current = segment.end
            }

            DiagramPathSegment.Close -> out += DiagramPathSegment.Close
        }
    }
    return DiagramPath(out)
}

/** Stable non-cryptographic seed for the sketch jitter of an element id. */
internal fun sketchSeed(id: String): Int {
    var hash = 5381
    id.forEach { hash = hash * 33 + it.code }
    return hash
}
