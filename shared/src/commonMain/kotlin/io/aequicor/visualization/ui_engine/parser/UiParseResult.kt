package io.aequicor.visualization.ui_engine.parser

import io.aequicor.visualization.ui_engine.ui_document_ir.UiDiagnostic
import io.aequicor.visualization.ui_engine.ui_document_ir.UiDocument

sealed interface UiParseResult {
    data class Success(
        val document: UiDocument,
        val diagnostics: List<UiDiagnostic> = emptyList(),
    ) : UiParseResult

    data class Failure(
        val diagnostics: List<UiDiagnostic>,
    ) : UiParseResult
}

