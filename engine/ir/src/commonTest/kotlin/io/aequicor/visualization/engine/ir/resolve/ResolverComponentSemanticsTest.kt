package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 5.3 resolver: libraries, slots, nested overrides, reset, detach. */
class ResolverComponentSemanticsTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun resolveFirst(
        json: String,
        context: ResolveContext = ResolveContext(),
    ): Pair<ResolvedNode, DesignResolver> {
        val document = parse(json)
        val resolver = DesignResolver(document, context)
        val root = assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
        return root to resolver
    }

    private fun ResolvedNode.findBySourceId(id: String): ResolvedNode? {
        if (sourceId == id) return this
        return children.firstNotNullOfOrNull { it.findBySourceId(id) }
    }

    private val libraryDocument = parseLibrary()

    private fun parseLibrary(): DesignDocument =
        assertIs<DesignParseResult.Success>(
            parseDesignDocument(
                """
                {
                  "components": {
                    "cmp_badge": {
                      "name": "Badge",
                      "root": { "id": "badge_root", "type": "frame", "children": [
                        { "id": "badge_label", "type": "text", "characters": "Lib" }
                      ] }
                    }
                  }
                }
                """.trimIndent(),
            ),
        ).document

    @Test
    fun libraryComponentResolvesThroughRegistryByPrefixedComponentId() {
        val json = """
            {
              "libraries": [ { "id": "ds" } ],
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "badge", "type": "instance", "componentId": "ds/cmp_badge" }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json, ResolveContext(libraries = mapOf("ds" to libraryDocument)))
        val badge = assertNotNull(root.findBySourceId("badge"))
        assertEquals("Lib", assertNotNull(badge.findBySourceId("badge_label")?.text).characters)
        assertEquals("Badge", badge.name, "instance inherits the library component name")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun libraryComponentResolvesThroughRegistryByLibraryRef() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "badge", "type": "instance", "componentId": "cmp_badge", "libraryRef": "ds" }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json, ResolveContext(libraries = mapOf("ds" to libraryDocument)))
        assertEquals("Lib", assertNotNull(root.findBySourceId("badge_label")?.text).characters)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun unknownLibraryYieldsPlaceholderAndDiagnostic() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "badge", "type": "instance", "componentId": "cmp_badge", "libraryRef": "ghost" }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json)
        val badge = assertNotNull(root.findBySourceId("badge"))
        assertIs<ResolvedPaint.Unknown>(badge.fills.first())
        assertTrue(resolver.diagnostics.any { "ghost" in it.message })
    }

    private val slotJson = """
        {
          "components": {
            "cmp_card": {
              "name": "Card",
              "properties": { "content": { "type": "slot" } },
              "root": { "id": "card_root", "type": "frame", "children": [
                { "id": "card_slot", "type": "slot", "slot": "content", "children": [
                  { "id": "slot_default", "type": "text", "characters": "Empty" }
                ] }
              ] }
            }
          },
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame", "children": [
              { "id": "card", "type": "instance", "componentId": "cmp_card"INSTANCE_EXTRAS }
            ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun slotFallsBackToAuthoredChildren() {
        val (root, resolver) = resolveFirst(slotJson.replace("INSTANCE_EXTRAS", ""))
        val slot = assertNotNull(root.findBySourceId("card_slot"))
        assertEquals(listOf("slot_default"), slot.children.map { it.sourceId })
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun slotContentPropFillsTheSlot() {
        val extras = """,
            "props": { "content": [ { "id": "from_prop", "type": "text", "characters": "Prop" } ] }"""
        val (root, _) = resolveFirst(slotJson.replace("INSTANCE_EXTRAS", extras))
        val slot = assertNotNull(root.findBySourceId("card_slot"))
        assertEquals(listOf("from_prop"), slot.children.map { it.sourceId }, "prop fill replaces the default")
        assertEquals("Prop", slot.children.first().text?.characters)
    }

    @Test
    fun slotOverrideBeatsPropAndDefault() {
        val extras = """,
            "props": { "content": [ { "id": "from_prop", "type": "text", "characters": "Prop" } ] },
            "overrides": [
              { "target": ["card_slot"],
                "set": { "slotContent": [ { "id": "from_override", "type": "text", "characters": "Override" } ] } }
            ]"""
        val (root, _) = resolveFirst(slotJson.replace("INSTANCE_EXTRAS", extras))
        val slot = assertNotNull(root.findBySourceId("card_slot"))
        assertEquals(listOf("from_override"), slot.children.map { it.sourceId }, "override fill wins")
    }

    private val resetJson = """
        {
          "components": {
            "cmp_label": {
              "root": { "id": "label_root", "type": "text", "characters": "Default" }
            }
          },
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame", "children": [
              { "id": "inst", "type": "instance", "componentId": "cmp_label",
                "resetOverrides": RESET,
                "overrides": [
                  { "target": ["label_root"], "set": { "characters": "Overridden" } }
                ] }
            ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun resetOverridesClearsInstanceOverridesBeforeExpansion() {
        val (withOverride, _) = resolveFirst(resetJson.replace("RESET", "false"))
        assertEquals("Overridden", withOverride.findBySourceId("inst")?.text?.characters)

        val (reset, _) = resolveFirst(resetJson.replace("RESET", "true"))
        assertEquals("Default", reset.findBySourceId("inst")?.text?.characters, "resetOverrides drops the override")
    }

    @Test
    fun detachMarksTheResolvedNode() {
        val json = """
            {
              "components": {
                "cmp_tile": { "root": { "id": "tile_root", "type": "frame", "children": [
                  { "id": "tile_child", "type": "rectangle", "size": { "width": 8, "height": 8 } }
                ] } }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "kept", "type": "instance", "componentId": "cmp_tile" },
                  { "id": "loose", "type": "instance", "componentId": "cmp_tile", "detach": true }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json)
        val kept = assertNotNull(root.findBySourceId("kept"))
        assertFalse(kept.detached)
        val loose = assertNotNull(root.findBySourceId("loose"))
        assertTrue(loose.detached, "detach: true marks the resolved node")
        assertNotNull(loose.findBySourceId("tile_child"), "a detached instance still expands identically")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun nestedInstanceOverrideAppliesVariantAndPropsBeforeExpansion() {
        val json = """
            {
              "components": {
                "cmp_a": { "root": { "id": "a_root", "type": "frame", "fills": [ { "type": "solid", "color": "#0000FF" } ] } },
                "cmp_b": { "root": { "id": "b_root", "type": "frame", "fills": [ { "type": "solid", "color": "#00FF00" } ] } },
                "cmp_text": {
                  "properties": { "label": { "type": "text", "default": "Inner" } },
                  "root": { "id": "text_root", "type": "text", "characters": { "${'$'}prop": "label" } }
                },
                "cmp_holder": {
                  "root": { "id": "holder_root", "type": "frame", "children": [
                    { "id": "swap", "type": "instance", "componentId": "set_x", "variant": { "kind": "a" } },
                    { "id": "caption", "type": "instance", "componentId": "cmp_text" }
                  ] }
                }
              },
              "componentSets": {
                "set_x": { "axes": { "kind": ["a", "b"] }, "variants": { "kind=a": "cmp_a", "kind=b": "cmp_b" } }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "holder", "type": "instance", "componentId": "cmp_holder",
                    "overrides": [
                      { "target": ["swap"], "set": { "variant": { "kind": "b" } } },
                      { "target": ["caption"], "set": { "props": { "label": "Custom" } } }
                    ] }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json)
        val swapped = assertNotNull(root.findBySourceId("swap"))
        assertEquals(
            DesignColor.fromHex("#00FF00"),
            (swapped.fills.first() as ResolvedPaint.Solid).color,
            "override variant re-selects the component before expansion",
        )
        val caption = assertNotNull(root.findBySourceId("caption"))
        assertEquals("Custom", caption.text?.characters, "override props reach the inner instance")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun slotFilledContentStillResolvesConditionsAndBindings() {
        val json = """
            {
              "components": {
                "cmp_list": {
                  "root": { "id": "list_root", "type": "frame", "children": [
                    { "id": "list_slot", "type": "slot", "slot": "items" }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "list", "type": "instance", "componentId": "cmp_list",
                    "props": { "items": [
                      { "id": "shown", "type": "frame", "condition": "{{show}}" },
                      { "id": "hidden", "type": "frame", "condition": "{{!show}}" }
                    ] } }
                ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(data = mapOf("show" to DataValue.Bool(true)))
        val (root, _) = resolveFirst(json, context)
        assertNotNull(root.findBySourceId("shown"), "slot content participates in condition resolution")
        assertNull(root.findBySourceId("hidden"))
    }
}
