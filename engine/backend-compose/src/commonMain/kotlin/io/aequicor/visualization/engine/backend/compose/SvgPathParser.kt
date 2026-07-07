package io.aequicor.visualization.engine.backend.compose

import androidx.compose.ui.graphics.Path

/**
 * Minimal SVG path-data parser: M/L/H/V/C/S/Q/T/Z with relative variants. Arcs are
 * not supported and end the parse gracefully (partial path is returned).
 */
internal fun parseSvgPath(d: String): Path {
    val path = Path()
    val tokens = tokenizePath(d)
    var index = 0
    var command = ' '
    var currentX = 0f
    var currentY = 0f
    var startX = 0f
    var startY = 0f
    var lastControlX = 0f
    var lastControlY = 0f
    var lastCommand = ' '

    fun nextNumber(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()

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
            // Numbers cannot follow a close/unknown command; stop instead of looping.
            return path
        }
        when (command) {
            'M', 'm' -> {
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                currentX = if (command == 'm') currentX + x else x
                currentY = if (command == 'm') currentY + y else y
                startX = currentX
                startY = currentY
                path.moveTo(currentX, currentY)
            }
            'L', 'l' -> {
                val x = nextNumber() ?: break
                val y = nextNumber() ?: break
                currentX = if (command == 'l') currentX + x else x
                currentY = if (command == 'l') currentY + y else y
                path.lineTo(currentX, currentY)
            }
            'H', 'h' -> {
                val x = nextNumber() ?: break
                currentX = if (command == 'h') currentX + x else x
                path.lineTo(currentX, currentY)
            }
            'V', 'v' -> {
                val y = nextNumber() ?: break
                currentY = if (command == 'v') currentY + y else y
                path.lineTo(currentX, currentY)
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
                path.cubicTo(cx1, cy1, cx2, cy2, currentX, currentY)
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
                path.cubicTo(cx1, cy1, cx2, cy2, currentX, currentY)
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
                path.quadraticTo(cx, cy, currentX, currentY)
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
                path.quadraticTo(cx, cy, currentX, currentY)
                lastControlX = cx
                lastControlY = cy
            }
            'Z', 'z' -> {
                path.close()
                currentX = startX
                currentY = startY
            }
            else -> return path
        }
        lastCommand = command
    }
    return path
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
            // Exponent markers belong to the number being built ("1.5e-4"), and only
            // then; a bare 'e' is treated as an (invalid) command letter below.
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
                if (number.contains('.')) flush()
                number.append(char)
            }
            char.isDigit() -> number.append(char)
            else -> flush()
        }
    }
    flush()
    return tokens
}

private fun StringBuilder.contains(char: Char): Boolean {
    for (i in 0 until length) {
        if (this[i] == char) return true
    }
    return false
}
