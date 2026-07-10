package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * IR-ASSET — structural vector-network integrity (continues the [MediaAssetChecks] family).
 *
 * - IR-ASSET-008 (error): a segment or region-loop references a vertex/segment index out of range.
 * - IR-ASSET-009 (error): a region loop does not form a closed cycle.
 * - IR-ASSET-010 (warning): degenerate network (fewer than 2 vertices, or no segments).
 * - IR-ASSET-011 (warning): a vertex declares a handle mirror mode its stored handles violate.
 * - IR-ASSET-012 (warning): a shape carries both a network and inline paths/pathRef (ambiguous source).
 * - IR-ASSET-013 (warning): ellipse arc start/sweep angle out of the [-360, 360]° range.
 * - IR-ASSET-014 (warning): shape innerRadius (star valley / ellipse donut ratio) outside [0, 1].
 * - IR-ASSET-015 (warning): a vertex declares a negative corner radius.
 * - IR-ASSET-016 (warning): a region fill references a region index the network does not have.
 */
internal object VectorNetworkChecks {

    private const val EPSILON = 1e-3

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            val node = entry.node
            val shape = node.kind as? DesignNodeKind.Shape ?: return@forEach
            val location = ctx.location(node)

            // Parametric range checks apply to every shape (arc/donut ellipse has no network).
            shape.arcStartDeg?.let {
                if (abs(it) > 360.0) {
                    add(validationWarning("IR-ASSET-013", "Shape '${node.id}' arcStart ${it}° is outside [-360, 360]", location))
                }
            }
            shape.arcSweepDeg?.let {
                if (abs(it) > 360.0) {
                    add(validationWarning("IR-ASSET-013", "Shape '${node.id}' arcSweep ${it}° is outside [-360, 360]", location))
                }
            }
            shape.innerRadius?.let {
                if (it < 0.0 || it > 1.0) {
                    add(validationWarning("IR-ASSET-014", "Shape '${node.id}' innerRadius $it is outside [0, 1]", location))
                }
            }
            shape.regionFills.keys.forEach { regionIndex ->
                val regionCount = shape.network?.regions?.size ?: 0
                if (regionIndex < 0 || regionIndex >= regionCount) {
                    add(validationWarning("IR-ASSET-016", "Shape '${node.id}' has a fill for region $regionIndex but the network has $regionCount region(s)", location))
                }
            }

            val network = shape.network ?: return@forEach

            network.vertices.forEachIndexed { index, vertex ->
                if (vertex.cornerRadius < 0.0) {
                    add(validationWarning("IR-ASSET-015", "Vector network of '${node.id}' vertex $index has a negative corner radius", location))
                }
            }

            if (network.vertices.size < 2 || network.segments.isEmpty()) {
                add(
                    validationWarning(
                        "IR-ASSET-010",
                        "Vector network of '${node.id}' is degenerate (needs ≥2 vertices and ≥1 segment)",
                        location,
                    ),
                )
            }
            if (shape.paths.isNotEmpty() || shape.pathRef.isNotEmpty()) {
                add(
                    validationWarning(
                        "IR-ASSET-012",
                        "Shape '${node.id}' carries both a vector network and inline paths/pathRef; the network wins",
                        location,
                    ),
                )
            }
            checkIndices(this, node.id, location, network)
            checkRegions(this, node.id, location, network)
            checkMirrors(this, node.id, location, network)
        }
    }

    private fun checkIndices(
        sink: MutableList<DesignDiagnostic>,
        nodeId: String,
        location: SourceLocation?,
        network: VectorNetwork,
    ) {
        val vertexRange = network.vertices.indices
        network.segments.forEachIndexed { index, segment ->
            if (segment.from !in vertexRange || segment.to !in vertexRange) {
                sink += validationError(
                    "IR-ASSET-008",
                    "Vector network of '$nodeId' segment $index references a vertex out of range (0..${vertexRange.last})",
                    location,
                )
            }
        }
        val segmentRange = network.segments.indices
        network.regions.forEachIndexed { regionIndex, region ->
            region.loops.forEachIndexed { loopIndex, loop ->
                loop.forEach { segmentIndex ->
                    if (segmentIndex !in segmentRange) {
                        sink += validationError(
                            "IR-ASSET-008",
                            "Vector network of '$nodeId' region $regionIndex loop $loopIndex references " +
                                "segment $segmentIndex out of range (0..${segmentRange.lastOrNull() ?: -1})",
                            location,
                        )
                    }
                }
            }
        }
    }

    private fun checkRegions(
        sink: MutableList<DesignDiagnostic>,
        nodeId: String,
        location: SourceLocation?,
        network: VectorNetwork,
    ) {
        network.regions.forEachIndexed { regionIndex, region ->
            region.loops.forEachIndexed { loopIndex, loop ->
                val segments = loop.mapNotNull { network.segments.getOrNull(it) }
                if (segments.size != loop.size || segments.isEmpty()) return@forEachIndexed // index error already reported
                val closed = segments.indices.all { i ->
                    val next = segments[(i + 1) % segments.size]
                    segments[i].to == next.from
                }
                if (!closed) {
                    sink += validationError(
                        "IR-ASSET-009",
                        "Vector network of '$nodeId' region $regionIndex loop $loopIndex is not a closed cycle",
                        location,
                    )
                }
            }
        }
    }

    private fun checkMirrors(
        sink: MutableList<DesignDiagnostic>,
        nodeId: String,
        location: SourceLocation?,
        network: VectorNetwork,
    ) {
        network.vertices.forEachIndexed { index, vertex ->
            val inHandle = vertex.inHandle
            val outHandle = vertex.outHandle
            if (vertex.mirror == HandleMirror.None || inHandle == null || outHandle == null) return@forEachIndexed
            val consistent = when (vertex.mirror) {
                HandleMirror.Angle -> colinearOpposite(inHandle, outHandle)
                HandleMirror.AngleAndLength -> colinearOpposite(inHandle, outHandle) &&
                    abs(length(inHandle) - length(outHandle)) <= EPSILON * (1 + length(outHandle))
                HandleMirror.None -> true
            }
            if (!consistent) {
                sink += validationWarning(
                    "IR-ASSET-011",
                    "Vector network of '$nodeId' vertex $index declares ${vertex.mirror} but its handles do not match",
                    location,
                )
            }
        }
    }

    private fun colinearOpposite(a: HandleOffset, b: HandleOffset): Boolean {
        val cross = a.dx * b.dy - a.dy * b.dx
        val dot = a.dx * b.dx + a.dy * b.dy
        val scale = length(a) * length(b)
        if (scale <= EPSILON) return true
        return abs(cross) <= EPSILON * scale && dot < 0.0
    }

    private fun length(handle: HandleOffset): Double = sqrt(handle.dx * handle.dx + handle.dy * handle.dy)
}
