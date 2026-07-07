package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.resolve.EvalScope
import io.aequicor.visualization.engine.ir.resolve.ExpressionEvaluator

/**
 * IR-DATA — `{{...}}` expressions: bindings, conditions, repeats.
 *
 * - IR-DATA-001 (error): expression that does not match the grammar
 *   (parse-only, via [ExpressionEvaluator.syntaxErrorOrNull]).
 * - IR-DATA-002 (warning): repeat itemName/indexName shadowing an outer repeat binding.
 * - IR-DATA-003 (warning): expression path that does not resolve against the
 *   context data (only when [io.aequicor.visualization.engine.ir.resolve.ResolveContext.data]
 *   is provided; expressions rooted at repeat bindings are skipped — their values
 *   only exist per item).
 * - IR-DATA-004 (error): repeat collection that evaluates to a non-list.
 */
internal object DataChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        val uses = ctx.document.pages.flatMap { page ->
            page.children.flatMap { collectExpressionUses(it) }
        } + ctx.document.components.values.flatMap { collectExpressionUses(it.root) }

        uses.forEach { use ->
            ExpressionEvaluator.syntaxErrorOrNull(use.expression)?.let { failure ->
                add(
                    validationError(
                        "IR-DATA-001",
                        "Expression '{{${use.expression.raw}}}' (${use.field} of '${use.node.id}') " +
                            "does not parse: $failure",
                        ctx.location(use.node),
                    ),
                )
            }
        }

        checkRepeatShadowing(this, ctx)

        if (ctx.resolveContext.data.isNotEmpty()) {
            val scope = EvalScope(bindings = ctx.resolveContext.data)
            uses.forEach { use ->
                val root = rootNameOf(use.expression)
                if (root.isEmpty() || root in use.repeatBindings) return@forEach
                var failure: String? = null
                val value = ExpressionEvaluator.evaluate(use.expression, scope) { failure = it }
                when {
                    failure != null -> add(
                        validationWarning(
                            "IR-DATA-003",
                            "Expression '{{${use.expression.raw}}}' (${use.field} of '${use.node.id}') " +
                                "does not resolve against the provided data: $failure",
                            ctx.location(use.node),
                        ),
                    )
                    use.expectsList && value != null && value !is DataValue.ListValue -> add(
                        validationError(
                            "IR-DATA-004",
                            "Repeat collection '{{${use.expression.raw}}}' of '${use.node.id}' " +
                                "is not a list",
                            ctx.location(use.node),
                        ),
                    )
                    else -> Unit
                }
            }
        }
    }

    private fun checkRepeatShadowing(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext) {
        fun walk(node: DesignNode, inherited: Set<String>) {
            val bindings = node.repeat?.let { repeat ->
                setOfNotNull(repeat.itemName, repeat.indexName).onEach { name ->
                    if (name in inherited) {
                        sink += validationWarning(
                            "IR-DATA-002",
                            "Repeat on '${node.id}' shadows outer binding '$name'",
                            ctx.location(node),
                        )
                    }
                } + inherited
            } ?: inherited
            node.children.forEach { walk(it, bindings) }
        }
        ctx.document.pages.forEach { page -> page.children.forEach { walk(it, emptySet()) } }
        ctx.document.components.values.forEach { walk(it.root, emptySet()) }
    }
}
