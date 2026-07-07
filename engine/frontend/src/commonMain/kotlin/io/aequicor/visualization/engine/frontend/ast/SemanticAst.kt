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
    /** `Component:`-marked subtrees, lifted out of the visible tree. */
    val componentDefs: List<SemanticNode> = emptyList(),
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
    /** Typed attribute block entries bound to this node's anchor, document order. */
    val explicitPatches: List<TypedEntry> = emptyList(),
    /** Patches contributed by extraction rules; explicit patches take precedence. */
    val semanticPatches: List<TypedPatch> = emptyList(),
    val irSplice: IrSpliceBlock? = null,
    /** True when the node owns a markdown anchor element (edit-index addressable). */
    val isAnchor: Boolean = false,
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

/** Key-generation hint carried by every extracted text until stage 7.8 assigns keys. */
sealed interface KeyHint {
    data object ScreenTitle : KeyHint

    data class SectionTitle(val path: List<String>) : KeyHint

    data class ActionLabel(val slug: String) : KeyHint

    data object EmptyTitle : KeyHint

    data class CardField(val collection: String, val field: String) : KeyHint

    data object MediaAlt : KeyHint

    data object TableHeader : KeyHint

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

/** Raw ```ir fence content; parsed by the escape hatch in a later stage. */
data class IrSpliceBlock(
    val json: String,
    val contentStartLine: Int,
    val span: SlmSourceSpan,
)
