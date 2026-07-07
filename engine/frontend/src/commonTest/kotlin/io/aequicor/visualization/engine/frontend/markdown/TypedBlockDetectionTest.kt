package io.aequicor.visualization.engine.frontend.markdown

import io.aequicor.visualization.engine.frontend.blocks.TypedBlockKind
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.yaml.YamlMap
import io.aequicor.visualization.engine.frontend.yaml.YamlScalar
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TypedBlockDetectionTest {
    private fun parse(
        source: String,
        collector: DiagnosticCollector = DiagnosticCollector("test.layout.md"),
    ): SlmMarkdownDocument = SlmMarkdownParser(collector).parse(source)

    @Test
    fun reservedKeyAtColumnZeroAfterHeadingStartsTypedBlock() {
        val doc = parse("## CTA Card\nnode: frame\n")
        assertIs<HeadingBlock>(doc.blocks[0])
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        val entry = typed.entries.single()
        assertEquals(TypedBlockKind.Node, entry.kind)
        assertEquals("frame", assertIs<YamlScalar>(entry.value).value)
        assertEquals(SlmSourceSpan(2, 2), entry.span)
    }

    @Test
    fun consecutiveReservedEntriesMergeIntoOneGroup() {
        val doc = parse(
            """
            ## CTA Card
            node: frame
            layout:
              mode: row
              gap: ${'$'}space.3
            style:
              radius: ${'$'}radius.md
            """.trimIndent(),
        )
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(
            listOf(TypedBlockKind.Node, TypedBlockKind.Layout, TypedBlockKind.Style),
            typed.entries.map { it.kind },
        )
        assertEquals(SlmSourceSpan(2, 7), typed.span)
        assertEquals(SlmSourceSpan(3, 5), typed.entries[1].span)
        val layout = assertIs<YamlMap>(typed.entries[1].value)
        assertEquals("row", assertIs<YamlScalar>(layout.entries.getValue("mode")).value)
        assertEquals("\$space.3", assertIs<YamlScalar>(layout.entries.getValue("gap")).value)
    }

    @Test
    fun overridesIsAccepted() {
        val doc = parse(
            """
            ## Card
            props:
              title: "{{mission.name}}"
            overrides:
              slots:
                actions: []
            """.trimIndent(),
        )
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(
            listOf(TypedBlockKind.Props, TypedBlockKind.Overrides),
            typed.entries.map { it.kind },
        )
    }

    @Test
    fun blankLineClosesGroupAndNextGroupIsSeparateBlock() {
        val doc = parse(
            """
            ## Card
            node: frame

            layout:
              mode: row
            """.trimIndent(),
        )
        val first = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(listOf(TypedBlockKind.Node), first.entries.map { it.kind })
        val second = assertIs<TypedAttributeBlock>(doc.blocks[2])
        assertEquals(listOf(TypedBlockKind.Layout), second.entries.map { it.kind })
    }

    @Test
    fun typedBlockWithoutAnchorBindsToScreenRoot() {
        val doc = parse(
            """
            variables:
              collections: []
            """.trimIndent(),
        )
        val typed = assertIs<TypedAttributeBlock>(doc.blocks.single())
        assertEquals(TypedBlockKind.Variables, typed.entries.single().kind)
    }

    @Test
    fun typedBlockInsideListItemBindsAtContentColumn() {
        val doc = parse(
            """
            - Поиск по {{query.search}}
              layout:
                sizing:
                  width: fill
            """.trimIndent(),
        )
        val list = assertIs<ListBlock>(doc.blocks.single())
        val item = list.items.single()
        val typed = assertIs<TypedAttributeBlock>(item.children.single())
        val entry = typed.entries.single()
        assertEquals(TypedBlockKind.Layout, entry.kind)
        assertEquals(SlmSourceSpan(2, 4), entry.span)
        val layout = assertIs<YamlMap>(entry.value)
        // columns are absolute in the file, not window-relative
        assertEquals(3, layout.line)
        assertEquals(5, layout.column)
        val sizing = assertIs<YamlMap>(layout.entries.getValue("sizing"))
        assertEquals("fill", assertIs<YamlScalar>(sizing.entries.getValue("width")).value)
    }

    @Test
    fun proseWithColonStaysParagraph() {
        val collector = DiagnosticCollector()
        val doc = parse("Заметка: это просто текст.", collector)
        assertIs<ParagraphBlock>(doc.blocks.single())
        assertTrue(collector.diagnostics.isEmpty())
    }

    @Test
    fun reservedKeyMidParagraphStaysProse() {
        val doc = parse("Первая строка абзаца\ntext: hi")
        val paragraph = assertIs<ParagraphBlock>(doc.blocks.single())
        assertEquals(SlmSourceSpan(1, 2), paragraph.span)
        assertTrue(paragraph.inlines.filterIsInstance<TextRun>().any { it.text == "text: hi" })
    }

    @Test
    fun misspelledKeyGetsHintAndStaysProse() {
        val collector = DiagnosticCollector()
        val doc = parse("stlye:\n  radius: 4", collector)
        assertIs<ParagraphBlock>(doc.blocks.single())
        assertTrue(
            collector.diagnostics.any { "Did you mean typed block `style:`" in it.message },
            collector.diagnostics.joinToString { it.message },
        )
    }

    @Test
    fun wrongCaseKeyGetsHintAndStaysProse() {
        val collector = DiagnosticCollector()
        val doc = parse("Layout: row", collector)
        assertIs<ParagraphBlock>(doc.blocks.single())
        assertTrue(collector.diagnostics.any { "Did you mean typed block `layout:`" in it.message })
    }

    @Test
    fun unfencedIrIsErrorAndConsumed() {
        val collector = DiagnosticCollector()
        val doc = parse(
            """
            ## Card
            node: frame
            ir:
              type: vector
            layout:
              mode: row
            """.trimIndent(),
            collector,
        )
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "fenced" in it.message
            },
        )
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(
            listOf(TypedBlockKind.Node, TypedBlockKind.Layout),
            typed.entries.map { it.kind },
        )
    }

    @Test
    fun brokenEntryIsSkippedButSiblingsSurvive() {
        val collector = DiagnosticCollector()
        val doc = parse(
            """
            ## Card
            node: &anchor frame
            layout:
              mode: row
            """.trimIndent(),
            collector,
        )
        assertTrue(collector.diagnostics.any { it.severity == DesignSeverity.Error })
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(listOf(TypedBlockKind.Layout), typed.entries.map { it.kind })
    }

    @Test
    fun entrySpanIncludesContinuationAndTrailingComment() {
        val doc = parse(
            """
            ## Card
            layout:
              mode: row # keep row
              # full-line trailing note
            """.trimIndent(),
        )
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(SlmSourceSpan(2, 4), typed.entries.single().span)
        val layout = assertIs<YamlMap>(typed.entries.single().value)
        val mode = assertIs<YamlScalar>(layout.entries.getValue("mode"))
        assertEquals("row", mode.raw)
    }

    @Test
    fun fencedYamlWithReservedFirstKeyBecomesTypedBlock() {
        val doc = parse(
            """
            ## Card
            ```yaml
            layout:
              mode: row

            style:
              radius: 4
            ```
            """.trimIndent(),
        )
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(
            listOf(TypedBlockKind.Layout, TypedBlockKind.Style),
            typed.entries.map { it.kind },
        )
        assertEquals(SlmSourceSpan(2, 8), typed.span)
    }

    @Test
    fun emptyReservedValueBecomesNullScalar() {
        val doc = parse("## Card\nexport:")
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        val entry = typed.entries.single()
        assertEquals(TypedBlockKind.Export, entry.kind)
        assertEquals(null, assertIs<YamlScalar>(entry.value).value)
    }

    @Test
    fun typedBlockAfterBlockquoteAnchorIsParsed() {
        val doc = parse("> Пустое состояние\nstyle:\n  opacity: 0.5")
        assertIs<BlockquoteBlock>(doc.blocks[0])
        val typed = assertIs<TypedAttributeBlock>(doc.blocks[1])
        assertEquals(TypedBlockKind.Style, typed.entries.single().kind)
    }

    @Test
    fun reservedKeyWithoutSpaceAfterColonIsProse() {
        val doc = parse("text:hi")
        assertIs<ParagraphBlock>(doc.blocks.single())
    }
}
