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
}
