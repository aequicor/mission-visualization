package io.aequicor.visualization.subsystems.diagrams.lint

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.templates.diagramTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vision-test rule for the reported screenshot: a caption and the box it was given must
 * agree. Before this rule, `lintDiagram` ran six rules and not one of them compared a node's
 * label to the node's own box — which is precisely why the defect shipped.
 */
class NodeLabelFitLintTest {

    private val longLabel = "Вести контакты собственников и совета дома"

    private fun useCase(w: Double, h: Double, text: String = longLabel) = DiagramGraph().withNode(
        DiagramNode(
            id = DiagramNodeId("uc"),
            x = 0.0, y = 0.0, width = w, height = h,
            payload = UmlUseCaseNode(text),
        ),
    )

    private fun fits(graph: DiagramGraph): List<DiagramLintFinding.NodeLabelFit> =
        lintDiagram(graph).filterIsInstance<DiagramLintFinding.NodeLabelFit>()

    @Test
    fun theReportedNineHundredThirtyByTwoHundredSixtyEllipseIsFlaggedAsOversized() {
        val findings = fits(useCase(930.0, 260.0))

        assertEquals(1, findings.size, "the repro must produce exactly one finding: $findings")
        assertEquals(DiagramLintFinding.NodeLabelFit.Kind.OVERSIZED, findings.single().kind)
        assertTrue("930x260" in findings.single().message, findings.single().message)
    }

    @Test
    fun aCaptionCrammedIntoATinyEllipseIsFlaggedAsOverflow() {
        val findings = fits(useCase(160.0, 70.0))

        assertEquals(1, findings.size, "$findings")
        assertEquals(DiagramLintFinding.NodeLabelFit.Kind.OVERFLOW, findings.single().kind)
    }

    @Test
    fun theHuggedSizeIsClean() {
        // What fitNodeToText actually produces for this caption: (294.8 + 16) x √2 wide.
        assertEquals(emptyList(), fits(useCase(440.0, 46.0)), "a hugged shape must lint clean")
    }

    @Test
    fun aRoomyButReasonableShapeIsNotNagged() {
        // The size the authoring skill recommends for this caption. A rule that fires here
        // would be noise, and authors would learn to ignore it.
        assertEquals(emptyList(), fits(useCase(450.0, 120.0)), "the documented size must lint clean")
    }

    @Test
    fun anOrdinaryBoxAroundAShortCaptionIsNotNagged() {
        // A short caption hugs to almost nothing, so judging by the caption alone would
        // condemn every normal shape stamped at its default size.
        assertEquals(emptyList(), fits(useCase(140.0, 70.0, text = "Idle")))
    }

    @Test
    fun contentSizedNodesAreNotJudgedByTheirCaption() {
        // A class is sized by its rows; its name says nothing about how tall it should be.
        val graph = DiagramGraph().withNode(
            DiagramNode(
                id = DiagramNodeId("c"),
                x = 0.0, y = 0.0, width = 300.0, height = 200.0,
                payload = UmlClassNode(name = "A"),
            ),
        )

        assertEquals(emptyList(), fits(graph))
    }

    @Test
    fun everyShippedTemplateLintsCleanForLabelFit() {
        // If a template trips this rule, the P3 defaults are wrong, not the template.
        diagramTemplates().forEach { template ->
            val findings = fits(template.graph)
            assertTrue(
                findings.isEmpty(),
                "template '${template.id}' has label-fit findings: ${findings.map { it.message }}",
            )
        }
    }
}
