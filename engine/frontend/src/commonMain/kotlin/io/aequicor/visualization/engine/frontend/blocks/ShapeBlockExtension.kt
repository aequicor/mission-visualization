package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.subsystems.figures.ShapeType

/**
 * The built-in `shape` patch application routed through the [TypedBlockExtension]
 * contract — the reference implementation validating the extension API. `shape`
 * stays a built-in reserved key handled by CNL desugar and `PatchMerger` (which
 * delegates here), never by the registry — registering a `shape` extension is
 * rejected as a reserved-key conflict.
 */
object ShapeBlockExtension : TypedBlockExtension<ShapePatch> {
    override val kind: String = "shape"

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

    private fun mergedSize(existing: DesignSize?, width: Double?, height: Double?): DesignSize? {
        if (width == null && height == null) return existing
        return DesignSize(
            width = width ?: existing?.width,
            height = height ?: existing?.height,
        )
    }
}
