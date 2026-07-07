package io.aequicor.visualization.engine.frontend.semantics

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.frontmatter.SlmFrontmatter
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageDetectorTest {
    private fun detect(
        body: String,
        frontmatter: SlmFrontmatter = SlmFrontmatter(screen = "s"),
        threshold: Double = 0.3,
    ): Pair<SlmLocale, DiagnosticCollector> {
        val collector = DiagnosticCollector("test.layout.md")
        val document = SlmMarkdownParser(collector).parse(slm(body))
        val locale = detectSourceLocale(
            document = document,
            frontmatter = frontmatter,
            diagnostics = collector,
            cyrillicRatioThreshold = threshold,
        )
        return locale to collector
    }

    @Test
    fun frontmatterSourceLocaleWins() {
        val (locale, collector) = detect(
            "# Fully English title with english prose",
            frontmatter = SlmFrontmatter(screen = "s", sourceLocale = SlmLocale("ru-RU")),
        )
        assertEquals("ru-RU", locale.tag)
        assertTrue(collector.diagnostics.isEmpty())
    }

    @Test
    fun malformedFrontmatterLocaleStillWinsWithWarning() {
        val (locale, collector) = detect(
            "# Title",
            frontmatter = SlmFrontmatter(screen = "s", sourceLocale = SlmLocale("russian")),
        )
        assertEquals("russian", locale.tag)
        assertTrue(collector.diagnostics.any { "xx-XX" in it.message })
    }

    @Test
    fun pureRussianProseDetectsRu() {
        val (locale, _) = detect(
            """
            # Панель миссий

            Обычный абзац описания экрана.

            - Первый пункт
            - Второй пункт
            """,
        )
        assertEquals("ru-RU", locale.tag)
    }

    @Test
    fun pureEnglishProseDetectsFallback() {
        val (locale, _) = detect(
            """
            # Mission Dashboard

            A regular paragraph describing the screen.

            - First item
            - Second item
            """,
        )
        assertEquals("en-US", locale.tag)
    }

    @Test
    fun russianProseWithLatinProperNounsStaysRu() {
        val (locale, _) = detect(
            """
            # Панель миссий

            Верхняя панель: заголовок Mission Control, справа кнопка.

            > Пустое состояние: миссий пока нет.
            """,
        )
        assertEquals("ru-RU", locale.tag)
    }

    @Test
    fun expressionsAndLinkTargetsAreExcludedFromProse() {
        // Prose letters: only "Статус" (RU) and the link label "Открыть" (RU);
        // the expression body and route are Latin but must not count.
        val (locale, _) = detect(
            """
            # Статус

            Статус {{queryStatusValueFromBackend.longPath}} [Открыть](/very/long/latin/route)
            """,
        )
        assertEquals("ru-RU", locale.tag)
    }

    @Test
    fun thresholdEdgeCountsAsRussian() {
        // 3 Cyrillic of 10 letters = 0.3 -> at the threshold, detected as ru.
        val (atThreshold, _) = detect("# abcdefg ру\n\nв", threshold = 0.3)
        assertEquals("ru-RU", atThreshold.tag)
        val (belowThreshold, _) = detect("# abcdefgh ру\n\nв", threshold = 0.3)
        assertEquals("en-US", belowThreshold.tag)
    }

    @Test
    fun emptyProseFallsBack() {
        val (locale, _) = detect("layout:\n  mode: row")
        assertEquals("en-US", locale.tag)
    }
}
