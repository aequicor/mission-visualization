package io.aequicor.visualization.subsystems.diagrams.arrows

import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowhead
import io.aequicor.visualization.subsystems.diagrams.model.DiagramArrowheadKind
import io.aequicor.visualization.subsystems.diagrams.model.DiagramEdge
import io.aequicor.visualization.subsystems.diagrams.model.DiagramRelation
import io.aequicor.visualization.subsystems.diagrams.model.DiagramStrokePattern
import io.aequicor.visualization.subsystems.diagrams.model.isDashedNotation
import io.aequicor.visualization.subsystems.diagrams.model.notationArrowheads
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPath
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathBuilder
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPathSegment
import io.aequicor.visualization.subsystems.diagrams.path.DiagramPoint
import io.aequicor.visualization.subsystems.diagrams.path.diagramPath
import kotlin.math.sqrt

/**
 * Renderer-ready geometry of one arrowhead marker.
 *
 * @param path marker outline/strokes in document coordinates (empty for
 *   [DiagramArrowheadKind.NONE]).
 * @param filled `true` = fill the path with the edge stroke color, `false` = stroke the
 *   outline only (hollow markers keep the background visible inside).
 * @param lineShorten how far (in document units, measured back from the attachment point)
 *   the edge line must be cut so it does not poke through the marker. `0.0` means the line
 *   runs all the way to the tip (open/tick markers drawn over the line).
 */
data class ArrowheadGeometry(
    val path: DiagramPath,
    val filled: Boolean,
    val lineShorten: Double,
) {
    companion object {
        val None: ArrowheadGeometry = ArrowheadGeometry(DiagramPath.Empty, filled = false, lineShorten = 0.0)
    }
}

/**
 * Builds the marker geometry for [arrowhead] at the edge end.
 *
 * @param tip the attachment point (where the edge meets the node/port/free point).
 * @param direction unit vector of the line travel *into* the tip (from the line body toward
 *   the endpoint). Non-unit vectors are normalized defensively; a zero vector falls back
 *   to `(1, 0)`.
 *
 * [DiagramArrowhead.inset] pulls the whole marker (and the reported
 * [ArrowheadGeometry.lineShorten]) back from the attachment point.
 */
fun arrowheadPath(
    arrowhead: DiagramArrowhead,
    tip: DiagramPoint,
    direction: DiagramPoint,
): ArrowheadGeometry {
    val magnitude = sqrt(direction.x * direction.x + direction.y * direction.y)
    val dx = if (magnitude < EPSILON) 1.0 else direction.x / magnitude
    val dy = if (magnitude < EPSILON) 0.0 else direction.y / magnitude
    val inset = arrowhead.inset.coerceAtLeast(0.0)
    val effectiveTip = DiagramPoint(tip.x - dx * inset, tip.y - dy * inset)
    val size = arrowhead.size

    /** Point [back] units behind the tip along the line, [side] units along the perpendicular. */
    fun at(back: Double, side: Double): DiagramPoint = DiagramPoint(
        x = effectiveTip.x - dx * back - dy * side,
        y = effectiveTip.y - dy * back + dx * side,
    )

    fun DiagramPathBuilder.circle(centerBack: Double, radius: Double) {
        val near = at(centerBack - radius, 0.0)
        val far = at(centerBack + radius, 0.0)
        moveTo(near)
        arcTo(radiusX = radius, radiusY = radius, sweep = true, endX = far.x, endY = far.y)
        arcTo(radiusX = radius, radiusY = radius, sweep = true, endX = near.x, endY = near.y)
        close()
    }

    fun DiagramPathBuilder.crowFoot() {
        val vertex = at(size, 0.0)
        moveTo(vertex)
        lineTo(at(0.0, size / 2))
        moveTo(vertex)
        lineTo(effectiveTip)
        moveTo(vertex)
        lineTo(at(0.0, -size / 2))
    }

    fun DiagramPathBuilder.tick(back: Double) {
        moveTo(at(back, size / 2))
        lineTo(at(back, -size / 2))
    }

    val kind = arrowhead.kind
    val geometry = when (kind) {
        DiagramArrowheadKind.NONE -> ArrowheadGeometry.None

        DiagramArrowheadKind.OPEN -> ArrowheadGeometry(
            path = diagramPath {
                moveTo(at(size, size / 2))
                lineTo(effectiveTip)
                lineTo(at(size, -size / 2))
            },
            filled = false,
            lineShorten = 0.0,
        )

        DiagramArrowheadKind.BLOCK, DiagramArrowheadKind.BLOCK_FILLED -> ArrowheadGeometry(
            path = diagramPath {
                moveTo(effectiveTip)
                lineTo(at(size, size / 2))
                lineTo(at(size, -size / 2))
                close()
            },
            filled = kind == DiagramArrowheadKind.BLOCK_FILLED,
            lineShorten = size,
        )

        DiagramArrowheadKind.TRIANGLE, DiagramArrowheadKind.TRIANGLE_FILLED -> {
            val length = size * TRIANGLE_LENGTH_FACTOR
            val halfWidth = size * TRIANGLE_HALF_WIDTH_FACTOR
            ArrowheadGeometry(
                path = diagramPath {
                    moveTo(effectiveTip)
                    lineTo(at(length, halfWidth))
                    lineTo(at(length, -halfWidth))
                    close()
                },
                filled = kind == DiagramArrowheadKind.TRIANGLE_FILLED,
                lineShorten = length,
            )
        }

        DiagramArrowheadKind.DIAMOND, DiagramArrowheadKind.DIAMOND_FILLED -> {
            val length = size * DIAMOND_LENGTH_FACTOR
            val halfWidth = size * DIAMOND_HALF_WIDTH_FACTOR
            ArrowheadGeometry(
                path = diagramPath {
                    moveTo(effectiveTip)
                    lineTo(at(length / 2, halfWidth))
                    lineTo(at(length, 0.0))
                    lineTo(at(length / 2, -halfWidth))
                    close()
                },
                filled = kind == DiagramArrowheadKind.DIAMOND_FILLED,
                lineShorten = length,
            )
        }

        DiagramArrowheadKind.OVAL, DiagramArrowheadKind.OVAL_FILLED -> ArrowheadGeometry(
            path = diagramPath { circle(centerBack = size / 2, radius = size / 2) },
            filled = kind == DiagramArrowheadKind.OVAL_FILLED,
            lineShorten = size,
        )

        DiagramArrowheadKind.CROSS -> ArrowheadGeometry(
            path = diagramPath {
                moveTo(at(size, size / 2))
                lineTo(at(0.0, -size / 2))
                moveTo(at(size, -size / 2))
                lineTo(at(0.0, size / 2))
            },
            filled = false,
            lineShorten = 0.0,
        )

        DiagramArrowheadKind.DASH -> ArrowheadGeometry(
            path = diagramPath {
                moveTo(at(size * 0.75, size / 2))
                lineTo(at(size * 0.25, -size / 2))
            },
            filled = false,
            lineShorten = 0.0,
        )

        DiagramArrowheadKind.ER_ONE -> ArrowheadGeometry(
            path = diagramPath { tick(size) },
            filled = false,
            lineShorten = 0.0,
        )

        DiagramArrowheadKind.ER_MANY -> ArrowheadGeometry(
            path = diagramPath { crowFoot() },
            filled = false,
            lineShorten = 0.0,
        )

        DiagramArrowheadKind.ER_ONE_OR_MANY -> ArrowheadGeometry(
            path = diagramPath {
                crowFoot()
                tick(size * 1.5)
            },
            filled = false,
            lineShorten = 0.0,
        )

        DiagramArrowheadKind.ER_ZERO_OR_ONE -> ArrowheadGeometry(
            path = diagramPath {
                // Spine connecting the tip to the circle's near edge (the shortened
                // edge line stops behind the circle).
                moveTo(effectiveTip)
                lineTo(at(size * 1.5, 0.0))
                tick(size)
                circle(centerBack = size * 2, radius = size / 2)
            },
            filled = false,
            lineShorten = size * 2.5,
        )

        DiagramArrowheadKind.ER_ZERO_OR_MANY -> ArrowheadGeometry(
            path = diagramPath {
                crowFoot()
                moveTo(at(size, 0.0))
                lineTo(at(size * 1.5, 0.0))
                circle(centerBack = size * 2, radius = size / 2)
            },
            filled = false,
            lineShorten = size * 2.5,
        )
    }
    return if (inset == 0.0) geometry else geometry.copy(lineShorten = geometry.lineShorten + inset)
}

/**
 * How far the marker for [arrowhead] reaches back from its attachment point along the line
 * — the straight run an edge end needs for the marker to read as a marker instead of
 * merging into the first bend. Measured off the real geometry, so it follows any change to
 * the marker shapes rather than restating their proportions.
 */
fun arrowheadExtent(arrowhead: DiagramArrowhead): Double {
    val geometry = arrowheadPath(arrowhead, tip = DiagramPoint.Zero, direction = DiagramPoint(1.0, 0.0))
    // Tip at the origin travelling along +x, so every marker point sits at x = -back.
    var extent = geometry.lineShorten
    for (segment in geometry.path.segments) {
        val point = when (segment) {
            is DiagramPathSegment.MoveTo -> segment.point
            is DiagramPathSegment.LineTo -> segment.point
            is DiagramPathSegment.QuadTo -> segment.end
            is DiagramPathSegment.CubicTo -> segment.end
            is DiagramPathSegment.ArcTo -> segment.end
            DiagramPathSegment.Close -> continue
        }
        extent = maxOf(extent, -point.x)
    }
    return extent
}

/**
 * This arrowhead scaled down so its marker fits on a [run]-long straight segment, or
 * unchanged when it already fits. Routing reserves that run
 * (`RoutingOptions.endpointClearance`); this is the fallback for the ends it cannot honor
 * — nodes packed closer together than the marker is long — where a smaller marker reads
 * better than one sliding across the first bend.
 */
fun DiagramArrowhead.fittedTo(run: Double): DiagramArrowhead {
    val extent = arrowheadExtent(this)
    if (extent <= EPSILON || extent <= run) return this
    val scale = (run / extent).coerceAtLeast(0.0)
    return copy(size = size * scale, inset = inset * scale)
}

/**
 * The UML/ER notation for a relation: default source/target arrowheads plus the stroke
 * pattern (dashed for dependency/realization/include/extend and return/create messages).
 */
data class RelationArrowheads(
    val source: DiagramArrowhead,
    val target: DiagramArrowhead,
    val pattern: DiagramStrokePattern,
)

/**
 * Notation defaults for [relation]: generalization = hollow triangle + solid, realization =
 * hollow triangle + dashed, dependency = open arrow + dashed, aggregation = hollow diamond
 * at the source (whole), composition = filled diamond at the source, association = plain or
 * open arrow when directed, ER = crow's-foot per cardinality.
 */
fun arrowheadsForRelation(relation: DiagramRelation): RelationArrowheads {
    val notation = relation.notationArrowheads()
    return RelationArrowheads(
        source = notation.source,
        target = notation.target,
        pattern = if (relation.isDashedNotation) DiagramStrokePattern.DASHED else DiagramStrokePattern.SOLID,
    )
}

/**
 * The heads [edge] actually renders: its explicit arrowheads, each falling back to its
 * relation's notation where the edge does not override it, plus the effective stroke
 * pattern. Shared by the renderer and the router so the geometry that reserves room for a
 * marker and the geometry that draws it can never disagree.
 */
fun resolvedArrowheads(edge: DiagramEdge): RelationArrowheads {
    val notation = arrowheadsForRelation(edge.relation)
    return RelationArrowheads(
        source = edge.sourceArrowhead.orNotation(notation.source),
        target = edge.targetArrowhead.orNotation(notation.target),
        pattern = if (edge.style.pattern == DiagramStrokePattern.SOLID) notation.pattern else edge.style.pattern,
    )
}

/** Explicit head, or the relation-notation head when the edge does not override. */
private fun DiagramArrowhead.orNotation(notation: DiagramArrowhead): DiagramArrowhead =
    if (kind == DiagramArrowheadKind.NONE && notation.kind != DiagramArrowheadKind.NONE) notation else this

private const val EPSILON = 1e-9
private const val TRIANGLE_LENGTH_FACTOR = 1.4
private const val TRIANGLE_HALF_WIDTH_FACTOR = 0.8
private const val DIAMOND_LENGTH_FACTOR = 1.6
private const val DIAMOND_HALF_WIDTH_FACTOR = 0.5
