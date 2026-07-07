package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.InteractionTrigger
import io.aequicor.visualization.engine.ir.serialization.DesignParseResult
import io.aequicor.visualization.engine.ir.serialization.parseDesignDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 5.3 resolver: conditions, repeat, and `{{...}}` data bindings. */
class ResolverDataTest {

    private fun parse(json: String): DesignDocument =
        assertIs<DesignParseResult.Success>(parseDesignDocument(json)).document

    private fun resolveFirst(json: String, context: ResolveContext): Pair<ResolvedNode, DesignResolver> {
        val document = parse(json)
        val resolver = DesignResolver(document, context)
        val root = assertNotNull(resolver.resolvePage(document.pages.first()).firstOrNull())
        return root to resolver
    }

    private fun ResolvedNode.findBySourceId(id: String): ResolvedNode? {
        if (sourceId == id) return this
        return children.firstNotNullOfOrNull { it.findBySourceId(id) }
    }

    private val missionsData = mapOf(
        "missions" to DataValue.ListValue(
            listOf(
                DataValue.MapValue(mapOf("id" to DataValue.Str("m1"), "name" to DataValue.Str("Alpha"))),
                DataValue.MapValue(mapOf("id" to DataValue.Str("m2"), "name" to DataValue.Str("Beta"))),
            ),
        ),
    )

    private val repeatJson = """
        {
          "pages": [ { "id": "p", "children": [
            { "id": "root", "type": "frame", "children": [
              { "id": "card", "type": "frame",
                "repeat": { "item": "mission", "in": "missions", "index": "i", "key": "mission.id" },
                "children": [
                  { "id": "title", "type": "text", "characters": { "${'$'}data": "mission.name" } }
                ] }
            ] }
          ] } ]
        }
    """.trimIndent()

    @Test
    fun repeatExpandsWithStableKeyedIds() {
        val (root, resolver) = resolveFirst(repeatJson, ResolveContext(data = missionsData))
        assertEquals(listOf("card[m1]", "card[m2]"), root.children.map { it.id })
        assertEquals(listOf("card", "card"), root.children.map { it.sourceId })
        assertEquals(
            listOf("Alpha", "Beta"),
            root.children.map { assertNotNull(it.findBySourceId("title")?.text).characters },
            "each clone resolves bindings against its own item scope",
        )
        assertEquals(
            "card[m1]/title",
            assertNotNull(root.children.first().findBySourceId("title")).id,
            "clone descendants are namespaced per item",
        )
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun repeatIndexBindingIsAvailableInTheItemScope() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "row", "type": "frame",
                    "repeat": { "item": "mission", "in": "missions", "index": "i" },
                    "opacity": { "${'$'}data": "i" } }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json, ResolveContext(data = missionsData))
        assertEquals(listOf(0.0, 1.0), root.children.map { it.opacity }, "index binding resolves per item")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun repeatWithoutKeyFallsBackToIndexIds() {
        val json = repeatJson.replace(""", "key": "mission.id"""", "")
        val (root, _) = resolveFirst(json, ResolveContext(data = missionsData))
        assertEquals(listOf("card[0]", "card[1]"), root.children.map { it.id })
    }

    @Test
    fun repeatWithoutDataKeepsSinglePlaceholderWithoutRepeatWarning() {
        val (root, resolver) = resolveFirst(repeatJson, ResolveContext())
        assertEquals(1, root.children.size, "preview mode keeps one placeholder instance")
        assertEquals("card", root.children.first().id, "placeholder keeps the authored id")
        assertTrue(
            resolver.diagnostics.none { "missions" in it.message },
            "the unevaluable collection itself is warning-free: ${resolver.diagnostics}",
        )
    }

    @Test
    fun conditionFalseDropsNodeAndUnevaluableKeepsItWithWarning() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "kept", "type": "frame", "condition": "{{user.premium}}" },
                  { "id": "dropped", "type": "frame", "condition": "{{user.premium == false}}" },
                  { "id": "unevaluable", "type": "frame", "condition": "{{ghost.flag}}" }
                ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(
            data = mapOf("user" to DataValue.MapValue(mapOf("premium" to DataValue.Bool(true)))),
        )
        val (root, resolver) = resolveFirst(json, context)
        assertNotNull(root.findBySourceId("kept"), "true condition keeps the node")
        assertNull(root.findBySourceId("dropped"), "false condition drops the node")
        assertNotNull(root.findBySourceId("unevaluable"), "unevaluable condition keeps the node")
        assertTrue(
            resolver.diagnostics.any { "ghost.flag" in it.message },
            "expected an unevaluable-condition warning: ${resolver.diagnostics}",
        )
    }

    @Test
    fun perItemConditionFiltersInsideTheItemScope() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame", "children": [
                  { "id": "card", "type": "frame",
                    "repeat": "{{mission in missions}}",
                    "condition": "{{mission.id == 'm2'}}" }
                ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json, ResolveContext(data = missionsData))
        assertEquals(listOf("card[1]"), root.children.map { it.id }, "only the matching item survives")
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun dataBindingResolvesInTextAndProps() {
        val json = """
            {
              "components": {
                "cmp_chip": {
                  "properties": { "label": { "type": "text", "default": "Chip" } },
                  "root": { "id": "chip_root", "type": "frame", "children": [
                    { "id": "chip_label", "type": "text", "characters": { "${'$'}prop": "label" } }
                  ] }
                }
              },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "layout": { "mode": "vertical", "gap": { "${'$'}data": "spacing" } },
                  "children": [
                    { "id": "direct", "type": "text", "characters": { "${'$'}data": "user.name" } },
                    { "id": "chip", "type": "instance", "componentId": "cmp_chip",
                      "props": { "label": "{{user.name}}" } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(
            data = mapOf(
                "spacing" to DataValue.Num(12.0),
                "user" to DataValue.MapValue(mapOf("name" to DataValue.Str("Ada"))),
            ),
        )
        val (root, resolver) = resolveFirst(json, context)
        assertEquals(12.0, root.layout.gap, "numeric binding resolves in layout")
        assertEquals("Ada", assertNotNull(root.findBySourceId("direct")?.text).characters)
        assertEquals(
            "Ada",
            assertNotNull(root.findBySourceId("chip_label")?.text).characters,
            "PropValue.Data resolves through instance expansion",
        )
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun dataBindingTypeMismatchWarnsAndFallsBack() {
        val json = """
            {
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "opacity": { "${'$'}data": "user.name" } }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(
            data = mapOf("user" to DataValue.MapValue(mapOf("name" to DataValue.Str("Ada")))),
        )
        val (root, resolver) = resolveFirst(json, context)
        assertEquals(1.0, root.opacity, "mismatch falls back to the default")
        assertTrue(
            resolver.diagnostics.any { "user.name" in it.message && "number" in it.message },
            "expected a type-mismatch warning: ${resolver.diagnostics}",
        )
    }

    @Test
    fun interactionsCarryThroughWithVariablesSubstituted() {
        val json = """
            {
              "variables": { "collections": { "col": {
                "modes": ["m"], "defaultMode": "m",
                "vars": { "var_tab": { "type": "string", "values": { "m": "overview" } } }
              } } },
              "pages": [ { "id": "p", "children": [
                { "id": "root", "type": "frame",
                  "interactions": [
                    { "trigger": "onClick",
                      "action": { "type": "setVariable", "variable": "tab", "value": { "${'$'}var": "var_tab" } } },
                    { "trigger": "onHover",
                      "action": { "type": "changeToVariant", "target": "btn", "variant": { "state": "hover" } } }
                  ] }
              ] } ]
            }
        """.trimIndent()
        val (root, resolver) = resolveFirst(json, ResolveContext())
        assertEquals(2, root.interactions.size)
        val click = root.interactions.first()
        assertEquals(InteractionTrigger.OnClick, click.trigger)
        val setVariable = assertIs<DesignAction.SetVariable>(click.actions.single())
        assertEquals("tab", setVariable.variable)
        assertEquals(Bindable.Value("overview"), setVariable.value, "variable value substituted to a literal")
        val hover = root.interactions.last()
        val changeToVariant = assertIs<DesignAction.ChangeToVariant>(hover.actions.single())
        assertEquals(mapOf("state" to "hover"), changeToVariant.variant)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }

    @Test
    fun dataBoundContentParamResolvesAgainstTheDataScope() {
        val json = """
            {
              "i18n": { "sourceLocale": "en", "resources": { "en": { "greet": "Hello, {name}!" } } },
              "pages": [ { "id": "p", "children": [
                { "id": "label", "type": "text",
                  "content": { "key": "greet", "params": { "name": "{{user.name}}" } } }
              ] } ]
            }
        """.trimIndent()
        val context = ResolveContext(
            data = mapOf("user" to DataValue.MapValue(mapOf("name" to DataValue.Str("Ada")))),
        )
        val (root, resolver) = resolveFirst(json, context)
        assertEquals("Hello, Ada!", assertNotNull(root.text).characters)
        assertEquals("greet", assertNotNull(root.text).contentKey)
        assertEquals(0, resolver.diagnostics.size, "expected no diagnostics: ${resolver.diagnostics}")
    }
}
