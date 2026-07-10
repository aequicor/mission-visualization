package io.aequicor.visualization.engine.frontend.blocks

/**
 * Reserved top-level keys of typed attribute blocks. `overrides` is included by design
 * decision (the spec's reserved-key table omits it but uses it in examples). `ir` is
 * reserved as well but is only valid as a fenced code block, so it is not listed here;
 * an unfenced `ir:` entry is an error.
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

        /** All reserved keys, including fenced-only `ir`. */
        val reservedKeys: Set<String> = entries.map { it.key }.toSet() + "ir"
    }
}
