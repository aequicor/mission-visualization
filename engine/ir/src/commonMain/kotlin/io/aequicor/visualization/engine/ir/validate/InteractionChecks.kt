package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.SourceLocation
import io.aequicor.visualization.engine.ir.model.VariableType

/**
 * IR-PROTO — prototyping interactions, actions, prototype variables, motion.
 *
 * - IR-PROTO-001 (warning): unknown action type (kept for forward compatibility,
 *   ignored by the prototype runtime).
 * - IR-PROTO-002 (error): navigation/overlay/scroll/variant action target that does
 *   not exist (node ids, page ids, or the screen id).
 * - IR-PROTO-003 (warning): Back/CloseOverlay in a document that never opens an overlay.
 * - IR-PROTO-004 (error): SetVariable on an undeclared variable.
 * - IR-PROTO-005 (error): SetVariable literal incompatible with the variable's type.
 * - IR-PROTO-006 (error): onVariableChange trigger on an undeclared variable.
 * - IR-PROTO-007 (error): RunActionSet with an unknown action set id.
 * - IR-PROTO-008 (error): motion ref not in `motionRefs` with no inline fallback.
 */
internal object InteractionChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        val targets = navigationTargets(ctx)
        ctx.entries.forEach { entry ->
            val node = entry.node
            node.interactions.forEach { interaction ->
                if (interaction.variable.isNotEmpty() && variableType(ctx, interaction.variable) == null) {
                    add(
                        validationError(
                            "IR-PROTO-006",
                            "onVariableChange on '${node.id}' watches undeclared variable " +
                                "'${interaction.variable}'",
                            ctx.location(node),
                        ),
                    )
                }
                interaction.actions.forEach { action ->
                    checkAction(this, ctx, action, targets, node.id, ctx.location(node))
                }
            }
            checkMotion(this, ctx, node)
        }
        ctx.document.actionSets.forEach { (setId, actions) ->
            actions.forEach { action ->
                checkAction(this, ctx, action, targets, "actionSet '$setId'", location = null)
            }
        }
    }

    private fun navigationTargets(ctx: ValidationContext): Set<String> = buildSet {
        addAll(ctx.pageNodeIds)
        ctx.document.pages.forEach { page -> add(page.id) }
        ctx.document.screen?.id?.takeIf { it.isNotEmpty() }?.let(::add)
    }

    private fun checkAction(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        action: DesignAction,
        targets: Set<String>,
        owner: String,
        location: SourceLocation?,
    ) {
        fun flagTarget(target: String, what: String) {
            if (target.isNotEmpty() && target !in targets) {
                sink += validationError(
                    "IR-PROTO-002",
                    "$what target '$target' of '$owner' does not exist",
                    location,
                )
            }
        }

        when (action) {
            is DesignAction.Navigate -> flagTarget(action.to, "Navigate")
            is DesignAction.OpenOverlay -> flagTarget(action.destination, "OpenOverlay")
            is DesignAction.SwapOverlay -> flagTarget(action.destination, "SwapOverlay")
            is DesignAction.ScrollTo -> flagTarget(action.target, "ScrollTo")
            is DesignAction.ChangeToVariant -> flagTarget(action.target, "ChangeToVariant")
            is DesignAction.SetVariable -> checkSetVariable(sink, ctx, action, owner, location)
            is DesignAction.RunActionSet -> {
                if (action.actionSetId !in ctx.document.actionSets) {
                    sink += validationError(
                        "IR-PROTO-007",
                        "RunActionSet of '$owner' references unknown action set '${action.actionSetId}'",
                        location,
                    )
                }
            }
            DesignAction.Back, is DesignAction.CloseOverlay -> {
                if (!ctx.hasOpenOverlay) {
                    sink += validationWarning(
                        "IR-PROTO-003",
                        "'$owner' uses ${if (action is DesignAction.CloseOverlay) "CloseOverlay" else "Back"} " +
                            "but the document never opens an overlay",
                        location,
                    )
                }
            }
            is DesignAction.OpenLink -> Unit
            is DesignAction.Unknown -> sink += validationWarning(
                "IR-PROTO-001",
                "Unknown action type '${action.rawType}' on '$owner' is ignored by the prototype runtime",
                location,
            )
        }
    }

    private fun variableType(ctx: ValidationContext, name: String): VariableType? =
        ctx.document.prototypeVariables[name]?.type
            ?: ctx.variableIndex[name]?.second?.type

    private fun checkSetVariable(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        action: DesignAction.SetVariable,
        owner: String,
        location: SourceLocation?,
    ) {
        val type = variableType(ctx, action.variable)
        if (type == null) {
            sink += validationError(
                "IR-PROTO-004",
                "SetVariable of '$owner' targets undeclared variable '${action.variable}'",
                location,
            )
            return
        }
        val literal = (action.value as? Bindable.Value)?.value ?: return
        val compatible = when (type) {
            VariableType.Number -> literal.toDoubleOrNull() != null
            VariableType.Bool -> literal == "true" || literal == "false"
            VariableType.Color -> DesignColor.fromHex(literal) != null
            VariableType.Text -> true
        }
        if (!compatible) {
            sink += validationError(
                "IR-PROTO-005",
                "SetVariable of '$owner' assigns '$literal' to '${action.variable}', " +
                    "which is declared as $type",
                location,
            )
        }
    }

    private fun checkMotion(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val motion = node.motion ?: return
        if (motion.ref.isNotEmpty() && motion.ref !in ctx.document.motionRefs && motion.fallback == null) {
            sink += validationError(
                "IR-PROTO-008",
                "Motion ref '${motion.ref}' on '${node.id}' is not in motionRefs and has no fallback",
                ctx.location(node),
            )
        }
    }
}
