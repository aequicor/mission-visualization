package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNodeKind

/**
 * IR-HANDOFF — handoff annotations, measurements, export settings.
 *
 * - IR-HANDOFF-001 (error): annotation target that does not exist (both document
 *   handoff annotations and annotation-kind nodes).
 * - IR-HANDOFF-002 (error): measurement endpoint that does not exist.
 * - IR-HANDOFF-003 (error): measurement from a node to itself.
 * - IR-HANDOFF-004 (error): export scale outside (0, 4].
 * - IR-HANDOFF-005 (warning): export suffix that is not filename-safe.
 */
internal object HandoffExportChecks {

    private val FilenameSafeSuffix = Regex("""[A-Za-z0-9._@-]*""")

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.document.handoff.annotations.forEach { annotation ->
            if (annotation.target.isNotEmpty() && annotation.target !in ctx.allNodeIds) {
                add(
                    validationError(
                        "IR-HANDOFF-001",
                        "Handoff annotation '${annotation.id.ifEmpty { annotation.text.take(40) }}' " +
                            "targets unknown node '${annotation.target}'",
                    ),
                )
            }
        }
        ctx.entries.forEach { entry ->
            val annotation = (entry.node.kind as? DesignNodeKind.Annotation)?.annotation ?: return@forEach
            if (annotation.target.isNotEmpty() && annotation.target !in ctx.allNodeIds) {
                add(
                    validationError(
                        "IR-HANDOFF-001",
                        "Annotation node '${entry.node.id}' targets unknown node '${annotation.target}'",
                        ctx.location(entry.node),
                    ),
                )
            }
        }

        ctx.document.handoff.measurements.forEach { measurement ->
            listOf(measurement.from, measurement.to).forEach { endpoint ->
                if (endpoint !in ctx.allNodeIds) {
                    add(
                        validationError(
                            "IR-HANDOFF-002",
                            "Measurement endpoint '$endpoint' does not exist",
                        ),
                    )
                }
            }
            if (measurement.from == measurement.to) {
                add(
                    validationError(
                        "IR-HANDOFF-003",
                        "Measurement from '${measurement.from}' to itself is meaningless",
                    ),
                )
            }
        }

        ctx.entries.forEach { entry ->
            entry.node.exportSettings.forEachIndexed { index, setting ->
                if (setting.scale <= 0.0 || setting.scale > 4.0) {
                    add(
                        validationError(
                            "IR-HANDOFF-004",
                            "Export setting $index of '${entry.node.id}' has scale ${setting.scale}; " +
                                "expected within (0, 4]",
                            ctx.location(entry.node),
                        ),
                    )
                }
                if (!FilenameSafeSuffix.matches(setting.suffix)) {
                    add(
                        validationWarning(
                            "IR-HANDOFF-005",
                            "Export suffix '${setting.suffix}' of '${entry.node.id}' is not filename-safe",
                            ctx.location(entry.node),
                        ),
                    )
                }
            }
        }
    }
}
