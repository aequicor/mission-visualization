package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.expr.ComparisonOp
import io.aequicor.visualization.engine.frontend.expr.SlmExpression
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlmMarkdownParserTest {
    private fun parse(
        source: String,
        collector: DiagnosticCollector = DiagnosticCollector("test.layout.md"),
    ): SlmMarkdownDocument = SlmMarkdownParser(collector).parse(source)

    private inline fun <reified T : SlmBlock> SlmMarkdownDocument.block(index: Int): T =
        assertIs<T>(blocks[index])

    private fun List<SlmInline>.plainText(): String =
        joinToString("") { if (it is TextRun) it.text else "" }

    // --- headings ---

    @Test
    fun parsesHeadingLevels1To6() {
        val collector = DiagnosticCollector()
        val doc = parse("# a\n## b\n### c\n#### d\n##### e\n###### f", collector)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), doc.blocks.map { (it as HeadingBlock).level })
        assertEquals("a", (doc.blocks[0] as HeadingBlock).inlines.plainText())
        val hints = collector.diagnostics.filter { "nested section" in it.message }
        assertEquals(3, hints.size)
    }

    @Test
    fun sevenHashesIsParagraph() {
        val doc = parse("####### too deep")
        assertIs<ParagraphBlock>(doc.blocks.single())
    }

    @Test
    fun headingSpanAndInlinePosition() {
        val doc = parse("## Mission Detail Panel")
        val heading = doc.block<HeadingBlock>(0)
        assertEquals(SlmSourceSpan(1, 1), heading.span)
        val run = assertIs<TextRun>(heading.inlines.single())
        assertEquals(1, run.line)
        assertEquals(4, run.column)
    }

    // --- paragraphs ---

    @Test
    fun multiLineParagraphKeepsPerLineRuns() {
        val doc = parse("first line\nsecond line")
        val paragraph = doc.block<ParagraphBlock>(0)
        assertEquals(SlmSourceSpan(1, 2), paragraph.span)
        assertEquals(listOf(1, 2), paragraph.inlines.map { it.line })
    }

    @Test
    fun blankLinesSeparateParagraphs() {
        val doc = parse("one\n\n\n\ntwo")
        assertEquals(2, doc.blocks.size)
        assertEquals(SlmSourceSpan(5, 5), doc.blocks[1].span)
    }

    @Test
    fun linkWithTrailingI18nCommentGetsOverride() {
        val doc = parse("[Создать миссию](/missions/new) <!-- i18n:key=mission.actions.create -->")
        val paragraph = doc.block<ParagraphBlock>(0)
        val link = assertIs<LinkRun>(paragraph.inlines.first())
        assertEquals("/missions/new", link.target)
        assertEquals("mission.actions.create", link.i18nKeyOverride)
        assertEquals("Создать миссию", link.label.plainText())
        assertTrue(paragraph.inlines.any { it is CommentRun })
    }

    @Test
    fun expressionRunsCarryParsedExpressionAndPosition() {
        val doc = parse("Если {{missions.length == 0}}:")
        val paragraph = doc.block<ParagraphBlock>(0)
        val expression = assertIs<ExpressionRun>(paragraph.inlines[1])
        assertEquals("missions.length == 0", expression.raw)
        assertEquals(
            SlmExpression.Comparison(
                SlmExpression.Path(listOf("missions", "length")),
                ComparisonOp.Eq,
                SlmExpression.Literal.Num(0.0),
            ),
            expression.expression,
        )
        assertEquals(6, expression.column)
        assertEquals(":", (paragraph.inlines[2] as TextRun).text)
    }

    @Test
    fun unparseableExpressionWarnsAndKeepsRaw() {
        val collector = DiagnosticCollector()
        val doc = parse("Всего {{count +}} штук", collector)
        val paragraph = doc.block<ParagraphBlock>(0)
        val expression = assertIs<ExpressionRun>(paragraph.inlines[1])
        assertEquals(SlmExpression.Raw("count +"), expression.expression)
        assertTrue(collector.diagnostics.any { "Unparseable expression" in it.message })
    }

    // --- lists ---

    @Test
    fun parsesFlatUnorderedList() {
        val doc = parse("Фильтры:\n- Поиск по {{query.search}}\n- Статус из {{query.status}}")
        assertEquals("Фильтры:", doc.block<ParagraphBlock>(0).inlines.plainText())
        val list = doc.block<ListBlock>(1)
        assertEquals(false, list.ordered)
        assertEquals(2, list.items.size)
        assertEquals(SlmSourceSpan(2, 3), list.span)
        val first = list.items[0]
        assertEquals("Поиск по ", first.inlines.plainText())
        val expr = assertIs<ExpressionRun>(first.inlines[1])
        assertEquals(SlmExpression.Path(listOf("query", "search")), expr.expression)
        assertTrue(first.children.isEmpty())
    }

    @Test
    fun parsesOrderedList() {
        val doc = parse("1. first\n2. second")
        val list = doc.block<ListBlock>(0)
        assertEquals(true, list.ordered)
        assertEquals(listOf("first", "second"), list.items.map { it.inlines.plainText() })
    }

    @Test
    fun parsesNestedListsAtTwoSpaceIndent() {
        val doc = parse(
            """
            - parent
              - child one
              - child two
            - sibling
            """.trimIndent(),
        )
        val list = doc.block<ListBlock>(0)
        assertEquals(2, list.items.size)
        val nested = assertIs<ListBlock>(list.items[0].children.single())
        assertEquals(listOf("child one", "child two"), nested.items.map { it.inlines.plainText() })
        assertEquals(SlmSourceSpan(1, 3), list.items[0].span)
        assertEquals(SlmSourceSpan(4, 4), list.items[1].span)
    }

    @Test
    fun listItemColumnsAccountForMarkers() {
        val doc = parse("- item text")
        val run = assertIs<TextRun>(doc.block<ListBlock>(0).items.single().inlines.single())
        assertEquals(3, run.column)
    }

    // --- blockquotes ---

    @Test
    fun parsesBlockquoteWithNesting() {
        val doc = parse("> outer text\n> > inner text")
        val quote = doc.block<BlockquoteBlock>(0)
        assertEquals(SlmSourceSpan(1, 2), quote.span)
        assertEquals(2, quote.blocks.size)
        assertEquals("outer text", (quote.blocks[0] as ParagraphBlock).inlines.plainText())
        val inner = assertIs<BlockquoteBlock>(quote.blocks[1])
        assertEquals("inner text", (inner.blocks.single() as ParagraphBlock).inlines.plainText())
    }

    // --- images ---

    @Test
    fun standaloneImageBecomesImageBlock() {
        val doc = parse("![Карта миссий](assets/mission-map.png)")
        val image = doc.block<ImageBlock>(0)
        assertEquals("Карта миссий", image.alt)
        assertEquals("assets/mission-map.png", image.path)
        assertNull(image.i18nKeyOverride)
    }

    @Test
    fun standaloneImageWithI18nComment() {
        val doc = parse("![Карта](map.png) <!-- i18n:key=screen.map.alt -->")
        assertEquals("screen.map.alt", doc.block<ImageBlock>(0).i18nKeyOverride)
    }

    @Test
    fun imageInsideTextStaysInline() {
        val doc = parse("Смотри ![icon](i.png) дальше")
        val paragraph = doc.block<ParagraphBlock>(0)
        val image = assertIs<InlineImageRun>(paragraph.inlines[1])
        assertEquals("icon", image.alt)
        assertEquals("i.png", image.path)
    }

    // --- tables ---

    @Test
    fun parsesGfmTableWithEscapedPipe() {
        val doc = parse(
            """
            | Название | Статус |
            | --- | --- |
            | Alpha | active |
            | Pipe \| cell | ok |
            """.trimIndent(),
        )
        val table = doc.block<TableBlock>(0)
        assertEquals(SlmSourceSpan(1, 4), table.span)
        assertEquals(listOf("Название", "Статус"), table.header.map { it.plainText() })
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Alpha", "active"), table.rows[0].map { it.plainText() })
        assertEquals(listOf("Pipe | cell", "ok"), table.rows[1].map { it.plainText() })
    }

    @Test
    fun tableCellsCanContainExpressions() {
        val doc = parse("| A |\n| --- |\n| {{mission.name}} |")
        val table = doc.block<TableBlock>(0)
        val cell = table.rows[0][0]
        assertIs<ExpressionRun>(cell.single())
    }

    // --- fenced code blocks ---

    @Test
    fun parsesIrFenceVerbatim() {
        val doc = parse(
            """
            ```ir
            {
              "type": "vector"
            }
            ```
            """.trimIndent(),
        )
        val fence = doc.block<FencedCodeBlock>(0)
        assertEquals("ir", fence.info)
        assertEquals("{\n  \"type\": \"vector\"\n}", fence.content)
        assertEquals(2, fence.contentStartLine)
        assertEquals(SlmSourceSpan(1, 5), fence.span)
    }

    @Test
    fun unknownFenceInfoWarnsButKeepsBlock() {
        val collector = DiagnosticCollector()
        val doc = parse("```js\nconsole.log(1)\n```", collector)
        assertEquals("js", doc.block<FencedCodeBlock>(0).info)
        assertTrue(collector.diagnostics.any { "Unsupported fenced code block" in it.message })
    }

    @Test
    fun unclosedFenceIsError() {
        val collector = DiagnosticCollector()
        parse("```ir\n{}", collector)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "Unclosed fenced code block" in it.message
            },
        )
    }

    // --- HTML comments ---

    @Test
    fun standaloneCommentBecomesBlock() {
        val doc = parse("<!-- заметка для следующего блока -->\n\nАбзац.")
        val comment = doc.block<HtmlCommentBlock>(0)
        assertEquals("заметка для следующего блока", comment.text)
        assertIs<ParagraphBlock>(doc.blocks[1])
    }

    @Test
    fun multiLineCommentBecomesSingleBlock() {
        val doc = parse("<!-- строка один\nстрока два -->")
        val comment = doc.block<HtmlCommentBlock>(0)
        assertEquals("строка один\nстрока два", comment.text)
        assertEquals(SlmSourceSpan(1, 2), comment.span)
    }

    @Test
    fun sameLineCommentStaysInline() {
        val doc = parse("Текст <!-- пометка --> дальше")
        val paragraph = doc.block<ParagraphBlock>(0)
        val comment = assertIs<CommentRun>(paragraph.inlines[1])
        assertEquals("пометка", comment.text)
    }

    // --- frontmatter ---

    @Test
    fun splitsFrontmatterWithoutParsingIt() {
        val doc = parse("---\nscreen: missionDashboard\npage: Operations\n---\n\n# Заголовок")
        val frontmatter = doc.frontmatter
        assertEquals("screen: missionDashboard\npage: Operations", frontmatter?.text)
        assertEquals(2, frontmatter?.startLine)
        assertEquals(SlmSourceSpan(1, 4), frontmatter?.span)
        val heading = doc.block<HeadingBlock>(0)
        assertEquals(SlmSourceSpan(6, 6), heading.span)
    }

    @Test
    fun unclosedFrontmatterIsErrorAndRestIsBody() {
        val collector = DiagnosticCollector()
        val doc = parse("---\nscreen: x\n\n# Заголовок", collector)
        assertNull(doc.frontmatter)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "Unclosed frontmatter" in it.message
            },
        )
        assertTrue(doc.blocks.any { it is HeadingBlock })
    }

    @Test
    fun documentWithoutFrontmatterHasNullFrontmatter() {
        val doc = parse("# Только тело")
        assertNull(doc.frontmatter)
    }

    // --- the spec body example (design-book/semantic-layout-markdown-i18n.md ~171-187) ---

    @Test
    fun parsesSpecBodyExample() {
        val collector = DiagnosticCollector("mission-dashboard.layout.md")
        val source = listOf(
            "# Панель миссий",
            "",
            "Верхняя панель: заголовок Mission Control, справа основная кнопка [Создать миссию](/missions/new).",
            "",
            "Фильтры:",
            "- Поиск по {{query.search}}",
            "- Статус из {{query.status}}",
            "",
            "Если {{missions.length == 0}}:",
            "> Пустое состояние: миссий пока нет. Основное действие [Создать миссию](/missions/new).",
            "",
            "Миссии:",
            "- Карточка для каждой {{mission in missions}}:",
            "  - Название: {{mission.name}}",
            "  - Статус: {{mission.status}} как badge",
            "  - Действие: [Открыть](/missions/{{mission.id}})",
        ).joinToString("\n")
        val doc = parse(source, collector)

        assertEquals(0, collector.diagnostics.size, collector.diagnostics.joinToString { it.message })
        assertEquals(8, doc.blocks.size)

        val title = doc.block<HeadingBlock>(0)
        assertEquals(1, title.level)
        assertEquals("Панель миссий", title.inlines.plainText())
        assertEquals(SlmSourceSpan(1, 1), title.span)

        val topbar = doc.block<ParagraphBlock>(1)
        assertEquals(SlmSourceSpan(3, 3), topbar.span)
        val topbarLink = assertIs<LinkRun>(topbar.inlines[1])
        assertEquals("/missions/new", topbarLink.target)
        assertEquals("Создать миссию", topbarLink.label.plainText())
        assertTrue(topbar.inlines.plainText().startsWith("Верхняя панель: заголовок Mission Control"))

        val filtersLead = doc.block<ParagraphBlock>(2)
        assertEquals("Фильтры:", filtersLead.inlines.plainText())
        assertEquals(SlmSourceSpan(5, 5), filtersLead.span)

        val filters = doc.block<ListBlock>(3)
        assertEquals(SlmSourceSpan(6, 7), filters.span)
        assertEquals(2, filters.items.size)
        assertEquals(
            SlmExpression.Path(listOf("query", "search")),
            assertIs<ExpressionRun>(filters.items[0].inlines[1]).expression,
        )
        assertEquals(
            SlmExpression.Path(listOf("query", "status")),
            assertIs<ExpressionRun>(filters.items[1].inlines[1]).expression,
        )

        val condition = doc.block<ParagraphBlock>(4)
        assertEquals(SlmSourceSpan(9, 9), condition.span)
        assertEquals("Если ", (condition.inlines[0] as TextRun).text)
        assertEquals(
            SlmExpression.Comparison(
                SlmExpression.Path(listOf("missions", "length")),
                ComparisonOp.Eq,
                SlmExpression.Literal.Num(0.0),
            ),
            assertIs<ExpressionRun>(condition.inlines[1]).expression,
        )
        assertEquals(":", (condition.inlines[2] as TextRun).text)

        val empty = doc.block<BlockquoteBlock>(5)
        assertEquals(SlmSourceSpan(10, 10), empty.span)
        val emptyParagraph = assertIs<ParagraphBlock>(empty.blocks.single())
        assertTrue(emptyParagraph.inlines.plainText().startsWith("Пустое состояние: миссий пока нет."))
        val emptyAction = assertIs<LinkRun>(emptyParagraph.inlines[1])
        assertEquals("/missions/new", emptyAction.target)

        val missionsLead = doc.block<ParagraphBlock>(6)
        assertEquals("Миссии:", missionsLead.inlines.plainText())

        val missions = doc.block<ListBlock>(7)
        assertEquals(SlmSourceSpan(13, 16), missions.span)
        val card = missions.items.single()
        assertEquals(SlmSourceSpan(13, 16), card.span)
        assertEquals(
            SlmExpression.Repeat("mission", SlmExpression.Path(listOf("missions"))),
            assertIs<ExpressionRun>(card.inlines[1]).expression,
        )

        val cardFields = assertIs<ListBlock>(card.children.single())
        assertEquals(SlmSourceSpan(14, 16), cardFields.span)
        assertEquals(3, cardFields.items.size)
        assertEquals(
            SlmExpression.Path(listOf("mission", "name")),
            assertIs<ExpressionRun>(cardFields.items[0].inlines[1]).expression,
        )
        val statusItem = cardFields.items[1]
        assertEquals(
            SlmExpression.Path(listOf("mission", "status")),
            assertIs<ExpressionRun>(statusItem.inlines[1]).expression,
        )
        assertEquals(" как badge", (statusItem.inlines[2] as TextRun).text)
        val openLink = assertIs<LinkRun>(cardFields.items[2].inlines[1])
        assertEquals("/missions/{{mission.id}}", openLink.target)
        assertEquals("Открыть", openLink.label.plainText())
    }
}
