package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector

/**
 * The CNL rule catalog — the dedicated place for CNL error handling. Every diagnostic
 * names **which rule** was broken and **how to fix it** (with a correct example), so a
 * generator (DeepSeek) can self-correct from the message alone. The catalog is mirrored
 * human-readably in `SLM-SKILL.md`.
 *
 * Each message is emitted as `[CNL:<id>] <what>. [Возможно: …] Правило: … Как исправить: …`.
 * CNL problems are **warnings**, not errors: the element still compiles from its valid
 * phrases, and the author sees exactly what to repair.
 */
internal enum class CnlRule(val id: String, val ruleText: String, val fixText: String) {
    UnknownKeyword(
        "unknown-keyword",
        "После существительного-элемента идут только известные свойства (цвет, размер, радиус, отступ, паддинги, поворот, позиция, прозрачность, обводка, направление, выравнивание).",
        "Проверьте написание ключевого слова. Например: «цвет #00B843», «радиус 15», «отступ 16».",
    ),
    MissingValue(
        "missing-value",
        "У свойства должно быть значение сразу после ключевого слова.",
        "Добавьте значение. Например: «цвет #00B843», «радиус 15».",
    ),
    BadColor(
        "bad-color",
        "Цвет задаётся как #RRGGBB, #RGB, #RRGGBBAA или \$токен.",
        "Например: «цвет #00B843» или «цвет \$color.accent».",
    ),
    BadNumber(
        "bad-number",
        "Значение свойства должно быть числом.",
        "Например: «радиус 15», «отступ 16», «прозрачность 0.5».",
    ),
    IncompleteSize(
        "incomplete-size",
        "Размер задаётся как «<ширина> на <высота>» (два числа через «на»/«x»/«×»).",
        "Например: «120 на 15» или «размер 120 на 15».",
    ),
    UnterminatedText(
        "unterminated-text",
        "Текст в кавычках должен быть закрыт: «…» или \"…\".",
        "Например: Текст «Активные миссии».",
    ),
    BadDirection(
        "bad-direction",
        "Направление выравнивания: вверх, вниз, влево, вправо, центр.",
        "Например: «родительский контейнер вверх».",
    ),
    StrayNumber(
        "stray-number",
        "Отдельное число должно относиться к свойству (размеру, повороту, радиусу…).",
        "Например: «размер 120 на 15», «поворот 30 градусов», «радиус 15».",
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
        val hint = suggestion?.let { " Возможно, вы имели в виду «$it»." }.orEmpty()
        diagnostics.warning(
            "[CNL:${rule.id}] $what.$hint Правило: ${rule.ruleText} Как исправить: ${rule.fixText}",
            line,
            blockPath = "cnl",
        )
    }
}
