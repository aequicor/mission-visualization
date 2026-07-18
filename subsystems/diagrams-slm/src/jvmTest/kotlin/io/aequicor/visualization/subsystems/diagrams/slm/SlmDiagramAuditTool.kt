package io.aequicor.visualization.subsystems.diagrams.slm

import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.subsystems.diagrams.export.diagramToSvg
import io.aequicor.visualization.subsystems.diagrams.lint.lintDiagram
import io.aequicor.visualization.subsystems.diagrams.lint.lintReport
import io.aequicor.visualization.subsystems.diagrams.routing.routeAllEdgesLenient
import java.io.File
import kotlin.test.Test

/**
 * Vision-test audit tool for real `*.layout.md` files (env-gated, no-op in CI):
 *
 * ```
 * SLM_AUDIT_FILE=/path/to/file.layout.md SLM_AUDIT_OUT=/tmp/audit \
 *   ./gradlew :subsystems:diagrams-slm:jvmTest --tests "*.SlmDiagramAuditTool"
 * ```
 *
 * Compiles the file with the diagram extension, routes every diagram, writes one SVG
 * per diagram plus a `lint.txt` report of vision-test warnings into SLM_AUDIT_OUT.
 */
class SlmDiagramAuditTool {

    @Test
    fun audit() {
        val path = System.getenv("SLM_AUDIT_FILE") ?: return
        val outDir = System.getenv("SLM_AUDIT_OUT") ?: return
        val result = compileWithDiagrams(File(path).readText())
        val document = requireNotNull(result.document) {
            "compile failed:\n" + result.diagnostics.joinToString("\n") { "${it.severity}: ${it.message}" }
        }
        val compileIssues = result.diagnostics.filter { it.severity == DesignSeverity.Error }
        require(compileIssues.isEmpty()) {
            "compile errors:\n" + compileIssues.joinToString("\n") { it.message }
        }

        val out = File(outDir).apply { mkdirs() }
        val report = StringBuilder()
        result.diagnostics.forEach { report.appendLine("compile ${it.severity}: ${it.message}") }

        val diagrams = mutableListOf<DesignNode>()
        fun collect(node: DesignNode) {
            if (node.kind is DesignNodeKind.Diagram) diagrams += node
            node.children.forEach(::collect)
        }
        document.pages.forEach { page -> page.children.forEach(::collect) }

        for (node in diagrams) {
            val graph = (node.kind as DesignNodeKind.Diagram).graph
            val routes = routeAllEdgesLenient(graph)
            File(out, "${node.id}.svg").writeText(diagramToSvg(graph, routes.values.toList()))
            val findings = lintDiagram(graph, routes)
            report.appendLine()
            report.appendLine("=== diagram '${node.id}': ${graph.nodes.size} nodes, ${graph.edges.size} edges, ${findings.size} warnings ===")
            report.appendLine(findings.lintReport())
            fun dumpRoute(id: io.aequicor.visualization.subsystems.diagrams.model.DiagramEdgeId) {
                val points = routes[id]?.points?.joinToString(" ") { "(${it.x.toInt()},${it.y.toInt()})" }
                report.appendLine("  route '${id.value}': $points")
            }
            findings.forEach { finding ->
                when (finding) {
                    is io.aequicor.visualization.subsystems.diagrams.lint.DiagramLintFinding.EdgeThroughNode ->
                        dumpRoute(finding.edgeId)
                    is io.aequicor.visualization.subsystems.diagrams.lint.DiagramLintFinding.EdgeOverlap -> {
                        dumpRoute(finding.first)
                        dumpRoute(finding.second)
                    }
                    is io.aequicor.visualization.subsystems.diagrams.lint.DiagramLintFinding.CrossingHotspot ->
                        report.appendLine(
                            "  hotspot (${finding.at.x.toInt()},${finding.at.y.toInt()}): " +
                                finding.edgeIds.joinToString(", ") { it.value },
                        )
                    else -> Unit
                }
            }
            val hotspotEdges = findings
                .filterIsInstance<io.aequicor.visualization.subsystems.diagrams.lint.DiagramLintFinding.CrossingHotspot>()
                .flatMap { it.edgeIds }
                .toSet()
            if (hotspotEdges.isNotEmpty()) {
                report.appendLine("  hotspot routes:")
                hotspotEdges.forEach(::dumpRoute)
            }
        }
        File(out, "lint.txt").writeText(report.toString())
        println(report)
    }
}
