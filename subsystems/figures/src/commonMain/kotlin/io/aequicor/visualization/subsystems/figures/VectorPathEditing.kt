package io.aequicor.visualization.subsystems.figures

/**
 * Minimal SVG-path point editing for the MVP vector edit mode. Parses a `d` string
 * into a flat list of on-path anchor points (the endpoints of `M`, `L`, `H`, `V` and
 * the final point of `C`/`Q`/`S`/`T` curves) so the editor can select and move them.
 * Control handles and relative commands are approximated: the whole document uses
 * absolute uppercase commands, matching what the compiler emits. Advanced vector
 * network editing (branching, bezier handles, lasso) is a documented follow-up — this
 * layer only needs to expose movable anchors without rewriting the path grammar.
 */

/** An on-path anchor: its position plus where its coordinate pair sits in the token list. */
data class VectorAnchor(
    val index: Int,
    val x: Double,
    val y: Double,
)

/** Extracts the movable anchor points of an absolute-command SVG path, in order. */
fun vectorAnchors(d: String): List<VectorAnchor> {
    val tokens = tokenize(d)
    val anchors = mutableListOf<VectorAnchor>()
    var i = 0
    var anchorIndex = 0
    while (i < tokens.size) {
        val cmd = tokens[i]
        if (cmd.length == 1 && cmd[0].isLetter()) {
            val nums = collectNumbers(tokens, i + 1)
            val consumed = nums.size
            when (cmd.uppercase()) {
                "M", "L", "T" -> forEachPair(nums) { x, y -> anchors += VectorAnchor(anchorIndex++, x, y) }
                "C" -> forEachTriplePair(nums) { x, y -> anchors += VectorAnchor(anchorIndex++, x, y) }
                "Q", "S" -> forEachDoublePair(nums) { x, y -> anchors += VectorAnchor(anchorIndex++, x, y) }
                else -> Unit // H/V/Z/A: no simple movable anchor in this MVP
            }
            i += 1 + consumed
        } else {
            i++
        }
    }
    return anchors
}

/**
 * Returns [d] with the anchor at [anchorIndex] translated by ([dx],[dy]), or null when
 * the index is out of range or the path uses commands this MVP cannot address.
 */
fun translateSvgPoint(d: String, anchorIndex: Int, dx: Double, dy: Double): String? {
    val tokens = tokenize(d).toMutableList()
    var i = 0
    var current = 0
    while (i < tokens.size) {
        val cmd = tokens[i]
        if (cmd.length == 1 && cmd[0].isLetter()) {
            val start = i + 1
            val nums = collectNumbers(tokens, start)
            val pairsForAnchor: List<Int> = when (cmd.uppercase()) {
                "M", "L", "T" -> nums.indices.step(2).toList()
                "C" -> nums.indices.step(6).map { it + 4 }.filter { it + 1 < nums.size }
                "Q", "S" -> nums.indices.step(4).map { it + 2 }.filter { it + 1 < nums.size }
                else -> emptyList()
            }
            for (pairStart in pairsForAnchor) {
                if (current == anchorIndex) {
                    val xTok = start + pairStart
                    val yTok = start + pairStart + 1
                    val x = tokens[xTok].toDoubleOrNull() ?: return null
                    val y = tokens[yTok].toDoubleOrNull() ?: return null
                    tokens[xTok] = formatNumber(x + dx)
                    tokens[yTok] = formatNumber(y + dy)
                    return tokens.joinToString(" ")
                }
                current++
            }
            i += 1 + nums.size
        } else {
            i++
        }
    }
    return null
}

private fun tokenize(d: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    fun flush() {
        if (sb.isNotEmpty()) {
            result += sb.toString()
            sb.clear()
        }
    }
    d.forEach { ch ->
        when {
            // `e`/`E` after a digit is an exponent, not a command letter.
            (ch == 'e' || ch == 'E') && sb.isNotEmpty() && sb.last().isDigit() -> sb.append(ch)
            ch.isLetter() -> {
                flush(); result += ch.toString()
            }
            ch == ',' || ch.isWhitespace() -> flush()
            // A `-`/`+` starts a new number unless it is an exponent sign (right after e/E).
            (ch == '-' || ch == '+') && sb.isNotEmpty() && sb.last() != 'e' && sb.last() != 'E' -> {
                flush(); sb.append(ch)
            }
            // A second `.` in the same token starts a new number (SVG packed decimals).
            ch == '.' && sb.contains('.') -> {
                flush(); sb.append(ch)
            }
            else -> sb.append(ch)
        }
    }
    flush()
    return result
}

private fun collectNumbers(tokens: List<String>, from: Int): List<String> {
    val nums = mutableListOf<String>()
    var i = from
    while (i < tokens.size && !(tokens[i].length == 1 && tokens[i][0].isLetter())) {
        nums += tokens[i]
        i++
    }
    return nums
}

private inline fun forEachPair(nums: List<String>, action: (Double, Double) -> Unit) {
    var i = 0
    while (i + 1 < nums.size) {
        val x = nums[i].toDoubleOrNull(); val y = nums[i + 1].toDoubleOrNull()
        if (x != null && y != null) action(x, y)
        i += 2
    }
}

private inline fun forEachDoublePair(nums: List<String>, action: (Double, Double) -> Unit) {
    var i = 0
    while (i + 3 < nums.size) {
        val x = nums[i + 2].toDoubleOrNull(); val y = nums[i + 3].toDoubleOrNull()
        if (x != null && y != null) action(x, y)
        i += 4
    }
}

private inline fun forEachTriplePair(nums: List<String>, action: (Double, Double) -> Unit) {
    var i = 0
    while (i + 5 < nums.size) {
        val x = nums[i + 4].toDoubleOrNull(); val y = nums[i + 5].toDoubleOrNull()
        if (x != null && y != null) action(x, y)
        i += 6
    }
}

private fun formatNumber(value: Double): String {
    val rounded = value.toLong()
    return if (value == rounded.toDouble()) rounded.toString() else ((value * 100).toLong() / 100.0).toString()
}
