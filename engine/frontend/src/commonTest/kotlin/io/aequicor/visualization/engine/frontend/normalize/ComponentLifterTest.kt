package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.blocks.ComponentPatch
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.Bindable
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.bindable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentLifterTest {
    private fun frame(id: String, children: List<DesignNode> = emptyList()): DesignNode =
        DesignNode(id = id, type = "frame", kind = DesignNodeKind.Frame, children = children)

    private fun instance(id: String, ref: String): DesignNode = DesignNode(
        id = id,
        type = "instance",
        kind = DesignNodeKind.Instance(componentId = ref.bindable()),
    )

    @Test
    fun registersComponentAndVariantAxesAsComponentSet() {
        val lifter = ComponentLifter(DiagnosticCollector())
        lifter.register(
            frame("componentMissionCard"),
            ComponentPatch(
                name = "ds/MissionCard",
                variantsAxes = mapOf("status" to listOf("nominal", "warning")),
            ),
            line = 1,
        )
        val component = lifter.components.getValue("componentMissionCard")
        assertEquals("ds/MissionCard", component.name)
        val set = lifter.componentSets.getValue("componentMissionCardSet")
        assertEquals(mapOf("status" to listOf("nominal", "warning")), set.axes)
    }

    @Test
    fun localRefByNameIsRewrittenToComponentId() {
        val lifter = ComponentLifter(DiagnosticCollector())
        lifter.register(frame("componentMissionCard"), ComponentPatch(name = "ds/MissionCard"), 1)
        val resolved = lifter.resolveInstances(
            frame("root", children = listOf(instance("card", "ds/MissionCard"))),
        )
        val kind = resolved.children.single().kind as DesignNodeKind.Instance
        assertEquals(Bindable.Value("componentMissionCard"), kind.componentId)
    }

    @Test
    fun localRefByIdResolvesWithoutRewrite() {
        val lifter = ComponentLifter(DiagnosticCollector())
        lifter.register(frame("componentMissionCard"), null, 1)
        val collector = DiagnosticCollector()
        val resolved = lifter.resolveInstances(instance("card", "componentMissionCard"))
        val kind = resolved.kind as DesignNodeKind.Instance
        assertEquals(Bindable.Value("componentMissionCard"), kind.componentId)
        assertTrue(collector.diagnostics.isEmpty())
    }

    @Test
    fun libraryRefWithSlashPassesValidation() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        lifter.resolveInstances(instance("button", "ds/Button"))
        assertTrue(collector.diagnostics.isEmpty())
    }

    @Test
    fun unresolvedLocalRefIsAnError() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        lifter.resolveInstances(instance("card", "missionCard"))
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error &&
                    "Unresolved local component ref \"missionCard\"" in it.message
            },
        )
    }

    @Test
    fun emptyRefIsAnError() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        lifter.resolveInstances(instance("card", ""))
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "has no component ref" in it.message
            },
        )
    }

    @Test
    fun duplicateDefinitionIsAnError() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        lifter.register(frame("card"), null, 1)
        lifter.register(frame("card"), null, 2)
        assertTrue(
            collector.diagnostics.any {
                it.severity == DesignSeverity.Error && "Duplicate component definition" in it.message
            },
        )
    }

    // --- component sets from multiple definitions ---

    private fun registerWireTiles(lifter: ComponentLifter) {
        lifter.register(
            frame("cmpWireTile"),
            ComponentPatch(
                name = "WireTile",
                variantsAxes = mapOf("kind" to listOf("default", "highlight")),
                variant = mapOf("kind" to "default"),
            ),
            line = 1,
        )
        lifter.register(
            frame("cmpWireTileHighlight"),
            ComponentPatch(name = "WireTile", variant = mapOf("kind" to "highlight")),
            line = 10,
        )
    }

    @Test
    fun siblingDefinitionsSharingNameGroupIntoOneSet() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        registerWireTiles(lifter)
        val set = lifter.componentSets.getValue("cmpWireTileSet")
        assertEquals("WireTile", set.name)
        assertEquals(mapOf("kind" to listOf("default", "highlight")), set.axes)
        assertEquals(
            mapOf(
                "kind=default" to "cmpWireTile",
                "kind=highlight" to "cmpWireTileHighlight",
            ),
            set.variants,
        )
        assertEquals("cmpWireTileHighlight", set.resolveVariant(mapOf("kind" to "highlight")))
        assertEquals("cmpWireTile", set.resolveVariant(emptyMap()))
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
    }

    @Test
    fun nameRefToVariantBearingSetRewritesToSetId() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        registerWireTiles(lifter)
        val resolved = lifter.resolveInstances(instance("tile", "WireTile"))
        val kind = resolved.kind as DesignNodeKind.Instance
        assertEquals(Bindable.Value("cmpWireTileSet"), kind.componentId)
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
    }

    @Test
    fun observedVariantValuesExtendDeclaredAxes() {
        val lifter = ComponentLifter(DiagnosticCollector())
        registerWireTiles(lifter)
        lifter.register(
            frame("cmpWireTileGhost"),
            ComponentPatch(name = "WireTile", variant = mapOf("kind" to "ghost")),
            line = 20,
        )
        val set = lifter.componentSets.getValue("cmpWireTileSet")
        assertEquals(mapOf("kind" to listOf("default", "highlight", "ghost")), set.axes)
        assertEquals("cmpWireTileGhost", set.variants["kind=ghost"])
    }

    @Test
    fun explicitSetIdGroupsDefinitionsAndValidatesAsRef() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        lifter.register(
            frame("cmpA"),
            ComponentPatch(name = "Tile", set = "wireTiles", variant = mapOf("kind" to "default")),
            line = 1,
        )
        lifter.register(
            frame("cmpB"),
            ComponentPatch(name = "Tile", set = "wireTiles", variant = mapOf("kind" to "highlight")),
            line = 10,
        )
        val set = lifter.componentSets.getValue("wireTiles")
        assertEquals(
            mapOf("kind=default" to "cmpA", "kind=highlight" to "cmpB"),
            set.variants,
        )
        // A direct ref to the explicit set id passes validation unchanged.
        val resolved = lifter.resolveInstances(instance("tile", "wireTiles"))
        assertEquals(
            Bindable.Value("wireTiles"),
            (resolved.kind as DesignNodeKind.Instance).componentId,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
    }

    @Test
    fun unspecifiedAxisFillsWithDefaultValueInVariantKey() {
        val lifter = ComponentLifter(DiagnosticCollector())
        lifter.register(
            frame("cmpCard"),
            ComponentPatch(
                name = "Card",
                variantsAxes = mapOf(
                    "kind" to listOf("default", "highlight"),
                    "size" to listOf("m", "s"),
                ),
                variant = mapOf("kind" to "highlight"),
            ),
            line = 1,
        )
        val set = lifter.componentSets.getValue("cmpCardSet")
        assertEquals(mapOf("kind=highlight,size=m" to "cmpCard"), set.variants)
    }

    @Test
    fun soloDefinitionWithAxesRegistersDefaultFallbackAndInfo() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        lifter.register(
            frame("cmpCard"),
            ComponentPatch(
                name = "Card",
                variantsAxes = mapOf("kind" to listOf("default", "highlight")),
            ),
            line = 1,
        )
        val set = lifter.componentSets.getValue("cmpCardSet")
        assertEquals(mapOf("kind=default" to "cmpCard"), set.variants)

        // Name refs keep resolving to the component id (current behavior).
        val resolved = lifter.resolveInstances(instance("card", "Card"))
        assertEquals(
            Bindable.Value("cmpCard"),
            (resolved.kind as DesignNodeKind.Instance).componentId,
        )
        // The default-only set is reported once (info surfaces as a warning).
        assertEquals(
            1,
            collector.diagnostics.count { "defines only the default variant" in it.message },
        )
        assertTrue(collector.diagnostics.none { it.severity == DesignSeverity.Error })
        lifter.resolveInstances(instance("card2", "cmpCardSet"))
        assertEquals(
            1,
            collector.diagnostics.count { "defines only the default variant" in it.message },
        )
    }

    @Test
    fun duplicateVariantCombinationIsAnError() {
        val collector = DiagnosticCollector()
        val lifter = ComponentLifter(collector)
        registerWireTiles(lifter)
        lifter.register(
            frame("cmpWireTileCopy"),
            ComponentPatch(name = "WireTile", variant = mapOf("kind" to "highlight")),
            line = 20,
        )
        assertEquals(
            1,
            collector.diagnostics.count {
                it.severity == DesignSeverity.Error && "already defines variant \"kind=highlight\"" in it.message
            },
        )
        // The first definition keeps the combination.
        assertEquals(
            "cmpWireTileHighlight",
            lifter.componentSets.getValue("cmpWireTileSet").variants["kind=highlight"],
        )
    }
}
