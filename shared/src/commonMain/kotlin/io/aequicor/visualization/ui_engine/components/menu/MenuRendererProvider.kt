package io.aequicor.visualization.ui_engine.components.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object MenuRendererProvider : UiComponentRendererProvider {
    override val type: String = "menu"
    override val renderer: UiNodeRenderer = { node, _ ->
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFD6E4EE))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(node.title("Menu"), fontWeight = FontWeight.Bold)
                node.propList("items").forEach { item ->
                    Text(item, modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FBFF), RoundedCornerShape(6.dp)).padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
