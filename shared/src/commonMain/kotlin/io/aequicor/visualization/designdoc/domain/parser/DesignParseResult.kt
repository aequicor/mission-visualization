package io.aequicor.visualization.designdoc.domain.parser

import io.aequicor.visualization.designdoc.domain.model.DesignDiagnostic
import io.aequicor.visualization.designdoc.domain.model.DesignDocument

sealed interface DesignParseResult {
    val diagnostics: List<DesignDiagnostic>

    data class Success(
        val document: DesignDocument,
        override val diagnostics: List<DesignDiagnostic> = emptyList(),
    ) : DesignParseResult

    data class Failure(
        override val diagnostics: List<DesignDiagnostic>,
    ) : DesignParseResult
}

fun DesignParseResult.documentOrNull(): DesignDocument? =
    (this as? DesignParseResult.Success)?.document
