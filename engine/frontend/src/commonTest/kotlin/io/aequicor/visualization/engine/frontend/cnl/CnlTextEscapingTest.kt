package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.VariableValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `«…»` literals must survive emit → parse for any content: the emitter escapes
 * `»`/`\`/newlines via [CnlGrammar.escapeText] and every tokenizer decodes the same escapes
 * in [CnlGrammar.scanTextLiteral]. Regression for emitted text corrupting on reparse.
 */
class CnlTextEscapingTest {
    private fun compile(src: String): DesignDocument {
        val result = compileSlm(slm(src) + "\n")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document)
    }

    private fun compileBody(body: String): DesignNode = compile(
        """
        ---
        screen: demo
        sourceLocale: en-US
        targetLocales: [en-US]
        ---

        # Demo

        $body
        """.trimIndent(),
    ).pages.single().children.single()

    private fun textLeaf(body: String): DesignNode =
        compileBody(body).allDescendants().first { it.kind is DesignNodeKind.Text }

    private fun assertSentenceRoundTrips(sentence: String): DesignNode {
        val node1 = textLeaf(sentence)
        val emitted = CnlEmitter.emitSentence(node1)
        val node2 = textLeaf(emitted)
        assertEquals(node1.kind, node2.kind, "text kind survives reparse")
        assertEquals(node1.name, node2.name, "name survives reparse")
        assertEquals(emitted, CnlEmitter.emitSentence(node2), "emit is idempotent")
        return node2
    }

    @Test
    fun closingGuillemetInVisibleTextRoundTrips() {
        val node = assertSentenceRoundTrips("""Text «a \» b» size 14""")
        val kind = node.kind as DesignNodeKind.Text
        assertEquals("a » b", kind.content?.defaultText)
    }

    @Test
    fun backslashInVisibleTextRoundTrips() {
        val node = assertSentenceRoundTrips("""Text «C:\\temp» size 14""")
        val kind = node.kind as DesignNodeKind.Text
        assertEquals("""C:\temp""", kind.content?.defaultText)
    }

    @Test
    fun newlineEscapeInVisibleTextRoundTrips() {
        val node = assertSentenceRoundTrips("""Text «line one\nline two» size 14""")
        val kind = node.kind as DesignNodeKind.Text
        assertEquals("line one\nline two", kind.content?.defaultText)
    }

    @Test
    fun balancedNestedGuillemetsParseRawAndRoundTrip() {
        // Agents quote inside Russian prose with raw «…» — the scanner must balance them.
        val node = textLeaf("""Text «Признак «нет» — отдельное значение» size 14""")
        val kind = node.kind as DesignNodeKind.Text
        assertEquals("Признак «нет» — отдельное значение", kind.content?.defaultText)

        val reparsed = assertSentenceRoundTrips(CnlEmitter.emitSentence(node))
        assertEquals(kind.content?.defaultText, (reparsed.kind as DesignNodeKind.Text).content?.defaultText)
    }

    @Test
    fun legacyRawOpenerWithEscapedCloserStillTerminates() {
        // Older emitter escaped only `»`: a literal could hold a raw `«` next to `\»`.
        val node = textLeaf("""Text «a «b\» c» size 14""")
        val kind = node.kind as DesignNodeKind.Text
        assertEquals("a «b» c", kind.content?.defaultText)
    }

    @Test
    fun unpairedInnerOpenerStillClosesAtLineEnd() {
        val node = textLeaf("""Text «см. «сноску» size 14""")
        val kind = node.kind as DesignNodeKind.Text
        assertEquals("см. «сноску", kind.content?.defaultText)
    }

    @Test
    fun closingGuillemetInLayerNameRoundTrips() {
        val node = assertSentenceRoundTrips("""Text «Hi» name «Badge \» tail» size 14""")
        assertEquals("Badge » tail", node.name)
    }

    @Test
    fun closingGuillemetInStringVariableRoundTrips() {
        val source = """
            ---
            screen: demo
            sourceLocale: en-US
            targetLocales: [en-US]
            ---

            # Collection theme (modes light default light)

            String copy.quote light «a \» b»

            # Demo

            Rectangle 10 by 10 color #FFFFFF
        """.trimIndent()
        val document = compile(source)
        val variable = assertNotNull(document.variables.collections["theme"]?.vars?.get("copy.quote"))
        assertEquals(VariableValue.TextValue("a » b"), variable.values["light"])

        // emitVariables must re-escape so the emitted section recompiles to the same value.
        val emitted = CnlEmitter.emitVariables(document)
        assertTrue("""«a \» b»""" in emitted, "escaped literal in emitted variables:\n$emitted")
    }
}
