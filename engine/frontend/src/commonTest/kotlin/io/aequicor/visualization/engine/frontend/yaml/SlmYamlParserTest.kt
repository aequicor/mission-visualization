package io.aequicor.visualization.engine.frontend.yaml

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlmYamlParserTest {
    private fun parse(text: String, collector: DiagnosticCollector = DiagnosticCollector("test.layout.md")): YamlValue? =
        parseSlmYaml(text, collector)

    private fun errorsOf(collector: DiagnosticCollector): List<String> =
        collector.diagnostics.filter { it.severity == DesignSeverity.Error }.map { it.message }

    private fun YamlValue?.asMap(): YamlMap = assertIs(this)

    private fun YamlValue?.asList(): YamlList = assertIs(this)

    private fun YamlValue?.asScalar(): YamlScalar = assertIs(this)

    // --- maps ---

    @Test
    fun parsesNestedMapsWithInsertionOrder() {
        val map = parse(
            """
            mode: row
            padding:
              block: 4
              inline: 6
            gap: 3
            """.trimIndent(),
        ).asMap()
        assertEquals(listOf("mode", "padding", "gap"), map.entries.keys.toList())
        val padding = map.entries.getValue("padding").asMap()
        assertEquals(4.0, padding.entries.getValue("block").asScalar().value)
        assertEquals(6.0, padding.entries.getValue("inline").asScalar().value)
    }

    @Test
    fun mapValuePositionsAreOneBased() {
        val map = parse("mode: row\nwrap: false").asMap()
        val mode = map.entries.getValue("mode").asScalar()
        assertEquals(1, mode.line)
        assertEquals(7, mode.column)
        assertEquals("row", mode.raw)
        assertEquals(1, mode.endLine)
        assertEquals(10, mode.endColumn)
        val wrap = map.entries.getValue("wrap").asScalar()
        assertEquals(2, wrap.line)
        assertEquals(false, wrap.value)
    }

    @Test
    fun startLineOffsetsAllPositions() {
        val map = parseSlmYaml("screen: x", DiagnosticCollector(), startLine = 5).asMap()
        assertEquals(5, map.line)
        assertEquals(5, map.entries.getValue("screen").asScalar().line)
    }

    @Test
    fun emptyValueBecomesNullScalar() {
        val map = parse("next:").asMap()
        val next = map.entries.getValue("next").asScalar()
        assertNull(next.value)
        assertEquals("", next.raw)
    }

    @Test
    fun mapExtentCoversSubtree() {
        val map = parse(
            """
            sizing:
              width: fill
              height: hug
            """.trimIndent(),
        ).asMap()
        assertEquals(1, map.line)
        assertEquals(3, map.endLine)
        val sizing = map.entries.getValue("sizing").asMap()
        assertEquals(2, sizing.line)
        assertEquals(3, sizing.column)
        assertEquals(3, sizing.endLine)
        assertEquals(14, sizing.endColumn) // after "hug" on line 3
    }

    // --- lists ---

    @Test
    fun parsesScalarList() {
        val map = parse(
            """
            targetLocales:
              - ru-RU
              - en-US
            """.trimIndent(),
        ).asMap()
        val list = map.entries.getValue("targetLocales").asList()
        assertEquals(listOf("ru-RU", "en-US"), list.items.map { it.asScalar().value })
    }

    @Test
    fun parsesListAtSameIndentAsKey() {
        val map = parse("next:\n- createMissionDialog").asMap()
        val list = map.entries.getValue("next").asList()
        assertEquals("createMissionDialog", list.items.single().asScalar().value)
    }

    @Test
    fun parsesMapListItemsWithSiblingEntries() {
        val map = parse(
            """
            breakpoints:
              - id: desktop
                minWidth: 1024
              - id: mobile
                maxWidth: 767
            """.trimIndent(),
        ).asMap()
        val list = map.entries.getValue("breakpoints").asList()
        assertEquals(2, list.items.size)
        val desktop = list.items[0].asMap()
        assertEquals("desktop", desktop.entries.getValue("id").asScalar().value)
        assertEquals(1024.0, desktop.entries.getValue("minWidth").asScalar().value)
        val mobile = list.items[1].asMap()
        assertEquals(767.0, mobile.entries.getValue("maxWidth").asScalar().value)
    }

    @Test
    fun parsesListItemWithNestedContinuation() {
        val map = parse(
            """
            variants:
              - when:
                  breakpoint: mobile
                layout:
                  mode: column
            """.trimIndent(),
        ).asMap()
        val item = map.entries.getValue("variants").asList().items.single().asMap()
        assertEquals(listOf("when", "layout"), item.entries.keys.toList())
        val where = item.entries.getValue("when").asMap()
        assertEquals("mobile", where.entries.getValue("breakpoint").asScalar().value)
        assertEquals("column", item.entries.getValue("layout").asMap().entries.getValue("mode").asScalar().value)
    }

    @Test
    fun parsesNestedListItemValue() {
        val map = parse(
            """
            fixedChildren:
              - missionPanelHeader
            """.trimIndent(),
        ).asMap()
        val list = map.entries.getValue("fixedChildren").asList()
        assertEquals("missionPanelHeader", list.items.single().asScalar().value)
    }

    // --- scalars ---

    @Test
    fun parsesScalarTypes() {
        val map = parse(
            """
            a: null
            b: ~
            c: true
            d: false
            e: 42
            f: -3.5
            g: hello world
            h: ${'$'}space.4
            i: "{{mission.name}}"
            j: '{{mission.status}}'
            k: {{missions.length}}
            """.trimIndent(),
        ).asMap()
        assertNull(map.entries.getValue("a").asScalar().value)
        assertNull(map.entries.getValue("b").asScalar().value)
        assertEquals(true, map.entries.getValue("c").asScalar().value)
        assertEquals(false, map.entries.getValue("d").asScalar().value)
        assertEquals(42.0, map.entries.getValue("e").asScalar().value)
        assertEquals(-3.5, map.entries.getValue("f").asScalar().value)
        assertEquals("hello world", map.entries.getValue("g").asScalar().value)
        assertEquals("\$space.4", map.entries.getValue("h").asScalar().value)
        assertEquals("{{mission.name}}", map.entries.getValue("i").asScalar().value)
        assertEquals("{{mission.status}}", map.entries.getValue("j").asScalar().value)
        assertEquals("{{missions.length}}", map.entries.getValue("k").asScalar().value)
    }

    @Test
    fun quotedHexColorSurvivesAndRawKeepsQuotes() {
        val map = parse("light: \"#ffffff\"").asMap()
        val light = map.entries.getValue("light").asScalar()
        assertEquals("#ffffff", light.value)
        assertEquals("\"#ffffff\"", light.raw)
        assertEquals(8, light.column)
        assertEquals(17, light.endColumn)
    }

    @Test
    fun unquotedHexColorIsKeptAsValueNotComment() {
        // Weak models write `color: #1E293B` without quotes; keep it rather than eating it as a comment.
        assertEquals("#ffffff", parse("light: #ffffff").asMap().entries.getValue("light").asScalar().value)
        assertEquals("#1E293B", parse("color: #1E293B").asMap().entries.getValue("color").asScalar().value)
        assertEquals("#abc", parse("c: #abc").asMap().entries.getValue("c").asScalar().value)
        // Bare hex inside an inline map is kept too.
        val style = parse("style: {fill: #EAF5FF}").asMap().entries.getValue("style").asMap()
        assertEquals("#EAF5FF", style.entries.getValue("fill").asScalar().value)
    }

    @Test
    fun nonHexHashIsStillAComment() {
        assertEquals("value", parse("note: value # comment").asMap().entries.getValue("note").asScalar().value)
        assertNull(parse("empty: # only a comment").asMap().entries.getValue("empty").asScalar().value)
    }

    @Test
    fun doubleQuotedEscapes() {
        val map = parse("text: \"line\\nnext \\\"quoted\\\" back\\\\slash\"").asMap()
        assertEquals("line\nnext \"quoted\" back\\slash", map.entries.getValue("text").asScalar().value)
    }

    @Test
    fun singleQuotedWithApostropheEscape() {
        val map = parse("text: 'it''s'").asMap()
        assertEquals("it's", map.entries.getValue("text").asScalar().value)
    }

    @Test
    fun suffixValueKeepsColonInsideScalar() {
        val map = parse("time: 12:30").asMap()
        assertEquals("12:30", map.entries.getValue("time").asScalar().value)
    }

    // --- comments ---

    @Test
    fun fullLineAndTrailingCommentsAreIgnored() {
        val collector = DiagnosticCollector()
        val map = parseSlmYaml(
            """
            # document comment
            mode: row # trailing note
              # indented comment
            gap: 3
            """.trimIndent(),
            collector,
        ).asMap()
        val mode = map.entries.getValue("mode").asScalar()
        assertEquals("row", mode.value)
        assertEquals("row", mode.raw)
        assertEquals(3.0, map.entries.getValue("gap").asScalar().value)
        assertTrue(errorsOf(collector).isEmpty())
    }

    @Test
    fun hashInsideQuotesIsNotComment() {
        val map = parse("text: \"a # b\"").asMap()
        assertEquals("a # b", map.entries.getValue("text").asScalar().value)
    }

    // --- inline collections ---

    @Test
    fun parsesInlineListAndMap() {
        val map = parse(
            """
            modes: [light, dark]
            viewBox: [0, 0, 24, 24]
            empty: []
            placement: {column: 1, columnSpan: 8}
            """.trimIndent(),
        ).asMap()
        assertEquals(
            listOf("light", "dark"),
            map.entries.getValue("modes").asList().items.map { it.asScalar().value },
        )
        assertEquals(
            listOf(0.0, 0.0, 24.0, 24.0),
            map.entries.getValue("viewBox").asList().items.map { it.asScalar().value },
        )
        assertTrue(map.entries.getValue("empty").asList().items.isEmpty())
        val placement = map.entries.getValue("placement").asMap()
        assertEquals(1.0, placement.entries.getValue("column").asScalar().value)
        assertEquals(8.0, placement.entries.getValue("columnSpan").asScalar().value)
    }

    @Test
    fun inlineCollectionPositions() {
        val map = parse("modes: [light, dark]").asMap()
        val list = map.entries.getValue("modes").asList()
        assertEquals(8, list.column)
        assertEquals(21, list.endColumn)
        assertEquals(9, list.items[0].asScalar().column)
        assertEquals(16, list.items[1].asScalar().column)
    }

    @Test
    fun nestedInlineCollections() {
        val map = parse("grid: [[1, 2], [3]]").asMap()
        val grid = map.entries.getValue("grid").asList()
        assertEquals(2, grid.items.size)
        assertEquals(
            listOf(1.0, 2.0),
            grid.items[0].asList().items.map { it.asScalar().value },
        )
    }

    // --- errors: entry skipped, parse continues ---

    @Test
    fun tabIndentationIsErrorAndLineSkipped() {
        val collector = DiagnosticCollector()
        val map = parseSlmYaml("a: 1\n\tb: 2\nc: 3", collector).asMap()
        assertTrue(errorsOf(collector).any { "Tab" in it })
        assertEquals(setOf("a", "c"), map.entries.keys)
    }

    @Test
    fun duplicateKeyIsErrorAndFirstValueWins() {
        val collector = DiagnosticCollector()
        val map = parseSlmYaml("a: 1\na: 2\nb: 3", collector).asMap()
        assertTrue(errorsOf(collector).any { "Duplicate key" in it })
        assertEquals(1.0, map.entries.getValue("a").asScalar().value)
        assertEquals(3.0, map.entries.getValue("b").asScalar().value)
    }

    @Test
    fun anchorsAliasesAndTagsAreErrors() {
        val collector = DiagnosticCollector()
        val map = parseSlmYaml(
            """
            a: &anchor 1
            b: *anchor
            c: !!str x
            d: ok
            """.trimIndent(),
            collector,
        ).asMap()
        assertEquals(3, errorsOf(collector).size)
        assertEquals(setOf("d"), map.entries.keys)
    }

    @Test
    fun multilineBlockScalarsAreErrorsAndSkipped() {
        val collector = DiagnosticCollector()
        val map = parseSlmYaml(
            """
            a: |
              line one
              line two
            b: ok
            """.trimIndent(),
            collector,
        ).asMap()
        assertTrue(errorsOf(collector).any { "block scalars" in it })
        assertEquals(setOf("b"), map.entries.keys)
    }

    @Test
    fun unclosedInlineCollectionIsErrorAndSkipped() {
        val collector = DiagnosticCollector()
        val map = parseSlmYaml("a: [1, 2\nb: ok", collector).asMap()
        assertTrue(errorsOf(collector).any { "Unclosed" in it })
        assertEquals(setOf("b"), map.entries.keys)
    }

    @Test
    fun emptyDocumentReturnsNull() {
        assertNull(parse(""))
        assertNull(parse("# only a comment\n"))
    }
}
