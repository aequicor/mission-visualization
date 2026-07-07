package io.aequicor.visualization.ui_engine.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.UiNode
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.UiRenderContext
import io.aequicor.visualization.ui_engine.compose_render_engine.ifDefault
import io.aequicor.visualization.ui_engine.compose_render_engine.spacingDp
import io.aequicor.visualization.ui_engine.compose_render_engine.toneSurface

@Composable
fun ContainerNode(
    node: UiNode,
    context: UiRenderContext,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = toneSurface(node.style.tone),
        border = BorderStroke(1.dp, Color(0xFFD6E4EE)),
    ) {
        LayoutContainer(node, Modifier.padding(spacingDp(node.layout.padding).ifDefault(14.dp)), context)
    }
}

@Composable
private fun LayoutContainer(
    node: UiNode,
    modifier: Modifier,
    context: UiRenderContext,
) {
    val gap = spacingDp(node.layout.gap).ifDefault(10.dp)
    val title = node.propString("title")
    when (node.layout.type) {
        "row" -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
            if (title.isNotBlank()) Text(title, fontWeight = FontWeight.Bold, color = DeepLapis)
            node.children.forEach { Box(Modifier.weight(1f)) { context.renderChild(it) } }
        }
        "grid" -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (title.isNotBlank()) Text(title, fontWeight = FontWeight.Bold, color = DeepLapis)
            node.children.chunked(node.layout.columns ?: 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { Box(Modifier.weight(1f)) { context.renderChild(it) } }
                    repeat((node.layout.columns ?: 2) - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        "stack" -> Box(modifier.fillMaxWidth()) {
            node.children.forEach { context.renderChild(it) }
            if (title.isNotBlank()) Text(title, Modifier.align(Alignment.TopStart), fontWeight = FontWeight.Bold, color = DeepLapis)
        }
        else -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (title.isNotBlank()) Text(title, fontWeight = FontWeight.Bold, color = DeepLapis)
            node.propString("body").ifBlank { node.propString("text") }.takeIf { it.isNotBlank() }?.let {
                Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            node.children.forEach { context.renderChild(it) }
        }
    }
}
