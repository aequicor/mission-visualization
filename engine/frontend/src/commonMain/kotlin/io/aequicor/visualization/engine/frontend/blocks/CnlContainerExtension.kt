package io.aequicor.visualization.engine.frontend.blocks

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Result of offering one container-body line to a [CnlContainerExtension].
 *
 * [Sentence] — the line is a recognized element sentence; the parser consumes it and
 * collects [Sentence.element]. [Invalid] — the line is a sentence of this grammar but
 * malformed; diagnostics were reported, the line is consumed, no element is collected.
 * [Prose] — not a sentence of this grammar; the line falls back to ordinary markdown.
 */
public sealed interface CnlContainerLine<out E : Any> {
    public data class Sentence<E : Any>(val element: E) : CnlContainerLine<E>
    public data object Invalid : CnlContainerLine<Nothing>
    public data object Prose : CnlContainerLine<Nothing>
}

/**
 * CNL container-scoped grammar seam: a [TypedBlockExtension] that additionally authors its
 * payload as **element sentences in the body of a CNL container heading** whose noun prefix
 * is [containerNoun] (e.g. `## Diagram: …` for the `diagram` extension).
 *
 * Inside such a container body the markdown parser switches to the extension's scoped
 * vocabulary: every non-blank line is first offered to [parseSentence]; recognized
 * sentences are collected in source order and folded by [aggregateSentences] into the
 * extension payload, which lands on the container's design node through the standard
 * [ExtensionPatch] → [TypedBlockExtension.applyToNode] pipeline — fully typed, no YAML.
 * Global element nouns are inactive inside the scope (a container of this kind has no
 * design-node children authored as CNL sentences).
 *
 * The element type [E] and payload [P] stay opaque to `:engine:frontend` — the grammar
 * itself lives next to the subsystem's model (dependency rule: the frontend knows only
 * this contract). Registration is the ordinary [SlmExtensionRegistry] registration of the
 * [TypedBlockExtension]; the registry surfaces container-capable extensions by noun.
 *
 * Implementations must be pure; diagnostics only through the passed collector, with the
 * extension [kind] as the block path so messages carry the payload-kind label.
 */
public interface CnlContainerExtension<E : Any, P : Any> : TypedBlockExtension<P> {
    /**
     * Lowercase heading noun that opens the container scope, e.g. `diagram` for
     * `## Diagram: …`. The noun must also exist in the global CNL vocabulary so the
     * heading itself parses (name/id/size/position onto the design node).
     */
    public val containerNoun: String get() = kind

    /**
     * Offers one body line (1-based [lineNumber]) to the scoped grammar. Must not throw;
     * malformed sentences report diagnostics and return [CnlContainerLine.Invalid].
     */
    public fun parseSentence(
        line: String,
        lineNumber: Int,
        diagnostics: DiagnosticCollector,
    ): CnlContainerLine<E>

    /**
     * Folds the collected sentences (source order) into the payload. Runs the extension's
     * cross-reference validation with per-sentence source lines. An empty list must yield
     * the empty payload (an empty container body is a valid empty document).
     */
    public fun aggregateSentences(
        elements: List<E>,
        span: SlmSourceSpan,
        diagnostics: DiagnosticCollector,
    ): P

    /**
     * Deterministic inverse of the body grammar: the canonical element sentences for
     * [payload], one line each, in canonical order. `parse(emit(payload)) == payload`.
     */
    public fun emitBody(payload: P): List<String>
}

// The erased element values below always originate from the same extension's
// `parseSentence`, so the unchecked casts are safe by construction (same keystone
// as ExtensionPatch's applyErased).

/** Offers [line] to the extension, erasing the element type for the markdown parser. */
internal fun CnlContainerExtension<*, *>.parseSentenceErased(
    line: String,
    lineNumber: Int,
    diagnostics: DiagnosticCollector,
): CnlContainerLine<Any> = parseSentence(line, lineNumber, diagnostics)

/** Aggregates erased elements into the extension payload wrapped as an [ExtensionPatch]. */
internal fun CnlContainerExtension<*, *>.aggregateErased(
    elements: List<Any>,
    span: SlmSourceSpan,
    diagnostics: DiagnosticCollector,
): ExtensionPatch = aggregateChecked(this, elements, span, diagnostics)

@Suppress("UNCHECKED_CAST")
private fun <E : Any, P : Any> aggregateChecked(
    extension: CnlContainerExtension<E, P>,
    elements: List<Any>,
    span: SlmSourceSpan,
    diagnostics: DiagnosticCollector,
): ExtensionPatch = ExtensionPatch(
    extension = extension,
    payload = extension.aggregateSentences(elements.map { it as E }, span, diagnostics),
)

/** The container body sentences for the payload [node] carries, or null when it has none. */
internal fun CnlContainerExtension<*, *>.bodyLinesOrNull(node: DesignNode): List<String>? =
    bodyLinesChecked(this, node)

private fun <E : Any, P : Any> bodyLinesChecked(
    extension: CnlContainerExtension<E, P>,
    node: DesignNode,
): List<String>? = extension.payloadOf(node)?.let { extension.emitBody(it) }
