package io.aequicor.visualization.subsystems.figures

/**
 * Serializes a [PathGeometry]'s commands into an absolute SVG `d` string (uppercase M/L/Q/C/Z).
 * Numbers are trimmed to a compact form. Used by Flatten to bake combined geometry into inline
 * `vector.paths` that round-trip through the SLM/IR path readers.
 */
fun PathGeometry.toSvgPathData(): String {
    val sb = StringBuilder()
    commands.forEachIndexed { i, command ->
        if (i > 0) sb.append(' ')
        when (command) {
            is PathCommand.MoveTo -> sb.append("M ").append(n(command.x)).append(' ').append(n(command.y))
            is PathCommand.LineTo -> sb.append("L ").append(n(command.x)).append(' ').append(n(command.y))
            is PathCommand.QuadTo ->
                sb.append("Q ").append(n(command.cx)).append(' ').append(n(command.cy))
                    .append(' ').append(n(command.x)).append(' ').append(n(command.y))
            is PathCommand.CubicTo ->
                sb.append("C ").append(n(command.c1x)).append(' ').append(n(command.c1y))
                    .append(' ').append(n(command.c2x)).append(' ').append(n(command.c2y))
                    .append(' ').append(n(command.x)).append(' ').append(n(command.y))
            PathCommand.Close -> sb.append("Z")
        }
    }
    return sb.toString()
}

private fun n(value: Double): String {
    val rounded = (value * 1000.0).toLong() / 1000.0
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) asLong.toString() else rounded.toString()
}
