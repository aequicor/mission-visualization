package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.VariableValue

enum class LayoutDirection { Ltr, Rtl }

/**
 * Environment the resolver runs against: locale/direction, active modes and responsive
 * dimensions, viewport, runtime data, external component libraries, overlay i18n
 * resources, and prototype state.
 *
 * Stage note: at this stage the resolver consumes [locale], [direction], [resources],
 * and [modeSelections]; the remaining fields are carried for the upcoming resolve
 * semantics (responsive matching, data binding, libraries, prototype runtime).
 */
data class ResolveContext(
    /** Target locale; null falls back to the document's `i18n.sourceLocale`. */
    val locale: String? = null,
    /** Explicit direction; null derives it from the locale (ar/he/fa/ur -> Rtl). */
    val direction: LayoutDirection? = null,
    /** Collection id -> mode; seeds the root scope before per-subtree `variableModes`. */
    val modeSelections: Map<String, String> = emptyMap(),
    /** Active responsive dimension values, e.g. Theme -> "dark". */
    val dimensions: Map<ResponsiveDimension, String> = emptyMap(),
    val viewport: DesignSize? = null,
    val breakpointId: String? = null,
    /** Runtime data scope for `{{...}}` bindings. */
    val data: Map<String, DataValue> = emptyMap(),
    /** Library id -> library document, for `libraryRef` component lookups. */
    val libraries: Map<String, DesignDocument> = emptyMap(),
    /** locale -> key -> ICU message; merged OVER the document's `i18n.resources`. */
    val resources: Map<String, Map<String, String>> = emptyMap(),
    /** Prototype variable name -> current value. */
    val prototypeState: Map<String, VariableValue> = emptyMap(),
)

private val RtlLanguages = setOf("ar", "he", "fa", "ur")

/** Derives the layout direction from a locale tag: ar/he/fa/ur are right-to-left. */
fun directionForLocale(locale: String): LayoutDirection {
    val language = locale.substringBefore('-').substringBefore('_').trim().lowercase()
    return if (language in RtlLanguages) LayoutDirection.Rtl else LayoutDirection.Ltr
}
