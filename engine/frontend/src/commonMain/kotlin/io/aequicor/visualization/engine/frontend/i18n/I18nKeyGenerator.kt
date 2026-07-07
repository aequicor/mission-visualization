package io.aequicor.visualization.engine.frontend.i18n

import io.aequicor.visualization.engine.frontend.ast.KeyHint
import io.aequicor.visualization.engine.frontend.normalize.latinizeToCamelCase
import io.aequicor.visualization.engine.frontend.semantics.SemanticLexicon
import io.aequicor.visualization.engine.frontend.semantics.nounSlug

/**
 * Base resource key per [KeyHint] (design section F):
 *
 * ```
 * ScreenTitle             {screen}.title
 * SectionTitle(path)      {screen}.sections.{slugPath}.title
 * SectionText(path)       {screen}.sections.{slugPath}.text{N}   (N only when >1)
 * ActionLabel(slug)       {screen}.actions.{slug}
 * EmptyTitle              {screen}.empty.title
 * CardField(coll, field)  {screen}.{coll}.card.{field}
 * MediaAlt                {screen}.{nodeId}.alt
 * TableHeader(tbl, col)   {screen}.{tbl}.columns.{colSlug}
 * ComponentProp           components.{componentSlug}.{prop}
 * Plain                   {screen}.text{N}
 * ```
 *
 * Path elements and column names are sluggified through the lexicon's noun
 * dictionary first, then latinized/camelCased. `text{N}` numbering is resolved
 * by the resource generator once base-key multiplicity is known.
 */
internal class I18nKeyGenerator(
    private val screenId: String,
    private val lexicon: SemanticLexicon,
) {
    fun baseKeyFor(entry: TextEntry): String = when (val hint = entry.keyHint) {
        is KeyHint.ScreenTitle -> "$screenId.title"
        is KeyHint.SectionTitle -> {
            val path = slugPath(hint.path)
            if (path.isEmpty()) "$screenId.title" else "$screenId.sections.$path.title"
        }
        is KeyHint.SectionText -> {
            val path = slugPath(hint.path)
            if (path.isEmpty()) "$screenId.text" else "$screenId.sections.$path.text"
        }
        is KeyHint.ActionLabel -> "$screenId.actions.${hint.slug}"
        is KeyHint.EmptyTitle -> "$screenId.empty.title"
        is KeyHint.CardField -> "$screenId.${hint.collection}.card.${hint.field}"
        is KeyHint.MediaAlt -> "$screenId.${entry.nodeId}.alt"
        is KeyHint.TableHeader -> "$screenId.${hint.table}.columns.${slug(hint.column)}"
        is KeyHint.ComponentProp -> "components.${hint.componentSlug}.${hint.prop}"
        is KeyHint.Plain -> "$screenId.text"
    }

    /** True when the base key participates in `text{N}` numbering. */
    fun isNumberedText(entry: TextEntry): Boolean =
        entry.keyHint is KeyHint.SectionText || entry.keyHint is KeyHint.Plain

    private fun slugPath(path: List<String>): String =
        path.map { slug(it) }.filter { it.isNotEmpty() }.joinToString(".")

    private fun slug(text: String): String =
        lexicon.nounSlug(text) ?: latinizeToCamelCase(text)
}
