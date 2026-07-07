package io.aequicor.visualization.ui_engine.components.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.propList
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object SidebarRendererProvider : UiComponentRendererProvider {
    override val type: String = "sidebar"
    override val renderer: UiNodeRenderer = { node, context ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF8FBFF),
            border = BorderStroke(1.dp, Color(0xFFD6E4EE)),
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.width(132.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(node.title("Navigation"), fontWeight = FontWeight.Bold, color = DeepLapis)
                    node.propList("items").ifEmpty { listOf("Overview", "Details") }.forEachIndexed { index, item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else Color.White,
                        ) {
                            Text(item, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    node.children.forEach { context.renderChild(it) }
                }
            }
        }
    }
}
