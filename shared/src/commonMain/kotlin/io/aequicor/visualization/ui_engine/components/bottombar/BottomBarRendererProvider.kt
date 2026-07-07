package io.aequicor.visualization.ui_engine.components.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.propList
import io.aequicor.visualization.ui_engine.compose_render_engine.DeepLapis
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object BottomBarRendererProvider : UiComponentRendererProvider {
    override val type: String = "bottomBar"
    override val renderer: UiNodeRenderer = { node, _ ->
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F6FB)) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceAround) {
                node.propList("items").ifEmpty { listOf("Home", "Notes", "Export") }.forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = DeepLapis)
                }
            }
        }
    }
}
