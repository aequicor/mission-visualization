package io.aequicor.visualization.engine.frontend.blocks.readers

import io.aequicor.visualization.engine.frontend.blocks.VariablesPatch
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariablesBlockReaderTest {
    /** Spec variables example (~line 695-719). */
    @Test
    fun readsSpecCollectionsExample() {
        val (patch, collector) = readSingle(
            """
            variables:
              collections:
                - id: theme
                  modes: [light, dark]
                  variables:
                    color.surface:
                      type: color
                      values:
                        light: "#ffffff"
                        dark: "#101114"
                    radius.card:
                      type: number
                      values:
                        light: 8
                        dark: 8
                - id: density
                  modes: [compact, comfortable]
                  variables:
                    space.4:
                      type: number
                      values:
                        compact: 12
                        comfortable: 16
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            VariablesPatch(
                collections = mapOf(
                    "theme" to VariableCollection(
                        name = "theme",
                        modes = listOf("light", "dark"),
                        defaultMode = "light",
                        vars = mapOf(
                            "color.surface" to DesignVariable(
                                type = VariableType.Color,
                                values = mapOf(
                                    "light" to VariableValue.ColorValue(DesignColor(0xFFFFFFFF)),
                                    "dark" to VariableValue.ColorValue(DesignColor(0xFF101114)),
                                ),
                            ),
                            "radius.card" to DesignVariable(
                                type = VariableType.Number,
                                values = mapOf(
                                    "light" to VariableValue.NumberValue(8.0),
                                    "dark" to VariableValue.NumberValue(8.0),
                                ),
                            ),
                        ),
                    ),
                    "density" to VariableCollection(
                        name = "density",
                        modes = listOf("compact", "comfortable"),
                        defaultMode = "compact",
                        vars = mapOf(
                            "space.4" to DesignVariable(
                                type = VariableType.Number,
                                values = mapOf(
                                    "compact" to VariableValue.NumberValue(12.0),
                                    "comfortable" to VariableValue.NumberValue(16.0),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            patch,
        )
    }

    /** Spec prototype variables example (~line 1036-1044). */
    @Test
    fun readsPrototypeVariables() {
        val (patch, collector) = readSingle(
            """
            variables:
              prototype:
                selectedMissionId:
                  type: string
                  default: ""
                isCreateDialogOpen:
                  type: boolean
                  default: false
            """,
        )
        assertTrue(collector.diagnostics.isEmpty(), collector.diagnostics.joinToString { it.message })
        assertEquals(
            VariablesPatch(
                prototype = mapOf(
                    "selectedMissionId" to PrototypeVariable(
                        type = VariableType.Text,
                        default = VariableValue.TextValue(""),
                    ),
                    "isCreateDialogOpen" to PrototypeVariable(
                        type = VariableType.Bool,
                        default = VariableValue.BoolValue(false),
                    ),
                ),
            ),
            patch,
        )
    }

    @Test
    fun aliasValueAndUnknownModeWarning() {
        val (patch, collector) = readSingle(
            """
            variables:
              collections:
                - id: semantic
                  modes: [light]
                  variables:
                    color.primary:
                      type: color
                      values:
                        light: §palette.blue.500
                        dusk: "#000000"
            """,
        )
        val variables = (patch as VariablesPatch).collections!!.getValue("semantic").vars
        assertEquals(
            mapOf(
                "light" to VariableValue.Alias("palette.blue.500"),
                "dusk" to VariableValue.ColorValue(DesignColor(0xFF000000)),
            ),
            variables.getValue("color.primary").values,
        )
        assertTrue(collector.diagnostics.any { "dusk" in it.message })
    }
}
