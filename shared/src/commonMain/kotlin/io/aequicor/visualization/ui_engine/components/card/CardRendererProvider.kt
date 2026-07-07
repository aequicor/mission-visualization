package io.aequicor.visualization.ui_engine.components.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aequicor.visualization.ui_engine.ui_document_ir.propString
import io.aequicor.visualization.ui_engine.ui_document_ir.title
import io.aequicor.visualization.ui_engine.compose_render_engine.UiComponentRendererProvider
import io.aequicor.visualization.ui_engine.compose_render_engine.UiNodeRenderer

object CardRendererProvider : UiComponentRendererProvider {
    override val type: String = "card"
    override val renderer: UiNodeRenderer = { node, _ ->
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(node.title("Card"), fontWeight = FontWeight.Bold)
                val body = node.propString("body").ifBlank { node.propString("text") }
                if (body.isNotBlank()) Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
