package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * External typed-payload extension: a subsystem registers a payload kind (e.g.
 * `diagram`) together with a validator and a node-patch application. Authoring is
 * CNL-only — container-capable extensions additionally implement
 * [CnlContainerExtension], whose scoped sentence grammar produces the payload; the
 * frontend carries it through the patch pipeline as an [ExtensionPatch], which the
 * merger applies to the anchor node via [applyToNode].
 *
 * Extensions are collected in an [SlmExtensionRegistry] and passed to `compileSlm`
 * via `SlmCompileOptions.extensions`.
 *
 * Implementations must be pure and immutable: no side effects besides diagnostics
 * reported through the [BlockReading] context.
 */
interface TypedBlockExtension<P : Any> {
    /**
     * Payload kind key, e.g. `diagram`. Lowercase word (letters, digits, `_`, `-`;
     * starts with a letter); must not collide with the built-in
     * [TypedBlockKind.reservedKeys].
     */
    val kind: String

    /** Extra semantic validation of a payload; diagnostics only. */
    fun validate(payload: P, reading: BlockReading) {}

    /** Applies the payload onto the anchor IR node. Pure function. */
    fun applyToNode(node: DesignNode, payload: P): DesignNode

    /**
     * Extracts this extension's payload from an IR node when the node carries one —
     * the inverse of [applyToNode]; null when the node has no payload of this kind.
     * CNL write-back and structural inserts use it to re-emit the container body
     * sentences. Default: no payload.
     */
    fun payloadOf(node: DesignNode): P? = null
}

/**
 * A payload produced by a [CnlContainerExtension] grammar, carried through the patch
 * pipeline next to the built-in [TypedPatch] kinds. Application delegates to the
 * owning extension.
 */
data class ExtensionPatch(
    val extension: TypedBlockExtension<*>,
    val payload: Any,
) : TypedPatch {
    /** The extension kind key, used for provenance labels and source maps. */
    val kind: String get() = extension.kind

    /** Applies the payload onto [node] via the owning extension. */
    fun applyTo(node: DesignNode): DesignNode = applyErased(extension, node, payload)
}

// The payload always originates from the same extension's grammar, so the erased
// cast below is safe by construction.

@Suppress("UNCHECKED_CAST")
private fun <P : Any> applyErased(
    extension: TypedBlockExtension<P>,
    node: DesignNode,
    payload: Any,
): DesignNode = extension.applyToNode(node, payload as P)
