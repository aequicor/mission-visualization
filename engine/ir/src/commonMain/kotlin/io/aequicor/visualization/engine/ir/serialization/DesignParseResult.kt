package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument

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
