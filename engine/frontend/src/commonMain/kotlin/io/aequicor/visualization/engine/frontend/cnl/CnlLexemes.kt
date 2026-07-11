package io.aequicor.visualization.engine.frontend.cnl

/**
 * Public lexeme façade over the internal [CnlGrammar] helpers, for container-extension
 * grammars (e.g. the diagram CNL in `:subsystems:diagrams-slm`). Extensions must reuse
 * these so numbers, text escaping and literal scanning stay byte-identical with the core
 * CNL tokenizer — the grammar–emitter symmetry contract depends on it.
 */
public object CnlLexemes {
    /** Canonical number rendering: drop a trailing `.0`. */
    public fun num(value: Double): String = CnlGrammar.num(value)

    /** Escapes a raw string for a `«…»` text literal (`\\`, `\»`, `\n`, `\r`). */
    public fun escapeText(value: String): String = CnlGrammar.escapeText(value)

    /** Renders a `«…»` text literal with [escapeText] applied. */
    public fun quoteText(value: String): String = CnlGrammar.quoteText(value)

    /** Result of [scanTextLiteral]: unescaped text, index of the closing char, termination flag. */
    public data class TextScan(val text: String, val closeIndex: Int, val terminated: Boolean)

    /**
     * Scans a `«…»`/`"…"` literal body starting at [start] (the index just after the opener),
     * honoring backslash escapes (`\<close>`, `\\`, `\n`, `\r`).
     */
    public fun scanTextLiteral(line: String, start: Int, close: Char): TextScan {
        val scan = CnlGrammar.scanTextLiteral(line, start, close)
        return TextScan(scan.text, scan.closeIndex, scan.terminated)
    }
}
