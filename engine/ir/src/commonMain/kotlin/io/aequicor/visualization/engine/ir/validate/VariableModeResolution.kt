package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignVariables
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue

/**
 * Per-mode variable resolution for the validator, mirroring the resolver's alias-chain
 * walking (`DesignResolver.resolveVariable`) but without its default-mode fallback:
 * the validator reports the missing explicit mode value instead of silently falling
 * back, so every mode of every collection is proven covered.
 */
internal sealed interface ModeResolution {
    data class Resolved(val value: VariableValue, val type: VariableType) : ModeResolution

    data class MissingMode(val varId: String, val mode: String) : ModeResolution

    data class UnknownVariable(val varId: String) : ModeResolution

    data class Cycle(val varId: String) : ModeResolution
}

/**
 * Resolves [varId] for [mode], following alias chains cycle-safely. A cross-collection
 * alias resolves in the target collection's mode of the same name when it exists,
 * else in that collection's default mode.
 */
internal fun DesignVariables.resolveForMode(
    varId: String,
    mode: String,
    visited: Set<String> = emptySet(),
): ModeResolution {
    if (varId in visited) return ModeResolution.Cycle(varId)
    val found = findVariable(varId) ?: return ModeResolution.UnknownVariable(varId)
    val (collectionId, variable) = found
    val collection = collections.getValue(collectionId)
    val effectiveMode = mode.takeIf { it in collection.modes } ?: collection.defaultMode
    val value = variable.values[effectiveMode]
        ?: return ModeResolution.MissingMode(varId, effectiveMode)
    return when (value) {
        is VariableValue.Alias -> resolveForMode(value.varId, mode, visited + varId)
        else -> ModeResolution.Resolved(value, variable.type)
    }
}

internal fun VariableValue.scalarType(): VariableType =
    when (this) {
        is VariableValue.ColorValue -> VariableType.Color
        is VariableValue.NumberValue -> VariableType.Number
        is VariableValue.TextValue -> VariableType.Text
        is VariableValue.BoolValue -> VariableType.Bool
        is VariableValue.Alias -> VariableType.Text
    }
