package io.aequicor.visualization.ui_engine.parser

import io.aequicor.visualization.ui_engine.mv_yaml_source.SampleUiYaml
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnosticSeverity
import io.aequicor.visualization.ui_engine.parser.parseUiDocumentYaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiParserTest {
    @Test
    fun parsesStandaloneSampleYamlIntoUiDocument() {
        val result = parseUiDocumentYaml(SampleUiYaml)

        val success = assertIs<UiParseResult.Success>(result)
        assertEquals("Mission Control Onboarding", success.document.title)
        assertEquals(2, success.document.screens.size)
        assertEquals("dashboard", success.document.screens.first().id)
        assertEquals("dashboard-topbar", success.document.screens.first().children.first().id)
        assertTrue(success.document.source?.line == 1)
        assertTrue(success.document.screens.first().children.first().source?.line ?: 0 > 1)
    }

    @Test
    fun reportsSyntaxErrorsWithLineAndColumn() {
        val result = parseUiDocumentYaml(
            """
            version: 1
              title: Bad indent
            """.trimIndent(),
        )

        val failure = assertIs<UiParseResult.Failure>(result)
        assertEquals(2, failure.diagnostics.single().source.line)
        assertTrue(failure.diagnostics.single().source.column > 1)
        assertTrue(failure.diagnostics.single().message.contains("Unexpected indentation"))
    }

    @Test
    fun unknownFieldsBecomeWarnings() {
        val result = parseUiDocumentYaml(
            """
            version: 1
            title: Unknown fields
            unexpectedRoot: value
            screens: []
            """.trimIndent(),
        )

        val success = assertIs<UiParseResult.Success>(result)
        assertEquals(UiDiagnosticSeverity.Warning, success.diagnostics.single().severity)
        assertTrue(success.diagnostics.single().message.contains("unexpectedRoot"))
    }
}
