package io.aequicor.visualization.engine.frontend.frontmatter

import io.aequicor.visualization.engine.frontend.SlmLocale
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.markdown.RawYamlBlock
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.markdown.SlmSourceSpan
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrontmatterReaderTest {
    private fun raw(text: String, startLine: Int = 2): RawYamlBlock =
        RawYamlBlock(text, startLine, SlmSourceSpan(1, startLine + text.lines().size))

    /** The spec's full frontmatter example (semantic-layout-markdown-i18n.md lines ~137-169). */
    private val specFrontmatter = """
        screen: missionDashboard
        page: Operations
        sourceLocale: ru-RU
        targetLocales:
          - ru-RU
          - en-US
        density: compact
        platform: web
        theme: light
        frame:
          preset: desktop-1440
          width: 1440
          height: 1024
        canvas:
          section: Mission Flow
          position:
            x: 1200
            y: 400
        flow:
          id: missionOperations
          node: dashboard
          next:
            - createMissionDialog
        breakpoints:
          - id: desktop
            minWidth: 1024
          - id: mobile
            maxWidth: 767
        libraries:
          - id: ds
            source: "@company/design-system"
    """.trimIndent()

    @Test
    fun readsSpecFrontmatterExample() {
        val collector = DiagnosticCollector("mission-dashboard.layout.md")
        val frontmatter = readFrontmatter(raw(specFrontmatter), collector)

        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals("missionDashboard", frontmatter.screen)
        assertEquals("Operations", frontmatter.page)
        assertEquals(SlmLocale("ru-RU"), frontmatter.sourceLocale)
        assertEquals(listOf(SlmLocale("ru-RU"), SlmLocale("en-US")), frontmatter.targetLocales)
        assertEquals(
            mapOf("density" to "compact", "platform" to "web", "theme" to "light"),
            frontmatter.modes,
        )

        val frame = assertNotNull(frontmatter.frame)
        assertEquals("desktop-1440", frame.preset)
        assertEquals(1440.0, frame.width)
        assertEquals(1024.0, frame.height)

        val canvas = assertNotNull(frontmatter.canvas)
        assertEquals("Mission Flow", canvas.section)
        assertEquals(1200.0, canvas.x)
        assertEquals(400.0, canvas.y)

        val flow = assertNotNull(frontmatter.flow)
        assertEquals("missionOperations", flow.id)
        assertEquals("dashboard", flow.node)
        assertEquals(listOf("createMissionDialog"), flow.next)

        assertEquals(
            listOf(
                SlmBreakpoint("desktop", minWidth = 1024.0),
                SlmBreakpoint("mobile", maxWidth = 767.0),
            ),
            frontmatter.breakpoints,
        )
        assertEquals(listOf(SlmLibrary("ds", "@company/design-system")), frontmatter.libraries)
    }

    @Test
    fun roundTripsThroughMarkdownParser() {
        val collector = DiagnosticCollector("mission-dashboard.layout.md")
        val source = "---\n$specFrontmatter\n---\n\n# Панель миссий\n"
        val document = SlmMarkdownParser(collector).parse(source)
        val frontmatter = readFrontmatter(document.frontmatter, collector)
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals("missionDashboard", frontmatter.screen)
        assertEquals(2, frontmatter.breakpoints.size)
    }

    @Test
    fun unknownKeyProducesWarning() {
        val collector = DiagnosticCollector()
        val frontmatter = readFrontmatter(raw("screen: x\nmystery: 1"), collector)
        assertEquals("x", frontmatter.screen)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Warning && "Unknown frontmatter key \"mystery\"" in it.message
            },
        )
    }

    @Test
    fun missingScreenIsError() {
        val collector = DiagnosticCollector()
        val frontmatter = readFrontmatter(raw("page: Operations"), collector)
        assertEquals("", frontmatter.screen)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "screen" in it.message
            },
        )
    }

    @Test
    fun missingFrontmatterIsError() {
        val collector = DiagnosticCollector()
        readFrontmatter(raw = null, diagnostics = collector)
        assertTrue(collector.diagnostics.any { it.severity == DesignSeverity.Error })
    }

    @Test
    fun nonMapFrontmatterIsError() {
        val collector = DiagnosticCollector()
        readFrontmatter(raw("- just\n- a list"), collector)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "YAML map" in it.message
            },
        )
    }

    @Test
    fun canvasWithoutNestedPositionReadsFlatCoordinates() {
        val collector = DiagnosticCollector()
        val frontmatter = readFrontmatter(
            raw("screen: x\ncanvas:\n  section: Flow\n  x: 10\n  y: 20"),
            collector,
        )
        val canvas = assertNotNull(frontmatter.canvas)
        assertEquals(10.0, canvas.x)
        assertEquals(20.0, canvas.y)
    }

    @Test
    fun breakpointWithoutIdIsDroppedWithWarning() {
        val collector = DiagnosticCollector()
        val frontmatter = readFrontmatter(
            raw("screen: x\nbreakpoints:\n  - minWidth: 100\n  - id: ok"),
            collector,
        )
        assertEquals(listOf(SlmBreakpoint("ok")), frontmatter.breakpoints)
        assertTrue(collector.diagnostics.any { "Breakpoint without `id`" in it.message })
    }

    @Test
    fun wrongTypeProducesWarningAndDefault() {
        val collector = DiagnosticCollector()
        val frontmatter = readFrontmatter(raw("screen: x\nframe:\n  width: wide"), collector)
        assertEquals(null, frontmatter.frame?.width)
        assertTrue(collector.diagnostics.any { "must be a number" in it.message })
    }

    @Test
    fun diagnosticsUseAbsoluteLines() {
        val collector = DiagnosticCollector("doc.layout.md")
        readFrontmatter(raw("page: Operations", startLine = 2), collector)
        val error = collector.diagnostics.first { it.severity == DesignSeverity.Error }
        assertEquals(2, error.location?.line)
        assertEquals("doc.layout.md", error.location?.file)
        assertEquals("#frontmatter", error.location?.pointer)
    }
}
