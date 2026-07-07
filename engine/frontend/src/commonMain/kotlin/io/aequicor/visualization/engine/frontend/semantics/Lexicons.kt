package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.blocks.LayoutPatch
import io.aequicor.visualization.engine.ir.model.AlignItems
import io.aequicor.visualization.engine.ir.model.LayoutMode

/**
 * Built-in semantic lexicons per the design rule table:
 * topbar, trailingEnd, primaryButton, secondaryButton, emptyState, badge,
 * cardRepeat, densityCompact, title and the search extension. Effects are
 * shared between locales so extraction output never depends on the language.
 */
object SemanticLexicons {
    val builtIn: List<SemanticLexicon> get() = listOf(RuLexicon, EnLexicon)

    /** Lexicon whose primary language subtag matches [locale], if any. */
    fun forLocale(locale: SlmLocale, lexicons: List<SemanticLexicon> = builtIn): SemanticLexicon? =
        lexicons.firstOrNull { it.locale.tag == locale.tag }
            ?: lexicons.firstOrNull { it.locale.language == locale.language }
}

private val topbarEffects = listOf(
    SemanticEffect.SetRole("topbar"),
    SemanticEffect.Patch(LayoutPatch(mode = LayoutMode.Horizontal, alignBlock = AlignItems.Center)),
)

private val primaryButtonEffects = listOf(
    SemanticEffect.SetRole("primaryAction"),
    SemanticEffect.SynthesizeInstance("ds/Button", mapOf("type" to "primary")),
)

private val secondaryButtonEffects = listOf(
    SemanticEffect.SynthesizeInstance("ds/Button", mapOf("type" to "secondary")),
)

private val badgeEffects = listOf(
    SemanticEffect.MarkBadge,
    SemanticEffect.SynthesizeInstance("ds/Badge"),
)

private val searchEffects = listOf(
    SemanticEffect.SynthesizeInstance("ds/Input"),
)

val RuLexicon: SemanticLexicon = SemanticLexicon(
    locale = SlmLocale("ru-RU"),
    conditionMarkers = listOf("если"),
    componentMarkers = listOf("component", "компонент"),
    rules = listOf(
        SemanticRule(
            id = "topbar",
            phrases = listOf("верхняя панель", "верхней панели", "верхнюю панель", "верхней панелью"),
            effects = topbarEffects,
        ),
        SemanticRule(
            id = "trailingEnd",
            phrases = listOf("справа"),
            effects = listOf(SemanticEffect.AlignTrailingEnd),
        ),
        SemanticRule(
            id = "primaryButton",
            phrases = listOf(
                "основная кнопка", "основной кнопки", "основную кнопку", "основной кнопкой",
                "основное действие", "основного действия", "основным действием",
            ),
            effects = primaryButtonEffects,
        ),
        SemanticRule(
            id = "secondaryButton",
            phrases = listOf("вторичная кнопка", "вторичной кнопки", "вторичную кнопку", "вторичной кнопкой"),
            effects = secondaryButtonEffects,
        ),
        SemanticRule(
            id = "emptyState",
            phrases = listOf("пустое состояние", "пустого состояния", "пустым состоянием"),
            effects = listOf(SemanticEffect.MarkEmptyState),
        ),
        SemanticRule(
            id = "badge",
            phrases = listOf("как badge", "как бейдж"),
            effects = badgeEffects,
        ),
        SemanticRule(
            id = "cardRepeat",
            phrases = listOf(
                "карточка для каждой", "карточка для каждого",
                "карточки для каждой", "карточки для каждого",
            ),
            effects = listOf(SemanticEffect.MarkCardRepeat),
        ),
        SemanticRule(
            id = "densityCompact",
            phrases = listOf("компактно"),
            effects = listOf(SemanticEffect.SetMode("density", "compact")),
        ),
        SemanticRule(
            id = "title",
            phrases = listOf("заголовок"),
            effects = listOf(SemanticEffect.SetRole("title")),
        ),
        SemanticRule(
            id = "search",
            phrases = listOf("поиск"),
            effects = searchEffects,
        ),
    ),
    nounSlugs = mapOf(
        "фильтры" to "filters",
        "статус" to "status",
        "название" to "name",
        "заголовок" to "title",
        "действие" to "action",
        "поиск" to "search",
    ),
    actionSlugs = mapOf(
        "создать миссию" to "createMission",
        "открыть" to "open",
    ),
)

val EnLexicon: SemanticLexicon = SemanticLexicon(
    locale = SlmLocale("en-US"),
    conditionMarkers = listOf("if"),
    componentMarkers = listOf("component"),
    rules = listOf(
        SemanticRule(
            id = "topbar",
            phrases = listOf("top bar", "topbar"),
            effects = topbarEffects,
        ),
        SemanticRule(
            id = "trailingEnd",
            phrases = listOf("on the right", "to the right"),
            effects = listOf(SemanticEffect.AlignTrailingEnd),
        ),
        SemanticRule(
            id = "primaryButton",
            phrases = listOf("primary button", "primary action"),
            effects = primaryButtonEffects,
        ),
        SemanticRule(
            id = "secondaryButton",
            phrases = listOf("secondary button"),
            effects = secondaryButtonEffects,
        ),
        SemanticRule(
            id = "emptyState",
            phrases = listOf("empty state"),
            effects = listOf(SemanticEffect.MarkEmptyState),
        ),
        SemanticRule(
            id = "badge",
            phrases = listOf("as badge", "as a badge"),
            effects = badgeEffects,
        ),
        SemanticRule(
            id = "cardRepeat",
            phrases = listOf("card for each"),
            effects = listOf(SemanticEffect.MarkCardRepeat),
        ),
        SemanticRule(
            id = "densityCompact",
            phrases = listOf("compact"),
            effects = listOf(SemanticEffect.SetMode("density", "compact")),
        ),
        SemanticRule(
            id = "title",
            phrases = listOf("title", "heading"),
            effects = listOf(SemanticEffect.SetRole("title")),
        ),
        SemanticRule(
            id = "search",
            phrases = listOf("search"),
            effects = searchEffects,
        ),
    ),
    nounSlugs = mapOf(
        "filters" to "filters",
        "status" to "status",
        "name" to "name",
        "title" to "title",
        "heading" to "title",
        "action" to "action",
        "search" to "search",
    ),
    actionSlugs = mapOf(
        "create mission" to "createMission",
        "open" to "open",
    ),
)
