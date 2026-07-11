package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.blocks.readers.ReaderEnums
import io.aequicor.visualization.engine.frontend.blocks.readers.readShapeBlock
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.subsystems.figures.ShapeType
import kotlin.math.abs
import kotlin.math.floor

/**
 * The built-in `shape:` block routed through the [TypedBlockExtension] contract —
 * the reference implementation validating the extension API. Reachability is
 * unchanged: `shape` stays a built-in reserved key handled by `TypedBlockReader`
 * and `PatchMerger` (both delegate here), never by the registry — registering a
 * `shape` extension is rejected as a reserved-key conflict.
 */
object ShapeBlockExtension : TypedBlockExtension<ShapePatch> {
    override val kind: String = "shape"

    override fun read(value: YamlValue, reading: BlockReading): ShapePatch? =
        readShapeBlock(value, reading)

    override fun applyToNode(node: DesignNode, payload: ShapePatch): DesignNode {
        val kind = node.kind as? DesignNodeKind.Shape
            ?: DesignNodeKind.Shape(shape = ShapeType.Rectangle)
        return node.copy(
            type = if (node.kind is DesignNodeKind.Shape) node.type else "shape",
            kind = kind.copy(
                shape = payload.kind ?: kind.shape,
                pointCount = payload.pointCount ?: kind.pointCount,
                innerRadius = payload.innerRadius ?: kind.innerRadius,
                arcStartDeg = payload.arcStartDeg ?: kind.arcStartDeg,
                arcSweepDeg = payload.arcSweepDeg ?: kind.arcSweepDeg,
            ),
            size = mergedSize(node.size, payload.width, payload.height) ?: node.size,
        )
    }

    override fun write(payload: ShapePatch): String = buildList {
        add("shape:")
        payload.kind?.let { add("  kind: ${shapeKindKeys.getValue(it)}") }
        payload.width?.let { add("  width: ${number(it)}") }
        payload.height?.let { add("  height: ${number(it)}") }
        payload.pointCount?.let { add("  pointCount: $it") }
        payload.innerRadius?.let { add("  innerRadius: ${number(it)}") }
        payload.arcStartDeg?.let { add("  arcStart: ${number(it)}") }
        payload.arcSweepDeg?.let { add("  arcSweep: ${number(it)}") }
    }.joinToString("\n")

    private val shapeKindKeys: Map<ShapeType, String> =
        ReaderEnums.shapeKind.entries.associate { (key, type) -> type to key }

    private fun mergedSize(existing: DesignSize?, width: Double?, height: Double?): DesignSize? {
        if (width == null && height == null) return existing
        return DesignSize(
            width = width ?: existing?.width,
            height = height ?: existing?.height,
        )
    }

    /** Integers without a decimal point, minimal doubles (mirrors ScalarFormatter). */
    private fun number(value: Double): String =
        if (value == floor(value) && !value.isInfinite() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}
