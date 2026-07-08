package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.ActionPatch
import io.aequicor.visualization.engine.frontend.blocks.ComponentPatch
import io.aequicor.visualization.engine.frontend.blocks.NestedInstancePatch
import io.aequicor.visualization.engine.frontend.blocks.NodePatch
import io.aequicor.visualization.engine.frontend.blocks.OverridesPatch
import io.aequicor.visualization.engine.frontend.blocks.PropsPatch
import io.aequicor.visualization.engine.frontend.blocks.SlotOverridePatch
import io.aequicor.visualization.engine.ir.model.ComponentPropertyDefinition
import io.aequicor.visualization.engine.ir.model.ComponentPropertyType
import io.aequicor.visualization.engine.ir.model.DesignAction
import io.aequicor.visualization.engine.ir.model.DesignExpression
import io.aequicor.visualization.engine.ir.model.PropValue
import io.aequicor.visualization.engine.ir.model.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentBlockReaderTest {
    /** Spec instance example (~line 592-617). */
    @Test
    fun readsSpecInstanceExample() {
        val (patches, collector) = readPatches(
            """
            node:
              type: instance
              id: createMissionButton
            component:
              ref: ds/Button
              libraryRef: ds
              variant:
                type: primary
                size: md
                state: default
              props:
                label:
                  type: text
                  value: Создать миссию
                  i18nKey: missionDashboard.actions.createMission
                iconLeading:
                  type: instanceSwap
                  value: ds/Icon/Plus
                loading:
                  type: boolean
                  value: false
            action:
              type: navigate
              to: /missions/new
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            listOf(
                NodePatch(type = "instance", id = "createMissionButton"),
                ComponentPatch(
                    ref = "ds/Button",
                    libraryRef = "ds",
                    variant = mapOf("type" to "primary", "size" to "md", "state" to "default"),
                    props = mapOf(
                        "label" to PropValue.Content(
                            TextContent(
                                key = "missionDashboard.actions.createMission",
                                defaultText = "Создать миссию",
                            ),
                        ),
                        "iconLeading" to PropValue.Reference("ds/Icon/Plus"),
                        "loading" to PropValue.Bool(false),
                    ),
                ),
                ActionPatch(DesignAction.Navigate(to = "/missions/new")),
            ),
            patches,
        )
    }

    /** Spec component definition example (~line 621-649). */
    @Test
    fun readsSpecComponentDefinitionExample() {
        val (patches, collector) = readPatches(
            """
            node:
              type: component
              id: componentMissionCard
            component:
              name: ds/MissionCard
              variants:
                status:
                  values: [nominal, warning, critical]
                density:
                  values: [compact, comfortable]
              properties:
                title:
                  type: text
                  default: Mission name
                showBadge:
                  type: boolean
                  default: true
                icon:
                  type: instanceSwap
                  preferredValues:
                    - ds/Icon/Rocket
                    - ds/Icon/Alert
                actions:
                  type: slot
                  minItems: 0
                  maxItems: 3
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            ComponentPatch(
                name = "ds/MissionCard",
                variantsAxes = mapOf(
                    "status" to listOf("nominal", "warning", "critical"),
                    "density" to listOf("compact", "comfortable"),
                ),
                properties = mapOf(
                    "title" to ComponentPropertyDefinition(
                        type = ComponentPropertyType.Text,
                        default = PropValue.Text("Mission name"),
                    ),
                    "showBadge" to ComponentPropertyDefinition(
                        type = ComponentPropertyType.Boolean,
                        default = PropValue.Bool(true),
                    ),
                    "icon" to ComponentPropertyDefinition(
                        type = ComponentPropertyType.InstanceSwap,
                        preferredValues = listOf("ds/Icon/Rocket", "ds/Icon/Alert"),
                    ),
                    "actions" to ComponentPropertyDefinition(
                        type = ComponentPropertyType.Slot,
                        minItems = 0,
                        maxItems = 3,
                    ),
                ),
            ),
            patches[1],
        )
    }

    /** Spec instance overrides example (~line 653-668). */
    @Test
    fun readsSpecPropsAndOverridesExample() {
        val (patches, collector) = readPatches(
            """
            props:
              title: "{{mission.name}}"
              showBadge: "{{mission.status != 'archived'}}"
            overrides:
              slots:
                actions:
                  - instance: ds/Button
                    props:
                      label: Открыть
                      type: secondary
              nestedInstances:
                statusBadge:
                  variant:
                    tone: "{{mission.statusTone}}"
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            PropsPatch(
                props = mapOf(
                    "title" to PropValue.Data(DesignExpression("mission.name")),
                    "showBadge" to PropValue.Data(DesignExpression("mission.status != 'archived'")),
                ),
            ),
            patches[0],
        )
        assertEquals(
            OverridesPatch(
                slots = mapOf(
                    "actions" to listOf(
                        SlotOverridePatch(
                            instanceRef = "ds/Button",
                            props = mapOf(
                                "label" to PropValue.Text("Открыть"),
                                "type" to PropValue.Text("secondary"),
                            ),
                        ),
                    ),
                ),
                nestedInstances = mapOf(
                    "statusBadge" to NestedInstancePatch(
                        variant = mapOf("tone" to "{{mission.statusTone}}"),
                    ),
                ),
            ),
            patches[1],
        )
    }

    /** Definition-side `variant:` values plus an explicit `set:` id. */
    @Test
    fun readsDefinitionVariantValuesAndExplicitSetId() {
        val (patch, collector) = readSingle(
            """
            component:
              name: WireTile
              set: wireTiles
              variant:
                kind: highlight
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            ComponentPatch(
                name = "WireTile",
                set = "wireTiles",
                variant = mapOf("kind" to "highlight"),
            ),
            patch,
        )
    }

    /** Spec detach/resetOverrides example (~line 684-689). */
    @Test
    fun readsDetachAndResetOverrides() {
        val (patch, collector) = readSingle(
            """
            component:
              ref: ds/LegacyCard
              detach: false
              resetOverrides: false
            """,
        )
        assertTrue(collector.diagnostics.isEmpty())
        assertEquals(
            ComponentPatch(ref = "ds/LegacyCard", detach = false, resetOverrides = false),
            patch,
        )
    }
}
