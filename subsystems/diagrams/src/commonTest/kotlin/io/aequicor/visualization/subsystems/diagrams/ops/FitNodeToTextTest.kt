package io.aequicor.visualization.subsystems.diagrams.ops

import io.aequicor.visualization.subsystems.diagrams.model.DiagramGraph
import io.aequicor.visualization.subsystems.diagrams.model.DiagramLabel
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNode
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeId
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodePayload
import io.aequicor.visualization.subsystems.diagrams.model.DiagramNodeSizing
import io.aequicor.visualization.subsystems.diagrams.model.DiagramShapeKind
import io.aequicor.visualization.subsystems.diagrams.model.UmlClassNode
import io.aequicor.visualization.subsystems.diagrams.model.UmlUseCaseNode
import io.aequicor.visualization.subsystems.diagrams.model.withNode
import io.aequicor.visualization.subsystems.diagrams.text.ApproximateDiagramTextMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [fitNodeToText]: draw.io hug semantics, pinned to the reported repro's numbers. */
class FitNodeToTextTest {

    private val id = DiagramNodeId("n")
    private val measurer = ApproximateDiagramTextMeasurer()
    private val longLabel = "Вести контакты собственников и совета дома"

    private fun graphOf(node: DiagramNode) = DiagramGraph().withNode(node)

    private fun useCase(
        text: String = longLabel,
        w: Double = 930.0,
        h: Double = 260.0,
        sizing: DiagramNodeSizing = DiagramNodeSizing.Hug,
    ) = DiagramNode(
        id = id, x = 100.0, y = 50.0, width = w, height = h,
        payload = UmlUseCaseNode(text), sizing = sizing,
    )

    @Test
    fun hugReplacesTheGuessedNineHundredThirtyWithAboutFourHundredForty() {
        // The regression this whole change exists for: an author guessed 930x260 around a label
        // that measures ~295px. The ellipse that actually contains it is (294.8 + 2*8) * √2.
        val fitted = graphOf(useCase()).fitNodeToText(id, measurer)
        val node = requireNotNull(fitted.nodeById(id))

        assertEquals(439.5, node.width, 5.0)
        assertTrue(node.width < 500.0, "930 was 2.23x too wide; got ${node.width}")
        assertEquals(930.0, useCase().width, "guard: the authored size really was 930")
    }

    @Test
    fun hugRecomputesFromTheCenterAnchor() {
        val before = useCase()
        val fitted = graphOf(before).fitNodeToText(id, measurer)
        val node = requireNotNull(fitted.nodeById(id))

        assertEquals(
            before.x + before.width / 2.0,
            node.x + node.width / 2.0,
            1e-9,
            "a hug must grow from the alignment anchor, not from the top-left",
        )
        assertEquals(before.y + before.height / 2.0, node.y + node.height / 2.0, 1e-9)
    }

    @Test
    fun aFixedNodeIsNeverResized() {
        val node = useCase(sizing = DiagramNodeSizing.Fixed)
        val graph = graphOf(node)

        assertEquals(graph, graph.fitNodeToText(id, measurer), "geometry stays authoritative")
    }

    @Test
    fun forceFitsEvenAFixedNode() {
        // The explicit "fit to text" action works on any node; only the automatic path checks sizing.
        val fitted = graphOf(useCase(sizing = DiagramNodeSizing.Fixed))
            .fitNodeToText(id, measurer, force = true)

        assertTrue(requireNotNull(fitted.nodeById(id)).width < 500.0)
    }

    @Test
    fun hugMeasuresUnwrappedSoTheShapeGrowsSideways() {
        // A narrow start must not lock the caption into the narrow width: draw.io measures the
        // caption with no width constraint and lets the shape widen.
        val fitted = graphOf(useCase(w = 60.0, h = 40.0)).fitNodeToText(id, measurer)
        val node = requireNotNull(fitted.nodeById(id))

        assertEquals(439.5, node.width, 5.0, "unwrapped measurement must widen the ellipse")
    }

    @Test
    fun hugNeverShrinksBelowTheKindMinimum() {
        val fitted = graphOf(useCase(text = "x")).fitNodeToText(id, measurer)
        val node = requireNotNull(fitted.nodeById(id))
        val min = DiagramNodeDefaults.minimumSizeFor(UmlUseCaseNode("x"))

        assertTrue(node.width >= min.width, "${node.width} < ${min.width}")
        assertTrue(node.height >= min.height, "${node.height} < ${min.height}")
    }

    @Test
    fun aRectangleHugsWithoutTheEllipseFactor() {
        val node = DiagramNode(
            id = id, x = 0.0, y = 0.0, width = 400.0, height = 200.0,
            payload = DiagramNodePayload.BasicShape(DiagramShapeKind.RECTANGLE),
            labels = listOf(DiagramLabel(longLabel)),
            sizing = DiagramNodeSizing.Hug,
        )
        val fitted = requireNotNull(graphOf(node).fitNodeToText(id, measurer).nodeById(id))

        // 294.8 + 2*4 padding, no √2 — a rectangle contains its text directly.
        assertEquals(302.8, fitted.width, 5.0)
    }

    @Test
    fun contentSizedPayloadsAreLeftAlone() {
        // A class is as tall as its compartments; hugging it to its name would crush the rows.
        val node = DiagramNode(
            id = id, x = 0.0, y = 0.0, width = 160.0, height = 108.0,
            payload = UmlClassNode(name = "Class"), sizing = DiagramNodeSizing.Hug,
        )
        val graph = graphOf(node)

        assertEquals(graph, graph.fitNodeToText(id, measurer))
    }

    @Test
    fun anEmptyCaptionLeavesTheNodeAlone() {
        val graph = graphOf(useCase(text = ""))
        assertEquals(graph, graph.fitNodeToText(id, measurer))
    }
}
