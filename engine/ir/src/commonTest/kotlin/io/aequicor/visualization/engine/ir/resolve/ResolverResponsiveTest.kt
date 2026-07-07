package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSize
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 5.3 resolver: responsive dimension matching and per-group patching. */
class ResolverResponsiveTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun resolveFirst(json: String, context: ResolveContext): Pair<ResolvedNode, DesignResolver> {
        val document = parse(json)
        val resolver = DesignResolver(document, context)
        val root = assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
        return root to resolver
    }

    private val breakpointJson = """
        {
          "breakpoints": [
            { "id": "compact", "maxWidth": 600 },
            { "id": "regular", "minWidth": 601 }
          ],
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame",
              "responsive": [
                { "when": { "breakpoint": "compact" }, "set": { "opacity": 0.5 } }
              ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun breakpointIsMatchedFromViewportWidth() {
        val (compact, compactResolver) = resolveFirst(
            breakpointJson,
            ResolveContext(viewport = DesignSize(width = 400.0, height = 800.0)),
        )
        assertEquals("compact", compactResolver.activeBreakpointId)
        assertEquals("compact", compactResolver.activeDimensions[ResponsiveDimension.Breakpoint])
        assertEquals(0.5, compact.opacity, "compact variant patches opacity")

        val (regular, regularResolver) = resolveFirst(
            breakpointJson,
            ResolveContext(viewport = DesignSize(width = 800.0, height = 800.0)),
        )
        assertEquals("regular", regularResolver.activeBreakpointId)
        assertEquals(1.0, regular.opacity, "regular viewport leaves the node unpatched")

        val (_, unmatchedResolver) = resolveFirst(breakpointJson, ResolveContext())
        assertNull(unmatchedResolver.activeBreakpointId, "no viewport, no breakpoint")
    }

    @Test
    fun explicitBreakpointIdWinsOverViewport() {
        val (root, resolver) = resolveFirst(
            breakpointJson,
            ResolveContext(viewport = DesignSize(width = 800.0, height = 800.0), breakpointId = "compact"),
        )
        assertEquals("compact", resolver.activeBreakpointId)
        assertEquals(0.5, root.opacity)
    }

    @Test
    fun responsivePatchAppliesBeforeVariableResolution() {
        val json = """
            {
              "variables": { "collections": { "col": {
                "modes": ["light", "dark"], "defaultMode": "light",
                "vars": { "var_accent": { "type": "color", "values": { "light": "#112233", "dark": "#445566" } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "fills": [ { "type": "solid", "color": "#FFFFFF" } ],
                  "responsive": [
                    { "when": { "theme": "dark" },
                      "set": { "fills": [ { "type": "solid", "color": { "${'$'}var": "var_accent" } } ] } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(
            dimensions = mapOf(ResponsiveDimension.Theme to "dark"),
            modeSelections = mapOf("col" to "dark"),
        )
        val (root, resolver) = resolveFirst(json, context)
        val fill = assertIs<ResolvedPaint.Solid>(root.fills.single())
        assertEquals(
            DesignColor.fromHex("#445566"),
            fill.color,
            "the patched \$var fill resolves against the active mode",
        )
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun screenModesProvideDimensionDefaultsAndContextOverridesThem() {
        val json = """
            {
              "screen": { "id": "s", "modes": { "theme": "dark" } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "responsive": [
                    { "when": { "theme": "dark" }, "set": { "opacity": 0.25 } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val (defaulted, resolver) = resolveFirst(json, ResolveContext())
        assertEquals("dark", resolver.activeDimensions[ResponsiveDimension.Theme])
        assertEquals(0.25, defaulted.opacity, "screen.modes default activates the variant")

        val (overridden, _) = resolveFirst(
            json,
            ResolveContext(dimensions = mapOf(ResponsiveDimension.Theme to "light")),
        )
        assertEquals(1.0, overridden.opacity, "explicit context dimension overrides the screen default")
    }

    @Test
    fun mostSelectorsWinPerPropertyGroup() {
        val json = """
            {
              "breakpoints": [ { "id": "compact", "maxWidth": 600 } ],
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "responsive": [
                    { "when": { "theme": "dark" }, "set": { "opacity": 0.5 } },
                    { "when": { "theme": "dark", "breakpoint": "compact" }, "set": { "opacity": 0.25 } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(
            dimensions = mapOf(ResponsiveDimension.Theme to "dark"),
            viewport = DesignSize(width = 400.0, height = 800.0),
        )
        val (root, resolver) = resolveFirst(json, context)
        assertEquals(0.25, root.opacity, "the two-selector variant beats the one-selector variant")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun equalSpecificitySameGroupConflictWarnsAndFirstDeclaredWins() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "responsive": [
                    { "when": { "theme": "dark" }, "set": { "opacity": 0.3 } },
                    { "when": { "theme": "dark" }, "set": { "opacity": 0.6 } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(dimensions = mapOf(ResponsiveDimension.Theme to "dark"))
        val (root, resolver) = resolveFirst(json, context)
        assertEquals(0.3, root.opacity, "first-declared variant wins the conflict")
        assertTrue(
            resolver.diagnostics.any { "opacity" in it.message && "first declared" in it.message },
            "expected an equal-specificity warning: ${resolver.diagnostics}",
        )
    }

    @Test
    fun equalSpecificityDifferentGroupsBothApplyWithoutWarning() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "responsive": [
                    { "when": { "theme": "dark" }, "set": { "opacity": 0.5 } },
                    { "when": { "theme": "dark" }, "set": { "size": { "width": 320, "height": 200 } } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(dimensions = mapOf(ResponsiveDimension.Theme to "dark"))
        val (root, resolver) = resolveFirst(json, context)
        assertEquals(0.5, root.opacity)
        assertEquals(DesignSize(width = 320.0, height = 200.0), root.size)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun localeAndDirectionDimensionsAreExposed() {
        val document = parse("""{ "pages": [ { "id": "p", "children": [] } ] }""")
        val resolver = DesignResolver(document, ResolveContext(locale = "ar"))
        assertEquals("ar", resolver.activeDimensions[ResponsiveDimension.Locale])
        assertEquals("rtl", resolver.activeDimensions[ResponsiveDimension.Direction])
    }
}
