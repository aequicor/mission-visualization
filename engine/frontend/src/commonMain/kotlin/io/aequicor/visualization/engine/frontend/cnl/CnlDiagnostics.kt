package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector

/**
 * The CNL rule catalog — the dedicated place for CNL error handling. Every diagnostic
 * names **which rule** was broken and **how to fix it** (with a correct example), so a
 * generator can self-correct from the message alone. The catalog is mirrored human-readably
 * in `SLM-SKILL.md`.
 *
 * Each message is emitted as `[CNL:<id>] <what>. [Did you mean …] Rule: … How to fix: …`.
 * CNL problems are **warnings**, not errors: the element still compiles from its valid
 * phrases, and the author sees exactly what to repair.
 */
internal enum class CnlRule(val id: String, val ruleText: String, val fixText: String) {
    UnknownKeyword(
        "unknown-keyword",
        "After the element noun only known properties may follow (color, size, radius, gap, padding, rotation, position, opacity, stroke, direction, align).",
        "Check the keyword spelling. For example: \"color #00B843\", \"radius 15\", \"gap 16\".",
    ),
    MissingValue(
        "missing-value",
        "A property needs a value right after its keyword.",
        "Add a value. For example: \"color #00B843\", \"radius 15\".",
    ),
    BadColor(
        "bad-color",
        "A color is #RRGGBB, #RGB, #RRGGBBAA or \$token.",
        "For example: \"color #00B843\" or \"color \$color.accent\".",
    ),
    BadNumber(
        "bad-number",
        "A property value must be a number.",
        "For example: \"radius 15\", \"gap 16\", \"opacity 0.5\".",
    ),
    IncompleteSize(
        "incomplete-size",
        "Size is \"<width> by <height>\" (two numbers joined by \"by\"/\"x\"/\"×\").",
        "For example: \"120 by 15\" or \"size 120 by 15\".",
    ),
    UnterminatedText(
        "unterminated-text",
        "Quoted text must be closed: «…» or \"…\".",
        "For example: Text «Active missions».",
    ),
    BadDirection(
        "bad-direction",
        "Alignment direction is one of: top, bottom, left, right, center.",
        "For example: \"align center\".",
    ),
    StrayNumber(
        "stray-number",
        "A lone number must belong to a property (size, rotation, radius…).",
        "For example: \"size 120 by 15\", \"rotation 30 degrees\", \"radius 15\".",
    ),
    ;
}

internal object CnlDiagnostics {
    /** Emits one CNL rule violation as a self-explaining warning at [line]. */
    fun warn(
        diagnostics: DiagnosticCollector,
        rule: CnlRule,
        line: Int,
        what: String,
        suggestion: String? = null,
    ) {
        val hint = suggestion?.let { " Did you mean \"$it\"." }.orEmpty()
        diagnostics.warning(
            "[CNL:${rule.id}] $what.$hint Rule: ${rule.ruleText} How to fix: ${rule.fixText}",
            line,
            blockPath = "cnl",
        )
    }
}
