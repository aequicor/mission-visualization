package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import kotlin.test.Test
import kotlin.test.assertTrue

/** Each CNL rule violation must report its rule id and a "How to fix" hint. */
class CnlDiagnosticsTest {
    private fun messages(line: String): List<String> {
        val diagnostics = DiagnosticCollector("test.layout.md")
        CnlParser.parseElement(line, 1, 1, diagnostics)
        return diagnostics.diagnostics.map { it.message }
    }

    private fun assertRule(line: String, ruleId: String) {
        val messages = messages(line)
        assertTrue(messages.any { "[CNL:$ruleId]" in it && "How to fix" in it }, "\"$line\" → $messages")
    }

    @Test fun unknownKeyword() = assertRule("Rectangle 10 by 20 blabla", "unknown-keyword")

    @Test fun missingColorValue() = assertRule("Rectangle 10 by 10 color", "missing-value")

    @Test fun badColor() = assertRule("Rectangle 10 by 10 color blue", "bad-color")

    @Test fun incompleteSize() = assertRule("Rectangle 10 by 10 size", "incomplete-size")

    @Test fun strayNumber() = assertRule("Rectangle 10 by 10 42", "stray-number")

    @Test fun badDirection() = assertRule("Rectangle 10 by 10 align sideways", "bad-direction")

    @Test fun unterminatedText() = assertRule("Text «Active missions size 20", "unterminated-text")
}
