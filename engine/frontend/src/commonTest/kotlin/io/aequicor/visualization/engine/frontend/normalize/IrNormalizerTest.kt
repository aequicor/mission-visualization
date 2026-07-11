package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.blocks.readers.slm
import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.frontend.frontmatter.SlmFrontmatter
import io.aequicor.visualization.engine.frontend.markdown.SlmMarkdownParser
import io.aequicor.visualization.engine.frontend.semantics.extractStructure
import io.aequicor.visualization.engine.ir.model.DesignGap
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.model.DesignVariable
import io.aequicor.visualization.engine.ir.model.LayoutMode
import io.aequicor.visualization.engine.ir.model.VariableType
import io.aequicor.visualization.engine.ir.model.VariableValue
import io.aequicor.visualization.engine.ir.model.DesignColor
import io.aequicor.visualization.engine.ir.model.bindable
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IrNormalizerTest {
    private fun normalize(
        body: String,
        screenId: String = "testScreen",
    ): Pair<NormalizedScreen, DiagnosticCollector> {
        val collector = DiagnosticCollector("test.layout.md")
        val document = SlmMarkdownParser(collector).parse(slm(body))
        val screen = extractStructure(document, SlmFrontmatter(screen = screenId), collector)
        return IrNormalizer(collector, "test.layout.md").normalize(screen) to collector
    }

    @Test
    fun duplicateInteractionBlocksAccumulate() {
        val (normalized, collector) = normalize(
            """
            # S

            ## Button onClick navigate (/a) onHover back
            """,
        )
        val button = normalized.document.pages.single().children.single().children.single()
        assertEquals(2, button.interactions.size)
        assertTrue(collector.diagnostics.none { "Duplicate" in it.message })
    }

    @Test
    fun rootVariablesBlockBecomesDesignVariables() {
        val (normalized, collector) = normalize(
            """
            # S

            # Collection theme (modes light dark)

            Color color.surface light #FFFFFF dark #101114

            # Prototype Variables

            String selected default «»
            """,
        )
        assertTrue(
            collector.diagnostics.none { it.severity == DesignSeverity.Error },
            collector.diagnostics.joinToString { it.message },
        )
        val collection = normalized.document.variables.collections.getValue("theme")
        assertEquals(listOf("light", "dark"), collection.modes)
        assertEquals(
            DesignVariable(
                type = VariableType.Color,
                values = mapOf(
                    "light" to VariableValue.ColorValue(DesignColor(0xFFFFFFFF)),
                    "dark" to VariableValue.ColorValue(DesignColor(0xFF101114)),
                ),
            ),
            collection.vars.getValue("color.surface"),
        )
        assertEquals(
            VariableType.Text,
            normalized.document.prototypeVariables.getValue("selected").type,
        )
    }

    @Test
    fun conditionParagraphAttachesToNextBlock() {
        val (normalized, _) = normalize(
            """
            # S

            если {{missions.length == 0}}:

            > Пока нет миссий
            """,
        )
        val callout = normalized.document.pages.single().children.single().children.single()
        assertEquals("callout", callout.role)
        assertEquals("missions.length == 0", callout.condition?.expression?.raw)
    }

    @Test
    fun repeatListItemBecomesRepeatNode() {
        val (normalized, _) = normalize(
            """
            # S

            - Карточка {{mission in missions}}
              - {{mission.name}}
            """,
        )
        val group = normalized.document.pages.single().children.single().children.single()
        val repeat = group.children.single()
        assertEquals("mission", repeat.repeat?.itemName)
        assertEquals("missions", repeat.repeat?.collection?.raw)
        assertEquals("card", repeat.role)
        val field = repeat.children.single()
        val kind = assertIs<DesignNodeKind.Text>(field.kind)
        assertEquals("{missionName}", kind.content?.defaultText)
    }
}
