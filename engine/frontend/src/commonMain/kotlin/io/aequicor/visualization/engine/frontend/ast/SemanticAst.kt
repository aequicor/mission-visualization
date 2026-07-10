package io.aequicor.visualization.engine.frontend.ast

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.frontend.frontmatter.SlmFrontmatter
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.frontend.markdown.TypedEntry

/**
 * Semantic AST — the output of the extraction pass and the input of the IR
 * normalizer. Structure and intent only; ids, order and merged properties are
 * resolved by `normalize/IrNormalizer`.
 */
data class SemanticScreen(
    val frontmatter: SlmFrontmatter,
    val sourceLocale: SlmLocale,
    /** H1 heading; screen metadata + title resource, never a visible node. */
    val title: SemanticText? = null,
    val root: SemanticNode,
    /** Document-scoped CNL sections lowered to typed patches; never visible nodes. */
    val documentPatches: List<TypedEntry> = emptyList(),
    /** `Component:`-marked subtrees, lifted out of the visible tree. */
    val componentDefs: List<SemanticNode> = emptyList(),
    /** Mode values contributed by extraction rules (e.g. `density` -> `compact`). */
    val modes: Map<String, String> = emptyMap(),
)

enum class SemanticKind {
    Screen, Section, Group, Text, Action, Instance, Media, Table, Callout, EmptyState,
    Repeat, IrSplice,
}

data class SemanticNode(
    val kind: SemanticKind,
    val role: String = "",
    val name: String = "",
    /** Lexicon-provided id hint; wins over transliterated [name] in SlugGenerator. */
    val slugHint: String = "",
    val text: SemanticText? = null,
    val action: SemanticAction? = null,
    val repeat: SlmExpression.Repeat? = null,
    val condition: SlmExpression? = null,
    val componentRef: String? = null,
    val variant: Map<String, String> = emptyMap(),
    /** Data bindings for synthesized instances, prop name -> expression. */
    val propBindings: Map<String, SlmExpression> = emptyMap(),
    /** Typed attribute block entries bound to this node's anchor, document order. */
    val explicitPatches: List<TypedEntry> = emptyList(),
    /** Patches contributed by extraction rules; explicit patches take precedence. */
    val semanticPatches: List<TypedPatch> = emptyList(),
    val irSplice: IrSpliceBlock? = null,
    /** True when the node owns a markdown anchor element (edit-index addressable). */
    val isAnchor: Boolean = false,
    /** True when authored as a CNL element sentence; edits route through the CNL writer. */
    val isCnlElement: Boolean = false,
    /** True for `Component:`-marked subtrees; lifted by ComponentLifter. */
    val isComponentDef: Boolean = false,
    val children: List<SemanticNode> = emptyList(),
    val span: SlmSourceSpan,
)

/** Navigation intent extracted from a markdown link. */
data class SemanticAction(
    val navigateTo: String,
    val label: SemanticText? = null,
)

/** Key-generation hint carried by every extracted text; consumed by the i18n stage. */
sealed interface KeyHint {
    /** H1 -> `{screen}.title`. */
    data object ScreenTitle : KeyHint

    /** Section/group title -> `{screen}.sections.{slugPath}.title`. [path] holds slugs or raw names. */
    data class SectionTitle(val path: List<String>) : KeyHint

    /** Plain text in a section -> `{screen}.sections.{slugPath}.text{N}` (N only when >1). */
    data class SectionText(val path: List<String>) : KeyHint

    /** Action label -> `{screen}.actions.{slug}`. */
    data class ActionLabel(val slug: String) : KeyHint

    /** Empty-state first sentence -> `{screen}.empty.title`. */
    data object EmptyTitle : KeyHint

    /** Repeat card field -> `{screen}.{collection}.card.{field}` (both pre-slugged). */
    data class CardField(val collection: String, val field: String) : KeyHint

    /** Media alt -> `{screen}.{nodeId}.alt`. */
    data object MediaAlt : KeyHint

    /** Table header cell -> `{screen}.{table}.columns.{colSlug}`. */
    data class TableHeader(val table: String, val column: String) : KeyHint

    /** Component text prop default -> `components.{componentSlug}.{prop}`. */
    data class ComponentProp(
        val componentId: String,
        val componentSlug: String,
        val prop: String,
    ) : KeyHint

    /** No structural context -> `{screen}.text{N}`. */
    data object Plain : KeyHint
}

/**
 * Localizable text: [defaultText] with inline `{{expr}}` replaced by `{paramName}`
 * placeholders and the expressions kept in [params].
 */
data class SemanticText(
    val defaultText: String,
    val params: Map<String, SlmExpression> = emptyMap(),
    val explicitKey: String? = null,
    val keyHint: KeyHint = KeyHint.Plain,
    val span: SlmSourceSpan,
)

/** Raw ```ir fence content; parsed by `escape/IrEscapeHatch`. */
data class IrSpliceBlock(
    val json: String,
    val contentStartLine: Int,
    val span: SlmSourceSpan,
)
