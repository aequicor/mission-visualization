package io.aequicor.visualization.engine.ir.model

/**
 * Editable, Figma-style structural vector geometry: vertices with in/out bezier tangent
 * handles, segments connecting vertex indices, and winding regions selecting which closed
 * loops fill. Lives in the SAME coordinate space as the enclosing [DesignNodeKind.Shape]'s
 * `viewBox`/`paths`. An empty network means "not authored" — the shape then renders from
 * `paths`/`pathRef`/primitive. This is the authoring source of truth for interactive
 * editing; the resolver lowers it (one-way) into device-independent geometry for rendering.
 */
data class VectorNetwork(
    val vertices: List<VectorVertex> = emptyList(),
    val segments: List<VectorSegment> = emptyList(),
    /** Fillable areas; when empty, a single implicit region covers every closed loop (nonzero). */
    val regions: List<VectorRegion> = emptyList(),
) {
    fun isEmpty(): Boolean = vertices.isEmpty() && segments.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()
}

/**
 * A vertex at ([x], [y]) with optional bezier handles stored as OFFSETS relative to the
 * vertex — this makes vertex-drag leave handles invariant and keeps mirror math trivial.
 * A null handle means "no tangent on that side" (the incident segment is straight there).
 */
data class VectorVertex(
    val x: Double,
    val y: Double,
    /** Tangent handle ENTERING the vertex, as an offset from ([x], [y]). */
    val inHandle: HandleOffset? = null,
    /** Tangent handle LEAVING the vertex, as an offset from ([x], [y]). */
    val outHandle: HandleOffset? = null,
    /** Editor constraint tying the two handles together; the resolver normalizes to it. */
    val mirror: HandleMirror = HandleMirror.None,
    /** Authoring hint: sharp corner (true) vs smooth join (false). Advisory. */
    val corner: Boolean = false,
)

data class HandleOffset(val dx: Double, val dy: Double)

/** None = handles independent; Angle = colinear; AngleAndLength = colinear + equal length. */
enum class HandleMirror { None, Angle, AngleAndLength }

data class VectorSegment(
    val from: Int, // index into VectorNetwork.vertices
    val to: Int, // index into VectorNetwork.vertices
)

data class VectorRegion(
    /** "nonzero" | "evenodd", mirroring [VectorPath.windingRule]. */
    val windingRule: String = "nonzero",
    /** Closed loops; each loop is an ordered list of segment indices forming a cycle. */
    val loops: List<List<Int>> = emptyList(),
)
