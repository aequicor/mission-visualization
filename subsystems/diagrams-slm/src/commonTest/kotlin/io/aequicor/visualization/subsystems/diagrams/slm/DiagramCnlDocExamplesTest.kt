package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Doc-examples guard: every diagram CNL example published in the documentation
 * (`design-book/semantic-layout-markdown-i18n.md` «Diagrams», canonical
 * `SKILLS/SLM.md`, and `SKILLS/SLM-diagrams.md`) must compile without errors and
 * round-trip through the canonical emitter. Keep this file in sync with those docs.
 */
class DiagramCnlDocExamplesTest {

    private fun document(heading: String, body: List<String>): String = (
        listOf(
            "---",
            "screen: docExamples",
            "---",
            "",
            "# Doc Examples",
            "",
            heading,
            "",
        ) + body
        ).joinToString("\n")

    private fun assertCompilesClean(heading: String, body: List<String>, canvasId: String) {
        val result = compileWithDiagrams(document(heading, body))
        assertTrue(
            result.diagnostics.none { it.severity == DesignSeverity.Error },
            "doc example must compile without errors:\n${body.joinToString("\n")}\n-> ${result.diagnostics}",
        )
        val graph = result.diagramGraphOf(canvasId)
        // Grammar symmetry: the canonical emitter reproduces the authored sentences.
        assertEquals(
            body.filter { it.isNotBlank() },
            DiagramCnlWriter.sentences(graph),
            "doc example must be in canonical form",
        )
    }

    /** Spec «Diagrams» + skill: node sentence examples (one canvas, all payload families). */
    @Test
    fun specNodeExamplesCompileAndRoundTrip() {
        assertCompilesClean(
            heading = "## Diagram: Node Examples id nodes_canvas 1400 by 900 position 0 0",
            body = listOf(
                "Node component android_app «androidApp» stereotype «app» 150 by 56 position 100 20",
                "Node class shape «Shape» abstract 180 by 120 position 40 40 field (+ «origin: Point») method (+ abstract «area(): Double»)",
                "Node entity customer «Customer» 200 by 140 position 60 60 attribute («id» type «UUID» pk) attribute («name» type «String»)",
                "Node flowchart f1 decision 140 by 80 position 300 300 label «valid?»",
                "Node state s0 initial 24 by 24 position 40 40",
                "Node swimlane pool vertical title «Fulfilment» 640 by 360 position 20 200 lane («Intake» 140) lane («Review») lane 100",
                "Node table pricing 360 by 128 position 40 600 row (32 header) row 32 col (160 header) col 100 cell (0 0 «Plans») cell (1 1 «9€»)",
                "Node rounded-rectangle card1 220 by 120 position 40 380 style (fill #F4F7FB corners sharp) label «Draft card»",
            ),
            canvasId = "nodes_canvas",
        )
    }

    /**
     * Skill «Sizing text-bearing shapes»: the pair of use-case examples an author extrapolates
     * from — a short Latin caption and a long Cyrillic one, both hugging. One example alone left
     * the scale to guesswork, which is how a 42-character caption ended up in a 930px ellipse.
     */
    @Test
    fun skillHugExamplesCompileAndRoundTrip() {
        assertCompilesClean(
            heading = "## Diagram: Hug Examples id hug_canvas 1000 by 600 position 0 0",
            body = listOf(
                "Node use-case submit «Submit mission» 180 by 80 hug position 260 220",
                "Node use-case contacts «Вести контакты собственников и совета дома» 450 by 120 hug position 260 320",
            ),
            canvasId = "hug_canvas",
        )
    }

    /** Spec «Diagrams»: layer/edge/group examples (references resolve inside one canvas). */
    @Test
    fun specEdgeExamplesCompileAndRoundTrip() {
        assertCompilesClean(
            heading = "## Diagram: Edge Examples id edges_canvas 1200 by 800 position 0 0",
            body = listOf(
                "Layer wiring «Wiring» visible no",
                "Layer base locked yes",
                "Node class shape «Shape» 180 by 120 position 40 40",
                "Node class circle «Circle» 180 by 100 position 40 220",
                "Node class drawing «Drawing» 200 by 100 position 300 40",
                "Node entity customer «Customer» 200 by 140 position 560 40",
                "Node entity order «Order» 200 by 140 position 560 240",
                "Node bpmn gateway gateway 48 by 48 position 40 420 port (out right)",
                "Node bpmn service task 120 by 56 position 300 420 port (in left)",
                "Node rectangle intake 120 by 48 position 40 540",
                "Node rectangle review 120 by 48 position 300 540",
                "Edge e_extends from circle to shape relation generalization",
                "Edge e_owns from drawing to circle relation composition label «owns»",
                "Edge e_er from customer to order relation er one to zero-or-many label («places» at source dx 4 dy -6)",
                "Edge e_fixed from gateway.out to service.in routing straight via (420 160) via (420 240)",
                "Edge e_flow from intake to review relation transition jumps none mode link animated yes layer wiring",
                "Group g_uml «UML cluster» members (shape circle drawing)",
            ),
            canvasId = "edges_canvas",
        )
    }

    /** SLM-SKILL.md worked example 1: module/component dependency diagram. */
    @Test
    fun skillModuleDiagramExampleCompilesAndRoundTrips() {
        assertCompilesClean(
            heading = "## Diagram: Module Dependencies id module_graph 900 by 520 position 40 40",
            body = listOf(
                "Node component web_app «webApp» stereotype «app» 150 by 56 position 60 20",
                "Node component shared «shared» stereotype «app shell» 210 by 60 position 340 20",
                "Node component frontend «frontend» stereotype «engine» 170 by 56 position 60 200",
                "Node component ir «ir» stereotype «IR core» 200 by 64 position 340 200",
                "Node component backend_compose «backend-compose» stereotype «engine» 170 by 56 position 620 200",
                "Edge e_web from web_app to shared relation dependency",
                "Edge e_shared_frontend from shared to frontend relation dependency",
                "Edge e_frontend_ir from frontend to ir relation dependency label «api»",
                "Edge e_backend_ir from backend_compose to ir relation dependency",
                "Group g_engine «Engine» members (frontend ir backend_compose)",
            ),
            canvasId = "module_graph",
        )
    }

    /** SLM-SKILL.md worked example 2: UML class diagram. */
    @Test
    fun skillClassDiagramExampleCompilesAndRoundTrips() {
        assertCompilesClean(
            heading = "## Diagram: Shapes Model id class_diagram 560 by 400 position 48 48",
            body = listOf(
                "Node class shape «Shape» abstract 180 by 120 position 190 24 field (+ «origin: Point») method (+ abstract «area(): Double»)",
                "Node class circle «Circle» 180 by 100 position 60 220 field (- «radius: Double») method (+ «area(): Double»)",
                "Node class registry «Registry» stereotype «singleton» 200 by 140 position 320 220 field (- static «instance: Registry») method (+ static «get(): Registry»)",
                "Node note n1 «Circle owns its radius.» 160 by 64 position 320 40",
                "Edge e_extends from circle to shape relation generalization",
                "Edge e_uses from registry to shape relation dependency",
                "Edge e_assoc from registry to circle relation association directed label «caches»",
            ),
            canvasId = "class_diagram",
        )
    }
}
