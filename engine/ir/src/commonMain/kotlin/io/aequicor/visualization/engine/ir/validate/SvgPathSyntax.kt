package io.aequicor.visualization.engine.ir.validate

/**
 * Minimal pure-Kotlin sanity check of SVG path data grammar for the validator.
 * Deliberately local to `validate/` — the real path renderer (SvgPathParser) lives
 * in `engine/backend-compose`, which the IR module must not depend on.
 *
 * Checked: known command letters, numeric arguments, per-command argument counts
 * (in complete groups, so implicit command repetition is accepted), and that the
 * path starts with a moveto. Not checked: arc-flag 0/1 ranges or geometry.
 */
internal fun svgPathSyntaxError(d: String): String? {
    val argCounts = mapOf(
        'M' to 2, 'L' to 2, 'T' to 2,
        'H' to 1, 'V' to 1,
        'C' to 6, 'S' to 4, 'Q' to 4,
        'A' to 7, 'Z' to 0,
    )
    var index = 0
    var sawCommand = false

    fun skipSeparators() {
        while (index < d.length && (d[index].isWhitespace() || d[index] == ',')) index++
    }

    fun readNumber(): Boolean {
        val start = index
        if (index < d.length && (d[index] == '+' || d[index] == '-')) index++
        var digits = 0
        while (index < d.length && d[index].isDigit()) {
            index++
            digits++
        }
        if (index < d.length && d[index] == '.') {
            index++
            while (index < d.length && d[index].isDigit()) {
                index++
                digits++
            }
        }
        if (digits == 0) {
            index = start
            return false
        }
        if (index < d.length && (d[index] == 'e' || d[index] == 'E')) {
            index++
            if (index < d.length && (d[index] == '+' || d[index] == '-')) index++
            if (index >= d.length || !d[index].isDigit()) return false
            while (index < d.length && d[index].isDigit()) index++
        }
        return true
    }

    skipSeparators()
    if (index >= d.length) return "empty path data"
    while (index < d.length) {
        val command = d[index].uppercaseChar()
        val expected = argCounts[command]
            ?: return "unknown path command '${d[index]}' at offset $index"
        if (!sawCommand && command != 'M') {
            return "path data must start with a moveto, found '${d[index]}'"
        }
        sawCommand = true
        index++
        skipSeparators()
        var numbers = 0
        while (index < d.length && d[index].uppercaseChar() !in argCounts) {
            if (!readNumber()) return "expected a number at offset $index in path data"
            numbers++
            skipSeparators()
        }
        if (expected == 0) {
            if (numbers != 0) return "'$command' takes no arguments, found $numbers"
        } else if (numbers == 0 || numbers % expected != 0) {
            return "'$command' needs argument groups of $expected, found $numbers"
        }
    }
    return null
}
