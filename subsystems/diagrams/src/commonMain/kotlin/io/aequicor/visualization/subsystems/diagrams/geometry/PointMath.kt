package io.aequicor.visualization.subsystems.diagrams.geometry

import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import kotlin.math.abs
import kotlin.math.sqrt

/** Shared numeric tolerance for geometry/routing comparisons. */
internal const val GEOMETRY_EPSILON: Double = 1e-6

internal operator fun DiagramPoint.plus(other: DiagramPoint): DiagramPoint =
    DiagramPoint(x + other.x, y + other.y)

internal operator fun DiagramPoint.minus(other: DiagramPoint): DiagramPoint =
    DiagramPoint(x - other.x, y - other.y)

internal operator fun DiagramPoint.times(scalar: Double): DiagramPoint =
    DiagramPoint(x * scalar, y * scalar)

internal fun DiagramPoint.length(): Double = sqrt(x * x + y * y)

internal fun DiagramPoint.distanceTo(other: DiagramPoint): Double = (other - this).length()

internal fun DiagramPoint.nearlyEquals(
    other: DiagramPoint,
    epsilon: Double = GEOMETRY_EPSILON,
): Boolean = abs(x - other.x) <= epsilon && abs(y - other.y) <= epsilon
