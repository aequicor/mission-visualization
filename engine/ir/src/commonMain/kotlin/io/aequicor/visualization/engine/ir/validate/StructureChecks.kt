package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNodeKind

/**
 * IR-STRUCT — document structure.
 *
 * - IR-STRUCT-001 (error): unsupported schema version.
 * - IR-STRUCT-002 (error): node without an id.
 * - IR-STRUCT-003 (error): duplicate node id within a tree scope (page forest /
 *   one component tree; instance internals are namespaced at resolve).
 * - IR-STRUCT-004 (warning): unknown node type (renders as a fallback).
 * - IR-STRUCT-005 (warning): duplicate explicit sibling `order` — paint order ambiguous.
 *
 * Tree acyclicity is guaranteed structurally: nodes are immutable values, a cycle
 * cannot be constructed, so no check is needed.
 */
internal object StructureChecks {

    /** Mirrors the parser's accepted versions: legacy "1.x" and "slm-ir/1.x". */
    private val SupportedSchemaVersion = Regex("""(\d+\.|slm-ir/1\.).*""")

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        if (!SupportedSchemaVersion.matches(ctx.document.schemaVersion)) {
            add(
                validationError(
                    "IR-STRUCT-001",
                    "Unsupported schema version '${ctx.document.schemaVersion}'; expected 1.x or slm-ir/1.x",
                ),
            )
        }

        val seenPerScope = mutableMapOf<String, MutableSet<String>>()
        ctx.entries.forEach { entry ->
            val node = entry.node
            if (node.id.isEmpty()) {
                add(
                    validationError(
                        "IR-STRUCT-002",
                        "Node of type '${node.type}' has no id",
                        ctx.location(node),
                    ),
                )
                return@forEach
            }
            val seen = seenPerScope.getOrPut(entry.scope) { mutableSetOf() }
            if (!seen.add(node.id)) {
                val where = if (entry.scope.isEmpty()) "the page tree" else "component '${entry.scope}'"
                add(
                    validationError(
                        "IR-STRUCT-003",
                        "Duplicate node id '${node.id}' in $where",
                        ctx.location(node),
                    ),
                )
            }
            if (node.kind is DesignNodeKind.Unknown) {
                add(
                    validationWarning(
                        "IR-STRUCT-004",
                        "Unknown node type '${node.kind.rawType}' on '${node.id}'; it renders as a fallback",
                        ctx.location(node),
                    ),
                )
            }

            val orders = node.children.mapNotNull { it.order }
            val duplicated = orders.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (duplicated.isNotEmpty()) {
                add(
                    validationWarning(
                        "IR-STRUCT-005",
                        "Children of '${node.id}' repeat explicit order ${duplicated.sorted()}; " +
                            "sibling order is ambiguous",
                        ctx.location(node),
                    ),
                )
            }
        }
    }
}
