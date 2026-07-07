package io.aequicor.visualization.engine.frontend

import kotlin.jvm.JvmInline

/** BCP-47-ish locale tag used by SLM, e.g. `ru-RU`, `en-US`. */
@JvmInline
value class SlmLocale(val tag: String) {
    /** Primary language subtag, e.g. `ru` for `ru-RU`. */
    val language: String get() = tag.substringBefore('-')

    override fun toString(): String = tag
}
