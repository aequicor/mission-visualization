package io.aequicor.visualization.engine.frontend.blocks

/**
 * Typed patch kinds — the key vocabulary for CNL desugar output ([DirectPatchEntry]
 * keys and `blockSourceMaps` labels; resolved via [fromKey]). Not an authoring surface:
 * raw `<key>:` YAML blocks are no longer parsed. `overrides` is included by design
 * decision (the spec's reserved-key table omits it but uses it in examples).
 */
enum class TypedBlockKind(val key: String) {
    Node("node"),
    Layout("layout"),
    Style("style"),
    Text("text"),
    Component("component"),
    Props("props"),
    Overrides("overrides"),
    Media("media"),
    Shape("shape"),
    Vector("vector"),
    Mask("mask"),
    Action("action"),
    Interaction("interaction"),
    Motion("motion"),
    Responsive("responsive"),
    Variables("variables"),
    Styles("styles"),
    Handoff("handoff"),
    Export("export"),
    ;

    companion object {
        fun fromKey(key: String): TypedBlockKind? = entries.firstOrNull { it.key == key }

        /**
         * All built-in reserved keys. Used to validate that a registered
         * [io.aequicor.visualization.engine.frontend.blocks.TypedBlockExtension]
         * key does not collide with a built-in kind, and by the markdown parser's
         * ex-reserved-key deprecation warning.
         */
        val reservedKeys: Set<String> = entries.map { it.key }.toSet()
    }
}
