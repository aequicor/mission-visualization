package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import io.aequicor.visualization.engine.ir.geometry.PathCommand
import io.aequicor.visualization.engine.ir.geometry.PathFillRule
import io.aequicor.visualization.engine.ir.geometry.PathGeometry
import io.aequicor.visualization.engine.ir.geometry.RectD
import io.aequicor.visualization.engine.ir.geometry.meetFit
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.BooleanOperationKind

/**
 * The single adapter from the device-independent geometry IR (engine/ir) to a Compose [Path].
 * All shape math lives in engine/ir and is headless-tested; this layer only converts commands
 * and applies the view-box → box fit for lowered vectors.
 */

internal fun LayoutBox.rectD(): RectD = RectD(x, y, right, bottom)

internal fun PathGeometry.toComposePath(box: RectD): Path {
    val fit = sourceViewBox?.let { meetFit(it, box) }
    val path = Path()
    path.fillType = if (fillRule == PathFillRule.EvenOdd) PathFillType.EvenOdd else PathFillType.NonZero
    commands.forEach { command ->
        when (command) {
            is PathCommand.MoveTo -> {
                val (x, y) = fit.map(command.x, command.y)
                path.moveTo(x, y)
            }
            is PathCommand.LineTo -> {
                val (x, y) = fit.map(command.x, command.y)
                path.lineTo(x, y)
            }
            is PathCommand.QuadTo -> {
                val (cx, cy) = fit.map(command.cx, command.cy)
                val (x, y) = fit.map(command.x, command.y)
                path.quadraticTo(cx, cy, x, y)
            }
            is PathCommand.CubicTo -> {
                val (c1x, c1y) = fit.map(command.c1x, command.c1y)
                val (c2x, c2y) = fit.map(command.c2x, command.c2y)
                val (x, y) = fit.map(command.x, command.y)
                path.cubicTo(c1x, c1y, c2x, c2y, x, y)
            }
            PathCommand.Close -> path.close()
        }
    }
    return path
}

private fun io.aequicor.visualization.engine.ir.geometry.Affine2D?.map(x: Double, y: Double): Pair<Float, Float> {
    if (this == null) return x.toFloat() to y.toFloat()
    val (fx, fy) = apply(x, y)
    return fx.toFloat() to fy.toFloat()
}

internal fun strokeCapOf(cap: String): StrokeCap = when (cap) {
    "round" -> StrokeCap.Round
    "square" -> StrokeCap.Square
    else -> StrokeCap.Butt
}

internal fun strokeJoinOf(join: String): StrokeJoin = when (join) {
    "round" -> StrokeJoin.Round
    "bevel" -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

internal fun pathOperationOf(op: BooleanOperationKind): PathOperation = when (op) {
    BooleanOperationKind.Union -> PathOperation.Union
    BooleanOperationKind.Subtract -> PathOperation.Difference
    BooleanOperationKind.Intersect -> PathOperation.Intersect
    BooleanOperationKind.Exclude -> PathOperation.Xor
}
