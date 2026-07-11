package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.blocks.readers.BlockReading
import io.aequicor.visualization.engine.frontend.yaml.YamlValue
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * External typed-block extension: a subsystem registers a new block key (e.g.
 * `diagram`) together with a reader (block YAML -> typed payload), a validator,
 * a node-patch application and a writer for SLM write-back.
 *
 * Extensions are collected in an [SlmExtensionRegistry] and passed to `compileSlm`
 * via `SlmCompileOptions.extensions`. During compilation an entry whose key is not
 * a built-in [TypedBlockKind] is looked up in the registry; a hit is parsed with
 * [read], checked with [validate] and carried through the patch pipeline as an
 * [ExtensionPatch], which the merger applies to the anchor node via [applyToNode].
 * Unregistered keys keep the pre-registry behavior (the line stays prose).
 *
 * Implementations must be pure and immutable: no side effects besides diagnostics
 * reported through the [BlockReading] context.
 */
interface TypedBlockExtension<P : Any> {
    /**
     * Block key opening the typed entry, e.g. `diagram`. Lowercase word (letters,
     * digits, `_`, `-`; starts with a letter); must not collide with the built-in
     * reserved keys ([TypedBlockKind.reservedKeys], including fenced-only `ir`).
     */
    val kind: String

    /**
     * Parses the YAML value under `<kind>:` into the typed payload. Returns null
     * when the block is unreadable at the top level (report diagnostics through
     * [reading]); partial payloads with per-property diagnostics are preferred.
     */
    fun read(value: YamlValue, reading: BlockReading): P?

    /** Extra semantic validation of a successfully read payload; diagnostics only. */
    fun validate(payload: P, reading: BlockReading) {}

    /** Applies the payload onto the anchor IR node. Pure function. */
    fun applyToNode(node: DesignNode, payload: P): DesignNode

    /**
     * Renders the payload back to canonical SLM block text for write-back: the
     * first line is `<kind>:`, nested lines are indented with two spaces, no
     * trailing newline. Must round-trip through [read].
     */
    fun write(payload: P): String
}

/**
 * A payload read by a [TypedBlockExtension], carried through the patch pipeline
 * next to the built-in [TypedPatch] kinds. Application and write-back delegate
 * to the owning extension.
 */
data class ExtensionPatch(
    val extension: TypedBlockExtension<*>,
    val payload: Any,
) : TypedPatch {
    /** The extension block key, used for provenance labels and source maps. */
    val kind: String get() = extension.kind

    /** Applies the payload onto [node] via the owning extension. */
    fun applyTo(node: DesignNode): DesignNode = applyErased(extension, node, payload)

    /** Renders the payload back to SLM block text via the owning extension. */
    fun writeBlock(): String = writeErased(extension, payload)
}

// The payload always originates from the same extension's `read`, so the erased
// casts below are safe by construction.

@Suppress("UNCHECKED_CAST")
private fun <P : Any> applyErased(
    extension: TypedBlockExtension<P>,
    node: DesignNode,
    payload: Any,
): DesignNode = extension.applyToNode(node, payload as P)

@Suppress("UNCHECKED_CAST")
private fun <P : Any> writeErased(
    extension: TypedBlockExtension<P>,
    payload: Any,
): String = extension.write(payload as P)
