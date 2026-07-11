package io.aequicor.visualization.engine.frontend.blocks

/**
 * Immutable set of [TypedBlockExtension]s keyed by block kind, passed to the
 * compiler via `SlmCompileOptions.extensions`. The default everywhere is
 * [Empty], which reproduces the pre-registry behavior exactly.
 *
 * Registration is validated eagerly: a key that is not a well-formed block word,
 * collides with a built-in reserved key ([TypedBlockKind.reservedKeys], including
 * fenced-only `ir`) or duplicates another registered extension fails with
 * [IllegalArgumentException].
 */
class SlmExtensionRegistry private constructor(
    private val extensionsByKind: Map<String, TypedBlockExtension<*>>,
) {
    /** Registered extension block keys. */
    val kinds: Set<String> get() = extensionsByKind.keys

    val isEmpty: Boolean get() = extensionsByKind.isEmpty()

    /** The extension registered for [kind], or null. */
    fun find(kind: String): TypedBlockExtension<*>? = extensionsByKind[kind]

    companion object {
        /** No extensions; the compiler behaves exactly as before the registry. */
        val Empty: SlmExtensionRegistry = SlmExtensionRegistry(emptyMap())

        fun of(extensions: List<TypedBlockExtension<*>>): SlmExtensionRegistry {
            if (extensions.isEmpty()) return Empty
            val byKind = LinkedHashMap<String, TypedBlockExtension<*>>()
            extensions.forEach { extension ->
                val kind = extension.kind
                require(isBlockWord(kind)) {
                    "Extension block key \"$kind\" must be a word: a letter followed by " +
                        "letters, digits, `_` or `-`"
                }
                require(kind !in TypedBlockKind.reservedKeys) {
                    "Extension block key \"$kind\" collides with a built-in reserved key"
                }
                val previous = byKind.put(kind, extension)
                require(previous == null) {
                    "Duplicate extension block key \"$kind\": " +
                        "${previous!!::class.simpleName} vs ${extension::class.simpleName}"
                }
            }
            return SlmExtensionRegistry(byKind)
        }

        fun of(vararg extensions: TypedBlockExtension<*>): SlmExtensionRegistry =
            of(extensions.toList())

        /** Mirrors the markdown parser's `word:` detection rule for block keys. */
        private fun isBlockWord(key: String): Boolean =
            key.isNotEmpty() && key.first().isLetter() &&
                key.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
}
