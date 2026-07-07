package io.aequicor.visualization.engine.ir.serialization

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode

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

/** Result of [parseDesignNode]: a single node subtree instead of a whole document. */
sealed interface DesignNodeParseResult {
    val diagnostics: List<DesignDiagnostic>

    data class Success(
        val node: DesignNode,
        override val diagnostics: List<DesignDiagnostic> = emptyList(),
    ) : DesignNodeParseResult

    data class Failure(
        override val diagnostics: List<DesignDiagnostic>,
    ) : DesignNodeParseResult
}

fun DesignNodeParseResult.nodeOrNull(): DesignNode? =
    (this as? DesignNodeParseResult.Success)?.node
