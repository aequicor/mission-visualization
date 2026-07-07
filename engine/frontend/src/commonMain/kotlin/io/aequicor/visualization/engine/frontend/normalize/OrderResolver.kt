package io.aequicor.visualization.engine.frontend.normalize

import io.aequicor.visualization.engine.frontend.diagnostics.DiagnosticCollector
import io.aequicor.visualization.engine.ir.model.DesignNode

/**
 * Resolves sibling paint order. Nodes without an explicit `node.order` get the
 * implicit `(markdownIndex + 1) * 10`; the list is then stably sorted by order,
 * so equal orders keep document order. Mixing explicit and implicit order among
 * siblings is legal but gets an info diagnostic.
 */
fun resolveOrder(
    children: List<DesignNode>,
    diagnostics: DiagnosticCollector,
    parentLabel: String = "",
    line: Int = 0,
): List<DesignNode> {
    if (children.isEmpty()) return children
    val explicitCount = children.count { it.order != null }
    if (explicitCount in 1 until children.size) {
        diagnostics.info(
            "Mixed explicit and implicit `order` among children" +
                (if (parentLabel.isEmpty()) "" else " of \"$parentLabel\""),
            line,
        )
    }
    val ordered = children.mapIndexed { index, child ->
        if (child.order != null) child else child.copy(order = (index + 1) * 10)
    }
    return ordered.sortedBy { it.order }
}
