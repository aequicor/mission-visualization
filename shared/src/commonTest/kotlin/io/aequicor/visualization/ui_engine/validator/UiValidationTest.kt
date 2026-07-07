package io.aequicor.visualization.ui_engine.validator

import io.aequicor.visualization.ui_engine.mv_yaml_source.SampleUiYaml
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnosticSeverity
import io.aequicor.visualization.ui_engine.validator.loadUiDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiValidationTest {
    @Test
    fun sampleLoadsWithoutErrors() {
        val result = loadUiDocument(SampleUiYaml)

        val success = assertIs<UiLoadResult.Success>(result)
        assertFalse(success.diagnostics.any { it.severity == UiDiagnosticSeverity.Error })
    }

    @Test
    fun duplicateIdsAreErrors() {
        val result = loadUiDocument(
            """
            version: 1
            title: Duplicate ids
            screens:
              - id: dashboard
                title: Dashboard
                children:
                  - id: repeated
                    type: card
                  - id: repeated
                    type: button
            """.trimIndent(),
        )

        val failure = assertIs<UiLoadResult.Failure>(result)
        assertTrue(failure.diagnostics.any { it.message.contains("Duplicate target id 'repeated'") })
    }

    @Test
    fun unknownNodeTypeIsWarningAndStillLoads() {
        val result = loadUiDocument(
            """
            version: 1
            title: Unknown type
            screens:
              - id: dashboard
                title: Dashboard
                children:
                  - id: custom-widget
                    type: customWidget
            """.trimIndent(),
        )

        val success = assertIs<UiLoadResult.Success>(result)
        assertTrue(success.diagnostics.any { it.severity == UiDiagnosticSeverity.Warning && it.message.contains("customWidget") })
    }

    @Test
    fun missingRequiredFieldsAreErrors() {
        val result = loadUiDocument(
            """
            version: 1
            title: Missing fields
            screens:
              - title: Dashboard
                children:
                  - id: missing-type
            """.trimIndent(),
        )

        val failure = assertIs<UiLoadResult.Failure>(result)
        assertTrue(failure.diagnostics.any { it.message.contains("Screen id is required") })
        assertTrue(failure.diagnostics.any { it.message.contains("Node type is required") })
    }

    @Test
    fun brokenReferencesAreErrors() {
        val result = loadUiDocument(
            """
            version: 1
            title: Broken refs
            screens:
              - id: dashboard
                title: Dashboard
                children:
                  - id: action
                    type: button
                    action:
                      type: navigate
                      target: missing-screen
            scenarios:
              - id: flow
                title: Flow
                steps:
                  - screenId: dashboard
                    nodeId: missing-node
                    action: Select missing node.
            """.trimIndent(),
        )

        val failure = assertIs<UiLoadResult.Failure>(result)
        assertTrue(failure.diagnostics.any { it.message.contains("Action target 'missing-screen'") })
        assertTrue(failure.diagnostics.any { it.message.contains("nodeId 'missing-node'") })
    }

    @Test
    fun invalidLayoutAndStyleTokensAreErrors() {
        val result = loadUiDocument(
            """
            version: 1
            title: Bad tokens
            screens:
              - id: dashboard
                title: Dashboard
                layout:
                  type: freestyle
                  padding: huge
                children:
                  - id: card
                    type: card
                    style:
                      tone: loud
            """.trimIndent(),
        )

        val failure = assertIs<UiLoadResult.Failure>(result)
        assertTrue(failure.diagnostics.any { it.message.contains("Unknown layout type 'freestyle'") })
        assertTrue(failure.diagnostics.any { it.message.contains("Unknown padding token 'huge'") })
        assertTrue(failure.diagnostics.any { it.message.contains("Unknown tone token 'loud'") })
    }
}
