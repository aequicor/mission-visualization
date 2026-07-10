package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.subsystems.figures.HandleMirror
import io.aequicor.visualization.subsystems.figures.HandleOffset
import io.aequicor.visualization.subsystems.figures.VectorNetwork
import io.aequicor.visualization.subsystems.figures.VectorRegion
import io.aequicor.visualization.subsystems.figures.VectorSegment
import io.aequicor.visualization.subsystems.figures.VectorVertex

/**
 * Renders a [VectorNetwork] into a `network:` [YamlPayload] for write-back. Shared by the
 * surgical patcher ([SlmPatcher] `SetVectorNetwork`) and the structural writer
 * ([NodeSectionWriter] `vectorPayload`). Mirrors [StyleYamlWriter]/[TypographyYamlWriter].
 * Sub-lists use `replaceWhole = true` so a re-write cleanly replaces the whole vertex/segment
 * /region list rather than merging item-by-item.
 */
internal object NetworkYamlWriter {

    fun network(
        network: VectorNetwork,
        regionFills: Map<Int, List<DesignPaint>> = emptyMap(),
    ): YamlPayload.Mapping = YamlPayload.Mapping(
        buildList {
            add("vertices" to YamlPayload.Sequence(network.vertices.map(::vertex), replaceWhole = true))
            add("segments" to YamlPayload.Sequence(network.segments.map(::segment), replaceWhole = true))
            if (network.regions.isNotEmpty()) {
                add(
                    "regions" to YamlPayload.Sequence(
                        network.regions.mapIndexed { index, r -> region(r, regionFills[index]) },
                        replaceWhole = true,
                    ),
                )
            }
        },
    )

    private fun vertex(vertex: VectorVertex): YamlPayload = YamlPayload.Mapping(
        buildList {
            add("x" to num(vertex.x))
            add("y" to num(vertex.y))
            vertex.inHandle?.let { add("in" to offset(it)) }
            vertex.outHandle?.let { add("out" to offset(it)) }
            if (vertex.mirror != HandleMirror.None) add("mirror" to str(mirrorToken(vertex.mirror)))
            if (vertex.corner) add("corner" to bool(true))
            if (vertex.cornerRadius > 0.0) add("radius" to num(vertex.cornerRadius))
        },
    )

    private fun offset(handle: HandleOffset): YamlPayload =
        YamlPayload.Sequence(listOf(num(handle.dx), num(handle.dy)), replaceWhole = true)

    private fun segment(segment: VectorSegment): YamlPayload =
        YamlPayload.Sequence(listOf(num(segment.from.toDouble()), num(segment.to.toDouble())), replaceWhole = true)

    private fun region(region: VectorRegion, fills: List<DesignPaint>?): YamlPayload = YamlPayload.Mapping(
        buildList {
            if (region.windingRule != "nonzero") add("windingRule" to str(region.windingRule))
            add(
                "loops" to YamlPayload.Sequence(
                    region.loops.map { loop ->
                        YamlPayload.Sequence(loop.map { num(it.toDouble()) }, replaceWhole = true)
                    },
                    replaceWhole = true,
                ),
            )
            if (fills != null && fills.isNotEmpty()) add("fills" to StyleYamlWriter.fills(fills))
        },
    )

    private fun mirrorToken(mirror: HandleMirror): String = when (mirror) {
        HandleMirror.None -> "none"
        HandleMirror.Angle -> "angle"
        HandleMirror.AngleAndLength -> "angleAndLength"
    }

    private fun str(value: String): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Str(value))
    private fun num(value: Double): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Num(value))
    private fun bool(value: Boolean): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Bool(value))
}
