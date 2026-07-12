package io.aequicor.visualization.editor.ui

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.SourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceDiagnosticsFormattingTest {
    @Test
    fun formatsDiagnosticsAsAgentFriendlyCopyableText() {
        val diagnostics = listOf(
            DesignDiagnostic(
                severity = DesignSeverity.Error,
                code = "SLM-001",
                location = SourceLocation(file = "mission.layout.md", line = 12),
                message = "Unknown node type",
            ),
            DesignDiagnostic(
                severity = DesignSeverity.Warning,
                location = SourceLocation(file = "mission.layout.md", line = 18),
                message = "Missing value",
            ),
        )

        assertEquals(
            "ERROR [SLM-001] mission.layout.md:12 — Unknown node type\n" +
                "WARNING mission.layout.md:18 — Missing value",
            formatSourceDiagnostics(diagnostics),
        )
    }
}
