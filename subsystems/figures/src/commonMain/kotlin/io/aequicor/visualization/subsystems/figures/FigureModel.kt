package io.aequicor.visualization.subsystems.figures

/**
 * The figure vocabulary shared by the geometry builders, network lowering, and the
 * Compose renderer. Pure Kotlin — no dependency on the IR node model; the IR `Shape`
 * node references these types (its module depends on `:subsystems:figures`).
 */

/** The primitive/vector kinds a shape node can take. */
enum class ShapeType { Rectangle, Ellipse, Polygon, Star, Line, Arrow, Vector }

/** SVG-style view box a vector's paths/network are authored against. */
data class DesignViewBox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
)

/** A single inline vector path expressed as an SVG `d` string plus its winding rule. */
data class VectorPath(
    val windingRule: String = "nonzero",
    val d: String,
)

enum class BooleanOperationKind { Union, Subtract, Intersect, Exclude }
