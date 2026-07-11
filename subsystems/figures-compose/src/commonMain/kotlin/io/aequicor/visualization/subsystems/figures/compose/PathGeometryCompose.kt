package io.aequicor.visualization.subsystems.figures.compose

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import io.aequicor.visualization.subsystems.figures.Affine2D
import io.aequicor.visualization.subsystems.figures.BooleanOperationKind
import io.aequicor.visualization.subsystems.figures.PathCommand
import io.aequicor.visualization.subsystems.figures.PathFillRule
import io.aequicor.visualization.subsystems.figures.PathGeometry
import io.aequicor.visualization.subsystems.figures.RectD
import io.aequicor.visualization.subsystems.figures.meetFit

/**
 * The single adapter from the device-independent geometry IR (`:subsystems:figures`) to a Compose
 * [Path]. All shape math lives in the pure module and is headless-tested; this layer only converts
 * commands and applies the view-box → box fit for lowered vectors.
 */

fun PathGeometry.toComposePath(box: RectD): Path {
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

private fun Affine2D?.map(x: Double, y: Double): Pair<Float, Float> {
    if (this == null) return x.toFloat() to y.toFloat()
    val (fx, fy) = apply(x, y)
    return fx.toFloat() to fy.toFloat()
}

fun strokeCapOf(cap: String): StrokeCap = when (cap) {
    "round" -> StrokeCap.Round
    "square" -> StrokeCap.Square
    else -> StrokeCap.Butt
}

fun strokeJoinOf(join: String): StrokeJoin = when (join) {
    "round" -> StrokeJoin.Round
    "bevel" -> StrokeJoin.Bevel
    else -> StrokeJoin.Miter
}

fun pathOperationOf(op: BooleanOperationKind): PathOperation = when (op) {
    BooleanOperationKind.Union -> PathOperation.Union
    BooleanOperationKind.Subtract -> PathOperation.Difference
    BooleanOperationKind.Intersect -> PathOperation.Intersect
    BooleanOperationKind.Exclude -> PathOperation.Xor
}
