package io.aequicor.visualization.engine.frontend.expr

/**
 * Parses the inside of a `{{...}}` expression. Garbage never throws: any text that
 * does not match the grammar returns [SlmExpression.Raw] and the caller is expected
 * to emit a warning diagnostic. Expressions are never localized or sluggified.
 */
fun parseSlmExpression(text: String): SlmExpression {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return SlmExpression.Raw(trimmed)
    val tokens = tokenize(trimmed) ?: return SlmExpression.Raw(trimmed)
    return Parser(tokens).parseExpression() ?: SlmExpression.Raw(trimmed)
}

private sealed interface Token {
    data class Ident(val text: String) : Token

    data class Num(val value: Double) : Token

    data class Str(val value: String) : Token

    data class Op(val op: ComparisonOp) : Token

    data object Dot : Token
}

private fun tokenize(text: String): List<Token>? {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == ' ' || c == '\t' -> i++
            c == '.' -> {
                tokens += Token.Dot
                i++
            }
            c == '\'' -> {
                val end = text.indexOf('\'', i + 1)
                if (end < 0) return null
                tokens += Token.Str(text.substring(i + 1, end))
                i = end + 1
            }
            c.isDigit() || (c == '-' && text.getOrNull(i + 1)?.isDigit() == true) -> {
                val start = i
                i++
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                val token = text.substring(start, i)
                val value = token.toDoubleOrNull() ?: return null
                tokens += Token.Num(value)
            }
            c.isLetter() || c == '_' -> {
                val start = i
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                tokens += Token.Ident(text.substring(start, i))
            }
            c == '=' || c == '!' || c == '<' || c == '>' -> {
                val two = text.substring(i, minOf(i + 2, text.length))
                val op = ComparisonOp.fromSymbol(two)
                    ?: ComparisonOp.fromSymbol(c.toString())
                    ?: return null
                tokens += Token.Op(op)
                i += op.symbol.length
            }
            else -> return null
        }
    }
    return tokens
}

private class Parser(private val tokens: List<Token>) {
    private var pos = 0

    fun parseExpression(): SlmExpression? {
        val repeat = tryParseRepeat()
        if (repeat != null) return repeat
        val left = parseOperand() ?: return null
        if (pos == tokens.size) return left
        val op = (next() as? Token.Op)?.op ?: return null
        val right = parseOperand() ?: return null
        if (pos != tokens.size) return null
        return SlmExpression.Comparison(left, op, right)
    }

    private fun tryParseRepeat(): SlmExpression.Repeat? {
        val item = tokens.getOrNull(0) as? Token.Ident ?: return null
        val keyword = tokens.getOrNull(1) as? Token.Ident ?: return null
        if (keyword.text != "in") return null
        pos = 2
        val collection = parsePath() ?: run {
            pos = 0
            return null
        }
        if (pos != tokens.size) {
            pos = 0
            return null
        }
        return SlmExpression.Repeat(item.text, collection)
    }

    private fun parseOperand(): SlmExpression? =
        when (val token = next()) {
            is Token.Num -> SlmExpression.Literal.Num(token.value)
            is Token.Str -> SlmExpression.Literal.Str(token.value)
            is Token.Ident -> when (token.text) {
                "true" -> SlmExpression.Literal.Bool(true)
                "false" -> SlmExpression.Literal.Bool(false)
                else -> {
                    pos--
                    parsePath()
                }
            }
            else -> null
        }

    private fun parsePath(): SlmExpression.Path? {
        val first = next() as? Token.Ident ?: return null
        val segments = mutableListOf(first.text)
        while (tokens.getOrNull(pos) == Token.Dot) {
            pos++
            val segment = next() as? Token.Ident ?: return null
            segments += segment.text
        }
        return SlmExpression.Path(segments)
    }

    private fun next(): Token? = tokens.getOrNull(pos)?.also { pos++ }
}
