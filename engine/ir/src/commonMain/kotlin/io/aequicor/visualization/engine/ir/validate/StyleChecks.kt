package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignStyle
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * IR-STYLE — shared styles, variables, paints.
 *
 * - IR-STYLE-001 (error): style id that does not exist.
 * - IR-STYLE-002 (error): style id of the wrong kind for its slot.
 * - IR-STYLE-003 (error): `$var` reference to an unknown variable.
 * - IR-STYLE-004 (warning): variable without a concrete value for a mode of its
 *   collection (per-mode alias chains; the resolver would fall back to the default
 *   mode, so this is a coverage warning, not an error).
 * - IR-STYLE-005 (error): variable alias cycle.
 * - IR-STYLE-006 (error): `$var` reference whose resolved type does not match the field.
 * - IR-STYLE-007 (error): invalid gradient stops (fewer than 2, positions outside
 *   0..1, or not ascending).
 * - IR-STYLE-008 (warning): unknown blend mode.
 * - IR-STYLE-009 (warning): literal opacity outside 0..1 or negative stroke weight.
 */
internal object StyleChecks {

    private val KnownBlendModes = setOf(
        "normal", "passThrough", "multiply", "screen", "overlay", "darken", "lighten",
        "colorDodge", "colorBurn", "hardLight", "softLight", "difference", "exclusion",
        "hue", "saturation", "color", "luminosity",
    )

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            checkStyleRefs(this, ctx, entry.node)
            checkVariableUses(this, ctx, entry.node)
            checkPaints(this, ctx, entry.node)
            checkBlendModes(this, ctx, entry.node)
            checkRanges(this, ctx, entry.node)
        }
        checkModeCoverage(this, ctx)
    }

    private fun checkStyleRefs(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        fun flag(styleId: String, slot: String, expected: (DesignStyle) -> Boolean) {
            if (styleId.isEmpty()) return
            val style = ctx.document.styles[styleId]
            when {
                style == null -> sink += validationError(
                    "IR-STYLE-001",
                    "Unknown style '$styleId' referenced by $slot of '${node.id}'",
                    ctx.location(node),
                )
                !expected(style) -> sink += validationError(
                    "IR-STYLE-002",
                    "Style '$styleId' referenced by $slot of '${node.id}' has the wrong kind",
                    ctx.location(node),
                )
            }
        }

        flag(node.fillStyleId, "fillStyleId") { it is DesignStyle.Paint }
        flag(node.strokeStyleId, "strokeStyleId") { it is DesignStyle.Paint }
        flag(node.effectStyleId, "effectStyleId") { it is DesignStyle.Effect }
        flag(node.gridStyleId, "gridStyleId") { it is DesignStyle.Grid }
        (node.kind as? DesignNodeKind.Text)?.textStyleId?.let { textStyleId ->
            flag(textStyleId, "textStyleId") { it is DesignStyle.Text }
        }
    }

    private fun checkVariableUses(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        collectBindingUses(node).forEach { use ->
            val varRef = use.bindable as? Bindable.VarRef ?: return@forEach
            val found = ctx.variableIndex[varRef.id]
            if (found == null) {
                sink += validationError(
                    "IR-STYLE-003",
                    "Unknown variable '${varRef.id}' referenced by ${use.field} of '${node.id}'",
                    ctx.location(node),
                )
                return@forEach
            }
            val (collectionId, _) = found
            val defaultMode = ctx.document.variables.collections.getValue(collectionId).defaultMode
            val resolution = ctx.document.variables.resolveForMode(varRef.id, defaultMode)
            if (resolution is ModeResolution.Resolved && resolution.type != use.expected) {
                sink += validationError(
                    "IR-STYLE-006",
                    "Variable '${varRef.id}' is ${resolution.type} but ${use.field} of '${node.id}' " +
                        "expects ${use.expected}",
                    ctx.location(node),
                )
            }
        }
    }

    /** Every variable of every collection must resolve for every mode of that collection. */
    private fun checkModeCoverage(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext) {
        ctx.document.variables.collections.forEach { (collectionId, collection) ->
            collection.vars.keys.forEach { varId ->
                val modes = collection.modes.ifEmpty { listOf(collection.defaultMode) }
                modes.forEach { mode ->
                    when (val resolution = ctx.document.variables.resolveForMode(varId, mode)) {
                        is ModeResolution.MissingMode -> sink += validationWarning(
                            "IR-STYLE-004",
                            "Variable '${resolution.varId}' (via '$varId' of collection '$collectionId') " +
                                "has no value for mode '${resolution.mode}'",
                        )
                        is ModeResolution.Cycle -> sink += validationError(
                            "IR-STYLE-005",
                            "Variable alias cycle at '${resolution.varId}' " +
                                "(via '$varId' of collection '$collectionId')",
                        )
                        is ModeResolution.UnknownVariable -> sink += validationError(
                            "IR-STYLE-003",
                            "Variable '$varId' aliases unknown variable '${resolution.varId}'",
                        )
                        is ModeResolution.Resolved -> Unit
                    }
                }
            }
        }
    }

    private fun checkPaints(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val paints = node.fills.orEmpty() + node.strokes?.paints.orEmpty()
        paints.forEach { paint ->
            if (paint is DesignPaint.Gradient) {
                val positions = paint.stops.map { it.position }
                val invalid = paint.stops.size < 2 ||
                    positions.any { it < 0.0 || it > 1.0 } ||
                    positions.zipWithNext().any { (a, b) -> b < a }
                if (invalid) {
                    sink += validationError(
                        "IR-STYLE-007",
                        "Gradient on '${node.id}' needs >= 2 stops with ascending positions in 0..1",
                        ctx.location(node),
                    )
                }
            }
        }
    }

    private fun checkBlendModes(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        fun flag(mode: String, slot: String) {
            if (mode.isNotEmpty() && mode !in KnownBlendModes) {
                sink += validationWarning(
                    "IR-STYLE-008",
                    "Unknown blend mode '$mode' on $slot of '${node.id}'",
                    ctx.location(node),
                )
            }
        }

        flag(node.blendMode, "node")
        node.fills.orEmpty().forEachIndexed { index, paint -> flag(paint.blendMode, "fills[$index]") }
        node.strokes?.paints.orEmpty().forEachIndexed { index, paint -> flag(paint.blendMode, "strokes[$index]") }
    }

    private fun checkRanges(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        fun flagOpacity(value: Double?, slot: String) {
            if (value != null && (value < 0.0 || value > 1.0)) {
                sink += validationWarning(
                    "IR-STYLE-009",
                    "Opacity $value on $slot of '${node.id}' is outside 0..1",
                    ctx.location(node),
                )
            }
        }

        flagOpacity(node.opacity.literalOrNull(), "node")
        node.fills.orEmpty().forEachIndexed { index, paint ->
            flagOpacity(paint.opacity.literalOrNull(), "fills[$index]")
        }
        val weight = node.strokes?.weight?.literalOrNull()
        if (weight != null && weight < 0.0) {
            sink += validationWarning(
                "IR-STYLE-009",
                "Negative stroke weight $weight on '${node.id}'",
                ctx.location(node),
            )
        }
    }
}
