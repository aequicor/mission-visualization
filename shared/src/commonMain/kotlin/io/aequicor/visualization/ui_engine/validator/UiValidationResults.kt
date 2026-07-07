package io.aequicor.visualization.ui_engine.validator

import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnostic
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnosticSeverity
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument

data class UiValidationResult(
    val diagnostics: List<UiDiagnostic> = emptyList(),
) {
    val hasErrors: Boolean
        get() = diagnostics.any { it.severity == UiDiagnosticSeverity.Error }
}

sealed interface UiLoadResult {
    data class Success(
        val document: UiDocument,
        val diagnostics: List<UiDiagnostic> = emptyList(),
    ) : UiLoadResult

    data class Failure(
        val diagnostics: List<UiDiagnostic>,
    ) : UiLoadResult
}

