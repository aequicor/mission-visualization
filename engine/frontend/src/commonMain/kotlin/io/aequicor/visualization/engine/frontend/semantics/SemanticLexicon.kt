package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.blocks.TypedPatch

/**
 * Declarative per-locale semantic lexicon (design section D). Carries the
 * structural markers (condition lead words, component heading prefixes) plus the
 * semantic rule table and the noun/action slug dictionaries. All effects are
 * language-neutral; RU/EN parity is enforced by test.
 */
data class SemanticLexicon(
    val locale: SlmLocale,
    /** Condition paragraph lead words, `если {{...}}:` / `if {{...}}:`. */
    val conditionMarkers: List<String>,
    /** Heading prefixes marking a component definition subtree. */
    val componentMarkers: List<String>,
    val rules: List<SemanticRule>,
    /** Localized noun -> stable slug, e.g. `фильтры` -> `filters`. */
    val nounSlugs: Map<String, String>,
    /** Localized action label -> stable slug, e.g. `создать миссию` -> `createMission`. */
    val actionSlugs: Map<String, String>,
) {
    /** (rule, phrase) pairs sorted longest-phrase-first for greedy matching. */
    internal val phrasesByLength: List<Pair<SemanticRule, String>> by lazy {
        rules
            .flatMap { rule -> rule.phrases.map { phrase -> rule to normalizeProse(phrase) } }
            .sortedByDescending { (_, phrase) -> phrase.length }
    }
}

/** One lexicon row: inflections are listed explicitly in [phrases] (no stemming). */
data class SemanticRule(
    val id: String,
    val phrases: List<String>,
    val effects: List<SemanticEffect>,
)

/** Language-neutral outcome of a matched [SemanticRule]. */
sealed interface SemanticEffect {
    data class SetRole(val role: String) : SemanticEffect

    data class Patch(val patch: TypedPatch) : SemanticEffect

    data class SynthesizeInstance(
        val componentRef: String,
        val variant: Map<String, String> = emptyMap(),
    ) : SemanticEffect

    data object MarkEmptyState : SemanticEffect

    data object MarkBadge : SemanticEffect

    data object MarkCardRepeat : SemanticEffect

    data object AlignTrailingEnd : SemanticEffect

    data class SetMode(val dimension: String, val value: String) : SemanticEffect
}

/** One phrase hit inside a prose segment; [range] indexes the normalized text. */
internal data class RuleMatch(
    val rule: SemanticRule,
    val phrase: String,
    val range: IntRange,
)

/**
 * Prose normalization for lexicon matching: lowercase plus RU `ё`->`е` folding.
 * True NFC normalization is unavailable in common Kotlin; SLM sources are
 * expected to already be NFC (a documented deviation from the design note).
 */
internal fun normalizeProse(text: String): String = text.lowercase().replace('ё', 'е')

/**
 * All non-overlapping rule matches in [text]: word-boundary substring search,
 * longest-phrase-first; earlier (leftmost) hits win inside one phrase length.
 * Returned in text order.
 */
internal fun SemanticLexicon.matchesIn(text: String): List<RuleMatch> {
    val normalized = normalizeProse(text)
    val claimed = mutableListOf<IntRange>()
    val matches = mutableListOf<RuleMatch>()
    phrasesByLength.forEach { (rule, phrase) ->
        if (phrase.isEmpty()) return@forEach
        var from = 0
        while (true) {
            val index = normalized.indexOf(phrase, from)
            if (index < 0) break
            val range = index until index + phrase.length
            val boundaryBefore = index == 0 || !normalized[index - 1].isLetterOrDigit()
            val after = range.last + 1
            val boundaryAfter = after >= normalized.length || !normalized[after].isLetterOrDigit()
            if (boundaryBefore && boundaryAfter && claimed.none { it.overlaps(range) }) {
                claimed += range
                matches += RuleMatch(rule, phrase, range)
                from = after
            } else {
                from = index + 1
            }
        }
    }
    return matches.sortedBy { it.range.first }
}

/** Exact noun-slug lookup of the whole (trimmed, normalized) phrase. */
internal fun SemanticLexicon.nounSlug(text: String): String? =
    nounSlugs[normalizeProse(text.trim())]

/** Longest noun-slug key found at the start of [text] on a word boundary. */
internal fun SemanticLexicon.leadingNounSlug(text: String): String? {
    val normalized = normalizeProse(text.trim())
    return nounSlugs.entries
        .filter { (noun, _) ->
            normalized.startsWith(noun) &&
                (normalized.length == noun.length || !normalized[noun.length].isLetterOrDigit())
        }
        .maxByOrNull { (noun, _) -> noun.length }
        ?.value
}

/** Exact action-slug lookup of the whole (trimmed, normalized) label. */
internal fun SemanticLexicon.actionSlug(label: String): String? =
    actionSlugs[normalizeProse(label.trim())]

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last
