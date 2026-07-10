package io.aequicor.visualization.editor

import io.aequicor.visualization.editor.data.MissionOverviewSlm
import io.aequicor.visualization.editor.data.MissionOverviewYamlSnapshot
import io.aequicor.visualization.editor.data.MissionTelemetrySlm
import io.aequicor.visualization.editor.data.MissionTelemetryYamlSnapshot
import io.aequicor.visualization.editor.data.ShapesShowcaseSlm
import io.aequicor.visualization.editor.data.ShapesShowcaseYamlSnapshot
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoCnlMigrationEquivalenceTest {

    @Test
    fun shapesShowcaseCnlMatchesYamlSnapshot() {
        val yaml = compileClean(ShapesShowcaseYamlSnapshot)
        val cnl = compileClean(ShapesShowcaseSlm)

        assertDocumentsEquivalent(yaml, cnl)
    }

    @Test
    fun missionOverviewCnlMatchesYamlSnapshot() {
        val yaml = compileClean(MissionOverviewYamlSnapshot)
        val cnl = compileClean(MissionOverviewSlm)

        assertDocumentsEquivalent(yaml, cnl)
    }

    @Test
    fun missionTelemetryCnlMatchesYamlSnapshot() {
        val yaml = compileClean(MissionTelemetryYamlSnapshot)
        val cnl = compileClean(MissionTelemetrySlm)

        assertDocumentsEquivalent(yaml, cnl)
    }

    private fun compileClean(source: String): DesignDocument {
        val result = compileSlm(source)
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document)
    }

    private fun assertDocumentsEquivalent(expected: DesignDocument, actual: DesignDocument) {
        assertEquals(expected.schemaVersion, actual.schemaVersion, "schemaVersion")
        assertEquals(expected.id, actual.id, "id")
        assertEquals(expected.name, actual.name, "name")
        assertEquals(expected.styles, actual.styles, "styles")
        assertEquals(expected.variables, actual.variables, "variables")
        assertEquals(expected.assets, actual.assets, "assets")
        assertEquals(expected.screen, actual.screen, "screen")
        assertEquals(expected.libraries, actual.libraries, "libraries")
        assertEquals(expected.breakpoints, actual.breakpoints, "breakpoints")
        assertEquals(expected.devicePresets, actual.devicePresets, "devicePresets")
        assertEquals(expected.prototypeVariables, actual.prototypeVariables, "prototypeVariables")
        assertEquals(expected.actionSets, actual.actionSets, "actionSets")
        assertEquals(expected.i18n, actual.i18n, "i18n")
        assertEquals(expected.handoff, actual.handoff, "handoff")
        assertEquals(expected.motionRefs, actual.motionRefs, "motionRefs")
        assertEquals(expected.components.keys, actual.components.keys, "components.keys")
        expected.components.forEach { (id, component) ->
            val other = assertNotNull(actual.components[id], "component $id")
            assertEquals(component.copy(root = component.root.normalizedForAuthoringEquivalence()), other.copy(root = other.root.normalizedForAuthoringEquivalence()), "component $id")
        }
        assertEquals(expected.componentSets, actual.componentSets, "componentSets")
        assertEquals(expected.pages.map { it.id to it.name }, actual.pages.map { it.id to it.name }, "pages")
        expected.pages.zip(actual.pages).forEach { (expectedPage, actualPage) ->
            assertEquals(expectedPage.children.size, actualPage.children.size, "page ${expectedPage.id} child count")
            expectedPage.children.zip(actualPage.children).forEach { (expectedNode, actualNode) ->
                assertNodesEquivalent(expectedNode, actualNode, expectedPage.id)
            }
        }
    }

    private fun assertNodesEquivalent(expected: DesignNode, actual: DesignNode, path: String) {
        val nodePath = "$path/${expected.id}"
        assertEquals(
            expected.normalizedForAuthoringEquivalence().copy(children = emptyList()),
            actual.normalizedForAuthoringEquivalence().copy(children = emptyList()),
            nodePath,
        )
        assertEquals(expected.children.size, actual.children.size, "$nodePath child count")
        expected.children.zip(actual.children).forEach { (expectedChild, actualChild) ->
            assertNodesEquivalent(expectedChild, actualChild, nodePath)
        }
    }

    private fun DesignNode.normalizedForAuthoringEquivalence(): DesignNode =
        copy(
            sourceMap = null,
            blockSourceMaps = emptyMap(),
            interactions = interactions.map { it.copy(sourceMap = null) },
            children = children.map { it.normalizedForAuthoringEquivalence() },
        )
}
