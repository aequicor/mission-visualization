package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import kotlin.test.Test
import kotlin.test.assertTrue

/** Each CNL rule violation must report its rule id and a "how to fix" hint. */
class CnlDiagnosticsTest {
    private fun messages(line: String): List<String> {
        val diagnostics = DiagnosticCollector("test.layout.md")
        CnlParser.parseElement(line, 1, 1, diagnostics)
        return diagnostics.diagnostics.map { it.message }
    }

    private fun assertRule(line: String, ruleId: String) {
        val messages = messages(line)
        assertTrue(messages.any { "[CNL:$ruleId]" in it && "Как исправить" in it }, "«$line» → $messages")
    }

    @Test fun unknownKeyword() = assertRule("Прямоугольник 10 на 20 блабла", "unknown-keyword")

    @Test fun missingColorValue() = assertRule("Прямоугольник 10 на 10 цвет", "missing-value")

    @Test fun badColor() = assertRule("Прямоугольник 10 на 10 цвет синий", "bad-color")

    @Test fun incompleteSize() = assertRule("Прямоугольник 10 на 10 размер", "incomplete-size")

    @Test fun strayNumber() = assertRule("Прямоугольник 10 на 10 42", "stray-number")

    @Test fun badDirection() = assertRule("Прямоугольник 10 на 10 родительский контейнер вбок", "bad-direction")

    @Test fun unterminatedText() = assertRule("Текст «Активные миссии размер 20", "unterminated-text")
}
