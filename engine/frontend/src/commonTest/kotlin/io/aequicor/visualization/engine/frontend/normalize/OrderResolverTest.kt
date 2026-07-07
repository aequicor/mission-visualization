package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderResolverTest {
    private fun node(id: String, order: Int? = null): DesignNode =
        DesignNode(id = id, type = "frame", kind = DesignNodeKind.Frame, order = order)

    @Test
    fun implicitOrderIsMarkdownIndexTimesTen() {
        val resolved = resolveOrder(
            listOf(node("a"), node("b"), node("c")),
            DiagnosticCollector(),
        )
        assertEquals(listOf("a", "b", "c"), resolved.map { it.id })
        assertEquals(listOf(10, 20, 30), resolved.map { it.order })
    }

    @Test
    fun explicitOrderSortsAmongImplicit() {
        val resolved = resolveOrder(
            listOf(node("a"), node("b", order = 5), node("c")),
            DiagnosticCollector(),
        )
        assertEquals(listOf("b", "a", "c"), resolved.map { it.id })
    }

    @Test
    fun sortIsStableForEqualOrders() {
        val resolved = resolveOrder(
            listOf(node("a", order = 10), node("b", order = 10), node("c", order = 10)),
            DiagnosticCollector(),
        )
        assertEquals(listOf("a", "b", "c"), resolved.map { it.id })
    }

    @Test
    fun mixedUsageEmitsInfoDiagnostic() {
        val collector = DiagnosticCollector()
        resolveOrder(listOf(node("a"), node("b", order = 5)), collector, parentLabel = "panel")
        assertTrue(collector.diagnostics.any { "Mixed explicit and implicit" in it.message })
    }

    @Test
    fun allExplicitEmitsNoDiagnostic() {
        val collector = DiagnosticCollector()
        resolveOrder(listOf(node("a", order = 2), node("b", order = 1)), collector)
        assertTrue(collector.diagnostics.isEmpty())
    }
}
