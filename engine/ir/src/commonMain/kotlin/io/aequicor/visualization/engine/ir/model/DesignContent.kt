package io.aequicor.visualization.engine.ir.model

/**
 * i18n text intent: a resource [key] resolved against locale bundles, with
 * [defaultText] as the authored fallback and ICU [params] bound per instance.
 */
data class TextContent(
    val key: String = "",
    val defaultLocale: String = "",
    val defaultText: String = "",
    val params: Map<String, Bindable<String>> = emptyMap(),
) {
    /**
     * Raw fallback chain (no ICU formatting yet, see resolver stages):
     * `resources[locale][key]` -> [defaultText].
     */
    fun fallbackText(resources: Map<String, Map<String, String>>, locale: String): String =
        resources[locale]?.get(key) ?: defaultText
}

/** Formatting intent applied to resolved text (date/number/currency/...). */
data class TextFormat(
    val kind: TextFormatKind,
    val options: Map<String, String> = emptyMap(),
)

enum class TextFormatKind { Date, Time, Number, Currency, RelativeTime, Percent }

data class DesignI18n(
    val sourceLocale: String = "",
    val targetLocales: List<String> = emptyList(),
    /** locale -> key -> ICU message. */
    val resources: Map<String, Map<String, String>> = emptyMap(),
)

data class TextListSettings(
    val type: TextListType = TextListType.None,
    val indent: Int = 0,
)

enum class TextListType { None, Bullet, Ordered }
