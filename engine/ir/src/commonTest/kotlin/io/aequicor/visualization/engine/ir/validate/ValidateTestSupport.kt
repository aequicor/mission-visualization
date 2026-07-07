package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.resolve.ResolveContext
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun parseDocument(json: String): DesignDocument =
    assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

internal fun validate(
    json: String,
    context: ResolveContext = ResolveContext(),
    options: ValidationOptions = ValidationOptions(),
): List<DesignDiagnostic> = validateDesignDocument(parseDocument(json), context, options)

internal fun List<DesignDiagnostic>.assertHas(
    code: String,
    severity: DesignSeverity? = null,
    messagePart: String? = null,
) {
    val matches = filter { diagnostic ->
        diagnostic.code == code &&
            (severity == null || diagnostic.severity == severity) &&
            (messagePart == null || messagePart in diagnostic.message)
    }
    assertTrue(
        matches.isNotEmpty(),
        "expected a $code diagnostic" +
            (messagePart?.let { " containing '$it'" } ?: "") +
            "; got: ${joinToString("\n") { "${it.code} ${it.severity}: ${it.message}" }}",
    )
}

internal fun List<DesignDiagnostic>.assertNone(code: String) {
    val matches = filter { it.code == code }
    assertTrue(matches.isEmpty(), "expected no $code diagnostics; got: $matches")
}
