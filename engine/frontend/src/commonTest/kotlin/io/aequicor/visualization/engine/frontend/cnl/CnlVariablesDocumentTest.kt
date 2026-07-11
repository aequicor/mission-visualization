package io.aequicor.visualization.engine.frontend.cnl

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.compileSlm
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.PrototypeVariable
import io.aequicor.visualization.engine.ir.model.VariableCollection
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CnlVariablesDocumentTest {
    private val frontmatter = """
        ---
        screen: demo
        sourceLocale: en-US
        targetLocales: [en-US]
        ---
    """.trimIndent()

    private val variables = """
        # Collection theme «Theme» (modes light dark default light)

        Color color.surface light #FFFFFF dark #101114
        Color color.brand light ${'$'}color.surface dark #000000
        Number radius.card light 8 dark 8
        String copy.title light «Active missions» dark «Active missions»
        Boolean feature.enabled light yes dark no

        # Prototype Variables

        String selectedMissionId default «»
        Boolean isCreateDialogOpen default no
    """.trimIndent()

    private val body = """
        # Mission Overview

        Rectangle 10 by 10 color ${'$'}color.surface
    """.trimIndent()

    private fun compile(src: String): DesignDocument {
        val result = compileSlm(slm(src) + "\n")
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            result.diagnostics.filter { it.severity == DesignSeverity.Error }.joinToString { it.message },
        )
        return assertNotNull(result.document)
    }

    @Test
    fun variableSectionsCompileAsDocumentScopeOnly() {
        val document = compile("$frontmatter\n\n$variables\n\n$body")

        assertEquals(
            mapOf(
                "theme" to VariableCollection(
                    name = "Theme",
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
                        "color.brand" to DesignVariable(
                            type = VariableType.Color,
                            values = mapOf(
                                "light" to VariableValue.Alias("color.surface"),
                                "dark" to VariableValue.ColorValue(DesignColor(0xFF000000)),
                            ),
                        ),
                        "copy.title" to DesignVariable(
                            type = VariableType.Text,
                            values = mapOf(
                                "light" to VariableValue.TextValue("Active missions"),
                                "dark" to VariableValue.TextValue("Active missions"),
                            ),
                        ),
                        "feature.enabled" to DesignVariable(
                            type = VariableType.Bool,
                            values = mapOf(
                                "light" to VariableValue.BoolValue(true),
                                "dark" to VariableValue.BoolValue(false),
                            ),
                        ),
                    ),
                ),
            ),
            document.variables.collections,
        )
        assertEquals(
            mapOf(
                "selectedMissionId" to PrototypeVariable(
                    type = VariableType.Text,
                    default = VariableValue.TextValue(""),
                ),
                "isCreateDialogOpen" to PrototypeVariable(
                    type = VariableType.Bool,
                    default = VariableValue.BoolValue(false),
                ),
            ),
            document.prototypeVariables,
        )
        val names = document.pages.single().allNodes().map { it.name }
        assertFalse(names.any { it.startsWith("Collection") || it == "Prototype Variables" })
    }

    @Test
    fun variableSectionsEmitAndRecompile() {
        val doc1 = compile("$frontmatter\n\n$variables\n\n$body")
        val emitted = CnlEmitter.emitVariables(doc1)
        val doc2 = compile("$frontmatter\n\n$emitted\n\n$body")

        assertEquals(doc1.variables, doc2.variables)
        assertEquals(doc1.prototypeVariables, doc2.prototypeVariables)
        assertEquals(emitted, CnlEmitter.emitVariables(doc2))
        assertTrue(emitted.contains("# Collection theme «Theme» (modes light dark default light)"))
        assertTrue(emitted.contains("Color color.brand light ${'$'}color.surface dark #000000"))
        assertTrue(emitted.contains("String copy.title light «Active missions» dark «Active missions»"))
    }
}
