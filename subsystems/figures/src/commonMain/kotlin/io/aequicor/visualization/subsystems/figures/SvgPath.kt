package io.aequicor.visualization.subsystems.figures

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * SVG path-data parser producing the pure [PathGeometry] IR. Supports
 * `M/L/H/V/C/S/Q/T/A/Z` with relative variants and smooth-curve reflection. Arcs are
 * converted to cubic beziers ([arcToCubics]); malformed input ends the parse gracefully
 * (the partial geometry so far is returned).
 */
fun parseSvgPathToGeometry(d: String, fillRule: PathFillRule = PathFillRule.NonZero): PathGeometry {
    val commands = ArrayList<PathCommand>()
    val tokens = tokenizePath(d)
    var index = 0
    var command = ' '
    var currentX = 0.0
    var currentY = 0.0
    var startX = 0.0
    var startY = 0.0
    var lastControlX = 0.0
    var lastControlY = 0.0
    var lastCommand = ' '

    fun nextNumber(): Double? = tokens.getOrNull(index++)?.toDoubleOrNull()

    while (index < tokens.size) {
        val token = tokens[index]
        if (token.length == 1 && token[0].isLetter()) {
            command = token[0]
            index++
        } else if (command == 'M') {
            command = 'L'
        } else if (command == 'm') {
            command = 'l'
        } else if (command == 'Z' || command == 'z' || command == ' ') {
            return PathGeometry(commands, fillRule)
        }
        when (command) {
            'M', 'm' -> {
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                currentX = if (command == 'm') currentX + x else x
                currentY = if (command == 'm') currentY + y else y
                startX = currentX
                startY = currentY
                commands += PathCommand.MoveTo(currentX, currentY)
            }
            'L', 'l' -> {
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                currentX = if (command == 'l') currentX + x else x
                currentY = if (command == 'l') currentY + y else y
                commands += PathCommand.LineTo(currentX, currentY)
            }
            'H', 'h' -> {
                val x = nextNumber() ?: break
                currentX = if (command == 'h') currentX + x else x
                commands += PathCommand.LineTo(currentX, currentY)
            }
            'V', 'v' -> {
                val y = nextNumber() ?: break
                currentY = if (command == 'v') currentY + y else y
                commands += PathCommand.LineTo(currentX, currentY)
            }
            'C', 'c' -> {
                val x1 = nextNumber() ?: break
                val y1 = nextNumber() ?: break
                val x2 = nextNumber() ?: break
                val y2 = nextNumber() ?: break
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                val relative = command == 'c'
                val cx1 = if (relative) currentX + x1 else x1
                val cy1 = if (relative) currentY + y1 else y1
                val cx2 = if (relative) currentX + x2 else x2
                val cy2 = if (relative) currentY + y2 else y2
                currentX = if (relative) currentX + x else x
                currentY = if (relative) currentY + y else y
                commands += PathCommand.CubicTo(cx1, cy1, cx2, cy2, currentX, currentY)
                lastControlX = cx2
                lastControlY = cy2
            }
            'S', 's' -> {
                val x2 = nextNumber() ?: break
                val y2 = nextNumber() ?: break
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                val relative = command == 's'
                val cx1 = if (lastCommand in "CcSs") 2 * currentX - lastControlX else currentX
                val cy1 = if (lastCommand in "CcSs") 2 * currentY - lastControlY else currentY
                val cx2 = if (relative) currentX + x2 else x2
                val cy2 = if (relative) currentY + y2 else y2
                currentX = if (relative) currentX + x else x
                currentY = if (relative) currentY + y else y
                commands += PathCommand.CubicTo(cx1, cy1, cx2, cy2, currentX, currentY)
                lastControlX = cx2
                lastControlY = cy2
            }
            'Q', 'q' -> {
                val x1 = nextNumber() ?: break
                val y1 = nextNumber() ?: break
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                val relative = command == 'q'
                val cx = if (relative) currentX + x1 else x1
                val cy = if (relative) currentY + y1 else y1
                currentX = if (relative) currentX + x else x
                currentY = if (relative) currentY + y else y
                commands += PathCommand.QuadTo(cx, cy, currentX, currentY)
                lastControlX = cx
                lastControlY = cy
            }
            'T', 't' -> {
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                val relative = command == 't'
                val cx = if (lastCommand in "QqTt") 2 * currentX - lastControlX else currentX
                val cy = if (lastCommand in "QqTt") 2 * currentY - lastControlY else currentY
                currentX = if (relative) currentX + x else x
                currentY = if (relative) currentY + y else y
                commands += PathCommand.QuadTo(cx, cy, currentX, currentY)
                lastControlX = cx
                lastControlY = cy
            }
            'A', 'a' -> {
                val rx = nextNumber() ?: break
                val ry = nextNumber() ?: break
                val rotation = nextNumber() ?: break
                val largeArc = (nextNumber() ?: break) != 0.0
                val sweep = (nextNumber() ?: break) != 0.0
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                val endX = if (command == 'a') currentX + x else x
                val endY = if (command == 'a') currentY + y else y
                commands += arcToCubics(currentX, currentY, rx, ry, rotation, largeArc, sweep, endX, endY)
                currentX = endX
                currentY = endY
            }
            'Z', 'z' -> {
                commands += PathCommand.Close
                currentX = startX
                currentY = startY
            }
            else -> return PathGeometry(commands, fillRule)
        }
        lastCommand = command
    }
    return PathGeometry(commands, fillRule)
}

/**
 * Converts an SVG elliptical arc (endpoint parameterization) from `(x0, y0)` to `(x, y)`
 * into a list of cubic-bezier commands (SVG implementation notes F.6). Degenerate radii
 * produce a single [PathCommand.LineTo]; a zero-length arc produces nothing.
 */
fun arcToCubics(
    x0: Double,
    y0: Double,
    rxIn: Double,
    ryIn: Double,
    xAxisRotationDeg: Double,
    largeArc: Boolean,
    sweep: Boolean,
    x: Double,
    y: Double,
): List<PathCommand> {
    if (x0 == x && y0 == y) return emptyList()
    var rx = abs(rxIn)
    var ry = abs(ryIn)
    if (rx == 0.0 || ry == 0.0) return listOf(PathCommand.LineTo(x, y))

    val phi = xAxisRotationDeg * 3.141592653589793 / 180.0
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)

    // Step 1: (x1', y1') — midpoint delta in the rotated frame.
    val dx = (x0 - x) / 2.0
    val dy = (y0 - y) / 2.0
    val x1p = cosPhi * dx + sinPhi * dy
    val y1p = -sinPhi * dx + cosPhi * dy

    // Correct out-of-range radii.
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1.0) {
        val s = sqrt(lambda)
        rx *= s
        ry *= s
    }

    // Step 2: center' in the rotated frame.
    val rx2 = rx * rx
    val ry2 = ry * ry
    val x1p2 = x1p * x1p
    val y1p2 = y1p * y1p
    val numerator = max(0.0, rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2)
    val denominator = rx2 * y1p2 + ry2 * x1p2
    var coef = if (denominator == 0.0) 0.0 else sqrt(numerator / denominator)
    if (largeArc == sweep) coef = -coef
    val cxp = coef * (rx * y1p / ry)
    val cyp = coef * (-ry * x1p / rx)

    // Step 3: center in the original frame.
    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0

    // Step 4: start angle and sweep angle.
    fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
        val a = acos((if (len == 0.0) 0.0 else dot / len).coerceIn(-1.0, 1.0))
        return if (ux * vy - uy * vx < 0.0) -a else a
    }
    val ux = (x1p - cxp) / rx
    val uy = (y1p - cyp) / ry
    val vx = (-x1p - cxp) / rx
    val vy = (-y1p - cyp) / ry
    val theta1 = angle(1.0, 0.0, ux, uy)
    var deltaTheta = angle(ux, uy, vx, vy)
    val twoPi = 2.0 * 3.141592653589793
    if (!sweep && deltaTheta > 0.0) deltaTheta -= twoPi
    if (sweep && deltaTheta < 0.0) deltaTheta += twoPi

    // Step 5: split into ≤90° cubic segments.
    val segments = max(1, ceil(abs(deltaTheta) / (3.141592653589793 / 2.0)).toInt())
    val delta = deltaTheta / segments
    val alpha = 4.0 / 3.0 * tan(delta / 4.0)

    fun pointX(t: Double) = cx + rx * cos(t) * cosPhi - ry * sin(t) * sinPhi
    fun pointY(t: Double) = cy + rx * cos(t) * sinPhi + ry * sin(t) * cosPhi
    fun derivX(t: Double) = -rx * sin(t) * cosPhi - ry * cos(t) * sinPhi
    fun derivY(t: Double) = -rx * sin(t) * sinPhi + ry * cos(t) * cosPhi

    val result = ArrayList<PathCommand>(segments)
    for (i in 0 until segments) {
        val t0 = theta1 + i * delta
        val t1 = t0 + delta
        val p0x = pointX(t0)
        val p0y = pointY(t0)
        val p1x = pointX(t1)
        val p1y = pointY(t1)
        val d0x = derivX(t0)
        val d0y = derivY(t0)
        val d1x = derivX(t1)
        val d1y = derivY(t1)
        val c1x = p0x + alpha * d0x
        val c1y = p0y + alpha * d0y
        val c2x = p1x - alpha * d1x
        val c2y = p1y - alpha * d1y
        // Force the final endpoint to the exact requested coordinate to avoid drift.
        val endX = if (i == segments - 1) x else p1x
        val endY = if (i == segments - 1) y else p1y
        result += PathCommand.CubicTo(c1x, c1y, c2x, c2y, endX, endY)
    }
    return result
}

private fun tokenizePath(d: String): List<String> {
    val tokens = mutableListOf<String>()
    val number = StringBuilder()

    fun flush() {
        if (number.isNotEmpty()) {
            tokens += number.toString()
            number.clear()
        }
    }

    d.forEach { char ->
        when {
            (char == 'e' || char == 'E') && number.isNotEmpty() &&
                number.last() != 'e' && number.last() != 'E' -> number.append(char)
            char.isLetter() -> {
                flush()
                tokens += char.toString()
            }
            char == '-' || char == '+' -> {
                if (number.isNotEmpty() && (number.last() == 'e' || number.last() == 'E')) {
                    number.append(char)
                } else {
                    flush()
                    if (char == '-') number.append(char)
                }
            }
            char == '.' -> {
                if (numberContainsDot(number)) flush()
                number.append(char)
            }
            char.isDigit() -> number.append(char)
            else -> flush()
        }
    }
    flush()
    return tokens
}

private fun numberContainsDot(builder: StringBuilder): Boolean {
    for (i in 0 until builder.length) {
        if (builder[i] == '.') return true
    }
    return false
}
